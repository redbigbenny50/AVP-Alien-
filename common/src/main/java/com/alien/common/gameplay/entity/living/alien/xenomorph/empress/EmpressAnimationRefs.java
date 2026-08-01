package com.alien.common.gameplay.entity.living.alien.xenomorph.empress;

public class EmpressAnimationRefs {

    public static final String IDLE_ANIMATION_NAME = "idle";

    public static final String WALK_ANIMATION_NAME = "walk";

    public static final String RUN_ANIMATION_NAME = "run";

    public static final String SWIM_ANIMATION_NAME = "swim";

    public static final String RIDE_EGGSACK_ANIMATION_NAME = "rideeggsack";

    // Her crawl set. The locomotion pair replaces the SWIM stand-in the dispatcher shipped with - the clips exist
    // in the animation file (crawl, crawlidle) and the transition pair matches the RENAMED export of Aug 1
    // (crawldrop/crawlrise - the repo file previously called them crawlstand/standcrawl; the export ships with
    // this build and supersedes it). Tick counts are the clip lengths x20, shared by the client animator's track
    // hold and the server's CrawlingManager block window.
    public static final String CRAWL_ANIMATION_NAME = "crawl";

    public static final String CRAWL_IDLE_ANIMATION_NAME = "crawlidle";

    public static final String CRAWL_DROP_ANIMATION_NAME = "crawldrop";

    public static final String CRAWL_RISE_ANIMATION_NAME = "crawlrise";

    public static final int CRAWL_DROP_TICKS = 20;

    public static final int CRAWL_RISE_TICKS = 20;

    public static final String SWIPEDOWN_ANIMATION_NAME = "attack.armstrike";

    public static final String BACKHAND_ANIMATION_NAME = "attack.backhand";

    public static final String TAILSTRIKE_ANIMATION_NAME = "attack.righttail";
}
