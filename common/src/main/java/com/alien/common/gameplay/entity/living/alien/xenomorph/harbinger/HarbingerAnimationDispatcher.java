package com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger;

import com.alien.common.util.AzAlienAnimationUtil;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.blib.api.client.animation.v1.command.policy.AzDispatchMode;

public class HarbingerAnimationDispatcher {

    private static final AzCommand<Harbinger> CLAW_ATTACK = AzCommand.<Harbinger>replay()
        .play(AzAlienAnimationUtil.BODY, HarbingerAnimationRefs.ATTACK_CLAW_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Harbinger> BITE_ATTACK = AzCommand.<Harbinger>replay()
        .play(AzAlienAnimationUtil.BODY, HarbingerAnimationRefs.ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Harbinger> TAIL_ATTACK = AzCommand.<Harbinger>replay()
        .play(AzAlienAnimationUtil.BODY, HarbingerAnimationRefs.ATTACK_TAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Harbinger> CRAWL = AzCommand.<Harbinger>idempotent()
        .play(AzAlienAnimationUtil.BODY, HarbingerAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Harbinger> CRAWL_IDLE = AzCommand.<Harbinger>idempotent()
        .play(AzAlienAnimationUtil.BODY, HarbingerAnimationRefs.CRAWL_IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Harbinger> IDLE = AzCommand.<Harbinger>idempotent()
        .play(AzAlienAnimationUtil.BODY, HarbingerAnimationRefs.IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Harbinger> RUN = AzCommand.<Harbinger>idempotent()
        .play(AzAlienAnimationUtil.BODY, HarbingerAnimationRefs.RUN_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Harbinger> SWIM = AzCommand.<Harbinger>idempotent()
        .play(AzAlienAnimationUtil.BODY, HarbingerAnimationRefs.SWIM_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Harbinger> WALK = AzCommand.<Harbinger>idempotent()
        .play(AzAlienAnimationUtil.BODY, HarbingerAnimationRefs.WALK_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private final Harbinger harbinger;

    public HarbingerAnimationDispatcher(Harbinger harbinger) {
        this.harbinger = harbinger;
    }

    public void crawl() {
        CRAWL.dispatchForEntity(harbinger);
    }

    public void crawl(float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            HarbingerAnimationRefs.CRAWL_ANIMATION_NAME,
            AzPlayBehaviors.LOOP,
            AzDispatchMode.PLAY_IF_NOT_PLAYING,
            speed
        ).dispatchForEntity(harbinger);
    }

    public void crawlIdle() {
        CRAWL_IDLE.dispatchForEntity(harbinger);
    }

    public void idle() {
        IDLE.dispatchForEntity(harbinger);
    }

    public void run() {
        RUN.dispatchForEntity(harbinger);
    }

    public void swim() {
        SWIM.dispatchForEntity(harbinger);
    }

    public void walk() {
        WALK.dispatchForEntity(harbinger);
    }

    /** The crawl BITE reuses {@link #biteAttack} - the model's only bite clip is already the crawl one. */
    public void leftWhipstabAttack(float speed) {
        AzCommand.<Harbinger>replay()
            .play(
                AzAlienAnimationUtil.BODY,
                HarbingerAnimationRefs.ATTACKCRAWL_LEFT_WHIPSTAB_ANIMATION_NAME,
                AzPlayBehaviors.PLAY_ONCE
            )
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(harbinger);
    }

    public void rightWhipstabAttack(float speed) {
        AzCommand.<Harbinger>replay()
            .play(
                AzAlienAnimationUtil.BODY,
                HarbingerAnimationRefs.ATTACKCRAWL_RIGHT_WHIPSTAB_ANIMATION_NAME,
                AzPlayBehaviors.PLAY_ONCE
            )
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(harbinger);
    }

    /** Crawl posture transitions - one-shots on the crawl edge; speed 2 on a leg-loss collapse. */
    public void crawlDown(float speed) {
        AzCommand.<Harbinger>replay()
            .play(AzAlienAnimationUtil.BODY, HarbingerAnimationRefs.CRAWL_DOWN_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(harbinger);
    }

    public void crawlUp() {
        AzCommand.<Harbinger>replay()
            .play(AzAlienAnimationUtil.BODY, HarbingerAnimationRefs.CRAWL_UP_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(harbinger);
    }

    public void biteAttack() {
        BITE_ATTACK.dispatchForEntity(harbinger);
    }

    public void biteAttack(float speed) {
        AzCommand.<Harbinger>replay()
            .play(AzAlienAnimationUtil.BODY, HarbingerAnimationRefs.ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(harbinger);
    }

    public void rightClawAttack() {
        CLAW_ATTACK.dispatchForEntity(harbinger);
    }

    public void rightClawAttack(float speed) {
        AzCommand.<Harbinger>replay()
            .play(AzAlienAnimationUtil.BODY, HarbingerAnimationRefs.ATTACK_CLAW_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(harbinger);
    }

    public void tailAttack() {
        TAIL_ATTACK.dispatchForEntity(harbinger);
    }

    public void tailAttack(float speed) {
        AzCommand.<Harbinger>replay()
            .play(AzAlienAnimationUtil.BODY, HarbingerAnimationRefs.ATTACK_TAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(harbinger);
    }
}
