package com.alien.common.gameplay.hive.structure;

import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.registry.init.block.AlienBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Room-type-scoped slot reader for functional chambers. A chamber's usable spots are the resin-tendril FLOOR row the
 * templates carry (tendrils at the hive floor with air above): egg chambers bed their ovomorphs there, jelly vaults
 * grow their vats there, and the drones that haul eggs place onto these positions. Tendrils are generic decoration
 * everywhere in the hive, so slots are only ever read for a chunk whose recorded piece is the right chamber - callers
 * decide the room type from {@code structurePieceByChunk}.
 * <p>
 * Slot choice is deterministic per chunk (seeded by the chunk), spaced so beds/vats spread across the floor, and kept
 * off the wall ring so doorways stay clear. Positions are the STANDING space (one block above the tendril).
 */
public final class HiveChamberSlots {

    /** Ovomorph beds per egg chamber (the ~20-egg hive target across ~5 chambers). */
    public static final int EGG_BEDS_PER_CHAMBER = 6;

    /** Jelly vats generated per vault. */
    public static final int VATS_PER_VAULT = 10;

    private static final int PREFERRED_SPACING = 3; // aim for this spread first...

    private static final int EGG_FLOOR_SPACING = 2; // ...eggs never sit closer than 1 clear block (6 always fit)

    private static final int VAT_FLOOR_SPACING = 1; // ...vats may touch as a last resort (guarantees the full 10)

    private static final int WALL_MARGIN = 2; // keep slots off the chunk edge (doorways stay clear)

    private HiveChamberSlots() {}

    /** The egg-bed standing positions for an egg-chamber chunk. */
    public static List<BlockPos> eggBedSlots(ServerLevel level, HiveLocation location, ChunkPos chamber) {
        return slots(level, location, chamber, EGG_BEDS_PER_CHAMBER, EGG_FLOOR_SPACING);
    }

    /** The vat positions for a jelly-vault chunk. */
    public static List<BlockPos> vatSlots(ServerLevel level, HiveLocation location, ChunkPos chamber) {
        return slots(level, location, chamber, VATS_PER_VAULT, VAT_FLOOR_SPACING);
    }

    /**
     * Picks up to {@code count} spaced slots from the chamber's tendril floor: a tendril at the hive floor row with two
     * blocks of air above it, inside the wall margin. Deterministic per chunk, so the same chamber always yields the
     * same slots.
     */
    /** Air, or the hive's own resin growth (veins/webs) - neither blocks an egg bed or a jelly vat. */
    private static boolean isEmptyForSlot(net.minecraft.world.level.block.state.BlockState state) {
        return state.isAir()
            || state.is(com.alien.common.registry.tag.AlienBlockTags.RESIN_VEINS)
            || state.is(com.alien.common.registry.tag.AlienBlockTags.RESIN_WEBS);
    }

    private static List<BlockPos> slots(ServerLevel level, HiveLocation location, ChunkPos chamber, int count, int floorSpacing) {
        int floorY = location.hiveFloorY();
        var candidates = new ArrayList<BlockPos>();
        var pos = new BlockPos.MutableBlockPos();
        for (int x = chamber.getMinBlockX() + WALL_MARGIN; x <= chamber.getMaxBlockX() - WALL_MARGIN; x++) {
            for (int z = chamber.getMinBlockZ() + WALL_MARGIN; z <= chamber.getMaxBlockZ() - WALL_MARGIN; z++) {
                pos.set(x, floorY, z);
                // TAG, not the NORMAL strain's block. This tested only AlienResinBlocks.RESIN_TENDRIL, so a nether
                // hive - whose floors are nether tendril - produced ZERO candidates: no vat slots and no egg
                // beds. That is both reported symptoms from one line, and it hit aberrant and irradiated too.
                if (!level.getBlockState(pos).is(com.alien.common.registry.tag.AlienBlockTags.RESIN_TENDRILS)) {
                    continue;
                }
                // A spot already holding a vat IS a slot - without this, every placed vat erased its own candidacy
                // (no air above), the "deterministic" pick shifted each pass, and regrows multiplied the vats.
                var above = level.getBlockState(pos.move(0, 1, 0));
                boolean occupiedByVat = above.is(AlienBlocks.JELLY_VAT.get());
                // Resin GROWTH (veins/webs) is the hive's own decoration and spreads over its floors constantly.
                // It must NOT disqualify a slot: a vein growing on a tendril used to delete that egg bed from the
                // chamber entirely, so haulers found "no free bed", stood holding their eggs forever, and only
                // moved again when someone physically broke the vein. Treat resin growth as empty space.
                if (
                    !occupiedByVat
                        && (!isEmptyForSlot(above) || !isEmptyForSlot(level.getBlockState(pos.move(0, 1, 0))))
                ) {
                    continue;
                }
                candidates.add(new BlockPos(x, floorY + 1, z));
            }
        }
        // Deterministic shuffle, then RELAXING selection: fill at PREFERRED_SPACING first, then re-sweep at ever
        // tighter spacing (down to floorSpacing) until the count is met - spread-first, densify only as needed.
        var random = RandomSource.create(chamber.toLong() ^ 0x51075L);
        for (int i = candidates.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            var tmp = candidates.get(i);
            candidates.set(i, candidates.get(j));
            candidates.set(j, tmp);
        }
        var chosen = new ArrayList<BlockPos>(count);
        for (int spacing = PREFERRED_SPACING; spacing >= floorSpacing && chosen.size() < count; spacing--) {
            for (BlockPos candidate : candidates) {
                if (chosen.size() >= count) {
                    break;
                }
                if (chosen.contains(candidate)) {
                    continue;
                }
                boolean spaced = true;
                for (BlockPos existing : chosen) {
                    if (
                        Math.max(
                            Math.abs(candidate.getX() - existing.getX()),
                            Math.abs(candidate.getZ() - existing.getZ())
                        ) < spacing
                    ) {
                        spaced = false;
                        break;
                    }
                }
                if (spaced) {
                    chosen.add(candidate);
                }
            }
        }
        return chosen;
    }
}
