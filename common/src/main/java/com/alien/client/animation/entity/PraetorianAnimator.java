package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.praetorian.Praetorian;
import com.alien.common.gameplay.entity.living.alien.xenomorph.praetorian.PraetorianAnimationRefs;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class PraetorianAnimator extends AzEntityAnimator<Praetorian> {

    private static final String NAME = "praetorian";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    private int previousAttackId = Integer.MIN_VALUE;

    /** ⭐ THE JUMP DIAL - the same 3-tick floor every converted caste uses. RAISE IT if they hop over every slab. */
    private static final int AIRBORNE_TICKS_BEFORE_JUMP = 3;

    /** Blocks per tick of vertical motion below which the entity counts as ground-bound, not falling. */
    private static final double AIRBORNE_VERTICAL_EPSILON = 0.08;

    private int airborneTicks = 0;

    private boolean jumpPlayed = false;

    /**
     * ⭐⭐ THE FIRST CASTE WITH BOTH HALVES AUTHORED - enter, loop AND emerge - so it takes the three-selector
     * constructor and nothing is ever reversed. The two-selector forms would have discarded one of the two real clips
     * and substituted a reversal of the other.
     */
    private final CocoonAnimationStateTracker<Praetorian> cocoonAnimationStateTracker = new CocoonAnimationStateTracker<>(
        praetorian -> PraetorianAnimationRefs.MOLT_LOOP_ANIMATION_NAME,
        praetorian -> PraetorianAnimationRefs.MOLT_EMERGE_ANIMATION_NAME,
        praetorian -> PraetorianAnimationRefs.MOLT_ENTER_ANIMATION_NAME
    );

    public PraetorianAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<Praetorian> animationTrackContainer) {
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.BODY)
                .setTransitionLength(5)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(Praetorian animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(Praetorian animatable, float partialTicks) {
        super.setCustomAnimations(animatable, partialTicks);

        if (cocoonAnimationStateTracker.run(animatable)) {
            return;
        }

        AzAlienHeadAnimationUtil.applyHeadLookFromBindPose(animatable, context(), partialTicks, "gNeck");

        runPassiveAnimations(animatable);
    }

    private void runPassiveAnimations(Praetorian praetorian) {
        var dispatcher = praetorian.getAnimationDispatcher();

        var attackType = praetorian.attackType.get();
        var attackId = praetorian.attackId.get();

        if (!attackType.isNone()) {
            if (attackId != previousAttackId) {
                var speed = calculateAttackSpeed(praetorian, attackType);

                if (attackType == Praetorian.BITE) {
                    dispatcher.biteAttack(speed);
                } else if (attackType == Praetorian.CLAW) {
                    dispatcher.rightClawAttack(speed);
                } else if (attackType == Praetorian.TAIL) {
                    dispatcher.tailAttack(speed);
                } else if (attackType == Praetorian.BACKHAND) {
                    dispatcher.backhandAttack(speed);
                } else if (attackType == Praetorian.CRAWL_CLAW) {
                    dispatcher.crawlAttack(speed);
                } else if (attackType == Praetorian.CRAWL_BITE) {
                    dispatcher.crawlBiteAttack(speed);
                }

                previousAttackId = attackId;
            }
            return;
        }

        // AIRBORNE, ahead of every gait. Edge-detected: jump fires ONCE on the tick the floor is crossed and HOLDS,
        // and land only plays if a jump actually played, so a one-tick stumble produces neither.
        // ⚠⚠ VERTICAL MOTION IS REQUIRED, NOT JUST !onGround. A mob whose ground contact FLICKERS while it walks
        // (an uneven floor, resin ledges, a slab lip) would otherwise reach the airborne floor, dispatch the
        // HOLD_ON_LAST_FRAME jump, and then be blocked from every gait clip below by the jumpPlayed guard - so it
        // slides along with its pose frozen. Nothing genuinely airborne has zero vertical velocity.
        if (praetorian.onGround() || Math.abs(praetorian.getDeltaMovement().y) <= AIRBORNE_VERTICAL_EPSILON) {
            if (jumpPlayed) {
                dispatcher.land();
                jumpPlayed = false;
            }
            airborneTicks = 0;
        } else {
            airborneTicks++;

            if (airborneTicks == AIRBORNE_TICKS_BEFORE_JUMP) {
                dispatcher.jump();
                jumpPlayed = true;
            }

            if (jumpPlayed) {
                return;
            }
        }
        var isMovingOnGround = praetorian.isMovingHorizontally.get() && praetorian.onGround();
        var isCrawling = praetorian.getCrawlingManager().isCrawling();
        Runnable animFunction;

        if (praetorian.isUnderWater()) {
            // TODO: idle swim
            animFunction = dispatcher::swim;
        } else if (isMovingOnGround) {
            if (isCrawling) {
                animFunction = () -> dispatcher.crawl(AzAlienAnimationUtil.crawlAnimationSpeed(praetorian));
            } else if (praetorian.isMovingQuickly.get()) {
                animFunction = dispatcher::run;
            } else {
                animFunction = dispatcher::walk;
            }
        } else {
            animFunction = isCrawling ? dispatcher::crawlHold : dispatcher::idle;
        }

        animFunction.run();
    }

    private float calculateAttackSpeed(Praetorian praetorian, AttackType attackType) {
        String animationName;

        if (attackType == Praetorian.BITE) {
            animationName = PraetorianAnimationRefs.ATTACK_BITE_ANIMATION_NAME;
        } else if (attackType == Praetorian.CLAW) {
            // The mirrored pair are the same length, so the right clip stands in for both; which arm swings is
            // decided at dispatch, not here.
            animationName = PraetorianAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME;
        } else if (attackType == Praetorian.TAIL) {
            animationName = PraetorianAnimationRefs.ATTACK_TAIL_ANIMATION_NAME;
        } else if (attackType == Praetorian.BACKHAND) {
            // Mirrored pair, same length - the right clip stands in for both.
            animationName = PraetorianAnimationRefs.ATTACK_BACKHAND_RIGHT_ANIMATION_NAME;
        } else if (attackType == Praetorian.CRAWL_CLAW) {
            animationName = PraetorianAnimationRefs.CRAWL_ATTACK_RIGHT_ANIMATION_NAME;
        } else if (attackType == Praetorian.CRAWL_BITE) {
            animationName = PraetorianAnimationRefs.CRAWL_ATTACK_BITE_ANIMATION_NAME;
        } else {
            animationName = null;
        }

        var durationInTicks = praetorian.attackDurationInTicks.get();

        if (animationName == null || durationInTicks <= 0) {
            return 1.0f;
        }

        var animation = getAnimation(praetorian, animationName);

        // ⚠ A missing clip must never NPE the render thread - see DroneAnimator.calculateAttackSpeed.
        if (animation == null) {
            return 1.0f;
        }

        return (float) (animation.length() / durationInTicks);
    }
}
