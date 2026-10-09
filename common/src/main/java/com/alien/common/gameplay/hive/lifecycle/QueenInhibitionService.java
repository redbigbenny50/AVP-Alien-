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

import java.util.ArrayList;

/**
 * The inhibitor's effect on a queen's place in the hive system.
 * <p>
 * \u2b50\u2b50 Oct 3 - THE INHIBITOR NO LONGER GIVES HER A CLAIM. [stated] "when capturing a queen she on a chain or
 * with an inhibitor she can no longer make a claim using the faction system." It used to mint her a personal, severed,
 * single-chunk inhibited location that followed her around - which is the only reason chained queens had to be held in
 * the centre of one chunk. Now applying it just SEVERS her from her hive, exactly as the first chain does, and she
 * holds nothing at all. Everything that leaned on that claim was moved off it (see {@link QueenCaptivity}).
 * </p>
 * <p>
 * \u26a0 Old worlds still carry those personal claims. {@link #tearDownLegacyPersonalClaim} removes one the first time
 * its queen ticks. The location-level {@code inhibited} flag and every reader of it are left in place: nothing creates
 * one any more, but an admin can still set it by command, and it costs nothing to keep honouring.
 * </p>
 */
public final class QueenInhibitionService {

    private QueenInhibitionService() {}

    /**
     * Called when the inhibitor is applied: sever her from any hive she belongs to. No claim is minted - a captive
     * holds none. Idempotent.
     */
    public static void onInhibited(ServerLevel level, Queen queen) {
        // THE INHIBITOR DOES NOTHING TO AN IRRADIATED QUEEN. [stated] "not anymore - it most likely would get taken
        // off by the hive, otherwise its just decorative." Her strain's territory belongs to the LOCATION rather than
        // to her, so there is nothing of hers to sever.
        if (com.alien.common.gameplay.hive.economy.IrradiatedHiveRules.isIrradiated(queen)) {
            return;
        }
        tearDownLegacyPersonalClaim(level, queen);
        severFromHives(queen);
    }

    /**
     * Severs a queen from her existing hive - Oct 3: for the inhibitor AND for the first chain ([stated] "chaining
     * should severe her like the inhibitor does"). Clears her founder link on any location she founded and drops her
     * lineage + location faction memberships, leaving the old hive queenless-but-alive. Per design the severing never
     * kills the old hive directly — it becomes a normal queenless hive ({@link QueenlessMaturationTask} crowns a
     * successor) and only dies later if it independently hits the empty-membership + zero-reliable-population
     * conditions that the per-tick {@link LineageDeathHandler} already enforces. Her variant faction membership is left
     * intact so the fresh severed lineage can adopt her; removing her from lineage membership fires BLib's
     * onMemberRemoved hook, which clears the location member maps for us.
     */
    public static void severFromHives(Queen queen) {
        if (!belongsToLineage(queen)) {
            return;
        }
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
                    // pending (dispatches nothing) while she is still inside this hive's slab - there her kin claw at
                    // her chains directly (QueenRescueManager). Once she is captive and OUTSIDE the slab she has been
                    // kidnapped, and RescueCampaignTask arms it against the nearest player. It also blocks every
                    // succession path, so her seat is held for her until it succeeds or is exhausted.
                    // \u26a0 Oct 3 - NOT IN AN END-STYLE HIVE. [stated] End hives run no convoys, so RescueCampaignTask
                    // skips
                    // them outright - a campaign recorded there would never arm, never fail and never time out, and
                    // would
                    // hold the seat forever. In the End her kin rescue her in place or a summoned queen takes over.
                    if (location.rescueCampaign() == null && !location.isEndStyleHive()) {
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

    /**
     * Called when the inhibitor is removed. There is no claim to tear down any more; this only clears a personal claim
     * left over from an old save. Her release grace is started by {@link QueenCaptivity#tick} when she stops being
     * captive, so it covers every way the inhibitor can come off.
     */
    public static void onReleased(ServerLevel level, Queen queen) {
        tearDownLegacyPersonalClaim(level, queen);
    }

    /**
     * Removes the personal one-chunk inhibited claim an old save may still hold for this queen: releases its chunks and
     * unregisters it (the registry marks the lineage dirty itself). No-op when she has none, which is always the case
     * for a queen inhibited after this change.
     */
    public static void tearDownLegacyPersonalClaim(ServerLevel level, Queen queen) {
        var location = findInhibitedLocation(queen);
        if (location == null) {
            return;
        }
        for (var chunk : new ArrayList<>(location.claimedChunks())) {
            HiveLocationClaims.release(level, location, chunk);
        }
        HiveLocationRegistry.INSTANCE.unregister(location.id());
        Alien.LOGGER.info(
            "Inhibition: removed the legacy personal claim {} of queen {} - captives no longer hold claims",
            location.id(),
            queen.getUUID()
        );
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
