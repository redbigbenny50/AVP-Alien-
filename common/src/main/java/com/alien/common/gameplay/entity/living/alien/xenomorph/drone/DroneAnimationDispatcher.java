package com.alien.common.gameplay.entity.living.alien.xenomorph.drone;

import com.alien.common.util.AzAlienAnimationUtil;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.blib.api.client.animation.v1.command.policy.AzDispatchMode;

public class DroneAnimationDispatcher {

    /** The dig loop plays at 70% speed, matching the queen's dig loops - slowed in-game, not in the model. */
    private static final float DIG_ANIMATION_SPEED = 0.7F;

    private static final AzCommand<Drone> CLAW_ATTACK = AzCommand.<Drone>replay()
        .play(AzAlienAnimationUtil.BODY, DroneAnimationRefs.FULL_ATTACK_CLAW_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Drone> BITE_ATTACK = AzCommand.<Drone>replay()
        .play(AzAlienAnimationUtil.BODY, DroneAnimationRefs.FULL_ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Drone> TAIL_ATTACK = AzCommand.<Drone>replay()
        .play(AzAlienAnimationUtil.BODY, DroneAnimationRefs.FULL_ATTACK_TAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Drone> CRAWL = AzCommand.<Drone>idempotent()
        .play(AzAlienAnimationUtil.BODY, DroneAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Drone> CRAWL_HOLD = AzCommand.<Drone>idempotent()
        .play(AzAlienAnimationUtil.BODY, DroneAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
        .build();

    private static final AzCommand<Drone> IDLE = AzCommand.<Drone>idempotent()
        .play(AzAlienAnimationUtil.BODY, DroneAnimationRefs.IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Drone> LUNGE = AzCommand.<Drone>replay()
        .play(AzAlienAnimationUtil.BODY, DroneAnimationRefs.LUNGE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Drone> RUN = AzCommand.<Drone>idempotent()
        .play(AzAlienAnimationUtil.BODY, DroneAnimationRefs.RUN_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Drone> SWIM = AzCommand.<Drone>idempotent()
        .play(AzAlienAnimationUtil.BODY, DroneAnimationRefs.SWIM_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Drone> WALK = AzCommand.<Drone>idempotent()
        .play(AzAlienAnimationUtil.BODY, DroneAnimationRefs.WALK_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Drone> WALK_CARRY = AzCommand.<Drone>idempotent()
        .play(AzAlienAnimationUtil.BODY, DroneAnimationRefs.WALK_CARRY_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private final Drone drone;

    public DroneAnimationDispatcher(Drone drone) {
        this.drone = drone;
    }

    public void crawl() {
        CRAWL.dispatchForEntity(drone);
    }

    public void crawl(float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            DroneAnimationRefs.CRAWL_ANIMATION_NAME,
            AzPlayBehaviors.LOOP,
            AzDispatchMode.PLAY_IF_NOT_PLAYING,
            speed
        ).dispatchForEntity(drone);
    }

    public void crawlHold() {
        CRAWL_HOLD.dispatchForEntity(drone);
    }

    public void idle() {
        IDLE.dispatchForEntity(drone);
    }

    public void lunge() {
        LUNGE.dispatchForEntity(drone);
    }

    public void run() {
        RUN.dispatchForEntity(drone);
    }

    public void swim() {
        SWIM.dispatchForEntity(drone);
    }

    public void walk() {
        WALK.dispatchForEntity(drone);
    }

    /** Laden gait: an egg on the back, or a host held to the chest. */
    public void walkCarry() {
        WALK_CARRY.dispatchForEntity(drone);
    }

    /**
     * Digging gait, at 70% speed. Nothing calls this yet - see {@link DroneAnimationRefs#WALK_DIG_ANIMATION_NAME}. It
     * is here so the carve economy can simply call it.
     */
    public void walkDig() {
        walkDig(DIG_ANIMATION_SPEED);
    }

    /**
     * Digging gait at an explicit speed: the carve economy's placers dig at 50% (slower than the diggers' 70%), per the
     * construction design §5.
     */
    public void walkDig(float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            DroneAnimationRefs.WALK_DIG_ANIMATION_NAME,
            AzPlayBehaviors.LOOP,
            AzDispatchMode.PLAY_IF_NOT_PLAYING,
            speed
        ).dispatchForEntity(drone);
    }

    public void biteAttack() {
        BITE_ATTACK.dispatchForEntity(drone);
    }

    public void biteAttack(float speed) {
        AzCommand.<Drone>replay()
            .play(AzAlienAnimationUtil.BODY, DroneAnimationRefs.FULL_ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(drone);
    }

    public void rightClawAttack() {
        CLAW_ATTACK.dispatchForEntity(drone);
    }

    public void rightClawAttack(float speed) {
        AzCommand.<Drone>replay()
            .play(AzAlienAnimationUtil.BODY, DroneAnimationRefs.FULL_ATTACK_CLAW_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(drone);
    }

    public void tailAttack() {
        TAIL_ATTACK.dispatchForEntity(drone);
    }

    public void tailAttack(float speed) {
        AzCommand.<Drone>replay()
            .play(AzAlienAnimationUtil.BODY, DroneAnimationRefs.FULL_ATTACK_TAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(drone);
    }
}
