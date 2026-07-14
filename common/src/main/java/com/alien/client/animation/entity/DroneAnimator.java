package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.drone.Drone;
import com.alien.common.gameplay.entity.living.alien.xenomorph.drone.DroneAnimationRefs;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class DroneAnimator extends AzEntityAnimator<Drone> {

    private static final String NAME = "drone";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    private int previousAttackId = Integer.MIN_VALUE;

    private final CocoonAnimationStateTracker<Drone> cocoonAnimationStateTracker = new CocoonAnimationStateTracker<>();

    public DroneAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<Drone> animationTrackContainer) {
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.BODY)
                .setTransitionLength(5)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(Drone animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(Drone animatable, float partialTicks) {
        super.setCustomAnimations(animatable, partialTicks);

        if (cocoonAnimationStateTracker.run(animatable)) {
            return;
        }

        AzAlienHeadAnimationUtil.applyHeadLookFromBindPose(animatable, context(), partialTicks, "gNeck");

        runPassiveAnimations(animatable);
    }

    private void runPassiveAnimations(Drone drone) {
        var dispatcher = drone.getAnimationDispatcher();

        if (drone.isLunging.get()) {
            dispatcher.lunge();
            return;
        }

        var attackType = drone.attackType.get();
        var attackId = drone.attackId.get();

        if (!attackType.isNone()) {
            if (attackId != previousAttackId) {
                var speed = calculateAttackSpeed(drone, attackType);

                if (attackType == Drone.BITE)
                    dispatcher.biteAttack(speed);
                else if (attackType == Drone.CLAW)
                    dispatcher.rightClawAttack(speed);
                else if (attackType == Drone.TAIL)
                    dispatcher.tailAttack(speed);

                previousAttackId = attackId;
            }
            return;
        }

        var isMoving = drone.isMovingHorizontally.get() && drone.onGround();
        var isCrawling = drone.getCrawlingManager().isCrawling();
        Runnable animFunction;

        if (drone.isUnderWater()) {
            // TODO: idle swim
            animFunction = dispatcher::swim;
        } else if (isMoving) {
            if (isCrawling) {
                animFunction = () -> dispatcher.crawl(AzAlienAnimationUtil.crawlAnimationSpeed(drone));
            } else if (isCarrying(drone)) {
                // Laden. Takes precedence over run: there is no "run carry" animation, and a drone hauling an egg or a
                // thrashing villager should not be sprinting anyway.
                animFunction = dispatcher::walkCarry;
            } else if (drone.isMovingQuickly.get()) {
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

    /**
     * Is this drone hauling something? An egg on its back, or a host clutched to its chest.
     * <p>
     * Passengers are synced to the client already, so this needs no new network state - the animator can simply look.
     */
    private static boolean isCarrying(Drone drone) {
        for (var passenger : drone.getPassengers()) {
            var type = passenger.getType();
            if (type.is(AlienEntityTypeTags.HOSTS) || type.is(AlienEntityTypeTags.OVOMORPHS)) {
                return true;
            }
        }
        return false;
    }

    private float calculateAttackSpeed(Drone drone, AttackType attackType) {
        String animationName;

        if (attackType == Drone.BITE)
            animationName = DroneAnimationRefs.FULL_ATTACK_BITE_ANIMATION_NAME;
        else if (attackType == Drone.CLAW)
            animationName = DroneAnimationRefs.FULL_ATTACK_CLAW_ANIMATION_NAME;
        else if (attackType == Drone.TAIL)
            animationName = DroneAnimationRefs.FULL_ATTACK_TAIL_ANIMATION_NAME;
        else
            animationName = null;

        var durationInTicks = drone.attackDurationInTicks.get();

        if (animationName == null || durationInTicks <= 0) {
            return 1.0f;
        }

        var animation = getAnimation(drone, animationName);

        return (float) (animation.length() / durationInTicks);
    }
}
