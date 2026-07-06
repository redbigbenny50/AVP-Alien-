package com.alien.common.gameplay.hive.structure;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

import java.util.List;

/**
 * A fully parsed hive structure piece - the loader's output for one template NBT. Immutable; computed once at load and
 * reused for every placement decision.
 * <p>
 * Holds the piece's footprint (in chunks), its typed doorway sockets, and its functional positions (egg beds, jelly
 * vats, vents). The assembler queries {@link #socketsRotated} to test each of the four orientations against a frontier
 * when deciding whether and how this piece can attach.
 *
 * @param id               the piece's resource id (e.g. avp_alien:hive/hallway/hallway_corner_1x1)
 * @param footprintChunksX footprint width in chunks (blockSizeX / 16)
 * @param footprintChunksZ footprint depth in chunks (blockSizeZ / 16)
 * @param sockets          the piece's doorway sockets in its authored (unrotated) orientation
 * @param functional       egg beds, jelly vats, and vents found inside the piece
 */
public record HivePiece(
    ResourceLocation id,
    int footprintChunksX,
    int footprintChunksZ,
    List<DoorwaySocket> sockets,
    FunctionalPositions functional
) {

    /** Total footprint area in chunks (e.g. a 2x2 chamber = 4). */
    public int footprintChunks() {
        return footprintChunksX * footprintChunksZ;
    }

    /** True if this piece occupies exactly one chunk. */
    public boolean isSingleChunk() {
        return footprintChunksX == 1 && footprintChunksZ == 1;
    }

    /** Footprint width (chunks) after the given rotation - X and Z swap under a 90 or 270 degree turn. */
    public int rotatedFootprintX(Rotation rotation) {
        return swapsAxes(rotation) ? footprintChunksZ : footprintChunksX;
    }

    /** Footprint depth (chunks) after the given rotation - X and Z swap under a 90 or 270 degree turn. */
    public int rotatedFootprintZ(Rotation rotation) {
        return swapsAxes(rotation) ? footprintChunksX : footprintChunksZ;
    }

    private static boolean swapsAxes(Rotation rotation) {
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
    }

    /** This piece's sockets as they sit after the given rotation - used to test orientations against a frontier. */
    public List<DoorwaySocket> socketsRotated(Rotation rotation) {
        return sockets.stream()
            .map(s -> s.rotated(rotation, footprintChunksX, footprintChunksZ))
            .toList();
    }

    /** Count of sockets of a given door type (in any rotation - rotation doesn't change counts). */
    public long socketCount(String doorType) {
        return sockets.stream().filter(s -> s.doorType().equals(doorType)).count();
    }

    public boolean hasSocketType(String doorType) {
        return sockets.stream().anyMatch(s -> s.doorType().equals(doorType));
    }
}
