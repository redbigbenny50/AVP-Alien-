package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.empress.Empress;
import com.alien.common.gameplay.entity.living.alien.xenomorph.empress.EmpressAnimationRefs;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class EmpressAnimator extends AzEntityAnimator<Empress> {

    private static final String NAME = "empress";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    private int previousAttackId = Integer.MIN_VALUE;

    /** Crawl-edge tracker for the posture transitions. Null until first observed so a mid-crawl load doesn't replay a drop. */
    private Boolean previousCrawling;

    /** Ticks the current crawl transition one-shot still owns the track. */
    private int crawlOneShotHoldTicks;

    private final CocoonAnimationStateTracker<Empress> cocoonAnimationStateTracker = new CocoonAnimationStateTracker<>();

    public EmpressAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<Empress> animationTrackContainer) {
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.BODY)
                .setTransitionLength(5)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(Empress animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(Empress animatable, float partialTicks) {
        super.setCustomAnimations(animatable, partialTicks);

        if (cocoonAnimationStateTracker.run(animatable)) {
            return;
        }

        AzAlienHeadAnimationUtil.applyHeadLookFromBindPose(animatable, context(), partialTicks, "gNeck");

        runPassiveAnimations(animatable);

        var bakedModel = context().boneCache().getBakedModel();
        var eggSack = bakedModel.getBoneOrNull("root2");

        if (eggSack != null) {
            eggSack.setHidden(true);
        }
    }

    private void runPassiveAnimations(Empress empress) {
        var dispatcher = empress.getAnimationDispatcher();

        // Crawl posture transitions - edge-driven one-shots off the synced crawl flag; a leg-loss collapse plays
        // the same drop clip at double speed with half the hold ([stated]). Above the attack block so a posture
        // change visually pre-empts a swing; the server blocks NEW attacks for the same window.
        if (crawlOneShotHoldTicks > 0) {
            crawlOneShotHoldTicks--;
            return;
        }
        boolean crawlingNow = empress.getCrawlingManager().isCrawling();
        if (previousCrawling == null) {
            previousCrawling = crawlingNow;
        } else if (crawlingNow != previousCrawling) {
            previousCrawling = crawlingNow;
            if (crawlingNow) {
                var collapse = empress.getCrawlingManager().isLegForcedCrawl();
                dispatcher.crawlDrop(collapse ? 2.0F : 1.0F);
                crawlOneShotHoldTicks = collapse
                    ? Math.max(1, EmpressAnimationRefs.CRAWL_DROP_TICKS / 2)
                    : EmpressAnimationRefs.CRAWL_DROP_TICKS;
            } else {
                dispatcher.crawlRise();
                crawlOneShotHoldTicks = EmpressAnimationRefs.CRAWL_RISE_TICKS;
            }
            return;
        }

        var attackType = empress.attackType.get();
        var attackId = empress.attackId.get();

        if (!attackType.isNone()) {
            if (attackId != previousAttackId) {
                var speed = calculateAttackSpeed(empress, attackType);

                if (attackType == Empress.SWIPE_DOWN)
                    dispatcher.swipeDownAttack(speed);
                else if (attackType == Empress.BACKHAND)
                    dispatcher.backhandAttack(speed);
                else if (attackType == Empress.TAIL_STRIKE)
                    dispatcher.tailStrikeAttack(speed);

                previousAttackId = attackId;
            }
            return;
        }

        var isMovingOnGround = empress.isMovingHorizontally.get() && empress.onGround();
        var isCrawling = empress.getCrawlingManager().isCrawling();
        Runnable animFunction;

        if (empress.getEmpressOvipositorManager().hasOvipositor()) {
            animFunction = dispatcher::sitOnOvipositor;
        } else if (empress.isUnderWater()) {
            animFunction = dispatcher::swim;
        } else if (isMovingOnGround) {
            if (isCrawling) {
                animFunction = () -> dispatcher.crawl(AzAlienAnimationUtil.crawlAnimationSpeed(empress));
            } else if (empress.isMovingQuickly.get()) {
                animFunction = dispatcher::run;
            } else {
                animFunction = dispatcher::walk;
            }
        } else {
            animFunction = isCrawling ? dispatcher::crawlHold : dispatcher::idle;
        }

        animFunction.run();
    }

    private float calculateAttackSpeed(Empress empress, AttackType attackType) {
        String animationName = null;

        if (attackType == Empress.SWIPE_DOWN)
            animationName = EmpressAnimationRefs.SWIPEDOWN_ANIMATION_NAME;
        else if (attackType == Empress.BACKHAND)
            animationName = EmpressAnimationRefs.BACKHAND_ANIMATION_NAME;
        else if (attackType == Empress.TAIL_STRIKE)
            animationName = EmpressAnimationRefs.TAILSTRIKE_ANIMATION_NAME;

        var durationInTicks = empress.attackDurationInTicks.get();

        if (animationName == null || durationInTicks <= 0) {
            return 1.0f;
        }

        var animation = getAnimation(empress, animationName);

        return (float) (animation.length() / durationInTicks);
    }
}
