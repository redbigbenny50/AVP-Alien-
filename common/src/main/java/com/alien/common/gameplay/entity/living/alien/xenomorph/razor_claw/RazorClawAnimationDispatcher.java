package com.alien.common.gameplay.entity.living.alien.xenomorph.razor_claw;

import com.alien.common.gameplay.entity.dismemberment.MirroredAttackSide;
import com.alien.common.util.AzAlienAnimationUtil;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.blib.api.client.animation.v1.command.policy.AzDispatchMode;

public class RazorClawAnimationDispatcher {

    private static final AzCommand<RazorClaw> BITEATTACK = AzCommand.<RazorClaw>replay()
        .play(AzAlienAnimationUtil.BODY, RazorClawAnimationRefs.ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<RazorClaw> TAILATTACK = AzCommand.<RazorClaw>replay()
        .play(AzAlienAnimationUtil.BODY, RazorClawAnimationRefs.ATTACK_TAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<RazorClaw> SWIM_ATTACK = AzCommand.<RazorClaw>replay()
        .play(AzAlienAnimationUtil.BODY, RazorClawAnimationRefs.SWIM_ATTACK_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<RazorClaw> IDLE = AzCommand.<RazorClaw>idempotent()
        .play(AzAlienAnimationUtil.BODY, "idle", AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<RazorClaw> RUN = AzCommand.<RazorClaw>idempotent()
        .play(AzAlienAnimationUtil.BODY, "run", AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<RazorClaw> SWIM = AzCommand.<RazorClaw>idempotent()
        .play(AzAlienAnimationUtil.BODY, "swim", AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<RazorClaw> CRAWL = AzCommand.<RazorClaw>idempotent()
        .play(AzAlienAnimationUtil.BODY, "crawl", AzPlayBehaviors.LOOP)
        .build();

    /** ⭐ Now the REAL crawl.idle loop, not the crawl gait frozen on its last frame. */
    private static final AzCommand<RazorClaw> CRAWL_HOLD = AzCommand.<RazorClaw>idempotent()
        .play(AzAlienAnimationUtil.BODY, RazorClawAnimationRefs.CRAWL_IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<RazorClaw> WALK = AzCommand.<RazorClaw>idempotent()
        .play(AzAlienAnimationUtil.BODY, "walk", AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<RazorClaw> CRAWL_ATTACK_BITE = AzCommand.<RazorClaw>replay()
        .play(
            AzAlienAnimationUtil.BODY,
            RazorClawAnimationRefs.CRAWL_ATTACK_BITE_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE
        )
        .build();

    /** ⚠ HOLD_ON_LAST_FRAME - the jump freezes on its final frame until the ground is regained. */
    private static final AzCommand<RazorClaw> JUMP = AzCommand.<RazorClaw>replay()
        .play(AzAlienAnimationUtil.BODY, RazorClawAnimationRefs.JUMP_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
        .build();

    private static final AzCommand<RazorClaw> LAND = AzCommand.<RazorClaw>replay()
        .play(AzAlienAnimationUtil.BODY, RazorClawAnimationRefs.LAND_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<RazorClaw> QUICK_ATTACK = AzCommand.<RazorClaw>replay()
        .play(
            AzAlienAnimationUtil.BODY,
            RazorClawAnimationRefs.ATTACK_QUICK_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE
        )
        .build();

    private static final AzCommand<RazorClaw> DODGE = AzCommand.<RazorClaw>replay()
        .play(AzAlienAnimationUtil.BODY, RazorClawAnimationRefs.DODGE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private final RazorClaw razorClaw;

    public RazorClawAnimationDispatcher(RazorClaw razorClaw) {
        this.razorClaw = razorClaw;
    }

    public void idle() {
        IDLE.dispatchForEntity(razorClaw);
    }

    public void run() {
        RUN.dispatchForEntity(razorClaw);
    }

    public void swim() {
        SWIM.dispatchForEntity(razorClaw);
    }

    public void crawl() {
        CRAWL.dispatchForEntity(razorClaw);
    }

    public void crawl(float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            "crawl",
            AzPlayBehaviors.LOOP,
            AzDispatchMode.PLAY_IF_NOT_PLAYING,
            speed
        ).dispatchForEntity(razorClaw);
    }

    public void crawlHold() {
        CRAWL_HOLD.dispatchForEntity(razorClaw);
    }

    public void walk() {
        WALK.dispatchForEntity(razorClaw);
    }

    public void biteAttack() {
        BITEATTACK.dispatchForEntity(razorClaw);
    }

    public void biteAttack(float speed) {
        AzCommand.<RazorClaw>replay()
            .play(AzAlienAnimationUtil.BODY, RazorClawAnimationRefs.ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(razorClaw);
    }

    /**
     * ⚠ MIRRORED. Kept under the old NAME so no caller changes, but it is no longer literally the right arm - the side
     * is decided HERE, once per swing, by the shared seeded helper.
     */
    public void rightClawAttack() {
        rightClawAttack(1.0F);
    }

    public void rightClawAttack(float speed) {
        var clip = MirroredAttackSide.useLeftArm(razorClaw)
            ? RazorClawAnimationRefs.ATTACK_CLAW_LEFT_ANIMATION_NAME
            : RazorClawAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME;

        AzCommand.<RazorClaw>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(razorClaw);
    }

    /** Crawling claw swing - same side rule. */
    public void crawlAttack() {
        crawlAttack(1.0F);
    }

    public void crawlAttack(float speed) {
        var clip = MirroredAttackSide.useLeftArm(razorClaw)
            ? RazorClawAnimationRefs.CRAWL_ATTACK_LEFT_ANIMATION_NAME
            : RazorClawAnimationRefs.CRAWL_ATTACK_RIGHT_ANIMATION_NAME;

        AzCommand.<RazorClaw>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(razorClaw);
    }

    /**
     * One blow of the flurry. ⚠ REPLAY mode is load-bearing: the executor calls this once per repeat and the clip must
     * restart from frame 0 each time, which an idempotent dispatch would refuse to do.
     */
    public void quickAttack() {
        QUICK_ATTACK.dispatchForEntity(razorClaw);
    }

    public void quickAttack(float speed) {
        AzCommand.<RazorClaw>replay()
            .play(
                AzAlienAnimationUtil.BODY,
                RazorClawAnimationRefs.ATTACK_QUICK_ANIMATION_NAME,
                AzPlayBehaviors.PLAY_ONCE
            )
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(razorClaw);
    }

    public void dodge() {
        DODGE.dispatchForEntity(razorClaw);
    }

    /**
     * ⚠ MIRRORED. The armour-piercing charge leads with one arm, so it obeys the same limb rule as the claw - lose an
     * arm and it charges off the other.
     */
    public void chargeAttack() {
        chargeAttack(1.0F);
    }

    public void chargeAttack(float speed) {
        var clip = MirroredAttackSide.useLeftArm(razorClaw)
            ? RazorClawAnimationRefs.SPECIAL_ATTACK_CHARGE_LEFT_ANIMATION_NAME
            : RazorClawAnimationRefs.SPECIAL_ATTACK_CHARGE_RIGHT_ANIMATION_NAME;

        AzCommand.<RazorClaw>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(razorClaw);
    }

    public void crawlBiteAttack() {
        CRAWL_ATTACK_BITE.dispatchForEntity(razorClaw);
    }

    public void jump() {
        JUMP.dispatchForEntity(razorClaw);
    }

    public void land() {
        LAND.dispatchForEntity(razorClaw);
    }

    /**
     * Dropping INTO the crawl. BLOCKING and speed-scaled: a leg torn off plays the SAME clip FASTER, which is why the
     * speed is a parameter rather than a second clip.
     */
    public void crawlDrop(float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            RazorClawAnimationRefs.CRAWL_DROP_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE,
            AzDispatchMode.REPLAY,
            speed
        ).dispatchForEntity(razorClaw);
    }

    public void crawlRise() {
        AzCommand.<RazorClaw>replay()
            .play(
                AzAlienAnimationUtil.BODY,
                RazorClawAnimationRefs.CRAWL_RISE_ANIMATION_NAME,
                AzPlayBehaviors.PLAY_ONCE
            )
            .build()
            .dispatchForEntity(razorClaw);
    }

    public void tailAttack() {
        TAILATTACK.dispatchForEntity(razorClaw);
    }

    public void tailAttack(float speed) {
        AzCommand.<RazorClaw>replay()
            .play(AzAlienAnimationUtil.BODY, RazorClawAnimationRefs.ATTACK_TAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(razorClaw);
    }

    public void swimAttack() {
        SWIM_ATTACK.dispatchForEntity(razorClaw);
    }

    public void swimAttack(float speed) {
        AzCommand.<RazorClaw>replay()
            .play(AzAlienAnimationUtil.BODY, RazorClawAnimationRefs.SWIM_ATTACK_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(razorClaw);
    }

    /**
     * ⚠ MIRRORED. The AOE spin has a left and a right clip, so it obeys the same limb rule as the claw - lose an arm
     * and it spins the other way. It is a TRIGGERED special rather than a regular attack, but the arm is still the
     * thing doing the work.
     */
    public void specialAttackSpin() {
        specialAttackSpin(1.0F);
    }

    public void specialAttackSpin(float speed) {
        var clip = MirroredAttackSide.useLeftArm(razorClaw)
            ? RazorClawAnimationRefs.SPECIAL_ATTACK_SPIN_LEFT_ANIMATION_NAME
            : RazorClawAnimationRefs.SPECIAL_ATTACK_SPIN_RIGHT_ANIMATION_NAME;

        AzCommand.<RazorClaw>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(razorClaw);
    }
}
