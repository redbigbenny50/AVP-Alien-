package com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger;

import com.alien.common.gameplay.entity.dismemberment.MirroredAttackSide;
import com.alien.common.util.AzAlienAnimationUtil;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.blib.api.client.animation.v1.command.policy.AzDispatchMode;

public class HarbingerAnimationDispatcher {

    private static final AzCommand<Harbinger> BITE_ATTACK = AzCommand.<Harbinger>replay()
        .play(AzAlienAnimationUtil.BODY, HarbingerAnimationRefs.ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
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

    // ---- Back-whip layer -------------------------------------------------------------------------------
    // These run on LEFT_WHIP / RIGHT_WHIP, not BODY, so they play in parallel with whatever the body is
    // doing. Idempotent: the animator re-dispatches every frame and the track ignores a repeat of what it
    // is already playing.

    private static final AzCommand<Harbinger> LEFT_WHIP_IDLE = AzCommand.<Harbinger>idempotent()
        .play(
            AzAlienAnimationUtil.LEFT_WHIP,
            HarbingerAnimationRefs.IDLE_LEFT_WHIP_ANIMATION_NAME,
            AzPlayBehaviors.LOOP
        )
        .build();

    private static final AzCommand<Harbinger> RIGHT_WHIP_IDLE = AzCommand.<Harbinger>idempotent()
        .play(
            AzAlienAnimationUtil.RIGHT_WHIP,
            HarbingerAnimationRefs.IDLE_RIGHT_WHIP_ANIMATION_NAME,
            AzPlayBehaviors.LOOP
        )
        .build();

    private static final AzCommand<Harbinger> LEFT_WHIP_MOVEMENT = AzCommand.<Harbinger>idempotent()
        .play(
            AzAlienAnimationUtil.LEFT_WHIP,
            HarbingerAnimationRefs.MOVEMENT_LEFT_WHIP_ANIMATION_NAME,
            AzPlayBehaviors.LOOP
        )
        .build();

    private static final AzCommand<Harbinger> RIGHT_WHIP_MOVEMENT = AzCommand.<Harbinger>idempotent()
        .play(
            AzAlienAnimationUtil.RIGHT_WHIP,
            HarbingerAnimationRefs.MOVEMENT_RIGHT_WHIP_ANIMATION_NAME,
            AzPlayBehaviors.LOOP
        )
        .build();

    private static final AzCommand<Harbinger> LEFT_WHIP_ATTACK_IDLE = AzCommand.<Harbinger>idempotent()
        .play(
            AzAlienAnimationUtil.LEFT_WHIP,
            HarbingerAnimationRefs.ATTACK_LEFT_WHIP_IDLE_ANIMATION_NAME,
            AzPlayBehaviors.LOOP
        )
        .build();

    private static final AzCommand<Harbinger> RIGHT_WHIP_ATTACK_IDLE = AzCommand.<Harbinger>idempotent()
        .play(
            AzAlienAnimationUtil.RIGHT_WHIP,
            HarbingerAnimationRefs.ATTACK_RIGHT_WHIP_IDLE_ANIMATION_NAME,
            AzPlayBehaviors.LOOP
        )
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

    public void leftWhipIdle() {
        LEFT_WHIP_IDLE.dispatchForEntity(harbinger);
    }

    public void rightWhipIdle() {
        RIGHT_WHIP_IDLE.dispatchForEntity(harbinger);
    }

    public void leftWhipMovement() {
        LEFT_WHIP_MOVEMENT.dispatchForEntity(harbinger);
    }

    public void rightWhipMovement() {
        RIGHT_WHIP_MOVEMENT.dispatchForEntity(harbinger);
    }

    public void leftWhipAttackIdle() {
        LEFT_WHIP_ATTACK_IDLE.dispatchForEntity(harbinger);
    }

    public void rightWhipAttackIdle() {
        RIGHT_WHIP_ATTACK_IDLE.dispatchForEntity(harbinger);
    }

    /**
     * The crawl BITE reuses {@link #biteAttack} - the model's only bite clip is already the crawl one.
     * <p>
     * The whipstab clips touch ONLY that side's whip bones, so they are dispatched to that side's whip track rather
     * than to BODY. The body keeps crawling underneath and the opposite whip keeps its own loop.
     */
    public void leftWhipstabAttack(float speed) {
        AzCommand.<Harbinger>replay()
            .play(
                AzAlienAnimationUtil.LEFT_WHIP,
                HarbingerAnimationRefs.ATTACKCRAWL_LEFT_WHIPSTAB_ANIMATION_NAME,
                AzPlayBehaviors.PLAY_ONCE
            )
            .setSpeed(AzAlienAnimationUtil.LEFT_WHIP, speed)
            .build()
            .dispatchForEntity(harbinger);
    }

    public void rightWhipstabAttack(float speed) {
        AzCommand.<Harbinger>replay()
            .play(
                AzAlienAnimationUtil.RIGHT_WHIP,
                HarbingerAnimationRefs.ATTACKCRAWL_RIGHT_WHIPSTAB_ANIMATION_NAME,
                AzPlayBehaviors.PLAY_ONCE
            )
            .setSpeed(AzAlienAnimationUtil.RIGHT_WHIP, speed)
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

    /** ⚠ ONE authored clip using BOTH arms - not a mirrored pair. */
    public void swimClawsAttack() {
        AzCommand.<Harbinger>replay()
            .play(
                AzAlienAnimationUtil.BODY,
                HarbingerAnimationRefs.SWIM_ATTACK_CLAWS_ANIMATION_NAME,
                AzPlayBehaviors.PLAY_ONCE
            )
            .build()
            .dispatchForEntity(harbinger);
    }

    public void swimBiteAttack() {
        AzCommand.<Harbinger>replay()
            .play(
                AzAlienAnimationUtil.BODY,
                HarbingerAnimationRefs.SWIM_ATTACK_BITE_ANIMATION_NAME,
                AzPlayBehaviors.PLAY_ONCE
            )
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
        rightClawAttack(1.0F);
    }

    /** ⚠ MIRRORED. Kept under the old NAME so no caller changes - the side is decided HERE, once per swing. */
    public void rightClawAttack(float speed) {
        var clip = MirroredAttackSide.useLeftArm(harbinger)
            ? HarbingerAnimationRefs.ATTACK_CLAW_LEFT_ANIMATION_NAME
            : HarbingerAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME;

        AzCommand.<Harbinger>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(harbinger);
    }

    /** ⚠ MIRRORED prone claw swipe - new, the crawl set had only the whipstabs and the bite before. */
    public void crawlAttack() {
        crawlAttack(1.0F);
    }

    public void crawlAttack(float speed) {
        var clip = MirroredAttackSide.useLeftArm(harbinger)
            ? HarbingerAnimationRefs.CRAWL_ATTACK_LEFT_ANIMATION_NAME
            : HarbingerAnimationRefs.CRAWL_ATTACK_RIGHT_ANIMATION_NAME;

        AzCommand.<Harbinger>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(harbinger);
    }

    /**
     * ⚠ THE STANDING WHIPSTAB, per side. Dispatched on that side's WHIP TRACK, not the body track - the body must stay
     * free to keep walking underneath, exactly as the crawling pair already does.
     */
    public void whipStab(boolean leftSide) {
        AzCommand.<Harbinger>replay()
            .play(
                leftSide ? AzAlienAnimationUtil.LEFT_WHIP : AzAlienAnimationUtil.RIGHT_WHIP,
                leftSide
                    ? HarbingerAnimationRefs.ATTACK_LEFT_WHIPSTAB_ANIMATION_NAME
                    : HarbingerAnimationRefs.ATTACK_RIGHT_WHIPSTAB_ANIMATION_NAME,
                AzPlayBehaviors.PLAY_ONCE
            )
            .build()
            .dispatchForEntity(harbinger);
    }

    /** The hurt pose for a severed whip, on that side's own track. */
    public void damagedWhip(boolean leftSide) {
        AzCommand.<Harbinger>idempotent()
            .play(
                leftSide ? AzAlienAnimationUtil.LEFT_WHIP : AzAlienAnimationUtil.RIGHT_WHIP,
                HarbingerAnimationRefs.DAMAGED_WHIPS_ANIMATION_NAME,
                AzPlayBehaviors.LOOP
            )
            .build()
            .dispatchForEntity(harbinger);
    }

    public void tailAttack() {
        tailAttack(1.0F);
    }

    /** Regular swing that throws whatever crowded in. */
    /** ⚠ MIRRORED. The side is decided HERE, once per swing, by the shared seeded helper. */
    public void backhandAttack(float speed) {
        var clip = MirroredAttackSide.useLeftArm(harbinger)
            ? HarbingerAnimationRefs.ATTACK_BACKHAND_LEFT_ANIMATION_NAME
            : HarbingerAnimationRefs.ATTACK_BACKHAND_RIGHT_ANIMATION_NAME;

        AzCommand.<Harbinger>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(harbinger);
    }

    /** Triggered single-target punt. */
    public void kickAttack(float speed) {
        AzCommand.<Harbinger>replay()
            .play(
                AzAlienAnimationUtil.BODY,
                HarbingerAnimationRefs.ATTACK_KICK_ANIMATION_NAME,
                AzPlayBehaviors.PLAY_ONCE
            )
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(harbinger);
    }

    /** Triggered barrier breaker - fights masonry, not mobs. */
    public void frontKickAttack(float speed) {
        AzCommand.<Harbinger>replay()
            .play(
                AzAlienAnimationUtil.BODY,
                HarbingerAnimationRefs.ATTACK_FRONT_KICK_ANIMATION_NAME,
                AzPlayBehaviors.PLAY_ONCE
            )
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(harbinger);
    }

    /** Triggered crowd-breaker. Body track: it moves the whole skeleton, whips included via their own tracks. */
    public void groundSlamAttack(float speed) {
        AzCommand.<Harbinger>replay()
            .play(
                AzAlienAnimationUtil.BODY,
                HarbingerAnimationRefs.ATTACK_GROUND_SLAM_ANIMATION_NAME,
                AzPlayBehaviors.PLAY_ONCE
            )
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(harbinger);
    }

    /** ⚠ MIRRORED. The side is decided HERE, once per swing, by the shared seeded helper. */
    public void tailAttack(float speed) {
        var clip = MirroredAttackSide.useLeftArm(harbinger)
            ? HarbingerAnimationRefs.ATTACK_TAIL_LEFT_ANIMATION_NAME
            : HarbingerAnimationRefs.ATTACK_TAIL_RIGHT_ANIMATION_NAME;

        AzCommand.<Harbinger>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(harbinger);
    }
}
