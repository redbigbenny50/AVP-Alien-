package com.alien.common.gameplay.entity.living.alien.xenomorph.carrier.ai.panic_release;

import com.alien.common.gameplay.entity.living.alien.xenomorph.carrier.Carrier;
import com.alien.common.util.AlienPredicates;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.sensor.Sensor;
import com.just.ai.goap.sensor.Sensors;
import net.minecraft.world.entity.LivingEntity;

public class PanicReleaseSensors {

    private static final float LOW_HEALTH_THRESHOLD = 0.25F;

    private static final double SURROUNDED_HOST_RADIUS = 8.0;

    private static final int SURROUNDED_HOST_COUNT = 2;

    private static final int MIN_FACEHUGGER_COUNT = 2;

    public static final Sensor.Mono<Carrier, Boolean> IS_LOW_HEALTH = Sensors.map(
        StateKey.sensed("is_carrier_low_health"),
        PanicReleaseSensors::isLowHealth
    );

    /**
     * ⭐ Oct 1 - OPTED INTO {@code Sensors.every(10)}. A count of hosts around the carrier is a slow-changing fact,
     * nothing waits on it as an action effect, and no path is built from it; holding it half a second only delays a
     * panic by that much. Follows {@code blibGoapSensingOptimizations}.
     */
    public static final Sensor.Mono<Carrier, Boolean> IS_SURROUNDED_BY_HOSTS = Sensors.every(
        10,
        Sensors.map(
            StateKey.sensed("is_carrier_surrounded_by_hosts"),
            PanicReleaseSensors::isSurroundedByHosts
        )
    );

    public static final Sensor.Mono<Carrier, Boolean> HAS_RELEASE_FACEHUGGER_COUNT = Sensors.map(
        StateKey.sensed("has_release_facehugger_count"),
        PanicReleaseSensors::hasReleaseFacehuggerCount
    );

    public static final Sensor.Mono<Carrier, Boolean> SHOULD_PANIC_RELEASE = Sensors.map(
        StateKey.sensed("should_panic_release_facehuggers"),
        carrier -> hasReleaseFacehuggerCount(carrier) && (isLowHealth(carrier) || isSurroundedByHosts(carrier))
    );

    private static boolean isLowHealth(Carrier carrier) {
        return carrier.getMaxHealth() > 0.0F && carrier.getHealth() / carrier.getMaxHealth() < LOW_HEALTH_THRESHOLD;
    }

    /**
     * ⚠ SHOULD_PANIC_RELEASE also needs this answer and must stay a LIVE sensor (it triggers the release itself, so a
     * held "true" could release twice), so the entity query is memoised here instead and both sensors share it.
     */
    private static boolean isSurroundedByHosts(Carrier carrier) {
        return SURROUNDED.get(carrier, carrier.level(), () -> scanSurroundedByHosts(carrier));
    }

    private static final com.alien.common.gameplay.entity.living.alien.xenomorph.ai.SensorMemo<Carrier, Boolean> SURROUNDED =
        new com.alien.common.gameplay.entity.living.alien.xenomorph.ai.SensorMemo<>(10);

    private static boolean scanSurroundedByHosts(Carrier carrier) {
        return carrier.level()
            .getEntitiesOfClass(
                LivingEntity.class,
                carrier.getBoundingBox().inflate(SURROUNDED_HOST_RADIUS),
                host -> host != carrier && AlienPredicates.isFreeHost(carrier, host)
            )
            .size() > SURROUNDED_HOST_COUNT;
    }

    private static boolean hasReleaseFacehuggerCount(Carrier carrier) {
        return carrier.getRidingFacehuggerCount() > MIN_FACEHUGGER_COUNT;
    }

    private PanicReleaseSensors() {
        throw new UnsupportedOperationException();
    }
}
