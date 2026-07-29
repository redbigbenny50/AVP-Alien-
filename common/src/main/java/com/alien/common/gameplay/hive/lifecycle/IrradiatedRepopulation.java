package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.spawning.ReserveSpawnUtil;
import com.alien.common.registry.init.block.IrradiatedAlienResinBlocks;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;

import java.util.ArrayList;

/**
 * Filling a converted hive back up: floor, then queen, then everyone else.
 * <h2>Order is not cosmetic</h2> The nuke digs {@code withRadius(DOWN, 32)}. If the crater took the core, there is no
 * ground under the throne, so the FLOOR GOES FIRST and it goes in free - [stated] "just have the entire core floor
 * spawn in for the sake of solid ground". Her, then the retinue around her. Get that order wrong and a queen
 * materializes into a thirty-two block hole. Everything ABOVE the floor is still rebuilt the slow way by the crew, so
 * there is plenty left to watch.
 * <h2>Who wears the crown</h2> [stated] "it would use the reserves royal replacement bank first; if one doesnt exist
 * one of the praetorians/crushers is selected." A banked queen is spawned directly. Failing that a praetorian or
 * crusher is spawned and left for {@code QueenlessMaturationTask}, which already promotes exactly those two castes up
 * the queen track - rather than duplicating its growth logic here. NOTE that task charges royal jelly and holds while a
 * rescue campaign is live, neither of which suits this strain; both are the economy slice's problem, not this one's.
 */
public final class IrradiatedRepopulation {

    /** Radius of the free floor, in blocks either side of centre. One chunk's worth, centred on the throne. */
    private static final int CORE_FLOOR_RADIUS = 8;

    private IrradiatedRepopulation() {
        throw new UnsupportedOperationException();
    }

    public static void repopulate(ServerLevel level, HiveLocation location) {
        layCoreFloor(level, location);

        var queenSpawned = spawnRoyalty(level, location);
        var retinue = spawnRetinue(level, location);

        Alien.LOGGER.info(
            "Nuke: hive {} repopulated - royalty {}, retinue {}",
            location.id().value(),
            queenSpawned ? "spawned" : "none available",
            retinue
        );
    }

    /**
     * Solid ground under the throne, free of charge.
     * <p>
     * Only fills where there is nothing - a crater floor, not a resurfacing - so an intact core is left exactly as it
     * was and only the hole the blast took is made good.
     */
    private static void layCoreFloor(ServerLevel level, HiveLocation location) {
        var centre = location.centerPos();
        var floorY = location.hiveFloorY();
        var resin = IrradiatedAlienResinBlocks.IRRADIATED_RESIN.get().defaultBlockState();
        var cursor = new BlockPos.MutableBlockPos();
        var laid = 0;

        for (var dx = -CORE_FLOOR_RADIUS; dx <= CORE_FLOOR_RADIUS; dx++) {
            for (var dz = -CORE_FLOOR_RADIUS; dz <= CORE_FLOOR_RADIUS; dz++) {
                cursor.set(centre.getX() + dx, floorY, centre.getZ() + dz);

                if (level.getBlockState(cursor).isAir()) {
                    level.setBlockAndUpdate(cursor, resin);
                    laid++;
                }
            }
        }

        if (laid > 0) {
            Alien.LOGGER.info("Nuke: laid {} floor blocks under the throne of {}", laid, location.id().value());
        }
    }

    /** A banked queen if there is one, otherwise a praetorian or crusher for the crowning task to promote. */
    private static boolean spawnRoyalty(ServerLevel level, HiveLocation location) {
        var throne = throneOf(location);

        for (
            var casteTag : new java.util.ArrayList<>(
                java.util.List.of(
                    AlienEntityTypeTags.QUEENS,
                    AlienEntityTypeTags.PRAETORIANS,
                    AlienEntityTypeTags.CRUSHERS
                )
            )
        ) {
            for (var type : location.localReserves().getAvailableEntityTypes()) {
                if (type.is(casteTag) && spawnFromReserves(level, location, type, throne)) {
                    return true;
                }
            }
        }

        return false;
    }

    /** The rest of the household, up to the loaded cap, scattered around her. */
    private static int spawnRetinue(ServerLevel level, HiveLocation location) {
        var cap = HiveLocationRegistry.INSTANCE.config().hiveSpawnerMinimumLoadedXenomorphs();
        var throne = throneOf(location);
        var spawned = 0;

        var types = new ArrayList<>(location.localReserves().getAvailableEntityTypes());
        var progressed = true;

        while (progressed && countLoaded(location) + spawned < cap) {
            progressed = false;

            for (var type : types) {
                if (countLoaded(location) + spawned >= cap) {
                    break;
                }

                if (spawnFromReserves(level, location, type, throne)) {
                    spawned++;
                    progressed = true;
                }
            }
        }

        return spawned;
    }

    private static BlockPos throneOf(HiveLocation location) {
        return new BlockPos(location.centerPos().getX(), location.hiveFloorY() + 1, location.centerPos().getZ());
    }

    private static int countLoaded(HiveLocation location) {
        return location.loadedMembersByType().values().stream().mapToInt(java.util.Set::size).sum();
    }

    /** Mirrors CarveWorkers.materializeFromReserves: debit the bank, create, place, mark as reserve-born. */
    private static boolean spawnFromReserves(
        ServerLevel level,
        HiveLocation location,
        EntityType<?> type,
        BlockPos around
    ) {
        if (!location.localReserves().trySpawn(type)) {
            return false;
        }

        var entity = type.create(level);
        if (entity == null) {
            return false;
        }

        var x = around.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 6.0;
        var z = around.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 6.0;
        entity.moveTo(x, around.getY(), z, level.random.nextFloat() * 360.0F, 0.0F);

        if (entity instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(around), MobSpawnType.MOB_SUMMONED, null);
            mob.setPersistenceRequired();
        }

        level.addFreshEntityWithPassengers(entity);
        ReserveSpawnUtil.markSpawnedFromReserves(entity);
        return true;
    }
}
