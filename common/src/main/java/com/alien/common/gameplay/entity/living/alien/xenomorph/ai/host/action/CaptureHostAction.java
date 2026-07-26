package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host.action;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host.HostSensors;
import com.alien.common.gameplay.hive.party.HostCaptureRules;
import com.alien.common.gameplay.hive.party.HostCaptureTask;
import com.alien.common.gameplay.hive.party.HostClaims;
import com.blib.api.common.goap.v1.action.impl.NeoMoveToPosAction;
import com.just.ai.goap.action.Action;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Walk to a viable host and grab it.
 * <p>
 * This is the half that was missing: {@code HostCaptureTask} knew HOW to grab a host and hand it off at a vent, but
 * nothing ever made the drone WALK to one - so host-hunt parties spawned, wandered on their idle goals, captured
 * nothing, and timed out. Movement has to be a GOAP action holding the MOVE mask, exactly like the egg haul.
 */
public final class CaptureHostAction {

    private CaptureHostAction() {}

    /**
     * How much faster than its quarry the drone travels. Just enough to close the gap - not so much that it teleports
     * onto its prey.
     */
    private static final double OVERTAKE_MARGIN = 1.12;

    /** An unhurried approach for prey that is not going anywhere - a drone does not sprint at a grazing cow. */
    private static final double STALK_SPEED = 0.7;

    /** Never slower than this once actually pursuing, and never so fast it looks like teleporting. */
    private static final double MIN_CHASE_SPEED = 0.7;

    private static final double MAX_CHASE_SPEED = 1.6;

    /** Vanilla sprinting is a flat +30% on top of walking speed. */
    private static final double SPRINT_MULTIPLIER = 1.3;

    /** Close enough to seize the host. */
    private static final double GRAB_RANGE_SQUARED = 2.0 * 2.0;

    /** Ticks of unbroken NO_PATH before a drone accepts that it simply cannot get to this one. */
    private static final int NO_PATH_PATIENCE_TICKS = 100;

    /** Consecutive failed pathfinds per captor. */
    private static final Map<Xenomorph, Integer> FAILED_PATH_TICKS = new WeakHashMap<>();

    public static Action.Signal perform(Action.Context<? extends Xenomorph> context) {
        var xenomorph = context.getActor();

        var target = HostSensors.findCaptureTarget(xenomorph);
        if (target == null) {
            return Action.Signal.ABORT; // nothing worth taking any more
        }
        if (!HostCaptureRules.isCapturable(xenomorph, target)) {
            return Action.Signal.ABORT; // it healed, got implanted, or someone else took it
        }

        // Call dibs, and keep calling it. The claim lapses on its own if we stop, so an aborted or interrupted pursuit
        // never leaves a host permanently reserved.
        HostClaims.claim(target, xenomorph);

        if (xenomorph.distanceToSqr(target) <= GRAB_RANGE_SQUARED) {
            HostClaims.release(target); // it is on our back now; the passenger check guards it from here
            HostCaptureTask.capture(xenomorph, target);
            // Action.Signal has no FINISHED: the IS_CARRYING_HOST effect flips and the planner moves on.
            return Action.Signal.CONTINUE;
        }

        xenomorph.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // RUN it down. Hosts flee, and a hauler ambling after a bolting villager at walking pace never closes the
        // gap - it trails it forever. Chase at combat speed, and lead the target the way the combat AI does:
        // path to where it is GOING, not where it currently is, or every step is aimed at empty ground.
        var result = NeoMoveToPosAction.perform(
            context,
            interceptPoint(xenomorph, target),
            chaseSpeedFor(xenomorph, target)
        );

        if (xenomorph.tickCount % 60 == 0) {
            com.alien.Alien.LOGGER.info(
                "[hostdbg] CAPTURE: me={} quarry={} at {} distSq={} move={} navDone={} onGround={}",
                xenomorph.blockPosition(),
                target.getType().getDescriptionId(),
                target.blockPosition(),
                String.format("%.1f", xenomorph.distanceToSqr(target)),
                result,
                xenomorph.getNavigation().isDone(),
                xenomorph.onGround()
            );
        }

        // NEVER abort on a failed path. Aborting made the planner drop the action, re-plan, and abort again -
        // forever - so a drone with a perfectly valid quarry stood still and never took a single step. Keep the
        // action alive and let the navigator keep trying.
        //
        // But "keep trying forever" is its own trap: quarry that simply CANNOT be reached (on the far side of rock,
        // across a ravine) pins the drone in place for its whole life. So give up on the HOST, not on hunting - write
        // it off, and the sensor hands us a different one next tick.
        switch (result) {
            case MOVING, FINISHED -> FAILED_PATH_TICKS.remove(xenomorph);
            default -> {
                var failed = FAILED_PATH_TICKS.merge(xenomorph, 1, Integer::sum);
                if (failed >= NO_PATH_PATIENCE_TICKS) {
                    FAILED_PATH_TICKS.remove(xenomorph);
                    HostClaims.writeOffUnreachable(target, xenomorph);
                    NeoMoveToPosAction.onFinish(context);
                    com.alien.Alien.LOGGER.info(
                        "[hostdbg] gave up on unreachable quarry {} at {} - looking for another",
                        target.getType().getDescriptionId(),
                        target.blockPosition()
                    );
                }
            }
        }

        return Action.Signal.CONTINUE;
    }

    /**
     * Match the quarry.
     * <p>
     * Rather than one flat chase speed, the drone takes exactly as much pace as the thing it is hunting demands, plus a
     * small margin to close the gap. That reads right and it self-tunes:
     * <ul>
     * <li><b>A grazing cow</b> is not going anywhere - the drone STALKS in at {@link #STALK_SPEED} rather than
     * sprinting at a stationary animal.</li>
     * <li><b>A fleeing villager</b> gets jogged down.</li>
     * <li><b>A SPRINTING player</b> (or marine) forces a real run: a drone's own speed is only a player's WALKING pace,
     * so catching a sprint needs roughly 1.45x - which this works out on its own.</li>
     * </ul>
     * Because it is derived from the target's actual attributes it copes with anything - modded marines, speed potions,
     * slowness - without a lookup table of mob types.
     */
    private static double chaseSpeedFor(Xenomorph xenomorph, LivingEntity target) {
        // Not actually running away? Walk it down. No need for a spectacle.
        var horizontal = new Vec3(target.getDeltaMovement().x, 0.0, target.getDeltaMovement().z);
        if (horizontal.length() < 0.02) {
            return STALK_SPEED;
        }

        var chaserSpeed = xenomorph.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (chaserSpeed < 0.01) {
            return MAX_CHASE_SPEED;
        }

        var targetSpeed = target.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (target.isSprinting()) {
            targetSpeed *= SPRINT_MULTIPLIER;
        }

        var needed = (targetSpeed / chaserSpeed) * OVERTAKE_MARGIN;
        return Math.clamp(needed, MIN_CHASE_SPEED, MAX_CHASE_SPEED);
    }

    /**
     * Where the host WILL be, not where it is: project its horizontal velocity over the time we need to reach it.
     * Chasing a fleeing target's current position means always arriving where it just left.
     */
    private static Vec3 interceptPoint(Xenomorph xenomorph, LivingEntity target) {
        var targetPos = target.position();
        var velocity = target.getDeltaMovement();
        var horizontal = new Vec3(velocity.x, 0.0, velocity.z);
        if (horizontal.length() < 0.01) {
            return targetPos; // standing still - no need to lead it
        }
        var chaserSpeed = xenomorph.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (chaserSpeed < 0.01) {
            return targetPos;
        }
        var ticksToArrive = xenomorph.distanceTo(target) / chaserSpeed;
        return targetPos.add(horizontal.scale(ticksToArrive));
    }

    public static void onFinish(Action.Context<? extends Xenomorph> context) {
        NeoMoveToPosAction.onFinish(context);
    }
}
