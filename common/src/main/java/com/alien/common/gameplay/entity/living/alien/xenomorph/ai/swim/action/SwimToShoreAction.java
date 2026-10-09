package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.swim.action;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.just.ai.goap.action.Action;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * ⭐⭐⭐ A XENOMORPH IN WATER WITH NOTHING TO CHASE GETS OUT OF THE WATER.
 * <p>
 * [stated] "if i place them in water no target they just wait? ... then yes we need a swim behavior made for just being
 * in water in general. They would try to get out of the water and stay on land or some kind of structure. basically any
 * surface not in water."
 * </p>
 * <p>
 * ⚠⚠ THIS IS THE HALF THAT NEVER EXISTED. {@link SwimToLandAction} is gated on {@code HAS_ATTACK_TARGET} and aborts
 * outright when there is none — so leaving water was only ever a COMBAT manoeuvre. Every time a xenomorph has been seen
 * swimming it was chasing something; drop one in a lake with nothing to hunt and it had no behaviour at all and simply
 * floated, playing the swim animation because that animation keys off {@code isInWater()} rather than off anything the
 * navigator is doing.
 * </p>
 * <p>
 * ANY DRY SURFACE COUNTS, not just natural ground — the search accepts whatever the motion-blocking heightmap reports
 * as the top of the column, so a ship's deck, a bridge or a platform is as good as a beach.
 * </p>
 */
public final class SwimToShoreAction {

    private static final double NAVIGATION_SPEED = 1.0;

    /** Rings searched outward from the actor, in blocks. Cheap because only one column per candidate is touched. */
    static final int SHORE_SEARCH_RADIUS = 24;

    /** Candidate columns are sampled every this many blocks, so a 24-block search costs ~80 lookups, not ~1900. */
    static final int SHORE_SEARCH_STEP = 3;

    /**
     * How far the simulated crossing may reach when nobody is watching. See {@link #tryUnobservedCrossing}.
     */
    private static final int SIMULATED_CROSSING_RADIUS = 160;

    /** Nobody within this range means the crossing can be simulated rather than swum. */
    private static final double OBSERVER_RANGE = 96.0;

    /**
     * How often the navigator is re-issued, and how often a failed shore search is retried.
     * <p>
     * !!! THE SAME NUMBER AND THE SAME REASON AS WanderAction's WANDER_REPATH_INTERVAL_TICKS. A GOAP action's perform
     * callback runs EVERY TICK, so anything that starts a journey must be written idempotent, or it restarts that
     * journey every tick and the actor never gets more than one tick of movement out of it.
     * </p>
     */
    private static final int REPATH_INTERVAL_TICKS = 20;

    /** The shore this crossing is aimed at, cached so the ring search is not re-run every tick. */
    private static final com.just.ai.goap.StateKey<BlockPos> KEY_SHORE_TARGET =
        com.just.ai.goap.StateKey.sensed("swim_shore_target");

    public static Action.Signal perform(Action.Context<? extends Xenomorph> context) {
        var xenomorph = context.getActor();
        var blackboard = context.getBlackboard(com.just.ai.goap.state.Blackboard.Scope.ACTION);

        // Released in the SHALLOWS, not only on dry land - the same test the sensor uses, so the action and the
        // goal can never disagree about whether the crossing is finished.
        if (!com.alien.common.gameplay.entity.living.alien.xenomorph.ai.swim.SwimSensors.isSubmerged(xenomorph)) {
            blackboard.set(KEY_SHORE_TARGET, null);
            return Action.Signal.CONTINUE; // out - the goal is satisfied and the plan ends here
        }

        // One block of dry footing right beside it. Same stuck-breaker SwimToLandAction uses, and the reason it
        // exists: a mob wedged against a shelf can fail to path onto a block it is already touching.
        var adjacent = findAdjacentLandPosition(xenomorph);
        if (adjacent != null) {
            blackboard.set(KEY_SHORE_TARGET, null);
            xenomorph.setPos(adjacent.getX() + 0.5, adjacent.getY(), adjacent.getZ() + 0.5);
            return Action.Signal.CONTINUE;
        }

        var navigation = xenomorph.getNavigation();
        var shore = blackboard.getOrDefault(KEY_SHORE_TARGET, (BlockPos) null);

        // !! THE SEARCH IS THROTTLED TOO. Each call walks rings of the heightmap; running that every tick for every
        // stranded xenomorph is the same waste as the re-pathing, just without a visible symptom.
        if (shore == null && xenomorph.tickCount % REPATH_INTERVAL_TICKS == 0) {
            shore = findNearestDrySurface(xenomorph, SHORE_SEARCH_RADIUS, SHORE_SEARCH_STEP);

            // WARNING: THE WIDE SEARCH IS NO LONGER RESERVED FOR THE UNOBSERVED CASE, AND THAT WAS THE SECOND BUG.
            // A xenomorph in open ocean with the shore 60 blocks off found nothing in the 24-block ring, fell
            // through to the unobserved crossing, saw the watching player and returned CONTINUE - doing LITERALLY
            // NOTHING, every tick, forever. Reported as "you can see land in the distance but its not moving its
            // just floating there". It now aims at the distant shore and SWIMS; the observer check below only
            // decides whether the crossing is resolved instantly or actually swum.
            if (shore == null) {
                shore = findNearestDrySurface(xenomorph, SIMULATED_CROSSING_RADIUS, SHORE_SEARCH_STEP * 4);
            }

            blackboard.set(KEY_SHORE_TARGET, shore);
        }

        if (shore == null) {
            return Action.Signal.CONTINUE; // nothing dry in reach; float and try again on the next beat
        }

        if (!isObserved(xenomorph)) {
            blackboard.set(KEY_SHORE_TARGET, null);
            navigation.stop();
            xenomorph.setPos(shore.getX() + 0.5, shore.getY(), shore.getZ() + 0.5);
            return Action.Signal.CONTINUE;
        }

        // SWIM THERE VISIBLY. [stated] the swim animation "is meant to be their normal swim idle and movement ...
        // and the swim attacks are built off of it", so a xenomorph crossing water on purpose is a thing players are
        // supposed to see. Only the adjacent-block stuck-breaker and the unobserved crossing ever move it instantly.
        xenomorph.getLookControl().setLookAt(shore.getX() + 0.5, shore.getY(), shore.getZ() + 0.5);

        // IDEMPOTENT. While the navigator is still working the crossing, leave it alone; re-issue only when it has
        // finished or lost the path, plus a slow heartbeat. Calling moveTo every tick RESTARTS the path every tick,
        // which is exactly the "stuck bouncing left and right repeatedly not moving" that was reported.
        // ⚠⚠ Oct 5 - "DONE" WAS NOT "NEVER STARTED". A path that ended SHORT of the shore - the bank too high to climb,
        // the shore across a wall - leaves the navigation done with its path still set, so isDone() was true every
        // tick and this re-ran vanilla's path search every tick for as long as a player watched. Found in the
        // cross-mod audit after /blib perf caught a yautja doing the same thing at 9.5 ms a tick. Now: issue at once
        // only when there is no path at all, otherwise on the heartbeat.
        if (navigation.getPath() == null || xenomorph.tickCount % REPATH_INTERVAL_TICKS == 0) {
            var accepted = navigation.moveTo(
                shore.getX() + 0.5,
                shore.getY(),
                shore.getZ() + 0.5,
                NAVIGATION_SPEED
            );

            // The navigator refused this shore. Drop it so the next beat picks another rather than hammering an
            // unreachable target forever.
            if (!accepted) {
                blackboard.set(KEY_SHORE_TARGET, null);
            }
        }

        return Action.Signal.CONTINUE;
    }

    /** Whether a non-spectator player is close enough that the crossing has to actually happen on screen. */
    private static boolean isObserved(Xenomorph xenomorph) {
        if (!(xenomorph.level() instanceof ServerLevel serverLevel)) {
            return true; // cannot tell - assume watched and swim, rather than teleporting in front of someone
        }

        var observer = serverLevel.getNearestPlayer(xenomorph, OBSERVER_RANGE);

        return observer != null && !observer.isSpectator();
    }

    static @Nullable BlockPos findNearestDrySurface(Xenomorph xenomorph, int radius, int step) {
        var level = xenomorph.level();
        var origin = xenomorph.blockPosition();
        var height = (int) Math.ceil(xenomorph.getBbHeight());

        for (var ring = step; ring <= radius; ring += step) {
            BlockPos best = null;
            var bestDistSqr = Double.MAX_VALUE;

            for (var dx = -ring; dx <= ring; dx += step) {
                for (var dz = -ring; dz <= ring; dz += step) {
                    // Ring, not disc — the interior was covered by a previous (nearer) iteration.
                    if (Math.abs(dx) != ring && Math.abs(dz) != ring) {
                        continue;
                    }

                    var x = origin.getX() + dx;
                    var z = origin.getZ() + dz;
                    if (!level.hasChunkAt(new BlockPos(x, origin.getY(), z))) {
                        continue; // never force-load a chunk from an AI scan
                    }

                    var surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    var candidate = new BlockPos(x, surfaceY, z);
                    if (!isWalkable(level, candidate, height)) {
                        continue;
                    }

                    var distSqr = candidate.distSqr(origin);
                    if (distSqr < bestDistSqr) {
                        best = candidate;
                        bestDistSqr = distSqr;
                    }
                }
            }

            if (best != null) {
                return best; // nearest ring wins; no need to search wider
            }
        }

        return null;
    }

    /** Dry footing directly beside the actor, at its level or one step up. Shared shape with SwimToLandAction. */
    private static @Nullable BlockPos findAdjacentLandPosition(Xenomorph xenomorph) {
        var pos = xenomorph.blockPosition();
        var level = xenomorph.level();
        var height = (int) Math.ceil(xenomorph.getBbHeight());

        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var candidate = pos.relative(direction);

            if (isWalkable(level, candidate, height)) {
                return candidate;
            }

            var above = candidate.above();
            if (isWalkable(level, above, height)) {
                return above;
            }

            // Oct 5 - a two-block bank too. A submerged xenomorph against a pit wall two blocks above its feet had no
            // way out at all: this only stepped one up, and the swim cannot climb.
            var twoAbove = candidate.above(2);
            if (isWalkable(level, twoAbove, height) && isClearAbove(level, pos, height + 2)) {
                return twoAbove;
            }
        }

        return null;
    }

    /**
     * Whether the column the body would pass through on the way up - its own column, {@code clearance} blocks high -
     * has no solid block in it. Water is allowed; it is the water being left.
     */
    private static boolean isClearAbove(Level level, BlockPos feetPos, int clearance) {
        for (var i = 1; i < clearance; i++) {
            if (level.getBlockState(feetPos.above(i)).isSolid()) {
                return false;
            }
        }

        return true;
    }

    /** Solid, non-liquid ground with a clear, dry column above it. */
    private static boolean isWalkable(Level level, BlockPos feetPos, int entityHeight) {
        var ground = level.getBlockState(feetPos.below());

        if (!ground.isSolid() || ground.liquid()) {
            return false;
        }

        for (var i = 0; i < entityHeight; i++) {
            var state = level.getBlockState(feetPos.above(i));

            if (state.isSolid() || state.liquid()) {
                return false;
            }
        }

        return true;
    }

    private SwimToShoreAction() {
        throw new UnsupportedOperationException();
    }
}
