package com.alien.common.gameplay.entity.living.alien.xenomorph.queen.ai.hibernation;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenLifecyclePhase;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenLifecyclePhaseManager;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenLifecyclePhaseManager.HibernationActivity;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.sensor.Sensor;
import com.just.ai.goap.sensor.Sensors;

/**
 * Sensors for hibernation (Stages 3a + 3b). HIBERNATION has three sub-states (see {@link HibernationActivity}): ASLEEP
 * (held, sleep clock running), DEFENDING (roused by damage, fighting, clock paused), and RETURNING (walking back to the
 * anchor once the threat clears). DEFENDING intentionally exposes no sensor — when neither flag is true her normal
 * combat/idle AI runs. Both flags are false whenever the front-end is disabled or she is not a queen in HIBERNATION.
 */
public final class HibernationSensors {

    public static final Sensor.Mono<Xenomorph, Boolean> IS_ASLEEP = Sensors.map(
        StateKey.sensed("hibernation_is_asleep"),
        xenomorph -> isInActivity(xenomorph, HibernationActivity.ASLEEP)
    );

    public static final Sensor.Mono<Xenomorph, Boolean> IS_RETURNING = Sensors.map(
        StateKey.sensed("hibernation_is_returning"),
        xenomorph -> isInActivity(xenomorph, HibernationActivity.RETURNING)
    );

    public static boolean isInActivity(Xenomorph xenomorph, HibernationActivity activity) {
        if (!QueenLifecyclePhaseManager.isEnabled() || !(xenomorph instanceof Queen queen)) {
            return false;
        }
        var manager = queen.getLifecyclePhaseManager();
        return manager.getPhase() == QueenLifecyclePhase.HIBERNATION
            && manager.getHibernationActivity() == activity;
    }

    private HibernationSensors() {
        throw new UnsupportedOperationException();
    }
}
