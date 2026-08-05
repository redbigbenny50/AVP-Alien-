package com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger;

public class HarbingerAnimationRefs {

    public static final String ATTACK_CLAW_ANIMATION_NAME = "attack.claw";

    public static final String ATTACK_BITE_ANIMATION_NAME = "attack.crawlbite";

    public static final String ATTACK_TAIL_ANIMATION_NAME = "attack.tail";

    /**
     * Two-armed ground slam. 2.0s clip = 40 ticks; the impact frame is 1.4035s (lower body snaps -12.5 to +67.2 degrees
     * and drops 4 units, arms bottom out) = tick 28, which is where the shockwave fires.
     */
    public static final String ATTACK_GROUND_SLAM_ANIMATION_NAME = "attack.groundslam";

    public static final int GROUND_SLAM_DURATION_TICKS = 40;

    /** Backhand. 1.0s = 20 ticks; the shoulder sweep reaches full extension at 0.5s, so contact is tick 10. */
    public static final String ATTACK_BACKHAND_ANIMATION_NAME = "attack.backhand";

    public static final int BACKHAND_DURATION_TICKS = 20;

    /** Kick. 0.7917s = 16 ticks; knee chambers at 0.33s, leg drives out 0.42-0.58s, contact at tick 10. */
    public static final String ATTACK_KICK_ANIMATION_NAME = "attack.kick";

    public static final int KICK_DURATION_TICKS = 16;

    /** Front kick. 0.7917s = 16 ticks; the leg pistons out and the lower body braces back at 0.2917s = tick 6. */
    public static final String ATTACK_FRONT_KICK_ANIMATION_NAME = "attack.frontkick";

    public static final int FRONT_KICK_DURATION_TICKS = 16;

    public static final String CRAWL_ANIMATION_NAME = "crawl";

    public static final String CRAWL_IDLE_ANIMATION_NAME = "crawlidle";

    // Crawl attacks. The plain bite already plays "attack.crawlbite" (the model's only bite clip), so the crawl
    // bite reuses ATTACK_BITE_ANIMATION_NAME; the whipstabs have dedicated ground-authored clips per arm.
    public static final String ATTACKCRAWL_LEFT_WHIPSTAB_ANIMATION_NAME = "attackcrawl.leftwhipstab";

    public static final String ATTACKCRAWL_RIGHT_WHIPSTAB_ANIMATION_NAME = "attackcrawl.rightwhipstab";

    // Crawl posture transitions - tick counts are the authored clip lengths x20, shared by the client animator's
    // track hold and the server's CrawlingManager block window.
    public static final String CRAWL_DOWN_ANIMATION_NAME = "crawldown";

    public static final String CRAWL_UP_ANIMATION_NAME = "crawlup";

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
}
