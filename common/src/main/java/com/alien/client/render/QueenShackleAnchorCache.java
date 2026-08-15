package com.alien.client.render;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-queen client cache of the three shackle bones' live world positions, published each frame by
 * {@code ShackleAnchorLayer} and read by {@code AnchorBlockEntityRenderer} so capture chains attach to the actual
 * animated shackle points and follow the queen through movement and attacks. Keyed by network entity id; values are
 * absolute world coordinates (BLib's {@code AzBone.getWorldPosition()} already folds in the entity position).
 */
public final class QueenShackleAnchorCache {

    public static final int LEFT_ARM = 0;

    public static final int RIGHT_ARM = 1;

    public static final int NECK = 2;

    private static final Map<Integer, Vec3[]> CACHE = new ConcurrentHashMap<>();

    private QueenShackleAnchorCache() {}

    /** Store {@code worldPos} for one shackle bone of the queen with this network id. */
    public static void put(int entityId, int boneIndex, Vec3 worldPos) {
        CACHE.computeIfAbsent(entityId, k -> new Vec3[3])[boneIndex] = worldPos;
    }

    /** Latest world position of a shackle bone, or {@code null} if the queen has not been rendered recently. */
    @Nullable
    public static Vec3 get(int entityId, int boneIndex) {
        Vec3[] positions = CACHE.get(entityId);
        return positions == null ? null : positions[boneIndex];
    }

    public static void clear(int entityId) {
        CACHE.remove(entityId);
    }
}
