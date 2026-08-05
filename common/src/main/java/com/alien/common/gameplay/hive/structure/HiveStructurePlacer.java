package com.alien.common.gameplay.hive.structure;

import com.alien.Alien;
import com.alien.common.gameplay.hive.location.HiveLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Stamps a matched hive piece into the world: carves the chunks it occupies (reusing the founding carve primitive -
 * clear from floor+1 to the ceiling, leaving the floor row), places the piece's structure template with the match's
 * rotation, then records the piece's roles and registers its still-open doorways as new frontier sockets.
 * <p>
 * The piece footprint is chunk-aligned (pieces are authored in whole chunks), so the world offset is simply the origin
 * chunk's min-block corner at the hive floor Y. Structure placement uses vanilla {@code placeInWorld}, so the authored
 * blocks (walls, resin, doorways) land exactly as designed; the carve first hollows the volume so the piece isn't
 * buried in stone.
 * <p>
 * Instant placement for now (same as the founding chamber's instant carve); animated/amortized excavation can layer on
 * top later using this same carve primitive.
 */
public final class HiveStructurePlacer {

    /**
     * Special doorways whose twin jigsaw socket must be consumed along with a placement. [stated] "every doorway has
     * two jigsaws and always have" - each of these doorway types invites exactly ONE room, so the leftover twin is
     * never a second invitation: royal halls doubled off the queen chamber's doors, and [stated] "the two scourge
     * sockets were connected" - a raid chamber's one scourge doorway grew two scourge chambers. Plain hive_door twins
     * stay untouched: corridors are governed by routing, and merging adjacent plain doors could eat a genuine second
     * doorway on a long wall.
     */
    private static boolean isTwinnedSpecialDoor(String doorType) {
        return doorType.contains("royal") || doorType.contains("scourge") || doorType.contains("jelly");
    }

    private HiveStructurePlacer() {}

    /**
     * Carves and stamps {@code match} for {@code location}. Returns true on success. The connecting doorway (the one
     * that mated with {@code connectedTo}) is excluded from the new frontier sockets so growth doesn't try to build
     * back into the piece it just came from.
     *
     * @param level       the server level to build in
     * @param location    the hive location being grown
     * @param match       the chosen piece placement (piece + rotation + origin chunk)
     * @param connectedTo the frontier socket this piece attached to (its mate is excluded from new sockets)
     */
    /**
     * THE SEAM (construction economy step 2, design §2): placement is split into a WORLD half and a BOOKKEEPING half.
     * <ul>
     * <li>{@link #placeWorld} - resolve the template, stamp it (clear air cells, write resin), drain liquids. This is
     * the part the progressive carve replaces at step 3: diggers clear the volume, placers write the resin, over
     * time.</li>
     * <li>{@link #finalizePlacement} - roles, socket math, socket swap. Runs ONCE, at completion, whichever way the
     * world got built. Because new sockets only register here, a half-built piece exposes no doorways and the router
     * cannot commission past it - the one-build-at-a-time access rule enforces itself (design §8.3).</li>
     * </ul>
     * This method is the INSTANT STAMP: both halves in one tick. The routed build path always commissions carve sites
     * instead; this stays for the never-wedge fallbacks and the carve-completion stamp itself.
     */
    public static boolean place(ServerLevel level, HiveLocation location, PieceMatch match, FrontierSocket connectedTo) {
        if (!placeWorld(level, location, match)) {
            return false;
        }
        finalizePlacement(level, location, match, connectedTo);
        return true;
    }

    /**
     * A resolved placement: the template plus the offset and settings the stamp uses. Extracted (step 3) so the
     * progressive carve resolves EXACTLY the resolution the stamp does - {@code CarveSiteWork}'s air-cell enumeration
     * ({@code filterBlocks}) and its bounded patch stamps ({@link #placeWorldPatch}) share this object, so they can
     * never disagree with {@link #placeWorld}/{@link #finishWorld} about where a single block goes.
     * <p>
     * {@code settings} carries NO bounding box - it is the full-piece resolution. Patch stamps bound a COPY.
     */
    public record ResolvedPlacement(
        StructureTemplate template,
        BlockPos placeAt,
        StructurePlaceSettings settings
    ) {}

    /**
     * Resolves {@code match} into the template + placement offset + settings the stamp uses, or null if the template is
     * missing. Pure resolution: no world reads or writes. The offset is the origin chunk's min corner at the hive floor
     * row (rotation-corrected); the authored piece sits with its own floor on that row.
     */
    public static @Nullable ResolvedPlacement resolvePlacement(ServerLevel level, HiveLocation location, PieceMatch match) {
        var server = level.getServer();
        var templateOpt = server.getStructureManager().get(match.piece().id());
        if (templateOpt.isEmpty()) {
            Alien.LOGGER.warn("Cannot place hive piece {} - template not found.", match.piece().id());
            return null;
        }
        StructureTemplate template = templateOpt.get();

        // placeInWorld rotates block positions around pivot (0,0,0), which pushes some rotations into negative coords.
        // Correct the placement offset so the ROTATED footprint's min corner still lands at the origin chunk's min
        // block. The correction is (sizeX-1) and/or (sizeZ-1) depending on rotation (see rotationOffset).
        var originChunk = match.originChunk();
        var size = template.getSize();
        var correction = rotationOffset(match.rotation(), size.getX(), size.getZ());
        var placeAt = new BlockPos(
            originChunk.getMinBlockX() + correction.getX(),
            location.hiveFloorY(),
            originChunk.getMinBlockZ() + correction.getZ()
        );
        var settings = new StructurePlaceSettings()
            .setRotation(match.rotation())
            .setIgnoreEntities(true)
            // Vanilla placement preserves liquids by default (shipwrecks spawn flooded). Hive interiors must be
            // DRY: air cells displace water sources and placed resin never waterlogs, even when the hive is
            // stamped into an ocean or aquifer.
            .setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING)
            // Replace each authored jigsaw block with its final_state (air, for these pieces) so no raw gray jigsaw
            // blocks are left in the world at doorway seams.
            .addProcessor(JigsawReplacementProcessor.INSTANCE);
        return new ResolvedPlacement(template, placeAt, settings);
    }

    /** The WORLD half of placement: template stamp + liquid drain. No roles, no sockets. See {@link #place}. */
    public static boolean placeWorld(ServerLevel level, HiveLocation location, PieceMatch match) {
        var resolved = resolvePlacement(level, location, match);
        if (resolved == null) {
            return false;
        }
        // Place the template. placeInWorld already honors the carve contract from the authored piece: it writes air
        // into the piece's air cells (clearing whatever terrain was there), places the resin structure blocks, and
        // never touches structure_void cells (they aren't in the block list) - so void regions blend with existing
        // terrain. No separate box-carve: that would wrongly clear the void margins. Offset is the origin chunk's min
        // corner at the hive floor row; the authored piece sits with its own floor on that row.
        var placeAt = resolved.placeAt();
        boolean placed = resolved.template()
            .placeInWorld(level, placeAt, placeAt, resolved.settings(), RandomSource.create(), 2);
        if (!placed) {
            Alien.LOGGER.warn("Structure placement returned false for hive piece {}.", match.piece().id());
            return false;
        }

        // Drain any liquid left inside the piece's AUTHORED AIR CELLS - and nothing else. placeInWorld +
        // IGNORE_WATERLOGGING stops resin from waterlogging, but it does not empty fluid that seeped into the
        // interior during construction. The old drain swept ENTIRE OCCUPIED CHUNKS floor-to-ceiling, deleting every
        // fluid regardless of whether the cell had anything to do with the piece - and upkeep re-stamps re-ran it -
        // so a nether hive built at lava-ocean level vacuumed ragged, ever-growing holes into the sea around it
        // ([stated] "lava being drained in the nether for building. it seems we are creating gaps... constrain the
        // lava removal to only the air and blocks in the structure only"). The authored air list is exactly that
        // constraint: interior cells only, structure_void margins and everything outside the shell untouched, so the
        // ocean laps against the resin instead of disappearing around it.
        drainStructureAir(level, resolved);
        // Ceiled dimensions: fortress-style 4x4 ribbed-resin piers under every occupied chunk, so pieces stamped
        // over open air or the lava ocean stand on something instead of floating. Runs on the completion stamp and
        // on upkeep re-stamps alike; idempotent (see HiveSupportPillars).
        HiveSupportPillars.build(level, location, match);
        return true;
    }

    /**
     * A BOUNDED stamp (construction economy step 3): places only the authored blocks inside {@code patch}, via the
     * exact same vanilla {@code placeInWorld} pipeline as the full stamp - the settings are COPIED before the bounding
     * box is applied, so the shared {@link ResolvedPlacement} stays unbounded for the next patch. Used by the carve
     * tick's resin fill so a patch can never disagree with what the final stamp would put there.
     */
    public static boolean placeWorldPatch(ServerLevel level, ResolvedPlacement resolved, BoundingBox patch) {
        var bounded = resolved.settings().copy().setBoundingBox(patch);
        return resolved.template()
            .placeInWorld(level, resolved.placeAt(), resolved.placeAt(), bounded, RandomSource.create(), 2);
    }

    /**
     * The COMPLETION stamp (construction economy step 3): one full, unbounded pass over the piece - idempotent over
     * everything the progressive carve already built, closes the roof above reach height, catches any straggler cells,
     * and drains liquids. Identical to {@link #placeWorld}; named separately so the carve tick's call sites read as
     * what they are.
     */
    public static boolean finishWorld(ServerLevel level, HiveLocation location, PieceMatch match) {
        return placeWorld(level, location, match);
    }

    /**
     * The BOOKKEEPING half of placement: chunk roles, socket math, and the frontier swap. Runs once, at completion. See
     * {@link #place} for why new sockets registering ONLY here is load-bearing.
     */
    public static void finalizePlacement(ServerLevel level, HiveLocation location, PieceMatch match, FrontierSocket connectedTo) {
        var originChunk = match.originChunk();
        // Record roles for the occupied chunks and register the piece's open doorways as new frontier sockets.
        String pieceId = match.piece().id().toString();
        for (ChunkPos chunk : match.occupiedChunks()) {
            location.assignStructure(chunk, roleFor(match.piece()), pieceId);
        }
        // Origin + rotation, so the upkeep pass can re-stamp this piece later. The per-chunk map above answers
        // "what is here"; only this answers "how do I rebuild it".
        location.recordBuiltPlacement(originChunk, pieceId, match.rotation());

        // The socket that connected back to the frontier: its cell is the frontier's target chunk (relative to origin)
        // and its facing is the mate of the frontier's facing. Exclude it from the new open sockets.
        int connectedCellX = connectedTo.chunk().x + connectedTo.facing().getStepX() - originChunk.x;
        int connectedCellZ = connectedTo.chunk().z + connectedTo.facing().getStepZ() - originChunk.z;
        // A corner extends the corridor's corner run; anything else resets it. The piece's new doorways carry that
        // run so the planner can cap how many corners chain in a row.
        int cornerRun = match.piece().id().getPath().contains("corner") ? connectedTo.cornerRun() + 1 : 0;
        int straightRun = match.piece().id().getPath().contains("straight") ? connectedTo.straightRun() + 1 : 0;
        var newSockets =
            match.openFrontierSockets(
                connectedCellX,
                connectedCellZ,
                connectedTo.facing().getOpposite(),
                cornerRun,
                straightRun
            );

        // ROYAL HALLS DO NOT CHAIN: every royal-hallway template ends in a second royal door, and registering it
        // verbatim let the router's royal priority build hall after hall off each new far door - the hive grew
        // ONLY royal corridors and starved its tunnels and rooms ([stated] "the exits out of each hall that
        // usually lead to a tunnel or jelly/egg room? it just makes a new royal hall"). A placed royal hallway's
        // remaining doors register DOWNGRADED to ordinary hive doors, so its far end grows tunnels, hubs and
        // rooms like any corridor. Royal doors therefore only ever exist on the queen chamber itself - the four
        // grand connections - which is the design.
        if (match.piece().id().getPath().contains("hallway_royal")) {
            newSockets = newSockets.stream()
                .map(
                    socket -> socket.doorType() != null && socket.doorType().contains("royal")
                        ? new FrontierSocket(
                            socket.chunk(),
                            socket.facing(),
                            socket.doorType().replace("_royal_door", "_door"),
                            socket.cornerRun(),
                            socket.straightRun()
                        )
                        : socket
                )
                .collect(java.util.stream.Collectors.toList());
        }
        // The frontier we just consumed is no longer open; remove it and add the piece's remaining doorways.
        location.frontierSockets().remove(connectedTo);
        // [stated] "every doorway has two jigsaws and always have" - one socket registers PER JIGSAW, so every
        // doorway carries a twin entry (same chunk, same facing; the doorway never crosses the chunk border).
        // Placing a royal hall consumes the DOORWAY, not the jigsaw: every remaining royal socket on this
        // doorway - same facing, same chunk or the immediate neighbour - goes with it, or the leftover twin
        // invites a second hall beside the first.
        if (connectedTo != null && connectedTo.doorType() != null && isTwinnedSpecialDoor(connectedTo.doorType())) {
            var consumedType = connectedTo.doorType();
            location.frontierSockets()
                .removeIf(
                    other -> consumedType.equals(other.doorType())
                        && other.facing() == connectedTo.facing()
                        && other.chunk().getChessboardDistance(connectedTo.chunk()) <= 1
                );
        }
        location.frontierSockets().addAll(newSockets);

        Alien.LOGGER.info(
            "Placed hive piece {} (rot {}) at {}; {} new frontier sockets.",
            match.piece().id(),
            match.rotation(),
            originChunk,
            newSockets.size()
        );
    }

    /**
     * Empties water/lava from EXACTLY the piece's authored air cells. filterBlocks resolves the template's AIR entries
     * through the same settings the stamp used, so this list IS the interior - rotated, offset, and minus the
     * structure_void margins. A fluid cell outside this list belongs to the world, not the hive, and stays. The old
     * chunk-sweeping drain (and its unused per-chunk re-drain entry) are gone; mid-carve leaks are already plugged
     * face-by-face by CarveSiteWork.sealLiquidNeighbours, and upkeep re-stamps route through placeWorld and inherit
     * this drain.
     */
    private static void drainStructureAir(ServerLevel level, ResolvedPlacement resolved) {
        var airCells = resolved.template()
            .filterBlocks(resolved.placeAt(), resolved.settings(), net.minecraft.world.level.block.Blocks.AIR);
        for (var cell : airCells) {
            var pos = cell.pos();
            var state = level.getBlockState(pos);
            if (state.getFluidState().isEmpty()) {
                continue;
            }
            if (
                state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
                    && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
            ) {
                level.setBlock(
                    pos,
                    state.setValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,
                        Boolean.FALSE
                    ),
                    2
                );
            } else {
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    /**
     * The offset that keeps a piece rotated around pivot (0,0,0) aligned with its origin chunk. Rotating block coords
     * around the corner pushes 90/180/270 rotations into negative space; adding this offset shifts the rotated
     * footprint back so its min corner sits at the origin. Uses (size-1) since block coords run 0..size-1.
     */
    private static BlockPos rotationOffset(Rotation rotation, int sizeX, int sizeZ) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(sizeZ - 1, 0, 0);
            case CLOCKWISE_180 -> new BlockPos(sizeX - 1, 0, sizeZ - 1);
            case COUNTERCLOCKWISE_90 -> new BlockPos(0, 0, sizeX - 1);
            default -> BlockPos.ZERO;
        };
    }

    /** The structure role a placed piece's chunks get, by piece category. */
    private static HiveStructureRole roleFor(HivePiece piece) {
        String path = piece.id().getPath();
        if (path.contains("hallway_royal")) {
            return HiveStructureRole.ROYAL_HALLWAY;
        }
        if (path.contains("hallway")) {
            return HiveStructureRole.HALLWAY;
        }
        if (path.contains("hub")) {
            return HiveStructureRole.JUNCTION;
        }
        if (path.contains("jelly")) {
            return HiveStructureRole.JELLY_CHAMBER;
        }
        return HiveStructureRole.CHAMBER;
    }
}
