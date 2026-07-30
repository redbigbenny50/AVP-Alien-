package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.gameplay.block.entity.jelly.JellyVatBlockEntity;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.structure.HiveFootprint;
import com.alien.common.gameplay.hive.structure.HiveRouter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.UUID;

/**
 * What a nuke does to the hives it reaches.
 * <h2>Why this lives on our side</h2> AVP: Human's explosion already walks {@code getEntitiesInRadius} and transmutes
 * aliens to irradiated, guarded by {@code AVPAlien.MOD.isLoaded()}. That covers ENTITIES inside the BLAST - which is
 * only radius 5. The rule here is "explosion OR irradiated biome", and the biome is radius 128, so a hive whose
 * territory the fallout swallows but the blast never touches would be invisible to an entity loop. This needs the
 * detonation point and BOTH radii, and it needs to test hive TERRITORIES rather than entities, so it cannot be done
 * from inside their loop.
 * <h2>Aberrant hives die</h2> The aberrant strain cannot convert - it is the weak line, which is why radiation kills it
 * rather than changing it. So a nuke that reaches an aberrant hive's territory ends the hive outright: every loaded
 * member is killed properly so its loot drops, and the location, its reserves and its slab are removed. Normal and
 * nether hives CONVERT instead; that is the larger half of the job and is not built here.
 */
public final class NukeStrike {

    private NukeStrike() {
        throw new UnsupportedOperationException();
    }

    /**
     * Called when a nuke detonates. {@code biomeRadius} is the irradiated biome's reach and {@code blastRadius} the
     * explosion's; a hive dies if EITHER touches its territory, so only the larger actually decides anything today -
     * but both are passed because the blast is what will matter once conversion has its own, tighter rule.
     */
    public static void onDetonation(ServerLevel level, Vec3 center, int biomeRadius, int blastRadius) {
        var reach = Math.max(biomeRadius, blastRadius);

        // Walk lineages rather than the location registry, because a location alone cannot hand back the
        // LineageFactionData that killing one requires - and this is the pattern LocationDormancyTask already uses.
        // Snapshot at BOTH levels: killing a location removes its per-location faction, which mutates the id view,
        // and removes it from locationsById.
        for (var factionId : new ArrayList<>(Alien.MOD.factions().getAllIds())) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }

            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage) || !lineage.isAlive()) {
                continue;
            }

            if (!lineage.dimension().equals(level.dimension())) {
                continue;
            }

            for (var location : new ArrayList<>(lineage.locationsById().values())) {
                if (!location.isAlive() || !territoryReached(location, center, reach)) {
                    continue;
                }

                var variant = location.lineageVariantOrNull();
                if (variant == null) {
                    continue;
                }

                switch (variant) {
                    // The weak line cannot take it. The hive ends here.
                    case ABERRANT -> destroy(level, location, lineage, center);

                    // Everything else is remade rather than removed - see NukeConversion.
                    case NORMAL, NETHER -> NukeConversion.convert(level, location, lineage);

                    // Already irradiated: it is standing in its own element. Same empty arm the potion uses, and it
                    // is what stops a second nuke re-converting a hive that has nothing left to convert.
                    case IRRADIATED -> {}
                }
            }
        }
    }

    /**
     * Who this hive blames - decided PER HIVE, because a blast that catches two of them may well have had different
     * people standing in each.
     * <p>
     * FIRST: anyone physically inside the territory when it went off, nearest to the blast. Territory is a column - the
     * square is tested on X and Z only, so someone tunnelling underneath or watching from a ledge above counts exactly
     * as much as someone standing in the doorway.
     * <p>
     * FAILING THAT, two ledgers in order of how strong a claim they make. The attack campaigns first, by time spent
     * fighting - an entry only exists there once a player has DAMAGED a member, so it is the sharpest evidence the hive
     * has. Then the visit ledger, oldest first, which records everyone who ever set foot inside whether they swung at
     * anything or not. That last one is what catches the quiet approach: walk in, place a warhead, walk out, detonate
     * from a hilltop. It leaves no fight and no witness at the blast, but the hive still remembers the day you arrived.
     */
    /**
     * EVERY player this hive holds responsible, not just the worst one.
     * <p>
     * The single-culprit {@link #resolveCulprit} answers "who does the empress avenge this on" - one grudge, one
     * target. The birth raid instead comes for [stated] "the person or persons involved", so it needs the whole list:
     * anyone standing in the territory when it went off, anyone who has been fighting the hive, and anyone the visit
     * ledger ever saw inside it.
     */
    public static java.util.List<UUID> resolveAllCulprits(ServerLevel level, HiveLocation location) {
        var culprits = new java.util.LinkedHashSet<UUID>();

        for (var player : level.players()) {
            if (HiveFootprint.contains(location, player.getX(), player.getZ())) {
                culprits.add(player.getUUID());
            }
        }

        culprits.addAll(location.attackCampaigns().keySet());
        culprits.addAll(location.territoryVisits().keySet());

        return new ArrayList<>(culprits);
    }

    private static @Nullable UUID resolveCulprit(ServerLevel level, HiveLocation location, Vec3 center) {
        UUID closest = null;
        var closestDistance = Double.MAX_VALUE;

        for (var player : level.players()) {
            if (!HiveFootprint.contains(location, player.getX(), player.getZ())) {
                continue;
            }

            var distance = player.position().distanceToSqr(center);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = player.getUUID();
            }
        }

        if (closest != null) {
            return closest;
        }

        // Nobody present. Fall back on who has spent the most time fighting this hive - that is the strongest claim
        // to blame, and it is what the attack ledger is for.
        UUID longestFighting = null;
        var longestDwell = -1L;

        for (var entry : location.attackCampaigns().entrySet()) {
            var dwell = entry.getValue().dwellTicks();
            if (dwell > longestDwell) {
                longestDwell = dwell;
                longestFighting = entry.getKey();
            }
        }

        if (longestFighting != null) {
            return longestFighting;
        }

        // Still nobody. The visit ledger catches the quiet ones: someone who walked in, placed a warhead and left
        // without hitting anything appears HERE and nowhere else. Oldest entry wins - whoever found the place first.
        UUID firstVisitor = null;
        var earliestVisit = Long.MAX_VALUE;

        for (var entry : location.territoryVisits().entrySet()) {
            if (entry.getValue() < earliestVisit) {
                earliestVisit = entry.getValue();
                firstVisitor = entry.getKey();
            }
        }

        return firstVisitor;
    }

    /**
     * Whether a blast of {@code reach} blocks touches this hive's territory square - 19x19 normally, 23x23 under an
     * empress, straight off {@link HiveRouter}'s own extents so the two can never disagree about how big a hive is.
     * <p>
     * Measured to the NEAREST POINT of the square rather than its centre, so clipping the corner of a big hive counts.
     */
    private static boolean territoryReached(HiveLocation location, Vec3 center, int reach) {
        return HiveFootprint.distanceTo(location, center.x, center.z) <= reach;
    }

    private static void destroy(ServerLevel level, HiveLocation location, LineageFactionData lineage, Vec3 center) {
        // BOTH read BEFORE the kill: LocationDeathHandler tears the location down, HiveRouter forgets its influence,
        // and the attack-campaign ledger goes with the location.
        var empressInfluenced = location.isEmpressInfluenced();
        var culprit = resolveCulprit(level, location, center);

        var killed = killMembersInside(level, location);
        halveSurvivingVats(level, location);

        // Routed through LocationDeathHandler rather than LocationRemovalHelper directly: the handler also fires the
        // kill-a-hive advancement, records lineage kill credit, and drops the per-location faction. A nuke is a kill,
        // so it should count as one.
        //
        // A redstone-triggered nuke with nobody nearby leaves no killer, and killByPlayer needs one - so a nuke with
        // no attributable player is recorded as an admin kill instead of inventing a culprit.
        if (culprit == null) {
            LocationDeathHandler.killAdmin(level, location, lineage, "nuclear detonation with no attributable player");
        } else {
            LocationDeathHandler.killByPlayer(level, location, lineage, culprit);
        }

        // An empress does not let this go. Her lineage answers a day later - see NukeRetribution for why the delay
        // is the point, and why nothing happens at all if she did not survive to collect.
        if (empressInfluenced && culprit != null) {
            NukeRetribution.schedule(level, location.lineageFactionId().toString(), culprit);
        }

        Alien.LOGGER.info(
            "Nuke: aberrant hive {} caught in the blast - {} loaded members killed, location removed",
            location.id().value(),
            killed
        );
    }

    /**
     * Kills the members that were actually HOME, and only those.
     * <p>
     * Anything of this hive's standing outside the territory when the warhead lands survives - a specimen someone
     * captured and dragged off, penned somewhere, or eventually sat in a cryotube should not die because a hive it can
     * no longer see was destroyed. It does lose its location, but that falls out for free: {@code LocationDeathHandler}
     * drops the per-location faction, so every survivor keeps LINEAGE membership and is re-homed to another location of
     * that lineage, or drifts as a free agent if there is none left.
     * <p>
     * Kills rather than discards, so loot tables fire - the hive is destroyed, not deleted, and a player who nuked one
     * should be able to walk into the crater and pick up what was in it.
     */
    private static int killMembersInside(ServerLevel level, HiveLocation location) {
        var killed = 0;

        for (var members : location.loadedMembersByType().values()) {
            for (var memberId : new ArrayList<>(members)) {
                if (!(level.getEntity(memberId) instanceof LivingEntity member) || !member.isAlive()) {
                    continue;
                }

                if (!HiveFootprint.contains(location, member.getX(), member.getZ())) {
                    continue;
                }

                member.hurt(member.damageSources().explosion(null, null), Float.MAX_VALUE);
                killed++;
            }
        }

        return killed;
    }

    /**
     * Half the jelly, and that is the price of the shortcut.
     * <p>
     * Vats outside the blast survive the detonation as blocks, and their contents survive with them - but only half of
     * it. Nuking a hive is the fast way to empty one, so it pays worse than taking it apart: you get half the treasure.
     * Vats INSIDE the blast need no handling at all; the explosion destroys those blocks and everything in them.
     */
    private static void halveSurvivingVats(ServerLevel level, HiveLocation location) {
        var built = new java.util.HashSet<>(location.structureRoleByChunk().keySet());
        built.addAll(location.structurePieceByChunk().keySet());

        for (var chunkPos : built) {
            if (!level.isLoaded(chunkPos.getWorldPosition())) {
                continue;
            }

            for (var blockEntity : level.getChunk(chunkPos.x, chunkPos.z).getBlockEntities().values()) {
                if (blockEntity instanceof JellyVatBlockEntity vat && vat.getFillLevel() > 0) {
                    vat.setFillLevel(vat.getFillLevel() / 2);
                }
            }
        }
    }
}
