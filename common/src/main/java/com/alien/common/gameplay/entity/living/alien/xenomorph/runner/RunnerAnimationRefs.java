package com.alien.common.gameplay.entity.living.alien.xenomorph.runner;

public class RunnerAnimationRefs {

    // #####################
    // ## ANIMATION NAMES ##
    // #####################

    public static final String CRAWL_ANIMATION_NAME = "crawl";

    public static final String IDLE_ANIMATION_NAME = "idle";

    public static final String LUNGE_ANIMATION_NAME = "lunge";

    public static final String RUN_ANIMATION_NAME = "run";

    public static final String SWIM_ANIMATION_NAME = "swim";

    public static final String WALK_ANIMATION_NAME = "walk";

    /** Crew gait: dig/place locomotion, used while rostered onto a carve or repair job. */

    // ###############
    // ## ATTACKS ##
    // ###############
    //
    // NAMING: <context>.<action>[.<side>]. The old run-together names are GONE from the animation file - a constant
    // pointing at one resolves to nothing and fails silently, every frame it plays.

    /**
     * ⚠ MIRRORED PAIR - go through {@code RunnerAnimationDispatcher.clawAttack()}, never dispatch directly.
     * <p>
     * The runner's arm attack was called FULL_ATTACK_ARM; the art now calls it a claw, like every other caste.
     * </p>
     */
    public static final String ATTACK_CLAW_LEFT_ANIMATION_NAME = "attack.claw.left";

    public static final String ATTACK_CLAW_RIGHT_ANIMATION_NAME = "attack.claw.right";

    public static final String ATTACK_BITE_ANIMATION_NAME = "attack.bite";

    public static final String ATTACK_TAIL_ANIMATION_NAME = "attack.tail";

    /** Mirrored pair, crawling. */
    public static final String CRAWL_ATTACK_LEFT_ANIMATION_NAME = "crawl.attack.left";

    public static final String CRAWL_ATTACK_RIGHT_ANIMATION_NAME = "crawl.attack.right";

    public static final String CRAWL_ATTACK_BITE_ANIMATION_NAME = "crawl.attack.bite";

    /**
     * ⚠ THE RUNNER'S SWIM ATTACK IS A BITE ONLY - [stated] "they dont have a left and right swim attack only a bite".
     * <p>
     * There is NO mirrored swim pair for this caste, so {@code swimAttack()} must NOT consult MirroredAttackSide the
     * way the drone's and warrior's do. It swims like an animal; it bites.
     * </p>
     */
    public static final String SWIM_ATTACK_BITE_ANIMATION_NAME = "swim.attack.bite";

    // ###############
    // ## GAIT ##
    // ###############

    /** ⚠ RENAMED from the run-together {@code walkdig}. */
    public static final String WALK_DIG_ANIMATION_NAME = "walk.dig";

    public static final String CRAWL_IDLE_ANIMATION_NAME = "crawl.idle";

    public static final String CRAWL_DIG_ANIMATION_NAME = "crawl.dig";

    // #####################
    // ## AIRBORNE ##
    // #####################
    //
    // ⚠ THESE ARE NEW ART. An earlier pass recorded the runner as having NO airborne clips and deliberately left
    // its airborne branch empty; that note is now STALE - the uploaded animation json ships both. The lunge is NOT
    // an airborne pose and must never be borrowed for one: it is already driven by the pounce state.

    /**
     * Leaving the ground. HOLDS on its last frame so ONE clip stretches to any time in the air - a 2-block hop, a
     * running leap and a cliff fall all use it.
     */
    public static final String JUMP_ANIMATION_NAME = "jump";

    /** Played on the tick the ground is REGAINED, and only if a jump actually played. */
    public static final String LAND_ANIMATION_NAME = "land";

    // ###############
    // ## MOLT ##
    // ###############

    /** Wrapping up. FORWARDS to cocoon, BACKWARDS to emerge - no separate emerge clip. */
    public static final String MOLT_ENTER_ANIMATION_NAME = "molt.enter";

    /** ⚠ RENAMED from the shared {@code molting}; opted into via the loop SELECTOR, not the shared default. */
    public static final String MOLT_LOOP_ANIMATION_NAME = "molt.loop";
}
