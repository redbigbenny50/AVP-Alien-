package com.alien.common.gameplay.hive.party;

import com.alien.Alien;
import com.alien.common.gameplay.hive.config.HiveConfig;
import com.alien.common.gameplay.hive.location.HiveLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;

/**
 * Per-tick (piggybacking {@code HiveLocationLoadedTickTask}'s 20-tick cadence) resolution for
 * {@link HiveParty.HostHunt} parties. No day/night cycle like {@link SurfacePartyLifecycleTask} — this party runs
 * for a fixed {@code config.hostHuntPartyDurationTicks()} active duration (tracked from
 * {@link HiveParty#dispatchedTick()}), then resolves: surviving members are instantly teleported to the nearest known
 * hive vent (the "vents act as fast travel points back to hive" design point) before refunding to reserves and being
 * discarded. Party-specific targeting eligibility (the biomass-hunting THREAT_2 bypass in
 * {@code AlienPredicates#isActiveBiomassHuntingPartyMember}) is cleared alongside membership.
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
        var homeVent = nearestVent(serverLevel, location, config);

        for (var entry : new ArrayList<>(party.materializedMembers().entrySet())) {
            var entity = serverLevel.getEntity(entry.getKey());
            party.untrackMaterializedMember(entry.getKey());
            if (entity == null || !entity.isAlive()) {
                // Lost in the field — nothing to refund.
                continue;
            }

            if (homeVent != null) {
                entity.teleportTo(homeVent.getX() + 0.5, homeVent.getY(), homeVent.getZ() + 0.5);
            }

            location.localReserves().addReturningMember(entry.getValue(), 1);
            if (entity instanceof com.alien.common.gameplay.entity.living.alien.Alien alien) {
                alien.clearPartyMembership();
            }
            // EGG DUTY: a carrier is refunded but NEVER discarded - discarding it mid-haul vanished the
            // worker and dropped its egg. It stays alive to finish the delivery.
            if (EggDutyGuard.isOnEggDuty(entity)) {
                continue;
            }
            entity.discard();
        }

        // Any never-materialized remainder in composition also refunds.
        for (var type : new ArrayList<>(party.composition().getAvailableEntityTypes())) {
            var count = party.composition().getCount(type);
            if (count <= 0) {
                continue;
            }
            location.localReserves().addReturningMember(type, count);
            party.composition().add(type, -count);
        }

        Alien.LOGGER.info("Hive: host hunt party resolved (duration elapsed) for location {}", location.id());
    }

    private static BlockPos nearestVent(ServerLevel serverLevel, HiveLocation location, HiveConfig config) {
        // Prefer a near-surface vent (matches the design's "vents" as the fast-travel network); fall back to any
        // known vent if the hive somehow has none near the surface anymore.
        var surfaceVents = PartyVentUtil.findSurfaceVents(serverLevel, location, config.surfacePartySurfaceBandBlocks());
        if (!surfaceVents.isEmpty()) {
            return closestTo(surfaceVents, location.centerPos());
        }
        var allVents = new ArrayList<>(location.ventManager().allVents());
        if (!allVents.isEmpty()) {
            return closestTo(allVents, location.centerPos());
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