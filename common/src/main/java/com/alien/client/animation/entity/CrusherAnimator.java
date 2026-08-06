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

    private final CocoonAnimationStateTracker<Crusher> cocoonAnimationStateTracker = new CocoonAnimationStateTracker<>();

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
                dispatcher.run();
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
                else if (attackType == Crusher.BITE)
                    dispatcher.biteAttack(speed);
                else if (attackType == Crusher.TAIL)
                    dispatcher.tailAttack(speed);

                previousAttackId = attackId;
            }
            return;
        }

        var isMovingOnGround = crusher.isMovingHorizontally.get() && crusher.onGround();
        var isCrawling = crusher.getCrawlingManager().isCrawling();
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
        else if (attackType == Crusher.TAIL)
            animationName = CrusherAnimationRefs.TAIL_ATTACK_ANIMATION_NAME;
        else
            animationName = null;

        var durationInTicks = crusher.attackDurationInTicks.get();

        if (animationName == null || durationInTicks <= 0) {
            return 1.0f;
        }

        var animation = getAnimation(crusher, animationName);

        return (float) (animation.length() / durationInTicks);
    }
}
