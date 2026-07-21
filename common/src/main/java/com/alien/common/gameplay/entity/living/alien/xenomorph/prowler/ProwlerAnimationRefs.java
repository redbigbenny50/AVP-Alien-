package com.alien.common.gameplay.entity.living.alien.xenomorph.prowler;

/**
 * Animation names for the prowler.
 * <p>
 * The prowler was originally rigged the old way - one clip per body part ({@code walk.body}, {@code walk.head},
 * {@code walk.leftarm} ...) composed across seven tracks at runtime. It has since been rebuilt onto the single-body
 * scheme every other alien uses, so these are whole-body clips played on one track.
 */
public class ProwlerAnimationRefs {

    // #####################
    // ## ANIMATION NAMES ##
    // #####################

    public static final String CRAWL_ANIMATION_NAME = "crawl";

    public static final String IDLE_ANIMATION_NAME = "idle";

    public static final String LUNGE_ANIMATION_NAME = "lunge";

    public static final String RUN_ANIMATION_NAME = "run";

    public static final String SWIM_ANIMATION_NAME = "swim";

    public static final String WALK_ANIMATION_NAME = "walk";

    public static final String FULL_ATTACK_ARM_ANIMATION_NAME = "fullattackarm";

    public static final String FULL_ATTACK_BITE_ANIMATION_NAME = "fullattackbite";

    public static final String FULL_ATTACK_TAIL_ANIMATION_NAME = "fullattacktail";

    public static final String FULL_ATTACK_SWIM_ANIMATION_NAME = "fullattackswim";
}
