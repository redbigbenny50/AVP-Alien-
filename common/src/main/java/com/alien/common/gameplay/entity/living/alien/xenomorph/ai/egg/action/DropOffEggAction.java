package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg.action;

import com.alien.common.gameplay.entity.living.alien.ovomorph.Ovomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg.EggSpotClaims;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg.QueenEggZone;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.structure.HiveChamberSlots;
import com.alien.common.gameplay.hive.structure.HostEggDelivery;
import com.alien.common.gameplay.hive.vent.HiveVents;
import com.alien.common.gameplay.hive.vent.VentKind;
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

    /** How many times this hauler has abandoned its STAMPED host-drop target on this haul. */
    private static final StateKey<Integer> KEY_STAMP_ATTEMPTS = StateKey.sensed("egg_drop_stamp_attempts");

    /**
     * A stamped delivery survives this many abandons (stuck, no-path, truncated walk) before the stamp is cleared and
     * the egg falls back to the nursery. Early abandons do NOT blacklist the cell: the stuck path has already unlocked
     * the vents (KEY_WALK_FAILED), so the re-search re-picks the SAME cell and tries the duct instead of walking the
     * egg straight back to a nursery bed - the U-turn the tester kept watching.
     */
    private static final int STAMP_MAX_ATTEMPTS = 3;

    /** Walk attempts spent on the CURRENT nursery bed before it is written off. See {@code abandonTarget}. */
    private static final StateKey<Integer> KEY_BED_ATTEMPTS = StateKey.sensed("egg_drop_bed_attempts");

    /** One walk, then one ducted retry, then the bed is blacklisted. */
    private static final int BED_MAX_ATTEMPTS = 2;

    private static final double DROP_OFF_RANGE_SQUARED = 2.0 * 2.0;

    private static final double VENT_REACH_SQUARED = 2.5 * 2.5; // close enough to slip into a vent

    /** Abandon the duct shortcut and walk instead if the entry vent is not reached in this long (anti-freeze). */
    private static final int VENT_LEG_TIMEOUT_TICKS = 200;

    private static final StateKey<Integer> KEY_VENT_LEG_START_TICK = StateKey.sensed("egg_dropoff_vent_leg_start");

    /** Entry vents this hauler could not reach on this run - never offered to it again for this haul. */
    private static final StateKey<Set<BlockPos>> KEY_FAILED_VENTS = StateKey.sensed("egg_dropoff_failed_vents");

    /**
     * Set once a hauler has FAILED to walk to a spot. From then on the duct is on the table no matter how near the next
     * target looks: proximity is what made it choose to walk in the first place, and walking is what failed.
     */
    private static final StateKey<Boolean> KEY_WALK_FAILED = StateKey.sensed("egg_dropoff_walk_failed");

    private static final double VENT_WORTHWHILE_DIST_SQUARED = 32.0 * 32.0; // shorter hauls just walk

    /**
     * The ducted route must beat the direct walk by at least this many blocks to be worth taking. A margin (not just
     * "any shorter") keeps a hauler from flip-flopping between duct and walk when the two are near-equal.
     */
    private static final double VENT_MIN_SHORTCUT_BLOCKS = 8.0;

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

            // Entity tickCount RESETS TO 0 when the entity reloads, but the blackboard can still hold a retry tick
            // from the previous session (e.g. 50000). "tickCount < nextSearchTick" was then true FOREVER: the
            // hauler bailed out here every tick - never searching, never moving, never logging - and just stood
            // holding its egg. Anything further out than the retry delay is stale, so search immediately.
            var waitRemaining = nextSearchTick - xenomorph.tickCount;
            if (waitRemaining > 0 && waitRemaining <= SEARCH_RETRY_DELAY_TICKS) {
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
                var hostDropLocation = resolveLocation(hostDropLevel, xenomorph);
                var ownEgg = getPassengerOvomorphs(xenomorph).stream().findFirst().orElse(null);
                if (hostDropLocation != null && ownEgg != null) {
                    // STAMP FIRST: an egg the ferry (or a previous search) designated for a host cell already KNOWS
                    // where it is going - no query runs, so nothing else in the hive can hide the destination. Only
                    // re-validate that the host still wants it; a served/dead host clears the stamp and the egg
                    // becomes an ordinary nursery haul again.
                    var stamped = ownEgg.getHostDropTarget();
                    if (stamped != null) {
                        if (HostEggDelivery.isHostDropStillValid(hostDropLevel, hostDropLocation, stamped)) {
                            hostDrop = java.util.Optional.of(stamped);
                        } else {
                            ownEgg.setHostDropTarget(null);
                        }
                    }
                    if (hostDrop.isEmpty() && ownEgg.getHostDropTarget() == null) {
                        // UNSTAMPED egg (e.g. a fresh clutch egg being hauled to the nursery): it may still serve a
                        // waiting host opportunistically. The carrier-aware query ignores our own egg; a spot another
                        // hauler is already on its way to is NOT free (three drones once queued on ONE webbed host).
                        hostDrop = HostEggDelivery.findHostDropForCarrier(hostDropLevel, hostDropLocation, ownEgg)
                            .filter(spot -> !isClaimedByOther(xenomorph, spot));
                        // Self-stamp on the spot: from this instant the delivery is visible to the ferry gate and
                        // every other carrier, so no duplicate egg is released or routed for this cell.
                        hostDrop.ifPresent(ownEgg::setHostDropTarget);
                    }
                }
            }

            var isHostDrop = hostDrop.isPresent();
            var freeSpot = isHostDrop
                ? hostDrop
                : findChamberBedSpot(xenomorph, failedSpots)
                    .filter(spot -> !isClaimedByOther(xenomorph, spot));
            var isChamberBed = !isHostDrop && freeSpot.isPresent();
            if (freeSpot.isEmpty()) {
                // OVERFLOW: nurseries full/unreachable -> the queen's clutch zone (a bounded patch in FRONT of
                // her). The old fallback spiralled outward from the HAULER's position, which pushed overflow eggs
                // into hallways and doorways. The zone is anchored to the queen and eggs can never leave it.
                freeSpot = findQueenZoneSpot(xenomorph, failedSpots)
                    .filter(spot -> !isClaimedByOther(xenomorph, spot));
            }
            setFailedSpots(blackboard, failedSpots);

            if (freeSpot.isEmpty()) {
                // Nowhere to put this egg. Report WHY - a hauler frozen holding an egg with no log was the single
                // hardest thing to troubleshoot in testing.
                logNoEggDestination(xenomorph, blackboard);

                // AMNESTY ON EXHAUSTION. The size-based amnesty above can only fire while the failed set is still
                // GROWING - and growth stops at exactly the moment the hauler gets stuck: once every bed is
                // blacklisted no target is ever chosen, so no new failure is ever recorded. The set froze short of
                // the threshold (testers saw 14-21 entries against 12-17 genuinely FREE beds) and every retry
                // re-ran against the same poisoned list, leaving runners standing around holding eggs forever.
                // Reaching here means nothing at all was accepted, so the blacklist has no value left: drop it and
                // let the next retry reconsider the whole nursery. Spots that really are bad simply fail again.
                blackboard.set(KEY_FAILED_SPOTS, List.<BlockPos>of());

                scheduleSearchRetry(blackboard, xenomorph.tickCount);
                return Action.Signal.CONTINUE;
            }

            // Ours now - other haulers will look elsewhere. The claim expires by itself if we never arrive.
            claimSpot(xenomorph, freeSpot.get());
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
            // Approach a STANDABLE spot beside/below the vent, never the vent block itself: vents sit IN walls,
            // often 2-3 blocks up, so a hauler pathing at the vent centre can never reach it - NeoMoveToPos
            // returned MOVING forever and the drone stood frozen holding its egg with a perfectly valid target.
            var ventApproach = HiveVents.emergencePosNear(xenomorph.level(), ventEntry);
            var ventTarget = ventApproach != null
                ? Vec3.atBottomCenterOf(ventApproach)
                : Vec3.atCenterOf(ventEntry);
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
            // Give up on the duct if the entry vent is not reached in time: a hauler must never be trapped by its
            // own shortcut. Falls through to walking the whole way.
            var ventLegStart = blackboard.getOrDefault(KEY_VENT_LEG_START_TICK, xenomorph.tickCount);
            blackboard.set(KEY_VENT_LEG_START_TICK, ventLegStart);
            var ventLegElapsed = xenomorph.tickCount - ventLegStart;
            if (ventLegElapsed < 0 || ventLegElapsed > VENT_LEG_TIMEOUT_TICKS) {
                // Could not reach this entry vent (blocked, unreachable, bad geometry). Blacklist it for this
                // haul and re-plan onto the NEXT-nearest vent rather than abandoning the shortcut outright -
                // one bad vent should not cost the hauler its duct. Only when no usable vent remains does it walk.
                var failedVents = getFailedVents(blackboard);
                failedVents.add(ventEntry);
                setFailedVents(blackboard, failedVents);
                blackboard.set(KEY_VENT_ENTRY, (BlockPos) null);
                blackboard.set(KEY_VENT_EXIT, (BlockPos) null);
                blackboard.set(KEY_VENT_LEG_START_TICK, xenomorph.tickCount);
                NeoMoveToPosAction.onFinish(context);
                planVentLeg(xenomorph, BlockPos.containing(targetPos), blackboard);
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
                    // Could not WALK there in time. Unlock the duct for the rest of this haul: a nursery or host
                    // chamber only a short way off can still be unreachable on foot (a dead-end room, a wall, a
                    // drop), and simply re-picking another NEARBY spot walks straight back into the same trap.
                    // Carriers were filing into a dead-end room beside the queen and standing there forever,
                    // never reconsidering the vents, because proximity ruled ducting out before they set off.
                    blackboard.set(KEY_WALK_FAILED, true);
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
        EggSpotClaims.release(BlockPos.containing(targetPos)); // delivered - release the reservation
        blackboard.set(KEY_WALK_FAILED, false); // delivered - stop forcing the duct
        blackboard.set(KEY_STAMP_ATTEMPTS, 0); // delivered - the next stamped haul starts with a clean slate
        blackboard.set(KEY_BED_ATTEMPTS, 0); // delivered - the next bed starts with its full retry budget
        return Action.Signal.CONTINUE;
    }

    /** Give up on the current target: blacklist it, clear the search so a fresh spot is picked, stop navigating. */
    private static Action.Signal abandonTarget(
        Action.Context<? extends Xenomorph> context,
        Blackboard blackboard,
        Vec3 targetPos,
        int currentTick
    ) {
        var target = BlockPos.containing(targetPos);
        // A STAMPED host delivery is not given up lightly: early abandons keep the cell OFF the failed list so the
        // re-search re-picks it (with the vents now unlocked via KEY_WALK_FAILED). Only after the attempt cap does
        // the stamp clear - the egg goes to a nursery and the ferry re-serves the host after its cooldown.
        var stampedEgg = getPassengerOvomorphs(context.getActor())
            .stream()
            .filter(egg -> target.equals(egg.getHostDropTarget()))
            .findFirst()
            .orElse(null);
        if (stampedEgg != null) {
            var attempts = blackboard.getOrDefault(KEY_STAMP_ATTEMPTS, 0) + 1;
            blackboard.set(KEY_STAMP_ATTEMPTS, attempts);
            if (attempts < STAMP_MAX_ATTEMPTS) {
                EggSpotClaims.release(target); // giving up this leg - but NOT the delivery
                scheduleSearchRetry(blackboard, currentTick);
                NeoMoveToPosAction.onFinish(context);
                return Action.Signal.CONTINUE;
            }
            stampedEgg.setHostDropTarget(null);
            blackboard.set(KEY_STAMP_ATTEMPTS, 0);
        }
        // NURSERY BED, WALK FAILED: give the DUCT its turn before writing the bed off. KEY_WALK_FAILED was just
        // set, which unlocks ducting for this haul - but blacklisting the bed in the same breath meant the
        // re-search could never pick it again, so the ducted route to THAT bed was never attempted. Only stamped
        // host deliveries got the retry the design intended; ordinary nursery hauls were written off on the first
        // failed walk. A bed that is unreachable on foot but fine through the hive's own ducts therefore poisoned
        // the failed list one entry at a time until nothing was left to choose.
        var walkFailed = blackboard.getOrDefault(KEY_WALK_FAILED, false);
        var bedAttempts = blackboard.getOrDefault(KEY_BED_ATTEMPTS, 0) + 1;
        if (walkFailed && bedAttempts < BED_MAX_ATTEMPTS) {
            blackboard.set(KEY_BED_ATTEMPTS, bedAttempts);
            EggSpotClaims.release(target); // release the claim, but keep the bed selectable
            scheduleSearchRetry(blackboard, currentTick);
            NeoMoveToPosAction.onFinish(context);
            return Action.Signal.CONTINUE;
        }
        blackboard.set(KEY_BED_ATTEMPTS, 0);

        rememberFailedSpot(blackboard, target);
        EggSpotClaims.release(target); // giving up - let another hauler have it
        scheduleSearchRetry(blackboard, currentTick);
        NeoMoveToPosAction.onFinish(context);
        return Action.Signal.CONTINUE;
    }

    /** True once the drone has been pursuing the current target longer than the stuck window without arriving. */
    private static boolean isTargetStale(Blackboard blackboard, int currentTick) {
        var setTick = blackboard.getOrDefault(KEY_TARGET_SET_TICK, currentTick);
        // A set-tick in the FUTURE means the entity reloaded and its tickCount reset. Treat the target as stale
        // instead of letting it live forever, which froze haulers holding eggs across a relog.
        if (setTick > currentTick) {
            return true;
        }
        return currentTick - setTick > STUCK_TIMEOUT_TICKS;
    }

    /** True when another ovomorph already occupies (or a race just claimed) the spot - the caller re-searches. */
    private static boolean isClaimedByOther(Xenomorph xenomorph, BlockPos spot) {
        if (!(xenomorph.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        return EggSpotClaims.isClaimedByOther(serverLevel, spot, xenomorph.getUUID());
    }

    private static void claimSpot(Xenomorph xenomorph, BlockPos spot) {
        if (xenomorph.level() instanceof ServerLevel serverLevel) {
            EggSpotClaims.claim(serverLevel, spot, xenomorph.getUUID());
        }
    }

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
            ovomorph.setHostDropTarget(null); // rooted = no longer in transit, wherever it landed
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
    private static Set<BlockPos> getFailedVents(Blackboard blackboard) {
        var failed = blackboard.getOrDefault(KEY_FAILED_VENTS, (Set<BlockPos>) null);
        return failed == null ? new HashSet<>() : new HashSet<>(failed);
    }

    private static void setFailedVents(Blackboard blackboard, Set<BlockPos> failedVents) {
        blackboard.set(KEY_FAILED_VENTS, failedVents);
    }

    private static void planVentLeg(Xenomorph xenomorph, BlockPos bed, Blackboard blackboard) {
        if (!(xenomorph.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        // Proximity normally rules the duct out - but NOT once a walk has already failed. See KEY_WALK_FAILED.
        var walkFailed = blackboard.getOrDefault(KEY_WALK_FAILED, false);
        if (!walkFailed && xenomorph.blockPosition().distSqr(bed) < VENT_WORTHWHILE_DIST_SQUARED) {
            return;
        }
        var location = resolveLocation(serverLevel, xenomorph);
        if (location == null) {
            return;
        }
        var vents = location.ventManager();
        var failedVents = getFailedVents(blackboard);
        // Egg haulers travel the hive's OWN ducts - STRUCTURE vents. Never a party door, and never a vent this hauler
        // has already failed to reach on this run.
        java.util.function.Predicate<BlockPos> usableEntry =
            v -> location.ventManager().isKind(v, VentKind.STRUCTURE) && !failedVents.contains(v);
        // In-hive shortcut: STRUCTURE ducts only (was "any vent not near the surface", which caught frontier vents).
        java.util.function.Predicate<BlockPos> interiorOnly =
            v -> location.ventManager().isKind(v, VentKind.STRUCTURE);
        var entry = HiveVents.nearestVent(vents, xenomorph.blockPosition(), VENT_SEARCH_RADIUS_CHUNKS, usableEntry);
        var exit = HiveVents.nearestVent(vents, bed, VENT_SEARCH_RADIUS_CHUNKS, interiorOnly);
        if (entry == null || exit == null || entry.equals(exit)) {
            return;
        }

        // The duct is only worth taking if the WHOLE ducted route - walk to the entry vent, then walk from the
        // exit vent to the bed - is meaningfully shorter than just walking straight to the bed. The old check only
        // compared the EXIT leg, ignoring how far the entry vent is. That let a hauler commit to an entry vent
        // sitting BEHIND it (away from the bed): it would trudge backward to the vent, and because that backward
        // walk kept it 'far enough' the plan kept re-choosing the duct - the walk-vs-vent tug-of-war the tester
        // saw at certain positions. Requiring the entry leg to pay for itself removes the backward-vent trap.
        // Skip this route check once the direct walk has already FAILED: at that point any duct beats standing in
        // a dead end, so we take the shortcut even if it isn't shorter. Only the healthy (not-yet-failed) case
        // has to justify the detour.
        if (!walkFailed) {
            var self = xenomorph.blockPosition();
            var directToBed = Math.sqrt(self.distSqr(bed));
            var ductedRoute = Math.sqrt(self.distSqr(entry)) + Math.sqrt(exit.distSqr(bed));
            if (ductedRoute >= directToBed - VENT_MIN_SHORTCUT_BLOCKS) {
                return; // the duct wouldn't shorten the trip enough to be worth the detour
            }
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
        var location = resolveLocation(serverLevel, xenomorph);
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

    /**
     * The hive this hauler belongs to. getByChunk only resolves CLAIMED chunks, so a hauler standing a chunk outside
     * the claim (or in a not-yet-claimed pocket) resolved to null - every destination search then came back empty and
     * the drone froze holding its egg. Fall back to the nearest hive in this dimension.
     */
    /** Blackboard key: last tick we logged a "nowhere to put this egg" diagnostic (rate limit). */
    private static final StateKey<Integer> KEY_LAST_NO_DEST_LOG = StateKey.sensed("egg_no_destination_log_tick");

    private static final int NO_DEST_LOG_INTERVAL_TICKS = 200; // at most once per 10s per hauler

    /**
     * Explain why a hauler holding an egg has nowhere to put it: which of the three destinations (host drop, nursery
     * bed, queen clutch zone) refused, and the counts behind each refusal.
     */
    private static void logNoEggDestination(Xenomorph xenomorph, Blackboard blackboard) {
        int now = xenomorph.tickCount;
        int last = blackboard.getOrDefault(KEY_LAST_NO_DEST_LOG, -NO_DEST_LOG_INTERVAL_TICKS - 1);
        // NB: do NOT default to Integer.MIN_VALUE - (now - MIN_VALUE) OVERFLOWS to a negative number, which is
        // always < the interval, so this rate-limiter silently suppressed the diagnostic FOREVER.
        if (last <= now && now - last < NO_DEST_LOG_INTERVAL_TICKS) {
            return;
        }
        blackboard.set(KEY_LAST_NO_DEST_LOG, now);

        if (!(xenomorph.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        var location = resolveLocation(serverLevel, xenomorph);
        if (location == null) {
            com.alien.Alien.LOGGER.info(
                "Egg haul STUCK: {} at {} is holding an egg but belongs to NO hive location.",
                xenomorph.getType().getDescriptionId(),
                xenomorph.blockPosition()
            );
            return;
        }

        int eggChambers = 0;
        int freeBeds = 0;
        int occupiedBeds = 0;
        int unloadedChambers = 0;
        for (var entry : location.structurePieceByChunk().entrySet()) {
            if (!entry.getValue().contains("chamber_egg")) {
                continue;
            }
            eggChambers++;
            var chamber = entry.getKey();
            if (!serverLevel.isLoaded(chamber.getWorldPosition())) {
                unloadedChambers++;
                continue;
            }
            for (var bed : HiveChamberSlots.eggBedSlots(serverLevel, location, chamber)) {
                if (isBedOccupied(serverLevel, bed)) {
                    occupiedBeds++;
                } else {
                    freeBeds++;
                }
            }
        }

        var failedSpots = getFailedSpots(blackboard);
        Queen queen = null;
        if (location.founderId() != null && serverLevel.getEntity(location.founderId()) instanceof Queen q) {
            queen = q;
        }
        int queenZoneFree = queen == null ? -1 : QueenEggZone.candidates(serverLevel, queen).size();

        com.alien.Alien.LOGGER.info(
            "Egg haul STUCK at {}: hive {} has {} egg chamber(s) ({} unloaded), {} free bed(s), {} occupied; "
                + "queen clutch zone has {} free cell(s) (queen {}); {} spot(s) on this hauler's failed list. "
                + "No destination accepted the egg.",
            xenomorph.blockPosition(),
            location.id(),
            eggChambers,
            unloadedChambers,
            freeBeds,
            occupiedBeds,
            queenZoneFree,
            queen == null ? "MISSING" : "alive",
            failedSpots.size()
        );
    }

    private static com.alien.common.gameplay.hive.location.HiveLocation resolveLocation(
        ServerLevel serverLevel,
        Xenomorph xenomorph
    ) {
        var byChunk = HiveLocationRegistry.INSTANCE.getByChunk(
            serverLevel.dimension(),
            xenomorph.chunkPosition()
        );
        if (byChunk != null) {
            return byChunk;
        }
        return HiveLocationRegistry.INSTANCE.findNearestInDim(
            serverLevel.dimension(),
            xenomorph.blockPosition()
        );
    }

    private static Optional<BlockPos> findChamberBedSpot(Xenomorph xenomorph, Set<BlockPos> failedSpots) {
        if (!(xenomorph.level() instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }
        var location = resolveLocation(serverLevel, xenomorph);
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
