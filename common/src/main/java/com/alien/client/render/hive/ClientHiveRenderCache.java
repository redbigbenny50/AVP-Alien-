package com.alien.client.render.hive;

import com.alien.common.gameplay.hive.render.HiveRenderDataBuilder;
import com.alien.common.network.payload.S2CHiveRenderDataPayload;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side cache of the most-recent {@link S2CHiveRenderDataPayload}, decoded into a render-friendly form. The
 * world-render hook (per loader) reads {@link #current()} each frame and draws the wireframe. Holds at most one
 * snapshot — each payload clears and replaces. Empty until the first payload arrives (overlay just toggled on) and
 * after {@link #clear()} (overlay toggled off / world unloaded).
 */
public final class ClientHiveRenderCache {

    /** One hive's render data: slab band Y's and its claimed chunks. */
    public record HiveRender(
        int floorY,
        int ceilingY,
        List<ChunkPos> chunks
    ) {}

    private static volatile List<HiveRender> current = List.of();

    private ClientHiveRenderCache() {}

    public static List<HiveRender> current() {
        return current;
    }

    public static void clear() {
        current = List.of();
    }

    /** Decode an incoming payload into cached {@link HiveRender}s. Called on the client packet thread. */
    public static void apply(S2CHiveRenderDataPayload payload) {
        var tag = payload.data();
        var hives = new ArrayList<HiveRender>();

        var list = tag.getList(HiveRenderDataBuilder.K_HIVES, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (var i = 0; i < list.size(); i++) {
            var entry = list.getCompound(i);
            var floorY = entry.getInt(HiveRenderDataBuilder.K_FLOOR_Y);
            var ceilingY = entry.getInt(HiveRenderDataBuilder.K_CEILING_Y);

            var packed = entry.getLongArray(HiveRenderDataBuilder.K_CHUNKS);
            var chunks = new ArrayList<ChunkPos>(packed.length);
            for (var p : packed) {
                chunks.add(HiveRenderDataBuilder.unpackChunk(p));
            }
            hives.add(new HiveRender(floorY, ceilingY, chunks));
        }

        current = hives;
    }
}
