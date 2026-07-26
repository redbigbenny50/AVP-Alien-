package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.prowler.Prowler;
import com.alien.common.gameplay.entity.living.alien.xenomorph.prowler.ProwlerAnimationRefs;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class ProwlerAnimator extends AzEntityAnimator<Prowler> {

    private static final String NAME = "prowler";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    private int previousAttackId = Integer.MIN_VALUE;

    private final CocoonAnimationStateTracker<Prowler> cocoonAnimationStateTracker = new CocoonAnimationStateTracker<>();

    public ProwlerAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<Prowler> animationTrackContainer) {
        // Single track. The prowler was rebuilt from per-body-part clips onto whole-body ones, so the seven-track
        // limb rig it used to need is gone - everything plays on BODY, exactly like the runner.
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.BODY)
                .setTransitionLength(5)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(Prowler animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(Prowler animatable, float partialTicks) {
        super.setCustomAnimations(animatable, partialTicks);

        if (cocoonAnimationStateTracker.run(animatable)) {
            return;
        }

        AzAlienHeadAnimationUtil.applyHeadLookFromBindPose(animatable, context(), partialTicks, "gNeck");

        runPassiveAnimations(animatable);
    }

    private void runPassiveAnimations(Prowler prowler) {
        var dispatcher = prowler.getAnimationDispatcher();

        if (prowler.isLunging.get()) {
            dispatcher.lunge();
            return;
        }

        var attackType = prowler.attackType.get();
        var attackId = prowler.attackId.get();

        if (!attackType.isNone()) {
            if (attackId != previousAttackId) {
                var speed = calculateAttackSpeed(prowler, attackType);

                if (attackType == Prowler.BITE) {
                    dispatcher.biteAttack(speed);
                } else if (attackType == Prowler.CLAW) {
                    dispatcher.rightClawAttack(speed);
                } else if (attackType == Prowler.TAIL_QUAD) {
                    dispatcher.tailAttackQuad(speed);
                }

                previousAttackId = attackId;
            }
            return;
        }

        var isMoving = prowler.isMovingHorizontally.get() && prowler.onGround();
        var isCrawling = prowler.getCrawlingManager().isCrawling();
        Runnable animFunction;

        if (prowler.isUnderWater()) {
            // TODO: idle swim
            animFunction = dispatcher::swim;
        } else if (isMoving) {
            if (isCrawling) {
                animFunction = () -> dispatcher.crawl(AzAlienAnimationUtil.crawlAnimationSpeed(prowler));
            } else if (prowler.isMovingQuickly.get()) {
                animFunction = dispatcher::run;
            } else {
                animFunction = dispatcher::walk;
            }
        } else {
            // TODO: idle crawl
            animFunction = isCrawling ? dispatcher::crawlHold : dispatcher::idle;
        }

        animFunction.run();
    }

    private float calculateAttackSpeed(Prowler prowler, AttackType attackType) {
        String animationName;

        if (attackType == Prowler.BITE) {
            animationName = ProwlerAnimationRefs.FULL_ATTACK_BITE_ANIMATION_NAME;
        } else if (attackType == Prowler.CLAW) {
            animationName = ProwlerAnimationRefs.FULL_ATTACK_ARM_ANIMATION_NAME;
        } else if (attackType == Prowler.TAIL_QUAD) {
            animationName = ProwlerAnimationRefs.FULL_ATTACK_TAIL_ANIMATION_NAME;
        } else {
            animationName = null;
        }

        var durationInTicks = prowler.attackDurationInTicks.get();

        if (animationName == null || durationInTicks <= 0) {
            return 1.0f;
        }

        var animation = getAnimation(prowler, animationName);

        return (float) (animation.length() / durationInTicks);
    }
}
