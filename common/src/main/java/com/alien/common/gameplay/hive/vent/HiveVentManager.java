package com.alien.common.gameplay.hive.vent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

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

    public HiveVentManager() {
        this.hiveVentCache = new HiveVentCache();
    }

    public void addVent(BlockPos blockPos) {
        hiveVentCache.add(blockPos, null);
    }

    public void removeVent(BlockPos blockPos) {
        hiveVentCache.remove(blockPos);
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