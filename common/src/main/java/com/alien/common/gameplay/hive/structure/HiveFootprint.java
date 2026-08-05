package com.alien.common.gameplay.hive.structure;

import com.alien.common.gameplay.hive.location.HiveLocation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/**
 * The hive's square footprint, in the one unit it is actually measured in: CHUNKS.
 * <p>
 * {@link HiveRouter#BASE_EXTENT} (9) and {@link HiveRouter#EMPRESS_EXTENT} (11) are chunk radii - that is what "19x19"
 * and "23x23" mean, and the router only ever compares them against {@code new ChunkPos(location.centerPos())}. Outside
 * the router that was easy to miss: {@code centerPos()} returns a {@link BlockPos}, so comparing an extent to a raw
 * block coordinate compiles, reads naturally, and is wrong by a factor of sixteen. Every nuke test did exactly that,
 * shrinking a 304-block-wide hive to an 18-block box around its centre.
 * <p>
 * So the conversion lives here, once, and callers never touch the raw constant.
 */
public final class HiveFootprint {

    private HiveFootprint() {}

    /** The hive's footprint radius, in chunks. */
    public static int extentChunks(HiveLocation location) {
        return location.isEmpressInfluenced() ? HiveRouter.EMPRESS_EXTENT : HiveRouter.BASE_EXTENT;
    }

    /** Whether a world position falls inside the footprint square. Chebyshev on chunk coordinates, like the router. */
    public static boolean contains(HiveLocation location, double blockX, double blockZ) {
        var center = new ChunkPos(location.centerPos());
        var extent = extentChunks(location);
        var chunkX = Math.floorDiv((int) Math.floor(blockX), 16);
        var chunkZ = Math.floorDiv((int) Math.floor(blockZ), 16);

        return Math.abs(chunkX - center.x) <= extent && Math.abs(chunkZ - center.z) <= extent;
    }

    /**
     * Distance in blocks from a world position to the NEAREST POINT of the footprint square, or 0 inside it - so
     * clipping the corner of a big hive counts.
     */
    public static double distanceTo(HiveLocation location, double blockX, double blockZ) {
        var center = new ChunkPos(location.centerPos());
        var extent = extentChunks(location);

        var minX = (double) ((center.x - extent) << 4);
        var maxX = (double) (((center.x + extent) << 4) + 15);
        var minZ = (double) ((center.z - extent) << 4);
        var maxZ = (double) (((center.z + extent) << 4) + 15);

        var dx = Math.max(0.0, Math.max(minX - blockX, blockX - maxX));
        var dz = Math.max(0.0, Math.max(minZ - blockZ, blockZ - maxZ));

        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The footprint as a full-height column, for area sweeps. */
    public static AABB column(HiveLocation location, double minY, double maxY) {
        var center = new ChunkPos(location.centerPos());
        var extent = extentChunks(location);

        return new AABB(
            (center.x - extent) << 4,
            minY,
            (center.z - extent) << 4,
            (((center.x + extent) << 4) + 16),
            maxY,
            (((center.z + extent) << 4) + 16)
        );
    }
}
