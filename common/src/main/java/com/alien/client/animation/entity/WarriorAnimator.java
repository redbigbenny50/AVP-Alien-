package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.warrior.Warrior;
import com.alien.common.gameplay.entity.living.alien.xenomorph.warrior.WarriorAnimationRefs;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class WarriorAnimator extends AzEntityAnimator<Warrior> {

    private static final String NAME = "warrior";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    private int previousAttackId = Integer.MIN_VALUE;

    /** Edge detection for the lunge one-shot - see runPassiveAnimations. */
    private boolean wasLunging;

    /**
     * Warrior molt: {@code molt.enter} -> {@code molt.loop} -> emerge.
     * <p>
     * Only the LOOP is overridden - the warrior's art renamed {@code molting} to {@code molt.loop} while most castes
     * still ship the old name, so it opts in here rather than changing the shared default. ENTER-ORIENTED ({@code
     * true}): the clip runs forwards to cocoon and backwards to emerge, so there is no separate emerge clip. Passing
     * false is the queen's orientation and would run the whole molt in reverse.
     * </p>
     */
    private final CocoonAnimationStateTracker<Warrior> cocoonAnimationStateTracker = new CocoonAnimationStateTracker<>(
        warrior -> WarriorAnimationRefs.MOLT_LOOP_ANIMATION_NAME,
        warrior -> WarriorAnimationRefs.MOLT_ENTER_ANIMATION_NAME,
        true
    );

    /**
     * ⭐ THE JUMP DIAL for the warrior - same figure and same reasoning as {@code DroneAnimator}. Raise it if warriors
     * look like they are hopping over every stair and slab; a TIME floor, because the animator only knows how long it
     * has been airborne, not how far it means to fall.
     */
    private static final int AIRBORNE_TICKS_BEFORE_JUMP = 3;

    /** Blocks per tick of vertical motion below which the entity counts as ground-bound, not falling. */
    private static final double AIRBORNE_VERTICAL_EPSILON = 0.08;

    private int airborneTicks = 0;

    /** Only land if a jump actually played - a one-tick stumble must produce neither. */
    private boolean jumpPlayed = false;

    public WarriorAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<Warrior> animationTrackContainer) {
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.BODY)
                .setTransitionLength(5)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(Warrior animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(Warrior animatable, float partialTicks) {
        super.setCustomAnimations(animatable, partialTicks);

        if (cocoonAnimationStateTracker.run(animatable)) {
            return;
        }

        AzAlienHeadAnimationUtil.applyHeadLookFromBindPose(animatable, context(), partialTicks, "gNeck");

        runPassiveAnimations(animatable);
    }

    private void runPassiveAnimations(Warrior warrior) {
        var dispatcher = warrior.getAnimationDispatcher();

        // ⭐⭐ THE LUNGE IS A ONE-SHOT AND MUST BE DISPATCHED ONCE, NOT EVERY TICK.
        //
        // ⚠⚠ LUNGE is an AzCommand.replay() - re-dispatching RESTARTS it from frame 0. Sending it on every tick of
        // the lunge state pinned the body to the opening frame for the whole leap, so the mob slid along in a
        // FROZEN POSE. [stated] "it seems to start but it just glides without moving its limbs." It also RETURNS,
        // so the gait below never ran either - which is why walk and idle looked missing entirely while run and
        // crawl (reached in other states) were fine.
        //
        // ⚠ Same trap as the jump clips: edge-detect the flip, then let the clip own the track for its length.
        if (warrior.isLunging.get()) {
            if (!wasLunging) {
                dispatcher.lunge();
                wasLunging = true;
            }
            return;
        }

        wasLunging = false;

        var attackType = warrior.attackType.get();
        var attackId = warrior.attackId.get();

        if (!attackType.isNone()) {
            if (attackId != previousAttackId) {
                var speed = calculateAttackSpeed(warrior, attackType);

                // ⭐ PRONE ATTACKS - the server restricts a crawling caste to crawl AttackTypes, so reaching here
                // with one means it IS prone. [stated] "the attacks they can do are only the crawl ones."
                if (attackType == Warrior.CRAWL_BITE) {
                    dispatcher.crawlBiteAttack();
                } else if (attackType == Warrior.CRAWL_CLAW) {
                    dispatcher.crawlAttack();
                } else if (attackType == Warrior.BITE) {
                    dispatcher.biteAttack(speed);
                } else if (attackType == Warrior.CLAW) {
                    dispatcher.rightClawAttack(speed);
                } else if (attackType == Warrior.TAIL) {
                    dispatcher.tailAttack(speed);
                }

                previousAttackId = attackId;
            }
            return;
        }

        // AIRBORNE, ahead of every gait. Jump fires ONCE when the floor is crossed and holds its last frame for the
        // whole flight; land fires on touchdown. Below the floor it falls through on purpose, so a one-tick stumble
        // off a slab keeps walking instead of flickering.
        // ⚠⚠ VERTICAL MOTION IS REQUIRED, NOT JUST !onGround. A mob whose ground contact FLICKERS while it walks
        // (an uneven floor, resin ledges, a slab lip) would otherwise reach the airborne floor, dispatch the
        // HOLD_ON_LAST_FRAME jump, and then be blocked from every gait clip below by the jumpPlayed guard - so it
        // slides along with its pose frozen. Nothing genuinely airborne has zero vertical velocity.
        if (warrior.onGround() || Math.abs(warrior.getDeltaMovement().y) <= AIRBORNE_VERTICAL_EPSILON) {
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

        var isMoving = warrior.isMovingHorizontally.get() && warrior.onGround();
        var isCrawling = warrior.getCrawlingManager().isCrawling();
        Runnable animFunction;

        if (warrior.isUnderWater()) {
            // TODO: idle swim
            animFunction = dispatcher::swim;
        } else if (isMoving) {
            if (isCrawling) {
                animFunction = () -> dispatcher.crawl(AzAlienAnimationUtil.crawlAnimationSpeed(warrior));
            } else if (warrior.isMovingQuickly.get()) {
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

    private float calculateAttackSpeed(Warrior warrior, AttackType attackType) {
        String animationName;

        if (attackType == Warrior.BITE) {
            animationName = WarriorAnimationRefs.ATTACK_BITE_ANIMATION_NAME;
        } else if (attackType == Warrior.CLAW) {
            animationName = WarriorAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME;
        } else if (attackType == Warrior.TAIL) {
            animationName = WarriorAnimationRefs.ATTACK_TAIL_ANIMATION_NAME;
        } else {
            animationName = null;
        }

        var durationInTicks = warrior.attackDurationInTicks.get();

        if (animationName == null || durationInTicks <= 0) {
            return 1.0f;
        }

        var animation = getAnimation(warrior, animationName);
        return (float) (animation.length() / durationInTicks);
    }
}
