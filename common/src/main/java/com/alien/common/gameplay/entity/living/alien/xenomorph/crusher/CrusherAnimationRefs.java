package com.alien.common.gameplay.entity.living.alien.xenomorph.crusher;

public class CrusherAnimationRefs {

    // #####################
    // ## ANIMATION NAMES ##
    // #####################

    public static final String BITE_ATTACK_ANIMATION_NAME = "attack.bite";

    public static final String CRAWL_ANIMATION_NAME = "crawl";

    /** ⚠ BLOCKING transition clips - the crusher used to SNAP between postures with these sitting unplayed. */
    /** ⚠ MIRRORED PAIR - the side is chosen at dispatch by MirroredAttackSide, never baked into a caller. */
    public static final String CRAWL_ATTACK_LEFT_ANIMATION_NAME = "crawl.attack.left";

    public static final String CRAWL_ATTACK_RIGHT_ANIMATION_NAME = "crawl.attack.right";

    public static final String CRAWL_ATTACK_BITE_ANIMATION_NAME = "crawl.attack.bite";

    public static final String CRAWL_DROP_ANIMATION_NAME = "crawl.drop";

    public static final String CRAWL_RISE_ANIMATION_NAME = "crawl.rise";

    /** ⭐ The headbutt: medium damage, and the only attack in the game that throws a target UP as well as back. */
    public static final String ATTACK_HEADBUTT_ANIMATION_NAME = "attack.headbutt";

    /** ⭐ Plays for the whole CHARGE state, replacing the run clip it used to borrow. */
    public static final String SPECIAL_ATTACK_CHARGE_ANIMATION_NAME = "special.attack.charge";

    /** ⚠ Bite-only in the water, as on every other caste - there is no mirrored swim pair. */
    public static final String SWIM_ATTACK_BITE_ANIMATION_NAME = "swim.attack.bite";

    public static final String JUMP_ANIMATION_NAME = "jump";

    public static final String LAND_ANIMATION_NAME = "land";

    /** ⭐ ALL THREE MOLT CLIPS ARE AUTHORED - enter and emerge both play FORWARDS, nothing is reversed. */
    public static final String MOLT_ENTER_ANIMATION_NAME = "molt.enter";

    public static final String MOLT_LOOP_ANIMATION_NAME = "molt.loop";

    public static final String MOLT_EMERGE_ANIMATION_NAME = "molt.emerge";

    public static final String CRAWL_IDLE_ANIMATION_NAME = "crawl.idle";

    public static final String IDLE_ANIMATION_NAME = "idle";

    public static final String LEAP_ANIMATION_NAME = "lunge";

    public static final String RUN_ANIMATION_NAME = "run";

    public static final String SWIM_ANIMATION_NAME = "swim";

    public static final String TAIL_ATTACK_ANIMATION_NAME = "attack.tail";

    public static final String WALK_ANIMATION_NAME = "walk";
}
