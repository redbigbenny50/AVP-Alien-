package com.alien.common.gameplay.entity.living.alien.adolescent;

import com.alien.common.util.AzAlienAnimationUtil;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;

/**
 * Whole-body dispatch for the reworked adolescent.
 * <p>
 * This used to compose every action across {@code AzAlienAnimationUtil.XENO_LIMBS} - seven separate tracks per action,
 * named {@code walk.body}, {@code walk.head} and so on. The rework replaced that with 13 single whole-body clips, so
 * every command here plays one clip on the BODY track, matching the house pattern in the Crusher, RazorClaw and Queen
 * dispatchers.
 * <p>
 * The old composition was also actively broken: it asked for base names {@code "sprint"} and {@code "lunge"} that no
 * animation file has ever contained. A composed base that does not resolve fails SILENTLY, per limb, per frame - which
 * is why Razorem's log carried 924 "Unable to find animation: sprint.&lt;limb&gt; for Adolescent" warnings per track,
 * 6,468 in total, on the render thread. One clip that does not resolve now costs one warning, not seven.
 */
public class AdolescentAnimationDispatcher {

    private static final AzCommand<Adolescent> IDLE = AzCommand.<Adolescent>idempotent()
        .play(AzAlienAnimationUtil.BODY, AdolescentAnimationRefs.IDLE_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Adolescent> WALK = AzCommand.<Adolescent>idempotent()
        .play(AzAlienAnimationUtil.BODY, AdolescentAnimationRefs.WALK_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Adolescent> RUN = AzCommand.<Adolescent>idempotent()
        .play(AzAlienAnimationUtil.BODY, AdolescentAnimationRefs.RUN_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Adolescent> SWIM = AzCommand.<Adolescent>idempotent()
        .play(AzAlienAnimationUtil.BODY, AdolescentAnimationRefs.SWIM_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Adolescent> CRAWL = AzCommand.<Adolescent>idempotent()
        .play(AzAlienAnimationUtil.BODY, AdolescentAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<Adolescent> CRAWL_HOLD = AzCommand.<Adolescent>idempotent()
        .play(AzAlienAnimationUtil.BODY, AdolescentAnimationRefs.CRAWL_ANIMATION_NAME, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
        .build();

    private static final AzCommand<Adolescent> POUNCE = AzCommand.<Adolescent>replay()
        .play(AzAlienAnimationUtil.BODY, AdolescentAnimationRefs.POUNCE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Adolescent> BITE_ATTACK = AzCommand.<Adolescent>replay()
        .play(AzAlienAnimationUtil.BODY, AdolescentAnimationRefs.ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Adolescent> SWIPE_ATTACK = AzCommand.<Adolescent>replay()
        .play(AzAlienAnimationUtil.BODY, AdolescentAnimationRefs.ATTACK_SWIPE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private static final AzCommand<Adolescent> SWIM_BITE_ATTACK = AzCommand.<Adolescent>replay()
        .play(AzAlienAnimationUtil.BODY, AdolescentAnimationRefs.SWIM_ATTACK_BITE_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
        .build();

    private final Adolescent adolescent;

    public AdolescentAnimationDispatcher(Adolescent adolescent) {
        this.adolescent = adolescent;
    }

    public void crawl() {
        CRAWL.dispatchForEntity(adolescent);
    }

    public void crawlHold() {
        CRAWL_HOLD.dispatchForEntity(adolescent);
    }

    public void idle() {
        IDLE.dispatchForEntity(adolescent);
    }

    public void lunge() {
        POUNCE.dispatchForEntity(adolescent);
    }

    public void run() {
        RUN.dispatchForEntity(adolescent);
    }

    public void swim() {
        SWIM.dispatchForEntity(adolescent);
    }

    public void walk() {
        WALK.dispatchForEntity(adolescent);
    }

    public void biteAttack() {
        BITE_ATTACK.dispatchForEntity(adolescent);
    }

    /**
     * Kept under the old name so every existing caller still compiles. The rework retired the per-arm claw clip in
     * favour of a single whole-body swipe, so this now plays {@code attack.swipe}.
     */
    public void rightClawAttack() {
        SWIPE_ATTACK.dispatchForEntity(adolescent);
    }

    /** New with the rework - nothing calls this yet; wire it where the bite is chosen while underwater. */
    public void swimBiteAttack() {
        SWIM_BITE_ATTACK.dispatchForEntity(adolescent);
    }
}
