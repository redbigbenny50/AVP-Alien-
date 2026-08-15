package com.alien.common.gameplay.entity.living.alien.xenomorph.ravager;

import com.alien.common.gameplay.entity.dismemberment.RavagerHeadDismemberment;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackExecutor;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.DamageApplicator;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ravager.ai.RavagerAreaAttackUtil;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.key.AlienDamageTypeKeys;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public final class RavagerSpecialCleaveAttack {

    private static final int WINDUP_DURATION_IN_TICKS = 20;

    private static final int MAX_EXTRA_WINDUP_TICKS = 5;

    private static final int ACTIVE_DURATION_IN_TICKS = 20;

    private static final int COOLDOWN_IN_TICKS = 30 * 20;

    private static final float DAMAGE_POINT_PERCENT = 0.5F;

    public static final AttackType WINDUP = AttackType.builder("ravager_special_cleave_windup")
        .requiresAllLegs()
        .defaultDurationInTicks(WINDUP_DURATION_IN_TICKS)
        .damageThresholdPercent(0F)
        .damageApplicator(DamageApplicator.NOOP)
        .build();

    public static final AttackType ATTACK = AttackType.builder("ravager_special_cleave")
        .requiresAllLegs()
        .defaultDurationInTicks(ACTIVE_DURATION_IN_TICKS)
        .damageThresholdPercent(0F)
        .cooldownInTicks(COOLDOWN_IN_TICKS)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .damageApplicator(DamageApplicator.NOOP)
        .executorFactory(Executor::new)
        .build();

    private RavagerSpecialCleaveAttack() {
        throw new UnsupportedOperationException();
    }

    public static class Executor implements AttackExecutor {

        private int windupTicksRemaining;

        private int activeTicksRemaining;

        private int totalActiveTicks;

        private float lockedYaw;

        private boolean damageDealt;

        @Override
        public int totalDurationInTicks(AttackType attack) {
            return WINDUP_DURATION_IN_TICKS + 2;
        }

        @Override
        public void onStart(Xenomorph entity, AttackType attack, @Nullable LivingEntity target) {
            var random = entity.getRandom();
            var windupDuration = WINDUP_DURATION_IN_TICKS + random.nextInt(MAX_EXTRA_WINDUP_TICKS + 1);

            this.windupTicksRemaining = windupDuration;
            this.activeTicksRemaining = 0;
            this.totalActiveTicks = ACTIVE_DURATION_IN_TICKS;
            this.lockedYaw = target != null ? computeYawTowards(entity, target) : entity.getYRot();
            this.damageDealt = false;

            entity.transitionAttack(WINDUP, windupDuration + 2);
        }

        @Override
        public boolean onTick(Xenomorph entity, AttackType attack) {
            if (windupTicksRemaining > 0) {
                lockYaw(entity);
                windupTicksRemaining--;

                if (windupTicksRemaining <= 0) {
                    activeTicksRemaining = totalActiveTicks;
                    damageDealt = false;
                    entity.transitionAttack(attack, totalActiveTicks + 1);
                }

                return true;
            }

            if (activeTicksRemaining <= 0) {
                return false;
            }

            lockYaw(entity);

            var elapsedTicks = totalActiveTicks - activeTicksRemaining;
            var damageThresholdTick = (int) (totalActiveTicks * DAMAGE_POINT_PERCENT);

            if (!damageDealt && elapsedTicks >= damageThresholdTick && entity instanceof Ravager ravager) {
                damageEntitiesInFront(ravager);
                damageDealt = true;
            }

            activeTicksRemaining--;

            return activeTicksRemaining > 0;
        }

        private void lockYaw(Xenomorph entity) {
            entity.setYRot(lockedYaw);
            entity.setYHeadRot(lockedYaw);
            entity.setYBodyRot(lockedYaw);
            entity.getNavigation().stop();
        }

        private static void damageEntitiesInFront(Ravager ravager) {
            var damageSource = ravager.damageSources().source(AlienDamageTypeKeys.RAVAGER_SPECIAL, ravager);
            var targets = RavagerAreaAttackUtil.getEntitiesInFront(
                ravager,
                Ravager.FRONT_AOE_RANGE_IN_BLOCKS,
                Ravager.FRONT_AOE_CONE_ANGLE_DEGREES
            );

            for (var target : targets) {
                if (target.isInvulnerableTo(damageSource)) {
                    continue;
                }

                if (RavagerAreaAttackUtil.isSmallerThanRavager(ravager, target)) {
                    target.hurt(damageSource, Float.MAX_VALUE);
                    RavagerHeadDismemberment.tryDismemberHead(
                        target,
                        RavagerHeadDismemberment.knockbackAwayFrom(ravager)
                    );
                } else {
                    target.hurt(damageSource, target.getMaxHealth() * 0.25F);
                }
            }
        }

        private static float computeYawTowards(Xenomorph entity, LivingEntity target) {
            var dx = target.getX() - entity.getX();
            var dz = target.getZ() - entity.getZ();
            return (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
        }
    }
}
