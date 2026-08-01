package com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger;

public class HarbingerAnimationRefs {

    public static final String ATTACK_CLAW_ANIMATION_NAME = "attack.claw";

    public static final String ATTACK_BITE_ANIMATION_NAME = "attack.crawlbite";

    public static final String ATTACK_TAIL_ANIMATION_NAME = "attack.tail";

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
}
