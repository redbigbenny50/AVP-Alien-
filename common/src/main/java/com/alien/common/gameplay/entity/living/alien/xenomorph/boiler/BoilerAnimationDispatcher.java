package com.alien.common.gameplay.entity.living.alien.xenomorph.boiler;

import com.alien.common.util.AzAlienAnimationUtil;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.blib.api.client.animation.v1.command.policy.AzDispatchMode;

public class BoilerAnimationDispatcher {

    private static final AzCommand<Boiler> CRAWL = AzCommand.<Boiler>idempotent()
        .play(BoilerAnimationRefs.FULL_BODY, BoilerAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Boiler> CRAWL_HOLD = AzCommand.<Boiler>idempotent()
        .play(BoilerAnimationRefs.FULL_BODY, BoilerAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
        .build();

    private static final AzCommand<Boiler> IDLE = AzCommand.<Boiler>idempotent()
        .play(BoilerAnimationRefs.FULL_BODY, BoilerAnimationRefs.IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Boiler> RUN = AzCommand.<Boiler>idempotent()
        .play(BoilerAnimationRefs.FULL_BODY, BoilerAnimationRefs.RUN_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Boiler> SWIM = AzCommand.<Boiler>idempotent()
        .play(BoilerAnimationRefs.FULL_BODY, BoilerAnimationRefs.SWIM_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Boiler> WALK = AzCommand.<Boiler>idempotent()
        .play(BoilerAnimationRefs.FULL_BODY, BoilerAnimationRefs.WALK_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    /** ⚠ HOLD_ON_LAST_FRAME - the jump freezes on its final frame until the ground is regained. */
    private static final AzCommand<Boiler> JUMP = AzCommand.<Boiler>replay()
        .play(BoilerAnimationRefs.FULL_BODY, BoilerAnimationRefs.JUMP_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
        .build();

    private static final AzCommand<Boiler> LAND = AzCommand.<Boiler>replay()
        .play(BoilerAnimationRefs.FULL_BODY, BoilerAnimationRefs.LAND_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private final Boiler boiler;

    public BoilerAnimationDispatcher(Boiler boiler) {
        this.boiler = boiler;
    }

    public void crawl() {
        CRAWL.dispatchForEntity(boiler);
    }

    public void crawl(float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            BoilerAnimationRefs.FULL_BODY,
            BoilerAnimationRefs.CRAWL_ANIMATION_NAME,
            AzPlayBehaviors.LOOP,
            AzDispatchMode.PLAY_IF_NOT_PLAYING,
            speed
        ).dispatchForEntity(boiler);
    }

    public void crawlHold() {
        CRAWL_HOLD.dispatchForEntity(boiler);
    }

    public void jump() {
        JUMP.dispatchForEntity(boiler);
    }

    public void land() {
        LAND.dispatchForEntity(boiler);
    }

    public void idle() {
        IDLE.dispatchForEntity(boiler);
    }

    public void run() {
        RUN.dispatchForEntity(boiler);
    }

    public void swim() {
        SWIM.dispatchForEntity(boiler);
    }

    public void walk() {
        WALK.dispatchForEntity(boiler);
    }

}
