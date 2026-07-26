package com.alien.compatibility.avp_human;

import com.alien.Alien;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;

/**
 * Bridge to AVPHuman's radiation exposure counter.
 * <p>
 * Resolved REFLECTIVELY on purpose. avp_human is an optional dependency, and avp_alien compiles against a published
 * avp_human JAR rather than its source - so importing a freshly added avp_human class here would make avp_alien refuse
 * to compile until avp_human had been rebuilt and its jar refreshed, chaining the build order of two mods that are
 * meant to be independent. Reflection keeps avp_alien compiling against ANY avp_human version: if the exposure API is
 * present the irradiated touch works, and if it is missing (older avp_human, or the mod absent entirely) the touch
 * simply does nothing.
 * </p>
 * <p>
 * The lookup is performed once and cached, including the failure case, so a missing API costs one reflective probe for
 * the whole session rather than one per claw.
 * </p>
 */
public final class RadiationCompat {

    /**
     * Exposure added by a single landed hit from an irradiated xenomorph - a twentieth of a sickness level.
     * <p>
     * Deliberately small because these things SWARM. AVPHuman's counter is shared, so ten aliens landing ten hits add
     * ten increments rather than ten separate doses: one scratch is a harmless level-I warning, a sustained brawl walks
     * the victim up a rung or two, and only a losing siege reaches dangerous territory. Ordinary decay sheds it between
     * fights.
     * </p>
     */
    public static final int EXPOSURE_PER_HIT = 180;

    private static final String EXPOSURE_INTERFACE = "com.human.common.model.RadiationExposure";

    private static final String ADD_EXPOSURE_METHOD = "avp_human$addRadiationExposure";

    private static Method addExposureMethod;

    private static boolean lookupAttempted;

    private RadiationCompat() {}

    /** Adds a hit's worth of radiation exposure to the victim, or does nothing if AVPHuman offers no such API. */
    public static void irradiateOnHit(LivingEntity victim) {
        var method = resolveAddExposure();

        if (method == null || !method.getDeclaringClass().isInstance(victim)) {
            return;
        }

        try {
            method.invoke(victim, EXPOSURE_PER_HIT);
        } catch (ReflectiveOperationException exception) {
            Alien.LOGGER.warn("Failed to apply radiation exposure through the AVPHuman bridge", exception);
        }
    }

    private static Method resolveAddExposure() {
        if (lookupAttempted) {
            return addExposureMethod;
        }

        lookupAttempted = true;

        try {
            addExposureMethod = Class.forName(EXPOSURE_INTERFACE).getMethod(ADD_EXPOSURE_METHOD, int.class);
        } catch (ReflectiveOperationException exception) {
            // An AVPHuman without the exposure system (or no AVPHuman at all): the irradiated touch is simply inert.
            addExposureMethod = null;
        }

        return addExposureMethod;
    }
}
