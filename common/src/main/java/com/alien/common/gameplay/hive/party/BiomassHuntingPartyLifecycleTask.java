package com.alien.common.gameplay.hive.party;

import com.alien.Alien;
import com.alien.common.gameplay.hive.config.HiveConfig;
import com.alien.common.gameplay.hive.location.HiveLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Per-tick (piggybacking {@code HiveLocationLoadedTickTask}'s 20-tick cadence) resolution for
 * {@link HiveParty.BiomassHunting} parties. No day/night cycle like {@link SurfacePartyLifecycleTask} — this party runs
 * for a fixed {@code config.biomassHuntingPartyDurationTicks()} active duration (tracked from
 * {@link HiveParty#dispatchedTick()}), then resolves: surviving members are instantly teleported to the nearest known
 * hive vent (the "vents act as fast travel points back to hive" design point) before refunding to reserves and being
 * discarded. Party-specific targeting eligibility (the biomass-hunting THREAT_2 bypass in
 * {@code AlienPredicates#isActiveBiomassHuntingPartyMember}) is cleared alongside membership.
 */
public final class BiomassHuntingPartyLifecycleTask {

    private BiomassHuntingPartyLifecycleTask() {}

    public static void run(MinecraftServer server, HiveLocation location, HiveConfig config) {
        if (location.parties().isEmpty()) {
            return;
        }

        var serverLevel = server.getLevel(location.dimension());
        if (serverLevel == null) {
            return;
        }

        var currentTick = serverLevel.getGameTime();

        var iterator = location.parties().iterator();
        while (iterator.hasNext()) {
            var party = iterator.next();
            if (!(party instanceof HiveParty.BiomassHunting biomassHunting)) {
                continue;
            }

            if (currentTick - biomassHunting.dispatchedTick() < config.biomassHuntingPartyDurationTicks()) {
                continue;
            }

            resolve(serverLevel, location, biomassHunting, config);
            iterator.remove();
        }
    }

    private static void resolve(
        ServerLevel serverLevel,
        HiveLocation location,
        HiveParty.BiomassHunting party,
        HiveConfig config
    ) {
        // Oct 5 audit: one shared return - see PartyReturn for the three bugs the four copies carried.
        PartyReturn.returnHome(serverLevel, location, party, nearestVent(serverLevel, location, config));

        Alien.LOGGER.info("Hive: biomass hunting party resolved (duration elapsed) for location {}", location.id());
    }

    private static BlockPos nearestVent(ServerLevel serverLevel, HiveLocation location, HiveConfig config) {
        // Prefer a near-surface vent (matches the design's "vents" as the fast-travel network); fall back to any
        // known vent if the hive somehow has none near the surface anymore.
        // Home is any door onto the world - surface or frontier. No fallback to interior ducts: survivors cannot
        // walk home to a vent buried in the rock.
        var surfaceVents = PartyVentUtil.findPartyVents(serverLevel, location);
        if (!surfaceVents.isEmpty()) {
            return closestTo(surfaceVents, location.centerPos());
        }
        return null;
    }

    private static BlockPos closestTo(java.util.List<BlockPos> candidates, BlockPos reference) {
        BlockPos closest = null;
        var closestDistSqr = Double.MAX_VALUE;
        for (var candidate : candidates) {
            var distSqr = candidate.distSqr(reference);
            if (distSqr < closestDistSqr) {
                closestDistSqr = distSqr;
                closest = candidate;
            }
        }
        return closest;
    }
}
