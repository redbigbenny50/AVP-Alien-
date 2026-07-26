package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.client.render.entity.carrier.CarrierSpineBoneCache;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.carrier.Carrier;
import com.alien.common.gameplay.entity.living.alien.xenomorph.carrier.CarrierAnimationRefs;
import com.alien.common.gameplay.entity.living.alien.xenomorph.carrier.CarrierSpine;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class CarrierAnimator extends AzEntityAnimator<Carrier> {

    private static final String NAME = "carrier";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    private int previousAttackId = Integer.MIN_VALUE;

    private double prevEntityX;

    private double prevEntityY;

    private double prevEntityZ;

    private boolean hasPrevEntityPosition;

    /**
     * This caste authors an EMERGE-oriented molt clip ({@code molt.emerge}) rather than the {@code molt.enter} most
     * castes ship, so it stays on the emerge-first path: the clip plays forwards to emerge and backwards to cocoon in.
     * Left on the default it would ask for a {@code molt.enter} that does not exist.
     */
    private final CocoonAnimationStateTracker<Carrier> cocoonAnimationStateTracker =
        new CocoonAnimationStateTracker<>(xenomorph -> "molting", xenomorph -> "molt.emerge");

    public CarrierAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<Carrier> animationTrackContainer) {
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.BODY)
                .setTransitionLength(5)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(Carrier animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(Carrier animatable, float partialTicks) {
        super.setCustomAnimations(animatable, partialTicks);

        if (cocoonAnimationStateTracker.run(animatable)) {
            return;
        }

        AzAlienHeadAnimationUtil.applyHeadLookFromBindPose(animatable, context(), partialTicks, "gNeck");

        runPassiveAnimations(animatable);
        updateSpineBoneData(animatable);
    }

    private void runPassiveAnimations(Carrier carrier) {
        var dispatcher = carrier.getAnimationDispatcher();

        var attackType = carrier.attackType.get();
        var attackId = carrier.attackId.get();

        if (!attackType.isNone()) {
            if (attackId != previousAttackId) {
                if (attackType == Carrier.BITE)
                    dispatcher.biteAttack(calculateAttackSpeed(carrier, attackType));
                else if (attackType == Carrier.CLAW)
                    dispatcher.clawAttack(calculateAttackSpeed(carrier, attackType));
                else if (attackType == Carrier.TAIL)
                    dispatcher.tailAttack(calculateAttackSpeed(carrier, attackType));
                else if (attackType == Carrier.THROW)
                    dispatcher.throwAttack();
                else if (attackType == Carrier.SCREAM)
                    dispatcher.screamAttack();

                previousAttackId = attackId;
            }
            return;
        }

        var isMovingOnGround = carrier.isMovingHorizontally.get() && carrier.onGround();
        var isCrawling = carrier.getCrawlingManager().isCrawling();
        Runnable animFunction;

        if (carrier.isUnderWater()) {
            animFunction = dispatcher::swim;
        } else if (isMovingOnGround) {
            if (isCrawling) {
                animFunction = () -> dispatcher.crawl(AzAlienAnimationUtil.crawlAnimationSpeed(carrier));
            } else if (carrier.isMovingQuickly.get()) {
                animFunction = dispatcher::run;
            } else {
                animFunction = dispatcher::walk;
            }
        } else {
            animFunction = isCrawling ? dispatcher::crawlHold : dispatcher::idle;
        }

        animFunction.run();
    }

    private void updateSpineBoneData(Carrier carrier) {
        if (!hasPrevEntityPosition) {
            saveCurrentEntityPosition(carrier);
            CarrierSpineBoneCache.remove(carrier.getId());
            return;
        }

        var bakedModel = context().boneCache().getBakedModel();
        var offsets = new Vector3d[CarrierSpine.COUNT];
        var rotations = new Vector3f[CarrierSpine.COUNT];

        for (var spine : CarrierSpine.values()) {
            var bone = bakedModel.getBoneOrNull(spine.getBoneName());

            if (bone != null) {
                bone.setTrackingMatrices(true);
                var worldPos = bone.getWorldPosition();

                // Matrix tracking is updated after this animation pass, during model rendering. The world position
                // available here is therefore from the previous render and includes the carrier's tick position from
                // that same render. Subtract that tick position, not the interpolated render position, so movement
                // interpolation is applied exactly once by the facehugger renderer.
                offsets[spine.getPassengerIndex()] = new Vector3d(
                    worldPos.x - prevEntityX,
                    worldPos.y - prevEntityY,
                    worldPos.z - prevEntityZ
                );

                rotations[spine.getPassengerIndex()] = new Vector3f(
                    bone.getRotX(),
                    bone.getRotY(),
                    bone.getRotZ()
                );
            }
        }

        CarrierSpineBoneCache.put(carrier.getId(), offsets, rotations);
        saveCurrentEntityPosition(carrier);
    }

    private void saveCurrentEntityPosition(Carrier carrier) {
        prevEntityX = carrier.getX();
        prevEntityY = carrier.getY();
        prevEntityZ = carrier.getZ();
        hasPrevEntityPosition = true;
    }

    private float calculateAttackSpeed(Carrier carrier, AttackType attackType) {
        String animationName;

        if (attackType == Carrier.BITE)
            animationName = CarrierAnimationRefs.ATTACKBITE_ANIMATION_NAME;
        else if (attackType == Carrier.CLAW)
            animationName = CarrierAnimationRefs.ATTACKCLAW_ANIMATION_NAME;
        else if (attackType == Carrier.TAIL)
            animationName = CarrierAnimationRefs.ATTACKTAIL_ANIMATION_NAME;
        else if (attackType == Carrier.SCREAM)
            animationName = CarrierAnimationRefs.SPECIAL_ATTACK_SCREAM_ANIMATION_NAME;
        else
            animationName = null;

        var durationInTicks = carrier.attackDurationInTicks.get();

        if (animationName == null || durationInTicks <= 0) {
            return 1.0f;
        }

        var animation = getAnimation(carrier, animationName);

        return (float) (animation.length() / durationInTicks);
    }
}
