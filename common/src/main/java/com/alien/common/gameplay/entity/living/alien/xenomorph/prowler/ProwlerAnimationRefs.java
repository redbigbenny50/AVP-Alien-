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

    // ###############
    // ## ATTACKS ##
    // ###############

    /** ⚠ MIRRORED PAIR - go through {@code ProwlerAnimationDispatcher.clawAttack()}, never dispatch directly. */
    public static final String ATTACK_CLAW_LEFT_ANIMATION_NAME = "attack.claw.left";

    public static final String ATTACK_CLAW_RIGHT_ANIMATION_NAME = "attack.claw.right";

    public static final String ATTACK_BITE_ANIMATION_NAME = "attack.bite";

    public static final String ATTACK_TAIL_ANIMATION_NAME = "attack.tail";

    /** Mirrored pair, crawling. */
    public static final String CRAWL_ATTACK_LEFT_ANIMATION_NAME = "crawl.attack.left";

    public static final String CRAWL_ATTACK_RIGHT_ANIMATION_NAME = "crawl.attack.right";

    public static final String CRAWL_ATTACK_BITE_ANIMATION_NAME = "crawl.attack.bite";

    /**
     * ⚠ BITE ONLY IN THE WATER, exactly like the runner - there is no mirrored swim pair for this caste, so
     * {@code swimAttack()} must NOT consult MirroredAttackSide. The old constant was FULL_ATTACK_SWIM.
     */
    public static final String SWIM_ATTACK_BITE_ANIMATION_NAME = "swim.attack.bite";

    // ###############
    // ## CRAWL ##
    // ###############
    //
    // ⚠ NO crawl.drop / crawl.rise, and that is DELIBERATE: [stated] the prowler is low to the ground like the
    // runner, so it is effectively already in the crawl posture and has nothing to transition from. Do not wire
    // transitions for this caste - the clips do not exist and dispatching them fails silently, every frame.

    public static final String CRAWL_IDLE_ANIMATION_NAME = "crawl.idle";

    // #####################
    // ## AIRBORNE ##
    // #####################
    //
    // ⚠ NEW ART, same as the runner. The prowler previously had no airborne clips and its airborne branch was
    // left empty on purpose; the uploaded json now ships both. Do not reuse `lunge` here - it belongs to the pounce.

    /** HOLDS on its last frame so one clip covers any airborne duration. */
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
