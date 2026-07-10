package com.alien.common.gameplay.hive.spawning;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.ovomorph.Ovomorph;
import com.alien.common.gameplay.hive.faction.LocationMembership;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.structure.HiveChamberSlots;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Stage 4 of the egg logistics: the RESERVE egg bank restocks the physical nurseries. When free chamber beds exist and
 * no loose egg is already awaiting a hauler, one banked egg materializes at the queen (or the hive core when she is
 * absent - a raided, queenless hive can still restock from its savings) and the normal pickup/haul pipeline carries it
 * to a bed, visibly. One egg per growth cycle, and the bank keeps a floor so purchases stay funded.
 */
public final class EggRestockTask {

    /** Banked eggs kept back for unit purchases - restocking never drains below this. */
    private static final int RESERVE_EGG_FLOOR = 10;

    /** How far around the anchor a loose (unrooted, unhauled) egg suppresses another materialization. */
    private static final double PENDING_EGG_RADIUS = 16.0;

    private EggRestockTask() {}

    /** Called on the growth cadence. Materializes at most one banked egg per call. */
    public static void run(ServerLevel level, HiveLocation location) {
        var variant = location.lineageVariantOrNull();
        if (variant == null) {
            return;
        }
        var eggType = Ovomorph.getType(variant, false);
        if (eggType == null || location.localReserves().getCount(eggType) <= RESERVE_EGG_FLOOR) {
            return;
        }
        if (!hasFreeChamberBed(level, location)) {
            return;
        }

        var anchor = anchorPos(level, location);
        if (anchor == null) {
            return;
        }

        // One in flight at a time: a loose egg near the anchor means a hauler is (or will be) on it already.
        var pendingBox = new AABB(anchor).inflate(PENDING_EGG_RADIUS);
        boolean pending = !level.getEntitiesOfClass(
            Ovomorph.class,
            pendingBox,
            e -> e.getType() == eggType && !e.isRooted.get() && !e.isPassenger() && e.isAlive()
        ).isEmpty();
        if (pending) {
            return;
        }

        if (!location.localReserves().trySpawn(eggType)) {
            return;
        }
        var egg = eggType.create(level);
        if (egg == null) {
            return; // reserve count already decremented; vanishingly rare, self-corrects via banking
        }
        egg.moveTo(
            anchor.getX() + 0.5,
            anchor.getY(),
            anchor.getZ() + 0.5,
            level.random.nextFloat() * 360.0F,
            0.0F
        );
        if (!level.addFreshEntity(egg)) {
            location.localReserves().tryAdd(eggType, 1); // put it back
            return;
        }
        LocationMembership.join(location, egg);
        level.playSound(null, egg, AlienSoundEvents.ENTITY_OVOMORPH_LAID.get(), SoundSource.HOSTILE, 1.0F, 1.1F);
        Alien.LOGGER.info(
            "Hive at {}: materialized a banked egg for nursery restock ({} left in reserve).",
            location.centerPos(),
            location.localReserves().getCount(eggType)
        );
    }

    /** Whether any loaded egg chamber has a bed without a rooted ovomorph on it. */
    private static boolean hasFreeChamberBed(ServerLevel level, HiveLocation location) {
        for (var entry : location.structurePieceByChunk().entrySet()) {
            if (!entry.getValue().contains("chamber_egg")) {
                continue;
            }
            var chamber = entry.getKey();
            if (!level.isLoaded(chamber.getWorldPosition())) {
                continue;
            }
            for (var bed : HiveChamberSlots.eggBedSlots(level, location, chamber)) {
                var box = new AABB(
                    bed.getCenter().x - 1.0,
                    bed.getY() - 1.0,
                    bed.getCenter().z - 1.0,
                    bed.getCenter().x + 1.0,
                    bed.getY() + 2.0,
                    bed.getCenter().z + 1.0
                );
                boolean occupied = !level.getEntitiesOfClass(
                    Ovomorph.class,
                    box,
                    e -> e.isRooted.get() && e.isAlive()
                ).isEmpty();
                if (!occupied) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The queen's position when one is loaded (eggs come from her), else the hive core. */
    @Nullable
    private static BlockPos anchorPos(ServerLevel level, HiveLocation location) {
        for (var entry : location.loadedMembersByType().entrySet()) {
            if (!entry.getKey().is(AlienEntityTypeTags.QUEENS)) {
                continue;
            }
            for (var uuid : entry.getValue()) {
                var queen = level.getEntity(uuid);
                if (queen != null && queen.isAlive()) {
                    return queen.blockPosition();
                }
            }
        }
        var center = location.centerPos();
        return level.isLoaded(center) ? new BlockPos(center.getX(), location.hiveFloorY() + 1, center.getZ()) : null;
    }
}
