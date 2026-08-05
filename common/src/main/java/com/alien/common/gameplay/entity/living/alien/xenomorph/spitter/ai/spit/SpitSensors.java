package com.alien.common.gameplay.entity.living.alien.xenomorph.spitter.ai.spit;

import com.alien.common.gameplay.entity.living.alien.xenomorph.spitter.Spitter;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.goap.v1.GOAPSensors;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.sensor.Compose2;
import com.just.ai.goap.sensor.Sensor;
import com.just.ai.goap.sensor.Sensors;
import com.just.core.functional.option.Option;
import net.minecraft.world.entity.LivingEntity;

public class SpitSensors {

    /**
     * Below this the spitter closes and bites instead - acid is for things it cannot reach with claws.
     * <p>
     * WAIVED FOR AIRBORNE TARGETS: the whole point of the minimum is to stop it wasting acid on something it could
     * simply walk into. A phantom hovering three blocks over its head is not something it can walk into, and the dead
     * zone meant a swooping one was never shot at - it was too far while circling and too close while diving.
     */
    private static final int MIN_SPIT_RANGE_IN_BLOCKS = 5;

    private static final int MAX_SPIT_RANGE_IN_BLOCKS = 16;

    /** Height above the spitter at which a target counts as airborne, and so unreachable by melee. */
    private static final double AIRBORNE_HEIGHT_ABOVE = 3.0;

    public static final Compose2<Spitter, Option<LivingEntity>, Boolean, Boolean> IS_TARGET_AT_SPIT_DISTANCE = Sensors.compose(
        GOAPSensors.NEAREST_ATTACKABLE_TARGET.key(),
        GOAPSensors.IS_ON_GROUND.key(),
        StateKey.sensed("is_target_at_spit_distance"),
        (spitter, attackTargetOption, isOnGround) -> {
            if (!isOnGround || attackTargetOption.isNone()) {
                return false;
            }

            var target = attackTargetOption.unwrap();

            if (target.getType().is(AlienEntityTypeTags.ACID_IMMUNE)) {
                return false;
            }

            var distanceSquared = spitter.distanceToSqr(target);
            var airborne = target.getY() - spitter.getY() > AIRBORNE_HEIGHT_ABOVE && !target.onGround();
            var minSquared = airborne ? 0 : MIN_SPIT_RANGE_IN_BLOCKS * MIN_SPIT_RANGE_IN_BLOCKS;
            var maxSquared = MAX_SPIT_RANGE_IN_BLOCKS * MAX_SPIT_RANGE_IN_BLOCKS;

            return distanceSquared >= minSquared
                && distanceSquared <= maxSquared
                && spitter.getSensing().hasLineOfSight(target);
        }
    );

    public static final Sensor.Mono<Spitter, Boolean> IS_SPIT_COOLDOWN_READY = Sensors.map(
        StateKey.sensed("is_spit_cooldown_ready"),
        spitter -> spitter.getSpitterData().isCooldownReady(spitter.tickCount)
    );

    private SpitSensors() {
        throw new UnsupportedOperationException();
    }
}
