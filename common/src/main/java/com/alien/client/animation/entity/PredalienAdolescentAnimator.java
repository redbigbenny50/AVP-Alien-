package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.common.gameplay.entity.dismemberment.AdultXenomorphHitboxCatalog;
import com.alien.common.gameplay.entity.living.alien.adolescent.AdolescentAnimationRefs;
import com.alien.common.gameplay.entity.living.alien.predalien_adolescent.PredalienAdolescent;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class PredalienAdolescentAnimator extends AzEntityAnimator<PredalienAdolescent> {

    private static final String NAME = "predalien_adolescent";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    /**
     * ⭐ The predalien adolescent's molt, wired the same way the normal adolescent's is.
     * <p>
     * It only has one destination, so the selector could have been a constant - it is destination-keyed anyway so that
     * both adolescents resolve their clips through the SAME helper and the same roster. Enter-oriented, and
     * source-only: nothing molts into a predalien adolescent except a chestburster, which does not cocoon.
     * </p>
     */
    private final CocoonAnimationStateTracker<PredalienAdolescent> cocoonAnimationStateTracker =
        new CocoonAnimationStateTracker<>(
            adolescent -> AdolescentAnimationRefs.moltLoopFor(moltFormOf(adolescent)),
            adolescent -> AdolescentAnimationRefs.moltEnterFor(moltFormOf(adolescent)),
            true
        );

    private static String moltFormOf(PredalienAdolescent predalienAdolescent) {
        var target = predalienAdolescent.getCocoonManager().getTargetType();

        if (target == null) {
            return null;
        }

        return AdultXenomorphHitboxCatalog
            .modelForEntityPath(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target).getPath())
            .orElse(null);
    }

    public PredalienAdolescentAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<PredalienAdolescent> animationTrackContainer) {
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.BODY)
                .setTransitionLength(5)
                .build(),
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.HEAD)
                .setTransitionLength(5)
                .build(),
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.LEFT_ARM)
                .setTransitionLength(5)
                .build(),
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.LEFT_LEG)
                .setTransitionLength(5)
                .build(),
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.RIGHT_ARM)
                .setTransitionLength(5)
                .build(),
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.RIGHT_LEG)
                .setTransitionLength(5)
                .build(),
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.TAIL)
                .setTransitionLength(5)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(PredalienAdolescent animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(PredalienAdolescent animatable, float partialTicks) {
        super.setCustomAnimations(animatable, partialTicks);

        // Ahead of everything: a molting adolescent is doing nothing else.
        if (cocoonAnimationStateTracker.run(animatable)) {
            return;
        }

        AzAlienHeadAnimationUtil.applyHeadLookFromBindPose(animatable, context(), partialTicks, "gNeck");

        runPassiveAnimations(animatable);
    }

    private void runPassiveAnimations(PredalienAdolescent predalienAdolescent) {
        var dispatcher = predalienAdolescent.getAnimationDispatcher();
        var isMovingOnGround = predalienAdolescent.isMovingHorizontally.get() && predalienAdolescent.onGround();
        Runnable animFunction;

        if (predalienAdolescent.isUnderWater()) {
            // TODO: idle swim
            animFunction = dispatcher::swim;
        } else if (isMovingOnGround) {
            if (predalienAdolescent.isMovingQuickly.get()) {
                animFunction = dispatcher::run;
            } else {
                animFunction = dispatcher::walk;
            }
        } else {
            animFunction = dispatcher::idle;
        }

        animFunction.run();
    }
}
