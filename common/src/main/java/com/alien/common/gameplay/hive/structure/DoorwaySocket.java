package com.alien.common.gameplay.hive.structure;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

/**
 * A doorway on a hive structure piece - the unit of connection between pieces. Built from a PAIR of jigsaw blocks
 * (even-width, straddling the chunk-edge seam), collapsed into one logical socket.
 * <p>
 * Rotation-aware: {@link #rotated} returns the socket as it would sit after the piece is rotated, so the
 * assembler can test all four orientations of a piece against a frontier without re-parsing. This is what lets the elbow
 * (corner) hallway - and every other piece - orient to match whatever connection the layout needs.
 *
 * @param edgeChunkX the chunk-relative X (in chunks) of the piece cell this doorway sits on (0-based within footprint)
 * @param edgeChunkZ the chunk-relative Z (in chunks) of the piece cell this doorway sits on
 * @param facing the horizontal direction the doorway opens (outward through the opening)
 * @param doorType the socket type label (e.g. avp_alien:hive_door, hive_royal_door, hive_jelly_door, hive_scourge_door);
 *     only sockets with the same doorType connect
 */
public record DoorwaySocket(int edgeChunkX, int edgeChunkZ, Direction facing, String doorType) {

    /**
     * This socket as it would sit after the piece is rotated by {@code rotation} about the piece's footprint. Rotates
     * both the cell coordinates (about the footprint's chunk dimensions) and the facing. {@code footprintChunksX/Z} are
     * the piece's unrotated footprint size in chunks.
     */
    public DoorwaySocket rotated(Rotation rotation, int footprintChunksX, int footprintChunksZ) {
        var newFacing = facing.getAxis() == Direction.Axis.Y ? facing : rotation.rotate(facing);
        int cx = edgeChunkX;
        int cz = edgeChunkZ;
        // Rotate the cell coordinate within the footprint (chunk grid). Max index is (dim - 1).
        int maxX = footprintChunksX - 1;
        int maxZ = footprintChunksZ - 1;
        return switch (rotation) {
            case NONE -> new DoorwaySocket(cx, cz, newFacing, doorType);
            case CLOCKWISE_90 -> new DoorwaySocket(maxZ - cz, cx, newFacing, doorType);
            case CLOCKWISE_180 -> new DoorwaySocket(maxX - cx, maxZ - cz, newFacing, doorType);
            case COUNTERCLOCKWISE_90 -> new DoorwaySocket(cz, maxX - cx, newFacing, doorType);
        };
    }
}