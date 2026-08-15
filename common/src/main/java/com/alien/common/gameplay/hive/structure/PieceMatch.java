package com.alien.common.gameplay.hive.structure;

import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.List;

/**
 * A valid way to attach a piece to a frontier socket: which piece, at which rotation, with its footprint's minimum
 * corner at {@link #originChunk}. From these three the planner can derive every chunk the piece occupies and where its
 * still-open doorways land (to register as new frontier sockets).
 *
 * @param piece       the piece being placed
 * @param rotation    the rotation applied to it
 * @param originChunk the chunk holding the piece's rotated cell (0,0) - the min corner of its footprint
 */
public record PieceMatch(
    HivePiece piece,
    Rotation rotation,
    ChunkPos originChunk
) {

    /** Every chunk this placement occupies, in world chunk coordinates. */
    public List<ChunkPos> occupiedChunks() {
        int w = piece.rotatedFootprintX(rotation);
        int d = piece.rotatedFootprintZ(rotation);
        var chunks = new ArrayList<ChunkPos>(w * d);
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                chunks.add(new ChunkPos(originChunk.x + dx, originChunk.z + dz));
            }
        }
        return chunks;
    }

    /**
     * The world-space open doorways of this placement, EXCLUDING the one that connects back to the frontier it attached
     * to. Each becomes a new {@link FrontierSocket} the planner can grow from. The excluded socket is the one whose
     * cell is {@code connectedCell} and whose facing is {@code connectedFacing} (the mate of the frontier).
     */
    public List<FrontierSocket> openFrontierSockets(
        int connectedCellX,
        int connectedCellZ,
        Direction connectedFacing,
        int cornerRun,
        int straightRun
    ) {
        var result = new ArrayList<FrontierSocket>();
        for (DoorwaySocket s : piece.socketsRotated(rotation)) {
            boolean isTheConnection = s.edgeChunkX() == connectedCellX
                && s.edgeChunkZ() == connectedCellZ
                && s.facing() == connectedFacing;
            if (isTheConnection) {
                continue;
            }
            var chunk = new ChunkPos(originChunk.x + s.edgeChunkX(), originChunk.z + s.edgeChunkZ());
            result.add(new FrontierSocket(chunk, s.facing(), s.doorType(), cornerRun, straightRun));
        }
        return result;
    }
}
