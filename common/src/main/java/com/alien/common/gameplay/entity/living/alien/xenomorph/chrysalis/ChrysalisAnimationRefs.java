package com.alien.common.gameplay.entity.living.alien.xenomorph.chrysalis;

public class ChrysalisAnimationRefs {

    public static final String IDLE_ANIMATION_NAME = "idle";

    public static final String WALK_ANIMATION_NAME = "walk";

    public static final String RUN_ANIMATION_NAME = "run";

    public static final String SWIM_ANIMATION_NAME = "swim";

    public static final String CRAWL_ANIMATION_NAME = "crawl";

    public static final String ATTACKBITE_ANIMATION_NAME = "attack.bite";

    /** ⚠ MIRRORED PAIR - the side is chosen at dispatch by MirroredAttackSide, never baked into a caller. */
    public static final String ATTACK_CLAW_LEFT_ANIMATION_NAME = "attack.claw.left";

    public static final String ATTACK_CLAW_RIGHT_ANIMATION_NAME = "attack.claw.right";

    public static final String ATTACKTAIL_ANIMATION_NAME = "attack.tail";

    public static final String CRAWL_IDLE_ANIMATION_NAME = "crawl.idle";

    /**
     * ⚠ BLOCKING transition clips. The chrysalis is a TALL caste by the low-slung rule, so it gets the pair - it used
     * to SNAP between postures with nothing to play.
     */
    public static final String CRAWL_DROP_ANIMATION_NAME = "crawl.drop";

    public static final String CRAWL_RISE_ANIMATION_NAME = "crawl.rise";

    /** ⚠ MIRRORED PAIR. */
    public static final String CRAWL_ATTACK_LEFT_ANIMATION_NAME = "crawl.attack.left";

    public static final String CRAWL_ATTACK_RIGHT_ANIMATION_NAME = "crawl.attack.right";

    public static final String CRAWL_ATTACK_BITE_ANIMATION_NAME = "crawl.attack.bite";

    /** ⚠ Bite-only in the water, like the runner and prowler - there is no mirrored swim pair here. */
    public static final String SWIM_ATTACK_BITE_ANIMATION_NAME = "swim.attack.bite";

    public static final String JUMP_ANIMATION_NAME = "jump";

    public static final String LAND_ANIMATION_NAME = "land";

    /**
     * ⚠⚠ THE LOOP IS `molt.loop`, NOT `molting`. ChrysalisAnimator was on the no-arg tracker, which hardcodes "molting"
     * - a clip this art has never contained - so the loop half of every prowler→chrysalis molt was bind-posing
     * SILENTLY. Named through the Refs now.
     */
    public static final String MOLT_ENTER_ANIMATION_NAME = "molt.enter";

    public static final String MOLT_LOOP_ANIMATION_NAME = "molt.loop";

    /** ⭐ The stunning headbutt - a REGULAR attack on a long cooldown, not a special. */
    public static final String ATTACK_CHARGE_ANIMATION_NAME = "attack.charge";

    /**
     * ⭐ The defensive curl. `defense.start` plays on the tick the stance opens and HOLDS its last frame for the whole
     * 30 seconds - there is no authored loop, so the held pose IS the loop. `defense.end` plays as it uncurls.
     */
    public static final String DEFENSE_START_ANIMATION_NAME = "defense.start";

    public static final String DEFENSE_END_ANIMATION_NAME = "defense.end";

    public static final String ROLL_START_ANIMATION_NAME = "roll.start";

    public static final String ROLL_LOOP_ANIMATION_NAME = "roll.loop";

    public static final String ROLL_STOP_ANIMATION_NAME = "roll.stop";

    public static final String ROLL_SMASHED_ANIMATION_NAME = "roll.smashed";
}
