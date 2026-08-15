package com.alien.common.gameplay.entity.living.alien.xenomorph.prowler;

import com.alien.common.gameplay.entity.dismemberment.MirroredAttackSide;
import com.alien.common.util.AzAlienAnimationUtil;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.blib.api.client.animation.v1.command.policy.AzDispatchMode;

/**
 * Prowler animation dispatch, single-track.
 * <p>
 * This used to compose every locomotion clip across {@code XENO_LIMBS} - seven tracks, one clip per body part - which
 * is how the whole mod was originally rigged. The prowler has been rebuilt onto whole-body clips like the runner, so
 * everything now plays on {@code BODY} alone. Fewer moving parts, and the per-limb clips no longer have to be kept in
 * sync with each other by hand.
 */
public class ProwlerAnimationDispatcher {

    private static final AzCommand<Prowler> ARM_ATTACK_LEFT = AzCommand.<Prowler>replay()
        .play(AzAlienAnimationUtil.BODY, ProwlerAnimationRefs.ATTACK_CLAW_LEFT_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Prowler> ARM_ATTACK = AzCommand.<Prowler>replay()
        .play(AzAlienAnimationUtil.BODY, ProwlerAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Prowler> BITE_ATTACK = AzCommand.<Prowler>replay()
        .play(AzAlienAnimationUtil.BODY, ProwlerAnimationRefs.ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Prowler> TAIL_ATTACK = AzCommand.<Prowler>replay()
        .play(AzAlienAnimationUtil.BODY, ProwlerAnimationRefs.ATTACK_TAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Prowler> SWIM_ATTACK = AzCommand.<Prowler>replay()
        .play(AzAlienAnimationUtil.BODY, ProwlerAnimationRefs.SWIM_ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Prowler> CRAWL = AzCommand.<Prowler>idempotent()
        .play(AzAlienAnimationUtil.BODY, ProwlerAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Prowler> CRAWL_HOLD = AzCommand.<Prowler>idempotent()
        .play(AzAlienAnimationUtil.BODY, ProwlerAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
        .build();

    private static final AzCommand<Prowler> IDLE = AzCommand.<Prowler>idempotent()
        .play(AzAlienAnimationUtil.BODY, ProwlerAnimationRefs.IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Prowler> LUNGE = AzCommand.<Prowler>replay()
        .play(AzAlienAnimationUtil.BODY, ProwlerAnimationRefs.LUNGE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Prowler> RUN = AzCommand.<Prowler>idempotent()
        .play(AzAlienAnimationUtil.BODY, ProwlerAnimationRefs.RUN_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Prowler> SWIM = AzCommand.<Prowler>idempotent()
        .play(AzAlienAnimationUtil.BODY, ProwlerAnimationRefs.SWIM_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Prowler> WALK = AzCommand.<Prowler>idempotent()
        .play(AzAlienAnimationUtil.BODY, ProwlerAnimationRefs.WALK_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private final Prowler prowler;

    public ProwlerAnimationDispatcher(Prowler prowler) {
        this.prowler = prowler;
    }

    public void crawl() {
        CRAWL.dispatchForEntity(prowler);
    }

    public void crawl(float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            ProwlerAnimationRefs.CRAWL_ANIMATION_NAME,
            AzPlayBehaviors.LOOP,
            AzDispatchMode.PLAY_IF_NOT_PLAYING,
            speed
        ).dispatchForEntity(prowler);
    }

    public void crawlHold() {
        CRAWL_HOLD.dispatchForEntity(prowler);
    }

    public void idle() {
        IDLE.dispatchForEntity(prowler);
    }

    public void lunge() {
        LUNGE.dispatchForEntity(prowler);
    }

    public void run() {
        RUN.dispatchForEntity(prowler);
    }

    public void swim() {
        SWIM.dispatchForEntity(prowler);
    }

    public void walk() {
        WALK.dispatchForEntity(prowler);
    }

    public void biteAttack() {
        BITE_ATTACK.dispatchForEntity(prowler);
    }

    public void biteAttack(float speed) {
        playAttackWithSpeed(ProwlerAnimationRefs.ATTACK_BITE_ANIMATION_NAME, speed);
    }

    public void rightClawAttack() {
        ARM_ATTACK.dispatchForEntity(prowler);
    }

    public void rightClawAttack(float speed) {
        playAttackWithSpeed(ProwlerAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME, speed);
    }

    public void tailAttackQuad() {
        TAIL_ATTACK.dispatchForEntity(prowler);
    }

    public void tailAttackQuad(float speed) {
        playAttackWithSpeed(ProwlerAnimationRefs.ATTACK_TAIL_ANIMATION_NAME, speed);
    }

    public void swimAttack() {
        SWIM_ATTACK.dispatchForEntity(prowler);
    }

    public void swimAttack(float speed) {
        playAttackWithSpeed(ProwlerAnimationRefs.SWIM_ATTACK_BITE_ANIMATION_NAME, speed);
    }

    private void playAttackWithSpeed(String animationName, float speed) {
        AzCommand.<Prowler>replay()
            .play(AzAlienAnimationUtil.BODY, animationName, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(prowler);
    }

    /** Claw swing, side chosen from the dismemberment state. */
    public void clawAttack() {
        if (MirroredAttackSide.useLeftArm(prowler)) {
            ARM_ATTACK_LEFT.dispatchForEntity(prowler);
            return;
        }
        ARM_ATTACK.dispatchForEntity(prowler);
    }

    public void clawAttack(float speed) {
        var clip = MirroredAttackSide.useLeftArm(prowler)
            ? ProwlerAnimationRefs.ATTACK_CLAW_LEFT_ANIMATION_NAME
            : ProwlerAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME;

        AzCommand.<Prowler>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(prowler);
    }

    /** Crawling claw swing - same side rule. */
    public void crawlAttack() {
        var clip = MirroredAttackSide.useLeftArm(prowler)
            ? ProwlerAnimationRefs.CRAWL_ATTACK_LEFT_ANIMATION_NAME
            : ProwlerAnimationRefs.CRAWL_ATTACK_RIGHT_ANIMATION_NAME;

        AzCommand.<Prowler>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(prowler);
    }

    /** Crawling bite - not mirrored. */
    public void crawlBiteAttack() {
        AzCommand.<Prowler>replay()
            .play(AzAlienAnimationUtil.BODY, ProwlerAnimationRefs.CRAWL_ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(prowler);
    }

    /**
     * Leaving the ground. HOLD_ON_LAST_FRAME so one clip covers any airborne duration.
     * <p>
     * ⚠ NOT the lunge. {@code lunge()} is the POUNCE state's animation and is dispatched from {@code isLunging};
     * borrowing it for airborne once made a prowler walking off a ledge play its pounce.
     * </p>
     */
    public void jump() {
        AzCommand.<Prowler>replay()
            .play(AzAlienAnimationUtil.BODY, ProwlerAnimationRefs.JUMP_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
            .build()
            .dispatchForEntity(prowler);
    }

    /** Touching down. */
    public void land() {
        AzCommand.<Prowler>replay()
            .play(AzAlienAnimationUtil.BODY, ProwlerAnimationRefs.LAND_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(prowler);
    }
}
