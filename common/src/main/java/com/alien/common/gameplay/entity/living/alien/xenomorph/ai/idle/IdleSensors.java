package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.idle;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.sensor.Sensor;
import com.just.ai.goap.sensor.Sensors;

public class IdleSensors {

    public static final Sensor.Mono<Xenomorph, Boolean> IS_BORED = Sensors.map(
        StateKey.sensed("is_bored"),
        // !!! A XENOMORPH IN WATER IS NOT BORED, IT IS STRANDED - AND THIS IS WHY SWIMMING NEVER GOT A LOOK IN.
        //
        // WanderAction and SwimToShoreAction both declare ActionMasks.MOVE, and BLib's ActionMaskPlanResolver
        // returns KEEP_ACTIVE whenever an incoming plan shares a mask with the plan already running. So a xenomorph
        // that walked into water still holding a live wander plan could never accept the swim plan - the crossing was
        // locked out by an idle stroll it could not finish.
        //
        // WARNING: IT WAS ALSO BURNING THE PATHER. In water the navigator finishes instantly with no path, so
        // WanderAction's "re-issue when done" guard fired EVERY TICK. A live diagnostic recorded 17,450
        // path/IdleActions calls in 37 seconds at 99.4% MOVING - paths that succeeded and went nowhere.
        //
        // Making the sensor false in water invalidates the running wander plan at its next update (the engine checks
        // an action's runtime preconditions every tick and marks the plan INVALID), which releases the MOVE mask and
        // lets the crossing start. Boredom resumes on dry land untouched.
        // SUBMERGED, NOT MERELY WET - isInWater() is true for a single wet block, and stopping a xenomorph from
        // wandering because it stepped in a puddle would trade one bug for another.
        xenomorph -> xenomorph.getXenomorphData().getTicksUntilBored() == 0
            && !com.alien.common.gameplay.entity.living.alien.xenomorph.ai.swim.SwimSensors.isSubmerged(xenomorph)
            && !hasAssignedWork(xenomorph)
    );

    /**
     * Whether this xenomorph has a concrete job, so is not idle however long it has been standing about.
     * <p>
     * !!! THIS IS WHY WORKERS IGNORED THEIR JOBS. Every movement action declares ActionMasks.MOVE - egg hauling, vent
     * digging, resin spreading, host capture, carve work, combat - and BLib's resolver refuses any incoming plan
     * sharing a mask with the running one. A xenomorph that had started a wander could accept none of them until
     * boredom ran out: 7-12 seconds per stroll. Reported as sites logging "unstaffed - no free drones" while drones
     * stood in plain sight.
     * </p>
     * <p>
     * TELL THE PLANNER IT IS NOT IDLE - DO NOT FIGHT OVER THE MASK. Marking WANDER interruptible was tried and
     * measured: any plan could displace it, including ones that then could not run, so actors were displaced and
     * re-planned several times a second. That churn restarted animations and showed as mod-wide jitter. Here there is
     * no displacement: the wander is never chosen, and an in-flight one is invalidated the moment work arrives because
     * its precondition stops holding.
     * </p>
     * <p>
     * The same trick the swim fix uses one line above - a submerged xenomorph is not bored either.
     * </p>
     * <p>
     * WARNING: party membership is deliberately absent. A member waiting to move out has no task in hand, and freezing
     * a whole mustering party would be worse than the bug.
     * </p>
     */
    private static boolean hasAssignedWork(Xenomorph xenomorph) {
        if (com.alien.common.gameplay.hive.party.EggDutyGuard.isOnEggDuty(xenomorph)) {
            return true;
        }

        if (com.alien.common.gameplay.hive.party.HostCaptureTask.isCarryingHost(xenomorph)) {
            return true;
        }

        // A worker walking to a vent to fold into the bank is leaving, not loitering.
        if (xenomorph.isMarkedForReserveReturn()) {
            return true;
        }

        // WARNING: the carve case never went through GOAP at all. CarveWorkers steers its crew with its own
        // moveTo calls, so a wandering drone was not merely refusing the job - it was fighting the site for the
        // navigator every tick.
        var location = com.alien.common.gameplay.hive.faction.HiveMemberLocationResolver
            .reserveReturnLocation(xenomorph);

        if (location != null) {
            var site = location.activeCarveSite();

            if (site != null && site.hasWorker(xenomorph.getUUID())) {
                return true;
            }
        }

        return false;
    }

    private IdleSensors() {
        throw new UnsupportedOperationException();
    }
}
