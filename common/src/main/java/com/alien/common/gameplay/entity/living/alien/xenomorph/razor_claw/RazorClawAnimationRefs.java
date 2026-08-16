package com.alien.common.gameplay.entity.living.alien.xenomorph.razor_claw;

public class RazorClawAnimationRefs {

    /** ⚠ MIRRORED PAIR - the side is chosen at dispatch by MirroredAttackSide, never baked into a caller. */
    public static final String ATTACK_CLAW_LEFT_ANIMATION_NAME = "attack.claw.left";

    public static final String ATTACK_CLAW_RIGHT_ANIMATION_NAME = "attack.claw.right";

    public static final String ATTACK_BITE_ANIMATION_NAME = "attack.bite";

    public static final String ATTACK_TAIL_ANIMATION_NAME = "attack.tail";

    /** ⚠ ONE clip, not a mirrored pair - it is "claws" plural, both at once. */
    public static final String SWIM_ATTACK_ANIMATION_NAME = "swim.attack.claws";

    /**
     * ⚠⚠ THE AOE SPIN IS NOW A MIRRORED PAIR. It was a single `aoeattackspin`; the art has left and right, so the spin
     * picks a side like the claw does. It is a TRIGGERED special, not a regular, but the limb rule is the same.
     */
    public static final String SPECIAL_ATTACK_SPIN_LEFT_ANIMATION_NAME = "special.attack.aoespinleft";

    public static final String SPECIAL_ATTACK_SPIN_RIGHT_ANIMATION_NAME = "special.attack.aoespinright";

    public static final String CRAWL_ANIMATION_NAME = "crawl";

    public static final String CRAWL_IDLE_ANIMATION_NAME = "crawl.idle";

    /** ⚠ BLOCKING transition clips - it used to SNAP between postures with nothing to play. */
    public static final String CRAWL_DROP_ANIMATION_NAME = "crawl.drop";

    public static final String CRAWL_RISE_ANIMATION_NAME = "crawl.rise";

    /** ⚠ MIRRORED PAIR. */
    public static final String CRAWL_ATTACK_LEFT_ANIMATION_NAME = "crawl.attack.left";

    public static final String CRAWL_ATTACK_RIGHT_ANIMATION_NAME = "crawl.attack.right";

    public static final String CRAWL_ATTACK_BITE_ANIMATION_NAME = "crawl.attack.bite";

    /**
     * ⭐ THE FLURRY. [stated] "its a quick attack with both arms... the animation plays 4 times is succession think of
     * it like a flurry of blows back to back." ONE clip, replayed by {@code FlurryAttackExecutor}.
     */
    public static final String ATTACK_QUICK_ANIMATION_NAME = "attack.quick";

    /** ⭐ The evasive roll-off that opens the speed buff. */
    public static final String DODGE_ANIMATION_NAME = "dodge";

    /** ⚠ MIRRORED PAIR - the armour-piercing charge leads with one arm, so it follows the claw's limb rule. */
    public static final String SPECIAL_ATTACK_CHARGE_LEFT_ANIMATION_NAME = "special.attack.chargeleft";

    public static final String SPECIAL_ATTACK_CHARGE_RIGHT_ANIMATION_NAME = "special.attack.chargeright";

    public static final String JUMP_ANIMATION_NAME = "jump";

    public static final String LAND_ANIMATION_NAME = "land";

    /**
     * ⚠⚠ THE LOOP IS `molt.loop`, NOT `molting`. RazorClawAnimator passed the LITERAL "molting" - a clip this art has
     * never contained - so the loop half of every drone→razor_claw molt was bind-posing SILENTLY.
     */
    public static final String MOLT_LOOP_ANIMATION_NAME = "molt.loop";

    /** Emerge-oriented: the razor claw is a molt DESTINATION only, so there is no enter clip and none is needed. */
    public static final String MOLT_EMERGE_ANIMATION_NAME = "molt.emerge";
}
