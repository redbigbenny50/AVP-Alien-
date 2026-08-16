package com.alien.common.gameplay.entity.living.alien.xenomorph.queen;

public class QueenAnimationRefs {

    public static final String CRAWL_ANIMATION_NAME = "crawl";

    public static final String CRAWL_IDLE_ANIMATION_NAME = "crawl.idle";

    public static final String HIBERNATE_ANIMATION_NAME = "hibernate";

    public static final String IDLE_ANIMATION_NAME = "idle";

    public static final String INCAPACITATED_ANIMATION_NAME = "incapacitated.loop";

    public static final String INCAPACITATED_DROP_ANIMATION_NAME = "incapacitated.drop";

    public static final String INCAPACITATED_RISE_ANIMATION_NAME = "incapacitated.rise";

    public static final String BOUND_STRUGGLE_ANIMATION_NAME = "bound.struggle";

    /** ⚠ MIRRORED PAIR - the side is chosen at dispatch by MirroredAttackSide, never baked into a caller. */
    public static final String CRAWL_ATTACK_LEFT_ANIMATION_NAME = "crawl.attack.left";

    public static final String CRAWL_ATTACK_RIGHT_ANIMATION_NAME = "crawl.attack.right";

    public static final String CRAWL_ATTACK_BITE_ANIMATION_NAME = "crawl.attack.bite";

    // Crawl posture transitions (edge-driven one-shots, mirrored by the dig triptychs below). Tick counts are the
    // authored clip lengths x20 and are SHARED between the client animator's track hold and the server's
    // CrawlingManager block window - one constant, two consumers, so the sides can never disagree.
    public static final String CRAWL_DROP_ANIMATION_NAME = "crawl.drop";

    public static final String CRAWL_RISE_ANIMATION_NAME = "crawl.rise";

    public static final int CRAWL_DROP_TICKS = 15;

    public static final int CRAWL_RISE_TICKS = 30;

    public static final int CRAWL_ATTACK_DURATION_TICKS = 10;

    // Vertical dig (descending to her location anchor Y): one-shot down, looping dig, one-shot up.
    public static final String DIG_DOWN_ANIMATION_NAME = "digging.drop";

    public static final String DIGGING_ANIMATION_NAME = "digging";

    public static final String DIG_UP_ANIMATION_NAME = "digging.rise";

    // Standing/horizontal dig (also room carving): one-shot raise, looping dig, one-shot lower.
    public static final String DIG_STAND_START_ANIMATION_NAME = "digging.standing.start";

    public static final String STAND_DIGGING_ANIMATION_NAME = "digging.standing";

    public static final String DIG_STAND_STOP_ANIMATION_NAME = "digging.standing.stop";

    public static final String LEFT_BACKHAND_ANIMATION_NAME = "attack.backhandleft";

    public static final String LEFT_SWIPE_DOWN_ANIMATION_NAME = "attack.claw.downwardleft";

    public static final String LEFT_TAIL_STRIKE_ANIMATION_NAME = "attack.tailleft";

    public static final String EMERGE_CRUSHER_ANIMATION_NAME = "molt.emerge.crusher";

    public static final String EMERGE_PRAE_ANIMATION_NAME = "molt.emerge.prae";

    public static final String MOLTING_CRUSHER_ANIMATION_NAME = "molt.loop.crusher";

    public static final String MOLTING_PRAE_ANIMATION_NAME = "molt.loop.prae";

    public static final String RIDE_EGG_SACK_ANIMATION_NAME = "ride.eggsack.free";

    /**
     * ⭐⭐ THE CHAINED SACK. [stated] "theres a new riding eggsack animation for when she is inhibited and chained on the
     * restrained eggsack." Same seat, a bound posture - so the two share every caller and differ only here.
     */
    public static final String RIDE_EGG_SACK_RESTRAINED_ANIMATION_NAME = "ride.eggsack.restrained";

    public static final String ATTACK_BITE_ANIMATION_NAME = "attack.bite";

    public static final String SWIM_ATTACK_BITE_ANIMATION_NAME = "swim.attack.bite";

    /** ⭐ A forward head ram - her only non-limb strike. */
    public static final String ATTACK_HEADRAM_ANIMATION_NAME = "attack.headram";

    public static final String SPECIAL_ATTACK_SCREAM_ANIMATION_NAME = "special.attack.scream";

    /**
     * ⭐⭐ THE EMPRESS MOLT, AND IT IS PLAYED IN REVERSE. [stated] "the molt.emerge.empress needs to be played in reverse
     * like the other emerges that were entering molts to new forms."
     * <p>
     * ⚠ THE NAME IS NOW CORRECT ON BOTH SIDES - he re-exported it from `molt.enter.empress` to `molt.emerge.empress`,
     * which is what the mod's convention wants: a clip that gets REVERSED to produce the cocooning is an EMERGE clip,
     * and calling it an "enter" invited exactly the mistake of handing it to the tracker as one (which switches off all
     * reversal).
     * </p>
     */
    public static final String MOLT_EMERGE_EMPRESS_ANIMATION_NAME = "molt.emerge.empress";

    public static final String MOLT_LOOP_EMPRESS_ANIMATION_NAME = "molt.loop.empress";

    public static final String RIGHT_BACKHAND_ANIMATION_NAME = "attack.backhandright";

    public static final String RIGHT_SWIPE_DOWN_ANIMATION_NAME = "attack.claw.downwardright";

    public static final String RIGHT_TAIL_STRIKE_ANIMATION_NAME = "attack.tailright";

    public static final String RUN_ANIMATION_NAME = "run";

    public static final String SWIM_ANIMATION_NAME = "swim";

    public static final String WALK_ANIMATION_NAME = "walk";
}
