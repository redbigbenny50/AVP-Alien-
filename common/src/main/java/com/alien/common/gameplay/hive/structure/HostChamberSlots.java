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
        // \u2b50\u2b50 BUILD-FREE: WEB THEM ANYWHERE IN THE SLAB, PREFERRING NEAR EGGS.
        //
        // [stated] "with no room dedicated to it hosts will need to be brought inside the hive slab and then resin
        // webbed inplace. they can do this anywhere in the hive but it shoud occure close to eggs."
        //
        // \u2b50 THE WEBBING MECHANIC IS UNCHANGED AND ALREADY WORKED - HostParking.embed does the whole act, and
        // the debug command has been driving it for months. Only the SLOT SOURCE was chamber-bound. So this is a
        // finder, not a new system.
        if (com.alien.common.gameplay.hive.config.BuildFreeMode.isEnabled()) {
            return firstFreeBuildFreeSpot(level, location);
        }

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
     * Every host spot this hive has, in priority order - the set {@code HostEggDelivery} walks to find a webbed host
     * still waiting for its egg. Normal hives: the webbing of every built host chamber. Build-free: see
     * {@link #buildFreeSpots}.
     */
    public static List<Spot> allHostSpots(ServerLevel level, HiveLocation location) {
        if (com.alien.common.gameplay.hive.config.BuildFreeMode.isEnabled() && !location.isEndStyleHive()) {
            return buildFreeSpots(level, location);
        }
        var all = new ArrayList<Spot>();
        for (var origin : hostChamberGroups(location)) {
            if (level.isLoaded(origin.getWorldPosition())) {
                all.addAll(hostSpots(level, location, origin));
            }
        }
        return all;
    }

    /** The first unoccupied build-free spot, nearest the eggs first. */
    private static Spot firstFreeBuildFreeSpot(ServerLevel level, HiveLocation location) {
        for (var spot : buildFreeSpots(level, location)) {
            if (!isSpotOccupied(level, spot.pos())) {
                return spot;
            }
        }
        return null;
    }

    /**
     * ⭐⭐ BUILD-FREE HOSTS ARE WEBBED IN PLACE, NEXT TO THE EGGS - NOT STUCK TO WALLS THAT DO NOT EXIST.
     * <p>
     * 🚨🚨 THE OLD VERSION COULD NEVER RETURN A SPOT (Oct 1 audit). It reused the chamber reader, which looks for
     * EXISTING resin webbing one block above {@code hiveFloorY()} - and in build-free that is the bottom of a 48-block
     * band, 23 blocks under the queen, inside rock. Carriers held their host forever ("no free host-chamber spot"), and
     * every attempt scanned 32x32 blocks per CLAIMED chunk - up to ~370,000 block reads per call, repeated every tick
     * while a carrier held a host.
     * </p>
     * <p>
     * [stated] "webbing hosts in place instead of sticking them to already existing walls." ⇒ a spot is any ground cell
     * beside an egg cluster (then the queen's own chunk) that a host can stand in: solid floor at the cluster's
     * measured height, {@link com.alien.common.gameplay.hive.config.BuildFreeClusters#CLUSTER_HEADROOM} open cells for
     * tall hosts, and an open cell in front for its egg. The web is laid by {@code HostCaptureTask} at the moment the
     * host is embedded. Cells that are egg beds, or the cell in front of one, are never used, so hosts and eggs share
     * the cluster without taking each other's places.
     * </p>
     * <p>
     * ⚠ Bounded and cached: at most {@link #BUILD_FREE_SPOT_CHUNKS} chunks are read, and the list is held for
     * {@link #BUILD_FREE_SPOT_TTL_TICKS}. Occupancy is always checked live by the caller.
     * </p>
     */
    private static List<Spot> buildFreeSpots(ServerLevel level, HiveLocation location) {
        var now = level.getGameTime();
        var cached = BUILD_FREE_SPOTS.get(location);
        if (cached != null && now - cached.tick() < BUILD_FREE_SPOT_TTL_TICKS) {
            return cached.spots();
        }

        var center = new ChunkPos(location.centerPos());
        var chunks = new java.util.LinkedHashSet<ChunkPos>(
            com.alien.common.gameplay.hive.config.BuildFreeClusters.eggClusterChunks(location, center)
        );
        chunks.add(center);

        var spots = new ArrayList<Spot>();
        var read = 0;
        for (var chunk : chunks) {
            if (read++ >= BUILD_FREE_SPOT_CHUNKS) {
                break;
            }
            if (!level.isLoaded(chunk.getWorldPosition())) {
                continue;
            }
            spots.addAll(buildFreeSpotsIn(level, location, chunk));
        }

        var frozen = List.copyOf(spots);
        BUILD_FREE_SPOTS.put(location, new CachedSpots(now, frozen));
        return frozen;
    }

    private static List<Spot> buildFreeSpotsIn(ServerLevel level, HiveLocation location, ChunkPos chunk) {
        var floorY = com.alien.common.gameplay.hive.config.BuildFreeClusters.clusterFloorY(level, location, chunk);
        if (floorY == HiveLocation.NO_GROUND) {
            return List.of();
        }
        var standY = floorY + 1;

        var reserved = new HashSet<BlockPos>();
        for (var bed : HiveChamberSlots.eggBedSlots(level, location, chunk)) {
            reserved.add(bed);
            for (var dir : Direction.Plane.HORIZONTAL) {
                reserved.add(bed.relative(dir));
            }
        }

        var candidates = new ArrayList<Spot>();
        var floor = new BlockPos.MutableBlockPos();
        int minX = chunk.getMinBlockX() + 1;
        int minZ = chunk.getMinBlockZ() + 1;
        int maxX = chunk.getMaxBlockX() - 1;
        int maxZ = chunk.getMaxBlockZ() - 1;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                var spotPos = new BlockPos(x, standY, z);
                if (reserved.contains(spotPos)) {
                    continue;
                }
                floor.set(x, floorY, z);
                if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) {
                    continue; // no ground under the host
                }
                if (!com.alien.common.gameplay.hive.config.BuildFreeClusters.hasHeadroom(level, x, standY, z)) {
                    continue; // [stated] room for tall hosts
                }
                var facing = buildFreeFacing(level, spotPos, chunk, reserved);
                if (facing != null) {
                    candidates.add(new Spot(spotPos, facing));
                }
            }
        }

        var random = RandomSource.create(chunk.toLong() ^ 0xB0F7L);
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

    /**
     * The side a build-free host faces: toward the chunk's middle (where the cluster is) first, onto a cell with solid
     * ground and two open cells for the egg, and never onto a reserved bed cell.
     */
    private static Direction buildFreeFacing(ServerLevel level, BlockPos spot, ChunkPos chunk, java.util.Set<BlockPos> reserved) {
        Direction towardX = spot.getX() < chunk.getMiddleBlockX() ? Direction.EAST : Direction.WEST;
        Direction towardZ = spot.getZ() < chunk.getMiddleBlockZ() ? Direction.SOUTH : Direction.NORTH;
        for (Direction dir : new Direction[] { towardX, towardZ, towardX.getOpposite(), towardZ.getOpposite() }) {
            var front = spot.relative(dir);
            if (reserved.contains(front)) {
                continue;
            }
            var below = front.below();
            if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                continue; // the egg would fall
            }
            if (isOpenForHost(level, front) && isOpenForHost(level, front.above())) {
                return dir;
            }
        }
        return null;
    }

    /** Chunks read per rebuild: the egg clusters (max 8) plus the queen's own. */
    private static final int BUILD_FREE_SPOT_CHUNKS = 9;

    /** How long a build-free spot list is reused. Two seconds - occupancy is always checked live. */
    private static final long BUILD_FREE_SPOT_TTL_TICKS = 40L;

    private record CachedSpots(
        long tick,
        List<Spot> spots
    ) {}

    private static final Map<HiveLocation, CachedSpots> BUILD_FREE_SPOTS =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

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
