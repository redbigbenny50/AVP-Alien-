package com.alien.common.gameplay.hive.render;

import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * Builds the {@link CompoundTag} payload for the hive render overlay: every hive location near a player, with its slab
 * floor/ceiling and the full set of claimed chunk positions. Server-side only.
 * <p>
 * Tag shape:
 *
 * <pre>
 * { "hives": [ { "fy":int, "cy":int, "chunks":[long,long,...] }, ... ] }
 * </pre>
 *
 * Chunk positions are stored as packed longs ({@link ChunkPos#toLong()}). The bleed-zone band is derived on the client
 * from the slab Y's (floor-4 .. ceiling+4), so it is not sent separately.
 */
public final class HiveRenderDataBuilder {

    /** Only hives whose center is within this horizontal range of the player are sent. */
    private static final int RENDER_RANGE_BLOCKS = 256;

    public static final String K_HIVES = "hives";

    public static final String K_FLOOR_Y = "fy";

    public static final String K_CEILING_Y = "cy";

    public static final String K_CHUNKS = "chunks";

    private HiveRenderDataBuilder() {}

    public static CompoundTag empty() {
        var tag = new CompoundTag();
        tag.put(K_HIVES, new ListTag());
        return tag;
    }

    public static CompoundTag build(ServerPlayer player) {
        var tag = new CompoundTag();
        var hives = new ListTag();

        var dimension = player.level().dimension();
        var px = player.getX();
        var pz = player.getZ();
        var rangeSq = (double) RENDER_RANGE_BLOCKS * RENDER_RANGE_BLOCKS;

        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (!location.isAlive() || !location.dimension().equals(dimension)) {
                continue;
            }
            var center = location.centerPos();
            var dx = center.getX() - px;
            var dz = center.getZ() - pz;
            if (dx * dx + dz * dz > rangeSq) {
                continue;
            }

            hives.add(encodeLocation(location));
        }

        tag.put(K_HIVES, hives);
        return tag;
    }

    private static CompoundTag encodeLocation(HiveLocation location) {
        var entry = new CompoundTag();
        entry.putInt(K_FLOOR_Y, location.hiveFloorY());
        entry.putInt(K_CEILING_Y, location.hiveCeilingY());

        var chunks = location.claimedChunks();
        var packed = new long[chunks.size()];
        var i = 0;
        for (var chunk : chunks) {
            packed[i++] = chunk.toLong();
        }
        entry.putLongArray(K_CHUNKS, packed);
        return entry;
    }

    /** Convenience for the client side: unpack a chunk long back into a ChunkPos. */
    public static ChunkPos unpackChunk(long packed) {
        return new ChunkPos(packed);
    }
}
