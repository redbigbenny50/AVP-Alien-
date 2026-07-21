package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.predalien.Predalien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.predalien.PredalienAnimationRefs;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class PredalienAnimator extends AzEntityAnimator<Predalien> {

    private static final String NAME = "predalien";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    private int previousAttackId = Integer.MIN_VALUE;

    private final CocoonAnimationStateTracker<Predalien> cocoonAnimationStateTracker = new CocoonAnimationStateTracker<>();

    public PredalienAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<Predalien> animationTrackContainer) {
        // Single track. Rebuilt from per-body-part clips onto whole-body ones, so the seven-track limb rig is gone.
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.BODY)
                .setTransitionLength(5)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(Predalien animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(Predalien animatable, float partialTicks) {
        super.setCustomAnimations(animatable, partialTicks);

        if (cocoonAnimationStateTracker.run(animatable)) {
            return;
        }

        AzAlienHeadAnimationUtil.applyHeadLookFromBindPose(animatable, context(), partialTicks, "gNeck");

        runPassiveAnimations(animatable);
    }

    private void runPassiveAnimations(Predalien predalien) {
        var dispatcher = predalien.getAnimationDispatcher();

        // Pounce owns the body for its duration, exactly as it does on the runner and prowler.
        if (predalien.isLunging.get()) {
            dispatcher.lunge();
            return;
        }

        var attackType = predalien.attackType.get();
        var attackId = predalien.attackId.get();

        if (!attackType.isNone()) {
            if (attackId != previousAttackId) {
                var speed = calculateAttackSpeed(predalien, attackType);

                if (attackType == Predalien.BITE) {
                    dispatcher.biteAttack(speed);
                } else if (attackType == Predalien.CLAW) {
                    dispatcher.rightClawAttack(speed);
                } else if (attackType == Predalien.TAIL) {
                    dispatcher.tailAttack(speed);
                }

                previousAttackId = attackId;
            }
            return;
        }

        var isMovingOnGround = predalien.isMovingHorizontally.get() && predalien.onGround();
        var isCrawling = predalien.getCrawlingManager().isCrawling();
        Runnable animFunction;

        if (predalien.isUnderWater()) {
            // TODO: idle swim
            animFunction = dispatcher::swim;
        } else if (isMovingOnGround) {
            if (isCrawling) {
                animFunction = () -> dispatcher.crawl(AzAlienAnimationUtil.crawlAnimationSpeed(predalien));
            } else if (predalien.isMovingQuickly.get()) {
                animFunction = dispatcher::run;
            } else {
                animFunction = dispatcher::walk;
            }
        } else {
            animFunction = isCrawling ? dispatcher::crawlHold : dispatcher::idle;
        }

        animFunction.run();
    }

    private float calculateAttackSpeed(Predalien predalien, AttackType attackType) {
        String animationName;

        if (attackType == Predalien.BITE) {
            animationName = PredalienAnimationRefs.FULL_ATTACK_BITE_ANIMATION_NAME;
        } else if (attackType == Predalien.CLAW) {
            animationName = PredalienAnimationRefs.FULL_ATTACK_CLAW_ANIMATION_NAME;
        } else if (attackType == Predalien.TAIL) {
            animationName = PredalienAnimationRefs.FULL_ATTACK_TAIL_ANIMATION_NAME;
        } else {
            animationName = null;
        }

        var durationInTicks = predalien.attackDurationInTicks.get();

        if (animationName == null || durationInTicks <= 0) {
            return 1.0f;
        }

        var animation = getAnimation(predalien, animationName);

        return (float) (animation.length() / durationInTicks);
    }
}
