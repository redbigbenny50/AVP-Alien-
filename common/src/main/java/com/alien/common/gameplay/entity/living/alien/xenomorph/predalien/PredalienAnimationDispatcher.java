package com.alien.common.gameplay.entity.living.alien.xenomorph.predalien;

import com.alien.common.gameplay.entity.dismemberment.MirroredAttackSide;
import com.alien.common.util.AzAlienAnimationUtil;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;

/**
 * Predalien animation dispatch, single-track on {@code BODY}.
 * <p>
 * ⚠ THE MIRRORED METHODS PICK A SIDE THROUGH {@link MirroredAttackSide}, never a fixed clip: one arm gone forces the
 * survivor, both arms alternate for variety, and the seed is the SYNCED attack start time so the choice cannot flip
 * mid-swing and put the limb hitboxes on the opposite arm from the one the player can see moving.
 * </p>
 * <p>
 * ⭐ It NOW has a {@code crawl.attack.bite} clip - his re-export added one, so a prone bite no longer borrows the
 * standing clip.
 * </p>
 */
public class PredalienAnimationDispatcher {

    private static final AzCommand<Predalien> IDLE = AzCommand.<Predalien>idempotent()
        .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Predalien> WALK = AzCommand.<Predalien>idempotent()
        .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.WALK_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Predalien> RUN = AzCommand.<Predalien>idempotent()
        .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.RUN_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Predalien> SWIM = AzCommand.<Predalien>idempotent()
        .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.SWIM_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Predalien> CRAWL = AzCommand.<Predalien>idempotent()
        .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    /** ⚠ NOW A REAL AUTHORED CLIP. This used to hold the moving crawl on its last frame; the art ships a loop. */
    private static final AzCommand<Predalien> CRAWL_IDLE = AzCommand.<Predalien>idempotent()
        .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.CRAWL_IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private final Predalien predalien;

    public PredalienAnimationDispatcher(Predalien predalien) {
        this.predalien = predalien;
    }

    public void idle() {
        IDLE.dispatchForEntity(predalien);
    }

    public void walk() {
        WALK.dispatchForEntity(predalien);
    }

    public void run() {
        RUN.dispatchForEntity(predalien);
    }

    public void swim() {
        SWIM.dispatchForEntity(predalien);
    }

    public void crawl() {
        CRAWL.dispatchForEntity(predalien);
    }

    public void crawl(float speed) {
        AzCommand.<Predalien>idempotent()
            .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.LOOP)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(predalien);
    }

    public void crawlHold() {
        CRAWL_IDLE.dispatchForEntity(predalien);
    }

    /**
     * Getting DOWN into the crawl. Blocking and speed-scaled: [stated] a leg torn off plays the SAME clip FASTER, so
     * the caller passes the speed rather than picking a different animation.
     */
    public void crawlDrop(float speed) {
        AzCommand.<Predalien>replay()
            .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.CRAWL_DROP_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(predalien);
    }

    public void crawlRise() {
        AzCommand.<Predalien>replay()
            .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.CRAWL_RISE_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
            .build()
            .dispatchForEntity(predalien);
    }

    /** The POUNCE. Driven by {@code isLunging} - not an airborne pose, and never to be borrowed as one. */
    public void lunge() {
        AzCommand.<Predalien>replay()
            .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.LUNGE_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
            .build()
            .dispatchForEntity(predalien);
    }

    /** Leaving the ground. HOLDS on its last frame so one clip covers any airborne duration. */
    public void jump() {
        AzCommand.<Predalien>replay()
            .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.JUMP_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
            .build()
            .dispatchForEntity(predalien);
    }

    public void land() {
        AzCommand.<Predalien>replay()
            .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.LAND_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(predalien);
    }

    public void biteAttack() {
        biteAttack(1.0F);
    }

    public void biteAttack(float speed) {
        AzCommand.<Predalien>replay()
            .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(predalien);
    }

    public void tailAttack() {
        tailAttack(1.0F);
    }

    public void tailAttack(float speed) {
        AzCommand.<Predalien>replay()
            .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.ATTACK_TAIL_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(predalien);
    }

    /** Mirrored claw. */
    public void clawAttack() {
        clawAttack(1.0F);
    }

    public void clawAttack(float speed) {
        var clip = MirroredAttackSide.useLeftArm(predalien)
            ? PredalienAnimationRefs.ATTACK_CLAW_LEFT_ANIMATION_NAME
            : PredalienAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME;

        AzCommand.<Predalien>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(predalien);
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
        var clip = MirroredAttackSide.useLeftArm(predalien)
            ? PredalienAnimationRefs.ATTACK_BACKHAND_LEFT_ANIMATION_NAME
            : PredalienAnimationRefs.ATTACK_BACKHAND_RIGHT_ANIMATION_NAME;

        AzCommand.<Predalien>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(predalien);
    }

    /** Swimming bite - single clip, NOT mirrored. */
    public void swimAttack() {
        swimAttack(1.0F);
    }

    public void swimAttack(float speed) {
        AzCommand.<Predalien>replay()
            .play(AzAlienAnimationUtil.BODY, PredalienAnimationRefs.SWIM_ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(predalien);
    }

    /** Mirrored crawling swipe. ⚠ Needs restrictToCrawlAttacks + a crawl AttackType to fire. */
    public void crawlBiteAttack() {
        AzCommand.<Predalien>replay()
            .play(
                AzAlienAnimationUtil.BODY,
                PredalienAnimationRefs.CRAWL_ATTACK_BITE_ANIMATION_NAME,
                AzPlayBehaviors.PLAY_ONCE
            )
            .build()
            .dispatchForEntity(predalien);
    }

    public void crawlAttack() {
        crawlAttack(1.0F);
    }

    public void crawlAttack(float speed) {
        var clip = MirroredAttackSide.useLeftArm(predalien)
            ? PredalienAnimationRefs.CRAWL_ATTACK_LEFT_ANIMATION_NAME
            : PredalienAnimationRefs.CRAWL_ATTACK_RIGHT_ANIMATION_NAME;

        AzCommand.<Predalien>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(predalien);
    }
}
