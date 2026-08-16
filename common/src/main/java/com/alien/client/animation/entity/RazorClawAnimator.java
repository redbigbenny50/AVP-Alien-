package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.razor_claw.RazorClaw;
import com.alien.common.gameplay.entity.living.alien.xenomorph.razor_claw.RazorClawAnimationRefs;
import com.alien.common.gameplay.entity.living.alien.xenomorph.razor_claw.RazorClawSweepAttack;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class RazorClawAnimator extends AzEntityAnimator<RazorClaw> {

    private static final String NAME = "razor_claw";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    private int previousAttackId = Integer.MIN_VALUE;

    /**
     * This caste authors an EMERGE-oriented molt clip ({@code molt.emerge}) rather than the {@code molt.enter} most
     * castes ship, so it stays on the emerge-first path: the clip plays forwards to emerge and backwards to cocoon in.
     * Left on the default it would ask for a {@code molt.enter} that does not exist.
     */
    /**
     * ⚠⚠ THE LOOP NAME WAS A LITERAL `"molting"` AND THE ART HAS NEVER CONTAINED IT — the loop half of every
     * drone→razor_claw molt was bind-posing, silently. Now named through the Refs so a future rename is one line.
     * <p>
     * Emerge-oriented, correctly: the razor claw is a molt DESTINATION only, so it has no enter clip and the tracker
     * makes the cocooning half by reversing the emerge.
     * </p>
     */
    private final CocoonAnimationStateTracker<RazorClaw> cocoonAnimationStateTracker = new CocoonAnimationStateTracker<>(
        razorClaw -> RazorClawAnimationRefs.MOLT_LOOP_ANIMATION_NAME,
        razorClaw -> RazorClawAnimationRefs.MOLT_EMERGE_ANIMATION_NAME
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

    /** Edge detection for the dodge - the id is bumped server-side on every dodge. */
    private int previousDodgeId = -1;

    /**
     * ⚠ THE FLURRY REPEAT COUNTER. `attackId` bumps ONCE for the whole 4-blow flurry, so the ordinary attack-id
     * edge-detect would play the clip once and hold. This tracks the tick the flurry started and replays the clip on
     * each repeat boundary instead.
     */
    private int flurryTicksElapsed;

    private int flurryAttackId = -1;

    /** Edge detection for the BLOCKING crawl transitions - null until the first posture is observed. */
    private Boolean previousCrawling;

    public RazorClawAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<RazorClaw> animationTrackContainer) {
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.BODY)
                .setTransitionLength(5)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(RazorClaw animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(RazorClaw animatable, float partialTicks) {
        super.setCustomAnimations(animatable, partialTicks);

        if (cocoonAnimationStateTracker.run(animatable)) {
            return;
        }

        AzAlienHeadAnimationUtil.applyHeadLookFromBindPose(animatable, context(), partialTicks, "gNeck");

        runPassiveAnimations(animatable);
    }

    private void runPassiveAnimations(RazorClaw razorClaw) {
        var dispatcher = razorClaw.getAnimationDispatcher();

        // ⭐ THE DODGE OUTRANKS EVERYTHING. It is a reaction to a blow already being thrown, so nothing it was doing
        // beforehand should keep animating over it. Edge-detected on a server-bumped id, so it plays exactly once per
        // dodge and never re-triggers from a stale flag.
        var dodgeId = razorClaw.dodgeId.get();

        if (dodgeId != previousDodgeId) {
            previousDodgeId = dodgeId;

            if (dodgeId > 0) {
                dispatcher.dodge();
                return;
            }
        }

        var attackType = razorClaw.attackType.get();
        var attackId = razorClaw.attackId.get();

        if (!attackType.isNone()) {
            // ⭐⭐ THE FLURRY REPLAYS ITSELF. `attackId` bumps once for the whole flurry, so this counts ticks and
            // re-dispatches the clip on every repeat boundary - four blows off one attack id.
            if (attackType == RazorClaw.QUICK) {
                if (attackId != flurryAttackId) {
                    flurryAttackId = attackId;
                    flurryTicksElapsed = 0;
                    previousAttackId = attackId;
                    dispatcher.quickAttack(calculateAttackSpeed(razorClaw, attackType));
                    return;
                }

                flurryTicksElapsed++;

                if (flurryTicksElapsed % RazorClaw.QUICK_TICKS_PER_REPEAT == 0) {
                    dispatcher.quickAttack(calculateAttackSpeed(razorClaw, attackType));
                }
                return;
            }

            if (attackId != previousAttackId) {
                var speed = calculateAttackSpeed(razorClaw, attackType);

                // ⚠ POSTURE FIRST while crawling — the standing clips would snap the body upright for the swing.
                // ⚠ The SWIM attack and the AOE SPIN are exempt: each has exactly one authored form.
                if (
                    razorClaw.getCrawlingManager().isCrawling()
                        && attackType != RazorClaw.SWIM_ATTACK
                        && attackType != RazorClawSweepAttack.ATTACK
                ) {
                    if (attackType == RazorClaw.BITE)
                        dispatcher.crawlBiteAttack();
                    else
                        dispatcher.crawlAttack(speed);
                } else if (attackType == RazorClaw.BITE)
                    dispatcher.biteAttack(speed);
                else if (attackType == RazorClaw.CLAW)
                    dispatcher.rightClawAttack(speed);
                else if (attackType == RazorClaw.TAIL)
                    dispatcher.tailAttack(speed);
                else if (attackType == RazorClaw.SWIM_ATTACK)
                    dispatcher.swimAttack(speed);
                else if (attackType == RazorClawSweepAttack.ATTACK)
                    dispatcher.specialAttackSpin(speed);
                else if (attackType == RazorClaw.CHARGE)
                    dispatcher.chargeAttack(speed);

                previousAttackId = attackId;
            }
            return;
        }

        var isCrawling = razorClaw.getCrawlingManager().isCrawling();

        // ⭐ THE CRAWL TRANSITIONS. Both are BLOCKING: dispatched once on the flip and allowed to finish.
        // ⚠ A LOST LEG PLAYS THE SAME DROP CLIP FASTER rather than a different clip.
        if (previousCrawling == null) {
            previousCrawling = isCrawling;
        } else if (previousCrawling != isCrawling) {
            previousCrawling = isCrawling;

            if (isCrawling) {
                dispatcher.crawlDrop(razorClaw.getCrawlingManager().isLegForcedCrawl() ? 2.0F : 1.0F);
            } else {
                dispatcher.crawlRise();
            }
            return;
        }

        // ⭐ AIRBORNE. A TIME floor, not a height one - see AIRBORNE_TICKS_BEFORE_JUMP above.
        // ⚠⚠ VERTICAL MOTION IS REQUIRED, NOT JUST !onGround. A mob whose ground contact FLICKERS while it walks
        // (a short caste on an uneven floor, resin ledges, a slab lip) would otherwise reach the airborne floor,
        // dispatch the HOLD_ON_LAST_FRAME jump, and then be blocked from every gait clip below by the jumpPlayed
        // guard - so it slides along with its pose frozen. That is precisely the "gliding, animation paused, still
        // on all fours" the burster was doing. Nothing that is genuinely airborne has zero vertical velocity.
        var verticallyAirborne = Math.abs(razorClaw.getDeltaMovement().y) > AIRBORNE_VERTICAL_EPSILON;

        if (!razorClaw.onGround() && verticallyAirborne && !razorClaw.isUnderWater() && !isCrawling) {
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

        var isMovingOnGround = razorClaw.isMovingHorizontally.get() && razorClaw.onGround();
        Runnable animFunction;

        if (razorClaw.isUnderWater()) {
            animFunction = dispatcher::swim;
        } else if (isMovingOnGround) {
            if (isCrawling) {
                animFunction = () -> dispatcher.crawl(AzAlienAnimationUtil.crawlAnimationSpeed(razorClaw));
            } else if (razorClaw.isMovingQuickly.get()) {
                animFunction = dispatcher::run;
            } else {
                animFunction = dispatcher::walk;
            }
        } else {
            animFunction = isCrawling ? dispatcher::crawlHold : dispatcher::idle;
        }

        animFunction.run();
    }

    private float calculateAttackSpeed(RazorClaw razorClaw, AttackType attackType) {
        String animationName = null;

        if (attackType == RazorClaw.BITE)
            animationName = RazorClawAnimationRefs.ATTACK_BITE_ANIMATION_NAME;
        else if (attackType == RazorClaw.CLAW)
            // Mirrored pair, same length - the right clip stands in for both when measuring.
            animationName = RazorClawAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME;
        else if (attackType == RazorClaw.TAIL)
            animationName = RazorClawAnimationRefs.ATTACK_TAIL_ANIMATION_NAME;
        else if (attackType == RazorClaw.SWIM_ATTACK)
            animationName = RazorClawAnimationRefs.SWIM_ATTACK_ANIMATION_NAME;
        else if (attackType == RazorClawSweepAttack.ATTACK)
            animationName = RazorClawAnimationRefs.SPECIAL_ATTACK_SPIN_RIGHT_ANIMATION_NAME;
        else if (attackType == RazorClaw.QUICK)
            animationName = RazorClawAnimationRefs.ATTACK_QUICK_ANIMATION_NAME;
        else if (attackType == RazorClaw.CHARGE)
            // Mirrored pair, same length - the right clip stands in for both when measuring.
            animationName = RazorClawAnimationRefs.SPECIAL_ATTACK_CHARGE_RIGHT_ANIMATION_NAME;

        var durationInTicks = razorClaw.attackDurationInTicks.get();

        if (animationName == null || durationInTicks <= 0) {
            return 1.0f;
        }

        var animation = getAnimation(razorClaw, animationName);

        // ⚠ A missing clip must never NPE the render thread - see DroneAnimator.calculateAttackSpeed.
        if (animation == null) {
            return 1.0f;
        }

        return (float) (animation.length() / durationInTicks);
    }
}
