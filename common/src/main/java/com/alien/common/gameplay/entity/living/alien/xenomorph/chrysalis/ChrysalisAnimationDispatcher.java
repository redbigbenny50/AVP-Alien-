package com.alien.common.gameplay.entity.living.alien.xenomorph.chrysalis;

import com.alien.common.gameplay.entity.dismemberment.MirroredAttackSide;
import com.alien.common.util.AzAlienAnimationUtil;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.blib.api.client.animation.v1.command.policy.AzDispatchMode;

public class ChrysalisAnimationDispatcher {

    private static final AzCommand<Chrysalis> BITEATTACK = AzCommand.<Chrysalis>replay()
        .play(AzAlienAnimationUtil.BODY, ChrysalisAnimationRefs.ATTACKBITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Chrysalis> TAILATTACK = AzCommand.<Chrysalis>replay()
        .play(AzAlienAnimationUtil.BODY, ChrysalisAnimationRefs.ATTACKTAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Chrysalis> IDLE = AzCommand.<Chrysalis>idempotent()
        .play(AzAlienAnimationUtil.BODY, ChrysalisAnimationRefs.IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Chrysalis> RUN = AzCommand.<Chrysalis>idempotent()
        .play(AzAlienAnimationUtil.BODY, ChrysalisAnimationRefs.RUN_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Chrysalis> SWIM = AzCommand.<Chrysalis>idempotent()
        .play(AzAlienAnimationUtil.BODY, ChrysalisAnimationRefs.SWIM_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Chrysalis> WALK = AzCommand.<Chrysalis>idempotent()
        .play(AzAlienAnimationUtil.BODY, ChrysalisAnimationRefs.WALK_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Chrysalis> CRAWL = AzCommand.<Chrysalis>idempotent()
        .play(AzAlienAnimationUtil.BODY, ChrysalisAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Chrysalis> ROLL_START = AzCommand.<Chrysalis>replay()
        .play(AzAlienAnimationUtil.BODY, ChrysalisAnimationRefs.ROLL_START_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Chrysalis> ROLL_LOOP = AzCommand.<Chrysalis>idempotent()
        .play(AzAlienAnimationUtil.BODY, ChrysalisAnimationRefs.ROLL_LOOP_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Chrysalis> ROLL_STOP = AzCommand.<Chrysalis>replay()
        .play(AzAlienAnimationUtil.BODY, ChrysalisAnimationRefs.ROLL_STOP_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Chrysalis> ROLL_SMASHED = AzCommand.<Chrysalis>idempotent()
        .play(AzAlienAnimationUtil.BODY, ChrysalisAnimationRefs.ROLL_SMASHED_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
        .build();

    private static final AzCommand<Chrysalis> CRAWL_IDLE = AzCommand.<Chrysalis>idempotent()
        .play(AzAlienAnimationUtil.BODY, ChrysalisAnimationRefs.CRAWL_IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Chrysalis> CRAWL_ATTACK_BITE = AzCommand.<Chrysalis>replay()
        .play(
            AzAlienAnimationUtil.BODY,
            ChrysalisAnimationRefs.CRAWL_ATTACK_BITE_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE
        )
        .build();

    private static final AzCommand<Chrysalis> SWIM_ATTACK = AzCommand.<Chrysalis>replay()
        .play(
            AzAlienAnimationUtil.BODY,
            ChrysalisAnimationRefs.SWIM_ATTACK_BITE_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE
        )
        .build();

    /** ⚠ HOLD_ON_LAST_FRAME - the jump freezes on its final frame until the ground is regained. */
    private static final AzCommand<Chrysalis> JUMP = AzCommand.<Chrysalis>replay()
        .play(AzAlienAnimationUtil.BODY, ChrysalisAnimationRefs.JUMP_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
        .build();

    private static final AzCommand<Chrysalis> LAND = AzCommand.<Chrysalis>replay()
        .play(AzAlienAnimationUtil.BODY, ChrysalisAnimationRefs.LAND_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Chrysalis> CHARGE_ATTACK = AzCommand.<Chrysalis>replay()
        .play(
            AzAlienAnimationUtil.BODY,
            ChrysalisAnimationRefs.ATTACK_CHARGE_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE
        )
        .build();

    /** ⚠ HOLD_ON_LAST_FRAME: there is no authored defence LOOP, so the held final pose is the stance. */
    private static final AzCommand<Chrysalis> DEFENSE_START = AzCommand.<Chrysalis>replay()
        .play(
            AzAlienAnimationUtil.BODY,
            ChrysalisAnimationRefs.DEFENSE_START_ANIMATION_NAME,
            AzPlayBehaviors.HOLD_ON_LAST_FRAME
        )
        .build();

    private static final AzCommand<Chrysalis> DEFENSE_END = AzCommand.<Chrysalis>replay()
        .play(
            AzAlienAnimationUtil.BODY,
            ChrysalisAnimationRefs.DEFENSE_END_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE
        )
        .build();

    private final Chrysalis chrysalis;

    public ChrysalisAnimationDispatcher(Chrysalis chrysalis) {
        this.chrysalis = chrysalis;
    }

    public void idle() {
        IDLE.dispatchForEntity(chrysalis);
    }

    public void run() {
        RUN.dispatchForEntity(chrysalis);
    }

    public void swim() {
        SWIM.dispatchForEntity(chrysalis);
    }

    public void walk() {
        WALK.dispatchForEntity(chrysalis);
    }

    public void crawl() {
        CRAWL.dispatchForEntity(chrysalis);
    }

    public void crawl(float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            ChrysalisAnimationRefs.CRAWL_ANIMATION_NAME,
            AzPlayBehaviors.LOOP,
            AzDispatchMode.PLAY_IF_NOT_PLAYING,
            speed
        ).dispatchForEntity(chrysalis);
    }

    /** ⭐ Now the REAL crawl.idle loop, not the crawl gait frozen on its last frame. */
    public void crawlHold() {
        CRAWL_IDLE.dispatchForEntity(chrysalis);
    }

    public void rollStart() {
        ROLL_START.dispatchForEntity(chrysalis);
    }

    public void rollLoop() {
        ROLL_LOOP.dispatchForEntity(chrysalis);
    }

    public void rollStop() {
        ROLL_STOP.dispatchForEntity(chrysalis);
    }

    public void rollSmashed() {
        ROLL_SMASHED.dispatchForEntity(chrysalis);
    }

    public void rollSmashed(float speed) {
        AzCommand.<Chrysalis>idempotent()
            .play(AzAlienAnimationUtil.BODY, ChrysalisAnimationRefs.ROLL_SMASHED_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(chrysalis);
    }

    public void biteAttack() {
        BITEATTACK.dispatchForEntity(chrysalis);
    }

    public void biteAttack(float speed) {
        AzCommand.<Chrysalis>replay()
            .play(AzAlienAnimationUtil.BODY, ChrysalisAnimationRefs.ATTACKBITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(chrysalis);
    }

    /**
     * ⚠ MIRRORED. Kept under the old NAME so no caller changes, but it is no longer literally the right arm - the side
     * is decided HERE, once per swing, by the shared seeded helper.
     */
    public void rightClawAttack() {
        rightClawAttack(1.0F);
    }

    public void rightClawAttack(float speed) {
        var clip = MirroredAttackSide.useLeftArm(chrysalis)
            ? ChrysalisAnimationRefs.ATTACK_CLAW_LEFT_ANIMATION_NAME
            : ChrysalisAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME;

        AzCommand.<Chrysalis>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(chrysalis);
    }

    /** Crawling claw swing - same side rule. */
    public void crawlAttack() {
        crawlAttack(1.0F);
    }

    public void crawlAttack(float speed) {
        var clip = MirroredAttackSide.useLeftArm(chrysalis)
            ? ChrysalisAnimationRefs.CRAWL_ATTACK_LEFT_ANIMATION_NAME
            : ChrysalisAnimationRefs.CRAWL_ATTACK_RIGHT_ANIMATION_NAME;

        AzCommand.<Chrysalis>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(chrysalis);
    }

    public void chargeAttack() {
        CHARGE_ATTACK.dispatchForEntity(chrysalis);
    }

    public void chargeAttack(float speed) {
        AzCommand.<Chrysalis>replay()
            .play(
                AzAlienAnimationUtil.BODY,
                ChrysalisAnimationRefs.ATTACK_CHARGE_ANIMATION_NAME,
                AzPlayBehaviors.PLAY_ONCE
            )
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(chrysalis);
    }

    /** Curling up. Holds its last frame for the whole stance. */
    public void defenseStart() {
        DEFENSE_START.dispatchForEntity(chrysalis);
    }

    /** Uncurling. */
    public void defenseEnd() {
        DEFENSE_END.dispatchForEntity(chrysalis);
    }

    public void crawlBiteAttack() {
        CRAWL_ATTACK_BITE.dispatchForEntity(chrysalis);
    }

    /** ⚠ Bite-only in the water - there is no mirrored swim pair. */
    public void swimAttack() {
        SWIM_ATTACK.dispatchForEntity(chrysalis);
    }

    public void jump() {
        JUMP.dispatchForEntity(chrysalis);
    }

    public void land() {
        LAND.dispatchForEntity(chrysalis);
    }

    /**
     * Dropping INTO the crawl. BLOCKING and speed-scaled: a leg torn off plays the SAME clip FASTER, which is why the
     * speed is a parameter rather than a second clip.
     */
    public void crawlDrop(float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            ChrysalisAnimationRefs.CRAWL_DROP_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE,
            AzDispatchMode.REPLAY,
            speed
        ).dispatchForEntity(chrysalis);
    }

    public void crawlRise() {
        AzCommand.<Chrysalis>replay()
            .play(
                AzAlienAnimationUtil.BODY,
                ChrysalisAnimationRefs.CRAWL_RISE_ANIMATION_NAME,
                AzPlayBehaviors.PLAY_ONCE
            )
            .build()
            .dispatchForEntity(chrysalis);
    }

    public void tailAttack() {
        TAILATTACK.dispatchForEntity(chrysalis);
    }

    public void tailAttack(float speed) {
        AzCommand.<Chrysalis>replay()
            .play(AzAlienAnimationUtil.BODY, ChrysalisAnimationRefs.ATTACKTAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(chrysalis);
    }
}
