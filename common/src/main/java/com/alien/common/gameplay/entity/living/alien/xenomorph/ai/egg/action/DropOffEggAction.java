package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg.action;

import com.alien.common.gameplay.entity.living.alien.ovomorph.Ovomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg.QueenEggZone;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.structure.HiveChamberSlots;
import com.alien.common.gameplay.hive.structure.HostEggDelivery;
import com.alien.common.gameplay.hive.vent.HiveVents;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.goap.v1.action.impl.NeoMoveToPosAction;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.action.Action;
import com.just.ai.goap.state.Blackboard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
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

    private static final StateKey<BlockPos> KEY_VENT_ENTRY = StateKey.sensed("egg_drop_vent_entry");

    private static final StateKey<BlockPos> KEY_VENT_EXIT = StateKey.sensed("egg_drop_vent_exit");

    private static final StateKey<Integer> KEY_TARGET_SET_TICK = StateKey.sensed("egg_drop_target_set_tick");

    private static final double DROP_OFF_RANGE_SQUARED = 2.0 * 2.0;

    private static final double VENT_REACH_SQUARED = 2.5 * 2.5; // close enough to slip into a vent

    private static final double VENT_WORTHWHILE_DIST_SQUARED = 32.0 * 32.0; // shorter hauls just walk

    private static final int VENT_SEARCH_RADIUS_CHUNKS = 1; // vents within ~a chunk of each endpoint

    private static final int SEARCH_RETRY_DELAY_TICKS = 20;

    private static final int MAX_REMEMBERED_FAILED_SPOTS = 32;

    private static final int MAX_PATH_VALIDATION_ATTEMPTS = 24;

    private static final int STUCK_TIMEOUT_TICKS = 200; // ~10s pursuing one spot without arriving -> re-pick

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
            // Amnesty: a large failed set means the whole nursery got poisoned (range quirks, temporary
            // blockages). Forget and retry rather than falling back to the queen forever.
            if (failedSpots.size() > 24) {
                failedSpots.clear();
            }
            // STORAGE FIRST: haul the egg to a free bed in an egg chamber while any exists - eggs only accumulate
            // around the queen (the original spiral search below) once the nursery chambers are full or unreachable.
            // HOST DELIVERY FIRST: an embedded host awaiting an egg outranks nursery storage - the egg opens in
            // front of it and the facehugger attaches. Falls through to nursery/spiral when no host needs one.
            java.util.Optional<net.minecraft.core.BlockPos> hostDrop = java.util.Optional.empty();
            if (xenomorph.level() instanceof net.minecraft.server.level.ServerLevel hostDropLevel) {
                var hostDropLocation = HiveLocationRegistry.INSTANCE.getByChunk(
                    hostDropLevel.dimension(),
                    xenomorph.chunkPosition()
                );
                if (hostDropLocation != null) {
                    hostDrop = HostEggDelivery.findAwaitingHostEggDrop(hostDropLevel, hostDropLocation);
                }
            }
            var isHostDrop = hostDrop.isPresent();
            var freeSpot = isHostDrop ? hostDrop : findChamberBedSpot(xenomorph, failedSpots);
            var isChamberBed = !isHostDrop && freeSpot.isPresent();
            if (freeSpot.isEmpty()) {
                // OVERFLOW: nurseries full/unreachable -> the queen's clutch zone (a bounded patch in FRONT of
                // her). The old fallback spiralled outward from the HAULER's position, which pushed overflow eggs
                // into hallways and doorways. The zone is anchored to the queen and eggs can never leave it.
                freeSpot = findQueenZoneSpot(xenomorph, failedSpots);
            }
            setFailedSpots(blackboard, failedSpots);

            if (freeSpot.isEmpty()) {
                scheduleSearchRetry(blackboard, xenomorph.tickCount);
                return Action.Signal.CONTINUE;
            }

            targetPos = freeSpot.get().getCenter();
            blackboard.set(KEY_TARGET_POS, targetPos);
            blackboard.set(KEY_TARGET_SET_TICK, xenomorph.tickCount);
            if (isChamberBed || isHostDrop) {
                planVentLeg(xenomorph, freeSpot.get(), blackboard);
            }
        }

        if (targetPos == null) {
            return Action.Signal.ABORT;
        }

        // Duct leg: while an entry vent is planned, head there first; on reaching it the drone (egg riding
        // along) duct-travels to the exit vent near the chamber, and the normal walk to the bed resumes from
        // there. Any pathing trouble on this leg just abandons the duct and walks the whole way.
        var ventEntry = blackboard.getOrDefault(KEY_VENT_ENTRY, (BlockPos) null);
        if (ventEntry != null) {
            var ventTarget = Vec3.atCenterOf(ventEntry);
            var ventResult = NeoMoveToPosAction.perform(context, ventTarget, 0.5);
            if (xenomorph.distanceToSqr(ventTarget) <= VENT_REACH_SQUARED) {
                var ventExit = blackboard.getOrDefault(KEY_VENT_EXIT, (BlockPos) null);
                blackboard.set(KEY_VENT_ENTRY, (BlockPos) null);
                blackboard.set(KEY_VENT_EXIT, (BlockPos) null);
                NeoMoveToPosAction.onFinish(context);
                if (ventExit != null) {
                    HiveVents.ductTravel(xenomorph, ventEntry, ventExit);
                }
                return Action.Signal.CONTINUE;
            }
            switch (ventResult) {
                case MOVING -> { /* still walking to the vent */ }
                default -> {
                    blackboard.set(KEY_VENT_ENTRY, (BlockPos) null);
                    blackboard.set(KEY_VENT_EXIT, (BlockPos) null);
                    NeoMoveToPosAction.onFinish(context);
                }
            }
            return Action.Signal.CONTINUE;
        }

        var result = NeoMoveToPosAction.perform(context, targetPos, 0.5);
        var arrived = xenomorph.distanceToSqr(targetPos) <= DROP_OFF_RANGE_SQUARED;

        return switch (result) {
            case MOVING -> {
                if (arrived) {
                    yield arriveAtTarget(context, xenomorph, blackboard, targetPos);
                }
                // Wedged against another hauler, or orbiting a node it can't quite reach: after a generous window
                // give up on this spot and pick a different bed instead of freezing here holding the egg forever.
                if (isTargetStale(blackboard, xenomorph.tickCount)) {
                    yield abandonTarget(context, blackboard, targetPos, xenomorph.tickCount);
                }
                yield Action.Signal.CONTINUE;
            }
            case FINISHED -> {
                if (arrived) {
                    yield arriveAtTarget(context, xenomorph, blackboard, targetPos);
                }
                // Navigation completed but the drone is still short of the bed - the path was truncated (bed boxed
                // in, or another drone/egg blocking the final step). Without this branch the drone re-finishes every
                // tick and freezes holding the egg. Blacklist the spot and re-search.
                yield abandonTarget(context, blackboard, targetPos, xenomorph.tickCount);
            }
            case NO_PATH -> abandonTarget(context, blackboard, targetPos, xenomorph.tickCount);
            default -> abandonTarget(context, blackboard, targetPos, xenomorph.tickCount);
        };
    }

    public static void onFinish(Action.Context<? extends Xenomorph> context) {
        NeoMoveToPosAction.onFinish(context);
    }

    /** Root the carried egg(s) at the target, unless another drone claimed the spot mid-approach (then re-search). */
    private static Action.Signal arriveAtTarget(
        Action.Context<? extends Xenomorph> context,
        Xenomorph xenomorph,
        Blackboard blackboard,
        Vec3 targetPos
    ) {
        if (isSpotTaken(xenomorph, targetPos)) {
            return abandonTarget(context, blackboard, targetPos, xenomorph.tickCount);
        }
        placeEggs(xenomorph, targetPos);
        return Action.Signal.CONTINUE;
    }

    /** Give up on the current target: blacklist it, clear the search so a fresh spot is picked, stop navigating. */
    private static Action.Signal abandonTarget(
        Action.Context<? extends Xenomorph> context,
        Blackboard blackboard,
        Vec3 targetPos,
        int currentTick
    ) {
        rememberFailedSpot(blackboard, BlockPos.containing(targetPos));
        scheduleSearchRetry(blackboard, currentTick);
        NeoMoveToPosAction.onFinish(context);
        return Action.Signal.CONTINUE;
    }

    /** True once the drone has been pursuing the current target longer than the stuck window without arriving. */
    private static boolean isTargetStale(Blackboard blackboard, int currentTick) {
        var setTick = blackboard.getOrDefault(KEY_TARGET_SET_TICK, currentTick);
        return currentTick - setTick > STUCK_TIMEOUT_TICKS;
    }

    /** True when another ovomorph already occupies (or a race just claimed) the spot - the caller re-searches. */
    private static boolean isSpotTaken(Xenomorph xenomorph, Vec3 center) {
        var box = new AABB(center.x - 0.6, center.y - 0.5, center.z - 0.6, center.x + 0.6, center.y + 1.5, center.z + 0.6);
        return !xenomorph.level()
            .getEntitiesOfClass(
                Ovomorph.class,
                box,
                e -> e.isAlive() && e.getVehicle() != xenomorph
            )
            .isEmpty();
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
     * Plans the duct leg for a long chamber haul: nearest vent to the drone as entry, nearest vent to the bed as exit -
     * only when both exist, differ, the walk is long enough to be worth it, and the exit genuinely shortens the
     * remaining trip.
     */
    private static void planVentLeg(Xenomorph xenomorph, BlockPos bed, Blackboard blackboard) {
        if (!(xenomorph.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (xenomorph.blockPosition().distSqr(bed) < VENT_WORTHWHILE_DIST_SQUARED) {
            return;
        }
        var location = HiveLocationRegistry.INSTANCE.getByChunk(serverLevel.dimension(), xenomorph.chunkPosition());
        if (location == null) {
            return;
        }
        var vents = location.ventManager();
        // Egg carriers NEVER use surface vents - those are the hive's defensive/party mouths. Interior only.
        var band = HiveLocationRegistry.INSTANCE.config().surfacePartySurfaceBandBlocks();
        java.util.function.Predicate<BlockPos> interiorOnly = v -> !HiveVents.isNearSurface(serverLevel, v, band);
        var entry = HiveVents.nearestVent(vents, xenomorph.blockPosition(), VENT_SEARCH_RADIUS_CHUNKS, interiorOnly);
        var exit = HiveVents.nearestVent(vents, bed, VENT_SEARCH_RADIUS_CHUNKS, interiorOnly);
        if (entry == null || exit == null || entry.equals(exit)) {
            return;
        }
        if (exit.distSqr(bed) >= xenomorph.blockPosition().distSqr(bed)) {
            return; // the duct wouldn't shorten the trip
        }
        blackboard.set(KEY_VENT_ENTRY, entry);
        blackboard.set(KEY_VENT_EXIT, exit);
    }

    /**
     * The nearest free, reachable egg-chamber bed in the drone's hive: chambers sorted by distance, beds read from the
     * tendril-floor slots, a bed counting as free when no rooted ovomorph sits on it. Unreachable beds join the
     * failed-spot memory so retries skip them; empty result means the nursery is full/absent and the caller falls back
     * to the around-the-queen spiral.
     */
    /** A free cell in the queen's clutch zone (bounded patch in front of her), nearest to her first. */
    private static Optional<BlockPos> findQueenZoneSpot(Xenomorph xenomorph, Set<BlockPos> failedSpots) {
        if (!(xenomorph.level() instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }
        var location = HiveLocationRegistry.INSTANCE.getByChunk(serverLevel.dimension(), xenomorph.chunkPosition());
        if (location == null || location.founderId() == null) {
            return Optional.empty();
        }
        var queen = serverLevel.getEntity(location.founderId()) instanceof Queen q ? q : null;
        if (queen == null) {
            return Optional.empty();
        }
        for (var pos : QueenEggZone.candidates(serverLevel, queen)) {
            if (failedSpots.contains(pos)) {
                continue;
            }
            return Optional.of(pos);
        }
        return Optional.empty();
    }

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
                // NO path pre-check: the navigator's follow-range makes distant beds read "unreachable" at
                // search time even though the walk (or the vent duct) handles them fine - the pre-check was
                // poisoning every bed into the failed set and stranding all eggs at the queen. Real navigation
                // failures still land in failedSpots through the NO_PATH branch.
                return Optional.of(bed);
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
