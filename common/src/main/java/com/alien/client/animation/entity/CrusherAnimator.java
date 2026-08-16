package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.crusher.Crusher;
import com.alien.common.gameplay.entity.living.alien.xenomorph.crusher.CrusherAnimationRefs;
import com.alien.common.gameplay.entity.living.alien.xenomorph.crusher.CrusherChargeAttack;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class CrusherAnimator extends AzEntityAnimator<Crusher> {

    private static final String NAME = "crusher";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    private int previousAttackId = Integer.MIN_VALUE;

    /**
     * ⭐⭐ ALL THREE MOLT CLIPS ARE AUTHORED, so the three-selector form is used: enter and emerge BOTH play forwards and
     * nothing is reversed. The no-arg tracker would have thrown one of them away and produced the other half by
     * reversing its partner - and it hardcodes "molting", which this art has never contained, so the loop half was
     * silently bind-posing on every crusher molt.
     */
    private final CocoonAnimationStateTracker<Crusher> cocoonAnimationStateTracker = new CocoonAnimationStateTracker<>(
        crusher -> CrusherAnimationRefs.MOLT_LOOP_ANIMATION_NAME,
        crusher -> CrusherAnimationRefs.MOLT_EMERGE_ANIMATION_NAME,
        crusher -> CrusherAnimationRefs.MOLT_ENTER_ANIMATION_NAME
    );

    /**
     * ⭐⭐ THE JUMP DIAL. Ticks airborne before the jump clip plays - RAISE IT if crushers look like they are hopping
     * over every stair and slab. Same figure as every other caste.
     */
    private static final int AIRBORNE_TICKS_BEFORE_JUMP = 3;

    /** Blocks per tick of vertical motion below which the entity counts as ground-bound, not falling. */
    private static final double AIRBORNE_VERTICAL_EPSILON = 0.08;

    private int airborneTicks;

    private boolean jumpPlayed;

    /** Edge detection for the BLOCKING crawl transitions - null until the first posture is observed. */
    private Boolean previousCrawling;

    public CrusherAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<Crusher> animationTrackContainer) {
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.BODY)
                .setTransitionLength(5)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(Crusher animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(Crusher animatable, float partialTicks) {
        super.setCustomAnimations(animatable, partialTicks);

        if (cocoonAnimationStateTracker.run(animatable)) {
            return;
        }

        AzAlienHeadAnimationUtil.applyHeadLookFromBindPose(animatable, context(), partialTicks, "gNeck");

        runPassiveAnimations(animatable);
    }

    private void runPassiveAnimations(Crusher crusher) {
        var dispatcher = crusher.getAnimationDispatcher();

        var attackType = crusher.attackType.get();
        var attackId = crusher.attackId.get();

        if (!attackType.isNone()) {
            if (attackType == CrusherChargeAttack.ATTACK) {
                // Its OWN clip now, not the run gait it used to borrow.
                dispatcher.charge();
                return;
            }

            if (attackType == CrusherChargeAttack.BACKUP) {
                dispatcher.walk();
                return;
            }

            if (attackId != previousAttackId) {
                var speed = calculateAttackSpeed(crusher, attackType);

                if (attackType == CrusherChargeAttack.WINDUP)
                    dispatcher.lunge();
                // ⚠ Underwater it has a bite-only attack clip, like every other caste.
                else if (crusher.isUnderWater())
                    dispatcher.swimAttack();
                // ⭐ PRONE IT HAS ITS OWN SWIPES NOW. The charge windup is exempt above; anything else that
                // lands while crawling uses the crawl clips, or the body snaps upright for the swing.
                else if (crusher.getCrawlingManager().isCrawling() && attackType == Crusher.BITE)
                    dispatcher.crawlBiteAttack();
                else if (crusher.getCrawlingManager().isCrawling())
                    dispatcher.crawlAttack(speed);
                else if (attackType == Crusher.HEADBUTT)
                    dispatcher.headbuttAttack(speed);
                else if (attackType == Crusher.BITE)
                    dispatcher.biteAttack(speed);
                else if (attackType == Crusher.TAIL)
                    dispatcher.tailAttack(speed);

                previousAttackId = attackId;
            }
            return;
        }

        var isCrawling = crusher.getCrawlingManager().isCrawling();

        // ⭐ THE CRAWL TRANSITIONS, at last. crawl.drop and crawl.rise existed in the art and NOTHING played them, so
        // the crusher snapped between postures. Both are BLOCKING: dispatched once on the flip and allowed to finish.
        // ⚠ A LOST LEG PLAYS THE SAME DROP CLIP FASTER rather than a different clip - the collapse is the same motion
        // at twice the speed, which is the rule the ravager already follows.
        if (previousCrawling == null) {
            previousCrawling = isCrawling;
        } else if (previousCrawling != isCrawling) {
            previousCrawling = isCrawling;

            if (isCrawling) {
                dispatcher.crawlDrop(crusher.getCrawlingManager().isLegForcedCrawl() ? 2.0F : 1.0F);
            } else {
                dispatcher.crawlRise();
            }
            return;
        }

        // ⭐ AIRBORNE. A TIME floor, not a height one - see AIRBORNE_TICKS_BEFORE_JUMP above.
        // ⚠⚠ VERTICAL MOTION IS REQUIRED, NOT JUST !onGround. A mob whose ground contact FLICKERS while it walks
        // (a short caste on an uneven floor, resin ledges, a slab lip) would otherwise reach the airborne floor,
        // dispatch the HOLD_ON_LAST_FRAME jump, and then be blocked from every gait clip below by the jumpPlayed
        // guard - so it slides along with its pose frozen. That is precisely the "gliding, animation paused, still
        // on all fours" the burster was doing. Nothing that is genuinely airborne has zero vertical velocity.
        var verticallyAirborne = Math.abs(crusher.getDeltaMovement().y) > AIRBORNE_VERTICAL_EPSILON;

        if (!crusher.onGround() && verticallyAirborne && !crusher.isUnderWater() && !isCrawling) {
            airborneTicks++;

            // Fire ONCE, on the tick the count EQUALS the floor - re-dispatching restarts the clip and freezes it.
            if (airborneTicks == AIRBORNE_TICKS_BEFORE_JUMP) {
                dispatcher.jump();
                jumpPlayed = true;
                return;
            }

            if (jumpPlayed) {
                return; // holding the last frame until the ground comes back
            }
        } else {
            if (jumpPlayed) {
                dispatcher.land();
                jumpPlayed = false;
                airborneTicks = 0;
                return;
            }

            airborneTicks = 0;
        }

        var isMovingOnGround = crusher.isMovingHorizontally.get() && crusher.onGround();
        Runnable animFunction;

        if (crusher.isUnderWater()) {
            // TODO: idle swim
            animFunction = dispatcher::swim;
        } else if (isMovingOnGround) {
            if (isCrawling) {
                animFunction = () -> dispatcher.crawl(AzAlienAnimationUtil.crawlAnimationSpeed(crusher));
            } else if (crusher.isMovingQuickly.get()) {
                animFunction = dispatcher::run;
            } else {
                animFunction = dispatcher::walk;
            }
        } else {
            animFunction = isCrawling ? dispatcher::crawlIdle : dispatcher::idle;
        }

        animFunction.run();
    }

    private float calculateAttackSpeed(Crusher crusher, AttackType attackType) {
        String animationName;

        if (attackType == Crusher.BITE)
            animationName = CrusherAnimationRefs.BITE_ATTACK_ANIMATION_NAME;
        else if (attackType == Crusher.HEADBUTT)
            animationName = CrusherAnimationRefs.ATTACK_HEADBUTT_ANIMATION_NAME;
        else if (attackType == Crusher.TAIL)
            animationName = CrusherAnimationRefs.TAIL_ATTACK_ANIMATION_NAME;
        else
            animationName = null;

        var durationInTicks = crusher.attackDurationInTicks.get();

        if (animationName == null || durationInTicks <= 0) {
            return 1.0f;
        }

        var animation = getAnimation(crusher, animationName);

        // ⚠ A missing clip must never NPE the render thread - see DroneAnimator.calculateAttackSpeed.
        if (animation == null) {
            return 1.0f;
        }

        return (float) (animation.length() / durationInTicks);
    }
}
