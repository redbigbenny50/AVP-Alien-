package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg.action;

import com.alien.common.gameplay.entity.living.alien.EggCarrier;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.vent.HiveVents;
import com.blib.api.common.goap.v1.action.impl.NeoMoveToPosAction;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.action.Action;
import com.just.ai.goap.state.Blackboard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class PickUpEggAction {

    private static final double PICKUP_RANGE_SQUARED = 2.0 * 2.0;

    // Vent duct leg (mirrors the drop-off leg): a worker fetching a loose egg that is far away - e.g. a fresh egg the
    // queen just laid back in her ring - ducts most of the way via an interior vent instead of trudging the whole path.
    private static final StateKey<Boolean> KEY_VENT_PLANNED = StateKey.sensed("egg_pickup_vent_planned");

    private static final StateKey<BlockPos> KEY_VENT_ENTRY = StateKey.sensed("egg_pickup_vent_entry");

    private static final StateKey<BlockPos> KEY_VENT_EXIT = StateKey.sensed("egg_pickup_vent_exit");

    private static final double VENT_REACH_SQUARED = 2.5 * 2.5; // close enough to slip into a vent

    private static final double VENT_WORTHWHILE_DIST_SQUARED = 32.0 * 32.0; // shorter fetches just walk

    private static final int VENT_SEARCH_RADIUS_CHUNKS = 1;

    public static Action.Signal perform(Action.Context<? extends Xenomorph> context) {
        var xenomorph = context.getActor();

        if (!(xenomorph instanceof EggCarrier eggCarrier)) {
            return Action.Signal.ABORT;
        }

        var targetOvomorph = eggCarrier.getEggPickupManager().getTargetOvomorphOrNull();

        if (targetOvomorph == null || !targetOvomorph.wantsPickup || targetOvomorph.isPassenger()) {
            eggCarrier.getEggPickupManager().setTargetOvomorph(null);
            return Action.Signal.ABORT;
        }
        // ONE egg per hauler. The guards above test the EGG's state, but nothing tested the HAULER's - after a
        // grab the manager clears its slot, the sensor hands out a fresh target, and this action would mount a
        // SECOND ovomorph (xenomorphs allow multiple passengers for the host-carry system, so vanilla's single
        // -rider default does not save us; tester screenshot: aberrant drone hauling two eggs). Already loaded ->
        // this plan is done; drop-off owns the turn.
        if (isCarryingAnEgg(xenomorph)) {
            eggCarrier.getEggPickupManager().setTargetOvomorph(null);
            return Action.Signal.ABORT;
        }

        var blackboard = context.getBlackboard(Blackboard.Scope.ACTION);

        // Plan the duct leg once per fetch: if the egg is far, set an interior entry/exit vent pair to shortcut to it.
        if (!blackboard.getOrDefault(KEY_VENT_PLANNED, false)) {
            blackboard.set(KEY_VENT_PLANNED, true);
            planVentLeg(xenomorph, targetOvomorph.blockPosition(), blackboard);
        }

        // Duct leg: head to a standable spot beside the entry vent; on reaching it, duct to the exit vent near the egg,
        // then resume the normal walk. Any pathing trouble on this leg just abandons the duct and walks the whole way.
        var ventEntry = blackboard.getOrDefault(KEY_VENT_ENTRY, (BlockPos) null);
        if (ventEntry != null) {
            // Approach a STANDABLE spot beside/below the vent, not the vent block: vents sit in walls (often 2-3 up),
            // so the worker can never reach the vent center. emergencePosNear finds the floor spot the exit side uses.
            var ventApproach = HiveVents.emergencePosNear(xenomorph.level(), ventEntry);
            var ventTarget = ventApproach != null ? Vec3.atBottomCenterOf(ventApproach) : Vec3.atCenterOf(ventEntry);
            var ventResult = NeoMoveToPosAction.perform(context, ventTarget, 0.5);
            if (xenomorph.distanceToSqr(ventTarget) <= VENT_REACH_SQUARED) {
                var ventExit = blackboard.getOrDefault(KEY_VENT_EXIT, (BlockPos) null);
                blackboard.set(KEY_VENT_ENTRY, (BlockPos) null);
                blackboard.set(KEY_VENT_EXIT, (BlockPos) null);
                NeoMoveToPosAction.onFinish(context);
                if (ventExit != null) {
                    HiveVents.ductTravel(xenomorph, ventEntry, ventExit);
                }
                return Action.Signal.CONTINUE;
            }
            switch (ventResult) {
                case MOVING -> {
                    return Action.Signal.CONTINUE;
                }
                default -> {
                    blackboard.set(KEY_VENT_ENTRY, (BlockPos) null);
                    blackboard.set(KEY_VENT_EXIT, (BlockPos) null);
                    NeoMoveToPosAction.onFinish(context);
                }
            }
        }

        var result = NeoMoveToPosAction.perform(context, targetOvomorph.position(), 0.5);

        var arrived = xenomorph.distanceToSqr(targetOvomorph) <= PICKUP_RANGE_SQUARED;

        return switch (result) {
            case MOVING -> {
                if (arrived && !isCarryingAnEgg(xenomorph)) {
                    targetOvomorph.startRiding(xenomorph);
                    // Egg duty: a loaded carrier must not vanilla-despawn mid-haul (the egg would drop).
                    xenomorph.setPersistenceRequired();
                    eggCarrier.getEggPickupManager().setTargetOvomorph(null);
                }
                yield Action.Signal.CONTINUE;
            }
            case FINISHED -> {
                if (arrived && !isCarryingAnEgg(xenomorph)) {
                    targetOvomorph.startRiding(xenomorph);
                    // Egg duty: a loaded carrier must not vanilla-despawn mid-haul (the egg would drop).
                    xenomorph.setPersistenceRequired();
                    eggCarrier.getEggPickupManager().setTargetOvomorph(null);
                    yield Action.Signal.CONTINUE;
                }
                // Finished navigating but still short of the egg (blocked/truncated) - abort so the planner re-plans
                // instead of freezing the drone next to an egg it can't quite reach.
                yield Action.Signal.ABORT;
            }
            case NO_PATH -> Action.Signal.ABORT;
            default -> Action.Signal.ABORT;
        };
    }

    /** If the egg is far enough to be worth it, pick an interior entry/exit vent pair that shortens the trip. */
    /** Delegates to the shared planner - this was a private copy of HiveVents.planInteriorLeg. */
    private static void planVentLeg(Xenomorph xenomorph, BlockPos egg, Blackboard blackboard) {
        HiveVents.planInteriorLeg(xenomorph, egg, false).ifPresent(leg -> {
            blackboard.set(KEY_VENT_ENTRY, leg.entry());
            blackboard.set(KEY_VENT_EXIT, leg.exit());
        });
    }

    public static void onFinish(Action.Context<? extends Xenomorph> context) {
        if (context.getActor() instanceof EggCarrier eggCarrier) {
            eggCarrier.getEggPickupManager().setTargetOvomorph(null);
        }

        NeoMoveToPosAction.onFinish(context);
    }

    private PickUpEggAction() {
        throw new UnsupportedOperationException();
    }

    /** True when any current passenger is an ovomorph - the hauler's hands are full. */
    private static boolean isCarryingAnEgg(Xenomorph xenomorph) {
        for (var passenger : xenomorph.getPassengers()) {
            if (passenger instanceof com.alien.common.gameplay.entity.living.alien.ovomorph.Ovomorph) {
                return true;
            }
        }
        return false;
    }
}
