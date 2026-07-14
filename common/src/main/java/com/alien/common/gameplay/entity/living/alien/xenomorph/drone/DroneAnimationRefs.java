package com.alien.common.gameplay.entity.living.alien.xenomorph.drone;

public class DroneAnimationRefs {

    // #####################
    // ## ANIMATION NAMES ##
    // #####################

    public static final String CRAWL_ANIMATION_NAME = "crawl";

    public static final String IDLE_ANIMATION_NAME = "idle";

    public static final String LUNGE_ANIMATION_NAME = "lunge";

    public static final String RUN_ANIMATION_NAME = "run";

    public static final String SWIM_ANIMATION_NAME = "swim";

    public static final String WALK_ANIMATION_NAME = "walk";

    /**
     * Laden gait - played whenever the drone has an egg on its back or a host clutched to its chest.
     * <p>
     * The key is "walk carry" with a SPACE, matching the animation file. Not "walk.carry".
     */
    public static final String WALK_CARRY_ANIMATION_NAME = "walk carry";

    /**
     * Digging gait, played at 70% speed exactly like the queen's dig loops.
     * <p>
     * NOT YET TRIGGERED BY ANYTHING. Drones do not dig: hive construction still instant-stamps its structure pieces
     * every 200 ticks, and vent placement stopped drilling entirely. This is wired and waiting for the drone-carve /
     * biomass construction economy, at which point the carve action calls {@code walkDig()} and it just works.
     */
    public static final String WALK_DIG_ANIMATION_NAME = "walk dig";

    public static final String FULL_ATTACK_CLAW_ANIMATION_NAME = "fullattackclaw";

    public static final String FULL_ATTACK_BITE_ANIMATION_NAME = "fullattackbite";

    public static final String FULL_ATTACK_TAIL_ANIMATION_NAME = "fullattacktail";
}
