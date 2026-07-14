package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.party.HostCaptureRules;
import com.alien.common.gameplay.hive.party.HostClaims;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.sensor.Sensor;
import com.just.ai.goap.sensor.Sensors;
import net.minecraft.world.entity.LivingEntity;

/**
 * GOAP state for the host-hunt arc: is there a host worth taking, and are we already carrying one.
 * <p>
 * Only a dispatched host-hunt party member hunts. Everything else in the hive ignores this entirely, so a worker never
 * wanders off to grab a cow.
 */
public final class HostSensors {

    private HostSensors() {}

    /** How far a host-hunt drone will look for something to carry home. */
    public static final double HOST_SEARCH_RADIUS = 32.0;

    public static final Sensor.Mono<Xenomorph, Boolean> HAS_TARGET_HOST = Sensors.map(
        StateKey.sensed("has_target_host"),
        xenomorph -> findCaptureTarget(xenomorph) != null
    );

    public static final Sensor.Mono<Xenomorph, Boolean> IS_CARRYING_HOST = Sensors.map(
        StateKey.sensed("is_carrying_host"),
        xenomorph -> xenomorph.getPassengers()
            .stream()
            .anyMatch(passenger -> passenger.getType().is(AlienEntityTypeTags.HOSTS))
    );

    /**
     * The best host for this xenomorph to capture, or null. Gated on host-hunt party membership: a xenomorph that was
     * not sent out to fetch a host will never target one.
     * <p>
     * Hosts already claimed by another drone are invisible here (see {@link HostClaims}). Every party member scores
     * candidates identically, so without that filter the whole party walks past two viable hosts to pile onto one.
     */
    public static LivingEntity findCaptureTarget(Xenomorph xenomorph) {
        if (!HostHuntDuty.isOnHostHunt(xenomorph)) {
            return null;
        }
        var box = xenomorph.getBoundingBox().inflate(HOST_SEARCH_RADIUS);
        var candidates = xenomorph.level()
            .getEntitiesOfClass(LivingEntity.class, box)
            .stream()
            .filter(candidate -> !HostClaims.isClaimedByOther(candidate, xenomorph))
            .filter(candidate -> !HostClaims.isUnreachableFor(candidate, xenomorph))
            .toList();
        return HostCaptureRules.pickTarget(xenomorph, candidates);
    }
}
