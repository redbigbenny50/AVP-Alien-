package com.alien.common.gameplay.hive.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a raw structure NBT ({@link CompoundTag}, as produced by the vanilla structure block / saved to .nbt) into a
 * {@link HivePiece}: footprint, typed doorway sockets, and functional positions (egg beds, jelly vats, vents).
 * <p>
 * Works directly on the NBT rather than a {@code StructureTemplate} so it is pure and testable (NBT in, HivePiece out)
 * and independent of version-specific template internals. The NBT layout used:
 * <ul>
 * <li>{@code size}: list of 3 ints [x,y,z] in blocks.</li>
 * <li>{@code palette}: list of block states, each {@code {Name: "...", Properties: {...}}}.</li>
 * <li>{@code blocks}: list of {@code {pos: [x,y,z], state: <palette index>, nbt?: {...}}}. Jigsaw blocks carry a block
 * entity nbt with {@code name}, {@code target}, {@code final_state}, {@code joint}.</li>
 * </ul>
 *
 * Door types recognized as sockets are any jigsaw whose {@code name} starts with {@code avp_alien:hive}. Adjacent
 * jigsaws of the same type on the same edge are collapsed into one {@link DoorwaySocket} (the even-width paired-jigsaw
 * doorway).
 */
public final class HivePieceParser {

    private static final String JELLY_ROYAL_VAT_BLOCK = "avp_alien:royal_jelly_vat";
    private static final String JELLY_SCOURGE_VAT_BLOCK = "avp_alien:scourge_jelly_vat";
    private static final String TENDRIL_BLOCK = "avp_alien:resin_tendril";
    private static final String VENT_BLOCK = "avp_alien:resin_vent";

    private HivePieceParser() {}

    public static HivePiece parse(ResourceLocation id, CompoundTag root) {
        var size = root.getList("size", Tag.TAG_INT);
        int sizeX = size.getInt(0);
        int sizeY = size.getInt(1);
        int sizeZ = size.getInt(2);

        var palette = root.getList("palette", Tag.TAG_COMPOUND);
        var blocks = root.getList("blocks", Tag.TAG_COMPOUND);

        int footprintX = Math.max(1, Math.round(sizeX / 16.0f));
        int footprintZ = Math.max(1, Math.round(sizeZ / 16.0f));

        // Raw jigsaw records (position + door type) before pairing.
        var rawJigsaws = new ArrayList<RawJigsaw>();
        var eggBeds = new ArrayList<BlockPos>();
        var royalVats = new ArrayList<BlockPos>();
        var scourgeVats = new ArrayList<BlockPos>();
        var vents = new ArrayList<BlockPos>();

        for (int i = 0; i < blocks.size(); i++) {
            var block = blocks.getCompound(i);
            var posList = block.getList("pos", Tag.TAG_INT);
            var pos = new BlockPos(posList.getInt(0), posList.getInt(1), posList.getInt(2));
            int stateIdx = block.getInt("state");
            String blockName = paletteName(palette, stateIdx);

            // Jigsaw doorway?
            if (block.contains("nbt", Tag.TAG_COMPOUND)) {
                var be = block.getCompound("nbt");
                if (be.contains("name")) {
                    String name = be.getString("name");
                    if (name.startsWith(Alien_HIVE_PREFIX)) {
                        rawJigsaws.add(new RawJigsaw(pos, name));
                        continue;
                    }
                }
            }

            // Functional blocks (detect by palette name).
            switch (blockName) {
                case TENDRIL_BLOCK -> {
                    // Egg beds: only the bottom-most row (y == 0). Tendril appears only on the floor of these rooms.
                    if (pos.getY() == 0) {
                        eggBeds.add(pos);
                    }
                }
                case VENT_BLOCK -> vents.add(pos);
                case JELLY_ROYAL_VAT_BLOCK -> royalVats.add(pos);
                case JELLY_SCOURGE_VAT_BLOCK -> scourgeVats.add(pos);
                default -> { /* structural block - ignored */ }
            }
        }

        var sockets = pairJigsaws(rawJigsaws, sizeX, sizeZ, footprintX, footprintZ);
        var functional = new FunctionalPositions(
                List.copyOf(eggBeds),
                List.copyOf(royalVats),
                List.copyOf(scourgeVats),
                List.copyOf(vents)
        );
        return new HivePiece(id, footprintX, footprintZ, List.copyOf(sockets), functional);
    }

    private static final String Alien_HIVE_PREFIX = "avp_alien:hive";

    /** Palette entry name for a given state index, or "" if out of range. */
    private static String paletteName(ListTag palette, int stateIdx) {
        if (stateIdx < 0 || stateIdx >= palette.size()) {
            return "";
        }
        return palette.getCompound(stateIdx).getString("Name");
    }

    /**
     * Collapses raw jigsaws into doorway sockets. Each doorway is a PAIR of adjacent jigsaws (even-width, on the
     * chunk-edge seam), so we group jigsaws by (door type, edge, edge-position) and emit one socket per group. Facing is
     * derived from which outer face the jigsaw sits on (the piece bounds), pointing outward.
     */
    private static List<DoorwaySocket> pairJigsaws(
            List<RawJigsaw> raws,
            int sizeX,
            int sizeZ,
            int footprintX,
            int footprintZ
    ) {
        var sockets = new ArrayList<DoorwaySocket>();
        var consumed = new boolean[raws.size()];

        for (int i = 0; i < raws.size(); i++) {
            if (consumed[i]) {
                continue;
            }
            var a = raws.get(i);
            var facing = outwardFacing(a.pos(), sizeX, sizeZ);
            if (facing == null) {
                // Not on an outer edge - skip (shouldn't happen for a well-authored doorway).
                consumed[i] = true;
                continue;
            }
            // Find its pair: same door type, same facing, adjacent along the edge (1 block apart on the edge axis).
            for (int j = i + 1; j < raws.size(); j++) {
                if (consumed[j]) {
                    continue;
                }
                var b = raws.get(j);
                if (!b.name().equals(a.name())) {
                    continue;
                }
                if (outwardFacing(b.pos(), sizeX, sizeZ) != facing) {
                    continue;
                }
                if (adjacentOnEdge(a.pos(), b.pos(), facing)) {
                    consumed[j] = true;
                    break;
                }
            }
            consumed[i] = true;

            // Which chunk cell (in the footprint grid) does this doorway belong to?
            int cellX = clampCell(a.pos().getX() / 16, footprintX);
            int cellZ = clampCell(a.pos().getZ() / 16, footprintZ);
            sockets.add(new DoorwaySocket(cellX, cellZ, facing, a.name()));
        }
        return sockets;
    }

    /** The outward horizontal face a block sits on given the piece bounds, or null if it isn't on an outer edge. */
    private static Direction outwardFacing(BlockPos pos, int sizeX, int sizeZ) {
        if (pos.getX() == 0) {
            return Direction.WEST;
        }
        if (pos.getX() == sizeX - 1) {
            return Direction.EAST;
        }
        if (pos.getZ() == 0) {
            return Direction.NORTH;
        }
        if (pos.getZ() == sizeZ - 1) {
            return Direction.SOUTH;
        }
        return null;
    }

    /** True if two jigsaws are 1 apart along the edge (the paired-jigsaw doorway), on the same face. */
    private static boolean adjacentOnEdge(BlockPos a, BlockPos b, Direction facing) {
        if (facing.getAxis() == Direction.Axis.X) {
            // On an X-face (west/east): pair runs along Z.
            return a.getX() == b.getX() && Math.abs(a.getZ() - b.getZ()) == 1;
        } else {
            // On a Z-face (north/south): pair runs along X.
            return a.getZ() == b.getZ() && Math.abs(a.getX() - b.getX()) == 1;
        }
    }

    private static int clampCell(int cell, int footprint) {
        return Math.max(0, Math.min(cell, footprint - 1));
    }

    private record RawJigsaw(BlockPos pos, String name) {}
}