package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.predalien.Predalien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.predalien.PredalienAnimationRefs;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class PredalienAnimator extends AzEntityAnimator<Predalien> {

    private static final String NAME = "predalien";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    private int previousAttackId = Integer.MIN_VALUE;

    /** Edge detection for the lunge one-shot - see runPassiveAnimations. */
    private boolean wasLunging;

    /** ⭐ THE JUMP DIAL - the same 3-tick floor every converted caste uses. RAISE IT if they hop over every slab. */
    private static final int AIRBORNE_TICKS_BEFORE_JUMP = 3;

    /** Blocks per tick of vertical motion below which the entity counts as ground-bound, not falling. */
    private static final double AIRBORNE_VERTICAL_EPSILON = 0.08;

    private int airborneTicks = 0;

    private boolean jumpPlayed = false;

    private final CocoonAnimationStateTracker<Predalien> cocoonAnimationStateTracker = new CocoonAnimationStateTracker<>(
        predalien -> PredalienAnimationRefs.MOLT_LOOP_ANIMATION_NAME,
        predalien -> PredalienAnimationRefs.MOLT_EMERGE_ANIMATION_NAME,
        predalien -> PredalienAnimationRefs.MOLT_ENTER_ANIMATION_NAME
    );

    public PredalienAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<Predalien> animationTrackContainer) {
        // Single track. Rebuilt from per-body-part clips onto whole-body ones, so the seven-track limb rig is gone.
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.BODY)
                .setTransitionLength(5)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(Predalien animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(Predalien animatable, float partialTicks) {
        super.setCustomAnimations(animatable, partialTicks);

        if (cocoonAnimationStateTracker.run(animatable)) {
            return;
        }

        AzAlienHeadAnimationUtil.applyHeadLookFromBindPose(animatable, context(), partialTicks, "gNeck");

        runPassiveAnimations(animatable);
    }

    private void runPassiveAnimations(Predalien predalien) {
        var dispatcher = predalien.getAnimationDispatcher();

        // Pounce owns the body for its duration, exactly as it does on the runner and prowler.
        // ⭐⭐ THE LUNGE IS A ONE-SHOT AND MUST BE DISPATCHED ONCE, NOT EVERY TICK.
        //
        // ⚠⚠ LUNGE is an AzCommand.replay() - re-dispatching RESTARTS it from frame 0. Sending it on every tick of
        // the lunge state pinned the body to the opening frame for the whole leap, so the mob slid along in a
        // FROZEN POSE. [stated] "it seems to start but it just glides without moving its limbs." It also RETURNS,
        // so the gait below never ran either - which is why walk and idle looked missing entirely while run and
        // crawl (reached in other states) were fine.
        //
        // ⚠ Same trap as the jump clips: edge-detect the flip, then let the clip own the track for its length.
        if (predalien.isLunging.get()) {
            if (!wasLunging) {
                dispatcher.lunge();
                wasLunging = true;
            }
            return;
        }

        wasLunging = false;

        var attackType = predalien.attackType.get();
        var attackId = predalien.attackId.get();

        if (!attackType.isNone()) {
            if (attackId != previousAttackId) {
                var speed = calculateAttackSpeed(predalien, attackType);

                // ⭐ Prone, the bite has its own clip now rather than borrowing the standing one.
                if (attackType == Predalien.CRAWL_BITE) {
                    dispatcher.crawlBiteAttack();
                } else if (attackType == Predalien.BITE) {
                    dispatcher.biteAttack(speed);
                } else if (attackType == Predalien.CLAW) {
                    dispatcher.rightClawAttack(speed);
                } else if (attackType == Predalien.TAIL) {
                    dispatcher.tailAttack(speed);
                } else if (attackType == Predalien.BACKHAND) {
                    dispatcher.backhandAttack(speed);
                } else if (attackType == Predalien.CRAWL_CLAW) {
                    dispatcher.crawlAttack(speed);
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
        if (predalien.onGround() || Math.abs(predalien.getDeltaMovement().y) <= AIRBORNE_VERTICAL_EPSILON) {
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
        var isMovingOnGround = predalien.isMovingHorizontally.get() && predalien.onGround();
        var isCrawling = predalien.getCrawlingManager().isCrawling();
        Runnable animFunction;

        if (predalien.isUnderWater()) {
            // TODO: idle swim
            animFunction = dispatcher::swim;
        } else if (isMovingOnGround) {
            if (isCrawling) {
                animFunction = () -> dispatcher.crawl(AzAlienAnimationUtil.crawlAnimationSpeed(predalien));
            } else if (predalien.isMovingQuickly.get()) {
                animFunction = dispatcher::run;
            } else {
                animFunction = dispatcher::walk;
            }
        } else {
            animFunction = isCrawling ? dispatcher::crawlHold : dispatcher::idle;
        }

        animFunction.run();
    }

    private float calculateAttackSpeed(Predalien predalien, AttackType attackType) {
        String animationName;

        if (attackType == Predalien.BITE) {
            animationName = PredalienAnimationRefs.ATTACK_BITE_ANIMATION_NAME;
        } else if (attackType == Predalien.CLAW) {
            // Mirrored pair, same length - the right clip stands in for both.
            animationName = PredalienAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME;
        } else if (attackType == Predalien.TAIL) {
            animationName = PredalienAnimationRefs.ATTACK_TAIL_ANIMATION_NAME;
        } else if (attackType == Predalien.BACKHAND) {
            // Mirrored pair, same length - the right clip stands in for both.
            animationName = PredalienAnimationRefs.ATTACK_BACKHAND_RIGHT_ANIMATION_NAME;
        } else if (attackType == Predalien.CRAWL_CLAW) {
            animationName = PredalienAnimationRefs.CRAWL_ATTACK_RIGHT_ANIMATION_NAME;
        } else {
            animationName = null;
        }

        var durationInTicks = predalien.attackDurationInTicks.get();

        if (animationName == null || durationInTicks <= 0) {
            return 1.0f;
        }

        var animation = getAnimation(predalien, animationName);

        // ⚠ A missing clip must never NPE the render thread - see DroneAnimator.calculateAttackSpeed.
        if (animation == null) {
            return 1.0f;
        }

        return (float) (animation.length() / durationInTicks);
    }
}
