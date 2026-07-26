package com.alien.common.gameplay.entity.living.alien.xenomorph.predalien;

import com.alien.common.util.AzAlienAnimationUtil;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.blib.api.client.animation.v1.command.policy.AzDispatchMode;

/**
 * Predalien animation dispatch, single-track.
 * <p>
 * Everything used to be composed across {@code XENO_LIMBS} - seven tracks, one clip per body part. The predalien has
 * been rebuilt onto whole-body clips, so it all plays on {@code BODY} now, matching the praetorian.
 */
public class PredalienAnimationDispatcher {

    /**
     * The pounce plays at 70% speed. It is a heavier animal than the runner or prowler that share this move, and at
     * full rate the leap reads as a twitch rather than a lunge.
     */
    private static final float LUNGE_ANIMATION_SPEED = 0.7F;

    private static final AzCommand<Predalien> CLAW_ATTACK = AzCommand.<Predalien>replay()
        .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.FULL_ATTACK_CLAW_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Predalien> BITE_ATTACK = AzCommand.<Predalien>replay()
        .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.FULL_ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Predalien> TAIL_ATTACK = AzCommand.<Predalien>replay()
        .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.FULL_ATTACK_TAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Predalien> BACKHAND_ATTACK = AzCommand.<Predalien>replay()
        .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.FULL_ATTACK_BACKHAND_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Predalien> SWIM_ATTACK = AzCommand.<Predalien>replay()
        .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.FULL_ATTACK_SWIM_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Predalien> CRAWL = AzCommand.<Predalien>idempotent()
        .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Predalien> CRAWL_HOLD = AzCommand.<Predalien>idempotent()
        .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
        .build();

    private static final AzCommand<Predalien> IDLE = AzCommand.<Predalien>idempotent()
        .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Predalien> RUN = AzCommand.<Predalien>idempotent()
        .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.RUN_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Predalien> SWIM = AzCommand.<Predalien>idempotent()
        .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.SWIM_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Predalien> WALK = AzCommand.<Predalien>idempotent()
        .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.WALK_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Predalien> JUMP = AzCommand.<Predalien>replay()
        .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.JUMP_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Predalien> LAND = AzCommand.<Predalien>replay()
        .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.LAND_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private final Predalien predalien;

    public PredalienAnimationDispatcher(Predalien predalien) {
        this.predalien = predalien;
    }

    public void crawl() {
        CRAWL.dispatchForEntity(predalien);
    }

    public void crawl(float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            PredalienAnimationRefs.CRAWL_ANIMATION_NAME,
            AzPlayBehaviors.LOOP,
            AzDispatchMode.PLAY_IF_NOT_PLAYING,
            speed
        ).dispatchForEntity(predalien);
    }

    public void crawlHold() {
        CRAWL_HOLD.dispatchForEntity(predalien);
    }

    public void idle() {
        IDLE.dispatchForEntity(predalien);
    }

    public void run() {
        RUN.dispatchForEntity(predalien);
    }

    public void swim() {
        SWIM.dispatchForEntity(predalien);
    }

    public void walk() {
        WALK.dispatchForEntity(predalien);
    }

    /** The pounce. Idempotent so holding the lunge state doesn't restart it every frame. */
    public void lunge() {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            PredalienAnimationRefs.LUNGE_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE,
            AzDispatchMode.PLAY_IF_NOT_PLAYING,
            LUNGE_ANIMATION_SPEED
        ).dispatchForEntity(predalien);
    }

    public void jump() {
        JUMP.dispatchForEntity(predalien);
    }

    public void land() {
        LAND.dispatchForEntity(predalien);
    }

    public void biteAttack() {
        BITE_ATTACK.dispatchForEntity(predalien);
    }

    public void biteAttack(float speed) {
        playAttackWithSpeed(PredalienAnimationRefs.FULL_ATTACK_BITE_ANIMATION_NAME, speed);
    }

    public void rightClawAttack() {
        CLAW_ATTACK.dispatchForEntity(predalien);
    }

    public void rightClawAttack(float speed) {
        playAttackWithSpeed(PredalienAnimationRefs.FULL_ATTACK_CLAW_ANIMATION_NAME, speed);
    }

    public void tailAttack() {
        TAIL_ATTACK.dispatchForEntity(predalien);
    }

    public void tailAttack(float speed) {
        playAttackWithSpeed(PredalienAnimationRefs.FULL_ATTACK_TAIL_ANIMATION_NAME, speed);
    }

    public void backhandAttack() {
        BACKHAND_ATTACK.dispatchForEntity(predalien);
    }

    public void backhandAttack(float speed) {
        playAttackWithSpeed(PredalienAnimationRefs.FULL_ATTACK_BACKHAND_ANIMATION_NAME, speed);
    }

    public void swimAttack() {
        SWIM_ATTACK.dispatchForEntity(predalien);
    }

    public void swimAttack(float speed) {
        playAttackWithSpeed(PredalienAnimationRefs.FULL_ATTACK_SWIM_ANIMATION_NAME, speed);
    }

    private void playAttackWithSpeed(String animationName, float speed) {
        AzCommand.<Predalien>replay()
            .play(AzAlienAnimationUtil.BODY, animationName, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(predalien);
    }
}
