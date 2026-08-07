package com.alien.common.gameplay.entity.living.alien.adolescent;

/**
 * Clip names for the reworked adolescent skeleton.
 * <p>
 * The rework replaced a 44-clip LIMB-SPLIT set (`walk.body`, `walk.head`, ... seven tracks per action) with 13
 * WHOLE-BODY clips. The dotted names below are SUB-NAMES, not limb tracks - `attack.bite` is one clip, not a bite
 * played across seven bones - so everything dispatches on {@code AzAlienAnimationUtil.BODY}.
 * <p>
 * The royal adolescent shares these names exactly: it is the same {@link Adolescent} class on the same renderer, and
 * its own animation file was authored with an identical clip set, so one dispatcher drives both.
 */
public class AdolescentAnimationRefs {

    // #####################
    // ## ANIMATION NAMES ##
    // #####################

    public static final String IDLE_ANIMATION_NAME = "idle";

    public static final String WALK_ANIMATION_NAME = "walk";

    public static final String RUN_ANIMATION_NAME = "run";

    public static final String SWIM_ANIMATION_NAME = "swim";

    public static final String CRAWL_ANIMATION_NAME = "crawl";

    public static final String POUNCE_ANIMATION_NAME = "pounce";

    public static final String ATTACK_BITE_ANIMATION_NAME = "attack.bite";

    /** Replaces the old per-arm claw clip: the rework animates the whole swipe in one clip. */
    public static final String ATTACK_SWIPE_ANIMATION_NAME = "attack.swipe";

    /** Bite variant used while swimming - the land bite reads wrong with no ground contact. */
    public static final String ATTACK_SWIMBITE_ANIMATION_NAME = "attack.swimbite";
}
