package com.alien.common.gameplay.hive.structure;

import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Finds valid ways to attach a piece to a frontier socket. Given an open doorway on the hive (a
 * {@link FrontierSocket}), it returns every {@link PieceMatch} - piece + rotation + origin - whose connecting doorway
 * lines up with the frontier and whose footprint lands entirely in free chunks.
 * <p>
 * A match requires three things: the piece has a socket of the SAME door type; that socket, once rotated, faces back at
 * the frontier (opposite the frontier's outward facing); and every chunk the placed piece would occupy passes the
 * caller's {@code chunkIsFree} test. The caller supplies that test (typically "unclaimed and undecorated"), keeping
 * this matcher pure geometry - no world or hive state - so it is cheap to call and easy to reason about.
 * <p>
 * This is pure, allocation-light, and event-driven by design: the planner calls it when it decides to grow, not on a
 * tick loop. Nothing here searches the world; it only tests candidate placements against the supplied predicate.
 */
public final class HivePieceMatcher {

    private static final Rotation[] ROTATIONS = Rotation.values();

    private HivePieceMatcher() {}

    /**
     * All valid placements of {@code piece} against {@code frontier}, across all four rotations.
     *
     * @param frontier    the open doorway to attach to
     * @param piece       the candidate piece
     * @param chunkIsFree returns true if a chunk is available to build in (unclaimed)
     */
    public static List<PieceMatch> matches(FrontierSocket frontier, HivePiece piece, Predicate<ChunkPos> chunkIsFree) {
        var results = new ArrayList<PieceMatch>();

        // The new piece attaches into the chunk on the far side of the frontier doorway, and its connecting socket must
        // face back the opposite way.
        ChunkPos targetChunk = neighbor(frontier.chunk(), frontier.facing());
        Direction requiredFacing = frontier.facing().getOpposite();

        for (Rotation rotation : ROTATIONS) {
            for (DoorwaySocket socket : piece.socketsRotated(rotation)) {
                if (!socket.doorType().equals(frontier.doorType())) {
                    continue;
                }
                if (socket.facing() != requiredFacing) {
                    continue;
                }
                // Place the piece so this socket's cell sits in targetChunk: origin = targetChunk - socketCell.
                var origin = new ChunkPos(
                    targetChunk.x - socket.edgeChunkX(),
                    targetChunk.z - socket.edgeChunkZ()
                );
                var match = new PieceMatch(piece, rotation, origin);
                if (allFree(match, chunkIsFree)) {
                    results.add(match);
                }
            }
        }
        return results;
    }

    /**
     * All valid placements of any catalog piece against {@code frontier}. Convenience over
     * {@link #matches(FrontierSocket, HivePiece, Predicate)} for every loaded piece of the frontier's door type.
     */
    public static List<PieceMatch> matchesFromRegistry(
        FrontierSocket frontier,
        HivePieceRegistry registry,
        Predicate<ChunkPos> chunkIsFree
    ) {
        return matchesFromRegistry(frontier, registry, chunkIsFree, null);
    }

    /** Strain-scoped variant: only pieces of the lineage variant's own strain set are considered. */
    public static List<PieceMatch> matchesFromRegistry(
        FrontierSocket frontier,
        HivePieceRegistry registry,
        Predicate<ChunkPos> chunkIsFree,
        @org.jetbrains.annotations.Nullable com.alien.common.model.alien.variant.AlienVariant variant
    ) {
        var results = new ArrayList<PieceMatch>();
        for (HivePiece piece : registry.piecesWithDoorType(frontier.doorType(), variant)) {
            results.addAll(matches(frontier, piece, chunkIsFree));
        }
        return results;
    }

    private static boolean allFree(PieceMatch match, Predicate<ChunkPos> chunkIsFree) {
        for (ChunkPos c : match.occupiedChunks()) {
            if (!chunkIsFree.test(c)) {
                return false;
            }
        }
        return true;
    }

    private static ChunkPos neighbor(ChunkPos chunk, Direction facing) {
        return new ChunkPos(chunk.x + facing.getStepX(), chunk.z + facing.getStepZ());
    }
}
