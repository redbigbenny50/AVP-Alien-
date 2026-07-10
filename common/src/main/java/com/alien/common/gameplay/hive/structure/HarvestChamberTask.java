package com.alien.common.gameplay.hive.structure;

import com.alien.Alien;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.registry.init.block.AlienResinBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runtime half of the harvest chamber: fills its four spawner slots from the location's pending captured spawners. The
 * slots are the FIXED four corner blocks around the point where the 2x2's chunks meet, at floor level, marked with
 * {@code smooth_resin} by the authored piece - the marker is read at those exact cells only, so smooth resin used
 * decoratively elsewhere in the piece is ignored. A filled slot replaces the marker with a mob spawner set to the
 * captured monster type. Once placed, the vermin rule makes the spawned monsters always-huntable and kill biomass pays
 * out - the farm runs itself.
 * <p>
 * Also tracks capacity: while spawners are still pending and every existing chamber's slots are filled, the router
 * reads {@link #isCapacityFull} and synthesizes an overflow chamber goal.
 */
public final class HarvestChamberTask {

    /** Hives whose harvest slots are all filled while spawners remain pending - the router builds another chamber. */
    private static final Set<HiveLocation> CAPACITY_FULL =
        java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    private HarvestChamberTask() {}

    public static boolean isCapacityFull(HiveLocation location) {
        return CAPACITY_FULL.contains(location);
    }

    /** Called on the growth cadence. Fills free slots from pending; maintains the capacity flag. */
    public static void run(ServerLevel level, HiveLocation location) {
        var pending = location.pendingHarvestSpawners();
        var groups = chamberGroups(location);
        if (groups.isEmpty()) {
            CAPACITY_FULL.remove(location);
            return;
        }

        int freeSlots = 0;
        for (var origin : groups) {
            if (!level.isLoaded(origin.getWorldPosition())) {
                continue;
            }
            for (var slot : spawnerSlots(location, origin)) {
                var state = level.getBlockState(slot);
                if (!state.is(AlienResinBlocks.SMOOTH_RESIN.get())) {
                    continue; // not a marked (or already-filled) slot
                }
                if (pending.isEmpty()) {
                    freeSlots++;
                    continue;
                }
                var type = pending.remove(0);
                level.setBlock(slot, Blocks.SPAWNER.defaultBlockState(), 3);
                if (level.getBlockEntity(slot) instanceof SpawnerBlockEntity spawnerBe) {
                    spawnerBe.setEntityId(type, level.random);
                }
                Alien.LOGGER.info(
                    "Hive at {}: harvest chamber slot at {} filled with a {} spawner ({} still pending).",
                    location.centerPos(),
                    slot,
                    BuiltInRegistries.ENTITY_TYPE.getKey(type),
                    pending.size()
                );
            }
        }

        if (!pending.isEmpty() && freeSlots == 0) {
            CAPACITY_FULL.add(location);
        } else {
            CAPACITY_FULL.remove(location);
        }
    }

    /**
     * The four spawner-slot positions for the 2x2 whose min-corner chunk is {@code origin}: the corner blocks around
     * the chunks' meeting point, at hive floor level.
     */
    public static List<BlockPos> spawnerSlots(HiveLocation location, ChunkPos origin) {
        int meetX = (origin.x + 1) << 4; // min block X of the +x chunks = the meeting line
        int meetZ = (origin.z + 1) << 4;
        int y = location.hiveFloorY();
        return List.of(
            new BlockPos(meetX - 1, y, meetZ - 1),
            new BlockPos(meetX, y, meetZ - 1),
            new BlockPos(meetX - 1, y, meetZ),
            new BlockPos(meetX, y, meetZ)
        );
    }

    /** Min-corner chunks of each distinct 2x2 harvest chamber (the four chunks sharing a harvest piece id). */
    private static List<ChunkPos> chamberGroups(HiveLocation location) {
        Map<ChunkPos, String> byChunk = location.structurePieceByChunk();
        var harvestChunks = new HashSet<ChunkPos>();
        for (var entry : byChunk.entrySet()) {
            if (entry.getValue().contains("chamber_harvest")) {
                harvestChunks.add(entry.getKey());
            }
        }
        var origins = new ArrayList<ChunkPos>();
        for (var chunk : harvestChunks) {
            if (
                harvestChunks.contains(new ChunkPos(chunk.x + 1, chunk.z))
                    && harvestChunks.contains(new ChunkPos(chunk.x, chunk.z + 1))
                    && harvestChunks.contains(new ChunkPos(chunk.x + 1, chunk.z + 1))
                    // min-corner only: the chunk left/above must not also form a square containing this one
                    && !(harvestChunks.contains(new ChunkPos(chunk.x - 1, chunk.z))
                        && harvestChunks.contains(new ChunkPos(chunk.x - 1, chunk.z + 1)))
                    && !(harvestChunks.contains(new ChunkPos(chunk.x, chunk.z - 1))
                        && harvestChunks.contains(new ChunkPos(chunk.x + 1, chunk.z - 1)))
            ) {
                origins.add(chunk);
            }
        }
        return origins;
    }
}
