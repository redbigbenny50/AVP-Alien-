package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.lunge;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphAttackLimbRequirement;
import com.blib.api.common.goap.v1.GOAPSensors;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.sensor.Compose2;
import com.just.ai.goap.sensor.Sensors;
import com.just.core.functional.option.Option;
import net.minecraft.world.entity.LivingEntity;

public class LungeSensors {

    public static <T extends Xenomorph> Compose2<T, Option<LivingEntity>, Boolean, Boolean> createLungeRangeSensor(LungeConfig config) {
        return Sensors.compose(
            GOAPSensors.NEAREST_ATTACKABLE_TARGET.key(),
            GOAPSensors.IS_ON_GROUND.key(),
            StateKey.sensed("is_target_in_lunge_range"),
            (xenomorph, attackTargetOption, isOnGround) -> {
                if (!isOnGround || attackTargetOption.isNone() || !hasRequiredLegs(xenomorph)) {
                    return false;
                }

                if (!isLungeCooldownExpired(xenomorph, config)) {
                    return false;
                }

                var attackTarget = attackTargetOption.unwrap();

                return isInLungeRange(xenomorph, attackTarget, config)
                    && xenomorph.getSensing().hasLineOfSight(attackTarget);
            }
        );
    }

    public static boolean hasRequiredLegs(Xenomorph xenomorph) {
        return XenomorphAttackLimbRequirement.ALL_LEGS.isSatisfiedBy(xenomorph);
    }

    private static boolean isLungeCooldownExpired(Xenomorph xenomorph, LungeConfig config) {
        var ticksSinceLastLunge = xenomorph.tickCount - xenomorph.getXenomorphData().getLastLungeTick();
        return ticksSinceLastLunge >= config.cooldownInTicks();
    }

    private static boolean isInLungeRange(Xenomorph xenomorph, LivingEntity target, LungeConfig config) {
        var dx = xenomorph.getX() - target.getX();
        var dz = xenomorph.getZ() - target.getZ();
        var horizontalDistanceSquared = dx * dx + dz * dz;

        var minimumRangeSquared = config.minRangeInBlocks() * config.minRangeInBlocks();
        var maximumRangeSquared = config.maxRangeInBlocks() * config.maxRangeInBlocks();

        if (horizontalDistanceSquared > maximumRangeSquared) {
            return false;
        }

        var dy = Math.abs(xenomorph.getY() - target.getY());
        var verticalDistanceSquared = dy * dy;

        if (verticalDistanceSquared > maximumRangeSquared) {
            return false;
        }

        var minimumVerticalLungeRange = xenomorph.getBbHeight() * xenomorph.getBbHeight();
        var isTargetTooCloseHorizontally = horizontalDistanceSquared < minimumRangeSquared;
        var isTargetTooCloseVertically = dy <= minimumVerticalLungeRange;

        return !isTargetTooCloseHorizontally || !isTargetTooCloseVertically;
    }

    private LungeSensors() {
        throw new UnsupportedOperationException();
    }
}
