package com.alien.common.gameplay.entity.living.alien.xenomorph.spitter;

import com.alien.common.gameplay.entity.dismemberment.MirroredAttackSide;
import com.alien.common.util.AzAlienAnimationUtil;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.blib.api.client.animation.v1.command.policy.AzDispatchMode;

public class SpitterAnimationDispatcher {

    private static final AzCommand<Spitter> CLAW_ATTACK = AzCommand.<Spitter>replay()
        .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Spitter> CLAW_ATTACK_LEFT = AzCommand.<Spitter>replay()
        .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.ATTACK_CLAW_LEFT_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Spitter> QUAD_CLAW_ATTACK_LEFT = AzCommand.<Spitter>replay()
        .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.QUAD_ATTACK_CLAW_LEFT_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Spitter> QUAD_ARM_ATTACK = AzCommand.<Spitter>replay()
        .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.QUAD_ATTACK_CLAW_RIGHT_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Spitter> QUAD_BITE_ATTACK = AzCommand.<Spitter>replay()
        .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.QUAD_ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Spitter> QUAD_TAIL_ATTACK = AzCommand.<Spitter>replay()
        .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.QUAD_ATTACK_TAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Spitter> BITE_ATTACK = AzCommand.<Spitter>replay()
        .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Spitter> TAIL_ATTACK = AzCommand.<Spitter>replay()
        .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.ATTACK_TAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Spitter> SPIT_ATTACK = AzCommand.<Spitter>replay()
        .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.SPECIAL_ATTACK_SPIT_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Spitter> CRAWL = AzCommand.<Spitter>idempotent()
        .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Spitter> CRAWL_HOLD = AzCommand.<Spitter>idempotent()
        .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
        .build();

    private static final AzCommand<Spitter> IDLE = AzCommand.<Spitter>idempotent()
        .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Spitter> QUAD_IDLE = AzCommand.<Spitter>idempotent()
        .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.QUAD_IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Spitter> LUNGE = AzCommand.<Spitter>replay()
        .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.LUNGE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Spitter> RUN = AzCommand.<Spitter>idempotent()
        .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.RUN_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Spitter> SWIM = AzCommand.<Spitter>idempotent()
        .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.SWIM_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Spitter> WALK = AzCommand.<Spitter>idempotent()
        .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.WALK_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private final Spitter spitter;

    public SpitterAnimationDispatcher(Spitter spitter) {
        this.spitter = spitter;
    }

    public void crawl() {
        CRAWL.dispatchForEntity(spitter);
    }

    public void crawl(float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            SpitterAnimationRefs.CRAWL_ANIMATION_NAME,
            AzPlayBehaviors.LOOP,
            AzDispatchMode.PLAY_IF_NOT_PLAYING,
            speed
        ).dispatchForEntity(spitter);
    }

    public void crawlHold() {
        CRAWL_HOLD.dispatchForEntity(spitter);
    }

    public void idle() {
        IDLE.dispatchForEntity(spitter);
    }

    /**
     * Standing still ON ALL FOURS.
     * <p>
     * This is what lets quad be a posture the spitter can hold rather than a pose it snaps out of the moment it stops
     * running. Chosen by {@code Spitter.isQuadPosture}, never by the gait directly.
     * </p>
     */
    public void quadIdle() {
        QUAD_IDLE.dispatchForEntity(spitter);
    }

    public void lunge() {
        LUNGE.dispatchForEntity(spitter);
    }

    public void run() {
        RUN.dispatchForEntity(spitter);
    }

    public void swim() {
        SWIM.dispatchForEntity(spitter);
    }

    public void walk() {
        WALK.dispatchForEntity(spitter);
    }

    public void biteAttack() {
        BITE_ATTACK.dispatchForEntity(spitter);
    }

    public void biteAttack(float speed) {
        AzCommand.<Spitter>replay()
            .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(spitter);
    }

    public void rightClawAttack() {
        CLAW_ATTACK.dispatchForEntity(spitter);
    }

    public void rightClawAttack(float speed) {
        AzCommand.<Spitter>replay()
            .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(spitter);
    }

    /**
     * Quad-posture claw swing.
     * <p>
     * ⚠ NAME IS HISTORICAL - side is now chosen from the dismemberment state, exactly as in biped posture. A lost arm
     * is lost in both postures, so the quad set is mirrored too.
     * </p>
     */
    public void rightClawAttackQuad() {
        clawAttackQuad();
    }

    public void clawAttackQuad() {
        if (MirroredAttackSide.useLeftArm(spitter)) {
            QUAD_CLAW_ATTACK_LEFT.dispatchForEntity(spitter);
            return;
        }
        QUAD_ARM_ATTACK.dispatchForEntity(spitter);
    }

    /** Quad claw at a fitted speed - the side rule is identical, only the clip set differs. */
    public void clawAttackQuad(float speed) {
        var clip = MirroredAttackSide.useLeftArm(spitter)
            ? SpitterAnimationRefs.QUAD_ATTACK_CLAW_LEFT_ANIMATION_NAME
            : SpitterAnimationRefs.QUAD_ATTACK_CLAW_RIGHT_ANIMATION_NAME;

        AzCommand.<Spitter>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(spitter);
    }

    public void biteAttackQuad() {
        QUAD_BITE_ATTACK.dispatchForEntity(spitter);
    }

    public void biteAttackQuad(float speed) {
        AzCommand.<Spitter>replay()
            .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.QUAD_ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(spitter);
    }

    public void tailAttackQuad() {
        QUAD_TAIL_ATTACK.dispatchForEntity(spitter);
    }

    public void tailAttackQuad(float speed) {
        AzCommand.<Spitter>replay()
            .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.QUAD_ATTACK_TAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(spitter);
    }

    public void tailAttack() {
        TAIL_ATTACK.dispatchForEntity(spitter);
    }

    public void tailAttack(float speed) {
        AzCommand.<Spitter>replay()
            .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.ATTACK_TAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(spitter);
    }

    public void spitAttack() {
        SPIT_ATTACK.dispatchForEntity(spitter);
    }

    public void spitAttack(float speed) {
        AzCommand.<Spitter>replay()
            .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.SPECIAL_ATTACK_SPIT_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(spitter);
    }

    /** Biped claw swing, side chosen from the dismemberment state. */
    public void clawAttack() {
        if (MirroredAttackSide.useLeftArm(spitter)) {
            CLAW_ATTACK_LEFT.dispatchForEntity(spitter);
            return;
        }
        CLAW_ATTACK.dispatchForEntity(spitter);
    }

    /** Crawling claw swing - same side rule. */
    public void crawlAttack() {
        var clip = MirroredAttackSide.useLeftArm(spitter)
            ? SpitterAnimationRefs.CRAWL_ATTACK_LEFT_ANIMATION_NAME
            : SpitterAnimationRefs.CRAWL_ATTACK_RIGHT_ANIMATION_NAME;

        AzCommand.<Spitter>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(spitter);
    }

    /** Crawling bite - not mirrored. */
    public void crawlBiteAttack() {
        AzCommand.<Spitter>replay()
            .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.CRAWL_ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(spitter);
    }

    /** Swimming claw swing - the spitter DOES have a mirrored swim pair, unlike the runner and prowler. */
    public void swimAttack() {
        var clip = MirroredAttackSide.useLeftArm(spitter)
            ? SpitterAnimationRefs.SWIM_ATTACK_LEFT_ANIMATION_NAME
            : SpitterAnimationRefs.SWIM_ATTACK_RIGHT_ANIMATION_NAME;

        AzCommand.<Spitter>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(spitter);
    }

    /** Getting INTO a crawl. A torn-off leg plays the SAME clip faster. */
    public void crawlDrop(float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            SpitterAnimationRefs.CRAWL_DROP_ANIMATION_NAME,
            AzPlayBehaviors.HOLD_ON_LAST_FRAME,
            AzDispatchMode.PLAY_IF_NOT_PLAYING,
            speed
        ).dispatchForEntity(spitter);
    }

    public void crawlRise() {
        AzCommand.<Spitter>replay()
            .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.CRAWL_RISE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(spitter);
    }

    /** Leaving the ground. HOLD_ON_LAST_FRAME so one clip covers any airborne duration. */
    public void jump() {
        AzCommand.<Spitter>replay()
            .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.JUMP_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
            .build()
            .dispatchForEntity(spitter);
    }

    /** Touching down. */
    public void land() {
        AzCommand.<Spitter>replay()
            .play(AzAlienAnimationUtil.BODY, SpitterAnimationRefs.LAND_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(spitter);
    }
}
