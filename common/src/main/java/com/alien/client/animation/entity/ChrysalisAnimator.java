package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.chrysalis.Chrysalis;
import com.alien.common.gameplay.entity.living.alien.xenomorph.chrysalis.ChrysalisAnimationRefs;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class ChrysalisAnimator extends AzEntityAnimator<Chrysalis> {

    private static final String NAME = "chrysalis";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    private int previousAttackId = Integer.MIN_VALUE;

    private boolean wasRolling = false;

    private int rollAnimationTicks = 0;

    /**
     * ⚠⚠ EXPLICIT SELECTORS. The no-arg tracker hardcodes "molting" + "molt.enter", and this art has NEVER contained a
     * clip called "molting" - so the loop half of every prowler→chrysalis molt was bind-posing SILENTLY (no crash, no
     * log, because the ctor never mentions the name and nothing fails to compile).
     * <p>
     * Enter-oriented: the chrysalis ships an enter and a loop but no emerge, so the tracker makes the emerge by
     * reversing the enter. That is unchanged and correct.
     * </p>
     */
    private final CocoonAnimationStateTracker<Chrysalis> cocoonAnimationStateTracker = new CocoonAnimationStateTracker<>(
        chrysalis -> ChrysalisAnimationRefs.MOLT_LOOP_ANIMATION_NAME,
        chrysalis -> ChrysalisAnimationRefs.MOLT_ENTER_ANIMATION_NAME,
        true
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

    /** Edge detection for the BLOCKING crawl transitions - null until the first posture is observed. */
    private Boolean previousCrawling;

    /** Edge detection for the defence stance - start on the way in, end on the way out, nothing in between. */
    private boolean wasDefending;

    public ChrysalisAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<Chrysalis> animationTrackContainer) {
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.BODY)
                .setTransitionLength(5)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(Chrysalis animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(Chrysalis animatable, float partialTicks) {
        super.setCustomAnimations(animatable, partialTicks);

        if (cocoonAnimationStateTracker.run(animatable)) {
            return;
        }

        AzAlienHeadAnimationUtil.applyHeadLookFromBindPose(animatable, context(), partialTicks, "gNeck");

        runPassiveAnimations(animatable);
    }

    private void runPassiveAnimations(Chrysalis chrysalis) {
        var dispatcher = chrysalis.getAnimationDispatcher();

        // ⭐⭐ THE DEFENSIVE CURL OUTRANKS EVERYTHING BELOW IT. [stated] "nothing can get it out of this stance", so
        // the gait, the attacks, the crawl and the roll must not animate over it. Edge-detected: defense.start is
        // dispatched ONCE and HOLDS its last frame for the whole 30 seconds (there is no authored loop), and
        // defense.end plays once on the way out.
        if (chrysalis.isDefending.get()) {
            if (!wasDefending) {
                dispatcher.defenseStart();
                wasDefending = true;
            }
            return;
        }

        if (wasDefending) {
            dispatcher.defenseEnd();
            wasDefending = false;
            return;
        }

        if (chrysalis.isRolling.get()) {
            if (!wasRolling) {
                dispatcher.rollStart();
                rollAnimationTicks = 0;
                wasRolling = true;
            } else if (rollAnimationTicks > 10) {
                dispatcher.rollLoop();
            }
            rollAnimationTicks++;
            return;
        }

        if (wasRolling) {
            if (chrysalis.rollWasSmashed.get()) {
                var speed = calculateStunSpeed(chrysalis);
                dispatcher.rollSmashed(speed);
            } else {
                dispatcher.rollStop();
            }
            wasRolling = false;
            rollAnimationTicks = 0;
            return;
        }

        if (chrysalis.isStunned.get()) {
            return;
        }

        var attackType = chrysalis.attackType.get();
        var attackId = chrysalis.attackId.get();

        if (!attackType.isNone()) {
            if (attackId != previousAttackId) {
                var speed = calculateAttackSpeed(chrysalis, attackType);

                // ⚠ POSTURE FIRST. Underwater it has a bite-only attack clip; crawling it must use the crawl clips
                // or the body snaps upright for the swing.
                if (chrysalis.isUnderWater())
                    dispatcher.swimAttack();
                else if (chrysalis.getCrawlingManager().isCrawling()) {
                    if (attackType == Chrysalis.BITE)
                        dispatcher.crawlBiteAttack();
                    else
                        dispatcher.crawlAttack(speed);
                } else if (attackType == Chrysalis.CHARGE)
                    dispatcher.chargeAttack(speed);
                else if (attackType == Chrysalis.BITE)
                    dispatcher.biteAttack(speed);
                else if (attackType == Chrysalis.CLAW)
                    dispatcher.rightClawAttack(speed);
                else if (attackType == Chrysalis.TAIL)
                    dispatcher.tailAttack(speed);

                previousAttackId = attackId;
            }
            return;
        }

        var isCrawling = chrysalis.getCrawlingManager().isCrawling();

        // ⭐ THE CRAWL TRANSITIONS. Both are BLOCKING: dispatched once on the flip and allowed to finish.
        // ⚠ A LOST LEG PLAYS THE SAME DROP CLIP FASTER rather than a different clip.
        if (previousCrawling == null) {
            previousCrawling = isCrawling;
        } else if (previousCrawling != isCrawling) {
            previousCrawling = isCrawling;

            if (isCrawling) {
                dispatcher.crawlDrop(chrysalis.getCrawlingManager().isLegForcedCrawl() ? 2.0F : 1.0F);
            } else {
                dispatcher.crawlRise();
            }
            return;
        }

        // ⭐ AIRBORNE. A TIME floor, not a height one - see AIRBORNE_TICKS_BEFORE_JUMP above.
        // ⚠ NOT while rolling: the roll has its own clip set and drives itself.
        // ⚠⚠ VERTICAL MOTION IS REQUIRED, NOT JUST !onGround. A mob whose ground contact FLICKERS while it walks
        // (a short caste on an uneven floor, resin ledges, a slab lip) would otherwise reach the airborne floor,
        // dispatch the HOLD_ON_LAST_FRAME jump, and then be blocked from every gait clip below by the jumpPlayed
        // guard - so it slides along with its pose frozen. That is precisely the "gliding, animation paused, still
        // on all fours" the burster was doing. Nothing that is genuinely airborne has zero vertical velocity.
        var verticallyAirborne = Math.abs(chrysalis.getDeltaMovement().y) > AIRBORNE_VERTICAL_EPSILON;

        if (!chrysalis.onGround() && verticallyAirborne && !chrysalis.isUnderWater() && !isCrawling) {
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

        var isMovingOnGround = chrysalis.isMovingHorizontally.get() && chrysalis.onGround();
        Runnable animFunction;

        if (chrysalis.isUnderWater()) {
            animFunction = dispatcher::swim;
        } else if (isMovingOnGround) {
            if (isCrawling) {
                animFunction = () -> dispatcher.crawl(AzAlienAnimationUtil.crawlAnimationSpeed(chrysalis));
            } else if (chrysalis.isMovingQuickly.get()) {
                animFunction = dispatcher::run;
            } else {
                animFunction = dispatcher::walk;
            }
        } else {
            animFunction = isCrawling ? dispatcher::crawlHold : dispatcher::idle;
        }

        animFunction.run();
    }

    private float calculateStunSpeed(Chrysalis chrysalis) {
        var durationInTicks = chrysalis.stunDurationTicks.get();

        if (durationInTicks <= 0) {
            return 1.0f;
        }

        var animation = getAnimation(chrysalis, ChrysalisAnimationRefs.ROLL_SMASHED_ANIMATION_NAME);

        // ⚠ A missing clip must never NPE the render thread - see DroneAnimator.calculateAttackSpeed.
        if (animation == null) {
            return 1.0f;
        }

        return (float) (animation.length() / durationInTicks);
    }

    private float calculateAttackSpeed(Chrysalis chrysalis, AttackType attackType) {
        String animationName;

        if (attackType == Chrysalis.CHARGE)
            animationName = ChrysalisAnimationRefs.ATTACK_CHARGE_ANIMATION_NAME;
        else if (attackType == Chrysalis.BITE)
            animationName = ChrysalisAnimationRefs.ATTACKBITE_ANIMATION_NAME;
        else if (attackType == Chrysalis.CLAW)
            // Mirrored pair, same length - the right clip stands in for both when measuring.
            animationName = ChrysalisAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME;
        else if (attackType == Chrysalis.TAIL)
            animationName = ChrysalisAnimationRefs.ATTACKTAIL_ANIMATION_NAME;
        else
            animationName = null;

        var durationInTicks = chrysalis.attackDurationInTicks.get();

        if (animationName == null || durationInTicks <= 0) {
            return 1.0f;
        }

        var animation = getAnimation(chrysalis, animationName);

        // ⚠ A missing clip must never NPE the render thread - see DroneAnimator.calculateAttackSpeed.
        if (animation == null) {
            return 1.0f;
        }

        return (float) (animation.length() / durationInTicks);
    }
}
