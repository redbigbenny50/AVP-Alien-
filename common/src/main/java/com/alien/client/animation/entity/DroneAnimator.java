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
        // Carve-crew gait (construction economy step 5): a rostered digger (1) or placer (2) plays walk dig -
        // diggers at the default 70%, placers at 50% so the two read differently side by side.
        //
        // While rostered this is the ONLY passive animation the drone plays. It deliberately outranks swim, crawl,
        // carry, run and idle: the carve gait is a long cycle (2.5s at 0.7 speed = 3.57s; placers 5s) and every
        // switch to another animation restarts the track into its 5-tick transition. Carve work is start-stop by
        // nature - step, dig, turn, step - and isMovingQuickly is a raw per-tick position-delta test with no
        // hysteresis, so it chatters many times a second. Letting any of those states interleave meant the gait
        // never advanced past its opening frames and the legs sat frozen in the t=0 stride pose.
        //
        // Real interruptions still win: lunge, attacks and the cocoon state all return above this point, and the
        // roster clears carveDigMode the moment the drone leaves the crew.
        var carveDigMode = drone.carveDigMode.get();
        Runnable animFunction;

        if (carveDigMode > 0) {
            animFunction = carveDigMode == 2 ? () -> dispatcher.walkDig(0.5F) : dispatcher::walkDig;
        } else if (drone.isUnderWater()) {
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
