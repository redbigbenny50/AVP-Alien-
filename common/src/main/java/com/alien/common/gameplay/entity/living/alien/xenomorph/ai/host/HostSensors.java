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
 * GOAP state for the host-capture arc: is there a host worth taking, and are we already carrying one.
 * <p>
 * There are exactly TWO ways a xenomorph is allowed to want a host:
 * <ol>
 * <li>It was <b>dispatched on a host hunt</b> ({@link HostHuntDuty}) - an expedition out into the world.</li>
 * <li>It is a drone doing chores and there is a host <b>loose inside the hive</b> ({@link InteriorSweepDuty}) - a cow
 * that wandered in through a vent, or a captive someone cut out of the webbing.</li>
 * </ol>
 * Everything else ignores hosts entirely, so a worker never wanders off across the claim to grab a sheep out of a
 * field.
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
     * The best host for this xenomorph to capture, or null.
     * <p>
     * A dispatched host-hunt member may take a host anywhere. A drone doing chores may ONLY take one that is loose
     * inside the hive's built interior - never one out in the claim, or the whole workforce downs tools and goes
     * cow-fetching.
     * <p>
     * Hosts already claimed by another drone are invisible here (see {@link HostClaims}). Every candidate is scored
     * identically, so without that filter the whole party walks past two viable hosts to pile onto one.
     */
    public static LivingEntity findCaptureTarget(Xenomorph xenomorph) {
        var onHostHunt = HostHuntDuty.isOnHostHunt(xenomorph);
        var sweeping = !onHostHunt && InteriorSweepDuty.isSweeper(xenomorph);

        if (!onHostHunt && !sweeping) {
            return null;
        }

        // A sweeper only sees what is loose INSIDE the hive.
        var hive = sweeping ? InteriorSweepDuty.hiveOf(xenomorph) : null;
        if (sweeping && hive == null) {
            return null;
        }

        var box = xenomorph.getBoundingBox().inflate(HOST_SEARCH_RADIUS);
        var candidates = xenomorph.level()
                .getEntitiesOfClass(LivingEntity.class, box)
                .stream()
                .filter(candidate -> !sweeping || InteriorSweepDuty.isInsideHive(hive, candidate))
                .filter(candidate -> !HostClaims.isClaimedByOther(candidate, xenomorph))
                .filter(candidate -> !HostClaims.isUnreachableFor(candidate, xenomorph))
                .toList();
        return HostCaptureRules.pickTarget(xenomorph, candidates);
    }
}