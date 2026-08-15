package com.alien.common.gameplay.entity.living.alien.xenomorph.crusher;

import com.alien.common.gameplay.entity.dismemberment.MirroredAttackSide;
import com.alien.common.util.AzAlienAnimationUtil;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.blib.api.client.animation.v1.command.policy.AzDispatchMode;

public class CrusherAnimationDispatcher {

    private static final AzCommand<Crusher> BITE_ATTACK = AzCommand.<Crusher>replay()
        .play(AzAlienAnimationUtil.BODY, CrusherAnimationRefs.BITE_ATTACK_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Crusher> CRAWL = AzCommand.<Crusher>idempotent()
        .play(AzAlienAnimationUtil.BODY, CrusherAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Crusher> CRAWL_IDLE = AzCommand.<Crusher>idempotent()
        .play(AzAlienAnimationUtil.BODY, CrusherAnimationRefs.CRAWL_IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Crusher> IDLE = AzCommand.<Crusher>idempotent()
        .play(AzAlienAnimationUtil.BODY, CrusherAnimationRefs.IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Crusher> LEAP = AzCommand.<Crusher>replay()
        .play(AzAlienAnimationUtil.BODY, CrusherAnimationRefs.LEAP_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Crusher> RUN = AzCommand.<Crusher>idempotent()
        .play(AzAlienAnimationUtil.BODY, CrusherAnimationRefs.RUN_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Crusher> SWIM = AzCommand.<Crusher>idempotent()
        .play(AzAlienAnimationUtil.BODY, CrusherAnimationRefs.SWIM_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Crusher> TAIL_ATTACK = AzCommand.<Crusher>replay()
        .play(AzAlienAnimationUtil.BODY, CrusherAnimationRefs.TAIL_ATTACK_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Crusher> WALK = AzCommand.<Crusher>idempotent()
        .play(AzAlienAnimationUtil.BODY, CrusherAnimationRefs.WALK_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Crusher> HEADBUTT_ATTACK = AzCommand.<Crusher>replay()
        .play(
            AzAlienAnimationUtil.BODY,
            CrusherAnimationRefs.ATTACK_HEADBUTT_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE
        )
        .build();

    /** ⚠ LOOPS - the charge runs for as long as the state does, so it must not stop on its own. */
    private static final AzCommand<Crusher> CHARGE = AzCommand.<Crusher>idempotent()
        .play(
            AzAlienAnimationUtil.BODY,
            CrusherAnimationRefs.SPECIAL_ATTACK_CHARGE_ANIMATION_NAME,
            AzPlayBehaviors.LOOP
        )
        .build();

    private static final AzCommand<Crusher> SWIM_ATTACK = AzCommand.<Crusher>replay()
        .play(
            AzAlienAnimationUtil.BODY,
            CrusherAnimationRefs.SWIM_ATTACK_BITE_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE
        )
        .build();

    /** ⚠ HOLD_ON_LAST_FRAME - the jump freezes on its final frame until the ground is regained. */
    private static final AzCommand<Crusher> JUMP = AzCommand.<Crusher>replay()
        .play(AzAlienAnimationUtil.BODY, CrusherAnimationRefs.JUMP_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
        .build();

    private static final AzCommand<Crusher> LAND = AzCommand.<Crusher>replay()
        .play(AzAlienAnimationUtil.BODY, CrusherAnimationRefs.LAND_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private final Crusher crusher;

    public CrusherAnimationDispatcher(Crusher crusher) {
        this.crusher = crusher;
    }

    public void headbuttAttack() {
        HEADBUTT_ATTACK.dispatchForEntity(crusher);
    }

    public void headbuttAttack(float speed) {
        AzCommand.<Crusher>replay()
            .play(
                AzAlienAnimationUtil.BODY,
                CrusherAnimationRefs.ATTACK_HEADBUTT_ANIMATION_NAME,
                AzPlayBehaviors.PLAY_ONCE
            )
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(crusher);
    }

    /**
     * Crawling claw swing. ⚠ MIRRORED even though the crusher has NO standing arm attack - prone it swipes, and the
     * limb rule is the same one every other caste follows.
     */
    public void crawlAttack() {
        crawlAttack(1.0F);
    }

    public void crawlAttack(float speed) {
        var clip = MirroredAttackSide.useLeftArm(crusher)
            ? CrusherAnimationRefs.CRAWL_ATTACK_LEFT_ANIMATION_NAME
            : CrusherAnimationRefs.CRAWL_ATTACK_RIGHT_ANIMATION_NAME;

        AzCommand.<Crusher>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(crusher);
    }

    public void crawlBiteAttack() {
        AzCommand.<Crusher>replay()
            .play(
                AzAlienAnimationUtil.BODY,
                CrusherAnimationRefs.CRAWL_ATTACK_BITE_ANIMATION_NAME,
                AzPlayBehaviors.PLAY_ONCE
            )
            .build()
            .dispatchForEntity(crusher);
    }

    /** The charge proper - its own clip at last, instead of borrowing the run gait. */
    public void charge() {
        CHARGE.dispatchForEntity(crusher);
    }

    public void swimAttack() {
        SWIM_ATTACK.dispatchForEntity(crusher);
    }

    public void jump() {
        JUMP.dispatchForEntity(crusher);
    }

    public void land() {
        LAND.dispatchForEntity(crusher);
    }

    /**
     * Dropping INTO the crawl. BLOCKING and speed-scaled: a leg torn off plays the SAME clip FASTER, which is why the
     * speed is a parameter rather than a second clip.
     */
    public void crawlDrop(float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            CrusherAnimationRefs.CRAWL_DROP_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE,
            AzDispatchMode.REPLAY,
            speed
        ).dispatchForEntity(crusher);
    }

    public void crawlRise() {
        AzCommand.<Crusher>replay()
            .play(
                AzAlienAnimationUtil.BODY,
                CrusherAnimationRefs.CRAWL_RISE_ANIMATION_NAME,
                AzPlayBehaviors.PLAY_ONCE
            )
            .build()
            .dispatchForEntity(crusher);
    }

    public void biteAttack() {
        BITE_ATTACK.dispatchForEntity(crusher);
    }

    public void biteAttack(float speed) {
        AzCommand.<Crusher>replay()
            .play(AzAlienAnimationUtil.BODY, CrusherAnimationRefs.BITE_ATTACK_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(crusher);
    }

    public void crawl() {
        CRAWL.dispatchForEntity(crusher);
    }

    public void crawl(float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            CrusherAnimationRefs.CRAWL_ANIMATION_NAME,
            AzPlayBehaviors.LOOP,
            AzDispatchMode.PLAY_IF_NOT_PLAYING,
            speed
        ).dispatchForEntity(crusher);
    }

    public void crawlIdle() {
        CRAWL_IDLE.dispatchForEntity(crusher);
    }

    public void idle() {
        IDLE.dispatchForEntity(crusher);
    }

    public void lunge() {
        LEAP.dispatchForEntity(crusher);
    }

    public void run() {
        RUN.dispatchForEntity(crusher);
    }

    public void swim() {
        SWIM.dispatchForEntity(crusher);
    }

    public void tailAttack() {
        TAIL_ATTACK.dispatchForEntity(crusher);
    }

    public void tailAttack(float speed) {
        AzCommand.<Crusher>replay()
            .play(AzAlienAnimationUtil.BODY, CrusherAnimationRefs.TAIL_ATTACK_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(crusher);
    }

    public void walk() {
        WALK.dispatchForEntity(crusher);
    }
}
