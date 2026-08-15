package com.alien.common.gameplay.entity.living.alien.xenomorph.predalien;

/**
 * Predalien clip names, converted to the dotted {@code <context>.<action>[.<side>]} convention (20 clips).
 * <p>
 * THREE MIRRORED PAIRS - claw, BACKHAND and crawl attack. ⚠ Unlike the praetorian it has NO {@code crawl.attack.bite}:
 * crawling, it only swipes. And unlike the praetorian its swim attack IS named the ordinary way round.
 * </p>
 * <p>
 * ⭐⭐ ALL THREE MOLT CLIPS, and the predalien is **NOT** terminal: [stated] "the predalien has a enter and loop because
 * it will eventually become a predqueen". So it is a molt SOURCE as well as a destination, and it takes the same
 * three-selector tracker as the praetorian - enter forwards to cocoon, emerge forwards to come out, no reversal.
 * </p>
 */
public class PredalienAnimationRefs {

    // #####################
    // ## GAIT ##
    // #####################

    public static final String IDLE_ANIMATION_NAME = "idle";

    public static final String WALK_ANIMATION_NAME = "walk";

    public static final String RUN_ANIMATION_NAME = "run";

    public static final String SWIM_ANIMATION_NAME = "swim";

    public static final String CRAWL_ANIMATION_NAME = "crawl";

    /** ⚠ THE POUNCE STATE's clip, driven by {@code isLunging} - never borrow it for an airborne pose. */
    public static final String LUNGE_ANIMATION_NAME = "lunge";

    public static final String JUMP_ANIMATION_NAME = "jump";

    public static final String LAND_ANIMATION_NAME = "land";

    // #####################
    // ## ATTACKS ##
    // #####################

    public static final String ATTACK_CLAW_LEFT_ANIMATION_NAME = "attack.claw.left";

    public static final String ATTACK_CLAW_RIGHT_ANIMATION_NAME = "attack.claw.right";

    public static final String ATTACK_BACKHAND_LEFT_ANIMATION_NAME = "attack.backhand.left";

    public static final String ATTACK_BACKHAND_RIGHT_ANIMATION_NAME = "attack.backhand.right";

    public static final String ATTACK_BITE_ANIMATION_NAME = "attack.bite";

    public static final String ATTACK_TAIL_ANIMATION_NAME = "attack.tail";

    public static final String SWIM_ATTACK_BITE_ANIMATION_NAME = "swim.attack.bite";

    // #####################
    // ## CRAWL ##
    // #####################

    public static final String CRAWL_DROP_ANIMATION_NAME = "crawl.drop";

    public static final String CRAWL_IDLE_ANIMATION_NAME = "crawl.idle";

    public static final String CRAWL_RISE_ANIMATION_NAME = "crawl.rise";

    public static final String CRAWL_ATTACK_LEFT_ANIMATION_NAME = "crawl.attack.left";

    public static final String CRAWL_ATTACK_RIGHT_ANIMATION_NAME = "crawl.attack.right";

    public static final String CRAWL_ATTACK_BITE_ANIMATION_NAME = "crawl.attack.bite";

    // #####################
    // ## MOLT ##
    // #####################
    //
    // Enter AND emerge are both authored, so BOTH play forwards - see PraetorianAnimationRefs for why the tracker
    // needed a third selector to stop one of them being replaced by a reversal of the other.

    public static final String MOLT_ENTER_ANIMATION_NAME = "molt.enter";

    public static final String MOLT_LOOP_ANIMATION_NAME = "molt.loop";

    public static final String MOLT_EMERGE_ANIMATION_NAME = "molt.emerge";
}
