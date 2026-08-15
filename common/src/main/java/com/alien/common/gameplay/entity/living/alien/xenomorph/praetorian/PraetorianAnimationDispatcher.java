package com.alien.common.gameplay.entity.living.alien.xenomorph.praetorian;

import com.alien.common.gameplay.entity.dismemberment.MirroredAttackSide;
import com.alien.common.util.AzAlienAnimationUtil;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;

/**
 * Praetorian animation dispatch, single-track on {@code BODY}.
 * <p>
 * ⚠ THE MIRRORED METHODS PICK A SIDE THROUGH {@link MirroredAttackSide}, never a fixed clip: one arm gone forces the
 * survivor, both arms alternate for variety, and the seed is the SYNCED attack start time so the choice cannot flip
 * mid-swing and put the limb hitboxes on the opposite arm from the one the player can see moving.
 * </p>
 * <p>
 * ⚠ NO LUNGE METHOD: the praetorian has no pounce clip and its animator has never dispatched one.
 * </p>
 */
public class PraetorianAnimationDispatcher {

    private static final AzCommand<Praetorian> IDLE = AzCommand.<Praetorian>idempotent()
        .play(AzAlienAnimationUtil.BODY, PraetorianAnimationRefs.IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Praetorian> WALK = AzCommand.<Praetorian>idempotent()
        .play(AzAlienAnimationUtil.BODY, PraetorianAnimationRefs.WALK_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Praetorian> RUN = AzCommand.<Praetorian>idempotent()
        .play(AzAlienAnimationUtil.BODY, PraetorianAnimationRefs.RUN_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Praetorian> SWIM = AzCommand.<Praetorian>idempotent()
        .play(AzAlienAnimationUtil.BODY, PraetorianAnimationRefs.SWIM_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Praetorian> CRAWL = AzCommand.<Praetorian>idempotent()
        .play(AzAlienAnimationUtil.BODY, PraetorianAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    /** ⚠ NOW A REAL AUTHORED CLIP. This used to hold the moving crawl on its last frame; the art ships a loop. */
    private static final AzCommand<Praetorian> CRAWL_IDLE = AzCommand.<Praetorian>idempotent()
        .play(AzAlienAnimationUtil.BODY, PraetorianAnimationRefs.CRAWL_IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private final Praetorian praetorian;

    public PraetorianAnimationDispatcher(Praetorian praetorian) {
        this.praetorian = praetorian;
    }

    public void idle() {
        IDLE.dispatchForEntity(praetorian);
    }

    public void walk() {
        WALK.dispatchForEntity(praetorian);
    }

    public void run() {
        RUN.dispatchForEntity(praetorian);
    }

    public void swim() {
        SWIM.dispatchForEntity(praetorian);
    }

    public void crawl() {
        CRAWL.dispatchForEntity(praetorian);
    }

    public void crawl(float speed) {
        AzCommand.<Praetorian>idempotent()
            .play(AzAlienAnimationUtil.BODY, PraetorianAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.LOOP)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(praetorian);
    }

    public void crawlHold() {
        CRAWL_IDLE.dispatchForEntity(praetorian);
    }

    /**
     * Getting DOWN into the crawl. Blocking and speed-scaled: [stated] a leg torn off plays the SAME clip FASTER, so
     * the caller passes the speed rather than picking a different animation.
     */
    public void crawlDrop(float speed) {
        AzCommand.<Praetorian>replay()
            .play(AzAlienAnimationUtil.BODY, PraetorianAnimationRefs.CRAWL_DROP_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(praetorian);
    }

    public void crawlRise() {
        AzCommand.<Praetorian>replay()
            .play(AzAlienAnimationUtil.BODY, PraetorianAnimationRefs.CRAWL_RISE_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
            .build()
            .dispatchForEntity(praetorian);
    }

    /** Leaving the ground. HOLDS on its last frame so one clip covers any airborne duration. */
    public void jump() {
        AzCommand.<Praetorian>replay()
            .play(AzAlienAnimationUtil.BODY, PraetorianAnimationRefs.JUMP_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
            .build()
            .dispatchForEntity(praetorian);
    }

    public void land() {
        AzCommand.<Praetorian>replay()
            .play(AzAlienAnimationUtil.BODY, PraetorianAnimationRefs.LAND_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(praetorian);
    }

    public void biteAttack() {
        biteAttack(1.0F);
    }

    public void biteAttack(float speed) {
        AzCommand.<Praetorian>replay()
            .play(AzAlienAnimationUtil.BODY, PraetorianAnimationRefs.ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(praetorian);
    }

    public void tailAttack() {
        tailAttack(1.0F);
    }

    public void tailAttack(float speed) {
        AzCommand.<Praetorian>replay()
            .play(AzAlienAnimationUtil.BODY, PraetorianAnimationRefs.ATTACK_TAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(praetorian);
    }

    /** Mirrored claw. */
    public void clawAttack() {
        clawAttack(1.0F);
    }

    public void clawAttack(float speed) {
        var clip = MirroredAttackSide.useLeftArm(praetorian)
            ? PraetorianAnimationRefs.ATTACK_CLAW_LEFT_ANIMATION_NAME
            : PraetorianAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME;

        AzCommand.<Praetorian>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(praetorian);
    }

    /** ⚠ HISTORICAL NAME, kept so no caller changes - it is side-PICKED, not right-handed. */
    public void rightClawAttack() {
        clawAttack(1.0F);
    }

    public void rightClawAttack(float speed) {
        clawAttack(speed);
    }

    /** Mirrored backhand. ⚠ NO ATTACK TYPE DRIVES THIS YET - the art exists, the trigger does not. */
    public void backhandAttack() {
        backhandAttack(1.0F);
    }

    public void backhandAttack(float speed) {
        var clip = MirroredAttackSide.useLeftArm(praetorian)
            ? PraetorianAnimationRefs.ATTACK_BACKHAND_LEFT_ANIMATION_NAME
            : PraetorianAnimationRefs.ATTACK_BACKHAND_RIGHT_ANIMATION_NAME;

        AzCommand.<Praetorian>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(praetorian);
    }

    /** Swimming bite - single clip, NOT mirrored. */
    public void swimAttack() {
        swimAttack(1.0F);
    }

    public void swimAttack(float speed) {
        AzCommand.<Praetorian>replay()
            .play(AzAlienAnimationUtil.BODY, PraetorianAnimationRefs.SWIM_ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(praetorian);
    }

    /** Mirrored crawling swipe. ⚠ Needs restrictToCrawlAttacks + a crawl AttackType to fire. */
    public void crawlAttack() {
        crawlAttack(1.0F);
    }

    public void crawlAttack(float speed) {
        var clip = MirroredAttackSide.useLeftArm(praetorian)
            ? PraetorianAnimationRefs.CRAWL_ATTACK_LEFT_ANIMATION_NAME
            : PraetorianAnimationRefs.CRAWL_ATTACK_RIGHT_ANIMATION_NAME;

        AzCommand.<Praetorian>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(praetorian);
    }

    /** Crawling bite - not mirrored. */
    public void crawlBiteAttack() {
        crawlBiteAttack(1.0F);
    }

    public void crawlBiteAttack(float speed) {
        AzCommand.<Praetorian>replay()
            .play(AzAlienAnimationUtil.BODY, PraetorianAnimationRefs.CRAWL_ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(praetorian);
    }
}
