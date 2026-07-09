package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg.action;

import com.alien.common.gameplay.entity.living.alien.ovomorph.Ovomorph;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.structure.HiveChamberSlots;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.goap.v1.action.impl.NeoMoveToPosAction;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.action.Action;
import com.just.ai.goap.state.Blackboard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public class DropOffEggAction {

    private static final StateKey<Vec3> KEY_TARGET_POS = StateKey.sensed("egg_drop_target_pos");

    private static final StateKey<Boolean> KEY_HAS_SEARCHED = StateKey.sensed("egg_drop_has_searched");

    private static final StateKey<List<BlockPos>> KEY_FAILED_SPOTS = StateKey.sensed("egg_drop_failed_spots");

    private static final StateKey<Integer> KEY_NEXT_SEARCH_TICK = StateKey.sensed("egg_drop_next_search_tick");

    private static final double DROP_OFF_RANGE_SQUARED = 2.0 * 2.0;

    private static final int SEARCH_RETRY_DELAY_TICKS = 20;

    private static final int MAX_REMEMBERED_FAILED_SPOTS = 32;

    private static final int MAX_PATH_VALIDATION_ATTEMPTS = 24;

    private static final int EGG_GRID_SPACING_BLOCKS = 3;

    private static final double MIN_HORIZONTAL_OVOMORPH_SPACING_BLOCKS = 2.0;

    private static final List<BlockPos> EGG_GRID_POS_OFFSETS = generateSpiralOffsets(16);

    public static Action.Signal perform(Action.Context<? extends Xenomorph> context) {
        var xenomorph = context.getActor();
        var blackboard = context.getBlackboard(Blackboard.Scope.ACTION);

        if (!isCarryingOvomorph(xenomorph)) {
            return Action.Signal.CONTINUE;
        }

        var hasSearched = blackboard.getOrDefault(KEY_HAS_SEARCHED, false);
        var targetPos = blackboard.getOrDefault(KEY_TARGET_POS, (Vec3) null);

        if (!hasSearched || targetPos == null) {
            var nextSearchTick = blackboard.getOrDefault(KEY_NEXT_SEARCH_TICK, 0);

            if (xenomorph.tickCount < nextSearchTick) {
                return Action.Signal.CONTINUE;
            }

            blackboard.set(KEY_HAS_SEARCHED, true);

            var failedSpots = getFailedSpots(blackboard);
            // STORAGE FIRST: haul the egg to a free bed in an egg chamber while any exists - eggs only accumulate
            // around the queen (the original spiral search below) once the nursery chambers are full or unreachable.
            var freeSpot = findChamberBedSpot(xenomorph, failedSpots);
            if (freeSpot.isEmpty()) {
                freeSpot = findFreeEggSpot(
                        xenomorph,
                        xenomorph.level(),
                        xenomorph.blockPosition(),
                        pos -> xenomorph.level().getBlockState(pos).entityCanStandOn(xenomorph.level(), pos, xenomorph),
                        failedSpots
                );
            }
            setFailedSpots(blackboard, failedSpots);

            if (freeSpot.isEmpty()) {
                scheduleSearchRetry(blackboard, xenomorph.tickCount);
                return Action.Signal.CONTINUE;
            }

            targetPos = freeSpot.get().getCenter();
            blackboard.set(KEY_TARGET_POS, targetPos);
        }

        if (targetPos == null) {
            return Action.Signal.ABORT;
        }

        var result = NeoMoveToPosAction.perform(context, targetPos, 0.5);

        return switch (result) {
            case FINISHED, MOVING -> {
                if (xenomorph.distanceToSqr(targetPos) <= DROP_OFF_RANGE_SQUARED) {
                    placeEggs(xenomorph, targetPos);
                    yield Action.Signal.CONTINUE;
                }

                yield Action.Signal.CONTINUE;
            }
            case NO_PATH -> {
                rememberFailedSpot(blackboard, BlockPos.containing(targetPos));
                scheduleSearchRetry(blackboard, xenomorph.tickCount);
                NeoMoveToPosAction.onFinish(context);
                yield Action.Signal.CONTINUE;
            }
            default -> {
                rememberFailedSpot(blackboard, BlockPos.containing(targetPos));
                scheduleSearchRetry(blackboard, xenomorph.tickCount);
                NeoMoveToPosAction.onFinish(context);
                yield Action.Signal.CONTINUE;
            }
        };
    }

    public static void onFinish(Action.Context<? extends Xenomorph> context) {
        NeoMoveToPosAction.onFinish(context);
    }

    private static void placeEggs(Xenomorph xenomorph, Vec3 center) {
        getPassengerOvomorphs(xenomorph).forEach(ovomorph -> {
            xenomorph.level()
                    .playSound(null, ovomorph, AlienSoundEvents.ENTITY_OVOMORPH_ROOT.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
            ovomorph.isRooted.set(true);
            ovomorph.stopRiding();
            ovomorph.setPos(center.x, center.y, center.z);

            var randomYaw = xenomorph.getRandom().nextFloat() * 360.0F;

            ovomorph.setYRot(randomYaw);
            ovomorph.setYHeadRot(randomYaw);
            ovomorph.yBodyRot = randomYaw;
            ovomorph.yRotO = randomYaw;
            ovomorph.yHeadRotO = randomYaw;
            ovomorph.yBodyRotO = randomYaw;
        });
    }

    private static boolean isCarryingOvomorph(Xenomorph xenomorph) {
        return !getPassengerOvomorphs(xenomorph).isEmpty();
    }

    private static List<Ovomorph> getPassengerOvomorphs(Xenomorph xenomorph) {
        return xenomorph.getPassengers()
                .stream()
                .filter(passenger -> passenger instanceof Ovomorph)
                .map(passenger -> (Ovomorph) passenger)
                .toList();
    }

    /**
     * The nearest free, reachable egg-chamber bed in the drone's hive: chambers sorted by distance, beds read from
     * the tendril-floor slots, a bed counting as free when no rooted ovomorph sits on it. Unreachable beds join the
     * failed-spot memory so retries skip them; empty result means the nursery is full/absent and the caller falls
     * back to the around-the-queen spiral.
     */
    private static Optional<BlockPos> findChamberBedSpot(Xenomorph xenomorph, Set<BlockPos> failedSpots) {
        if (!(xenomorph.level() instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }
        var location = HiveLocationRegistry.INSTANCE.getByChunk(serverLevel.dimension(), xenomorph.chunkPosition());
        if (location == null) {
            return Optional.empty();
        }
        var chambers = new ArrayList<ChunkPos>();
        for (var entry : location.structurePieceByChunk().entrySet()) {
            if (entry.getValue().contains("chamber_egg")) {
                chambers.add(entry.getKey());
            }
        }
        if (chambers.isEmpty()) {
            return Optional.empty();
        }
        var here = xenomorph.chunkPosition();
        chambers.sort(Comparator.comparingInt(c -> Math.max(Math.abs(c.x - here.x), Math.abs(c.z - here.z))));

        for (var chamber : chambers) {
            if (!serverLevel.isLoaded(chamber.getWorldPosition())) {
                continue;
            }
            for (var bed : HiveChamberSlots.eggBedSlots(serverLevel, location, chamber)) {
                if (failedSpots.contains(bed) || isBedOccupied(serverLevel, bed)) {
                    continue;
                }
                var path = xenomorph.getNavigation().createPath(bed, 0);
                if (path != null && path.canReach()) {
                    return Optional.of(bed);
                }
                failedSpots.add(bed);
            }
        }
        return Optional.empty();
    }

    /** A bed is occupied while a rooted ovomorph sits within a block of it. */
    private static boolean isBedOccupied(ServerLevel level, BlockPos bed) {
        var center = bed.getCenter();
        var box = new AABB(center.x - 1.0, bed.getY() - 1.0, center.z - 1.0, center.x + 1.0, bed.getY() + 2.0, center.z + 1.0);
        return !level.getEntitiesOfClass(
                Ovomorph.class,
                box,
                entity -> entity.getType().is(AlienEntityTypeTags.OVOMORPHS) && entity.isRooted.get()
        ).isEmpty();
    }

    private static Optional<BlockPos> findFreeEggSpot(
            Xenomorph xenomorph,
            Level level,
            BlockPos center,
            Predicate<BlockPos> isWalkable,
            Set<BlockPos> failedSpots
    ) {
        var gridAlignedCenter = alignToEggGrid(center);

        var viable = new ArrayList<BlockPos>();

        for (var offset : EGG_GRID_POS_OFFSETS) {
            var pos = gridAlignedCenter.offset(offset);

            var verticalSearchRange = 4;

            for (var dy = -verticalSearchRange; dy <= verticalSearchRange; dy++) {
                var adjustedPos = pos.above(dy);

                if (failedSpots.contains(adjustedPos)) {
                    continue;
                }

                var below = adjustedPos.below();

                if (!isWalkable.test(below)) {
                    continue;
                }

                var state = level.getBlockState(adjustedPos);
                var aboveState = level.getBlockState(adjustedPos.above());

                if (
                        (state.isAir() || state.canBeReplaced())
                                && (aboveState.isAir() || aboveState.canBeReplaced())
                                && level.getEntities(null, new AABB(adjustedPos)).isEmpty()
                                && hasOvomorphSpacing(level, adjustedPos)
                ) {
                    viable.add(adjustedPos.immutable());
                }
            }
        }

        viable.sort(Comparator.comparingDouble(pos -> pos.distSqr(gridAlignedCenter)));

        for (var i = 0; i < Math.min(viable.size(), MAX_PATH_VALIDATION_ATTEMPTS); i++) {
            var pos = viable.get(i);
            var path = xenomorph.getNavigation().createPath(pos, 0);

            if (path != null && path.canReach()) {
                return Optional.of(pos);
            } else {
                failedSpots.add(pos);
            }
        }

        return Optional.empty();
    }

    private static BlockPos alignToEggGrid(BlockPos center) {
        var baseX = Math.floorDiv(center.getX(), EGG_GRID_SPACING_BLOCKS) * EGG_GRID_SPACING_BLOCKS;
        var baseZ = Math.floorDiv(center.getZ(), EGG_GRID_SPACING_BLOCKS) * EGG_GRID_SPACING_BLOCKS;
        return new BlockPos(baseX, center.getY(), baseZ);
    }

    private static boolean hasOvomorphSpacing(Level level, BlockPos pos) {
        var center = pos.getCenter();
        var halfSize = MIN_HORIZONTAL_OVOMORPH_SPACING_BLOCKS;
        var searchBox = new AABB(
                center.x - halfSize,
                pos.getY() - level.dimensionType().height(),
                center.z - halfSize,
                center.x + halfSize,
                pos.getY() + 5,
                center.z + halfSize
        );

        return level.getEntitiesOfClass(
                Ovomorph.class,
                searchBox,
                entity -> entity.getType().is(AlienEntityTypeTags.OVOMORPHS) && entity.isRooted.get()
        ).isEmpty();
    }

    private static Set<BlockPos> getFailedSpots(Blackboard blackboard) {
        return new HashSet<>(blackboard.getOrDefault(KEY_FAILED_SPOTS, List.<BlockPos>of()));
    }

    private static void rememberFailedSpot(Blackboard blackboard, BlockPos blockPos) {
        var failedSpots = getFailedSpots(blackboard);
        failedSpots.add(blockPos.immutable());
        setFailedSpots(blackboard, failedSpots);
    }

    private static void setFailedSpots(Blackboard blackboard, Set<BlockPos> failedSpots) {
        blackboard.set(
                KEY_FAILED_SPOTS,
                failedSpots.stream()
                        .limit(MAX_REMEMBERED_FAILED_SPOTS)
                        .toList()
        );
    }

    private static void scheduleSearchRetry(Blackboard blackboard, int currentTick) {
        blackboard.set(KEY_HAS_SEARCHED, false);
        blackboard.set(KEY_NEXT_SEARCH_TICK, currentTick + SEARCH_RETRY_DELAY_TICKS);
    }

    private static List<BlockPos> generateSpiralOffsets(int maxDist) {
        var step = EGG_GRID_SPACING_BLOCKS;
        var offsets = new ArrayList<BlockPos>();

        for (var dist = 0; dist <= maxDist; dist += step) {
            for (var dx = -dist; dx <= dist; dx += step) {
                var dz = dist - Math.abs(dx);

                offsets.add(new BlockPos(dx, 0, dz));

                if (dz != 0) {
                    offsets.add(new BlockPos(dx, 0, -dz));
                }
            }
        }

        return offsets;
    }

    private DropOffEggAction() {
        throw new UnsupportedOperationException();
    }
}