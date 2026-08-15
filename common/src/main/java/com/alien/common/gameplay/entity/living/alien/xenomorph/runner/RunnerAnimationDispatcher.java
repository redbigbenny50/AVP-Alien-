package com.alien.common.gameplay.entity.living.alien.xenomorph.runner;

import com.alien.common.gameplay.entity.dismemberment.MirroredAttackSide;
import com.alien.common.util.AzAlienAnimationUtil;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.blib.api.client.animation.v1.command.policy.AzDispatchMode;

public class RunnerAnimationDispatcher {

    private static final AzCommand<Runner> ARM_ATTACK = AzCommand.<Runner>replay()
        .play(AzAlienAnimationUtil.BODY, RunnerAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Runner> ARM_ATTACK_LEFT = AzCommand.<Runner>replay()
        .play(AzAlienAnimationUtil.BODY, RunnerAnimationRefs.ATTACK_CLAW_LEFT_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Runner> BITE_ATTACK = AzCommand.<Runner>replay()
        .play(AzAlienAnimationUtil.BODY, RunnerAnimationRefs.ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Runner> TAIL_ATTACK = AzCommand.<Runner>replay()
        .play(AzAlienAnimationUtil.BODY, RunnerAnimationRefs.ATTACK_TAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Runner> CRAWL = AzCommand.<Runner>idempotent()
        .play(AzAlienAnimationUtil.BODY, RunnerAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Runner> CRAWL_HOLD = AzCommand.<Runner>idempotent()
        .play(AzAlienAnimationUtil.BODY, RunnerAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
        .build();

    private static final AzCommand<Runner> IDLE = AzCommand.<Runner>idempotent()
        .play(AzAlienAnimationUtil.BODY, RunnerAnimationRefs.IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Runner> LUNGE = AzCommand.<Runner>replay()
        .play(AzAlienAnimationUtil.BODY, RunnerAnimationRefs.LUNGE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Runner> RUN = AzCommand.<Runner>idempotent()
        .play(AzAlienAnimationUtil.BODY, RunnerAnimationRefs.RUN_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Runner> SWIM = AzCommand.<Runner>idempotent()
        .play(AzAlienAnimationUtil.BODY, RunnerAnimationRefs.SWIM_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Runner> WALK = AzCommand.<Runner>idempotent()
        .play(AzAlienAnimationUtil.BODY, RunnerAnimationRefs.WALK_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private final Runner runner;

    public RunnerAnimationDispatcher(Runner runner) {
        this.runner = runner;
    }

    public void crawl() {
        CRAWL.dispatchForEntity(runner);
    }

    public void crawl(float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            RunnerAnimationRefs.CRAWL_ANIMATION_NAME,
            AzPlayBehaviors.LOOP,
            AzDispatchMode.PLAY_IF_NOT_PLAYING,
            speed
        ).dispatchForEntity(runner);
    }

    public void crawlHold() {
        CRAWL_HOLD.dispatchForEntity(runner);
    }

    public void idle() {
        IDLE.dispatchForEntity(runner);
    }

    public void lunge() {
        LUNGE.dispatchForEntity(runner);
    }

    public void run() {
        RUN.dispatchForEntity(runner);
    }

    public void swim() {
        SWIM.dispatchForEntity(runner);
    }

    public void walk() {
        WALK.dispatchForEntity(runner);
    }

    /** Crew gait. Speed matches the drone convention: diggers at the default, placers slower. */
    public void walkDig() {
        walkDig(0.7F);
    }

    public void walkDig(float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            RunnerAnimationRefs.WALK_DIG_ANIMATION_NAME,
            AzPlayBehaviors.LOOP,
            AzDispatchMode.PLAY_IF_NOT_PLAYING,
            speed
        ).dispatchForEntity(runner);
    }

    public void biteAttack() {
        BITE_ATTACK.dispatchForEntity(runner);
    }

    public void biteAttack(float speed) {
        AzCommand.<Runner>replay()
            .play(AzAlienAnimationUtil.BODY, RunnerAnimationRefs.ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(runner);
    }

    public void rightClawAttack() {
        ARM_ATTACK.dispatchForEntity(runner);
    }

    public void rightClawAttack(float speed) {
        AzCommand.<Runner>replay()
            .play(AzAlienAnimationUtil.BODY, RunnerAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(runner);
    }

    public void tailAttackQuad() {
        TAIL_ATTACK.dispatchForEntity(runner);
    }

    public void tailAttackQuad(float speed) {
        AzCommand.<Runner>replay()
            .play(AzAlienAnimationUtil.BODY, RunnerAnimationRefs.ATTACK_TAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(runner);
    }

    /**
     * Claw swing, side chosen from the dismemberment state. The runner's "arm" attack is a claw like everyone else's.
     */
    public void clawAttack() {
        if (MirroredAttackSide.useLeftArm(runner)) {
            ARM_ATTACK_LEFT.dispatchForEntity(runner);
            return;
        }
        ARM_ATTACK.dispatchForEntity(runner);
    }

    public void clawAttack(float speed) {
        var clip = MirroredAttackSide.useLeftArm(runner)
            ? RunnerAnimationRefs.ATTACK_CLAW_LEFT_ANIMATION_NAME
            : RunnerAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME;

        AzCommand.<Runner>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(runner);
    }

    /** Crawling claw swing - same side rule. */
    public void crawlAttack() {
        var clip = MirroredAttackSide.useLeftArm(runner)
            ? RunnerAnimationRefs.CRAWL_ATTACK_LEFT_ANIMATION_NAME
            : RunnerAnimationRefs.CRAWL_ATTACK_RIGHT_ANIMATION_NAME;

        AzCommand.<Runner>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(runner);
    }

    /** Crawling bite - not mirrored. */
    public void crawlBiteAttack() {
        AzCommand.<Runner>replay()
            .play(AzAlienAnimationUtil.BODY, RunnerAnimationRefs.CRAWL_ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(runner);
    }

    /**
     * ⚠ THE RUNNER BITES IN THE WATER - there is no mirrored swim pair for this caste, so this deliberately does NOT
     * consult {@link MirroredAttackSide}. [stated] "they dont have a left and right swim attack only a bite".
     */
    public void swimAttack() {
        AzCommand.<Runner>replay()
            .play(AzAlienAnimationUtil.BODY, RunnerAnimationRefs.SWIM_ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(runner);
    }

    /**
     * Leaving the ground. HOLD_ON_LAST_FRAME so one clip covers any airborne duration.
     * <p>
     * ⚠ NOT the lunge. {@code lunge()} is the POUNCE state's animation and is dispatched from {@code isLunging};
     * borrowing it for airborne once made a runner walking off a ledge play its pounce.
     * </p>
     */
    public void jump() {
        AzCommand.<Runner>replay()
            .play(AzAlienAnimationUtil.BODY, RunnerAnimationRefs.JUMP_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
            .build()
            .dispatchForEntity(runner);
    }

    /** Touching down. */
    public void land() {
        AzCommand.<Runner>replay()
            .play(AzAlienAnimationUtil.BODY, RunnerAnimationRefs.LAND_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(runner);
    }
}
