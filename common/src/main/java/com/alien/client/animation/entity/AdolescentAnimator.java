package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.common.gameplay.entity.living.alien.adolescent.Adolescent;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import com.blib.api.client.model.v1.AzBakedModel;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class AdolescentAnimator extends AzEntityAnimator<Adolescent> {

    private static final String NAME = "adolescent";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    private static final String ROYAL_NAME = "royal_adolescent";

    /**
     * The royal adolescent is the SAME entity class on the SAME renderer - only its texture, geo and now its animation
     * file differ. The renderer already switches the geo on {@code isRoyal()}; this does the same for the clips, which
     * were authored separately for the royal proportions. Clip NAMES are identical between the two files, so one
     * dispatcher drives both.
     */
    private static final ResourceLocation ROYAL_ANIMATION = AlienResources.entityAnimationLocation(ROYAL_NAME);

    public AdolescentAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<Adolescent> animationTrackContainer) {
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
    public @NotNull ResourceLocation getAnimationLocation(Adolescent animatable) {
        return animatable.isRoyal() ? ROYAL_ANIMATION : ANIMATION;
    }

    @Override
    public void setCustomAnimations(Adolescent animatable, float partialTicks) {
        super.setCustomAnimations(animatable, partialTicks);

        AzAlienHeadAnimationUtil.applyHeadLookFromBindPose(animatable, context(), partialTicks, "gNeck");

        // TODO: This belongs in rendering, not in animation.
        showDorsalTubes(animatable);

        runPassiveAnimations(animatable);
    }

    // TODO: This belongs in rendering, not in animation.
    private void showDorsalTubes(Adolescent entity) {
        var bakedModel = context().boneCache().getBakedModel();
        hideDorsalTube(entity, bakedModel, "gLeftUpperDorsalTubeNub");
        hideDorsalTube(entity, bakedModel, "gRightUpperDorsalTubeNub");
        hideDorsalTube(entity, bakedModel, "gLeftLowerDorsalTubeNub");
        hideDorsalTube(entity, bakedModel, "gRightLowerDorsalTubeNub");
    }

    // TODO: This belongs in rendering, not in animation.
    private static void hideDorsalTube(Adolescent entity, AzBakedModel bakedModel, String dorsalTubeBoneName) {
        var dorsalTubeNub = bakedModel.getBoneOrNull(dorsalTubeBoneName);

        if (dorsalTubeNub != null) {
            dorsalTubeNub.setHidden(!entity.hasDorsalTubes.get());
        }
    }

    private void runPassiveAnimations(Adolescent adolescent) {
        var dispatcher = adolescent.getAnimationDispatcher();
        var isMovingOnGround = adolescent.isMovingHorizontally.get() && adolescent.onGround();
        Runnable animFunction;

        if (adolescent.isUnderWater()) {
            // TODO: idle swim
            animFunction = dispatcher::swim;
        } else if (isMovingOnGround) {
            if (adolescent.isMovingQuickly.get()) {
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
