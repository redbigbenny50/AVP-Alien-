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

    private static final String SET_EXPOSURE_METHOD = "avp_human$setRadiationExposure";

    private static Method addExposureMethod;

    private static Method setExposureMethod;

    private static boolean setLookupAttempted;

    private static boolean lookupAttempted;

    private RadiationCompat() {}

    /**
     * One full sickness level, derived from {@link #EXPOSURE_PER_HIT} rather than restated, so the two can never drift
     * apart if the per-hit dose is ever retuned.
     */
    public static final int EXPOSURE_PER_SICKNESS_LEVEL = EXPOSURE_PER_HIT * 20;

    /** Adds a hit's worth of radiation exposure to the victim, or does nothing if AVPHuman offers no such API. */
    public static void irradiateOnHit(LivingEntity victim) {
        addExposure(victim, EXPOSURE_PER_HIT);
    }

    /**
     * Adds an arbitrary amount of exposure - use {@link #EXPOSURE_PER_SICKNESS_LEVEL} to think in sickness tiers rather
     * than raw counter units. Silently does nothing when AVPHuman is absent or too old to expose the API, which is the
     * whole point of the reflective bridge.
     */
    public static void addExposure(LivingEntity victim, int exposure) {
        var method = resolveAddExposure();

        if (method == null || !method.getDeclaringClass().isInstance(victim)) {
            return;
        }

        try {
            method.invoke(victim, exposure);
        } catch (Throwable throwable) {
            // ⚠ THROWABLE, NOT ReflectiveOperationException. Loading a class runs the mixin transformer over it,
            // so a reflective probe executes every OTHER mod's mixins targeting that class. A broken one throws
            // MixinTransformerError - an Error, and NOT a LinkageError - which the narrower catch let straight
            // through, turning another mod's version skew into a crash attributed to us.
            Alien.LOGGER.warn("Failed to apply radiation exposure through the AVPHuman bridge", throwable);
        }
    }

    /**
     * Pins the victim's exposure to zero. AVP: Human derives the sickness level from this counter, so holding it at
     * zero is immunity by another route - the only one a potion can reach, since their own immunity gate asks about
     * entity tags and armour rather than effects.
     */
    public static void clearExposure(LivingEntity victim) {
        var method = resolveSetExposure();

        if (method == null || !method.getDeclaringClass().isInstance(victim)) {
            return;
        }

        try {
            method.invoke(victim, 0);
        } catch (Throwable throwable) {
            // ⚠ THROWABLE, NOT ReflectiveOperationException. Loading a class runs the mixin transformer over it,
            // so a reflective probe executes every OTHER mod's mixins targeting that class. A broken one throws
            // MixinTransformerError - an Error, and NOT a LinkageError - which the narrower catch let straight
            // through, turning another mod's version skew into a crash attributed to us.
            Alien.LOGGER.warn("Failed to clear radiation exposure through the AVPHuman bridge", throwable);
        }
    }

    private static Method resolveSetExposure() {
        if (setLookupAttempted) {
            return setExposureMethod;
        }

        setLookupAttempted = true;

        try {
            setExposureMethod = Class.forName(EXPOSURE_INTERFACE).getMethod(SET_EXPOSURE_METHOD, int.class);
        } catch (Throwable throwable) {
            // ⚠ THROWABLE, NOT ReflectiveOperationException. Loading a class runs the mixin transformer over it,
            // so a reflective probe executes every OTHER mod's mixins targeting that class. A broken one throws
            // MixinTransformerError - an Error, and NOT a LinkageError - which the narrower catch let straight
            // through, turning another mod's version skew into a crash attributed to us.
            setExposureMethod = null;
        }

        return setExposureMethod;
    }

    private static Method resolveAddExposure() {
        if (lookupAttempted) {
            return addExposureMethod;
        }

        lookupAttempted = true;

        try {
            addExposureMethod = Class.forName(EXPOSURE_INTERFACE).getMethod(ADD_EXPOSURE_METHOD, int.class);
        } catch (Throwable throwable) {
            // ⚠ THROWABLE, NOT ReflectiveOperationException. Loading a class runs the mixin transformer over it,
            // so a reflective probe executes every OTHER mod's mixins targeting that class. A broken one throws
            // MixinTransformerError - an Error, and NOT a LinkageError - which the narrower catch let straight
            // through, turning another mod's version skew into a crash attributed to us.
            // An AVPHuman without the exposure system (or no AVPHuman at all): the irradiated touch is simply inert.
            addExposureMethod = null;
        }

        return addExposureMethod;
    }
}
