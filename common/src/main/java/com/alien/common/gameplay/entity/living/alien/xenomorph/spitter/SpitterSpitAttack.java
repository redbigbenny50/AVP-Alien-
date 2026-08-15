package com.alien.common.gameplay.entity.living.alien.xenomorph.spitter;

import com.alien.common.gameplay.entity.projectile.AcidSpit;
import com.alien.common.model.alien.variant.AlienVariant;
import net.minecraft.world.entity.LivingEntity;

public final class SpitterSpitAttack {

    private static final float PROJECTILE_POWER = 1.5F;

    private static final float PROJECTILE_INACCURACY = 2.0F;

    private static final double ARC_COMPENSATION_FACTOR = 0.1;

    private SpitterSpitAttack() {
        throw new UnsupportedOperationException();
    }

    public static void shootAtTarget(Spitter spitter, LivingEntity target) {
        shootAtTarget(spitter, target, spitter.getVariant());
        spitter.getSpitterData().setLastSpitTick(spitter.tickCount);
    }

    public static void shootAtTarget(LivingEntity shooter, LivingEntity target, AlienVariant variant) {
        var targetEyePos = target.getEyePosition();
        var directionX = targetEyePos.x - shooter.getX();
        var directionY = targetEyePos.y - shooter.getEyeY();
        var directionZ = targetEyePos.z - shooter.getZ();
        var gravityCompensation = computeGravityCompensation(directionX, directionZ);

        shoot(shooter, variant, directionX, directionY + gravityCompensation, directionZ);
    }

    public static void shootForward(LivingEntity shooter, AlienVariant variant) {
        var direction = shooter.getLookAngle();
        shoot(shooter, variant, direction.x, direction.y, direction.z);
    }

    private static void shoot(LivingEntity shooter, AlienVariant variant, double directionX, double directionY, double directionZ) {
        var spit = new AcidSpit(shooter, shooter.level(), variant);
        spit.shoot(directionX, directionY, directionZ, PROJECTILE_POWER, PROJECTILE_INACCURACY);
        shooter.level().addFreshEntity(spit);
    }

    private static double computeGravityCompensation(double directionX, double directionZ) {
        var horizontalDistance = Math.sqrt(directionX * directionX + directionZ * directionZ);
        return horizontalDistance * ARC_COMPENSATION_FACTOR;
    }
}
