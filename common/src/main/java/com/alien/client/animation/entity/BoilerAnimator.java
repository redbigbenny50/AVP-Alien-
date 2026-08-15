package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.common.gameplay.entity.living.alien.xenomorph.boiler.Boiler;
import com.alien.common.gameplay.entity.living.alien.xenomorph.boiler.BoilerAnimationRefs;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class BoilerAnimator extends AzEntityAnimator<Boiler> {

    private static final String NAME = "boiler";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    /**
     * ⭐⭐ THE JUMP DIAL. Ticks airborne before the jump clip plays - RAISE IT if boilers look like they are hopping over
     * every stair and slab.
     */
    private static final int AIRBORNE_TICKS_BEFORE_JUMP = 3;

    /** Blocks per tick of vertical motion below which the entity counts as ground-bound, not falling. */
    private static final double AIRBORNE_VERTICAL_EPSILON = 0.08;

    private int airborneTicks;

    private boolean jumpPlayed;

    /**
     * ⚠⚠ EXPLICIT SELECTORS, NOT THE DEFAULT. The no-arg tracker hardcodes "molting" + "molt.enter", and the boiler art
     * has NO "molting" clip at all - so the default was asking for a name that does not exist and the loop half of
     * every boiler molt was already bind-posing, silently. Both halves now come from molt.enter: it plays forwards to
     * cocoon and reversed to emerge.
     */
    private final CocoonAnimationStateTracker<Boiler> cocoonAnimationStateTracker = new CocoonAnimationStateTracker<>(
        boiler -> BoilerAnimationRefs.MOLT_ENTER_ANIMATION_NAME,
        boiler -> BoilerAnimationRefs.MOLT_ENTER_ANIMATION_NAME,
        true
    );

    public BoilerAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<Boiler> animationTrackContainer) {
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, BoilerAnimationRefs.FULL_BODY)
                .setTransitionLength(5)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(Boiler animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(Boiler animatable, float partialTicks) {
        super.setCustomAnimations(animatable, partialTicks);

        if (cocoonAnimationStateTracker.run(animatable)) {
            return;
        }

        AzAlienHeadAnimationUtil.applyHeadLookFromBindPose(animatable, context(), partialTicks, "gNeck");

        runPassiveAnimations(animatable);
    }

    private void runPassiveAnimations(Boiler boiler) {
        var dispatcher = boiler.getAnimationDispatcher();
        var isCrawling = boiler.getCrawlingManager().isCrawling();

        // ⭐ AIRBORNE. A TIME floor, not a height one - see AIRBORNE_TICKS_BEFORE_JUMP above.
        // ⚠⚠ VERTICAL MOTION IS REQUIRED, NOT JUST !onGround. A mob whose ground contact FLICKERS while it walks
        // (a short caste on an uneven floor, resin ledges, a slab lip) would otherwise reach the airborne floor,
        // dispatch the HOLD_ON_LAST_FRAME jump, and then be blocked from every gait clip below by the jumpPlayed
        // guard - so it slides along with its pose frozen. That is precisely the "gliding, animation paused, still
        // on all fours" the burster was doing. Nothing that is genuinely airborne has zero vertical velocity.
        var verticallyAirborne = Math.abs(boiler.getDeltaMovement().y) > AIRBORNE_VERTICAL_EPSILON;

        if (!boiler.onGround() && verticallyAirborne && !boiler.isUnderWater() && !isCrawling) {
            airborneTicks++;

            // Fire ONCE, on the tick the count EQUALS the floor - re-dispatching restarts the clip and freezes it.
            if (airborneTicks == AIRBORNE_TICKS_BEFORE_JUMP) {
                dispatcher.jump();
                jumpPlayed = true;
                return;
            }

            if (jumpPlayed) {
                return; // holding the last frame until the ground comes back
            }
        } else {
            if (jumpPlayed) {
                dispatcher.land();
                jumpPlayed = false;
                airborneTicks = 0;
                return;
            }

            airborneTicks = 0;
        }

        var isMovingOnGround = boiler.isMovingHorizontally.get() && boiler.onGround();
        Runnable animFunction;

        if (boiler.isUnderWater()) {
            // TODO: idle swim
            animFunction = dispatcher::swim;
        } else if (isMovingOnGround) {
            if (isCrawling) {
                animFunction = () -> dispatcher.crawl(AzAlienAnimationUtil.crawlAnimationSpeed(boiler));
            } else if (boiler.isMovingQuickly.get()) {
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
}
