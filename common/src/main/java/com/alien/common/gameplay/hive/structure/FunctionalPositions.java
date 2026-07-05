package com.alien.common.gameplay.hive.structure;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * The functional (non-connection) positions found inside a hive structure piece, located by the loader in one pass over
 * the piece's blocks. These drive the runtime systems that populate a placed room.
 * <p>
 * All positions are piece-relative (block coordinates within the unrotated template). The assembler rotates/translates
 * them to world coordinates when a piece is placed.
 *
 * @param eggBeds     floor-row {@code resin_tendril} positions; eggs are placed on top of these (up to the per-room
 *                    cap)
 * @param royalVats   positions of royal-jelly vats (fill from the hive's royal jelly bank)
 * @param scourgeVats positions of scourge-jelly vats (fill from the scourge bank)
 * @param vents       {@code resin_vent} positions; emergence/travel nodes. Facing (into the room) is derived at runtime
 *                    from the vent's position relative to the room center, so it is NOT stored here.
 */
public record FunctionalPositions(
    List<BlockPos> eggBeds,
    List<BlockPos> royalVats,
    List<BlockPos> scourgeVats,
    List<BlockPos> vents
) {

    public static FunctionalPositions empty() {
        return new FunctionalPositions(List.of(), List.of(), List.of(), List.of());
    }

    public int eggCapacity(int perRoomCap) {
        return Math.min(eggBeds.size(), perRoomCap);
    }
}
