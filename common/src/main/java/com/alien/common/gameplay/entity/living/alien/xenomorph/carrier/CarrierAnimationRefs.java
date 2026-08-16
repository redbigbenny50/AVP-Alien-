package com.alien.common.gameplay.entity.living.alien.xenomorph.carrier;

public class CarrierAnimationRefs {

    public static final String IDLE_ANIMATION_NAME = "idle";

    public static final String WALK_ANIMATION_NAME = "walk";

    public static final String RUN_ANIMATION_NAME = "run";

    public static final String SWIM_ANIMATION_NAME = "swim";

    public static final String ATTACKBITE_ANIMATION_NAME = "attack.bite";

    /** ⚠ MIRRORED PAIR - the side is chosen at dispatch by MirroredAttackSide, never baked into a caller. */
    public static final String ATTACK_CLAW_LEFT_ANIMATION_NAME = "attack.claw.left";

    public static final String ATTACK_CLAW_RIGHT_ANIMATION_NAME = "attack.claw.right";

    public static final String ATTACKTAIL_ANIMATION_NAME = "attack.tail";

    /**
     * ⚠⚠ THE THROW IS MIRRORED TOO, which no other caste's special attack is. It throws a facehugger with an ARM, so it
     * obeys the same limb rule as the claw - lose the left arm and it must throw right.
     */
    public static final String SPECIAL_ATTACK_THROW_LEFT_ANIMATION_NAME = "special.attack.throwleft";

    public static final String SPECIAL_ATTACK_THROW_RIGHT_ANIMATION_NAME = "special.attack.throwright";

    public static final String SPECIAL_ATTACK_SCREAM_ANIMATION_NAME = "special.attack.scream";

    public static final String SPECIAL_ATTACK_HOLD_START_ANIMATION_NAME = "special.attack.hold.start";

    public static final String HOLD_WAITING_ANIMATION_NAME = "hold.waiting";

    /** ⚠ Its own clip at last - CRAWL and CRAWL_HOLD were both playing the SWIM animation. */
    public static final String CRAWL_ANIMATION_NAME = "crawl";

    public static final String CRAWL_IDLE_ANIMATION_NAME = "crawl.idle";

    /** ⚠ BLOCKING transition clips - the carrier used to SNAP between postures. */
    public static final String CRAWL_DROP_ANIMATION_NAME = "crawl.drop";

    public static final String CRAWL_RISE_ANIMATION_NAME = "crawl.rise";

    /** ⚠ MIRRORED PAIR. */
    public static final String CRAWL_ATTACK_LEFT_ANIMATION_NAME = "crawl.attack.left";

    public static final String CRAWL_ATTACK_RIGHT_ANIMATION_NAME = "crawl.attack.right";

    public static final String CRAWL_ATTACK_BITE_ANIMATION_NAME = "crawl.attack.bite";

    /**
     * ⚠ MIRRORED IN THE WATER, unlike the runner and prowler which are bite-only there. Never assume a caste has the
     * same swim set as its neighbours.
     */
    public static final String SWIM_ATTACK_LEFT_ANIMATION_NAME = "swim.attack.left";

    public static final String SWIM_ATTACK_RIGHT_ANIMATION_NAME = "swim.attack.right";

    /**
     * ⭐ THE DEATH COLLAPSE, one clip PER POSTURE. `Carrier.die` calls `releaseAllFacehuggers`, and this is the body
     * dropping as they scatter. [stated] "changed name for trigger to include standing so it plays if its not in a
     * crawling stance and added another that plays when its crawling and it dies."
     * <p>
     * ⚠ POSTURE IS READ ONCE, AT THE MOMENT OF DEATH, and latched. Reading it live would let a body that settles out of
     * its crawl mid-death swap clips halfway through collapsing.
     * </p>
     * <p>
     * ⚠ Held on the last frame, not looped and not played once: the carrier keeps rendering for the whole 20-tick
     * vanilla death window, and a PLAY_ONCE clip would finish early and let the gait or idle take the body back over
     * mid-death - it would stand up again while dying.
     * </p>
     */
    public static final String COLLAPSE_TRIGGER_STANDING_ANIMATION_NAME = "collapse.trigger.standing";

    public static final String COLLAPSE_TRIGGER_CRAWLING_ANIMATION_NAME = "collapse.trigger.crawling";

    public static final String JUMP_ANIMATION_NAME = "jump";

    public static final String LAND_ANIMATION_NAME = "land";

    /**
     * ⚠⚠ THE LOOP IS `molt.loop`, NOT `molting`. The animator used to pass the literal "molting", which this art has
     * never contained - so the loop half of every carrier molt was bind-posing silently.
     */
    public static final String MOLT_LOOP_ANIMATION_NAME = "molt.loop";

    /** Emerge-oriented: the carrier is a molt DESTINATION only, so there is no enter clip and none is needed. */
    public static final String MOLT_EMERGE_ANIMATION_NAME = "molt.emerge";
}
