package com.alien.common.gameplay.hive.convoy;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.config.HiveConfig;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.faction.LocationMembership;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.RaidWaveProfileRegistry;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.entity.v1.EntityReserves;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.MobSpawnType;

import java.util.List;

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

        // ⭐⭐⭐ THE FOUNDER QUEEN MUST BE SPAWNED, NOT BANKED - do this BEFORE the rest goes to reserves.
        // A founder convoy is the ONE reinforcement that carries a queen. AbstractSpreadAttempt deliberately puts
        // her in the COMPOSITION rather than draining her from reserves, because queens are unique identity
        // entities that reserves cannot hold (see HiveIdentityReserveUnloadHandler: "never virtualize them").
        // Pouring the whole composition in regardless undid exactly that: she arrived as a NUMBER in the reserve
        // and never became an entity, so the daughter got a core claim and surface vents and then stopped dead -
        // no queen, no laying, no growth, forever.
        materializeFounderQueens(server, destinationLocation, reinforcement.composition());

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

        // AN EMPRESS NEVER ARRIVES. The flag is still set at dispatch and still persisted, so in-flight convoys in
        // older saves keep decoding, but there is deliberately nothing to do with it: when her seat is evacuated she
        // is EXILED there rather than carried (see EmpressExileService), which replaced the respawn this used to
        // promise. The old line claimed "Phase 10 work" and Phase 10 never came - it was superseded, not deferred.

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

    /**
     * Spawns any QUEENS-tagged member of an arriving composition as a REAL ENTITY and binds her to the destination,
     * removing her from the composition so the pour that follows cannot bank her.
     * <p>
     * ⚠ ORDER MATTERS: this runs BEFORE {@code addCompositionToLocation}. Everything else in the convoy - drones,
     * runners, the escort - is fungible and belongs in reserves exactly as before; only the royal is lifted out.
     * </p>
     * <p>
     * ⚠ IF THE LOCATION ALREADY HAS A LIVING FOUNDER she is NOT overwritten - a second royal arriving at a hive that
     * already has one joins as an ordinary member. Reassigning `founderId` would orphan the sitting queen from the hive
     * she founded.
     * </p>
     * <p>
     * ⚠ IF THE SPAWN FAILS the entry is LEFT IN the composition, so she still reaches the reserve rather than being
     * silently destroyed. That is the old (broken) outcome, but it is strictly better than deleting her.
     * </p>
     */
    private static void materializeFounderQueens(
        MinecraftServer server,
        HiveLocation destinationLocation,
        EntityReserves composition
    ) {
        var level = server.getLevel(destinationLocation.dimension());

        if (level == null) {
            return;
        }

        for (var entityType : List.copyOf(composition.getAvailableEntityTypes())) {
            if (!entityType.is(AlienEntityTypeTags.QUEENS) || composition.getCount(entityType) <= 0) {
                continue;
            }

            var spawnPos = destinationLocation.centerPos();
            var spawned = entityType.spawn(level, spawnPos, MobSpawnType.STRUCTURE);

            if (!(spawned instanceof Queen queen)) {
                if (spawned != null) {
                    spawned.discard();
                }
                Alien.LOGGER.warn(
                    "Hive: founder convoy carried {} but it did not spawn as a Queen at {} - leaving it in the "
                        + "composition so it is not lost",
                    entityType,
                    spawnPos
                );
                continue;
            }

            // ⚠⚠ MUST GO THROUGH add(), NOT getBackingMap(). That getter returns Collections.unmodifiableMap, so
            // the put/remove this used to do threw UnsupportedOperationException and killed the server tick
            // loop EVERY TIME A FOUNDER CONVOY ARRIVED. The comment that used to sit here claimed the map was
            // directly editable; it never was.
            //
            // add() takes a negative count and clamps at zero, and getAvailableEntityTypes() already skips
            // entries at zero - so decrementing is all that was ever needed, and dropping the key was
            // pointless as well as impossible.
            composition.add(entityType, -1);

            queen.setPersistenceRequired();
            queen.getMoltingManager().skipToFullMaturity();

            // Only claim the founder seat if it is genuinely vacant.
            if (destinationLocation.founderId() == null) {
                destinationLocation.setFounderId(queen.getUUID());
            }

            LocationMembership.join(destinationLocation, queen);

            Alien.LOGGER.info(
                "Hive: founder queen {} materialized at location {} ({}) - daughter hive is now alive",
                queen.getUUID(),
                destinationLocation.id(),
                spawnPos
            );
        }
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
