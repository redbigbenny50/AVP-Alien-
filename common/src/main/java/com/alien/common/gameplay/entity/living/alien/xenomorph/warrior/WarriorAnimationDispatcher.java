package com.alien.common.gameplay.entity.living.alien.xenomorph.warrior;

import com.alien.common.gameplay.entity.dismemberment.MirroredAttackSide;
import com.alien.common.util.AzAlienAnimationUtil;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.blib.api.client.animation.v1.command.policy.AzDispatchMode;

public class WarriorAnimationDispatcher {

    private static final AzCommand<Warrior> CLAW_ATTACK = AzCommand.<Warrior>replay()
        .play(AzAlienAnimationUtil.BODY, WarriorAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Warrior> CLAW_ATTACK_LEFT = AzCommand.<Warrior>replay()
        .play(AzAlienAnimationUtil.BODY, WarriorAnimationRefs.ATTACK_CLAW_LEFT_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Warrior> BITE_ATTACK = AzCommand.<Warrior>replay()
        .play(AzAlienAnimationUtil.BODY, WarriorAnimationRefs.ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Warrior> TAIL_ATTACK = AzCommand.<Warrior>replay()
        .play(AzAlienAnimationUtil.BODY, WarriorAnimationRefs.ATTACK_TAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Warrior> CRAWL = AzCommand.<Warrior>idempotent()
        .play(AzAlienAnimationUtil.BODY, WarriorAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Warrior> CRAWL_HOLD = AzCommand.<Warrior>idempotent()
        .play(AzAlienAnimationUtil.BODY, WarriorAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
        .build();

    private static final AzCommand<Warrior> IDLE = AzCommand.<Warrior>idempotent()
        .play(AzAlienAnimationUtil.BODY, WarriorAnimationRefs.IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Warrior> LUNGE = AzCommand.<Warrior>replay()
        .play(AzAlienAnimationUtil.BODY, WarriorAnimationRefs.LUNGE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Warrior> RUN = AzCommand.<Warrior>idempotent()
        .play(AzAlienAnimationUtil.BODY, WarriorAnimationRefs.RUN_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Warrior> SWIM = AzCommand.<Warrior>idempotent()
        .play(AzAlienAnimationUtil.BODY, WarriorAnimationRefs.SWIM_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Warrior> WALK = AzCommand.<Warrior>idempotent()
        .play(AzAlienAnimationUtil.BODY, WarriorAnimationRefs.WALK_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private final Warrior warrior;

    public WarriorAnimationDispatcher(Warrior warrior) {
        this.warrior = warrior;
    }

    public void crawl() {
        CRAWL.dispatchForEntity(warrior);
    }

    public void crawl(float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            WarriorAnimationRefs.CRAWL_ANIMATION_NAME,
            AzPlayBehaviors.LOOP,
            AzDispatchMode.PLAY_IF_NOT_PLAYING,
            speed
        ).dispatchForEntity(warrior);
    }

    public void crawlHold() {
        CRAWL_HOLD.dispatchForEntity(warrior);
    }

    public void idle() {
        IDLE.dispatchForEntity(warrior);
    }

    public void lunge() {
        LUNGE.dispatchForEntity(warrior);
    }

    public void run() {
        RUN.dispatchForEntity(warrior);
    }

    public void swim() {
        SWIM.dispatchForEntity(warrior);
    }

    public void walk() {
        WALK.dispatchForEntity(warrior);
    }

    public void biteAttack() {
        BITE_ATTACK.dispatchForEntity(warrior);
    }

    public void biteAttack(float speed) {
        AzCommand.<Warrior>replay()
            .play(AzAlienAnimationUtil.BODY, WarriorAnimationRefs.ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(warrior);
    }

    /**
     * A claw swing on whichever arm the warrior still has.
     * <p>
     * ⚠ THE METHOD NAME IS HISTORICAL - kept so no caller has to change, but it is NO LONGER always the right arm.
     * {@link MirroredAttackSide} decides per swing. There is no mirror op at dispatch, so left and right are separate
     * authored clips.
     * </p>
     */
    public void rightClawAttack() {
        clawAttack();
    }

    public void rightClawAttack(float speed) {
        clawAttack(speed);
    }

    /** Claw swing, side chosen from the dismemberment state. */
    public void clawAttack() {
        if (MirroredAttackSide.useLeftArm(warrior)) {
            CLAW_ATTACK_LEFT.dispatchForEntity(warrior);
            return;
        }
        CLAW_ATTACK.dispatchForEntity(warrior);
    }

    public void clawAttack(float speed) {
        var clip = MirroredAttackSide.useLeftArm(warrior)
            ? WarriorAnimationRefs.ATTACK_CLAW_LEFT_ANIMATION_NAME
            : WarriorAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME;

        AzCommand.<Warrior>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(warrior);
    }

    /** Crawling claw swing - same side rule, ground clips. */
    public void crawlAttack() {
        var clip = MirroredAttackSide.useLeftArm(warrior)
            ? WarriorAnimationRefs.CRAWL_ATTACK_LEFT_ANIMATION_NAME
            : WarriorAnimationRefs.CRAWL_ATTACK_RIGHT_ANIMATION_NAME;

        AzCommand.<Warrior>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(warrior);
    }

    /** Crawling bite - not mirrored, only the one clip. */
    public void crawlBiteAttack() {
        AzCommand.<Warrior>replay()
            .play(AzAlienAnimationUtil.BODY, WarriorAnimationRefs.CRAWL_ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(warrior);
    }

    /** Swimming claw swing - same side rule. */
    public void swimAttack() {
        var clip = MirroredAttackSide.useLeftArm(warrior)
            ? WarriorAnimationRefs.SWIM_ATTACK_LEFT_ANIMATION_NAME
            : WarriorAnimationRefs.SWIM_ATTACK_RIGHT_ANIMATION_NAME;

        AzCommand.<Warrior>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(warrior);
    }

    /** Laden gait - the warrior had no carry clip wired before. */
    public void walkCarry() {
        AzCommand.<Warrior>idempotent()
            .play(AzAlienAnimationUtil.BODY, WarriorAnimationRefs.WALK_CARRY_ANIMATION_NAME, AzPlayBehaviors.LOOP)
            .build()
            .dispatchForEntity(warrior);
    }

    /** Getting INTO a crawl. A torn-off leg plays the SAME clip faster - speed, not a different clip. */
    public void crawlDrop(float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            WarriorAnimationRefs.CRAWL_DROP_ANIMATION_NAME,
            AzPlayBehaviors.HOLD_ON_LAST_FRAME,
            AzDispatchMode.PLAY_IF_NOT_PLAYING,
            speed
        ).dispatchForEntity(warrior);
    }

    public void crawlRise() {
        AzCommand.<Warrior>replay()
            .play(AzAlienAnimationUtil.BODY, WarriorAnimationRefs.CRAWL_RISE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(warrior);
    }

    /**
     * Leaving the ground. HOLD_ON_LAST_FRAME is the point - one clip stretches to any airborne duration, so a hop, a
     * running leap and a cliff fall all use it.
     */
    public void jump() {
        AzCommand.<Warrior>replay()
            .play(AzAlienAnimationUtil.BODY, WarriorAnimationRefs.JUMP_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
            .build()
            .dispatchForEntity(warrior);
    }

    /** Touching down. */
    public void land() {
        AzCommand.<Warrior>replay()
            .play(AzAlienAnimationUtil.BODY, WarriorAnimationRefs.LAND_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(warrior);
    }

    public void tailAttack() {
        TAIL_ATTACK.dispatchForEntity(warrior);
    }

    public void tailAttack(float speed) {
        AzCommand.<Warrior>replay()
            .play(AzAlienAnimationUtil.BODY, WarriorAnimationRefs.ATTACK_TAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(warrior);
    }
}
