package com.alien.common.gameplay.entity.living.alien.xenomorph.ravager;

public class RavagerAnimationRefs {

    public static final String IDLE_ANIMATION_NAME = "idle";

    public static final String WALK_ANIMATION_NAME = "walk";

    public static final String RUN_ANIMATION_NAME = "run";

    public static final String SWIM_ANIMATION_NAME = "swim";

    public static final String CRAWL_ANIMATION_NAME = "crawl";

    public static final String CRAWL_IDLE_ANIMATION_NAME = "crawl.idle";

    public static final String CRAWL_UP_ANIMATION_NAME = "crawl.rise";

    public static final String CRAWL_DOWN_ANIMATION_NAME = "crawl.drop";

    // Crawl transition clip lengths x20, shared by the client animator's track hold and the server's
    // CrawlingManager block window.
    public static final int CRAWL_DOWN_TICKS = 10;

    public static final int CRAWL_UP_TICKS = 10;

    /** ⚠ MIRRORED PAIR. */
    public static final String CRAWL_ATTACK_LEFT_ANIMATION_NAME = "crawl.attack.left";

    public static final String CRAWL_ATTACK_RIGHT_ANIMATION_NAME = "crawl.attack.right";

    public static final String CRAWL_BITE_ANIMATION_NAME = "crawl.attack.bite";

    /** ⚠ MIRRORED PAIR - the side is chosen at dispatch by MirroredAttackSide, never baked into a caller. */
    public static final String ATTACK_CLAW_LEFT_ANIMATION_NAME = "attack.claw.left";

    public static final String ATTACK_CLAW_RIGHT_ANIMATION_NAME = "attack.claw.right";

    /**
     * ⚠⚠ THE BOTH-ARMS SWIPE. [stated] "if one arm is missing it will still play at 50% damage and wont play at all
     * with no arms." NOT mirrored - it is one authored clip using both arms.
     * <p>
     * ⚠ HIS NAME FOR IT WAS `attack.claw.both`; THE EXPORT CALLS IT `attack.claw.double`. The art wins.
     * </p>
     */
    public static final String ATTACK_CLAW_DOUBLE_ANIMATION_NAME = "attack.claw.double";

    public static final String ATTACK_BITE_ANIMATION_NAME = "attack.bite";

    public static final String ATTACK_TAIL_ANIMATION_NAME = "attack.tail";

    /** ⚠ ONE clip, not a mirrored pair - "claws" plural, both at once. */
    public static final String SWIM_ATTACK_ANIMATION_NAME = "swim.attack.claws";

    public static final String JUMP_ANIMATION_NAME = "jump";

    public static final String LAND_ANIMATION_NAME = "land";

    /**
     * ⚠⚠ THE LOOP IS `molt.loop`, NOT `molting`. RavagerAnimator passed the LITERAL "molting" - a clip this art has
     * never contained - so the loop half of every warrior→ravager molt was bind-posing SILENTLY.
     */
    public static final String MOLT_LOOP_ANIMATION_NAME = "molt.loop";

    /** Emerge-oriented: the ravager is a molt DESTINATION only, so there is no enter clip and none is needed. */
    public static final String MOLT_EMERGE_ANIMATION_NAME = "molt.emerge";

    public static final String SPECIAL_ATTACK_WARMUP_ANIMATION_NAME = "special.attack.warmup";

    public static final String SPECIAL_ATTACK_ACTIVATE_ANIMATION_NAME = "special.attack.activate";
}
