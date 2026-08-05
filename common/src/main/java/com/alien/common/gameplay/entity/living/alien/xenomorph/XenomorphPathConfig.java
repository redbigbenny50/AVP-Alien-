package com.alien.common.gameplay.entity.living.alien.xenomorph;

public record XenomorphPathConfig(
    int entityWidth,
    int entityHeight,
    boolean canOpenDoors,
    int crawlHeight
) {

    public XenomorphPathConfig(int entityWidth, int entityHeight, boolean canOpenDoors) {
        this(entityWidth, entityHeight, canOpenDoors, 1);
    }

    public static final XenomorphPathConfig SMALL_DOOR = new XenomorphPathConfig(1, 1, true);

    public static final XenomorphPathConfig MEDIUM_DOOR = new XenomorphPathConfig(1, 2, true);

    public static final XenomorphPathConfig MEDIUM_TALL = new XenomorphPathConfig(1, 3, true);

    public static final XenomorphPathConfig LARGE = new XenomorphPathConfig(1, 4, false);

    public static final XenomorphPathConfig LARGE_DOOR = new XenomorphPathConfig(1, 4, true);

    public static final XenomorphPathConfig WIDE = new XenomorphPathConfig(2, 2, false);

    /**
     * Queen and empress, and ONLY those two - so this can be sized to their box without touching anything else. Raised
     * from (2, 4) to match the 2.6 x 5.5 hitbox they now share: at 4 the navigator believed they fitted under a 4-high
     * ceiling and drove their heads through it.
     * <p>
     * crawlHeight 3 is what keeps that honest ([stated] "they can always duck through 4 high spaces so they can still
     * technically path if needed"): crawling scales height by {@code UNDERWATER_HEIGHT_SCALE} (0.4), so a crawling
     * royal is 2.2 tall and genuinely needs three blocks. Standing she now demands six; ducking she still gets through
     * a 3- or 4-high gap, which is the route that keeps her own hive passable.
     */
    public static final XenomorphPathConfig WIDE_TALL = new XenomorphPathConfig(3, 6, false, 3);

    /**
     * The harbinger. Her hitbox is 2.6 x 2.6 x 5.5, so she needs a 3-wide, 6-high column to occupy - LARGE (1 x 4)
     * would have routed her down corridors her body cannot fit in.
     * <p>
     * crawlHeight is 3 rather than the usual 1: crawling scales height by {@code UNDERWATER_HEIGHT_SCALE} (0.4), so a
     * crawling harbinger is still 2.2 tall and needs three blocks. That is the same derivation the water config does
     * for itself one line below where this is read - {@code ceil(entityHeight * 0.4)}. She still gets under a 3-high
     * doorway, which is what the crawl is for; she just stops claiming she fits under a 1-high gap.
     */
    public static final XenomorphPathConfig HUGE = new XenomorphPathConfig(3, 6, false, 3);
}
