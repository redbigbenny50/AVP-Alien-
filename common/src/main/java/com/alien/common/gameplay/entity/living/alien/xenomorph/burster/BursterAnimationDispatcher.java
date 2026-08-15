package com.alien.common.gameplay.entity.living.alien.xenomorph.burster;

import com.alien.common.gameplay.entity.dismemberment.MirroredAttackSide;
import com.alien.common.util.AzAlienAnimationUtil;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.blib.api.client.animation.v1.command.policy.AzDispatchMode;

public class BursterAnimationDispatcher {

    private static final AzCommand<Burster> IDLE = AzCommand.<Burster>idempotent()
        .play(AzAlienAnimationUtil.BODY, BursterAnimationRefs.IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Burster> WALK = AzCommand.<Burster>idempotent()
        .play(AzAlienAnimationUtil.BODY, BursterAnimationRefs.WALK_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Burster> RUN = AzCommand.<Burster>idempotent()
        .play(AzAlienAnimationUtil.BODY, BursterAnimationRefs.RUN_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Burster> CRAWL = AzCommand.<Burster>idempotent()
        .play(AzAlienAnimationUtil.BODY, BursterAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Burster> LUNGE = AzCommand.<Burster>replay()
        .play(AzAlienAnimationUtil.BODY, BursterAnimationRefs.LUNGE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Burster> SWIM = AzCommand.<Burster>idempotent()
        .play(AzAlienAnimationUtil.BODY, BursterAnimationRefs.SWIM_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Burster> ATTACK_BITE = AzCommand.<Burster>replay()
        .play(AzAlienAnimationUtil.BODY, BursterAnimationRefs.ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Burster> ATTACK_TAIL = AzCommand.<Burster>replay()
        .play(AzAlienAnimationUtil.BODY, BursterAnimationRefs.ATTACK_TAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Burster> CRAWL_IDLE = AzCommand.<Burster>idempotent()
        .play(AzAlienAnimationUtil.BODY, BursterAnimationRefs.CRAWL_IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Burster> CRAWL_ATTACK_BITE = AzCommand.<Burster>replay()
        .play(
            AzAlienAnimationUtil.BODY,
            BursterAnimationRefs.CRAWL_ATTACK_BITE_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE
        )
        .build();

    private static final AzCommand<Burster> SWIM_ATTACK = AzCommand.<Burster>replay()
        .play(
            AzAlienAnimationUtil.BODY,
            BursterAnimationRefs.SWIM_ATTACK_BITE_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE
        )
        .build();

    /** ⚠ HOLD_ON_LAST_FRAME - the jump freezes on its final frame until the ground is regained. */
    private static final AzCommand<Burster> JUMP = AzCommand.<Burster>replay()
        .play(AzAlienAnimationUtil.BODY, BursterAnimationRefs.JUMP_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
        .build();

    private static final AzCommand<Burster> LAND = AzCommand.<Burster>replay()
        .play(AzAlienAnimationUtil.BODY, BursterAnimationRefs.LAND_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private final Burster burster;

    public BursterAnimationDispatcher(Burster burster) {
        this.burster = burster;
    }

    public void idle() {
        IDLE.dispatchForEntity(burster);
    }

    public void walk() {
        WALK.dispatchForEntity(burster);
    }

    public void run() {
        RUN.dispatchForEntity(burster);
    }

    public void crawl() {
        CRAWL.dispatchForEntity(burster);
    }

    public void crawl(float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            BursterAnimationRefs.CRAWL_ANIMATION_NAME,
            AzPlayBehaviors.LOOP,
            AzDispatchMode.PLAY_IF_NOT_PLAYING,
            speed
        ).dispatchForEntity(burster);
    }

    /** ⭐ Now the REAL crawl.idle loop, not the crawl gait frozen on its last frame. */
    public void crawlHold() {
        CRAWL_IDLE.dispatchForEntity(burster);
    }

    public void lunge() {
        LUNGE.dispatchForEntity(burster);
    }

    public void swim() {
        SWIM.dispatchForEntity(burster);
    }

    /** ⚠ MIRRORED. The side is decided HERE, once per swing, by the shared seeded helper. */
    public void clawAttack() {
        clawAttack(1.0F);
    }

    public void clawAttack(float speed) {
        var clip = MirroredAttackSide.useLeftArm(burster)
            ? BursterAnimationRefs.ATTACK_CLAW_LEFT_ANIMATION_NAME
            : BursterAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME;

        AzCommand.<Burster>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(burster);
    }

    /** Crawling claw swing - same side rule. */
    public void crawlAttack() {
        crawlAttack(1.0F);
    }

    public void crawlAttack(float speed) {
        var clip = MirroredAttackSide.useLeftArm(burster)
            ? BursterAnimationRefs.CRAWL_ATTACK_LEFT_ANIMATION_NAME
            : BursterAnimationRefs.CRAWL_ATTACK_RIGHT_ANIMATION_NAME;

        AzCommand.<Burster>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(burster);
    }

    public void crawlBiteAttack() {
        CRAWL_ATTACK_BITE.dispatchForEntity(burster);
    }

    public void crawlBiteAttack(float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            BursterAnimationRefs.CRAWL_ATTACK_BITE_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE,
            AzDispatchMode.REPLAY,
            speed
        ).dispatchForEntity(burster);
    }

    /** ⚠ Bite-only in the water - there is no mirrored swim pair. */
    public void swimAttack() {
        SWIM_ATTACK.dispatchForEntity(burster);
    }

    public void jump() {
        JUMP.dispatchForEntity(burster);
    }

    public void land() {
        LAND.dispatchForEntity(burster);
    }

    public void biteAttack() {
        ATTACK_BITE.dispatchForEntity(burster);
    }

    public void biteAttack(float speed) {
        AzCommand.<Burster>replay()
            .play(AzAlienAnimationUtil.BODY, BursterAnimationRefs.ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(burster);
    }

    public void tailAttack() {
        ATTACK_TAIL.dispatchForEntity(burster);
    }

    public void tailAttack(float speed) {
        AzCommand.<Burster>replay()
            .play(AzAlienAnimationUtil.BODY, BursterAnimationRefs.ATTACK_TAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(burster);
    }
}
