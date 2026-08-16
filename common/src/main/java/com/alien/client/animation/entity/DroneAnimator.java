package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.drone.Drone;
import com.alien.common.gameplay.entity.living.alien.xenomorph.drone.DroneAnimationRefs;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class DroneAnimator extends AzEntityAnimator<Drone> {

    /**
     * ⭐ THE JUMP DIAL. How long the drone must be off the ground before the jump animation plays at all.
     * <p>
     * [stated] "i guess we will keep it at 3 but keep this in memory because if we need to change later or they keep
     * jump falling all over the place we can easily find and adjust it." **RAISE THIS NUMBER if drones look like they
     * are hopping over every stair and slab.**
     * </p>
     * <p>
     * WHY A TIME FLOOR AND NOT A HEIGHT. The animator cannot know how far something INTENDS to fall - only how long it
     * has been in the air. Stepping UP a single block never gets here at all (aliens have a 1.5 step height, so
     * {@code onGround} stays true), but walking OFF a one-block ledge, or crossing stairs, slabs and resin ribs, can
     * flick {@code onGround} false for a tick or two. Without a floor each of those would fire a jump/land pair -
     * restarting the clip and freezing it on its opening frames, the same failure the gait block below warns about.
     * </p>
     * <p>
     * 3 ticks is roughly a one-block fall; a two-block drop is airborne 5-6 ticks and clears it comfortably. The cost
     * is that the airborne pose starts 0.15s late - invisible in practice, because the clip HOLDS its last frame and so
     * still covers the whole flight.
     * </p>
     */
    private static final int AIRBORNE_TICKS_BEFORE_JUMP = 3;

    /** Blocks per tick of vertical motion below which the entity counts as ground-bound, not falling. */
    private static final double AIRBORNE_VERTICAL_EPSILON = 0.08;

    /** Previous ground state, so jump and land fire on the EDGE rather than every airborne tick. */
    private boolean wasOnGround = true;

    private int airborneTicks = 0;

    /** Only land if a jump actually played - a one-tick stumble must produce neither. */
    private boolean jumpPlayed = false;

    private static final String NAME = "drone";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    private int previousAttackId = Integer.MIN_VALUE;

    /** Edge detection for the lunge one-shot - see runPassiveAnimations. */
    private boolean wasLunging;

    /**
     * Drone molt: {@code molt.enter} -> {@code molt.loop} -> the warrior's own emerge.
     * <p>
     * Only the LOOP is overridden. The drone's art renamed {@code molting} to {@code molt.loop}, but the other eleven
     * castes still ship the old name, so the shared default stays put and the drone opts in here - a per-caste switch
     * as each animation file is converted, with nothing broken in between.
     * </p>
     * <p>
     * ENTER-ORIENTED ({@code true}) is the important half: the drone wraps ITSELF up, so the clip runs forwards to
     * cocoon and backwards to emerge, and there is no separate emerge animation to author. Passing false here would run
     * the whole molt in reverse - that is the queen's orientation, because she is only ever a destination.
     * </p>
     */
    private final CocoonAnimationStateTracker<Drone> cocoonAnimationStateTracker = new CocoonAnimationStateTracker<>(
        drone -> DroneAnimationRefs.MOLT_LOOP_ANIMATION_NAME,
        drone -> DroneAnimationRefs.MOLT_ENTER_ANIMATION_NAME,
        true
    );

    public DroneAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<Drone> animationTrackContainer) {
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.BODY)
                .setTransitionLength(5)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(Drone animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(Drone animatable, float partialTicks) {
        super.setCustomAnimations(animatable, partialTicks);

        if (cocoonAnimationStateTracker.run(animatable)) {
            return;
        }

        AzAlienHeadAnimationUtil.applyHeadLookFromBindPose(animatable, context(), partialTicks, "gNeck");

        runPassiveAnimations(animatable);
    }

    private void runPassiveAnimations(Drone drone) {
        var dispatcher = drone.getAnimationDispatcher();

        // ⭐⭐ THE LUNGE IS A ONE-SHOT AND MUST BE DISPATCHED ONCE, NOT EVERY TICK.
        //
        // ⚠⚠ LUNGE is an AzCommand.replay() - re-dispatching RESTARTS it from frame 0. Sending it on every tick of
        // the lunge state pinned the body to the opening frame for the whole leap, so the mob slid along in a
        // FROZEN POSE. [stated] "it seems to start but it just glides without moving its limbs." It also RETURNS,
        // so the gait below never ran either - which is why walk and idle looked missing entirely while run and
        // crawl (reached in other states) were fine.
        //
        // ⚠ Same trap as the jump clips: edge-detect the flip, then let the clip own the track for its length.
        if (drone.isLunging.get()) {
            if (!wasLunging) {
                dispatcher.lunge();
                wasLunging = true;
            }
            return;
        }

        wasLunging = false;

        var attackType = drone.attackType.get();
        var attackId = drone.attackId.get();

        if (!attackType.isNone()) {
            if (attackId != previousAttackId) {
                var speed = calculateAttackSpeed(drone, attackType);

                // ⭐ PRONE ATTACKS. [stated] "the attacks they can do are only the crawl ones" - the server already
                // restricts a crawling caste to crawl AttackTypes, so reaching here with one means it IS prone.
                if (attackType == Drone.CRAWL_BITE)
                    dispatcher.crawlBiteAttack();
                else if (attackType == Drone.CRAWL_CLAW)
                    dispatcher.crawlAttack();
                else if (attackType == Drone.BITE)
                    dispatcher.biteAttack(speed);
                else if (attackType == Drone.CLAW)
                    dispatcher.rightClawAttack(speed);
                else if (attackType == Drone.TAIL)
                    dispatcher.tailAttack(speed);

                previousAttackId = attackId;
            }
            return;
        }

        var isMoving = drone.isMovingHorizontally.get() && drone.onGround();
        var isCrawling = drone.getCrawlingManager().isCrawling();
        // Carve-crew gait (construction economy step 5): a rostered digger (1) or placer (2) plays walk dig -
        // diggers at the default 70%, placers at 50% so the two read differently side by side.
        //
        // While rostered this is the ONLY passive animation the drone plays. It deliberately outranks swim, crawl,
        // carry, run and idle: the carve gait is a long cycle (2.5s at 0.7 speed = 3.57s; placers 5s) and every
        // switch to another animation restarts the track into its 5-tick transition. Carve work is start-stop by
        // nature - step, dig, turn, step - and isMovingQuickly is a raw per-tick position-delta test with no
        // hysteresis, so it chatters many times a second. Letting any of those states interleave meant the gait
        // never advanced past its opening frames and the legs sat frozen in the t=0 stride pose.
        //
        // Real interruptions still win: lunge, attacks and the cocoon state all return above this point, and the
        // roster clears carveDigMode the moment the drone leaves the crew.
        // AIRBORNE, ahead of every gait.
        // [stated] "if it needs to go up a 2 block high rise it would do jump for however long it takes and then when
        // its coming down onto the block land would play... it could jump of a cliff then pause on the last frame as
        // it falls and then when it hits the block land plays. or it could be a longer jump like its running and it
        // jumps say 4 blocks forward over a gap and then lands and continues running."
        //
        // A FLIP-DETECT, not a per-tick dispatch: jump fires on the tick the ground is LOST and then holds its last
        // frame for the whole flight, land fires on the tick it is REGAINED. Re-dispatching jump every airborne tick
        // would restart the clip and freeze it on its opening frames - the same failure the gait block above warns
        // about. Walk/run resume by themselves on the tick after landing, so a running leap continues running.
        // ⚠⚠ VERTICAL MOTION IS REQUIRED, NOT JUST !onGround. A mob whose ground contact FLICKERS while it walks
        // (an uneven floor, resin ledges, a slab lip) would otherwise reach the airborne floor, dispatch the
        // HOLD_ON_LAST_FRAME jump, and then be blocked from every gait clip below by the jumpPlayed guard - so it
        // slides along with its pose frozen. Nothing genuinely airborne has zero vertical velocity.
        var onGround = drone.onGround()
            || Math.abs(drone.getDeltaMovement().y) <= AIRBORNE_VERTICAL_EPSILON;
        wasOnGround = onGround;

        if (onGround) {
            // Land only if the flight was long enough to have played a jump.
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

            // Airborne past the floor: hold the last frame and let no gait overwrite it. BELOW the floor we fall
            // through on purpose, so a one-tick stumble off a slab keeps walking and never flickers.
            if (jumpPlayed) {
                return;
            }
        }

        var carveDigMode = drone.carveDigMode.get();
        Runnable animFunction;

        if (carveDigMode > 0) {
            animFunction = carveDigMode == 2 ? () -> dispatcher.walkDig(0.5F) : dispatcher::walkDig;
        } else if (drone.isUnderWater()) {
            // TODO: idle swim
            animFunction = dispatcher::swim;
        } else if (isMoving) {
            if (isCrawling) {
                animFunction = () -> dispatcher.crawl(AzAlienAnimationUtil.crawlAnimationSpeed(drone));
            } else if (isCarrying(drone)) {
                // Laden. Takes precedence over run: there is no "run carry" animation, and a drone hauling an egg or a
                // thrashing villager should not be sprinting anyway.
                animFunction = dispatcher::walkCarry;
            } else if (drone.isMovingQuickly.get()) {
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

    /**
     * Is this drone hauling something? An egg on its back, or a host clutched to its chest.
     * <p>
     * Passengers are synced to the client already, so this needs no new network state - the animator can simply look.
     */
    private static boolean isCarrying(Drone drone) {
        for (var passenger : drone.getPassengers()) {
            var type = passenger.getType();
            if (type.is(AlienEntityTypeTags.HOSTS) || type.is(AlienEntityTypeTags.OVOMORPHS)) {
                return true;
            }
        }
        return false;
    }

    private float calculateAttackSpeed(Drone drone, AttackType attackType) {
        String animationName;

        if (attackType == Drone.CRAWL_BITE)
            animationName = DroneAnimationRefs.CRAWL_ATTACK_BITE_ANIMATION_NAME;
        else if (attackType == Drone.CRAWL_CLAW)
            // Mirrored pair, same length - the right clip stands in for both when measuring.
            animationName = DroneAnimationRefs.CRAWL_ATTACK_RIGHT_ANIMATION_NAME;
        else if (attackType == Drone.BITE)
            animationName = DroneAnimationRefs.ATTACK_BITE_ANIMATION_NAME;
        else if (attackType == Drone.CLAW)
            // Either side will do for TIMING: the mirrored pair are the same length, and this only measures the clip
            // to fit it to attackDurationInTicks. Which arm actually swings is MirroredAttackSide's call, at dispatch.
            animationName = DroneAnimationRefs.ATTACK_CLAW_RIGHT_ANIMATION_NAME;
        else if (attackType == Drone.TAIL)
            animationName = DroneAnimationRefs.ATTACK_TAIL_ANIMATION_NAME;
        else
            animationName = null;

        var durationInTicks = drone.attackDurationInTicks.get();

        if (animationName == null || durationInTicks <= 0) {
            return 1.0f;
        }

        var animation = getAnimation(drone, animationName);

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
