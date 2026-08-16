package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.prowler.Prowler;
import com.alien.common.gameplay.entity.living.alien.xenomorph.prowler.ProwlerAnimationRefs;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class ProwlerAnimator extends AzEntityAnimator<Prowler> {

    private static final String NAME = "prowler";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    private int previousAttackId = Integer.MIN_VALUE;

    /** Edge detection for the lunge one-shot - see runPassiveAnimations. */
    private boolean wasLunging;

    /**
     * ⭐ THE JUMP DIAL - the same 3-tick floor every other converted caste uses.
     * <p>
     * A TIME floor, not a height one: the animator cannot know how far something INTENDS to fall, only how long it has
     * been off the ground. Stepping UP a 1-block rise never reaches this code (maxUpStep is 1.5, so onGround stays
     * true), but walking off a ledge or crossing stairs, slabs and resin ribs can flick it false for a tick or two.
     * RAISE THIS if they look like they are hopping over every step.
     * </p>
     */
    private static final int AIRBORNE_TICKS_BEFORE_JUMP = 3;

    /** Blocks per tick of vertical motion below which the entity counts as ground-bound, not falling. */
    private static final double AIRBORNE_VERTICAL_EPSILON = 0.08;

    private int airborneTicks = 0;

    private boolean jumpPlayed = false;

    /**
     * Prowler molt: {@code molt.enter} -> {@code molt.loop} -> emerge. Only the LOOP is overridden while other castes
     * still ship {@code molting}; ENTER-ORIENTED, so the clip runs backwards to emerge.
     */
    private final CocoonAnimationStateTracker<Prowler> cocoonAnimationStateTracker = new CocoonAnimationStateTracker<>(
        prowler -> ProwlerAnimationRefs.MOLT_LOOP_ANIMATION_NAME,
        prowler -> ProwlerAnimationRefs.MOLT_ENTER_ANIMATION_NAME,
        true
    );

    public ProwlerAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<Prowler> animationTrackContainer) {
        // Single track. The prowler was rebuilt from per-body-part clips onto whole-body ones, so the seven-track
        // limb rig it used to need is gone - everything plays on BODY, exactly like the runner.
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.BODY)
                .setTransitionLength(5)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(Prowler animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(Prowler animatable, float partialTicks) {
        super.setCustomAnimations(animatable, partialTicks);

        if (cocoonAnimationStateTracker.run(animatable)) {
            return;
        }

        AzAlienHeadAnimationUtil.applyHeadLookFromBindPose(animatable, context(), partialTicks, "gNeck");

        runPassiveAnimations(animatable);
    }

    private void runPassiveAnimations(Prowler prowler) {
        var dispatcher = prowler.getAnimationDispatcher();

        // ⭐⭐ THE LUNGE IS A ONE-SHOT AND MUST BE DISPATCHED ONCE, NOT EVERY TICK.
        //
        // ⚠⚠ LUNGE is an AzCommand.replay() - re-dispatching RESTARTS it from frame 0. Sending it on every tick of
        // the lunge state pinned the body to the opening frame for the whole leap, so the mob slid along in a
        // FROZEN POSE. [stated] "it seems to start but it just glides without moving its limbs." It also RETURNS,
        // so the gait below never ran either - which is why walk and idle looked missing entirely while run and
        // crawl (reached in other states) were fine.
        //
        // ⚠ Same trap as the jump clips: edge-detect the flip, then let the clip own the track for its length.
        if (prowler.isLunging.get()) {
            if (!wasLunging) {
                dispatcher.lunge();
                wasLunging = true;
            }
            return;
        }

        wasLunging = false;

        var attackType = prowler.attackType.get();
        var attackId = prowler.attackId.get();

        if (!attackType.isNone()) {
            if (attackId != previousAttackId) {
                var speed = calculateAttackSpeed(prowler, attackType);

                // ⭐ PRONE ATTACKS - the server restricts a crawling caste to crawl AttackTypes, so reaching here
                // with one means it IS prone. [stated] "the attacks they can do are only the crawl ones."
                if (attackType == Prowler.CRAWL_BITE) {
                    dispatcher.crawlBiteAttack();
                } else if (attackType == Prowler.CRAWL_CLAW) {
                    dispatcher.crawlAttack();
                } else if (attackType == Prowler.BITE) {
                    dispatcher.biteAttack(speed);
                } else if (attackType == Prowler.CLAW) {
                    dispatcher.rightClawAttack(speed);
                } else if (attackType == Prowler.TAIL_QUAD) {
                    dispatcher.tailAttackQuad(speed);
                }

                previousAttackId = attackId;
            }
            return;
        }

        // AIRBORNE, ahead of every gait. ⚠ NEW for this caste - its art now ships jump and land, so it behaves like
        // the drone and warrior. Edge-detected: jump fires ONCE on the tick the floor is crossed and HOLDS, and land
        // only plays if a jump actually played, so a one-tick stumble produces neither. Below the floor this falls
        // THROUGH to the gait rather than returning, so a brief flicker never freezes the walk cycle.
        // ⚠⚠ VERTICAL MOTION IS REQUIRED, NOT JUST !onGround. A mob whose ground contact FLICKERS while it walks
        // (an uneven floor, resin ledges, a slab lip) would otherwise reach the airborne floor, dispatch the
        // HOLD_ON_LAST_FRAME jump, and then be blocked from every gait clip below by the jumpPlayed guard - so it
        // slides along with its pose frozen. Nothing genuinely airborne has zero vertical velocity.
        if (prowler.onGround() || Math.abs(prowler.getDeltaMovement().y) <= AIRBORNE_VERTICAL_EPSILON) {
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
        var isMoving = prowler.isMovingHorizontally.get() && prowler.onGround();
        var isCrawling = prowler.getCrawlingManager().isCrawling();
        Runnable animFunction;

        if (prowler.isUnderWater()) {
            // TODO: idle swim
            animFunction = dispatcher::swim;
        } else if (isMoving) {
            if (isCrawling) {
                animFunction = () -> dispatcher.crawl(AzAlienAnimationUtil.crawlAnimationSpeed(prowler));
            } else if (prowler.isMovingQuickly.get()) {
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

    private float calculateAttackSpeed(Prowler prowler, AttackType attackType) {
        String animationName;

        if (attackType == Prowler.BITE) {
            animationName = ProwlerAnimationRefs.ATTACK_BITE_ANIMATION_NAME;
        } else if (attackType == Prowler.CLAW) {
            animationName = ProwlerAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME;
        } else if (attackType == Prowler.TAIL_QUAD) {
            animationName = ProwlerAnimationRefs.ATTACK_TAIL_ANIMATION_NAME;
        } else {
            animationName = null;
        }

        var durationInTicks = prowler.attackDurationInTicks.get();

        if (animationName == null || durationInTicks <= 0) {
            return 1.0f;
        }

        var animation = getAnimation(prowler, animationName);

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
}
