package com.alien.common.gameplay.hive.structure;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.model.alien.variant.AlienVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fortress-style support pillars under hive pieces in ceiled dimensions.
 * <p>
 * The nether's height range makes hives BUILD IN OPEN AIR routinely - a piece stamped on a shelf edge or over the lava
 * ocean simply floats; vanilla's answer is the nether fortress's masonry piers, and this is that in the hive's material
 * ([stated] "the structures can float because of the nethers heights. i want to add pillars that are 4x4 in dimension
 * of the strains ribbed resin block similar to how nether fortresses do this").
 * <p>
 * SPACING ([stated] "i dont want a pillar in every chunk though... i would only want one under a hallway if there are
 * no other pillars close. i dont want a 19x19 of pillars"): CHAMBER pieces keep his stated per-chunk counts - the 3x3
 * core stands on 9, a 2x2 chamber on 4, an egg or jelly room on 1 - because a room's mass genuinely needs its footprint
 * carried. HALLWAYS are the sparse case: a hallway chunk is pillared only when NO pillar already stands within
 * {@value #HALLWAY_CLEARANCE_CHUNKS} chunk of it, so a corridor run picks up a pier roughly every other chunk
 * (~32-block pitch, very fortress) and corridors hugging an already-pillared chamber add nothing for the first stretch.
 * Placed pillars are recorded on the location (persisted), so spacing survives reloads and upkeep re-stamps never
 * densify what was deliberately left open.
 * <p>
 * Each pillar meets the structure through a stepped ARCH capital - 10x10 against the underside, then 8x8, 6x6, 5x5 -
 * before the 4x4 shaft runs to ground, all in the strain's ribbed resin and centered in the chunk. Air, fluids and
 * replaceables fill per cell until an entire layer needed nothing - so a buried piece grows no pillar, uneven shelf
 * terrain seats each edge at its own depth, and a lava-ocean piece is propped clear to the sea floor.
 */
public final class HiveSupportPillars {

    /**
     * Cross-section per layer, top-down from the structure's underside ([stated] "have it be more of an arch under
     * it... a step down fill in before it connects to the shaft... from the shaft which is 4x4 do a 5x5 layer a 6x6
     * layer a 8x8 layer then a 10x10 layer then it connects to the bottom of the structure"): a stepped capital - 10x10
     * against the piece, then 8x8, 6x6, 5x5 - flaring into the 4x4 shaft that runs to ground. Layers past the array use
     * the shaft size.
     */
    private static final int[] LAYER_SIZES_TOP_DOWN = { 10, 8, 6, 5 };

    /** Shaft cross-section below the capital, in blocks, per his spec. */
    private static final int PILLAR_SIZE = 4;

    /** A hallway chunk skips its pillar when one already stands within this chebyshev chunk distance. */
    private static final int HALLWAY_CLEARANCE_CHUNKS = 1;

    private HiveSupportPillars() {}

    /** Pillars for one piece: every chunk for chambers, spaced for hallways. Ceiled dimensions only. */
    public static void build(ServerLevel level, HiveLocation location, PieceMatch match) {
        if (!level.dimensionType().hasCeiling()) {
            return;
        }
        int topY = location.hiveFloorY() - 1;
        int bottomY = level.getMinBuildHeight() + 1;
        if (topY <= bottomY) {
            return;
        }
        var hallway = match.piece().id().getPath().contains("hallway");
        BlockState block = null;
        for (ChunkPos chunk : match.occupiedChunks()) {
            if (location.supportPillarChunks().contains(chunk.toLong())) {
                continue; // this chunk already carries its pier
            }
            if (hallway && pillarNearby(location, chunk)) {
                continue; // sparse rule: corridors borrow the neighbour's support
            }
            if (block == null) {
                block = ribbedResinFor(location);
            }
            buildPillar(level, chunk, topY, bottomY, block);
            location.supportPillarChunks().add(chunk.toLong());
        }
    }

    private static boolean pillarNearby(HiveLocation location, ChunkPos chunk) {
        for (int dx = -HALLWAY_CLEARANCE_CHUNKS; dx <= HALLWAY_CLEARANCE_CHUNKS; dx++) {
            for (int dz = -HALLWAY_CLEARANCE_CHUNKS; dz <= HALLWAY_CLEARANCE_CHUNKS; dz++) {
                if (
                    (dx != 0 || dz != 0)
                        && location.supportPillarChunks().contains(ChunkPos.asLong(chunk.x + dx, chunk.z + dz))
                ) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Descends layer by layer, filling air, fluids and replaceables with ribbed resin. The first layer that needed NO
     * fill is solid ground - the pier is seated and the descent stops.
     */
    private static void buildPillar(ServerLevel level, ChunkPos chunk, int topY, int bottomY, BlockState block) {
        var pos = new BlockPos.MutableBlockPos();
        for (int y = topY; y >= bottomY; y--) {
            int layerIndex = topY - y;
            int size = layerIndex < LAYER_SIZES_TOP_DOWN.length ? LAYER_SIZES_TOP_DOWN[layerIndex] : PILLAR_SIZE;
            // Every layer size centers on the same chunk midpoint: start = (16 - size) / 2, so the 10x10 capital,
            // the odd 5x5 step and the 4x4 shaft all share one axis.
            int x0 = chunk.getMinBlockX() + (16 - size) / 2;
            int z0 = chunk.getMinBlockZ() + (16 - size) / 2;
            var filled = 0;
            for (int dx = 0; dx < size; dx++) {
                for (int dz = 0; dz < size; dz++) {
                    pos.set(x0 + dx, y, z0 + dz);
                    var state = level.getBlockState(pos);
                    if (state.isAir() || !state.getFluidState().isEmpty() || state.canBeReplaced()) {
                        level.setBlock(pos, block, Block.UPDATE_CLIENTS);
                        filled++;
                    }
                }
            }
            if (filled == 0) {
                return; // seated on ground (or a previous pass's own resin)
            }
        }
    }

    /** The strain's ribbed resin - the hive's answer to fortress brick. Strainless (no lineage yet) = normal. */
    private static BlockState ribbedResinFor(HiveLocation location) {
        var variant = location.lineageVariantOrNull();
        var type = AlienVariantTypes.getFor(variant == null ? AlienVariant.NORMAL : variant);
        if (type == AlienVariantTypes.NETHER) {
            return com.alien.common.registry.init.block.NetherAlienResinBlocks.RIBBED_NETHER_RESIN.get()
                .defaultBlockState();
        }
        if (type == AlienVariantTypes.ABERRANT) {
            return com.alien.common.registry.init.block.AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN.get()
                .defaultBlockState();
        }
        if (type == AlienVariantTypes.IRRADIATED) {
            return com.alien.common.registry.init.block.IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN.get()
                .defaultBlockState();
        }
        return com.alien.common.registry.init.block.AlienResinBlocks.RIBBED_RESIN.get().defaultBlockState();
    }
}
