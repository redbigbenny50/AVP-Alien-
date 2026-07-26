package com.alien.common.gameplay.block.entity.capture.anchor;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Where the loaded capture anchors are.
 * <p>
 * Nothing tracked anchor positions before this, so the only way to find one was to scan the world - and a 32 block
 * radius is a quarter of a million block reads, far too expensive for a GOAP sensor that runs constantly. Anchors
 * instead announce themselves here as their block entities load and drop out as they unload or break, so "is there an
 * anchor near me" becomes a walk over a handful of positions.
 * <p>
 * The index is deliberately TRANSIENT - it is rebuilt from chunk loading and never saved. An anchor in an unloaded
 * chunk is not actionable by AI anyway, so the live set is exactly the useful set.
 */
public final class AnchorIndex {

    private AnchorIndex() {}

    private static final Map<ResourceKey<Level>, Set<BlockPos>> BY_DIMENSION = new ConcurrentHashMap<>();

    /** Called when an anchor block entity loads. Server side only - the client has no use for this. */
    public static void add(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        BY_DIMENSION
            .computeIfAbsent(level.dimension(), key -> ConcurrentHashMap.newKeySet())
            .add(pos.immutable());
    }

    /** Called when an anchor block entity unloads or is destroyed. */
    public static void remove(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        var positions = BY_DIMENSION.get(level.dimension());
        if (positions != null) {
            positions.remove(pos);
        }
    }

    /** Every loaded anchor in a dimension. Never null; empty when none are loaded. */
    public static Set<BlockPos> loadedIn(Level level) {
        return BY_DIMENSION.getOrDefault(level.dimension(), Set.of());
    }

    /**
     * Nearest loaded anchor to {@code from} within {@code radius}, or null. The filter is where callers apply their own
     * rules - hive claim membership, for instance.
     */
    @Nullable
    public static BlockPos nearest(Level level, BlockPos from, double radius, Predicate<BlockPos> filter) {
        var positions = loadedIn(level);
        if (positions.isEmpty()) {
            return null;
        }
        var radiusSquared = radius * radius;
        BlockPos best = null;
        double bestDistanceSquared = Double.MAX_VALUE;
        for (var pos : positions) {
            var distanceSquared = from.distSqr(pos);
            if (distanceSquared > radiusSquared || distanceSquared >= bestDistanceSquared) {
                continue;
            }
            if (!filter.test(pos)) {
                continue;
            }
            best = pos;
            bestDistanceSquared = distanceSquared;
        }
        return best;
    }
}
