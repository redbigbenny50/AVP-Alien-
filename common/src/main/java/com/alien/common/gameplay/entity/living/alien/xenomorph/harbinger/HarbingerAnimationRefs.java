package com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger;

/**
 * ⚠ NO JUMP OR LAND CLIPS, AND THAT IS DELIBERATE. [stated] "the empress and harbinger have no jump that is because
 * they can step onto 3 high tall walls so they dont need to jump." Do NOT add the airborne block every other caste has
 * - there is no clip for it and they never leave the ground under their own power.
 */
public class HarbingerAnimationRefs {

    /** ⚠ MIRRORED PAIR - the side is chosen at dispatch by MirroredAttackSide, never baked into a caller. */
    public static final String ATTACK_CLAW_LEFT_ANIMATION_NAME = "attack.clawleft";

    public static final String ATTACK_CLAW_RIGHT_ANIMATION_NAME = "attack.clawright";

    public static final String ATTACK_BITE_ANIMATION_NAME = "crawl.attack.bite";

    /** ⚠ MIRRORED PAIR - a tail has no left/right limb, but the art authors both sweeps and the side alternates. */
    public static final String ATTACK_TAIL_LEFT_ANIMATION_NAME = "attack.tailleft";

    public static final String ATTACK_TAIL_RIGHT_ANIMATION_NAME = "attack.tailright";

    /**
     * Two-armed ground slam. 2.0s clip = 40 ticks; the impact frame is 1.4035s (lower body snaps -12.5 to +67.2 degrees
     * and drops 4 units, arms bottom out) = tick 28, which is where the shockwave fires.
     */
    public static final String ATTACK_GROUND_SLAM_ANIMATION_NAME = "special.attack.groundslam";

    public static final int GROUND_SLAM_DURATION_TICKS = 40;

    /** Backhand. 1.0s = 20 ticks; the shoulder sweep reaches full extension at 0.5s, so contact is tick 10. */
    /** ⚠ MIRRORED PAIR. */
    public static final String ATTACK_BACKHAND_LEFT_ANIMATION_NAME = "attack.backhandleft";

    public static final String ATTACK_BACKHAND_RIGHT_ANIMATION_NAME = "attack.backhandright";

    public static final int BACKHAND_DURATION_TICKS = 20;

    /** Kick. 0.7917s = 16 ticks; knee chambers at 0.33s, leg drives out 0.42-0.58s, contact at tick 10. */
    public static final String ATTACK_KICK_ANIMATION_NAME = "attack.kick";

    public static final int KICK_DURATION_TICKS = 16;

    /** Front kick. 0.7917s = 16 ticks; the leg pistons out and the lower body braces back at 0.2917s = tick 6. */
    public static final String ATTACK_FRONT_KICK_ANIMATION_NAME = "attack.frontkick";

    public static final int FRONT_KICK_DURATION_TICKS = 16;

    public static final String CRAWL_ANIMATION_NAME = "crawl";

    public static final String CRAWL_IDLE_ANIMATION_NAME = "crawl.idle";

    // Crawl attacks. The plain bite already plays "crawl.attack.bite" (the model's only bite clip), so the crawl
    // bite reuses ATTACK_BITE_ANIMATION_NAME; the whipstabs have dedicated ground-authored clips per arm.
    public static final String ATTACKCRAWL_LEFT_WHIPSTAB_ANIMATION_NAME = "attackcrawl.leftwhipstab";

    public static final String ATTACKCRAWL_RIGHT_WHIPSTAB_ANIMATION_NAME = "attackcrawl.rightwhipstab";

    // Crawl posture transitions - tick counts are the authored clip lengths x20, shared by the client animator's
    // track hold and the server's CrawlingManager block window.
    public static final String CRAWL_DOWN_ANIMATION_NAME = "crawl.drop";

    public static final String CRAWL_UP_ANIMATION_NAME = "crawl.rise";

    public static final int CRAWL_DOWN_TICKS = 13;

    public static final int CRAWL_UP_TICKS = 20;

    public static final int CRAWL_BITE_DURATION_TICKS = 15;

    public static final int CRAWL_WHIPSTAB_DURATION_TICKS = 20;

    public static final String IDLE_ANIMATION_NAME = "idle";

    public static final String RUN_ANIMATION_NAME = "run";

    public static final String SWIM_ANIMATION_NAME = "swim";

    public static final String WALK_ANIMATION_NAME = "walk";

    // ---- Back-whip layer -------------------------------------------------------------------------------
    // The whip bones (gLeftWhip.. / gRightWhip..) appear in NO body clip, so the whips run as their own two
    // tracks in parallel with BODY rather than as states of the body machine. The idle/movement clips are
    // procedural: one Molang keyframe each and no baked motion, so they only sway while their track is
    // actually playing them. The attack pair is authored keyframes (3s, looping) and covers BOTH crawling and
    // any attack, so a swing never reads as a lazy idle sway.

    public static final String IDLE_LEFT_WHIP_ANIMATION_NAME = "idle.leftwhip";

    public static final String IDLE_RIGHT_WHIP_ANIMATION_NAME = "idle.rightwhip";

    public static final String MOVEMENT_LEFT_WHIP_ANIMATION_NAME = "movement.leftwhip";

    public static final String MOVEMENT_RIGHT_WHIP_ANIMATION_NAME = "movement.rightwhip";

    public static final String ATTACK_LEFT_WHIP_IDLE_ANIMATION_NAME = "attack.leftwhipidle";

    public static final String ATTACK_RIGHT_WHIP_IDLE_ANIMATION_NAME = "attack.rightwhipidle";

    /**
     * ⭐ THE STANDING WHIP STABS. Until now only the CRAWLING pair was authored, so a standing harbinger had no stab of
     * its own - see ATTACKCRAWL_* above for the prone versions.
     */
    public static final String ATTACK_LEFT_WHIPSTAB_ANIMATION_NAME = "attack.leftwhipstab";

    public static final String ATTACK_RIGHT_WHIPSTAB_ANIMATION_NAME = "attack.rightwhipstab";

    /** ⚠ MIRRORED PAIR - the prone claw swipes. */
    public static final String CRAWL_ATTACK_LEFT_ANIMATION_NAME = "crawl.attack.left";

    public static final String CRAWL_ATTACK_RIGHT_ANIMATION_NAME = "crawl.attack.right";

    /**
     * ⭐ THE WHIP LAYER'S HURT POSE. Plays on the whip tracks when a whip has been severed, so a harbinger missing a
     * whip stops waving one that is not there.
     */
    public static final String DAMAGED_WHIPS_ANIMATION_NAME = "damaged.whips";

    /**
     * ⚠⚠ THE LOOP IS `molt.loop`, NOT `molting`. HarbingerAnimator was on the NO-ARG tracker, which hardcodes "molting"
     * - a clip this art has never contained - so the loop half of every praetorian→harbinger molt was bind-posing
     * SILENTLY. He was the LAST caste still on the default.
     */
    /** ⚠ Its fallback in the water: the bite needs only a HEAD, so it survives losing both arms. */
    public static final String SWIM_ATTACK_BITE_ANIMATION_NAME = "swim.attack.bite";

    /**
     * ⚠⚠ BOTH ARMS AT ONCE, so it follows the ravager/empress double-claw rule: [stated] "swim attack claws uses the
     * usually two limb if one is lost 50% damage if both lost only bite works." NOT a mirrored pair - one authored clip
     * using both arms.
     */
    public static final String SWIM_ATTACK_CLAWS_ANIMATION_NAME = "swim.attack.claws";

    public static final String MOLT_LOOP_ANIMATION_NAME = "molt.loop";

    /** Emerge-oriented: the harbinger is a molt DESTINATION only, so there is no enter clip and none is needed. */
    public static final String MOLT_EMERGE_ANIMATION_NAME = "molt.emerge";

}
