package com.alien.common.gameplay.entity.living.alien.xenomorph.razor_claw;

import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackExecutor;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.DamageApplicator;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.util.AlienPredicates;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public final class RazorClawSweepAttack {

    private static final int DURATION_IN_TICKS = 22;

    private static final int COOLDOWN_IN_TICKS = 10 * 20;

    private static final int DAMAGE_SWEEP_START_TICK = 3;

    private static final int DAMAGE_SWEEP_DURATION_IN_TICKS = 10;

    private static final double RANGE_IN_BLOCKS = 3.0;

    private static final double HIT_ARC_DEGREES = 60.0;

    private static final double KNOCKBACK_STRENGTH = 0.3;

    private static final double KNOCKBACK_VERTICAL_BOOST = 0.0625;

    private static final int REQUIRED_MELEE_TARGET_COUNT = 3;

    public static final AttackType ATTACK = AttackType.builder("razor_claw_sweep")
        .requiresAnyArm()
        .defaultDurationInTicks(DURATION_IN_TICKS)
        .damageThresholdPercent(0F)
        .cooldownInTicks(COOLDOWN_IN_TICKS)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .damageApplicator(DamageApplicator.NOOP)
        .executorFactory(Executor::new)
        .activationCondition(
            xenomorph -> xenomorph instanceof RazorClaw razorClaw
                && countNearbyMeleeTargets(razorClaw) >= REQUIRED_MELEE_TARGET_COUNT
        )
        .build();

    private static long countNearbyMeleeTargets(RazorClaw razorClaw) {
        var closeTargetRange = razorClaw.getBbWidth() + 1.0;
        var closeTargetRangeSquared = closeTargetRange * closeTargetRange;

        return razorClaw.getEntitySenseCache()
            .getByClass(LivingEntity.class)
            .stream()
            .filter(target -> razorClaw.distanceToSqr(target) <= closeTargetRangeSquared)
            .filter(target -> razorClaw.getSensing().hasLineOfSight(target))
            .filter(target -> AlienPredicates.canTarget(razorClaw, target))
            .count();
    }

    private RazorClawSweepAttack() {
        throw new UnsupportedOperationException();
    }

    public static class Executor implements AttackExecutor {

        private final Set<Integer> hitEntityIds = new HashSet<>();

        private int totalTicks;

        private int ticksRemaining;

        private float lockedYaw;

        @Override
        public int totalDurationInTicks(AttackType attack) {
            return attack.defaultDurationInTicks() + 1;
        }

        @Override
        public void onStart(Xenomorph entity, AttackType attack, @Nullable LivingEntity target) {
            this.totalTicks = attack.defaultDurationInTicks();
            this.ticksRemaining = totalTicks;
            this.hitEntityIds.clear();
            this.lockedYaw = target != null ? computeYawTowards(entity, target) : entity.getYRot();
        }

        @Override
        public boolean onTick(Xenomorph entity, AttackType attack) {
            lockYaw(entity);

            var previousElapsedTicks = totalTicks - ticksRemaining;
            ticksRemaining--;
            var elapsedTicks = totalTicks - ticksRemaining;

            damageSweptArc(entity, previousElapsedTicks, elapsedTicks);

            return ticksRemaining > 0;
        }

        @Override
        public void onComplete(Xenomorph entity, AttackType attack) {
            hitEntityIds.clear();
        }

        private void lockYaw(Xenomorph entity) {
            entity.setYRot(lockedYaw);
            entity.setYHeadRot(lockedYaw);
            entity.setYBodyRot(lockedYaw);
            entity.getNavigation().stop();
        }

        private void damageSweptArc(Xenomorph entity, int previousElapsedTicks, int elapsedTicks) {
            var sweepStartTick = DAMAGE_SWEEP_START_TICK;
            var sweepEndTick = sweepStartTick + DAMAGE_SWEEP_DURATION_IN_TICKS;

            if (elapsedTicks <= sweepStartTick || previousElapsedTicks >= sweepEndTick) {
                return;
            }

            var previousSweepTick = Mth.clamp(previousElapsedTicks - sweepStartTick, 0, DAMAGE_SWEEP_DURATION_IN_TICKS);
            var currentSweepTick = Mth.clamp(elapsedTicks - sweepStartTick, 0, DAMAGE_SWEEP_DURATION_IN_TICKS);

            if (currentSweepTick <= previousSweepTick) {
                return;
            }

            var previousSweepDegrees = 360.0 * previousSweepTick / DAMAGE_SWEEP_DURATION_IN_TICKS;
            var currentSweepDegrees = 360.0 * currentSweepTick / DAMAGE_SWEEP_DURATION_IN_TICKS;
            var arcPadding = HIT_ARC_DEGREES * 0.5;
            var rangeSquared = RANGE_IN_BLOCKS * RANGE_IN_BLOCKS;

            var targets = entity.level()
                .getEntitiesOfClass(
                    LivingEntity.class,
                    entity.getBoundingBox().inflate(RANGE_IN_BLOCKS, 1.0, RANGE_IN_BLOCKS),
                    target -> target != entity
                        && target.isAlive()
                        && !hitEntityIds.contains(target.getId())
                        && entity.distanceToSqr(target) <= rangeSquared
                        && AlienPredicates.canTarget(entity, target)
                        && entity.getSensing().hasLineOfSight(target)
                );

            for (var target : targets) {
                var targetSweepAngle = computeSweepAngle(entity, target);

                if (targetSweepAngle < previousSweepDegrees - arcPadding || targetSweepAngle > currentSweepDegrees + arcPadding) {
                    continue;
                }

                if (entity.doHurtTarget(target)) {
                    applyRadialKnockback(entity, target);
                    hitEntityIds.add(target.getId());
                }
            }
        }

        private static void applyRadialKnockback(Xenomorph entity, LivingEntity target) {
            var dx = target.getX() - entity.getX();
            var dz = target.getZ() - entity.getZ();
            var length = Math.sqrt(dx * dx + dz * dz);

            if (length < 1.0E-4) {
                var yawRad = entity.getYRot() * Mth.DEG_TO_RAD;
                dx = -Mth.sin(yawRad);
                dz = Mth.cos(yawRad);
                length = 1.0;
            }

            var knockbackX = dx / length;
            var knockbackZ = dz / length;
            target.knockback(KNOCKBACK_STRENGTH, -knockbackX, -knockbackZ);
            target.setDeltaMovement(target.getDeltaMovement().add(0.0, KNOCKBACK_VERTICAL_BOOST, 0.0));
            target.hurtMarked = true;
        }

        private double computeSweepAngle(Xenomorph entity, LivingEntity target) {
            var dx = target.getX() - entity.getX();
            var dz = target.getZ() - entity.getZ();
            var targetYaw = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
            var relativeYaw = Mth.wrapDegrees(targetYaw - lockedYaw);
            return relativeYaw < 0 ? relativeYaw + 360.0 : relativeYaw;
        }

        private static float computeYawTowards(Xenomorph entity, LivingEntity target) {
            var dx = target.getX() - entity.getX();
            var dz = target.getZ() - entity.getZ();
            return (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
        }
    }
}
