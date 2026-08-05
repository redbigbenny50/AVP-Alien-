package com.alien.common.gameplay.entity.living.alien.xenomorph.ravager;

public class RavagerAnimationRefs {

    public static final String IDLE_ANIMATION_NAME = "idle";

    public static final String WALK_ANIMATION_NAME = "walk";

    public static final String RUN_ANIMATION_NAME = "run";

    public static final String SWIM_ANIMATION_NAME = "swim";

    public static final String CRAWL_ANIMATION_NAME = "crawl";

    public static final String CRAWL_IDLE_ANIMATION_NAME = "crawlidle";

    public static final String CRAWL_UP_ANIMATION_NAME = "crawlup";

    public static final String CRAWL_DOWN_ANIMATION_NAME = "crawldown";

    // Crawl transition clip lengths x20, shared by the client animator's track hold and the server's
    // CrawlingManager block window.
    public static final int CRAWL_DOWN_TICKS = 10;

    public static final int CRAWL_UP_TICKS = 10;

    public static final String CRAWL_ATTACK_ANIMATION_NAME = "crawlattack";

    public static final String ATTACK_ARM_SINGLE_ANIMATION_NAME = "attackarmsingle";

    public static final String ATTACK_ARM_DOUBLE_ANIMATION_NAME = "attackarmdouble";

    public static final String ATTACK_BITE_ANIMATION_NAME = "attackbite";

    public static final String ATTACK_TAIL_ANIMATION_NAME = "attacktail";

    public static final String SWIM_ATTACK_ANIMATION_NAME = "swimattack";

    public static final String SPECIAL_ATTACK_WARMUP_ANIMATION_NAME = "attackspecial.warmup";

    public static final String SPECIAL_ATTACK_ACTIVATE_ANIMATION_NAME = "attackspecial.activate";
}
