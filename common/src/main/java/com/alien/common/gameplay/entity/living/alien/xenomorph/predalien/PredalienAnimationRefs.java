package com.alien.common.gameplay.entity.living.alien.xenomorph.predalien;

/**
 * Animation names for the predalien.
 * <p>
 * Originally rigged the old way - one clip per body part ({@code walk.body}, {@code attackclaw.rightarm} ...) composed
 * across seven tracks at runtime. Rebuilt onto the single-body scheme the rest of the mod uses, so these are whole-body
 * clips played on one track.
 */
public class PredalienAnimationRefs {

    // #####################
    // ## ANIMATION NAMES ##
    // #####################

    public static final String IDLE_ANIMATION_NAME = "idle";

    public static final String WALK_ANIMATION_NAME = "walk";

    public static final String RUN_ANIMATION_NAME = "run";

    public static final String SWIM_ANIMATION_NAME = "swim";

    public static final String CRAWL_ANIMATION_NAME = "crawl";

    /** Pounce, as the runner and prowler have. Played slowed - see the dispatcher. */
    public static final String LUNGE_ANIMATION_NAME = "lunge";

    public static final String JUMP_ANIMATION_NAME = "jump";

    public static final String LAND_ANIMATION_NAME = "land";

    public static final String FULL_ATTACK_CLAW_ANIMATION_NAME = "fullattackclaw";

    public static final String FULL_ATTACK_BITE_ANIMATION_NAME = "fullattackbite";

    public static final String FULL_ATTACK_TAIL_ANIMATION_NAME = "fullattacktail";

    public static final String FULL_ATTACK_BACKHAND_ANIMATION_NAME = "fullattackbackhand";

    public static final String FULL_ATTACK_SWIM_ANIMATION_NAME = "fullattackswim";
}
