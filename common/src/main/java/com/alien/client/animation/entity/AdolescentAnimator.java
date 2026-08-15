package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.common.gameplay.entity.dismemberment.AdultXenomorphHitboxCatalog;
import com.alien.common.gameplay.entity.living.alien.adolescent.Adolescent;
import com.alien.common.gameplay.entity.living.alien.adolescent.AdolescentAnimationRefs;
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

    /**
     * ⭐⭐ THE ADOLESCENT'S MOLT, WHICH NEVER PLAYED BEFORE.
     * <p>
     * Two things had to be true and neither was: the entity had to reach a cocoon state at all (it now does - it
     * extends Xenomorph, so GrowthManager routes it through the cocoon pipeline instead of swapping it instantly), and
     * something had to dispatch the clips. This is that something.
     * </p>
     * <p>
     * ⚠ THE SELECTORS ARE DESTINATION-KEYED, unlike every other caste's. The adolescent has a separate enter/loop pair
     * per form it can become, so both selectors read {@code getCocoonManager().getTargetType()} rather than returning a
     * constant. ENTER-ORIENTED ({@code true}): the pair is authored as a wrapping-up, and the adolescent is only ever a
     * molt SOURCE, so the emerge side is never reached on this entity.
     * </p>
     */
    private final CocoonAnimationStateTracker<Adolescent> cocoonAnimationStateTracker = new CocoonAnimationStateTracker<>(
        adolescent -> AdolescentAnimationRefs.moltLoopFor(moltFormOf(adolescent)),
        adolescent -> AdolescentAnimationRefs.moltEnterFor(moltFormOf(adolescent)),
        true
    );

    /**
     * The bare caste name of what this adolescent is turning into - "spitter" for {@code avp_alien:nether_spitter}.
     * <p>
     * Strain prefixes are stripped because the clips are per FORM, not per strain: a nether adolescent becoming a
     * nether spitter plays the same {@code molt.spitter.*} pair as a normal one. {@code modelForEntityPath} already
     * does exactly this stripping for the limb-hitbox roster, so the rule lives in one place.
     * </p>
     */
    private static String moltFormOf(Adolescent adolescent) {
        var target = adolescent.getCocoonManager().getTargetType();

        if (target == null) {
            return null;
        }

        return AdultXenomorphHitboxCatalog
            .modelForEntityPath(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target).getPath())
            .orElse(null);
    }

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

        // Ahead of everything: a molting adolescent is doing nothing else.
        if (cocoonAnimationStateTracker.run(animatable)) {
            return;
        }

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
