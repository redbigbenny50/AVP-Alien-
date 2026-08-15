package com.alien.common.gameplay.entity.living.alien.xenomorph.praetorian;

/**
 * Praetorian clip names, converted to the dotted {@code <context>.<action>[.<side>]} convention (23 clips).
 * <p>
 * THREE MIRRORED PAIRS: the claw, the BACKHAND and the crawl attack. The backhand is new to this convention - only the
 * harbinger had one before - and it mirrors for the same reason the claw does: the arm it has lost is the arm it has
 * lost. The swim attack and the crawl bite are single clips.
 * </p>
 * <p>
 * The swim attack was briefly authored `attack.swim.bite`; he renamed it to `swim.attack.bite` so it matches every
 * other caste. Nothing in the mod reverses context and action any more.
 * </p>
 * <p>
 * ⚠ NO LUNGE. At 3.98 blocks the praetorian does not pounce, and {@code PraetorianAnimator} has never dispatched one -
 * so the absent clip is correct, not a gap.
 * </p>
 */
public class PraetorianAnimationRefs {

    // #####################
    // ## GAIT ##
    // #####################

    public static final String IDLE_ANIMATION_NAME = "idle";

    public static final String WALK_ANIMATION_NAME = "walk";

    public static final String RUN_ANIMATION_NAME = "run";

    public static final String SWIM_ANIMATION_NAME = "swim";

    public static final String CRAWL_ANIMATION_NAME = "crawl";

    // #####################
    // ## AIRBORNE ##
    // #####################
    //
    // NEW ART. HOLDS on its last frame so one clip stretches to any time in the air; land fires on the tick the
    // ground is regained, and only if a jump actually played.

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
    //
    // NEW ART. The praetorian is a TALL caste (3.98), so unlike the runner and prowler it genuinely has to get down
    // and back up - hence the drop/rise pair the low-slung castes deliberately lack.

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
    // ⚠⚠ THE PRAETORIAN IS THE FIRST CASTE TO SHIP ALL THREE: enter, loop AND a separately authored emerge. Every
    // other caste ships enter+loop (emerge = the enter reversed) or emerge-only (the queen and spitter). That is why
    // CocoonAnimationStateTracker gained a three-selector constructor - with only the old two, one of these two
    // authored clips would have been ignored and replaced by a reversal of the other.

    public static final String MOLT_ENTER_ANIMATION_NAME = "molt.enter";

    public static final String MOLT_LOOP_ANIMATION_NAME = "molt.loop";

    public static final String MOLT_EMERGE_ANIMATION_NAME = "molt.emerge";
}
