package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ravager.Ravager;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ravager.RavagerAnimationRefs;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ravager.RavagerSpecialCleaveAttack;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class RavagerAnimator extends AzEntityAnimator<Ravager> {

    private static final String NAME = "ravager";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    private int previousAttackId = Integer.MIN_VALUE;

    /** Crawl-edge tracker for the posture transitions. Null until first observed so a mid-crawl load doesn't replay a drop. */
    private Boolean previousCrawling;

    /** Ticks the current crawl transition one-shot still owns the track. */
    private int crawlOneShotHoldTicks;

    /**
     * This caste authors an EMERGE-oriented molt clip ({@code molt.emerge}) rather than the {@code molt.enter} most
     * castes ship, so it stays on the emerge-first path: the clip plays forwards to emerge and backwards to cocoon in.
     * Left on the default it would ask for a {@code molt.enter} that does not exist.
     */
    private final CocoonAnimationStateTracker<Ravager> cocoonAnimationStateTracker =
        new CocoonAnimationStateTracker<>(xenomorph -> "molting", xenomorph -> "molt.emerge");

    public RavagerAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<Ravager> animationTrackContainer) {
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.BODY)
                .setTransitionLength(5)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(Ravager animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(Ravager animatable, float partialTicks) {
        super.setCustomAnimations(animatable, partialTicks);

        if (cocoonAnimationStateTracker.run(animatable)) {
            return;
        }

        AzAlienHeadAnimationUtil.applyHeadLookFromBindPose(animatable, context(), partialTicks, "gNeck");

        runPassiveAnimations(animatable);
    }

    private void runPassiveAnimations(Ravager ravager) {
        var dispatcher = ravager.getAnimationDispatcher();

        // Crawl posture transitions - edge-driven one-shots off the synced crawl flag; a leg-loss collapse plays
        // the same drop clip at double speed with half the hold ([stated]). Above the attack block so a posture
        // change visually pre-empts a swing; the server blocks NEW attacks for the same window.
        if (crawlOneShotHoldTicks > 0) {
            crawlOneShotHoldTicks--;
            return;
        }
        boolean crawlingNow = ravager.getCrawlingManager().isCrawling();
        if (previousCrawling == null) {
            previousCrawling = crawlingNow;
        } else if (crawlingNow != previousCrawling) {
            previousCrawling = crawlingNow;
            if (crawlingNow) {
                var collapse = ravager.getCrawlingManager().isLegForcedCrawl();
                dispatcher.crawlDown(collapse ? 2.0F : 1.0F);
                crawlOneShotHoldTicks = collapse
                    ? Math.max(1, RavagerAnimationRefs.CRAWL_DOWN_TICKS / 2)
                    : RavagerAnimationRefs.CRAWL_DOWN_TICKS;
            } else {
                dispatcher.crawlUp();
                crawlOneShotHoldTicks = RavagerAnimationRefs.CRAWL_UP_TICKS;
            }
            return;
        }

        var attackType = ravager.attackType.get();
        var attackId = ravager.attackId.get();

        if (!attackType.isNone()) {
            if (attackId != previousAttackId) {
                var speed = calculateAttackSpeed(ravager, attackType);

                if (attackType == Ravager.BITE)
                    dispatcher.biteAttack(speed);
                else if (attackType == Ravager.CLAW)
                    dispatcher.rightClawAttack(speed);
                else if (attackType == Ravager.CLAW_DOUBLE)
                    dispatcher.doubleClawAttack(speed);
                else if (attackType == Ravager.TAIL)
                    dispatcher.tailAttack(speed);
                else if (attackType == Ravager.SWIM_ATTACK)
                    dispatcher.swimAttack(speed);
                else if (attackType == Ravager.CRAWL_ATTACK)
                    dispatcher.crawlAttack(speed);
                else if (attackType == RavagerSpecialCleaveAttack.WINDUP)
                    dispatcher.specialCleaveWarmup(calculateWindupAnimationSpeed(ravager));
                else if (attackType == RavagerSpecialCleaveAttack.ATTACK)
                    dispatcher.specialCleaveActivate(speed);

                previousAttackId = attackId;
            }
            return;
        }

        var isMovingOnGround = ravager.isMovingHorizontally.get() && ravager.onGround();
        var isCrawling = ravager.getCrawlingManager().isCrawling();
        Runnable animFunction;

        if (ravager.isUnderWater()) {
            animFunction = dispatcher::swim;
        } else if (isMovingOnGround) {
            if (isCrawling) {
                animFunction = () -> dispatcher.crawl(AzAlienAnimationUtil.crawlAnimationSpeed(ravager));
            } else if (ravager.isMovingQuickly.get()) {
                animFunction = dispatcher::run;
            } else {
                animFunction = dispatcher::walk;
            }
        } else {
            animFunction = isCrawling ? dispatcher::crawlHold : dispatcher::idle;
        }

        animFunction.run();
    }

    private float calculateAttackSpeed(Ravager ravager, AttackType attackType) {
        String animationName = null;

        if (attackType == Ravager.BITE)
            animationName = RavagerAnimationRefs.ATTACK_BITE_ANIMATION_NAME;
        else if (attackType == Ravager.CLAW)
            animationName = RavagerAnimationRefs.ATTACK_ARM_SINGLE_ANIMATION_NAME;
        else if (attackType == Ravager.CLAW_DOUBLE)
            animationName = RavagerAnimationRefs.ATTACK_ARM_DOUBLE_ANIMATION_NAME;
        else if (attackType == Ravager.TAIL)
            animationName = RavagerAnimationRefs.ATTACK_TAIL_ANIMATION_NAME;
        else if (attackType == Ravager.SWIM_ATTACK)
            animationName = RavagerAnimationRefs.SWIM_ATTACK_ANIMATION_NAME;
        else if (attackType == RavagerSpecialCleaveAttack.WINDUP)
            animationName = RavagerAnimationRefs.SPECIAL_ATTACK_WARMUP_ANIMATION_NAME;
        else if (attackType == RavagerSpecialCleaveAttack.ATTACK)
            animationName = RavagerAnimationRefs.SPECIAL_ATTACK_ACTIVATE_ANIMATION_NAME;

        var durationInTicks = ravager.attackDurationInTicks.get();

        if (animationName == null || durationInTicks <= 0) {
            return 1.0f;
        }

        var animation = getAnimation(ravager, animationName);

        return (float) (animation.length() / durationInTicks);
    }

    private float calculateWindupAnimationSpeed(Ravager ravager) {
        var animation = getAnimation(ravager, RavagerAnimationRefs.SPECIAL_ATTACK_WARMUP_ANIMATION_NAME);
        return (float) (animation.length() / RavagerSpecialCleaveAttack.WINDUP.defaultDurationInTicks());
    }
}
