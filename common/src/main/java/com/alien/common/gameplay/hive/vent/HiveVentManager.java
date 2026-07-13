package com.alien.common.gameplay.hive.vent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-{@link com.alien.common.gameplay.hive.location.HiveLocation} vent registry. Tracks all known
 * {@link com.alien.common.gameplay.block.entity.resin.vent.ResinVentBlockEntity} positions inside the location's
 * territory.
 * <p>
 * Lifted from the legacy {@code com.alien.common.gameplay.hive.vent} package as part of the Phase 12 cutover. State is
 * held in memory only; vents re-register themselves on chunk load via the block-entity tick.
 */
public class HiveVentManager {

    private final HiveVentCache hiveVentCache;

    /** What each vent is FOR. Re-populated from the block entities on chunk load, exactly like the position cache. */
    private final Map<BlockPos, VentKind> kindByVent;

    public HiveVentManager() {
        this.hiveVentCache = new HiveVentCache();
        this.kindByVent = new HashMap<>();
    }

    public void addVent(BlockPos blockPos, VentKind kind) {
        hiveVentCache.add(blockPos, null);
        kindByVent.put(blockPos, kind);
    }

    public void removeVent(BlockPos blockPos) {
        hiveVentCache.remove(blockPos);
        kindByVent.remove(blockPos);
    }

    /** What this vent is for, or null if it is not a known vent of this location. */
    public @Nullable VentKind kindOf(BlockPos blockPos) {
        return kindByVent.get(blockPos);
    }

    /** True if this vent is one of the given kinds. Unknown vents match nothing. */
    public boolean isKind(BlockPos blockPos, VentKind... kinds) {
        var kind = kindByVent.get(blockPos);
        if (kind == null) {
            return false;
        }
        for (var candidate : kinds) {
            if (kind == candidate) {
                return true;
            }
        }
        return false;
    }

    /** Every known vent of the given kinds. */
    public Set<BlockPos> ventsOfKind(VentKind... kinds) {
        var wanted = EnumSet.noneOf(VentKind.class);
        for (var kind : kinds) {
            wanted.add(kind);
        }

        var result = new HashSet<BlockPos>();
        for (var entry : kindByVent.entrySet()) {
            if (wanted.contains(entry.getValue())) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /** How many vents of this kind the hive has. The dig cap counts FRONTIER only - structure vents come free. */
    public int ventCountOfKind(VentKind kind) {
        var count = 0;
        for (var value : kindByVent.values()) {
            if (value == kind) {
                count++;
            }
        }
        return count;
    }

    public Set<BlockPos> getVentsWithinSection(BlockPos blockPos) {
        return hiveVentCache.getVentsForSection(blockPos);
    }

    public Set<BlockPos> getVentsWithinSection(SectionPos sectionPos) {
        return hiveVentCache.getVentsForSection(sectionPos);
    }

    /**
     * All known vent positions in this location's territory. Debug/inspection use.
     */
    public Set<BlockPos> allVents() {
        return hiveVentCache.allVents();
    }

    /**
     * All known vents anywhere in the given chunk column (any Y). Used by {@code SurfacePartyLifecycleTask} for the
     * surface-party vent-drop cap; the caller further filters to a near-surface Y band — this method itself does not
     * distinguish depth.
     */
    public Set<BlockPos> getVentsWithinChunk(net.minecraft.world.level.ChunkPos chunkPos) {
        var result = new java.util.HashSet<BlockPos>();
        for (var pos : allVents()) {
            if (pos.getX() >> 4 == chunkPos.x && pos.getZ() >> 4 == chunkPos.z) {
                result.add(pos);
            }
        }
        return result;
    }

    /**
     * Number of known vents in this location's territory.
     */
    public int ventCount() {
        return hiveVentCache.allVents().size();
    }
}