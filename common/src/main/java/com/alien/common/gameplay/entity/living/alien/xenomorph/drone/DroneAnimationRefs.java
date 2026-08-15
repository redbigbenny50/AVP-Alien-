package com.alien.common.gameplay.entity.living.alien.xenomorph.drone;

public class DroneAnimationRefs {

    // #####################
    // ## ANIMATION NAMES ##
    // #####################

    public static final String CRAWL_ANIMATION_NAME = "crawl";

    public static final String IDLE_ANIMATION_NAME = "idle";

    public static final String LUNGE_ANIMATION_NAME = "lunge";

    public static final String RUN_ANIMATION_NAME = "run";

    public static final String SWIM_ANIMATION_NAME = "swim";

    public static final String WALK_ANIMATION_NAME = "walk";

    /**
     * Laden gait - played whenever the drone has an egg on its back or a host clutched to its chest.
     * <p>
     * ⚠ RENAMED "walk carry" -> "walk.carry" with the Aug-10 art pass. The old key used a SPACE and the comment here
     * used to insist on it; the new file is dotted like everything else. A stale name here fails SILENTLY, per frame.
     * </p>
     */
    public static final String WALK_CARRY_ANIMATION_NAME = "walk.carry";

    /**
     * Digging gait, played at 70% speed exactly like the queen's dig loops.
     * <p>
     * NOT YET TRIGGERED BY ANYTHING. Drones do not dig: hive construction still instant-stamps its structure pieces
     * every 200 ticks, and vent placement stopped drilling entirely. This is wired and waiting for the drone-carve /
     * biomass construction economy, at which point the carve action calls {@code walkDig()} and it just works.
     */
    public static final String WALK_DIG_ANIMATION_NAME = "walk.dig";

    // ###############
    // ## ATTACKS ##
    // ###############
    //
    // NAMING, as of the Aug-10 art pass: <context>.<action>[.<side>]. The old run-together names (fullattackclaw and
    // friends) are GONE from the animation file - a constant left pointing at one resolves to nothing and fails
    // silently, every frame the attack plays.

    /**
     * ⚠ MIRRORED PAIR - never dispatch these directly, go through {@code DroneAnimationDispatcher.clawAttack()}.
     * <p>
     * A drone with both arms ALTERNATES for variety; one with an arm torn off is forced onto the survivor. The choice
     * is made per swing from the dismemberment state - see {@code MirroredAttackSide}.
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

    /** Mirrored pair, swimming. Same side rule again. */
    public static final String SWIM_ATTACK_LEFT_ANIMATION_NAME = "swim.attack.left";

    public static final String SWIM_ATTACK_RIGHT_ANIMATION_NAME = "swim.attack.right";

    // ###############
    // ## CRAWL ##
    // ###############

    /** Getting INTO a crawl. Blocking; a torn-off leg plays the SAME clip FASTER, it is not a different clip. */
    public static final String CRAWL_DROP_ANIMATION_NAME = "crawl.drop";

    /** Getting back UP. */
    public static final String CRAWL_RISE_ANIMATION_NAME = "crawl.rise";

    // ###############
    // ## MOLT ##
    // ###############

    /** Wrapping itself up. Played FORWARDS to cocoon in, and BACKWARDS to emerge - there is no separate emerge clip. */
    public static final String MOLT_ENTER_ANIMATION_NAME = "molt.enter";

    /**
     * The wait inside the cocoon.
     * <p>
     * ⚠ RENAMED from the shared {@code molting} in the Aug-10 art pass. The tracker's DEFAULT is still {@code molting}
     * because the other eleven castes have not been renamed yet, so the drone opts in through the loop SELECTOR rather
     * than by changing the shared constant - that way each caste can convert as its art lands, and nothing is broken in
     * between. When the last caste is converted, flip the default and delete the selectors.
     * </p>
     */
    public static final String MOLT_LOOP_ANIMATION_NAME = "molt.loop";

    /** Airborne. HOLDS on its last frame so one clip stretches to any time in the air - see the dispatcher. */
    public static final String JUMP_ANIMATION_NAME = "jump";

    public static final String LAND_ANIMATION_NAME = "land";

    public static final String CRAWL_IDLE_ANIMATION_NAME = "crawl.idle";

    public static final String CRAWL_DIG_ANIMATION_NAME = "crawl.dig";
}
