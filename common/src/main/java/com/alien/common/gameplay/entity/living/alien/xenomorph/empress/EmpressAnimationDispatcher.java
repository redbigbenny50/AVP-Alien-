package com.alien.common.gameplay.entity.living.alien.xenomorph.empress;

import com.alien.common.util.AzAlienAnimationUtil;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.blib.api.client.animation.v1.command.policy.AzDispatchMode;

public class EmpressAnimationDispatcher {

    private static final AzCommand<Empress> IDLE = AzCommand.<Empress>idempotent()
        .play(AzAlienAnimationUtil.BODY, EmpressAnimationRefs.IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Empress> RUN = AzCommand.<Empress>idempotent()
        .play(AzAlienAnimationUtil.BODY, EmpressAnimationRefs.RUN_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Empress> WALK = AzCommand.<Empress>idempotent()
        .play(AzAlienAnimationUtil.BODY, EmpressAnimationRefs.WALK_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Empress> SWIM = AzCommand.<Empress>idempotent()
        .play(AzAlienAnimationUtil.BODY, EmpressAnimationRefs.SWIM_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Empress> SIT_ON_OVIPOSITOR = AzCommand.<Empress>idempotent()
        .play(AzAlienAnimationUtil.BODY, EmpressAnimationRefs.RIDE_EGGSACK_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Empress> BACKHAND = AzCommand.<Empress>replay()
        .play(AzAlienAnimationUtil.BODY, EmpressAnimationRefs.BACKHAND_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    // Previously played SWIM as a stand-in; her crawl clips exist (crawl, crawlidle) and are wired now.
    private static final AzCommand<Empress> CRAWL = AzCommand.<Empress>idempotent()
        .play(AzAlienAnimationUtil.BODY, EmpressAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Empress> CRAWL_HOLD = AzCommand.<Empress>idempotent()
        .play(AzAlienAnimationUtil.BODY, EmpressAnimationRefs.CRAWL_IDLE_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
        .build();

    private static final AzCommand<Empress> SWIPEDOWN = AzCommand.<Empress>replay()
        .play(AzAlienAnimationUtil.BODY, EmpressAnimationRefs.SWIPEDOWN_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Empress> TAILSTRIKE = AzCommand.<Empress>replay()
        .play(AzAlienAnimationUtil.BODY, EmpressAnimationRefs.TAILSTRIKE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private final Empress empress;

    public EmpressAnimationDispatcher(Empress empress) {
        this.empress = empress;
    }

    public void idle() {
        IDLE.dispatchForEntity(empress);
    }

    public void run() {
        RUN.dispatchForEntity(empress);
    }

    public void sitOnOvipositor() {
        SIT_ON_OVIPOSITOR.dispatchForEntity(empress);
    }

    public void swim() {
        SWIM.dispatchForEntity(empress);
    }

    public void crawl() {
        CRAWL.dispatchForEntity(empress);
    }

    public void crawl(float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            EmpressAnimationRefs.SWIM_ANIMATION_NAME,
            AzPlayBehaviors.LOOP,
            AzDispatchMode.PLAY_IF_NOT_PLAYING,
            speed
        ).dispatchForEntity(empress);
    }

    /** Crawl posture transitions - one-shots on the crawl edge; speed 2 on a leg-loss collapse. */
    public void crawlDrop(float speed) {
        AzCommand.<Empress>replay()
            .play(AzAlienAnimationUtil.BODY, EmpressAnimationRefs.CRAWL_DROP_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(empress);
    }

    public void crawlRise() {
        AzCommand.<Empress>replay()
            .play(AzAlienAnimationUtil.BODY, EmpressAnimationRefs.CRAWL_RISE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(empress);
    }

    public void crawlHold() {
        CRAWL_HOLD.dispatchForEntity(empress);
    }

    public void walk() {
        WALK.dispatchForEntity(empress);
    }

    public void backhandAttack() {
        BACKHAND.dispatchForEntity(empress);
    }

    public void backhandAttack(float speed) {
        attackWithSpeed(EmpressAnimationRefs.BACKHAND_ANIMATION_NAME, speed);
    }

    public void swipeDownAttack() {
        SWIPEDOWN.dispatchForEntity(empress);
    }

    public void swipeDownAttack(float speed) {
        attackWithSpeed(EmpressAnimationRefs.SWIPEDOWN_ANIMATION_NAME, speed);
    }

    public void tailStrikeAttack() {
        TAILSTRIKE.dispatchForEntity(empress);
    }

    public void tailStrikeAttack(float speed) {
        attackWithSpeed(EmpressAnimationRefs.TAILSTRIKE_ANIMATION_NAME, speed);
    }

    private void attackWithSpeed(String animationName, float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            animationName,
            AzPlayBehaviors.PLAY_ONCE,
            AzDispatchMode.REPLAY,
            speed
        ).dispatchForEntity(empress);
    }
}
