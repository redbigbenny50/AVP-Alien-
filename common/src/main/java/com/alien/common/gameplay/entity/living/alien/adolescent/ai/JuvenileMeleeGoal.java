package com.alien.common.gameplay.entity.living.alien.adolescent.ai;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Closes on the current target and swings.
 * <p>
 * ⚠⚠ IT DOES <em>NOT</em> USE VANILLA'S {@code MeleeAttackGoal}, and that is the important part. Vanilla lands damage
 * itself on its own swing timer via {@code doHurtTarget}. Every other caste in this mod goes the other way: the GOAP
 * {@code MeleeAttackAction} calls {@code runAttackAnimations()}, which picks a weighted
 * {@link com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType}, plays its clip, and lets the attack's own
 * DamageApplicator land the hit on the frame the animation actually connects. Using the vanilla goal here would give
 * juveniles damage with no animation, on a different cadence from every other alien, and would bypass the limb
 * requirements ({@code requiresAnyArm}, {@code requiresHead}) that the AttackType builders declare.
 * </p>
 * <p>
 * So this is the goal-driven twin of that action: same call, same pipeline, minus GOAP - which juveniles do not run.
 * </p>
 */
public class JuvenileMeleeGoal extends Goal {

    /** How close the swing needs its target. Squared at the comparison, not here. */
    private static final double REACH_PADDING = 2.0D;

    private final Xenomorph xenomorph;

    private final double speedModifier;

    private int repathCooldown;

    public JuvenileMeleeGoal(Xenomorph xenomorph, double speedModifier) {
        this.xenomorph = xenomorph;
        this.speedModifier = speedModifier;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        var target = xenomorph.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        xenomorph.getNavigation().stop();
        repathCooldown = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        var target = xenomorph.getTarget();

        if (target == null) {
            return;
        }

        xenomorph.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (--repathCooldown <= 0) {
            repathCooldown = 10;
            xenomorph.getNavigation().moveTo(target, speedModifier);
        }

        // Already mid-swing: let the clip finish rather than restarting it, exactly as MeleeAttackAction does.
        if (xenomorph.isAttacking()) {
            return;
        }

        var reach = xenomorph.getBbWidth() * 2.0F * xenomorph.getBbWidth() * 2.0F + target.getBbWidth() + REACH_PADDING;

        if (xenomorph.distanceToSqr(target.getX(), target.getY(), target.getZ()) <= reach) {
            xenomorph.runAttackAnimations();
        }
    }
}
