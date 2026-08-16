package com.alien.common.gameplay.entity.living.alien.xenomorph.burster;

public class BursterAnimationRefs {

    public static final String IDLE_ANIMATION_NAME = "idle";

    public static final String WALK_ANIMATION_NAME = "walk";

    public static final String RUN_ANIMATION_NAME = "run";

    public static final String CRAWL_ANIMATION_NAME = "crawl";

    public static final String LUNGE_ANIMATION_NAME = "lunge";

    public static final String SWIM_ANIMATION_NAME = "swim";

    /** ⚠ MIRRORED PAIR - the side is chosen at dispatch by MirroredAttackSide, never baked into a caller. */
    public static final String ATTACK_CLAW_LEFT_ANIMATION_NAME = "attack.claw.left";

    public static final String ATTACK_CLAW_RIGHT_ANIMATION_NAME = "attack.claw.right";

    public static final String ATTACK_BITE_ANIMATION_NAME = "attack.bite";

    public static final String ATTACK_TAIL_ANIMATION_NAME = "attack.tail";

    /** ⚠ Bite-only in the water, like the runner and prowler - there is no mirrored swim pair. */
    public static final String SWIM_ATTACK_BITE_ANIMATION_NAME = "swim.attack.bite";

    public static final String MOLT_ENTER_ANIMATION_NAME = "molt.enter";

    /**
     * ⭐ The in-cocoon hold. [stated] "it is supposed to go from the preform cocoon into the molt emerge which is enter
     * reversed which is why i had no molt loop" - the EMERGE is still enter-reversed, exactly as designed. This loop
     * only covers the destination hold that CocoonManager runs BEFORE the emerge.
     */
    public static final String MOLT_LOOP_ANIMATION_NAME = "molt.loop";

    public static final String CRAWL_IDLE_ANIMATION_NAME = "crawl.idle";

    /** ⚠ MIRRORED PAIR. */
    public static final String CRAWL_ATTACK_LEFT_ANIMATION_NAME = "crawl.attack.left";

    public static final String CRAWL_ATTACK_RIGHT_ANIMATION_NAME = "crawl.attack.right";

    public static final String CRAWL_ATTACK_BITE_ANIMATION_NAME = "crawl.attack.bite";

    public static final String JUMP_ANIMATION_NAME = "jump";

    public static final String LAND_ANIMATION_NAME = "land";
}
