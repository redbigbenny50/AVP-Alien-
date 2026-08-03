package com.alien.common.gameplay.hive.structure;

import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.registry.tag.AlienBlockTags;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Host-chamber spot reader. Unlike egg and jelly chambers - whose functional slots sit on the tendril FLOOR - a host
 * chamber embeds its captives into the resin WEBBING that lines the authored piece's walls and columns. A host spot is
 * therefore a web cell at standing height: a {@code resin_webs} block one above the hive floor, next to an open
 * interior cell. A delivered host is planted there (feet inside the webbing, which slows it - the block is
 * {@code noCollision} so the body genuinely occupies it), facing the open cell where its egg is later set. Players stay
 * mobile-but-slowed and can cut free; mobs are additionally frozen (see {@code HostParking}).
 * <p>
 * Host chambers are 2x2 (four chunks sharing a {@code chamber_host} piece id). This reader resolves each distinct 2x2
 * group's min-corner chunk exactly like {@link HarvestChamberTask}, then reads the webbing across the whole footprint.
 * Spots are chosen deterministically (seeded by the group) and spaced so captives spread around the room.
 */
public final class HostChamberSlots {

    /** Web spots picked per 2x2 host chamber - a budget, not a hard fence. */
    public static final int HOSTS_PER_CHAMBER = 8;

    /** Feet row: one block above the hive floor, where the densest wall/column webbing sits. */
    private static final int FEET_ABOVE_FLOOR = 1;

    private static final int PREFERRED_SPACING = 5;

    private static final int MIN_SPACING = 2;

    /** A host embedded within this radius of a spot marks it taken. */
    private static final double OCCUPIED_RADIUS = 1.25;

    private HostChamberSlots() {}

    /** A chosen web spot: the cell the host stands in, and the direction it faces (toward the open interior / egg). */
    public record Spot(
        BlockPos pos,
        Direction facing
    ) {}

    /**
     * Min-corner chunks of each distinct 2x2 host chamber (the four chunks sharing a {@code chamber_host} piece id).
     */
    public static List<ChunkPos> hostChamberGroups(HiveLocation location) {
        Map<ChunkPos, String> byChunk = location.structurePieceByChunk();
        var hostChunks = new HashSet<ChunkPos>();
        for (var entry : byChunk.entrySet()) {
            if (entry.getValue().contains("chamber_host")) {
                hostChunks.add(entry.getKey());
            }
        }
        var origins = new ArrayList<ChunkPos>();
        for (var chunk : hostChunks) {
            // EVERY full 2x2 square of host chunks is a group - deliberately including overlapping squares. The
            // old min-corner exclusivity meant TWO host chambers built touching each other merged into one blob
            // whose second half was never enumerated: its beds were invisible to firstFreeSpot and egg delivery
            // ([stated] "the second host chamber never gets used. the main one fills up and the second one sits
            // unused"). Overlapping origins re-list some wall cells; every consumer checks the actual world state
            // per cell (occupancy, existing eggs), so duplicates cost a few reads and change nothing.
            if (
                hostChunks.contains(new ChunkPos(chunk.x + 1, chunk.z))
                    && hostChunks.contains(new ChunkPos(chunk.x, chunk.z + 1))
                    && hostChunks.contains(new ChunkPos(chunk.x + 1, chunk.z + 1))
            ) {
                origins.add(chunk);
            }
        }
        return origins;
    }

    /**
     * Spaced web spots for the 2x2 host chamber whose min-corner chunk is {@code origin}: standing-height
     * {@code resin_webs} cells with an open front, deterministically thinned so captives spread out.
     */
    public static List<Spot> hostSpots(ServerLevel level, HiveLocation location, ChunkPos origin) {
        int y = location.hiveFloorY() + FEET_ABOVE_FLOOR;
        int minX = origin.x << 4;
        int minZ = origin.z << 4;
        int maxX = minX + 31;
        int maxZ = minZ + 31;

        var candidates = new ArrayList<Spot>();
        var pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                pos.set(x, y, z);
                if (!level.getBlockState(pos).is(AlienBlockTags.RESIN_WEBS)) {
                    continue;
                }
                var spotPos = new BlockPos(x, y, z);
                var facing = facing(level, spotPos, minX, minZ);
                if (facing == null) {
                    continue; // buried on all sides - no open front for the host to face / egg to sit
                }
                candidates.add(new Spot(spotPos, facing));
            }
        }

        // Deterministic shuffle (seeded by the group), then relaxing-spacing selection - spread first, densify only as
        // needed. Mirrors HiveChamberSlots.slots().
        var random = RandomSource.create(origin.toLong() ^ 0x40571L);
        for (int i = candidates.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            var tmp = candidates.get(i);
            candidates.set(i, candidates.get(j));
            candidates.set(j, tmp);
        }
        var chosen = new ArrayList<Spot>(HOSTS_PER_CHAMBER);
        for (int spacing = PREFERRED_SPACING; spacing >= MIN_SPACING && chosen.size() < HOSTS_PER_CHAMBER; spacing--) {
            for (Spot candidate : candidates) {
                if (chosen.size() >= HOSTS_PER_CHAMBER) {
                    break;
                }
                if (chosen.contains(candidate)) {
                    continue;
                }
                boolean spaced = true;
                for (Spot existing : chosen) {
                    if (
                        Math.max(
                            Math.abs(candidate.pos().getX() - existing.pos().getX()),
                            Math.abs(candidate.pos().getZ() - existing.pos().getZ())
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

    /** The cell in front of a spot (toward the open interior) where the host's egg is set. */
    public static BlockPos eggDropFor(Spot spot) {
        return spot.pos().relative(spot.facing());
    }

    /** Whether a non-alien living entity is already embedded at {@code spot} (aliens passing through do not count). */
    public static boolean isSpotOccupied(ServerLevel level, BlockPos spot) {
        var box = new AABB(spot).inflate(OCCUPIED_RADIUS);
        for (var entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity.getType().is(AlienEntityTypeTags.ALIENS)) {
                continue;
            }
            return true;
        }
        return false;
    }

    /** The first free web spot across all built, loaded host chambers of this hive, or {@code null} if none. */
    public static Spot firstFreeSpot(ServerLevel level, HiveLocation location) {
        for (var origin : hostChamberGroups(location)) {
            if (!level.isLoaded(origin.getWorldPosition())) {
                continue;
            }
            for (var spot : hostSpots(level, location, origin)) {
                if (!isSpotOccupied(level, spot.pos())) {
                    return spot;
                }
            }
        }
        return null;
    }

    /**
     * Facing from a webbed wall cell toward the open interior: the horizontal direction (biased toward the 2x2 centre)
     * whose neighbour is a two-tall open column. {@code null} if the cell has no open front.
     */
    /** Air, or the hive's own resin growth - neither blocks a host from being webbed to a wall. */
    private static boolean isOpenForHost(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.isAir()
            || state.is(com.alien.common.registry.tag.AlienBlockTags.RESIN_VEINS)
            || state.is(com.alien.common.registry.tag.AlienBlockTags.RESIN_WEBS);
    }

    private static Direction facing(ServerLevel level, BlockPos spot, int minX, int minZ) {
        int centreX = minX + 16;
        int centreZ = minZ + 16;
        Direction towardX = spot.getX() < centreX ? Direction.EAST : Direction.WEST;
        Direction towardZ = spot.getZ() < centreZ ? Direction.SOUTH : Direction.NORTH;
        for (Direction dir : new Direction[] { towardX, towardZ, towardX.getOpposite(), towardZ.getOpposite() }) {
            var front = spot.relative(dir);
            // Resin growth (veins/webs) spreads over the hive constantly. If a vein grew in front of a host spot,
            // facing() returned null, the spot silently stopped existing, firstFreeSpot went empty - and the hive
            // would refuse to dispatch a host hunt ("no free host-chamber spot"). Resin is not an obstruction.
            if (isOpenForHost(level, front) && isOpenForHost(level, front.above())) {
                return dir;
            }
        }
        return null;
    }
}
