package com.alien.common.gameplay.entity.living.alien.xenomorph.empress;

public class EmpressAnimationRefs {

    public static final String IDLE_ANIMATION_NAME = "idle";

    public static final String WALK_ANIMATION_NAME = "walk";

    public static final String RUN_ANIMATION_NAME = "run";

    public static final String SWIM_ANIMATION_NAME = "swim";

    /**
     * ⭐⭐ THREE CLIPS FOR THE SACK, WHERE THE QUEEN HAS ONE. [stated] "she has a sit that preludes her being on it or
     * when the sack is first summoned. then the loop which plays while she is sitting on it then if she gets off of it
     * due to attack it plays the getoff animation."
     */
    public static final String RIDE_EGGSACK_SIT_ANIMATION_NAME = "ride.eggsack.sit";

    public static final String RIDE_EGGSACK_ANIMATION_NAME = "ride.eggsack.free";

    public static final String RIDE_EGGSACK_GETOFF_ANIMATION_NAME = "ride.eggsack.getoff";

    // Her crawl set. The locomotion pair replaces the SWIM stand-in the dispatcher shipped with - the clips exist
    // in the animation file (crawl, crawlidle) and the transition pair matches the RENAMED export of Aug 1
    // (crawldrop/crawlrise - the repo file previously called them crawlstand/standcrawl; the export ships with
    // this build and supersedes it). Tick counts are the clip lengths x20, shared by the client animator's track
    // hold and the server's CrawlingManager block window.
    public static final String CRAWL_ANIMATION_NAME = "crawl";

    public static final String CRAWL_IDLE_ANIMATION_NAME = "crawl.idle";

    public static final String CRAWL_DROP_ANIMATION_NAME = "crawl.drop";

    public static final String CRAWL_RISE_ANIMATION_NAME = "crawl.rise";

    public static final int CRAWL_DROP_TICKS = 20;

    public static final int CRAWL_RISE_TICKS = 20;

    /** ⚠ MIRRORED PAIR - the side is chosen at dispatch by MirroredAttackSide, never baked into a caller. */
    public static final String SWIPEDOWN_LEFT_ANIMATION_NAME = "attack.clawsleft";

    public static final String SWIPEDOWN_RIGHT_ANIMATION_NAME = "attack.clawsright";

    /** ⚠ MIRRORED PAIR. */
    public static final String BACKHAND_LEFT_ANIMATION_NAME = "attack.backhandleft";

    public static final String BACKHAND_RIGHT_ANIMATION_NAME = "attack.backhandright";

    /** ⚠ MIRRORED PAIR - a tail has no left/right limb, but the art authors both sweeps and the side alternates. */
    public static final String TAILSTRIKE_LEFT_ANIMATION_NAME = "attack.tailleft";

    public static final String TAILSTRIKE_RIGHT_ANIMATION_NAME = "attack.tailright";

    public static final String ATTACK_BITE_ANIMATION_NAME = "attack.bite";

    public static final String ATTACK_HEADRAM_ANIMATION_NAME = "attack.headram";

    public static final String SPECIAL_ATTACK_SCREAM_ANIMATION_NAME = "special.attack.scream";

    /** ⚠ MIRRORED PAIR. */
    public static final String CRAWL_ATTACK_LEFT_ANIMATION_NAME = "crawl.attack.left";

    public static final String CRAWL_ATTACK_RIGHT_ANIMATION_NAME = "crawl.attack.right";

    public static final String CRAWL_ATTACK_BITE_ANIMATION_NAME = "crawl.attack.bite";

    public static final String SWIM_ATTACK_BITE_ANIMATION_NAME = "swim.attack.bite";

    /**
     * ⚠⚠ BOTH ARMS AT ONCE, so it follows the ravager double-claw rule: [stated] "both arms use the same 50% damage
     * rule if missing 1 arm or doesnt play at all if both are gone." NOT a mirrored pair - one authored clip.
     */
    public static final String SWIM_ATTACK_CLAWS_ANIMATION_NAME = "swim.attack.claws";

    /** An idle variant that flicks the tail - purely cosmetic, picked at random between idles. */
    public static final String IDLE_TAIL_ANIMATION_NAME = "idle.tail";

    /**
     * ⚠⚠ THE LOOP IS `molt.loop`, NOT `molting`. EmpressAnimator was on the NO-ARG tracker, which hardcodes "molting" -
     * a clip this art has never contained - so the loop half of every queen→empress molt was bind-posing SILENTLY. She
     * was the LAST caste still exposed to this.
     */
    public static final String MOLT_LOOP_ANIMATION_NAME = "molt.loop";

    /** Emerge-oriented: the empress is a molt DESTINATION only, so there is no enter clip and none is needed. */
    public static final String MOLT_EMERGE_ANIMATION_NAME = "molt.emerge";
}
