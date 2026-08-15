package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ravager.Ravager;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ravager.RavagerAnimationRefs;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ravager.RavagerSpecialCleaveAttack;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class RavagerAnimator extends AzEntityAnimator<Ravager> {

    private static final String NAME = "ravager";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    private int previousAttackId = Integer.MIN_VALUE;

    /**
     * Crawl-edge tracker for the posture transitions. Null until first observed so a mid-crawl load doesn't replay a
     * drop.
     */
    private Boolean previousCrawling;

    /** Ticks the current crawl transition one-shot still owns the track. */
    private int crawlOneShotHoldTicks;

    /**
     * This caste authors an EMERGE-oriented molt clip ({@code molt.emerge}) rather than the {@code molt.enter} most
     * castes ship, so it stays on the emerge-first path: the clip plays forwards to emerge and backwards to cocoon in.
     * Left on the default it would ask for a {@code molt.enter} that does not exist.
     */
    /**
     * ⚠⚠ THE LOOP NAME WAS A LITERAL `"molting"` AND THE ART HAS NEVER CONTAINED IT — the loop half of every
     * warrior→ravager molt was bind-posing, silently. Sixth caste caught by this. Named through the Refs now.
     * <p>
     * Emerge-oriented, correctly: the ravager is a molt DESTINATION only, so it has no enter clip and the tracker makes
     * the cocooning half by reversing the emerge.
     * </p>
     */
    private final CocoonAnimationStateTracker<Ravager> cocoonAnimationStateTracker = new CocoonAnimationStateTracker<>(
        ravager -> RavagerAnimationRefs.MOLT_LOOP_ANIMATION_NAME,
        ravager -> RavagerAnimationRefs.MOLT_EMERGE_ANIMATION_NAME
    );

    /**
     * ⭐⭐ THE JUMP DIAL. Ticks airborne before the jump clip plays - RAISE IT if they look like they are hopping over
     * every stair and slab. Same figure as every other caste.
     */
    private static final int AIRBORNE_TICKS_BEFORE_JUMP = 3;

    /** Blocks per tick of vertical motion below which the entity counts as ground-bound, not falling. */
    private static final double AIRBORNE_VERTICAL_EPSILON = 0.08;

    private int airborneTicks;

    private boolean jumpPlayed;

    public RavagerAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<Ravager> animationTrackContainer) {
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.BODY)
                .setTransitionLength(5)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(Ravager animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(Ravager animatable, float partialTicks) {
        super.setCustomAnimations(animatable, partialTicks);

        if (cocoonAnimationStateTracker.run(animatable)) {
            return;
        }

        AzAlienHeadAnimationUtil.applyHeadLookFromBindPose(animatable, context(), partialTicks, "gNeck");

        runPassiveAnimations(animatable);
    }

    private void runPassiveAnimations(Ravager ravager) {
        var dispatcher = ravager.getAnimationDispatcher();

        // Crawl posture transitions - edge-driven one-shots off the synced crawl flag; a leg-loss collapse plays
        // the same drop clip at double speed with half the hold ([stated]). Above the attack block so a posture
        // change visually pre-empts a swing; the server blocks NEW attacks for the same window.
        if (crawlOneShotHoldTicks > 0) {
            crawlOneShotHoldTicks--;
            return;
        }
        boolean crawlingNow = ravager.getCrawlingManager().isCrawling();
        if (previousCrawling == null) {
            previousCrawling = crawlingNow;
        } else if (crawlingNow != previousCrawling) {
            previousCrawling = crawlingNow;
            if (crawlingNow) {
                var collapse = ravager.getCrawlingManager().isLegForcedCrawl();
                dispatcher.crawlDown(collapse ? 2.0F : 1.0F);
                crawlOneShotHoldTicks = collapse
                    ? Math.max(1, RavagerAnimationRefs.CRAWL_DOWN_TICKS / 2)
                    : RavagerAnimationRefs.CRAWL_DOWN_TICKS;
            } else {
                dispatcher.crawlUp();
                crawlOneShotHoldTicks = RavagerAnimationRefs.CRAWL_UP_TICKS;
            }
            return;
        }

        var attackType = ravager.attackType.get();
        var attackId = ravager.attackId.get();

        if (!attackType.isNone()) {
            if (attackId != previousAttackId) {
                var speed = calculateAttackSpeed(ravager, attackType);

                // ⭐ Prone, the bite has its own clip now (crawl.bite) rather than borrowing the standing one.
                if (attackType == Ravager.BITE && ravager.getCrawlingManager().isCrawling())
                    dispatcher.crawlBiteAttack();
                else if (attackType == Ravager.BITE)
                    dispatcher.biteAttack(speed);
                else if (attackType == Ravager.CLAW)
                    dispatcher.rightClawAttack(speed);
                else if (attackType == Ravager.CLAW_DOUBLE)
                    dispatcher.doubleClawAttack(speed);
                else if (attackType == Ravager.TAIL)
                    dispatcher.tailAttack(speed);
                else if (attackType == Ravager.SWIM_ATTACK)
                    dispatcher.swimAttack(speed);
                else if (attackType == Ravager.CRAWL_ATTACK)
                    dispatcher.crawlAttack(speed);
                else if (attackType == RavagerSpecialCleaveAttack.WINDUP)
                    dispatcher.specialCleaveWarmup(calculateWindupAnimationSpeed(ravager));
                else if (attackType == RavagerSpecialCleaveAttack.ATTACK)
                    dispatcher.specialCleaveActivate(speed);

                previousAttackId = attackId;
            }
            return;
        }

        var isCrawling = ravager.getCrawlingManager().isCrawling();

        // ⭐ AIRBORNE. ⚠ VERTICAL MOTION IS REQUIRED, NOT JUST !onGround: flickering ground contact would
        // otherwise dispatch the HOLD_ON_LAST_FRAME jump and then block every gait clip below, leaving the body
        // sliding with its pose frozen. Same guard as every other caste.
        if (
            !ravager.onGround()
                && Math.abs(ravager.getDeltaMovement().y) > AIRBORNE_VERTICAL_EPSILON
                && !ravager.isUnderWater()
                && !isCrawling
        ) {
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

        var isMovingOnGround = ravager.isMovingHorizontally.get() && ravager.onGround();
        Runnable animFunction;

        if (ravager.isUnderWater()) {
            animFunction = dispatcher::swim;
        } else if (isMovingOnGround) {
            if (isCrawling) {
                animFunction = () -> dispatcher.crawl(AzAlienAnimationUtil.crawlAnimationSpeed(ravager));
            } else if (ravager.isMovingQuickly.get()) {
                animFunction = dispatcher::run;
            } else {
                animFunction = dispatcher::walk;
            }
        } else {
            animFunction = isCrawling ? dispatcher::crawlHold : dispatcher::idle;
        }

        animFunction.run();
    }

    private float calculateAttackSpeed(Ravager ravager, AttackType attackType) {
        String animationName = null;

        if (attackType == Ravager.BITE)
            animationName = RavagerAnimationRefs.ATTACK_BITE_ANIMATION_NAME;
        else if (attackType == Ravager.CLAW)
            animationName = // Mirrored pair, same length - the right clip stands in for both when measuring.
                RavagerAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME;
        else if (attackType == Ravager.CLAW_DOUBLE)
            animationName = RavagerAnimationRefs.ATTACK_CLAW_DOUBLE_ANIMATION_NAME;
        else if (attackType == Ravager.TAIL)
            animationName = RavagerAnimationRefs.ATTACK_TAIL_ANIMATION_NAME;
        else if (attackType == Ravager.SWIM_ATTACK)
            animationName = RavagerAnimationRefs.SWIM_ATTACK_ANIMATION_NAME;
        else if (attackType == RavagerSpecialCleaveAttack.WINDUP)
            animationName = RavagerAnimationRefs.SPECIAL_ATTACK_WARMUP_ANIMATION_NAME;
        else if (attackType == RavagerSpecialCleaveAttack.ATTACK)
            animationName = RavagerAnimationRefs.SPECIAL_ATTACK_ACTIVATE_ANIMATION_NAME;

        var durationInTicks = ravager.attackDurationInTicks.get();

        if (animationName == null || durationInTicks <= 0) {
            return 1.0f;
        }

        var animation = getAnimation(ravager, animationName);

        // ⚠⚠ A MISSING CLIP MUST NEVER NPE THE RENDER THREAD. getAnimation returns NULL for a clip the loader
        // threw away - and GeckoLib discards an ENTIRE clip when one Molang expression fails to parse, so a
        // single bad keyframe in the art turns this line into a client crash the moment that animation is
        // selected. That is exactly what killed the game when a queen lost a leg: the forced crawl asked for
        // crawl.attack.*, which had been discarded, and this dereferenced null.
        if (animation == null) {
            return 1.0f;
        }

        return (float) (animation.length() / durationInTicks);
    }

    private float calculateWindupAnimationSpeed(Ravager ravager) {
        var animation = getAnimation(ravager, RavagerAnimationRefs.SPECIAL_ATTACK_WARMUP_ANIMATION_NAME);
        return (float) (animation.length() / RavagerSpecialCleaveAttack.WINDUP.defaultDurationInTicks());
    }
}
