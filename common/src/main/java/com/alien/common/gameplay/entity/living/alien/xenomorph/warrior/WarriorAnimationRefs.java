package com.alien.common.gameplay.entity.living.alien.xenomorph.warrior;

public class WarriorAnimationRefs {

    // #####################
    // ## ANIMATION NAMES ##
    // #####################

    public static final String CRAWL_ANIMATION_NAME = "crawl";

    public static final String IDLE_ANIMATION_NAME = "idle";

    public static final String LUNGE_ANIMATION_NAME = "lunge";

    public static final String RUN_ANIMATION_NAME = "run";

    public static final String SWIM_ANIMATION_NAME = "swim";

    public static final String WALK_ANIMATION_NAME = "walk";

    // ###############
    // ## ATTACKS ##
    // ###############
    //
    // NAMING: <context>.<action>[.<side>]. The old run-together names (fullattackclaw and friends) are GONE from the
    // animation file - a constant left pointing at one resolves to nothing and fails silently, every frame it plays.

    /**
     * ⚠ MIRRORED PAIR - never dispatch directly, go through {@code WarriorAnimationDispatcher.clawAttack()}.
     * <p>
     * Both arms present ALTERNATES for variety; an arm torn off forces the survivor. Decided per swing from the
     * dismemberment state - see {@code MirroredAttackSide}.
     * </p>
     */
    public static final String ATTACK_CLAW_LEFT_ANIMATION_NAME = "attack.claw.left";

    public static final String ATTACK_CLAW_RIGHT_ANIMATION_NAME = "attack.claw.right";

    public static final String ATTACK_BITE_ANIMATION_NAME = "attack.bite";

    public static final String ATTACK_TAIL_ANIMATION_NAME = "attack.tail";

    public static final String ATTACK_GRAB_ANIMATION_NAME = "attack.grab";

    /** Mirrored pair, crawling. Same side rule as the standing claw. */
    public static final String CRAWL_ATTACK_LEFT_ANIMATION_NAME = "crawl.attack.left";

    public static final String CRAWL_ATTACK_RIGHT_ANIMATION_NAME = "crawl.attack.right";

    public static final String CRAWL_ATTACK_BITE_ANIMATION_NAME = "crawl.attack.bite";

    /** Mirrored pair, swimming. */
    public static final String SWIM_ATTACK_LEFT_ANIMATION_NAME = "swim.attack.left";

    public static final String SWIM_ATTACK_RIGHT_ANIMATION_NAME = "swim.attack.right";

    // ###############
    // ## GAIT ##
    // ###############

    /** Laden gait - an egg on the back or a host held to the chest. The warrior had NO constant for this before. */
    public static final String WALK_CARRY_ANIMATION_NAME = "walk.carry";

    public static final String WALK_DIG_ANIMATION_NAME = "walk.dig";

    // ###############
    // ## CRAWL ##
    // ###############

    /** Getting INTO a crawl. Blocking; a torn-off leg plays the SAME clip FASTER, not a different clip. */
    public static final String CRAWL_DROP_ANIMATION_NAME = "crawl.drop";

    public static final String CRAWL_RISE_ANIMATION_NAME = "crawl.rise";

    public static final String CRAWL_IDLE_ANIMATION_NAME = "crawl.idle";

    public static final String CRAWL_DIG_ANIMATION_NAME = "crawl.dig";

    // ###############
    // ## AIRBORNE ##
    // ###############

    /** HOLDS on its last frame so one clip stretches to any time in the air - see the dispatcher. */
    public static final String JUMP_ANIMATION_NAME = "jump";

    public static final String LAND_ANIMATION_NAME = "land";

    // ###############
    // ## MOLT ##
    // ###############

    /** Wrapping up. Played FORWARDS to cocoon and BACKWARDS to emerge - there is no separate emerge clip. */
    public static final String MOLT_ENTER_ANIMATION_NAME = "molt.enter";

    /**
     * The wait inside the cocoon.
     * <p>
     * ⚠ RENAMED from the shared {@code molting}. The tracker's DEFAULT is still {@code molting} because most castes are
     * not renamed yet, so the warrior opts in through the loop SELECTOR rather than by changing the shared constant.
     * When the last caste converts, flip the default and delete the selectors.
     * </p>
     */
    public static final String MOLT_LOOP_ANIMATION_NAME = "molt.loop";
}
