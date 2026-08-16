package com.alien.common.gameplay.entity.living.alien.xenomorph.drone;

import com.alien.common.gameplay.entity.dismemberment.MirroredAttackSide;
import com.alien.common.util.AzAlienAnimationUtil;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.blib.api.client.animation.v1.command.policy.AzDispatchMode;

public class DroneAnimationDispatcher {

    /** The dig loop plays at 70% speed, matching the queen's dig loops - slowed in-game, not in the model. */
    private static final float DIG_ANIMATION_SPEED = 0.7F;

    private static final AzCommand<Drone> CLAW_ATTACK = AzCommand.<Drone>replay()
        .play(AzAlienAnimationUtil.BODY, DroneAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Drone> BITE_ATTACK = AzCommand.<Drone>replay()
        .play(AzAlienAnimationUtil.BODY, DroneAnimationRefs.ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Drone> TAIL_ATTACK = AzCommand.<Drone>replay()
        .play(AzAlienAnimationUtil.BODY, DroneAnimationRefs.ATTACK_TAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
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

    private static final AzCommand<Drone> CLAW_ATTACK_LEFT = AzCommand.<Drone>replay()
        .play(AzAlienAnimationUtil.BODY, DroneAnimationRefs.ATTACK_CLAW_LEFT_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

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
            .play(AzAlienAnimationUtil.BODY, DroneAnimationRefs.ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(drone);
    }

    /**
     * A claw swing on whichever arm the drone still has.
     * <p>
     * ⚠ THE METHOD NAME IS HISTORICAL - it is kept so no caller has to change, but it is NO LONGER always the right
     * arm. {@link MirroredAttackSide} decides per swing: a torn-off arm forces the survivor, and an intact drone
     * alternates for variety. There is no mirror operation at dispatch, so left and right are separate authored clips.
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
        if (MirroredAttackSide.useLeftArm(drone)) {
            CLAW_ATTACK_LEFT.dispatchForEntity(drone);
            return;
        }
        CLAW_ATTACK.dispatchForEntity(drone);
    }

    public void clawAttack(float speed) {
        var clip = MirroredAttackSide.useLeftArm(drone)
            ? DroneAnimationRefs.ATTACK_CLAW_LEFT_ANIMATION_NAME
            : DroneAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME;

        AzCommand.<Drone>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(drone);
    }

    /** Crawling claw swing - same side rule, ground clips. */
    public void crawlAttack() {
        var clip = MirroredAttackSide.useLeftArm(drone)
            ? DroneAnimationRefs.CRAWL_ATTACK_LEFT_ANIMATION_NAME
            : DroneAnimationRefs.CRAWL_ATTACK_RIGHT_ANIMATION_NAME;

        AzCommand.<Drone>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(drone);
    }

    /** Crawling bite - not mirrored, there is only the one clip. */
    public void crawlBiteAttack() {
        AzCommand.<Drone>replay()
            .play(AzAlienAnimationUtil.BODY, DroneAnimationRefs.CRAWL_ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(drone);
    }

    /** Swimming claw swing - same side rule again. */
    public void swimAttack() {
        var clip = MirroredAttackSide.useLeftArm(drone)
            ? DroneAnimationRefs.SWIM_ATTACK_LEFT_ANIMATION_NAME
            : DroneAnimationRefs.SWIM_ATTACK_RIGHT_ANIMATION_NAME;

        AzCommand.<Drone>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(drone);
    }

    /**
     * Leaving the ground.
     * <p>
     * ⚠ HOLD_ON_LAST_FRAME IS THE WHOLE POINT, and it is deliberate authoring, not a shortcut: [stated] "the jump
     * pauses on the last frame on purpose so it can be extended however long it needs to until it lands." One clip
     * therefore covers a hop over a two-block rise, a four-block running leap, and a fall off a cliff - the airborne
     * pose simply holds for as long as the drone is in the air.
     * </p>
     */
    public void jump() {
        AzCommand.<Drone>replay()
            .play(AzAlienAnimationUtil.BODY, DroneAnimationRefs.JUMP_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
            .build()
            .dispatchForEntity(drone);
    }

    /** Touching down. Plays once on the tick the drone regains the ground. */
    public void land() {
        AzCommand.<Drone>replay()
            .play(AzAlienAnimationUtil.BODY, DroneAnimationRefs.LAND_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(drone);
    }

    /** Getting INTO a crawl. A torn-off leg plays the SAME clip faster - speed, not a different clip. */
    public void crawlDrop(float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            DroneAnimationRefs.CRAWL_DROP_ANIMATION_NAME,
            AzPlayBehaviors.HOLD_ON_LAST_FRAME,
            AzDispatchMode.PLAY_IF_NOT_PLAYING,
            speed
        ).dispatchForEntity(drone);
    }

    /** Getting back up. */
    public void crawlRise() {
        AzCommand.<Drone>replay()
            .play(AzAlienAnimationUtil.BODY, DroneAnimationRefs.CRAWL_RISE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(drone);
    }

    public void tailAttack() {
        TAIL_ATTACK.dispatchForEntity(drone);
    }

    public void tailAttack(float speed) {
        AzCommand.<Drone>replay()
            .play(AzAlienAnimationUtil.BODY, DroneAnimationRefs.ATTACK_TAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(drone);
    }
}
