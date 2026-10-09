package com.alien.common.gameplay.hive.party;

import com.alien.Alien;
import com.alien.common.gameplay.hive.config.HiveConfig;
import com.alien.common.gameplay.hive.location.HiveLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Per-tick (piggybacking {@code HiveLocationLoadedTickTask}'s 20-tick cadence) resolution for
 * {@link HiveParty.HostHunt} parties. No day/night cycle like {@link SurfacePartyLifecycleTask} — this party runs for a
 * fixed {@code config.hostHuntPartyDurationTicks()} active duration (tracked from {@link HiveParty#dispatchedTick()}),
 * then resolves: surviving members are instantly teleported to the nearest known hive vent (the "vents act as fast
 * travel points back to hive" design point) before refunding to reserves and being discarded. Party-specific targeting
 * eligibility (the biomass-hunting THREAT_2 bypass in {@code AlienPredicates#isActiveBiomassHuntingPartyMember}) is
 * cleared alongside membership.
 */
public final class HostHuntPartyLifecycleTask {

    private HostHuntPartyLifecycleTask() {}

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
            if (!(party instanceof HiveParty.HostHunt biomassHunting)) {
                continue;
            }

            if (currentTick - biomassHunting.dispatchedTick() < config.hostHuntPartyDurationTicks()) {
                continue;
            }

            resolve(serverLevel, location, biomassHunting, config);
            iterator.remove();
        }
    }

    private static void resolve(
        ServerLevel serverLevel,
        HiveLocation location,
        HiveParty.HostHunt party,
        HiveConfig config
    ) {
        // Oct 5 audit: one shared return - see PartyReturn for the three bugs the four copies carried.
        PartyReturn.returnHome(serverLevel, location, party, nearestVent(serverLevel, location, config));

        Alien.LOGGER.info("Hive: host hunt party resolved (duration elapsed) for location {}", location.id());
    }

    /**
     * Host hunts come home through the front door. SURFACE vents only, and no fallback to "any vent" - falling back to
     * an interior duct would send survivors home to a vent buried in the rock that they cannot reach.
     */
    private static BlockPos nearestVent(ServerLevel serverLevel, HiveLocation location, HiveConfig config) {
        var surfaceVents = PartyVentUtil.findSurfaceVents(serverLevel, location);
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
