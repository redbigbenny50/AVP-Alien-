package com.alien.common.gameplay.entity.living.alien.xenomorph.spitter;

public class SpitterAnimationRefs {

    // #####################
    // ## ANIMATION NAMES ##
    // #####################

    public static final String CRAWL_ANIMATION_NAME = "crawl";

    public static final String IDLE_ANIMATION_NAME = "idle";

    public static final String LUNGE_ANIMATION_NAME = "lunge";

    public static final String RUN_ANIMATION_NAME = "run";

    public static final String SWIM_ANIMATION_NAME = "swim";

    public static final String WALK_ANIMATION_NAME = "walk";

    // ###############################
    // ## BIPED ATTACKS ##
    // ###############################

    /** ⚠ MIRRORED PAIR - go through {@code SpitterAnimationDispatcher.clawAttack()}. */
    public static final String ATTACK_CLAW_LEFT_ANIMATION_NAME = "attack.claw.left";

    public static final String ATTACK_CLAW_RIGHT_ANIMATION_NAME = "attack.claw.right";

    public static final String ATTACK_BITE_ANIMATION_NAME = "attack.bite";

    public static final String ATTACK_TAIL_ANIMATION_NAME = "attack.tail";

    /** The spitter's signature. Not mirrored - it spits with its head. */
    public static final String SPECIAL_ATTACK_SPIT_ANIMATION_NAME = "special.attack.spit";

    // ###############################
    // ## QUAD ATTACKS ##
    // ###############################
    //
    // ⚠ THE SPITTER IS THE ONLY CASTE WITH TWO POSTURES, so it carries a SECOND full attack set for quadruped mode.
    // The quad claw is mirrored too - the arm it has lost is the arm it has lost, whichever posture it is standing in.
    //
    // ⭐ WHICH CLIP IS WHICH POSTURE, [stated]:
    // BIPED - idle, walk, jump, land, special.attack.spit, and the whole attack.* set
    // QUAD - run, lunge, quad.idle, and the whole quad.attack.* set
    //
    // [stated] "if its walking and attacks an enemy close to it then it would use the normal arm attacks bite etc.
    // if its running at an enemy or closeing the distance it would use run and the quad attacks. if its spitting and
    // an enemy attacks it or gets close then it would use the normal attacks."
    //
    // The posture itself lives on the ENTITY as a synced flag (Spitter.isQuadPosture), not in the animator, because
    // the SERVER replays the attack clip every tick to place limb hitboxes and has to agree with what the client is
    // showing. See Spitter.updateQuadPosture for the rules and Spitter.beginAttack for why it freezes mid-swing.

    public static final String QUAD_ATTACK_CLAW_LEFT_ANIMATION_NAME = "quad.attack.claw.left";

    public static final String QUAD_ATTACK_CLAW_RIGHT_ANIMATION_NAME = "quad.attack.claw.right";

    public static final String QUAD_ATTACK_BITE_ANIMATION_NAME = "quad.attack.bite";

    public static final String QUAD_ATTACK_TAIL_ANIMATION_NAME = "quad.attack.tail";

    /**
     * ⭐⭐ THE CLIP THAT MAKES QUAD A POSTURE RATHER THAN A MOMENT.
     * <p>
     * Without it, quad existed only for as long as {@code run} or {@code lunge} was playing - the instant a spitter
     * stopped it had nothing to stand in but the BIPED {@code idle}, so it would rear up the moment it arrived and
     * every melee swing after the first would be biped. With a quad idle the posture can be HELD, so a spitter that
     * charges in stays down on all fours and keeps fighting from there.
     * </p>
     * <p>
     * ⚠ THERE IS NO QUAD WALK AND NO TRANSITION CLIP. Walking is biped-only, which is what makes walking the natural
     * trigger for standing back up; and the change of posture is covered by the track's 5-tick blend, not by an
     * authored rise/drop the way {@code crawl.drop} / {@code crawl.rise} cover the crawl.
     * </p>
     */
    public static final String QUAD_IDLE_ANIMATION_NAME = "quad.idle";

    // ###############################
    // ## CRAWL ##
    // ###############################

    /** ⚠ MIRRORED PAIR, crawling. */
    public static final String CRAWL_ATTACK_LEFT_ANIMATION_NAME = "crawl.attack.left";

    public static final String CRAWL_ATTACK_RIGHT_ANIMATION_NAME = "crawl.attack.right";

    /**
     * ⚠ RENAMED. This used to be the one caste-specific oddity in the whole convention - the spitter shipped
     * {@code crawl.bite} while every other caste used {@code crawl.attack.bite}. [stated] "also i renamed the
     * crawl.bite to crawl.attack.bite", so the exception is gone and the constant name now matches its siblings.
     */
    public static final String CRAWL_ATTACK_BITE_ANIMATION_NAME = "crawl.attack.bite";

    /** Getting INTO a crawl. Blocking; a torn-off leg plays the SAME clip FASTER. */
    public static final String CRAWL_DROP_ANIMATION_NAME = "crawl.drop";

    public static final String CRAWL_RISE_ANIMATION_NAME = "crawl.rise";

    public static final String CRAWL_IDLE_ANIMATION_NAME = "crawl.idle";

    // ###############################
    // ## SWIM / AIRBORNE ##
    // ###############################

    /** ⚠ MIRRORED PAIR - unlike the runner and prowler, the spitter DOES have left/right swim attacks. */
    public static final String SWIM_ATTACK_LEFT_ANIMATION_NAME = "swim.attack.left";

    public static final String SWIM_ATTACK_RIGHT_ANIMATION_NAME = "swim.attack.right";

    /** HOLDS on its last frame so one clip stretches to any time in the air. */
    public static final String JUMP_ANIMATION_NAME = "jump";

    public static final String LAND_ANIMATION_NAME = "land";

    // ###############################
    // ## MOLT ##
    // ###############################

    /**
     * ⚠ EMERGE ONLY - the spitter has NO {@code molt.enter}.
     * <p>
     * [stated] "this one has a molt emerge but no enter so it doesnt need to be reversed." So the tracker is built
     * EMERGE-ORIENTED ({@code clipIsEnterOriented = false}), the queen's arrangement: the emerge clip is authored
     * forwards and is never played in reverse. A spitter is only ever a molt DESTINATION.
     * </p>
     */
    public static final String MOLT_EMERGE_ANIMATION_NAME = "molt.emerge";
}
