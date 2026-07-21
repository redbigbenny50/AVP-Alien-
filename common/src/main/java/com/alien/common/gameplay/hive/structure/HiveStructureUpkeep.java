package com.alien.common.gameplay.hive.structure;

import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.registry.init.block.AlienBlocks;
import com.alien.common.registry.tag.AlienBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps a finished hive piece matching the shape it was built as.
 * <p>
 * A stamped piece is only correct at the instant it is placed. Afterwards the world edits it: a neighbouring carve
 * opens a lava pocket that floods a chamber, gravel falls into a hallway, terrain shifts, or a player walls off a
 * corridor. Nothing put any of it back, so a hive slowly degraded and - worst of all - passages that the routing layer
 * still believed were open became impassable. Egg haulers then read perfectly good nursery beds as unreachable.
 * <p>
 * The pass re-stamps one built piece at a time. The stamp is idempotent (see {@link HiveStructurePlacer#placeWorld}):
 * it rewrites the authored structure blocks, writes AIR back into the piece's air cells - which is what removes
 * intruding blocks and reopens blocked passages - and drains liquids.
 * <p>
 * <b>Exemptions.</b> A blind re-stamp would also erase what the hive itself put inside those air cells, so anything on
 * the hive's own allowlist is snapshotted before the stamp and restored after:
 * <ul>
 * <li>Jelly vats - placed into chamber slots after the stamp, and they carry contents worth preserving.</li>
 * <li>Resin veins and webs - the aliens grow these into open space; they occupy air cells by design.</li>
 * <li>Capture anchors - a player restraint, deliberately left standing. Clearing them here would quietly undo a capture
 * setup on a timer; whether an anchor survives inside a hive is for the aliens to settle by attacking it, not for
 * masonry upkeep to decide.</li>
 * </ul>
 * Everything else in an air cell is treated as debris and cleared, player-placed blocks included: that is deliberate,
 * so a player cannot permanently seal a hive passage.
 */
public final class HiveStructureUpkeep {

    private HiveStructureUpkeep() {}

    /** A hive-owned block held across the re-stamp. */
    private record Preserved(
        BlockPos pos,
        BlockState state,
        CompoundTag blockEntity
    ) {}

    /**
     * Re-stamps one finished piece. No-op when the piece is unknown, its chunks aren't loaded, or the placement can't
     * be resolved - upkeep is best-effort and must never be the reason a tick throws.
     */
    public static void tickPiece(ServerLevel level, HiveLocation location, ChunkPos originChunk, HiveLocation.BuiltPlacement placement) {
        var registry = HivePieceRegistry.get(level.getServer());
        if (registry == null) {
            return;
        }
        var pieceId = ResourceLocation.tryParse(placement.pieceId());
        if (pieceId == null) {
            return;
        }
        var piece = registry.get(pieceId);
        if (piece == null) {
            return;
        }

        var match = new PieceMatch(piece, placement.rotation(), originChunk);
        var chunks = match.occupiedChunks();
        for (ChunkPos chunk : chunks) {
            if (!level.isLoaded(chunk.getWorldPosition())) {
                return; // never edit a piece we can only half see
            }
        }

        // CHECK BEFORE REPAIRING. Re-stamping blindly every beat would rewrite an entire piece - thousands of
        // setBlock calls, each with the lighting and client-sync cost that implies - to fix nothing at all,
        // several times a minute, per hive. Resolving the placement is pure (no world access) and the authored
        // air cells come straight from the template palette, so the steady-state cost of upkeep is a read-only
        // scan of just those cells and NO writes whatsoever. The expensive path runs only when something has
        // actually got into the piece.
        var resolved = HiveStructurePlacer.resolvePlacement(level, location, match);
        if (resolved == null) {
            return;
        }
        if (!isBreached(level, resolved)) {
            return;
        }

        var preserved = snapshotHiveBlocks(level, location, chunks);
        if (!HiveStructurePlacer.placeWorld(level, location, match)) {
            return;
        }
        restore(level, preserved);
    }

    /**
     * Is anything sitting in a cell the template says should be open?
     * <p>
     * Covers exactly the two things that actually matter to the hive: liquid that has seeped in (a fluid IS a block
     * state, so a flooded cell reads as non-air here) and solid blocks dropped or placed into a passage. Both are what
     * make a route impassable and strand workers. Anything on the preserve list is expected to be there and is not a
     * breach.
     */
    private static boolean isBreached(ServerLevel level, HiveStructurePlacer.ResolvedPlacement resolved) {
        var airCells = resolved.template().filterBlocks(resolved.placeAt(), resolved.settings(), Blocks.AIR);
        for (var cell : airCells) {
            var state = level.getBlockState(cell.pos());
            if (state.isAir() || isPreserved(state)) {
                continue;
            }
            return true;
        }
        return false;
    }

    /** Collects the hive's own blocks inside the piece so the stamp can't eat them. */
    private static List<Preserved> snapshotHiveBlocks(ServerLevel level, HiveLocation location, List<ChunkPos> chunks) {
        var preserved = new ArrayList<Preserved>();
        var pos = new BlockPos.MutableBlockPos();
        int floorY = location.hiveFloorY();
        int maxY = location.hiveCeilingY() - 1;
        for (ChunkPos chunk : chunks) {
            int minX = chunk.getMinBlockX();
            int minZ = chunk.getMinBlockZ();
            for (int x = minX; x < minX + 16; x++) {
                for (int z = minZ; z < minZ + 16; z++) {
                    for (int y = floorY; y <= maxY; y++) {
                        pos.set(x, y, z);
                        var state = level.getBlockState(pos);
                        if (!isPreserved(state)) {
                            continue;
                        }
                        CompoundTag beTag = null;
                        var blockEntity = level.getBlockEntity(pos);
                        if (blockEntity != null) {
                            beTag = blockEntity.saveWithFullMetadata(level.registryAccess());
                        }
                        preserved.add(new Preserved(pos.immutable(), state, beTag));
                    }
                }
            }
        }
        return preserved;
    }

    /** Puts the hive's own blocks back, contents and all. */
    private static void restore(ServerLevel level, List<Preserved> preserved) {
        for (var entry : preserved) {
            // Only refill cells the stamp actually cleared - never overwrite structure it just (re)built.
            if (!level.getBlockState(entry.pos()).isAir()) {
                continue;
            }
            level.setBlock(entry.pos(), entry.state(), 3);
            if (entry.blockEntity() == null) {
                continue;
            }
            var blockEntity = level.getBlockEntity(entry.pos());
            if (blockEntity != null) {
                blockEntity.loadWithComponents(entry.blockEntity(), level.registryAccess());
                blockEntity.setChanged();
            }
        }
    }

    /**
     * The allowlist: blocks the upkeep pass must not clear. Mostly the hive's own (tag-driven for resin, so every
     * variant is covered and new resin blocks inherit the exemption for free), plus the capture anchor, which is left
     * for the aliens to settle rather than being tidied away by masonry.
     */
    private static boolean isPreserved(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        return state.is(AlienBlocks.JELLY_VAT.get())
            || state.is(AlienBlocks.ANCHOR.get())
            || state.is(AlienBlockTags.RESIN_VEINS)
            || state.is(AlienBlockTags.RESIN_WEBS);
    }
}
