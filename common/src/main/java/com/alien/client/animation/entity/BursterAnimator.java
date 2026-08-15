package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.burster.Burster;
import com.alien.common.gameplay.entity.living.alien.xenomorph.burster.BursterAnimationRefs;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class BursterAnimator extends AzEntityAnimator<Burster> {

    private static final String NAME = "burster";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    private int previousAttackId = Integer.MIN_VALUE;

    /** Edge detection for the lunge one-shot - see runPassiveAnimations. */
    private boolean wasLunging;

    /**
     * ⭐⭐ THE JUMP DIAL. Ticks airborne before the jump clip plays - RAISE IT if bursters look like they are hopping
     * over every stair and slab. Measured: a 1-2 tick flicker is a stumble, a 1-block ledge is about 3.
     */
    private static final int AIRBORNE_TICKS_BEFORE_JUMP = 3;

    /** Blocks per tick of vertical motion below which the entity counts as ground-bound, not falling. */
    private static final double AIRBORNE_VERTICAL_EPSILON = 0.08;

    private int airborneTicks;

    private boolean jumpPlayed;

    /**
     * ⚠⚠ EXPLICIT SELECTORS, NOT THE DEFAULT. The no-arg tracker hardcodes "molting" + "molt.enter"; naming them here
     * means a future rename of either clip is a one-line change in the Refs instead of a SILENT bind-pose mid-molt (the
     * ctor never mentions the names, so nothing would fail to compile).
     * <p>
     * ⭐ Enter-oriented with a real loop: molt.enter plays FORWARDS to cocoon and REVERSED to emerge, and molt.loop
     * covers the destination hold in between. That is his design - there is deliberately no authored emerge.
     * </p>
     */
    private final CocoonAnimationStateTracker<Burster> cocoonAnimationStateTracker = new CocoonAnimationStateTracker<>(
        burster -> BursterAnimationRefs.MOLT_LOOP_ANIMATION_NAME,
        burster -> BursterAnimationRefs.MOLT_ENTER_ANIMATION_NAME,
        true
    );

    public BursterAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<Burster> animationTrackContainer) {
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.BODY)
                .setTransitionLength(5)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(Burster animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(Burster animatable, float partialTicks) {
        super.setCustomAnimations(animatable, partialTicks);

        if (cocoonAnimationStateTracker.run(animatable)) {
            return;
        }

        AzAlienHeadAnimationUtil.applyHeadLookFromBindPose(animatable, context(), partialTicks, "gNeck");

        runPassiveAnimations(animatable);
    }

    private void runPassiveAnimations(Burster burster) {
        var dispatcher = burster.getAnimationDispatcher();

        // ⭐⭐ THE LUNGE IS A ONE-SHOT AND MUST BE DISPATCHED ONCE, NOT EVERY TICK.
        //
        // ⚠⚠ LUNGE is an AzCommand.replay() - re-dispatching RESTARTS it from frame 0. Sending it on every tick of
        // the lunge state pinned the body to the opening frame for the whole leap, so the mob slid along in a
        // FROZEN POSE. [stated] "it seems to start but it just glides without moving its limbs." It also RETURNS,
        // so the gait below never ran either - which is why walk and idle looked missing entirely while run and
        // crawl (reached in other states) were fine.
        //
        // ⚠ Same trap as the jump clips: edge-detect the flip, then let the clip own the track for its length.
        if (burster.isLunging.get()) {
            if (!wasLunging) {
                dispatcher.lunge();
                wasLunging = true;
            }
            return;
        }

        wasLunging = false;

        var attackType = burster.attackType.get();
        var attackId = burster.attackId.get();

        if (!attackType.isNone()) {
            if (attackId != previousAttackId) {
                var speed = calculateAttackSpeed(burster, attackType);

                // ⚠ POSTURE FIRST. A crawling burster must use its crawl clips or the body snaps upright for the
                // swing; underwater it has a bite-only attack clip.
                if (burster.isUnderWater()) {
                    dispatcher.swimAttack();
                } else if (burster.getCrawlingManager().isCrawling()) {
                    // ⚠ Route by the CRAWL type now that both exist, not by the standing BITE - the server already
                    // restricts a crawling caste to its crawl attacks.
                    if (attackType == Burster.CRAWL_BITE)
                        dispatcher.crawlBiteAttack(speed);
                    else
                        dispatcher.crawlAttack(speed);
                } else if (attackType == Burster.BITE)
                    dispatcher.biteAttack(speed);
                else if (attackType == Burster.CLAW)
                    dispatcher.clawAttack(speed);
                else if (attackType == Burster.TAIL)
                    dispatcher.tailAttack(speed);

                previousAttackId = attackId;
            }
            return;
        }

        var isCrawling = burster.getCrawlingManager().isCrawling();

        // ⭐ AIRBORNE. A TIME floor, not a height one - the animator cannot know how far something INTENDS to fall.
        // Stepping up a block never reaches here (maxUpStep keeps onGround true); walking off a ledge flicks it
        // false for a tick or two, so the floor filters stumbles from real jumps. AIRBORNE_TICKS_BEFORE_JUMP is THE
        // KNOB - raise it if bursters hop over every slab.
        // ⚠⚠ VERTICAL MOTION IS REQUIRED, NOT JUST !onGround. A mob whose ground contact FLICKERS while it walks
        // (a short caste on an uneven floor, resin ledges, a slab lip) would otherwise reach the airborne floor,
        // dispatch the HOLD_ON_LAST_FRAME jump, and then be blocked from every gait clip below by the jumpPlayed
        // guard - so it slides along with its pose frozen. That is precisely the "gliding, animation paused, still
        // on all fours" the burster was doing. Nothing that is genuinely airborne has zero vertical velocity.
        var verticallyAirborne = Math.abs(burster.getDeltaMovement().y) > AIRBORNE_VERTICAL_EPSILON;

        if (!burster.onGround() && verticallyAirborne && !burster.isUnderWater() && !isCrawling) {
            airborneTicks++;

            // Fire ONCE, on the tick the count EQUALS the floor. Re-dispatching restarts the clip and would freeze
            // it on its opening frames forever.
            if (airborneTicks == AIRBORNE_TICKS_BEFORE_JUMP) {
                dispatcher.jump();
                jumpPlayed = true;
                return;
            }

            if (jumpPlayed) {
                return; // holding the last frame of the jump until the ground comes back
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

        var isMoving = burster.isMovingHorizontally.get() && burster.onGround();
        Runnable animFunction;

        if (burster.isUnderWater()) {
            animFunction = dispatcher::swim;
        } else if (isMoving) {
            if (isCrawling) {
                animFunction = () -> dispatcher.crawl(AzAlienAnimationUtil.crawlAnimationSpeed(burster));
            } else if (burster.isMovingQuickly.get()) {
                animFunction = dispatcher::run;
            } else {
                animFunction = dispatcher::walk;
            }
        } else {
            animFunction = isCrawling ? dispatcher::crawlHold : dispatcher::idle;
        }

        animFunction.run();
    }

    private float calculateAttackSpeed(Burster burster, AttackType attackType) {
        String animationName;

        // The mirrored pair are the same length, so the right clip stands in for both when measuring.
        if (attackType == Burster.BITE)
            animationName = BursterAnimationRefs.ATTACK_BITE_ANIMATION_NAME;
        else if (attackType == Burster.CLAW)
            animationName = BursterAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME;
        else if (attackType == Burster.TAIL)
            animationName = BursterAnimationRefs.ATTACK_TAIL_ANIMATION_NAME;
        else
            animationName = null;

        var durationInTicks = burster.attackDurationInTicks.get();

        if (animationName == null || durationInTicks <= 0) {
            return 1.0f;
        }

        var animation = getAnimation(burster, animationName);

        // ⚠ A missing clip must never NPE the render thread - see DroneAnimator.calculateAttackSpeed.
        if (animation == null) {
            return 1.0f;
        }

        return (float) (animation.length() / durationInTicks);
    }
}
