package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.spitter.Spitter;
import com.alien.common.gameplay.entity.living.alien.xenomorph.spitter.SpitterAnimationRefs;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class SpitterAnimator extends AzEntityAnimator<Spitter> {

    private static final String NAME = "spitter";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    private int previousAttackId = Integer.MIN_VALUE;

    /** Edge detection for the lunge one-shot - see runPassiveAnimations. */
    private boolean wasLunging;

    /**
     * Spitter molt - ⚠ EMERGE-ORIENTED, unlike every caste converted so far.
     * <p>
     * [stated] "this one has a molt emerge but no enter so it doesnt need to be reversed." The spitter has NO
     * {@code molt.enter}, so the final {@code false} matters: the emerge clip is authored forwards and is played
     * forwards. Passing {@code true} here would try to run a nonexistent enter clip backwards.
     * </p>
     * <p>
     * ⚠ THERE IS NO LOOP CLIP AND THERE NEVER WILL BE. [stated] "the spitter also wont get a molt.loop because it
     * emerges from the previous forms loop ... it never changes form" - confirmed against the growth stages, where
     * nothing molts FROM a spitter. The destination window is collapsed to one tick for adolescent molts, so this
     * selector is reached for at most a single tick; it points at the emerge clip rather than the shared
     * {@code molting} default purely so it can never resolve to a name the model does not contain.
     * </p>
     */
    private final CocoonAnimationStateTracker<Spitter> cocoonAnimationStateTracker = new CocoonAnimationStateTracker<>(
        spitter -> SpitterAnimationRefs.MOLT_EMERGE_ANIMATION_NAME,
        spitter -> SpitterAnimationRefs.MOLT_EMERGE_ANIMATION_NAME,
        false
    );

    /** ⭐ THE JUMP DIAL - same 3-tick floor as the other castes. */
    private static final int AIRBORNE_TICKS_BEFORE_JUMP = 3;

    /** Blocks per tick of vertical motion below which the entity counts as ground-bound, not falling. */
    private static final double AIRBORNE_VERTICAL_EPSILON = 0.08;

    private int airborneTicks = 0;

    private boolean jumpPlayed = false;

    public SpitterAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<Spitter> animationTrackContainer) {
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.BODY)
                .setTransitionLength(5)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(Spitter animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(Spitter animatable, float partialTicks) {
        super.setCustomAnimations(animatable, partialTicks);

        if (cocoonAnimationStateTracker.run(animatable)) {
            return;
        }

        AzAlienHeadAnimationUtil.applyHeadLookFromBindPose(animatable, context(), partialTicks, "gNeck");

        runPassiveAnimations(animatable);
    }

    private void runPassiveAnimations(Spitter spitter) {
        var dispatcher = spitter.getAnimationDispatcher();

        // ⭐⭐ THE LUNGE IS A ONE-SHOT AND MUST BE DISPATCHED ONCE, NOT EVERY TICK.
        //
        // ⚠⚠ LUNGE is an AzCommand.replay() - re-dispatching RESTARTS it from frame 0. Sending it on every tick of
        // the lunge state pinned the body to the opening frame for the whole leap, so the mob slid along in a
        // FROZEN POSE. [stated] "it seems to start but it just glides without moving its limbs." It also RETURNS,
        // so the gait below never ran either - which is why walk and idle looked missing entirely while run and
        // crawl (reached in other states) were fine.
        //
        // ⚠ Same trap as the jump clips: edge-detect the flip, then let the clip own the track for its length.
        if (spitter.isLunging.get()) {
            if (!wasLunging) {
                dispatcher.lunge();
                wasLunging = true;
            }
            return;
        }

        wasLunging = false;

        var attackType = spitter.attackType.get();
        var attackId = spitter.attackId.get();

        if (!attackType.isNone()) {
            if (attackId != previousAttackId) {
                var speed = calculateAttackSpeed(spitter, attackType);

                // ⭐ POSTURE PICKS THE CLIP SET. The flag is frozen for the whole swing on the server side, so this
                // reads the same value from first frame to last - and the same one the limb hitboxes replay.
                // The SPIT is deliberately absent from this branch: it has no quad clip and stands the spitter up.
                var quad = spitter.isQuadPosture.get();

                // ⭐ PRONE FIRST. A leg-crawling spitter must use its crawl clips - the server already restricts it
                // to the crawl attack types, so reaching here with one means it IS prone.
                if (attackType == Spitter.CRAWL_BITE) {
                    dispatcher.crawlBiteAttack();
                } else if (attackType == Spitter.CRAWL_CLAW) {
                    dispatcher.crawlAttack();
                } else if (attackType == Spitter.BITE) {
                    if (quad) {
                        dispatcher.biteAttackQuad(speed);
                    } else {
                        dispatcher.biteAttack(speed);
                    }
                } else if (attackType == Spitter.CLAW) {
                    if (quad) {
                        dispatcher.clawAttackQuad(speed);
                    } else {
                        dispatcher.rightClawAttack(speed);
                    }
                } else if (attackType == Spitter.TAIL) {
                    if (quad) {
                        dispatcher.tailAttackQuad(speed);
                    } else {
                        dispatcher.tailAttack(speed);
                    }
                } else if (attackType == Spitter.SPIT) {
                    dispatcher.spitAttack(speed);
                }

                previousAttackId = attackId;
            }
            return;
        }

        // AIRBORNE, ahead of every gait. The spitter DOES have jump and land clips, so it behaves like the drone and
        // warrior rather than lunging like the runner and prowler. Edge-detected: jump fires once when the floor is
        // crossed and HOLDS; land only plays if a jump actually played, so a one-tick stumble produces neither.
        // ⚠⚠ VERTICAL MOTION IS REQUIRED, NOT JUST !onGround. A mob whose ground contact FLICKERS while it walks
        // (an uneven floor, resin ledges, a slab lip) would otherwise reach the airborne floor, dispatch the
        // HOLD_ON_LAST_FRAME jump, and then be blocked from every gait clip below by the jumpPlayed guard - so it
        // slides along with its pose frozen. Nothing genuinely airborne has zero vertical velocity.
        if (spitter.onGround() || Math.abs(spitter.getDeltaMovement().y) <= AIRBORNE_VERTICAL_EPSILON) {
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

        var isMovingOnGround = spitter.isMovingHorizontally.get() && spitter.onGround();
        var isCrawling = spitter.getCrawlingManager().isCrawling();
        Runnable animFunction;

        if (spitter.isUnderWater()) {
            // TODO: idle swim
            animFunction = dispatcher::swim;
        } else if (isMovingOnGround) {
            if (isCrawling) {
                animFunction = () -> dispatcher.crawl(AzAlienAnimationUtil.crawlAnimationSpeed(spitter));
            } else if (spitter.isMovingQuickly.get()) {
                animFunction = dispatcher::run;
            } else {
                animFunction = dispatcher::walk;
            }
        } else {
            // TODO: idle crawl
            //
            // ⭐ Standing still is where posture is HELD rather than derived. A spitter that charged in and stopped
            // stays down on all fours in quad.idle instead of rearing up, which is what keeps its follow-up swings
            // in the quad set. It stands up again when it walks, when it spits, or when it leaves the ground.
            animFunction = isCrawling
                ? dispatcher::crawlHold
                : spitter.isQuadPosture.get() ? dispatcher::quadIdle : dispatcher::idle;
        }

        animFunction.run();
    }

    /**
     * Fits the chosen clip to the attack's duration.
     * <p>
     * ⚠ IT HAS TO MEASURE THE CLIP THAT WILL ACTUALLY PLAY. The quad and biped clips of the same attack are not
     * necessarily the same length, so measuring the biped one and then dispatching the quad one would run the swing at
     * the wrong speed. Which ARM swings still does not matter - the left and right of a mirrored pair are the same
     * length by construction - so the right-hand clip stands in for the pair.
     * </p>
     */
    private float calculateAttackSpeed(Spitter spitter, AttackType attackType) {
        String animationName;

        var quad = spitter.isQuadPosture.get();

        if (attackType == Spitter.BITE) {
            animationName = quad
                ? SpitterAnimationRefs.QUAD_ATTACK_BITE_ANIMATION_NAME
                : SpitterAnimationRefs.ATTACK_BITE_ANIMATION_NAME;
        } else if (attackType == Spitter.CLAW) {
            animationName = quad
                ? SpitterAnimationRefs.QUAD_ATTACK_CLAW_RIGHT_ANIMATION_NAME
                : SpitterAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME;
        } else if (attackType == Spitter.TAIL) {
            animationName = quad
                ? SpitterAnimationRefs.QUAD_ATTACK_TAIL_ANIMATION_NAME
                : SpitterAnimationRefs.ATTACK_TAIL_ANIMATION_NAME;
        } else if (attackType == Spitter.SPIT) {
            animationName = SpitterAnimationRefs.SPECIAL_ATTACK_SPIT_ANIMATION_NAME;
        } else {
            animationName = null;
        }

        var durationInTicks = spitter.attackDurationInTicks.get();

        if (animationName == null || durationInTicks <= 0) {
            return 1.0f;
        }

        var animation = getAnimation(spitter, animationName);

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
