package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg;

import com.alien.common.gameplay.entity.living.alien.ovomorph.Ovomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The queen's clutch zone: a rectangular patch of ground laid out IN FRONT of the queen, replacing the old
 * outward-growing spiral.
 * <p>
 * The spiral had two problems. It grew in rings around a center, so a full ring pushed eggs outward until they spilled
 * into hallways and doorways; and the haul fallback seeded it from the HAULER's position rather than the queen's, so
 * overflow eggs ended up wherever a drone happened to be standing. This zone is anchored to the queen, oriented by her
 * facing, and strictly bounded - eggs can never leave it.
 * <p>
 * Geometry: {@link #ZONE_WIDTH} blocks wide (side to side) x {@link #ZONE_DEPTH} deep (running away from her), starting
 * {@link #ZONE_FRONT_OFFSET} blocks in front of her. Candidates are returned nearest-to-queen first, so the clutch
 * fills from her outward and stays tight.
 */
public final class QueenEggZone {

    private QueenEggZone() {}

    /** Side-to-side extent of the clutch zone, centered on the queen's facing axis. */
    public static final int ZONE_WIDTH = 10;

    /** How far the zone extends away from the queen. */
    public static final int ZONE_DEPTH = 8;

    /** Gap between the queen and the near edge of the zone (she does not stand in her own clutch). */
    public static final int ZONE_FRONT_OFFSET = 2;

    /** Vertical slack when looking for standable ground within the zone. */
    private static final int VERTICAL_SEARCH_RANGE = 4;

    /** Minimum spacing between rooted eggs, so the clutch reads as a nest and stays walkable. */
    private static final double EGG_SPACING = 1.5;

    /**
     * Candidate egg cells in front of {@code queen}, nearest to her first. Empty when the queen is gone or the zone has
     * no standable ground.
     */
    public static List<BlockPos> candidates(Level level, @Nullable Queen queen) {
        var out = new ArrayList<BlockPos>();
        if (queen == null || !queen.isAlive()) {
            return out;
        }
        var origin = queen.blockPosition();
        var facing = queen.getDirection();
        var right = facing.getClockWise();

        int halfWidth = ZONE_WIDTH / 2;
        for (int depth = 0; depth < ZONE_DEPTH; depth++) {
            for (int lateral = -halfWidth; lateral < ZONE_WIDTH - halfWidth; lateral++) {
                var base = origin
                    .relative(facing, ZONE_FRONT_OFFSET + depth)
                    .relative(right, lateral);
                var spot = firstStandable(level, base);
                if (spot != null) {
                    out.add(spot);
                }
            }
        }
        out.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        return out;
    }

    /** True if {@code pos} lies inside the queen's clutch zone (bounds check for placement guards). */
    public static boolean contains(@Nullable Queen queen, BlockPos pos) {
        if (queen == null) {
            return false;
        }
        var origin = queen.blockPosition();
        var facing = queen.getDirection();
        var right = facing.getClockWise();

        int forward = distanceAlong(origin, pos, facing);
        int lateral = distanceAlong(origin, pos, right);
        int halfWidth = ZONE_WIDTH / 2;

        return forward >= ZONE_FRONT_OFFSET
            && forward < ZONE_FRONT_OFFSET + ZONE_DEPTH
            && lateral >= -halfWidth
            && lateral < ZONE_WIDTH - halfWidth;
    }

    private static int distanceAlong(BlockPos origin, BlockPos pos, Direction direction) {
        int dx = pos.getX() - origin.getX();
        int dz = pos.getZ() - origin.getZ();
        return dx * direction.getStepX() + dz * direction.getStepZ();
    }

    /** A free, standable cell at or near {@code base}'s column, or null if the column offers none. */
    private static @Nullable BlockPos firstStandable(Level level, BlockPos base) {
        for (int dy = 0; dy <= VERTICAL_SEARCH_RANGE; dy++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                var pos = base.above(dy * sign);
                if (isFreeEggCell(level, pos)) {
                    return pos.immutable();
                }
                if (dy == 0) {
                    break; // only test the base column once
                }
            }
        }
        return null;
    }

    private static boolean isFreeEggCell(Level level, BlockPos pos) {
        var below = level.getBlockState(pos.below());
        if (!below.isFaceSturdy(level, pos.below(), Direction.UP)) {
            return false;
        }
        var state = level.getBlockState(pos);
        var above = level.getBlockState(pos.above());
        if (!(state.isAir() || state.canBeReplaced()) || !(above.isAir() || above.canBeReplaced())) {
            return false;
        }
        if (!level.getEntities(null, new AABB(pos)).isEmpty()) {
            return false;
        }
        return hasEggSpacing(level, pos);
    }

    private static boolean hasEggSpacing(Level level, BlockPos pos) {
        var box = new AABB(pos).inflate(EGG_SPACING);
        return level.getEntitiesOfClass(Ovomorph.class, box).isEmpty();
    }
}
