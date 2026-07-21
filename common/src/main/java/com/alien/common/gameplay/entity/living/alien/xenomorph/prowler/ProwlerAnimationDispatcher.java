package com.alien.common.gameplay.entity.living.alien.xenomorph.prowler;

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

    private static final AzCommand<Prowler> ARM_ATTACK = AzCommand.<Prowler>replay()
        .play(AzAlienAnimationUtil.BODY, ProwlerAnimationRefs.FULL_ATTACK_ARM_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Prowler> BITE_ATTACK = AzCommand.<Prowler>replay()
        .play(AzAlienAnimationUtil.BODY, ProwlerAnimationRefs.FULL_ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Prowler> TAIL_ATTACK = AzCommand.<Prowler>replay()
        .play(AzAlienAnimationUtil.BODY, ProwlerAnimationRefs.FULL_ATTACK_TAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Prowler> SWIM_ATTACK = AzCommand.<Prowler>replay()
        .play(AzAlienAnimationUtil.BODY, ProwlerAnimationRefs.FULL_ATTACK_SWIM_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
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
        playAttackWithSpeed(ProwlerAnimationRefs.FULL_ATTACK_BITE_ANIMATION_NAME, speed);
    }

    public void rightClawAttack() {
        ARM_ATTACK.dispatchForEntity(prowler);
    }

    public void rightClawAttack(float speed) {
        playAttackWithSpeed(ProwlerAnimationRefs.FULL_ATTACK_ARM_ANIMATION_NAME, speed);
    }

    public void tailAttackQuad() {
        TAIL_ATTACK.dispatchForEntity(prowler);
    }

    public void tailAttackQuad(float speed) {
        playAttackWithSpeed(ProwlerAnimationRefs.FULL_ATTACK_TAIL_ANIMATION_NAME, speed);
    }

    public void swimAttack() {
        SWIM_ATTACK.dispatchForEntity(prowler);
    }

    public void swimAttack(float speed) {
        playAttackWithSpeed(ProwlerAnimationRefs.FULL_ATTACK_SWIM_ANIMATION_NAME, speed);
    }

    private void playAttackWithSpeed(String animationName, float speed) {
        AzCommand.<Prowler>replay()
            .play(AzAlienAnimationUtil.BODY, animationName, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(prowler);
    }
}
