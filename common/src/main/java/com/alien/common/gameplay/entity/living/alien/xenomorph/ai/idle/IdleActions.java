package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.idle;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.goap.v1.GOAPSensors;
import com.blib.api.common.goap.v1.action.ActionMasks;
import com.blib.api.common.goap.v1.action.BLibAction;
import com.blib.api.common.goap.v1.action.impl.NeoMoveToPosAction;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.action.Action;
import com.just.ai.goap.condition.expression.Expressions;
import com.just.ai.goap.state.Blackboard;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class IdleActions {

    private static final StateKey<Vec3> KEY_WANDER_TARGET = StateKey.sensed("wander_target");

    private static final int WANDER_HORIZONTAL_RANGE = 10;

    private static final int WANDER_VERTICAL_RANGE = 7;

    private static final double WANDER_SPEED = 0.5;

    private static final int WANDER_TARGET_ATTEMPTS = 12;

    /**
     * ⭐ Oct 6 - A FAILED PICK WAITS BEFORE THE NEXT ONE. When none of the 12 attempts found a spot (each attempt is
     * vanilla's LandRandomPos, itself up to 10 probes), the action returned CONTINUE with no target - and the very next
     * tick ran all 12 attempts again, every tick, for as long as the xeno stayed somewhere cramped. /blib perf put the
     * wander at 25-33 us per warrior/runner in a cave fight. Now a failed pick waits two seconds before trying again.
     */
    private static final int WANDER_PICK_RETRY_TICKS = 40;

    private static final StateKey<Integer> KEY_WANDER_PICK_RETRY_TICK = StateKey.sensed("wander_pick_retry_tick");

    /**
     * How far a CACHED wander target may drift from the actor before it is thrown away and re-rolled.
     * <p>
     * Four times the roll range, so normal wandering never re-rolls for distance — this only catches an actor that was
     * MOVED rather than one that walked.
     * </p>
     */
    private static final double MAX_WANDER_TARGET_DISTANCE_SQR =
        (WANDER_HORIZONTAL_RANGE * 4.0) * (WANDER_HORIZONTAL_RANGE * 4.0);

    public static final Action<Xenomorph> WANDER = BLibAction.<Xenomorph>builder("WanderAction")
        .addMasks(ActionMasks.MOVE)
        // 🚨🚨 THIS IS WHAT LETS A WORKER TAKE A JOB. BLib's ActionMaskPlanResolver refuses any incoming plan sharing
        // a mask with the running one, and EVERY movement action in the mod declares MOVE - egg hauling, vent
        // digging, resin spreading, host capture, carve work, combat. So a xenomorph that started a wander could not
        // accept ANY of them until the wander ended by itself, which is 7-12 seconds of boredom per stroll.
        //
        // ⚠⚠ THAT IS THE "WORKERS STAND AROUND DOING NOTHING" BUG. It looked like broken job assignment and was
        // actually a scheduling rule: the jobs were being offered and refused. Marking ONLY the idle filler
        // interruptible means real work displaces it immediately, while work never displaces work.
        // TEMPORARILY OFF - isolating whether interruptible wander plans are causing movement churn.
        // BLib keeps the capability; nothing opts in while this line is commented, so behaviour returns to the
        // original mask rule without needing a BLib rebuild.
        // .markInterruptible()
        .addPrecondition(GOAPSensors.HAS_ATTACK_TARGET.key(), Expressions.Boolean.isFalse())
        .addPrecondition(IdleSensors.IS_BORED.key(), Expressions.Boolean.isTrue())
        .addEffect(IdleSensors.IS_BORED.key().asDerived(), false)
        .withPerformCallback(context -> {
            return performWander(context);
        })
        .withFinishCallback(context -> {
            NeoMoveToPosAction.onFinish(context);
            context.getBlackboard(Blackboard.Scope.ACTION).clear();
        })
        .build();

    /**
     * How often a wander path is re-issued while the actor is already walking to it.
     * <p>
     * ⚠ Only a heartbeat - the path is re-issued IMMEDIATELY whenever the navigator is done, so arrival, blockage and a
     * lost path are all still handled on the tick they happen. This interval only governs the pointless case: re-asking
     * for a path the actor is already successfully following.
     * </p>
     */
    private static final int WANDER_REPATH_INTERVAL_TICKS = 20;

    /**
     * How far an actor must move between beats to count as making progress.
     * <p>
     * Squared, and deliberately small - this is "did he move at all", not "did he move well". A xenomorph shuffling
     * against a wall covers essentially nothing in a second.
     * </p>
     */
    private static final double STUCK_DISTANCE_SQR = 0.25D;

    /**
     * The floor between two path requests from the SAME actor.
     * <p>
     * ⚠⚠ THIS ONLY GUARDS THE FAILURE CASE. At 10 ticks it also blocked the NORMAL one - a wander leg ended and the
     * actor stood for half a second before choosing the next - which is the bursty "walks a bit, stops, walks a bit"
     * that got reported. Arriving must flow straight into the next stroll; the floor exists only so a navigator that
     * reports done every tick, the wall-grinding case, cannot spin at one path per tick. ⭐ Three ticks is invisible to
     * the eye and still caps the pathological case at ~7 calls a second instead of 20.
     * </p>
     */
    private static final int MIN_TICKS_BETWEEN_WANDER_PATHS = 3;

    /** Where the actor was at the previous beat, for the no-progress test. */
    private static final com.just.ai.goap.StateKey<Vec3> KEY_WANDER_LAST_POS =
        com.just.ai.goap.StateKey.sensed("wander_last_pos");

    /** When that position sample was taken, so progress is only judged over a full heartbeat. */
    private static final com.just.ai.goap.StateKey<Integer> KEY_WANDER_LAST_POS_TICK =
        com.just.ai.goap.StateKey.sensed("wander_last_pos_tick");

    /** When this actor last asked for a wander path. Per-actor, so the hive does not move in lockstep. */
    private static final com.just.ai.goap.StateKey<Integer> KEY_WANDER_LAST_PATH_TICK =
        com.just.ai.goap.StateKey.sensed("wander_last_path_tick");

    private static Action.Signal performWander(Action.Context<? extends Xenomorph> context) {
        var actor = context.getActor();

        // ⭐⭐ A PASSENGER DOES NOT WANDER. Its vehicle owns its position, so pathing is meaningless — and on a
        // moving vehicle it is actively dangerous, see the distance guard below.
        if (actor.isPassenger()) {
            actor.getXenomorphData().resetTicksUntilBored();
            context.getBlackboard(Blackboard.Scope.ACTION).set(KEY_WANDER_TARGET, null);
            return Action.Signal.ABORT;
        }

        var location = resolveHiveBoundWanderLocation(actor);

        var blackboard = context.getBlackboard(Blackboard.Scope.ACTION);
        var target = blackboard.getOrDefault(KEY_WANDER_TARGET, (Vec3) null);
        if (target == null || !isValidWanderTarget(actor, location, target)) {
            if (actor.tickCount < blackboard.getOrDefault(KEY_WANDER_PICK_RETRY_TICK, 0)) {
                return Action.Signal.CONTINUE;
            }

            target = pickWanderTarget(actor, location);
            if (target == null) {
                actor.getXenomorphData().resetTicksUntilBored();
                blackboard.set(KEY_WANDER_PICK_RETRY_TICK, actor.tickCount + WANDER_PICK_RETRY_TICKS);
                blackboard.set(KEY_WANDER_TARGET, null);
                return Action.Signal.CONTINUE;
            }
            blackboard.set(KEY_WANDER_TARGET, target);
        }

        // ⭐⭐⭐ DO NOT RE-ISSUE THE PATH EVERY TICK. THIS IS THE SINGLE BIGGEST COST IN THE MOD.
        //
        // ⚠⚠ MEASURED, NOT GUESSED. Two diagnostic reports from live worlds:
        // 1,286,017 calls over 92,091 ticks = 14.0 per tick, 5.8% of all elapsed time
        // 1,213,747 calls over 42,650 ticks = 28.5 per tick, 10.3% of elapsed, worst single call 852ms
        // The next busiest pathing site was DropOffEggAction at 55,717 calls - this was TWENTY-THREE TIMES that.
        //
        // ⚠ AND THE PATHS SUCCEED: NO_PATH was 0.0-0.1%. Nothing was failing and retrying; every idle xenomorph was
        // simply asking for a fresh path to a target it had ALREADY been given, once per tick, forever. The wander
        // target is cached on the blackboard - only the pathing call was not.
        //
        // ⭐ While the navigator is still walking, there is nothing to decide: let it walk. Re-issue only when it
        // has finished or lost the path, plus a slow heartbeat so a target that drifts is still corrected.
        // 🚨 THE THROTTLE IS UNCONDITIONAL NOW, AND THE isDone() HALF WAS THE HOLE.
        //
        // ⚠⚠ THE OLD GUARD ONLY THROTTLED WHILE THE NAVIGATOR WAS STILL WALKING. The instant isDone() went true it
        // fell through EVERY TICK - and isDone() is true every tick for an actor that cannot make progress, which is
        // exactly a xenomorph wedged against a wall. A live diagnostic caught it at 134,996 calls over 4,200 ticks:
        // 32 PER TICK, 8.05% of all server time, alongside the report "some aliens are pathing to walls and just
        // standing there". The earlier fix cut the walking case and left the stuck case untouched.
        //
        // ⭐ Idle wandering needs no urgency. Once per second per actor is ample, and it caps the cost no matter what
        // state the navigator is in.
        // 🚨 A PER-ACTOR COOLDOWN, NOT A SHARED BEAT - AND IT RE-PATHS THE MOMENT IT ARRIVES.
        //
        // ⚠⚠ "tickCount % 20" WAS WRONG TWICE OVER. Every xenomorph spawned on the same tick shares a tickCount
        // phase, so a whole vent-load of them re-pathed on the SAME tick - reported as "they all do it at the same
        // time". And because arrival was only NOTICED on that beat, an actor that finished a short path stood frozen
        // until the next one: "they take one step and stand idle again".
        //
        // ⭐ Cooldown since THIS actor last asked for a path, so the rate is still capped and the hive is no longer
        // in lockstep - plus an immediate re-path when the navigator is genuinely done, which is what removes the
        // stutter without reopening the every-tick spam (a finished path only happens once per journey).
        var navigation = actor.getNavigation();
        var now = actor.tickCount;
        var lastPathTick = blackboard.getOrDefault(KEY_WANDER_LAST_PATH_TICK, 0);
        var sinceLastPath = now - lastPathTick;

        if (sinceLastPath < MIN_TICKS_BETWEEN_WANDER_PATHS) {
            return Action.Signal.CONTINUE;
        }

        if (!navigation.isDone() && sinceLastPath < WANDER_REPATH_INTERVAL_TICKS) {
            return Action.Signal.CONTINUE; // still walking and asked recently - let it walk
        }

        blackboard.set(KEY_WANDER_LAST_PATH_TICK, now);

        // ⭐⭐ AND DROP A TARGET WE ARE NOT ACTUALLY REACHING. Throttling alone would still leave him grinding at the
        // wall once a second forever, because the path keeps reporting MOVING while he goes nowhere. If he has not
        // covered any ground since the last beat, the target is unreachable in practice - forget it and pick another.
        // ⚠ ONLY JUDGE PROGRESS OVER A FULL HEARTBEAT. With the immediate re-path above, two samples can now be a
        // fraction of a second apart, and nothing covers ground in three ticks - that would call every actor stuck.
        var here = actor.position();
        var lastSeen = blackboard.getOrDefault(KEY_WANDER_LAST_POS, (Vec3) null);
        var lastSeenTick = blackboard.getOrDefault(KEY_WANDER_LAST_POS_TICK, 0);
        var measuredOverFullBeat = now - lastSeenTick >= WANDER_REPATH_INTERVAL_TICKS;

        blackboard.set(KEY_WANDER_LAST_POS, here);
        blackboard.set(KEY_WANDER_LAST_POS_TICK, now);

        if (measuredOverFullBeat && lastSeen != null && here.distanceToSqr(lastSeen) < STUCK_DISTANCE_SQR) {
            actor.getXenomorphData().resetTicksUntilBored();
            blackboard.set(KEY_WANDER_TARGET, null);
            blackboard.set(KEY_WANDER_LAST_POS, null);
            blackboard.set(KEY_WANDER_LAST_POS_TICK, 0);
            navigation.stop();
            return Action.Signal.ABORT;
        }

        // ⚠ target is reassigned above, so it cannot be captured by a lambda directly.
        var wanderTarget = target;
        var result = com.alien.common.gameplay.hive.diag.DiagProfiler.timed(
            "path/IdleActions",
            () -> NeoMoveToPosAction.perform(context, wanderTarget, WANDER_SPEED)
        );
        return switch (result) {
            case FINISHED -> {
                actor.getXenomorphData().resetTicksUntilBored();
                blackboard.set(KEY_WANDER_TARGET, null);
                blackboard.set(KEY_WANDER_LAST_POS, null);
                blackboard.set(KEY_WANDER_LAST_POS_TICK, 0);
                yield Action.Signal.CONTINUE;
            }
            case MOVING -> Action.Signal.CONTINUE;
            case NO_PATH -> {
                actor.getXenomorphData().resetTicksUntilBored();
                blackboard.set(KEY_WANDER_TARGET, null);
                yield Action.Signal.ABORT;
            }
            default -> {
                actor.getXenomorphData().resetTicksUntilBored();
                blackboard.set(KEY_WANDER_TARGET, null);
                yield Action.Signal.ABORT;
            }
        };
    }

    private static @Nullable Vec3 pickWanderTarget(Xenomorph actor, @Nullable HiveLocation location) {
        // Always try several times: LandRandomPos frequently returns null for large mobs (the queen especially),
        // so a single roll usually fails and she never wanders. Hive-bound queens also need the retries to land
        // a spot inside their claimed chunks.
        var attempts = WANDER_TARGET_ATTEMPTS;

        for (var attempt = 0; attempt < attempts; attempt++) {
            var candidate = LandRandomPos.getPos(actor, WANDER_HORIZONTAL_RANGE, WANDER_VERTICAL_RANGE);
            if (candidate != null && isValidWanderTarget(location, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * ⭐⭐⭐ THE CACHED WANDER TARGET MUST ALSO BE CHECKED FOR DISTANCE, AND THIS IS THE ACTUAL BUG.
     * <p>
     * ⚠⚠⚠ SYMPTOM: 40-to-125 SECOND SINGLE SERVER TICKS. Reported as xenomorphs on a Create Aeronautics physics entity
     * freezing the game — [stated] "it's not the seat, it's just the fact the xenos were on a phys entity." Four
     * watchdog thread dumps all caught the server thread in the same place: {@code IdleActions.performWander} →
     * {@code NeoMoveToPosAction} → {@code BLibPathFinder.findPathAsync}, blocked inside
     * {@code ServerChunkCache.getChunk}.
     * </p>
     * <p>
     * WHY: {@code findPathAsync} pre-populates its terrain cache by looping the whole chunk bounding box between start
     * and target and calling the BLOCKING, GENERATE-IF-ABSENT {@code level.getChunk(cx, cz)} on the server thread. That
     * is fine for a wander target ten blocks away. But the target is CACHED in the action blackboard and, for any
     * non-royal xenomorph, {@code location} is null — so the old test returned {@code true} unconditionally and the
     * cached target was NEVER invalidated no matter how far the actor moved from it. Ride a vehicle a few thousand
     * blocks and the bounding box becomes a few thousand chunks wide, every one of them generated synchronously
     * mid-tick.
     * </p>
     * <p>
     * ⭐ A vehicle is only the fastest way to trigger it — a teleport, a portal or being carried does the same. The cap
     * is generous ({@link #WANDER_HORIZONTAL_RANGE} squared) so ordinary wandering never re-rolls, while the
     * pathfinder's preload box stays bounded no matter what moves the actor.
     * </p>
     */
    private static boolean isValidWanderTarget(@Nullable HiveLocation location, Vec3 pos) {
        return location == null || isInsideLocation(location, pos);
    }

    /** As {@link #isValidWanderTarget} plus the distance cap that keeps the pathfinder's preload box bounded. */
    private static boolean isValidWanderTarget(Xenomorph actor, @Nullable HiveLocation location, Vec3 pos) {
        return isValidWanderTarget(location, pos) && actor.position().distanceToSqr(pos) <= MAX_WANDER_TARGET_DISTANCE_SQR;
    }

    private static boolean isHiveBoundIdleWanderer(Xenomorph actor) {
        return actor.getType().is(AlienEntityTypeTags.QUEENS)
            || actor.getType().is(AlienEntityTypeTags.EMPRESSES)
            || actor.getType().is(AlienEntityTypeTags.HARBINGERS);
    }

    private static @Nullable HiveLocation resolveHiveBoundWanderLocation(Xenomorph actor) {
        if (!isHiveBoundIdleWanderer(actor)) {
            return null;
        }

        var location = HiveLocationRegistry.INSTANCE.getByChunk(actor.level().dimension(), actor.chunkPosition());
        return location != null && location.isAlive() ? location : null;
    }

    private static boolean isInsideLocation(HiveLocation location, Vec3 pos) {
        return location.claimedChunks().contains(new ChunkPos((int) Math.floor(pos.x) >> 4, (int) Math.floor(pos.z) >> 4));
    }

    private IdleActions() {
        throw new UnsupportedOperationException();
    }
}
