package com.alien.common.gameplay.entity.living.alien.predalien_adolescent;

import com.alien.common.gameplay.entity.living.alien.adolescent.AdolescentAnimationRefs;
import com.alien.common.util.AzAlienAnimationUtil;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;

/**
 * Whole-body dispatch for the reworked predalien adolescent.
 * <p>
 * Same conversion as {@code AdolescentAnimationDispatcher}: the rework replaced 44 limb-split clips with 13 whole-body
 * ones, so every action plays a single clip on the BODY track instead of being composed across seven limb tracks.
 * <p>
 * Names are shared from {@link AdolescentAnimationRefs} on purpose - this class already borrowed them, and the reworked
 * predalien animation file was authored with a clip set identical to the base adolescent's. Its ANIMATION FILE is still
 * its own ({@code PredalienAdolescentAnimator} points at {@code predalien_adolescent}), so the poses differ; only the
 * names are shared.
 */
public class PredalienAdolescentAnimationDispatcher {

    private static final AzCommand<PredalienAdolescent> IDLE = AzCommand.<PredalienAdolescent>idempotent()
        .play(AzAlienAnimationUtil.BODY, AdolescentAnimationRefs.IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<PredalienAdolescent> WALK = AzCommand.<PredalienAdolescent>idempotent()
        .play(AzAlienAnimationUtil.BODY, AdolescentAnimationRefs.WALK_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<PredalienAdolescent> RUN = AzCommand.<PredalienAdolescent>idempotent()
        .play(AzAlienAnimationUtil.BODY, AdolescentAnimationRefs.RUN_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<PredalienAdolescent> SWIM = AzCommand.<PredalienAdolescent>idempotent()
        .play(AzAlienAnimationUtil.BODY, AdolescentAnimationRefs.SWIM_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<PredalienAdolescent> CRAWL = AzCommand.<PredalienAdolescent>idempotent()
        .play(AzAlienAnimationUtil.BODY, AdolescentAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<PredalienAdolescent> CRAWL_HOLD = AzCommand.<PredalienAdolescent>idempotent()
        .play(
            AzAlienAnimationUtil.BODY,
            AdolescentAnimationRefs.CRAWL_ANIMATION_NAME,
            AzPlayBehaviors.HOLD_ON_LAST_FRAME
        )
        .build();

    private static final AzCommand<PredalienAdolescent> POUNCE = AzCommand.<PredalienAdolescent>replay()
        .play(AzAlienAnimationUtil.BODY, AdolescentAnimationRefs.POUNCE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<PredalienAdolescent> BITE_ATTACK = AzCommand.<PredalienAdolescent>replay()
        .play(AzAlienAnimationUtil.BODY, AdolescentAnimationRefs.ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<PredalienAdolescent> SWIPE_ATTACK = AzCommand.<PredalienAdolescent>replay()
        .play(AzAlienAnimationUtil.BODY, AdolescentAnimationRefs.ATTACK_SWIPE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<PredalienAdolescent> SWIM_BITE_ATTACK = AzCommand.<PredalienAdolescent>replay()
        .play(
            AzAlienAnimationUtil.BODY,
            AdolescentAnimationRefs.ATTACK_SWIMBITE_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE
        )
        .build();

    private final PredalienAdolescent predalienAdolescent;

    public PredalienAdolescentAnimationDispatcher(PredalienAdolescent predalienAdolescent) {
        this.predalienAdolescent = predalienAdolescent;
    }

    public void crawl() {
        CRAWL.dispatchForEntity(predalienAdolescent);
    }

    public void crawlHold() {
        CRAWL_HOLD.dispatchForEntity(predalienAdolescent);
    }

    public void idle() {
        IDLE.dispatchForEntity(predalienAdolescent);
    }

    public void lunge() {
        POUNCE.dispatchForEntity(predalienAdolescent);
    }

    public void run() {
        RUN.dispatchForEntity(predalienAdolescent);
    }

    public void swim() {
        SWIM.dispatchForEntity(predalienAdolescent);
    }

    public void walk() {
        WALK.dispatchForEntity(predalienAdolescent);
    }

    public void biteAttack() {
        BITE_ATTACK.dispatchForEntity(predalienAdolescent);
    }

    /** Old name kept so existing callers still compile; the rework retired the per-arm claw for a whole-body swipe. */
    public void rightClawAttack() {
        SWIPE_ATTACK.dispatchForEntity(predalienAdolescent);
    }

    /** New with the rework - nothing calls this yet. */
    public void swimBiteAttack() {
        SWIM_BITE_ATTACK.dispatchForEntity(predalienAdolescent);
    }
}
