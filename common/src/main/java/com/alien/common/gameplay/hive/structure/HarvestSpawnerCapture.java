package com.alien.common.gameplay.hive.structure;

import com.alien.Alien;
import com.alien.common.gameplay.hive.location.HiveLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;

import java.util.ArrayList;

/**
 * Pre-stamp spawner discovery for the harvest chamber: just BEFORE a hive piece stamps its footprint (which would
 * destroy anything inside it), the slab band of each footprint chunk is checked for vanilla mob spawners. Any found are
 * captured - removed from the terrain, their monster type resolved (zombie/skeleton kept; anything else becomes a
 * random one of the two) - and held on the location's persisted pending list until a harvest chamber with a free slot
 * exists to remake them. Scans the chunk's block-entity map, not blocks, so the cost is negligible.
 */
public final class HarvestSpawnerCapture {

    /** The slab band above the hive floor that gets scanned (matches the hive's interior height). */
    private static final int SLAB_BAND_BLOCKS = 16;

    private HarvestSpawnerCapture() {}

    /** Captures every spawner in the slab band of the given (about-to-be-stamped) chunks. */
    public static void captureBeforeStamp(ServerLevel level, HiveLocation location, Iterable<ChunkPos> footprint) {
        int floorY = location.hiveFloorY();
        for (var chunkPos : footprint) {
            if (!level.isLoaded(chunkPos.getWorldPosition())) {
                continue;
            }
            var chunk = level.getChunk(chunkPos.x, chunkPos.z);
            var spawnerPositions = new ArrayList<BlockPos>();
            for (var entry : chunk.getBlockEntities().entrySet()) {
                int y = entry.getKey().getY();
                if (y >= floorY && y <= floorY + SLAB_BAND_BLOCKS && entry.getValue() instanceof SpawnerBlockEntity) {
                    spawnerPositions.add(entry.getKey().immutable());
                }
            }
            for (var pos : spawnerPositions) {
                if (!(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawnerBe)) {
                    continue;
                }
                var type = resolveMonsterType(level, spawnerBe);
                location.pendingHarvestSpawners().add(type);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                Alien.LOGGER.info(
                    "Hive at {}: captured a mob spawner at {} (as {}) - pending a harvest chamber slot ({} pending).",
                    location.centerPos(),
                    pos,
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type),
                    location.pendingHarvestSpawners().size()
                );
            }
        }
    }

    /**
     * The spawner's monster, converted for the harvest farm: zombie and skeleton pass through; anything else (spider,
     * cave spider, silverfish...) becomes a random one of the two.
     */
    private static EntityType<?> resolveMonsterType(ServerLevel level, SpawnerBlockEntity spawnerBe) {
        var tag = spawnerBe.saveWithoutMetadata(level.registryAccess());
        var id = tag.getCompound("SpawnData").getCompound("entity").getString("id");
        if (id.isEmpty()) {
            var potentials = tag.getList("SpawnPotentials", Tag.TAG_COMPOUND);
            if (!potentials.isEmpty()) {
                id = potentials.getCompound(0).getCompound("data").getCompound("entity").getString("id");
            }
        }
        if ("minecraft:zombie".equals(id)) {
            return EntityType.ZOMBIE;
        }
        if ("minecraft:skeleton".equals(id)) {
            return EntityType.SKELETON;
        }
        return level.random.nextBoolean() ? EntityType.ZOMBIE : EntityType.SKELETON;
    }
}
