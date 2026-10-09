package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Oct 8 - STEADY CHASE TARGETS, for every alien that runs something down (combat chase for every caste, and host
 * capture).
 * <h2>WHY</h2> /blib perf, Oct 8: in a cave fight, warriors' MoveToTargetAction cost 245 us per warrior per tick,
 * almost all of it path searches - and a chase that may break blocks never goes async, so each one runs on the server
 * thread. The chase aims at an INTERCEPT point (where the target is going): target position plus its velocity times the
 * time to get there. That point jumps around every tick - a little jitter in the target's velocity, multiplied by the
 * distance, moves it several blocks - and every jump past the navigator's threshold threw the path away and searched
 * again. BLib's chase budget only spaces re-paths out beyond 16 blocks; inside that, which is where fights happen,
 * nothing held them back.
 * <h2>WHAT</h2> Each chaser keeps the point it is steering for, and only takes a new one when the fresh intercept has
 * moved a meaningful amount: 20% of the distance to the target, at least 1.5 blocks and at most 6. Close in (4 blocks
 * or less) it follows to the block, so melee approach is as precise as before. The route that matters - the next few
 * blocks - is the same either way; only the far end of it stops wobbling.
 */
public final class ChaseTarget {

    /** Fraction of the distance to the target the aim point may drift before it is replaced. */
    private static final double DRIFT_FRACTION = 0.2;

    private static final double MIN_DRIFT = 1.5;

    private static final double MAX_DRIFT = 6.0;

    /** Inside this distance the chase follows exactly - melee approach needs it. */
    private static final double CLOSE_DISTANCE = 4.0;

    /** A held aim point older than this is refreshed anyway, so nothing chases a stale point for long. */
    private static final int MAX_HOLD_TICKS = 40;

    private record Aim(
        Vec3 point,
        int targetId,
        int tick
    ) {}

    /** Server thread only; entries die with the chaser. */
    private static final Map<Mob, Aim> AIMS = new WeakHashMap<>();

    private ChaseTarget() {}

    /**
     * @param chaser  the alien chasing
     * @param target  what it chases
     * @param desired this tick's intercept point
     * @return the point to steer for: the held one while the fresh intercept is close to it, otherwise the fresh one
     */
    public static Vec3 steady(Mob chaser, Entity target, Vec3 desired) {
        var distance = chaser.distanceTo(target);
        var held = AIMS.get(chaser);

        if (
            held != null
                && held.targetId() == target.getId()
                && distance > CLOSE_DISTANCE
                && chaser.tickCount - held.tick() < MAX_HOLD_TICKS
        ) {
            var drift = Math.min(MAX_DRIFT, Math.max(MIN_DRIFT, distance * DRIFT_FRACTION));

            if (held.point().distanceToSqr(desired) < drift * drift) {
                return held.point();
            }
        }

        AIMS.put(chaser, new Aim(desired, target.getId(), chaser.tickCount));
        return desired;
    }

    /** @param chaser forget its held aim (chase ended) */
    public static void clear(Mob chaser) {
        AIMS.remove(chaser);
    }
}
