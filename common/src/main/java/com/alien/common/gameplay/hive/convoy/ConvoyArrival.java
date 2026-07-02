package com.alien.common.gameplay.hive.convoy;

import com.alien.Alien;
import com.alien.common.gameplay.hive.config.HiveConfig;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.RaidWaveProfileRegistry;
import com.blib.api.common.entity.v1.EntityReserves;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/**
 * Detects when a convoy has reached its destination and applies the arrival effect.
 * <p>
 * For {@link Convoy.Reinforcement}: pours the entire {@code composition} into the destination location's
 * {@link com.alien.common.gameplay.hive.location.HiveLocationReserves}.
 * <p>
 * {@link #checkArrival} returns {@code true} when the arrival fires; the caller should remove the convoy from the
 * lineage's convoy list at that point.
 */
public final class ConvoyArrival {

    private ConvoyArrival() {}

    public static boolean checkArrival(MinecraftServer server, Convoy convoy, LineageFactionData lineage, HiveConfig config) {
        var distance = ConvoyTravel.distanceToTarget(convoy);
        if (distance > config.arrivalRadiusBlocks()) {
            return false;
        }

        if (convoy instanceof Convoy.Reinforcement reinforcement) {
            arriveReinforcement(server, reinforcement);
            return true;
        }

        if (convoy instanceof Convoy.Migration migration) {
            arriveMigration(server, migration, lineage);
            return true;
        }

        if (convoy instanceof Convoy.Raid raid) {
            if (raid.returningHome()) {
                arriveReturningRaid(server, raid, lineage);
                return true;
            }
            return arriveRaid(server, raid, lineage);
        }

        Alien.LOGGER.warn("Convoy {} arrived but has no arrival handler for type {}", convoy.id(), convoy.getClass().getName());
        return true;
    }

    private static void arriveReinforcement(MinecraftServer server, Convoy.Reinforcement reinforcement) {
        ConvoyMemberTracker.recallMaterializedMembers(server, reinforcement);
        var destinationLocation = HiveLocationRegistry.INSTANCE.get(reinforcement.destinationLocationId());

        if (destinationLocation == null) {
            Alien.LOGGER.info(
                "Convoy {} arrived but destination location {} is gone — refunding composition to source location",
                reinforcement.id(),
                reinforcement.destinationLocationId()
            );
            refundToLocation(
                HiveLocationRegistry.INSTANCE.get(reinforcement.sourceLocationId()),
                reinforcement.composition(),
                reinforcement.id().toString()
            );
            return;
        }

        addCompositionToLocation(destinationLocation, reinforcement.composition());

        Alien.LOGGER.info(
            "Convoy {} arrived at location {} (lineage {}); composition poured into reserves",
            reinforcement.id(),
            reinforcement.destinationLocationId(),
            reinforcement.lineageFactionId()
        );
    }

    private static void arriveMigration(MinecraftServer server, Convoy.Migration migration, LineageFactionData lineage) {
        ConvoyMemberTracker.recallMaterializedMembers(server, migration);
        var destination = HiveLocationRegistry.INSTANCE.get(migration.destinationLocationId());

        if (destination == null) {
            Alien.LOGGER.info(
                "Migration {} arrived but destination location {} is gone — refunding composition to nearest surviving location",
                migration.id(),
                migration.destinationLocationId()
            );
            refundToLocation(nearestAliveLocation(lineage, migration), migration.composition(), migration.id().toString());
            return;
        }

        addCompositionToLocation(destination, migration.composition());

        // Add biomass payload (capped at biomass cap by the location's setter).
        if (migration.biomassPayload() > 0) {
            destination.setBiomass(destination.biomass() + migration.biomassPayload());
        }

        // If carrying empress: respawn her at the destination's center. For Phase 8b, we just log it; Phase 10's
        // empress emergence ritual + entity-respawn machinery will be wired together with this.
        if (migration.carriesEmpress()) {
            Alien.LOGGER.info(
                "Migration {} arrived carrying empress — empress respawn at destination is Phase 10 work",
                migration.id()
            );
        }

        Alien.LOGGER.info(
            "Migration {} arrived at location {} (lineage {}); composition + {} biomass payload delivered",
            migration.id(),
            migration.destinationLocationId(),
            migration.lineageFactionId(),
            migration.biomassPayload()
        );
    }

    /**
     * Raid arrival: the raid has reached the player's last-known position. Spawns one wave at the convoy's current
     * position. The raid convoy itself remains alive so despawned raiders can return to its composition.
     */
    private static boolean arriveRaid(MinecraftServer server, Convoy.Raid raid, LineageFactionData lineage) {
        var serverLevel = server.getLevel(raid.dimension());
        if (serverLevel == null) {
            Alien.LOGGER.info(
                "Raid {} reached its target position but destination dimension {} is unloaded — holding raid in flight",
                raid.id(),
                raid.dimension().location()
            );
            return false;
        }

        var spawnPos = new BlockPos(
            (int) Math.round(raid.currentPos().x),
            (int) Math.round(raid.currentPos().y),
            (int) Math.round(raid.currentPos().z)
        );

        if (!raid.materializedMembers().isEmpty()) {
            return false;
        }
        if (raid.lossConfirmed()) {
            return false;
        }

        var targetPlayer = server.getPlayerList().getPlayer(raid.targetPlayerId());
        var breakStartedTick = raid.waveBreakStartedTick();
        var compositionCount = raid.composition().getCount();
        var nextWaveIndex = raid.nextWaveIndex();
        var spawnedCount = ConvoyMaterialization.spawnNextRaidWave(
            serverLevel,
            raid,
            RaidWaveProfileRegistry.forVariant(lineage.variant()),
            spawnPos,
            targetPlayer,
            server.overworld().getGameTime()
        );
        if (
            spawnedCount > 0
                || raid.waveBreakStartedTick() != breakStartedTick
                || raid.composition().getCount() != compositionCount
                || raid.nextWaveIndex() != nextWaveIndex
        ) {
            lineage.markDirty();
        }
        if (spawnedCount <= 0) {
            return false;
        }

        Alien.LOGGER.info(
            "Raid {} arrived at {} — spawned wave {} with {} attackers targeting player {}",
            raid.id(),
            spawnPos,
            raid.nextWaveIndex(),
            spawnedCount,
            raid.targetPlayerId()
        );
        return false;
    }

    private static void arriveReturningRaid(MinecraftServer server, Convoy.Raid raid, LineageFactionData lineage) {
        ConvoyMemberTracker.recallMaterializedMembers(server, raid);

        var destination = raid.returnLocationId() == null ? null : HiveLocationRegistry.INSTANCE.get(raid.returnLocationId());
        if (destination == null || !destination.isAlive()) {
            destination = nearestAliveLocation(lineage, raid);
        }

        if (destination == null) {
            Alien.LOGGER.info(
                "Raid {} completed and returned, but no live lineage location remains — disbanding {} member(s)",
                raid.id(),
                raid.composition().getCount()
            );
            return;
        }

        addCompositionToLocation(destination, raid.composition());
        Alien.LOGGER.info(
            "Raid {} returned home to location {} with {} member(s)",
            raid.id(),
            destination.id(),
            raid.composition().getCount()
        );
    }

    private static void addCompositionToLocation(HiveLocation location, EntityReserves composition) {
        for (var entityType : composition.getAvailableEntityTypes()) {
            var count = composition.getCount(entityType);
            if (count <= 0) {
                continue;
            }
            location.localReserves().tryAdd(entityType, count);
        }
    }

    private static void refundToLocation(HiveLocation location, EntityReserves composition, String convoyId) {
        if (location == null || !location.isAlive()) {
            Alien.LOGGER.warn(
                "Hive: convoy {} could not refund {} member(s) because no live hive location was available",
                convoyId,
                composition.getCount()
            );
            return;
        }
        addCompositionToLocation(location, composition);
    }

    private static HiveLocation nearestAliveLocation(LineageFactionData lineage, Convoy convoy) {
        HiveLocation best = null;
        var bestDistance = Double.MAX_VALUE;
        for (var location : lineage.locationsById().values()) {
            if (!location.isAlive()) {
                continue;
            }
            var dx = location.centerPos().getX() - convoy.currentPos().x;
            var dz = location.centerPos().getZ() - convoy.currentPos().z;
            var distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = location;
            }
        }
        return best;
    }
}
