package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host.action;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host.HostSensors;
import com.alien.common.gameplay.hive.party.HostCaptureRules;
import com.alien.common.gameplay.hive.party.HostCaptureTask;
import com.alien.common.gameplay.hive.party.HostClaims;
import com.blib.api.common.goap.v1.action.impl.NeoMoveToPosAction;
import com.just.ai.goap.action.Action;

/**
 * Walk to a viable host and grab it.
 * <p>
 * This is the half that was missing: {@code HostCaptureTask} knew HOW to grab a host and hand it off at a vent, but
 * nothing ever made the drone WALK to one - so host-hunt parties spawned, wandered on their idle goals, captured
 * nothing, and timed out. Movement has to be a GOAP action holding the MOVE mask, exactly like the egg haul.
 */
public final class CaptureHostAction {

    private CaptureHostAction() {}

    /** Close enough to seize the host. */
    private static final double GRAB_RANGE_SQUARED = 2.0 * 2.0;

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
        var result = NeoMoveToPosAction.perform(context, target.position(), 0.5);
        return switch (result) {
            case MOVING -> Action.Signal.CONTINUE;
            case FINISHED -> Action.Signal.CONTINUE; // arrived near it; the grab check fires next tick
            default -> Action.Signal.ABORT; // cannot path to it - let the planner pick another
        };
    }

    public static void onFinish(Action.Context<? extends Xenomorph> context) {
        NeoMoveToPosAction.onFinish(context);
    }
}
