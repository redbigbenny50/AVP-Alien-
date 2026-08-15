package com.alien.common.gameplay.entity.living.alien.xenomorph.empress;

import com.alien.common.gameplay.entity.dismemberment.MirroredAttackSide;
import com.alien.common.util.AzAlienAnimationUtil;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.blib.api.client.animation.v1.command.policy.AzDispatchMode;

public class EmpressAnimationDispatcher {

    private static final AzCommand<Empress> IDLE = AzCommand.<Empress>idempotent()
        .play(AzAlienAnimationUtil.BODY, EmpressAnimationRefs.IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Empress> RUN = AzCommand.<Empress>idempotent()
        .play(AzAlienAnimationUtil.BODY, EmpressAnimationRefs.RUN_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Empress> WALK = AzCommand.<Empress>idempotent()
        .play(AzAlienAnimationUtil.BODY, EmpressAnimationRefs.WALK_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Empress> SWIM = AzCommand.<Empress>idempotent()
        .play(AzAlienAnimationUtil.BODY, EmpressAnimationRefs.SWIM_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Empress> SIT_ON_OVIPOSITOR = AzCommand.<Empress>idempotent()
        .play(AzAlienAnimationUtil.BODY, EmpressAnimationRefs.RIDE_EGGSACK_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    // Previously played SWIM as a stand-in; her crawl clips exist (crawl, crawlidle) and are wired now.
    private static final AzCommand<Empress> CRAWL = AzCommand.<Empress>idempotent()
        .play(AzAlienAnimationUtil.BODY, EmpressAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Empress> CRAWL_HOLD = AzCommand.<Empress>idempotent()
        .play(AzAlienAnimationUtil.BODY, EmpressAnimationRefs.CRAWL_IDLE_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
        .build();

    private final Empress empress;

    public EmpressAnimationDispatcher(Empress empress) {
        this.empress = empress;
    }

    public void idle() {
        IDLE.dispatchForEntity(empress);
    }

    public void run() {
        RUN.dispatchForEntity(empress);
    }

    public void sitOnOvipositor() {
        SIT_ON_OVIPOSITOR.dispatchForEntity(empress);
    }

    public void swim() {
        SWIM.dispatchForEntity(empress);
    }

    public void crawl() {
        CRAWL.dispatchForEntity(empress);
    }

    public void crawl(float speed) {
        // ⚠⚠ THIS NAMED THE **SWIM** CLIP. A crawling empress on dry land swam on the spot. The static CRAWL
        // command above was already correct, which is exactly why it survived - the animator calls THIS overload,
        // not that one, so fixing the constant next to it changed nothing visible.
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            EmpressAnimationRefs.CRAWL_ANIMATION_NAME,
            AzPlayBehaviors.LOOP,
            AzDispatchMode.PLAY_IF_NOT_PLAYING,
            speed
        ).dispatchForEntity(empress);
    }

    /** Crawl posture transitions - one-shots on the crawl edge; speed 2 on a leg-loss collapse. */
    public void crawlDrop(float speed) {
        AzCommand.<Empress>replay()
            .play(AzAlienAnimationUtil.BODY, EmpressAnimationRefs.CRAWL_DROP_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(empress);
    }

    public void crawlRise() {
        AzCommand.<Empress>replay()
            .play(AzAlienAnimationUtil.BODY, EmpressAnimationRefs.CRAWL_RISE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(empress);
    }

    public void crawlHold() {
        CRAWL_HOLD.dispatchForEntity(empress);
    }

    public void walk() {
        WALK.dispatchForEntity(empress);
    }

    private void playAttack(String clip, float speed) {
        AzCommand.<Empress>replay()
            .play(AzAlienAnimationUtil.BODY, clip, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(empress);
    }

    /** ⚠ MIRRORED. The side is decided HERE, once per swing, by the shared seeded helper. */
    public void backhandAttack() {
        backhandAttack(1.0F);
    }

    public void backhandAttack(float speed) {
        playAttack(
            MirroredAttackSide.useLeftArm(empress)
                ? EmpressAnimationRefs.BACKHAND_LEFT_ANIMATION_NAME
                : EmpressAnimationRefs.BACKHAND_RIGHT_ANIMATION_NAME,
            speed
        );
    }

    /** ⚠ MIRRORED. */
    public void swipeDownAttack() {
        swipeDownAttack(1.0F);
    }

    public void swipeDownAttack(float speed) {
        playAttack(
            MirroredAttackSide.useLeftArm(empress)
                ? EmpressAnimationRefs.SWIPEDOWN_LEFT_ANIMATION_NAME
                : EmpressAnimationRefs.SWIPEDOWN_RIGHT_ANIMATION_NAME,
            speed
        );
    }

    /**
     * ⚠ MIRRORED, THOUGH A TAIL HAS NO LEFT AND RIGHT LIMB. The art authors both sweeps, so the side alternates on the
     * same seeded helper the arms use - it just never gets refused by a missing limb, because there is only one tail to
     * lose and losing it disables the attack entirely.
     */
    public void tailStrikeAttack() {
        tailStrikeAttack(1.0F);
    }

    public void tailStrikeAttack(float speed) {
        playAttack(
            MirroredAttackSide.useLeftArm(empress)
                ? EmpressAnimationRefs.TAILSTRIKE_LEFT_ANIMATION_NAME
                : EmpressAnimationRefs.TAILSTRIKE_RIGHT_ANIMATION_NAME,
            speed
        );
    }

    /** ⚠ MIRRORED crawling swipe - same side rule. */
    public void crawlAttack() {
        crawlAttack(1.0F);
    }

    public void crawlAttack(float speed) {
        playAttack(
            MirroredAttackSide.useLeftArm(empress)
                ? EmpressAnimationRefs.CRAWL_ATTACK_LEFT_ANIMATION_NAME
                : EmpressAnimationRefs.CRAWL_ATTACK_RIGHT_ANIMATION_NAME,
            speed
        );
    }

    public void crawlBiteAttack() {
        playAttack(EmpressAnimationRefs.CRAWL_ATTACK_BITE_ANIMATION_NAME, 1.0F);
    }

    public void biteAttack() {
        biteAttack(1.0F);
    }

    public void biteAttack(float speed) {
        playAttack(EmpressAnimationRefs.ATTACK_BITE_ANIMATION_NAME, speed);
    }

    public void headRamAttack() {
        headRamAttack(1.0F);
    }

    public void headRamAttack(float speed) {
        playAttack(EmpressAnimationRefs.ATTACK_HEADRAM_ANIMATION_NAME, speed);
    }

    public void screamAttack() {
        playAttack(EmpressAnimationRefs.SPECIAL_ATTACK_SCREAM_ANIMATION_NAME, 1.0F);
    }

    /** ⚠ Bite in the water when one arm is gone or the swing is refused - see swimClawsAttack. */
    public void swimBiteAttack() {
        playAttack(EmpressAnimationRefs.SWIM_ATTACK_BITE_ANIMATION_NAME, 1.0F);
    }

    /** ⚠ BOTH ARMS - one authored clip, not a mirrored pair. */
    public void swimClawsAttack() {
        playAttack(EmpressAnimationRefs.SWIM_ATTACK_CLAWS_ANIMATION_NAME, 1.0F);
    }

    /** The prelude: she settles onto the sack, or it has just been summoned under her. */
    public void sitOntoOvipositor() {
        AzCommand.<Empress>replay()
            .play(
                AzAlienAnimationUtil.BODY,
                EmpressAnimationRefs.RIDE_EGGSACK_SIT_ANIMATION_NAME,
                AzPlayBehaviors.HOLD_ON_LAST_FRAME
            )
            .build()
            .dispatchForEntity(empress);
    }

    /** Getting off, whether she chose to or was driven off. */
    public void getOffOvipositor() {
        AzCommand.<Empress>replay()
            .play(
                AzAlienAnimationUtil.BODY,
                EmpressAnimationRefs.RIDE_EGGSACK_GETOFF_ANIMATION_NAME,
                AzPlayBehaviors.PLAY_ONCE
            )
            .build()
            .dispatchForEntity(empress);
    }

    public void idleTail() {
        AzCommand.<Empress>idempotent()
            .play(AzAlienAnimationUtil.BODY, EmpressAnimationRefs.IDLE_TAIL_ANIMATION_NAME, AzPlayBehaviors.LOOP)
            .build()
            .dispatchForEntity(empress);
    }
}
