package com.alien.common.gameplay.entity.living.alien.xenomorph.queen;

import com.alien.common.gameplay.entity.dismemberment.MirroredAttackSide;
import com.alien.common.util.AzAlienAnimationUtil;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.blib.api.client.animation.v1.command.policy.AzDispatchMode;

public class QueenAnimationDispatcher {

    /** Dig loops (digging, stand_digging) play at 70% speed per design - slowed in-game, not in the model. */
    private static final float DIG_ANIMATION_SPEED = 0.7F;

    private static final AzCommand<Queen> IDLE = AzCommand.<Queen>idempotent()
        .play(AzAlienAnimationUtil.BODY, QueenAnimationRefs.IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Queen> HIBERNATE = AzCommand.<Queen>idempotent()
        .play(AzAlienAnimationUtil.BODY, QueenAnimationRefs.HIBERNATE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Queen> INCAPACITATED = AzCommand.<Queen>idempotent()
        .play(AzAlienAnimationUtil.BODY, QueenAnimationRefs.INCAPACITATED_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Queen> CRAWL = AzCommand.<Queen>idempotent()
        .play(AzAlienAnimationUtil.BODY, QueenAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Queen> CRAWL_IDLE = AzCommand.<Queen>idempotent()
        .play(AzAlienAnimationUtil.BODY, QueenAnimationRefs.CRAWL_IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Queen> RUN = AzCommand.<Queen>idempotent()
        .play(AzAlienAnimationUtil.BODY, QueenAnimationRefs.RUN_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Queen> SIT_ON_OVIPOSITOR = AzCommand.<Queen>idempotent()
        .play(AzAlienAnimationUtil.BODY, QueenAnimationRefs.RIDE_EGG_SACK_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    /**
     * ⭐⭐ THE CHAINED SEAT. [stated] "theres a new riding eggsack animation for when she is inhibited and chained on the
     * restrained eggsack."
     * <p>
     * ⚠ NO NEW STATE WAS NEEDED. `isInhibited()` is already networked (QUEEN_HAS_INHIBITOR) and `isRidingOvipositor()`
     * already drives the seat, so the client can pick the posture itself. Adding a synced "restrained" flag would have
     * been a third source of truth for a thing two existing flags already answer.
     * </p>
     */
    private static final AzCommand<Queen> SIT_ON_OVIPOSITOR_RESTRAINED = AzCommand.<Queen>idempotent()
        .play(
            AzAlienAnimationUtil.BODY,
            QueenAnimationRefs.RIDE_EGG_SACK_RESTRAINED_ANIMATION_NAME,
            AzPlayBehaviors.LOOP
        )
        .build();

    private static final AzCommand<Queen> SWIM = AzCommand.<Queen>idempotent()
        .play(AzAlienAnimationUtil.BODY, QueenAnimationRefs.SWIM_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Queen> WALK = AzCommand.<Queen>idempotent()
        .play(AzAlienAnimationUtil.BODY, QueenAnimationRefs.WALK_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private final Queen queen;

    public QueenAnimationDispatcher(Queen queen) {
        this.queen = queen;
    }

    public void idle() {
        IDLE.dispatchForEntity(queen);
    }

    /** Voluntary curled sleep during the hibernation phase (Stage 3). Looping; driven while she sleeps. */
    public void hibernate() {
        HIBERNATE.dispatchForEntity(queen);
    }

    /** Involuntary defeat collapse when downed/captured. Looping; driven while she is incapacitated. */
    public void incapacitated() {
        INCAPACITATED.dispatchForEntity(queen);
    }

    public void crawl() {
        CRAWL.dispatchForEntity(queen);
    }

    public void crawl(float speed) {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            QueenAnimationRefs.CRAWL_ANIMATION_NAME,
            AzPlayBehaviors.LOOP,
            AzDispatchMode.PLAY_IF_NOT_PLAYING,
            speed
        ).dispatchForEntity(queen);
    }

    public void crawlIdle() {
        CRAWL_IDLE.dispatchForEntity(queen);
    }

    public void run() {
        RUN.dispatchForEntity(queen);
    }

    /** Free on the sack, or chained to it. ⚠ IDEMPOTENT, so flipping mid-ride swaps posture without restarting. */
    public void sitOnOvipositor() {
        (queen.isInhibited() ? SIT_ON_OVIPOSITOR_RESTRAINED : SIT_ON_OVIPOSITOR).dispatchForEntity(queen);
    }

    public void swim() {
        SWIM.dispatchForEntity(queen);
    }

    public void walk() {
        WALK.dispatchForEntity(queen);
    }

    // ---- Vertical dig sequence (descend to anchor Y): digdown (once, hold) -> digging (loop @70%) -> digup (once)
    // ----

    /** One-shot: she plants and starts burrowing straight down. Holds on the last frame into the digging loop. */
    public void digDown() {
        AzCommand.<Queen>replay()
            .play(AzAlienAnimationUtil.BODY, QueenAnimationRefs.DIG_DOWN_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
            .build()
            .dispatchForEntity(queen);
    }

    /** Looping vertical dig, played at 70% speed per design. Idempotent so it isn't restarted every tick. */
    public void digging() {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            QueenAnimationRefs.DIGGING_ANIMATION_NAME,
            AzPlayBehaviors.LOOP,
            AzDispatchMode.PLAY_IF_NOT_PLAYING,
            DIG_ANIMATION_SPEED
        ).dispatchForEntity(queen);
    }

    /** One-shot: she pulls up out of the dig and returns toward idle. */
    public void digUp() {
        AzCommand.<Queen>replay()
            .play(AzAlienAnimationUtil.BODY, QueenAnimationRefs.DIG_UP_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(queen);
    }

    // ---- Standing/horizontal dig (also room carving): start (once) -> stand_digging (loop @70%) -> stop (once) ----

    /** One-shot: she raises her hands to begin a horizontal dig. */
    public void digStandStart() {
        AzCommand.<Queen>replay()
            .play(AzAlienAnimationUtil.BODY, QueenAnimationRefs.DIG_STAND_START_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(queen);
    }

    /** Looping horizontal dig (digging outward / carving a room), played at 70% speed per design. */
    public void standDigging() {
        AzAlienAnimationUtil.singleWithSpeed(
            AzAlienAnimationUtil.BODY,
            QueenAnimationRefs.STAND_DIGGING_ANIMATION_NAME,
            AzPlayBehaviors.LOOP,
            AzDispatchMode.PLAY_IF_NOT_PLAYING,
            DIG_ANIMATION_SPEED
        ).dispatchForEntity(queen);
    }

    /** One-shot: she lowers her arms back to idle after a horizontal dig. */
    public void digStandStop() {
        AzCommand.<Queen>replay()
            .play(AzAlienAnimationUtil.BODY, QueenAnimationRefs.DIG_STAND_STOP_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(queen);
    }

    // ---- Incapacitated sequence: drop (once) -> incapacitated (loop) -> rise (once) ----

    /** One-shot: 0-hp collapse into the incapacitated pose. */
    public void incapacitatedDrop() {
        AzCommand.<Queen>replay()
            .play(AzAlienAnimationUtil.BODY, QueenAnimationRefs.INCAPACITATED_DROP_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(queen);
    }

    /** One-shot: she awakens from incapacitation back toward idle. */
    public void incapacitatedRise() {
        AzCommand.<Queen>replay()
            .play(AzAlienAnimationUtil.BODY, QueenAnimationRefs.INCAPACITATED_RISE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(queen);
    }

    /** Looping struggle while fully chained (4 chains) but not yet inhibited (no eggsack). */
    public void boundStruggle() {
        AzCommand.<Queen>idempotent()
            .play(AzAlienAnimationUtil.BODY, QueenAnimationRefs.BOUND_STRUGGLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
            .build()
            .dispatchForEntity(queen);
    }

    public void backhandAttack() {
        playAttack(QueenAnimationRefs.RIGHT_BACKHAND_ANIMATION_NAME);
    }

    public void backhandAttack(float speed) {
        backhandAttack(QueenAnimationRefs.RIGHT_BACKHAND_ANIMATION_NAME, speed);
    }

    public void backhandAttack(String animationName, float speed) {
        playAttack(animationName, speed);
    }

    public void swipeDownAttack() {
        playAttack(QueenAnimationRefs.RIGHT_SWIPE_DOWN_ANIMATION_NAME);
    }

    public void swipeDownAttack(float speed) {
        swipeDownAttack(QueenAnimationRefs.RIGHT_SWIPE_DOWN_ANIMATION_NAME, speed);
    }

    public void swipeDownAttack(String animationName, float speed) {
        playAttack(animationName, speed);
    }

    public void tailStrikeAttack() {
        playAttack(QueenAnimationRefs.RIGHT_TAIL_STRIKE_ANIMATION_NAME);
    }

    public void tailStrikeAttack(float speed) {
        tailStrikeAttack(QueenAnimationRefs.RIGHT_TAIL_STRIKE_ANIMATION_NAME, speed);
    }

    public void tailStrikeAttack(String animationName, float speed) {
        playAttack(animationName, speed);
    }

    private void playAttack(String animationName) {
        AzCommand.<Queen>replay()
            .play(AzAlienAnimationUtil.BODY, animationName, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(queen);
    }

    /** ⚠ MIRRORED now - the side is decided HERE, once per swing, by the shared seeded helper. */
    public void crawlAttack() {
        crawlAttack(1.0F);
    }

    public void crawlAttack(float speed) {
        playAttack(
            MirroredAttackSide.useLeftArm(queen)
                ? QueenAnimationRefs.CRAWL_ATTACK_LEFT_ANIMATION_NAME
                : QueenAnimationRefs.CRAWL_ATTACK_RIGHT_ANIMATION_NAME,
            speed
        );
    }

    public void crawlBiteAttack() {
        playAttack(QueenAnimationRefs.CRAWL_ATTACK_BITE_ANIMATION_NAME);
    }

    public void biteAttack() {
        playAttack(QueenAnimationRefs.ATTACK_BITE_ANIMATION_NAME);
    }

    public void biteAttack(float speed) {
        playAttack(QueenAnimationRefs.ATTACK_BITE_ANIMATION_NAME, speed);
    }

    public void swimAttack() {
        playAttack(QueenAnimationRefs.SWIM_ATTACK_BITE_ANIMATION_NAME);
    }

    /** Her forward head ram - the only strike she has that uses neither arm nor tail. */
    public void headRamAttack() {
        headRamAttack(1.0F);
    }

    public void headRamAttack(float speed) {
        playAttack(QueenAnimationRefs.ATTACK_HEADRAM_ANIMATION_NAME, speed);
    }

    public void screamAttack() {
        playAttack(QueenAnimationRefs.SPECIAL_ATTACK_SCREAM_ANIMATION_NAME);
    }

    /** Crawl posture transitions - one-shots on the crawl edge; speed 2 on a leg-loss collapse. */
    public void crawlDrop(float speed) {
        AzCommand.<Queen>replay()
            .play(AzAlienAnimationUtil.BODY, QueenAnimationRefs.CRAWL_DROP_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(queen);
    }

    public void crawlRise() {
        AzCommand.<Queen>replay()
            .play(AzAlienAnimationUtil.BODY, QueenAnimationRefs.CRAWL_RISE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build()
            .dispatchForEntity(queen);
    }

    private void playAttack(String animationName, float speed) {
        AzCommand.<Queen>replay()
            .play(AzAlienAnimationUtil.BODY, animationName, AzPlayBehaviors.PLAY_ONCE)
            .setSpeed(AzAlienAnimationUtil.BODY, speed)
            .build()
            .dispatchForEntity(queen);
    }
}
