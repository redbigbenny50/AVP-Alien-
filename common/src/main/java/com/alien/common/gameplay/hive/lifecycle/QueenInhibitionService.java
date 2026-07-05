package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.growth.HiveLocationClaims;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.blib.api.common.faction.v1.FactionMember;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;

/**
 * Mints and tears down the personal, severed, single-chunk hive location that an inhibited queen carries (Part 2 — the
 * contained-breeder claim). The inhibitor flips her wild -&gt; inhibited; this service gives that flag a real claim so
 * the autonomy gate (Slice B1) has a location to suppress, and so her eggs (once she is contained) have a lineage to
 * key to.
 * <p>
 * Both capture cases end the same way — she ends up alone in a fresh severed lineage whose single location claims
 * exactly the chunk she stands in, flagged inhibited so it never expands, spawns, or accrues, and which follows her as
 * she roams (B2a-2). A <b>pre-foundation</b> queen (no lineage yet) is minted that claim directly. A
 * <b>post-foundation</b> queen is first severed out of her existing hive — her founder link cleared and her memberships
 * dropped — which leaves that hive queenless-but-alive; the severing itself never kills it.
 */
public final class QueenInhibitionService {

    private QueenInhibitionService() {}

    /**
     * Called when the inhibitor is applied. A never-founded queen is minted her personal single-chunk inhibited claim
     * directly; a post-foundation queen is first severed out of her existing hive (left queenless-but-alive) and then
     * minted the same claim. Idempotent — a queen who already carries her personal claim is left alone.
     */
    public static void onInhibited(ServerLevel level, Queen queen) {
        if (findInhibitedLocation(queen) != null) {
            return; // already carries her personal claim
        }
        if (belongsToLineage(queen)) {
            severFromOldHive(queen);
        }
        mintPersonalClaim(level, queen);
    }

    /**
     * Severs a post-foundation queen from her existing hive: clears her founder link on any location she founded and
     * drops her lineage + location faction memberships, leaving the old hive queenless-but-alive. Per design the
     * severing never kills the old hive directly — it becomes a normal queenless hive ({@link QueenlessMaturationTask}
     * crowns a successor) and only dies later if it independently hits the empty-membership + zero-reliable-population
     * conditions that the per-tick {@link LineageDeathHandler} already enforces. Her variant faction membership is left
     * intact so the fresh severed lineage can adopt her; removing her from lineage membership fires BLib's
     * onMemberRemoved hook, which clears the location member maps for us.
     */
    private static void severFromOldHive(Queen queen) {
        var member = FactionMember.entity(queen);
        for (var factionId : new ArrayList<>(Alien.MOD.factions().getFactionIds(queen.getUUID()))) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
                continue;
            }
            // Locations first (preserves the location-subset-lineage invariant), clearing founder links as we go.
            for (var location : new ArrayList<>(lineage.locationsById().values())) {
                if (queen.getUUID().equals(location.founderId())) {
                    location.setFounderId(null); // null founder == queenless; the growth/economy tasks expect this
                    // Record a PENDING rescue campaign on her original hive before the link is fully lost. It stays
                    // pending (dispatches nothing) until RescueCampaignTask sees her contained AND carried outside this
                    // claim — capture-in-place stays frenzy, not rescue. Captor is stamped later from her damage
                    // source.
                    if (location.rescueCampaign() == null) {
                        location.setRescueCampaign(
                            new com.alien.common.gameplay.hive.party.RescueCampaign(queen.getUUID())
                        );
                    }
                }
                var locationFaction = Alien.MOD.factions().get(location.id().value());
                if (locationFaction != null) {
                    locationFaction.membership().removeMember(member);
                }
            }
            faction.membership().removeMember(member);
            Alien.LOGGER.info(
                "Inhibition: severed queen {} from old lineage {}; left queenless-but-alive with {} location(s)",
                queen.getUUID(),
                factionId,
                lineage.locationsById().size()
            );
        }
    }

    /** Mints her personal single-chunk severed inhibited claim at the chunk she currently stands in. */
    private static void mintPersonalClaim(ServerLevel level, Queen queen) {
        var herChunk = new ChunkPos(queen.blockPosition());
        var locationId = HiveLocationFoundingService.foundNewLineage(queen, queen.blockPosition());
        var location = HiveLocationRegistry.INSTANCE.get(locationId);
        if (location == null) {
            Alien.LOGGER.warn(
                "Inhibition: minted location {} for queen {} vanished immediately",
                locationId,
                queen.getUUID()
            );
            return;
        }

        // Flag inhibited FIRST so the autonomy gate suppresses this location before its first tick, then trim the
        // freshly-claimed core down to exactly the chunk she stands in. An inhibited claim is a single chunk.
        location.setInhibited(true);
        for (var chunk : new ArrayList<>(location.claimedChunks())) {
            if (!chunk.equals(herChunk)) {
                HiveLocationClaims.release(level, location, chunk);
            }
        }

        Alien.LOGGER.info(
            "Inhibition: queen {} severed into personal inhibited location {} at chunk {}",
            queen.getUUID(),
            locationId,
            herChunk
        );
    }

    /**
     * Called when the inhibitor is removed (she reverts to wild). Releases her personal inhibited location's claim and
     * unregisters it so she can re-found normally. No-op if she has none. (Full lineage cleanup arrives with the
     * inhibitor-removal feature; this is the location-level teardown.)
     */
    public static void onReleased(ServerLevel level, Queen queen) {
        var location = findInhibitedLocation(queen);
        if (location == null) {
            return;
        }
        for (var chunk : new ArrayList<>(location.claimedChunks())) {
            HiveLocationClaims.release(level, location, chunk);
        }
        HiveLocationRegistry.INSTANCE.unregister(location.id());
        Alien.LOGGER.info(
            "Inhibition: queen {} released; tore down inhibited location {}",
            queen.getUUID(),
            location.id()
        );
    }

    /**
     * Per-tick (throttled by the caller) follow-chunk migration: an inhibited queen's single-chunk claim tracks the
     * chunk she currently stands in. When she has moved off her claimed chunk, this claims her new chunk, relocates the
     * location's center onto it (so the old chunk is no longer the protected center), and releases everything else —
     * leaving exactly one claim, on her. No-op once she is already centered on her chunk. Never steals a chunk another
     * hive owns; if she wanders onto claimed ground her claim simply stays put until she steps back onto free ground.
     */
    public static void tickFollow(ServerLevel level, Queen queen) {
        var location = findInhibitedLocation(queen);
        if (location == null) {
            return;
        }
        var herChunk = new ChunkPos(queen.blockPosition());
        var claimed = location.claimedChunks();
        if (claimed.size() == 1 && new ChunkPos(location.centerPos()).equals(herChunk)) {
            return; // already a single claim centered on her
        }
        if (!claimed.contains(herChunk)) {
            var occupant = HiveLocationRegistry.INSTANCE.getByChunk(location.dimension(), herChunk);
            if (occupant != null && !occupant.id().equals(location.id())) {
                return; // another hive owns this chunk — don't poach it
            }
            if (!HiveLocationClaims.claim(level, location, herChunk, level.getGameTime())) {
                return;
            }
        }
        // Her chunk becomes the new anchor, which frees the old center for release.
        location.setCenterPos(queen.blockPosition());
        for (var chunk : new ArrayList<>(location.claimedChunks())) {
            if (!chunk.equals(herChunk)) {
                HiveLocationClaims.release(level, location, chunk);
            }
        }
    }

    /** Her personal inhibited location (founded by her, flagged inhibited), or {@code null} if she has none. */
    public static HiveLocation findInhibitedLocation(Queen queen) {
        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (location.isInhibited() && queen.getUUID().equals(location.founderId())) {
                return location;
            }
        }
        return null;
    }

    private static boolean belongsToLineage(Queen queen) {
        for (var factionId : Alien.MOD.factions().getFactionIds(queen.getUUID())) {
            if (LineageIds.isLineageId(factionId)) {
                return true;
            }
        }
        return false;
    }
}
