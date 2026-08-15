package com.alien.common.gameplay.entity.living.alien.xenomorph.carrier;

import com.alien.common.gameplay.entity.dismemberment.MirroredAttackSide;
import com.alien.common.util.AzAlienAnimationUtil;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.blib.api.client.animation.v1.command.policy.AzDispatchMode;

public class CarrierAnimationDispatcher {

    private static final AzCommand<Carrier> IDLE = AzCommand.<Carrier>idempotent()
        .play(AzAlienAnimationUtil.BODY, CarrierAnimationRefs.IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Carrier> WALK = AzCommand.<Carrier>idempotent()
        .play(AzAlienAnimationUtil.BODY, CarrierAnimationRefs.WALK_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    // ⚠⚠ BOTH OF THESE PLAYED THE **SWIM** CLIP. A crawling carrier on dry land swam on the spot, and there was no
    // crawl clip in the art to catch it until now. Copy-paste, and invisible because swim is a plausible-looking loop.
    private static final AzCommand<Carrier> CRAWL = AzCommand.<Carrier>idempotent()
        .play(AzAlienAnimationUtil.BODY, CarrierAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    /** ⭐ The REAL crawl idle loop, not the gait frozen on its last frame. */
    private static final AzCommand<Carrier> CRAWL_HOLD = AzCommand.<Carrier>idempotent()
        .play(AzAlienAnimationUtil.BODY, CarrierAnimationRefs.CRAWL_IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Carrier> CRAWL_ATTACK_BITE = AzCommand.<Carrier>replay()
        .play(
            AzAlienAnimationUtil.BODY,
            CarrierAnimationRefs.CRAWL_ATTACK_BITE_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE
        )
        .build();

    /** ⚠ HOLD_ON_LAST_FRAME so the body STAYS collapsed for the rest of the death window - see the Refs note. */
    private static final AzCommand<Carrier> COLLAPSE_STANDING = AzCommand.<Carrier>replay()
        .play(
            AzAlienAnimationUtil.BODY,
            CarrierAnimationRefs.COLLAPSE_TRIGGER_STANDING_ANIMATION_NAME,
            AzPlayBehaviors.HOLD_ON_LAST_FRAME
        )
        .build();

    private static final AzCommand<Carrier> COLLAPSE_CRAWLING = AzCommand.<Carrier>replay()
        .play(
            AzAlienAnimationUtil.BODY,
            CarrierAnimationRefs.COLLAPSE_TRIGGER_CRAWLING_ANIMATION_NAME,
            AzPlayBehaviors.HOLD_ON_LAST_FRAME
        )
        .build();

    /** ⚠ HOLD_ON_LAST_FRAME - the jump freezes on its final frame until the ground is regained. */
    private static final AzCommand<Carrier> JUMP = AzCommand.<Carrier>replay()
        .play(AzAlienAnimationUtil.BODY, CarrierAnimationRefs.JUMP_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
        .build();

    private static final AzCommand<Carrier> LAND = AzCommand.<Carrier>replay()
        .play(AzAlienAnimationUtil.BODY, CarrierAnimationRefs.LAND_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Carrier> RUN = AzCommand.<Carrier>idempotent()
        .play(AzAlienAnimationUtil.BODY, CarrierAnimationRefs.RUN_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Carrier> SWIM = AzCommand.<Carrier>idempotent()
        .play(AzAlienAnimationUtil.BODY, CarrierAnimationRefs.SWIM_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Carrier> ATTACKBITE = AzCommand.<Carrier>replay()
        .play(AzAlienAnimationUtil.BODY, CarrierAnimationRefs.ATTACKBITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Carrier> ATTACKTAIL = AzCommand.<Carrier>replay()
        .play(AzAlienAnimationUtil.BODY, CarrierAnimationRefs.ATTACKTAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Carrier> SCREAM_ATTACK = AzCommand.<Carrier>replay()
        .play(AzAlienAnimationUtil.BODY, CarrierAnimationRefs.SPECIAL_ATTACK_SCREAM_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Carrier> HOLD_START = AzCommand.<Carrier>replay()
        .play(AzAlienAnimationUtil.BODY, CarrierAnimationRefs.SPECIAL_ATTACK_HOLD_START_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Carrier> HOLD_WAITING = AzCommand.<Carrier>idempotent()
        .play(AzAlienAnimationUtil.BODY, CarrierAnimationRefs.HOLD_WAITING_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private final Carrier carrier;

    public CarrierAnimationDispatcher(Carrier carrier) {
        this.carrier = carrier;
    }

    public void idle() {
        IDLE.dispatchForEntity(carrier);
    }

    public void walk() {
        WALK.dispatchForEntity(carrier);
    }

    public void crawl() {
        CRAWL.dispatchForEntity(carrier);
    }

    public void crawl(float speed) {
        // ⚠⚠ THIS NAMED THE **SWIM** CLIP. A crawling carrier on dry land swam on the spot. The static CRAWL
        // command above was already correct, which is exactly why it survived - the animator calls THIS overload,
        // not that one, so fixing the constant next to it changed nothing visible.
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            CarrierAnimationRefs.CRAWL_ANIMATION_NAME,
            AzPlayBehaviors.LOOP,
            AzDispatchMode.PLAY_IF_NOT_PLAYING,
            speed
        ).dispatchForEntity(carrier);
    }

    public void crawlHold() {
        CRAWL_HOLD.dispatchForEntity(carrier);
    }

    public void run() {
        RUN.dispatchForEntity(carrier);
    }

    public void swim() {
        SWIM.dispatchForEntity(carrier);
    }

    /** ⚠ MIRRORED. The side is decided HERE, once per swing, by the shared seeded helper. */
    public void clawAttack() {
        clawAttack(1.0F);
    }

    public void clawAttack(float speed) {
        var clip = MirroredAttackSide.useLeftArm(carrier)
            ? CarrierAnimationRefs.ATTACK_CLAW_LEFT_ANIMATION_NAME
            : CarrierAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME;

        AzCommand.<Carrier>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(carrier);
    }

    /** Crawling claw swing - same side rule. */
    public void crawlAttack() {
        crawlAttack(1.0F);
    }

    public void crawlAttack(float speed) {
        var clip = MirroredAttackSide.useLeftArm(carrier)
            ? CarrierAnimationRefs.CRAWL_ATTACK_LEFT_ANIMATION_NAME
            : CarrierAnimationRefs.CRAWL_ATTACK_RIGHT_ANIMATION_NAME;

        AzCommand.<Carrier>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(carrier);
    }

    public void crawlBiteAttack() {
        CRAWL_ATTACK_BITE.dispatchForEntity(carrier);
    }

    /** ⚠ MIRRORED in the water too - the carrier is not bite-only there. */
    public void swimAttack() {
        var clip = MirroredAttackSide.useLeftArm(carrier)
            ? CarrierAnimationRefs.SWIM_ATTACK_LEFT_ANIMATION_NAME
            : CarrierAnimationRefs.SWIM_ATTACK_RIGHT_ANIMATION_NAME;

        AzCommand.<Carrier>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(carrier);
    }

    /**
     * The death collapse, as its facehuggers scatter.
     *
     * @param crawling which posture it died in - decided ONCE by the caller and never re-read
     */
    public void collapse(boolean crawling) {
        (crawling ? COLLAPSE_CRAWLING : COLLAPSE_STANDING).dispatchForEntity(carrier);
    }

    public void jump() {
        JUMP.dispatchForEntity(carrier);
    }

    public void land() {
        LAND.dispatchForEntity(carrier);
    }

    /**
     * Dropping INTO the crawl. BLOCKING and speed-scaled: a leg torn off plays the SAME clip FASTER, which is why the
     * speed is a parameter rather than a second clip.
     */
    public void crawlDrop(float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            CarrierAnimationRefs.CRAWL_DROP_ANIMATION_NAME,
            AzPlayBehaviors.PLAY_ONCE,
            AzDispatchMode.REPLAY,
            speed
        ).dispatchForEntity(carrier);
    }

    public void crawlRise() {
        AzCommand.<Carrier>replay()
            .play(
                AzAlienAnimationUtil.BODY,
                CarrierAnimationRefs.CRAWL_RISE_ANIMATION_NAME,
                AzPlayBehaviors.PLAY_ONCE
            )
            .build()
            .dispatchForEntity(carrier);
    }

    public void biteAttack() {
        ATTACKBITE.dispatchForEntity(carrier);
    }

    public void biteAttack(float speed) {
        AzCommand.<Carrier>replay()
            .play(AzAlienAnimationUtil.BODY, CarrierAnimationRefs.ATTACKBITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(carrier);
    }

    public void tailAttack() {
        ATTACKTAIL.dispatchForEntity(carrier);
    }

    public void tailAttack(float speed) {
        AzCommand.<Carrier>replay()
            .play(AzAlienAnimationUtil.BODY, CarrierAnimationRefs.ATTACKTAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(carrier);
    }

    /**
     * ⚠ MIRRORED. It throws a facehugger with an ARM, so it obeys the same limb rule as the claw - lose the left arm
     * and it must throw right. No other caste has a sided SPECIAL attack.
     */
    public void throwAttack() {
        var clip = MirroredAttackSide.useLeftArm(carrier)
            ? CarrierAnimationRefs.SPECIAL_ATTACK_THROW_LEFT_ANIMATION_NAME
            : CarrierAnimationRefs.SPECIAL_ATTACK_THROW_RIGHT_ANIMATION_NAME;

        AzCommand.<Carrier>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(carrier);
    }

    public void screamAttack() {
        SCREAM_ATTACK.dispatchForEntity(carrier);
    }

    public void holdStart() {
        HOLD_START.dispatchForEntity(carrier);
    }

    public void holdWaiting() {
        HOLD_WAITING.dispatchForEntity(carrier);
    }
}
