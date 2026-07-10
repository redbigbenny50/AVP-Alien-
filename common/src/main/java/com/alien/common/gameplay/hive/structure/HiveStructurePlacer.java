package com.alien.common.gameplay.hive.structure;

import com.alien.Alien;
import com.alien.common.gameplay.hive.location.HiveLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

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
    public static boolean place(ServerLevel level, HiveLocation location, PieceMatch match, FrontierSocket connectedTo) {
        var server = level.getServer();
        var templateOpt = server.getStructureManager().get(match.piece().id());
        if (templateOpt.isEmpty()) {
            Alien.LOGGER.warn("Cannot place hive piece {} - template not found.", match.piece().id());
            return false;
        }
        StructureTemplate template = templateOpt.get();

        int floorY = location.hiveFloorY();

        // Place the template. placeInWorld already honors the carve contract from the authored piece: it writes air
        // into
        // the piece's air cells (clearing whatever terrain was there), places the resin structure blocks, and never
        // touches structure_void cells (they aren't in the block list) - so void regions blend with existing terrain.
        // No
        // separate box-carve: that would wrongly clear the void margins. Offset is the origin chunk's min corner at the
        // hive floor row; the authored piece sits with its own floor on that row.
        var originChunk = match.originChunk();
        // placeInWorld rotates block positions around pivot (0,0,0), which pushes some rotations into negative coords.
        // Correct the placement offset so the ROTATED footprint's min corner still lands at the origin chunk's min
        // block. The correction is (sizeX-1) and/or (sizeZ-1) depending on rotation (see rotationOffset).
        var size = template.getSize();
        var correction = rotationOffset(match.rotation(), size.getX(), size.getZ());
        var placeAt = new BlockPos(
            originChunk.getMinBlockX() + correction.getX(),
            floorY,
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
        boolean placed = template.placeInWorld(level, placeAt, placeAt, settings, RandomSource.create(), 2);
        if (!placed) {
            Alien.LOGGER.warn("Structure placement returned false for hive piece {}.", match.piece().id());
            return false;
        }

        // Record roles for the occupied chunks and register the piece's open doorways as new frontier sockets.
        String pieceId = match.piece().id().toString();
        for (ChunkPos chunk : match.occupiedChunks()) {
            location.assignStructure(chunk, roleFor(match.piece()), pieceId);
        }

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

        // The frontier we just consumed is no longer open; remove it and add the piece's remaining doorways.
        location.frontierSockets().remove(connectedTo);
        location.frontierSockets().addAll(newSockets);

        Alien.LOGGER.info(
            "Placed hive piece {} (rot {}) at {}; {} new frontier sockets.",
            match.piece().id(),
            match.rotation(),
            originChunk,
            newSockets.size()
        );
        return true;
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
