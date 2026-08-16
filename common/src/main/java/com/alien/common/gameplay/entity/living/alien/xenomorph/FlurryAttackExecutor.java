package com.alien.common.gameplay.entity.living.alien.xenomorph;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * An attack whose clip plays SEVERAL TIMES BACK TO BACK, landing a hit each time.
 * <p>
 * [stated] "the animation plays 4 times is succession think of it like a flurry of blows back to back."
 * </p>
 * <p>
 * ⚠ THE DEFAULT EXECUTOR CANNOT DO THIS. {@code DefaultAttackExecutor} latches {@code damageDealt} the first time it
 * fires and never unlatches, so a longer duration would have produced one hit and three-quarters of a second of
 * nothing. This keeps a per-repeat counter instead.
 * </p>
 * <p>
 * ⚠ THE ANIMATOR RE-DISPATCHES ON EACH REPEAT, not on each tick — {@code repeatStartedThisTick} is the signal, and it
 * is true for exactly one tick per repeat. Dispatching every tick would restart the clip on its opening frames forever
 * and the flurry would look like a single frozen pose.
 * </p>
 */
public class FlurryAttackExecutor implements AttackExecutor {

    private final int repeats;

    private final int ticksPerRepeat;

    /** The scaled figure, resolved at onStart from the entity - see Xenomorph.attackSpeedMultiplier. */
    private int activeTicksPerRepeat;

    private int elapsedTicks;

    private int repeatsDone;

    private boolean damageDealtThisRepeat;

    private boolean repeatStartedThisTick;

    private @Nullable LivingEntity target;

    public FlurryAttackExecutor(int repeats, int ticksPerRepeat) {
        this.repeats = Math.max(1, repeats);
        this.ticksPerRepeat = Math.max(1, ticksPerRepeat);
    }

    /** True for exactly one tick at the start of each repeat - the animator's cue to replay the clip. */
    public boolean repeatStartedThisTick() {
        return repeatStartedThisTick;
    }

    @Override
    public int totalDurationInTicks(AttackType attack) {
        return repeats * ticksPerRepeat;
    }

    @Override
    public int totalDurationInTicks(Xenomorph entity, AttackType attack) {
        return repeats * entity.scaleAttackDuration(ticksPerRepeat);
    }

    @Override
    public void onStart(Xenomorph entity, AttackType attack, @Nullable LivingEntity target) {
        // ⚠ SCALE THE REPEAT, NOT THE COUNT. A speed buff makes each blow faster; it must not turn four blows into
        // ten, or the flurry's total damage would ride the buff too.
        this.activeTicksPerRepeat = entity.scaleAttackDuration(ticksPerRepeat);
        this.elapsedTicks = 0;
        this.repeatsDone = 0;
        this.damageDealtThisRepeat = false;
        this.repeatStartedThisTick = true;
        this.target = target;
    }

    @Override
    public boolean onTick(Xenomorph entity, AttackType attack) {
        var tickWithinRepeat = elapsedTicks % activeTicksPerRepeat;

        // A fresh repeat opens whenever we wrap back to tick 0, except the very first (onStart already flagged it).
        repeatStartedThisTick = tickWithinRepeat == 0 && elapsedTicks > 0;

        if (repeatStartedThisTick) {
            damageDealtThisRepeat = false;
        }

        if (!damageDealtThisRepeat && target != null && target.isAlive()) {
            var damageTick = (int) (activeTicksPerRepeat * attack.damageThresholdPercent());

            if (tickWithinRepeat >= damageTick) {
                attack.damageApplicator().apply(entity, target);
                damageDealtThisRepeat = true;
                repeatsDone++;
            }
        }

        elapsedTicks++;

        return elapsedTicks < repeats * activeTicksPerRepeat;
    }

    @Override
    public void onComplete(Xenomorph entity, AttackType attack) {
        target = null;
        repeatStartedThisTick = false;
    }

    public int repeatsDone() {
        return repeatsDone;
    }
}
