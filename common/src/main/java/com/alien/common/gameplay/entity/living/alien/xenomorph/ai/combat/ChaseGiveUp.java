package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.combat;

import com.alien.common.gameplay.entity.living.alien.Alien;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Oct 7 - A XENOMORPH STOPS RE-SEARCHING FOR A TARGET IT HAS PROVEN IT CANNOT REACH.
 * <p>
 * A tester's /blib perf: the queen spent 749 us per tick in MoveToTargetAction and 5 of her 11 path searches failed -
 * she kept chasing something she could not get to, and every retry was a full block-breaking path search (a praetorian
 * doing the same hit 9 ms per tick). The pathfinder's own failure back-off spaces retries out, but the plan simply
 * re-picks the same target and searches again.
 * <p>
 * Rule: two failed searches for the SAME target within 30 s and the xenomorph lets that target go and will not pick it
 * up again for 10 s. Exception, always: a target that hurt this xenomorph in the last 5 s is never ignored - a mob
 * being shot is never made to stand there and take it. Server thread only; entries die with the xenomorph.
 */
public final class ChaseGiveUp {

    /** Failed searches for the same target that count as "unreachable". */
    private static final int FAILURES_TO_GIVE_UP = 2;

    /** Failures further apart than this do not add up. */
    private static final long FAILURE_WINDOW_TICKS = 30L * 20L;

    /** How long an unreachable target is left alone. */
    private static final long IGNORE_TICKS = 10L * 20L;

    /** A target that hurt the xenomorph this recently is never ignored. */
    private static final int RECENT_HURT_TICKS = 5 * 20;

    private static final Map<Alien, State> STATES = new WeakHashMap<>();

    private ChaseGiveUp() {}

    /** Records that a chase search toward {@code target} found no path; gives the target up after repeated failures. */
    public static void noteNoPath(@NotNull Alien alien, @NotNull LivingEntity target) {
        if (alien.level().isClientSide()) {
            return;
        }

        var now = alien.level().getGameTime();
        var state = STATES.computeIfAbsent(alien, $ -> new State());

        if (!target.getUUID().equals(state.failedTarget) || now - state.firstFailureTick > FAILURE_WINDOW_TICKS) {
            state.failedTarget = target.getUUID();
            state.failures = 0;
            state.firstFailureTick = now;
        }

        state.failures++;

        if (state.failures < FAILURES_TO_GIVE_UP || hurtByRecently(alien, target)) {
            return;
        }

        state.ignoredTarget = target.getUUID();
        state.ignoreUntilTick = now + IGNORE_TICKS;
        state.failedTarget = null;
        state.failures = 0;

        if (alien.getTarget() == target) {
            alien.setTarget(null);
        }
    }

    /** {@return true if this alien has given {@code target} up as unreachable and should not acquire it right now} */
    public static boolean isGivenUp(@NotNull Alien alien, @NotNull LivingEntity target) {
        var state = STATES.get(alien);

        if (state == null || state.ignoredTarget == null) {
            return false;
        }

        if (alien.level().getGameTime() >= state.ignoreUntilTick) {
            state.ignoredTarget = null;
            return false;
        }

        return state.ignoredTarget.equals(target.getUUID()) && !hurtByRecently(alien, target);
    }

    private static boolean hurtByRecently(Alien alien, LivingEntity target) {
        return alien.getLastHurtByMob() == target && alien.tickCount - alien.getLastHurtByMobTimestamp() < RECENT_HURT_TICKS;
    }

    private static final class State {

        private UUID failedTarget;

        private int failures;

        private long firstFailureTick;

        private UUID ignoredTarget;

        private long ignoreUntilTick;
    }
}
