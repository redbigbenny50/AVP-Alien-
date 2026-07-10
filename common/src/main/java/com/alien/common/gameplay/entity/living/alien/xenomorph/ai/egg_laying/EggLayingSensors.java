package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg_laying;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.entity.living.alien.ovomorph.Ovomorph;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.spawning.HiveLocationSpawnGate;
import com.alien.common.gameplay.hive.structure.HiveChamberSlots;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.sensor.Sensor;
import com.just.ai.goap.sensor.Sensors;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public class EggLayingSensors {

    public static final StateKey.Sensed<Boolean> CAN_LAY_EGG = StateKey.sensed("can_lay_egg");

    private static final double MIN_HORIZONTAL_OVOMORPH_SPACING_BLOCKS = 2.0;

    public static <T extends EggLayer> Sensor.Mono<T, Boolean> canLayEgg() {
        return Sensors.map(
            CAN_LAY_EGG,
            eggLayer -> {
                if (!eggLayer.isEggLayCooldownReady()) {
                    return false;
                }

                if (
                    !eggLayer.asEntity().isAlive()
                        || !eggLayer.hasOvipositor()
                        || !AlienVariantTypes.getFor(eggLayer.getVariant()).canReproduce()
                ) {
                    return false;
                }

                var location = HiveLocationSpawnGate.locationContaining(eggLayer.asEntity().level(), eggLayer.asEntity().blockPosition());
                if (location == null) {
                    return false;
                }
                if (!hasOvomorphCapacity(eggLayer, location)) {
                    return false;
                }

                return noEggsNearby(eggLayer);
            }
        );
    }

    /** Eggs the queen keeps producing INTO THE RESERVE once the hive is physically saturated. */
    public static final int RESERVE_EGG_CAP = 100;

    /** Physical eggs allowed around the queen herself, on top of the nursery beds. */
    public static final int QUEEN_RING_EGG_CAP = 10;

    private static boolean hasOvomorphCapacity(EggLayer eggLayer, HiveLocation location) {
        // She lays while there is room for a PHYSICAL egg, or - once the world is saturated - while the reserve
        // egg bank is below its cap (the lay action diverts those into the reserve instead of spawning).
        return hasPhysicalOvomorphCapacity(eggLayer, location) || hasReserveOvomorphCapacity(eggLayer, location);
    }

    /**
     * Whether another PHYSICAL egg fits. The capacity is dynamic - what the hive actually built: the queen's own ring
     * plus a bedful per existing egg chamber (a full 5-chamber hive holds 10 + 5x6 = 40; a 3-chamber hive 28). Used by
     * the lay action to decide between spawning an egg and banking a reserve one.
     */
    public static boolean hasPhysicalOvomorphCapacity(EggLayer eggLayer, HiveLocation location) {
        int chambers = 0;
        for (var pieceId : location.structurePieceByChunk().values()) {
            if (pieceId.contains("chamber_egg")) {
                chambers++;
            }
        }
        int cap = QUEEN_RING_EGG_CAP + HiveChamberSlots.EGG_BEDS_PER_CHAMBER * chambers;
        return countSameVariantOvomorphs(eggLayer.asEntity().level(), location, eggLayer.getVariant()) < cap;
    }

    /** Whether the reserve egg bank is below {@link #RESERVE_EGG_CAP}. */
    public static boolean hasReserveOvomorphCapacity(EggLayer eggLayer, HiveLocation location) {
        var variant = location.lineageVariantOrNull();
        if (variant == null) {
            variant = eggLayer.getVariant();
        }
        var type = Ovomorph.getType(variant, false);
        return type != null && location.localReserves().getCount(type) < RESERVE_EGG_CAP;
    }

    private static int countSameVariantOvomorphs(Level level, HiveLocation location, AlienVariant fallbackVariant) {
        if (location.claimedChunks().isEmpty()) {
            return 0;
        }

        var variant = location.lineageVariantOrNull();
        if (variant == null) {
            variant = fallbackVariant;
        }

        var normalType = Ovomorph.getType(variant, false);
        var royalType = Ovomorph.getType(variant, true);
        if (normalType == null && royalType == null) {
            return 0;
        }

        var bounds = claimedChunkBounds(level, location);
        return level.getEntitiesOfClass(
            Ovomorph.class,
            bounds,
            entity -> isSameVariantOvomorph(entity.getType(), normalType, royalType)
                && location.claimedChunks().contains(new ChunkPos(entity.blockPosition()))
        ).size();
    }

    private static AABB claimedChunkBounds(Level level, HiveLocation location) {
        var minX = Integer.MAX_VALUE;
        var minZ = Integer.MAX_VALUE;
        var maxX = Integer.MIN_VALUE;
        var maxZ = Integer.MIN_VALUE;

        for (var chunk : location.claimedChunks()) {
            var chunkMinX = chunk.x * 16;
            var chunkMinZ = chunk.z * 16;
            minX = Math.min(minX, chunkMinX);
            minZ = Math.min(minZ, chunkMinZ);
            maxX = Math.max(maxX, chunkMinX + 15);
            maxZ = Math.max(maxZ, chunkMinZ + 15);
        }

        return new AABB(minX, level.getMinBuildHeight(), minZ, maxX + 1, level.getMaxBuildHeight(), maxZ + 1);
    }

    private static boolean isSameVariantOvomorph(
        EntityType<?> type,
        EntityType<? extends Ovomorph> normalType,
        EntityType<? extends Ovomorph> royalType
    ) {
        return type == normalType || type == royalType;
    }

    private static boolean noEggsNearby(EggLayer eggLayer) {
        var eggPos = eggLayer.getEggLayingPosition();
        var halfSize = MIN_HORIZONTAL_OVOMORPH_SPACING_BLOCKS;

        var searchBox = new AABB(
            eggPos.x - halfSize,
            eggPos.y - eggLayer.asEntity().level().dimensionType().height(),
            eggPos.z - halfSize,
            eggPos.x + halfSize,
            eggPos.y + 5,
            eggPos.z + halfSize
        );

        return eggLayer.asEntity()
            .level()
            .getEntitiesOfClass(
                Ovomorph.class,
                searchBox,
                entity -> entity.getType().is(AlienEntityTypeTags.OVOMORPHS) && entity.isRooted.get()
            )
            .isEmpty();
    }

    private EggLayingSensors() {
        throw new UnsupportedOperationException();
    }
}
