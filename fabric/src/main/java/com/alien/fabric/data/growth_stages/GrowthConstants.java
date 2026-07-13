package com.alien.fabric.data.growth_stages;

import java.util.concurrent.TimeUnit;

public class GrowthConstants {

    /**
     * 2.5 minutes (3000 ticks). Halved from 5 minutes.
     * <p>
     * The drone line was paying 6000 + 6000 = 12000 ticks to reach adulthood - twice what a runner or spitter paid,
     * because those halve the adolescent stage (see below). Now the whole ladder is 3000 + 3000 = 6000 for a drone.
     * <p>
     * NOTE the chestburster stage is SHARED. There is one {@code chestburster_to_adolescent} with no host predicate:
     * every embryo, from any host, becomes the same chestburster. The host type is only consulted one stage later, at
     * the adolescent step, which is where runner / spitter / drone diverge. So this number is a cost every caste pays.
     */
    public static final int CHESTBURSTER_GROWTH_TIME_IN_TICKS = (int) TimeUnit.MINUTES.toSeconds(5) / 2 * 20;

    /**
     * 2.5 minutes (3000 ticks). The DRONE's adolescent stage - the fallback, for any host that is not a runner host or
     * a llama.
     * <p>
     * Runners and spitters are deliberately derived as {@code / 2} of this rather than hardcoded, so the fast castes
     * stay proportionally fast whenever this is retuned. Halving this therefore also takes them from 3000 to 1500:
     * a drone totals 6000 ticks, a runner or spitter 4500. Grabbing a cow instead of a villager is still the quicker
     * way to a body.
     */
    public static final int ADOLESCENT_GROWTH_TIME_IN_TICKS = (int) TimeUnit.MINUTES.toSeconds(5) / 2 * 20;

    /**
     * The predalien grows on its own clock - 5 minutes a stage, 12000 ticks all told, exactly as before. Split out of
     * the constants above so that retuning the xenomorph ladder does not silently retune the predalien with it.
     */
    public static final int PREDALIEN_CHESTBURSTER_GROWTH_TIME_IN_TICKS = (int) TimeUnit.MINUTES.toSeconds(5) * 20;

    public static final int PREDALIEN_ADOLESCENT_GROWTH_TIME_IN_TICKS = (int) TimeUnit.MINUTES.toSeconds(5) * 20;

    public static final int DRONE_GROWTH_TIME_IN_TICKS = (int) TimeUnit.MINUTES.toSeconds(10) * 20;

    public static final int WARRIOR_GROWTH_TIME_IN_TICKS = (int) TimeUnit.MINUTES.toSeconds(15) * 20;

    public static final int PRAETORIAN_GROWTH_TIME_IN_TICKS = (int) TimeUnit.MINUTES.toSeconds(20) * 20;

    public static final int ROYAL_ADOLESCENT_GROWTH_TIME_IN_TICKS = (int) TimeUnit.MINUTES.toSeconds(5) * 20;

    public static final int ROYAL_CHESTBURSTER_GROWTH_TIME_IN_TICKS = (int) TimeUnit.MINUTES.toSeconds(5) * 20;
}