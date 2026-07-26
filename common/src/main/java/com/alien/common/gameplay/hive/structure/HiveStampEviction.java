package com.alien.common.gameplay.hive.structure;

import com.alien.Alien;
import com.alien.common.gameplay.hive.location.HiveLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/**
 * When a hive piece stamps its footprint, any hostile mobs standing in the slab band are removed - construction changes
 * blocks, not entities, so without this the cave mobs that lived in the carved-over terrain would survive inside the
 * finished halls (and dark hive interiors would keep them alive indefinitely). The natural-spawn suppression mixin
 * prevents NEW spawns; this handles what was already there. The hive's own entities are exempt.
 */
public final class HiveStampEviction {

    private static final int SLAB_BAND_BLOCKS = 16;

    private HiveStampEviction() {}

    public static void evict(ServerLevel level, HiveLocation location, Iterable<ChunkPos> footprint) {
        int floorY = location.hiveFloorY();
        int evicted = 0;
        for (var chunkPos : footprint) {
            if (!level.isLoaded(chunkPos.getWorldPosition())) {
                continue;
            }
            var box = new AABB(
                chunkPos.getMinBlockX(),
                floorY - 1,
                chunkPos.getMinBlockZ(),
                chunkPos.getMaxBlockX() + 1,
                floorY + SLAB_BAND_BLOCKS + 1,
                chunkPos.getMaxBlockZ() + 1
            );
            for (
                var monster : level.getEntitiesOfClass(
                    Monster.class,
                    box,
                    m -> m.isAlive() && !Alien.MOD_ID.equals(EntityType.getKey(m.getType()).getNamespace())
                )
            ) {
                monster.discard();
                evicted++;
            }
        }
        if (evicted > 0) {
            Alien.LOGGER.info(
                "Hive at {}: evicted {} hostile mob(s) from a stamped footprint.",
                location.centerPos(),
                evicted
            );
        }
    }
}
