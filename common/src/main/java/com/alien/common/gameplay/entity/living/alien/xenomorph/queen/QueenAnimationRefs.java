package com.alien.common.gameplay.entity.living.alien.xenomorph.queen;

public class QueenAnimationRefs {

    public static final String CRAWL_ANIMATION_NAME = "crawl";

    public static final String CRAWL_IDLE_ANIMATION_NAME = "crawl.idle";

    public static final String HIBERNATE_ANIMATION_NAME = "hibernate";

    public static final String IDLE_ANIMATION_NAME = "idle";

    public static final String INCAPACITATED_ANIMATION_NAME = "incapacitated";

    public static final String INCAPACITATED_DROP_ANIMATION_NAME = "incapacitated_drop";

    public static final String INCAPACITATED_RISE_ANIMATION_NAME = "incapacitated_rise";

    public static final String BOUND_STRUGGLE_ANIMATION_NAME = "bound_struggle";

    public static final String CRAWL_ATTACK_ANIMATION_NAME = "crawl_attack";

    // Crawl posture transitions (edge-driven one-shots, mirrored by the dig triptychs below). Tick counts are the
    // authored clip lengths x20 and are SHARED between the client animator's track hold and the server's
    // CrawlingManager block window - one constant, two consumers, so the sides can never disagree.
    public static final String CRAWL_DROP_ANIMATION_NAME = "crawl.drop";

    public static final String CRAWL_RISE_ANIMATION_NAME = "crawl.rise";

    public static final int CRAWL_DROP_TICKS = 15;

    public static final int CRAWL_RISE_TICKS = 30;

    public static final int CRAWL_ATTACK_DURATION_TICKS = 10;

    // Vertical dig (descending to her location anchor Y): one-shot down, looping dig, one-shot up.
    public static final String DIG_DOWN_ANIMATION_NAME = "digdown";

    public static final String DIGGING_ANIMATION_NAME = "digging";

    public static final String DIG_UP_ANIMATION_NAME = "digup";

    // Standing/horizontal dig (also room carving): one-shot raise, looping dig, one-shot lower.
    public static final String DIG_STAND_START_ANIMATION_NAME = "digstand_start";

    public static final String STAND_DIGGING_ANIMATION_NAME = "stand_digging";

    public static final String DIG_STAND_STOP_ANIMATION_NAME = "digstand_stop";

    public static final String LEFT_BACKHAND_ANIMATION_NAME = "fullbodyattack.leftbackhand";

    public static final String LEFT_SWIPE_DOWN_ANIMATION_NAME = "fullbodyattack.leftarmdownward";

    public static final String LEFT_TAIL_STRIKE_ANIMATION_NAME = "fullbodyattack.lefttail";

    public static final String EMERGE_CRUSHER_ANIMATION_NAME = "emerge.crusher";

    public static final String EMERGE_PRAE_ANIMATION_NAME = "emerge.prae";

    public static final String MOLTING_CRUSHER_ANIMATION_NAME = "molting.crusher";

    public static final String MOLTING_PRAE_ANIMATION_NAME = "molting.prae";

    public static final String RIDE_EGG_SACK_ANIMATION_NAME = "rideeggsack";

    public static final String RIGHT_BACKHAND_ANIMATION_NAME = "fullbodyattack.rightbackhand";

    public static final String RIGHT_SWIPE_DOWN_ANIMATION_NAME = "fullbodyattack.rightarmdownward";

    public static final String RIGHT_TAIL_STRIKE_ANIMATION_NAME = "fullbodyattack.righttail";

    public static final String RUN_ANIMATION_NAME = "run";

    public static final String SWIM_ANIMATION_NAME = "swim";

    public static final String WALK_ANIMATION_NAME = "walk";
}
