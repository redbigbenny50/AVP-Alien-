package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host.action;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.party.HostCaptureTask;
import com.alien.common.gameplay.hive.party.PartyVentUtil;
import com.alien.common.gameplay.hive.vent.HiveVents;
import com.blib.api.common.goap.v1.action.impl.NeoMoveToPosAction;
import com.just.ai.goap.action.Action;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Carry a captured host to the nearest surface vent and hand it into the host chamber.
 * <p>
 * Hosts are NEVER walked home overland - the vent is the way in. The drone walks to the vent, and once it is within
 * reach the host is ducted straight into a free host-chamber spot.
 * <p>
 * <b>Do not path to the vent block.</b> A vent has no standable centre: chamber vents sit IN a wall (often two or three
 * blocks up) and party-dropped surface vents sit inside a resin collar. Aiming the navigator at the block itself yields
 * NO_PATH forever, and because this action must keep hold of the host rather than abort, the drone simply stands still
 * with its captive - which is exactly what a tester sees as "it froze". {@link HiveVents#emergencePosNear} returns the
 * standable floor spot beside the vent; the egg haul already learned this lesson the hard way.
 * <p>
 * Every stall path also logs a reason. An action that can neither finish nor abort is invisible otherwise.
 */
public final class DeliverHostAction {

    private DeliverHostAction() {}

    /** How close the carrier must get to the vent approach before the hand-off fires. */
    private static final double VENT_REACH_SQUARED = 2.5 * 2.5;

    /** Stall reasons are logged on this cadence, not every tick. */
    private static final int LOG_INTERVAL_TICKS = 100;

    /**
     * Vents this carrier could not reach, so it stops hammering the same one.
     * <p>
     * "Near-surface" is measured against the heightmap of the vent's OWN column, so a vent under a hill or in a dip
     * can qualify while sitting well below the ground the carrier is standing on - and a carrier outside the hive
     * cannot walk down to it. Picking strictly the closest such vent and never reconsidering means a permanent stall
     * with a live host on its back. Instead: fail once, write it off, try the next nearest.
     */
    private static final Map<Entity, Set<BlockPos>> UNREACHABLE_VENTS = new WeakHashMap<>();

    public static Action.Signal perform(Action.Context<? extends Xenomorph> context) {
        var xenomorph = context.getActor();

        var carried = HostCaptureTask.carriedHost(xenomorph);
        if (carried == null) {
            return Action.Signal.ABORT; // lost it (rescued, killed, escaped)
        }
        if (!carried.isAlive()) {
            carried.stopRiding();
            return Action.Signal.ABORT;
        }
        if (!(xenomorph.level() instanceof ServerLevel serverLevel)) {
            return Action.Signal.CONTINUE;
        }

        var location = HiveLocationRegistry.INSTANCE.findNearestInDim(
                serverLevel.dimension(),
                xenomorph.blockPosition()
        );
        if (location == null) {
            stall(xenomorph, "no hive location resolved - cannot deliver the host");
            return Action.Signal.CONTINUE;
        }

        var vent = nearestSurfaceVent(serverLevel, location, xenomorph);
        if (vent == null) {
            var writtenOff = UNREACHABLE_VENTS.get(xenomorph);
            if (writtenOff != null && !writtenOff.isEmpty()) {
                // Every vent has been written off. The world may have changed (a fresh vent dug, a wall broken), so
                // forget the failures and start over rather than holding the host forever.
                stall(xenomorph, "every near-surface vent was unreachable - retrying them all");
                writtenOff.clear();
                return Action.Signal.CONTINUE;
            }
            stall(xenomorph, "no near-surface vent to carry the host to - holding it");
            return Action.Signal.CONTINUE; // no way in yet - keep hold of the host
        }

        // The vent block itself is unreachable (in a wall, or inside a resin collar). Walk to the standable spot beside
        // it instead, exactly as the egg haul does.
        var approach = HiveVents.emergencePosNear(serverLevel, vent);
        var ventTarget = approach != null ? Vec3.atBottomCenterOf(approach) : Vec3.atBottomCenterOf(vent);

        if (xenomorph.distanceToSqr(ventTarget) <= VENT_REACH_SQUARED) {
            NeoMoveToPosAction.onFinish(context);
            UNREACHABLE_VENTS.remove(xenomorph);
            HostCaptureTask.deliverToHostChamber(xenomorph, carried, serverLevel, location);
            // Action.Signal has no FINISHED: the IS_CARRYING_HOST effect flips and the planner moves on.
            return Action.Signal.CONTINUE;
        }

        var result = NeoMoveToPosAction.perform(context, ventTarget, 0.5);
        return switch (result) {
            case MOVING, FINISHED -> Action.Signal.CONTINUE;
            default -> {
                // Cannot path to this vent. Write it off for this carrier and take the next nearest next tick, rather
                // than standing here forever hammering an unreachable target with a live host on our back.
                UNREACHABLE_VENTS.computeIfAbsent(xenomorph, ignored -> new HashSet<>()).add(vent);
                NeoMoveToPosAction.onFinish(context);
                stall(xenomorph, "cannot path to the vent at " + vent + " - writing it off, trying the next nearest");
                yield Action.Signal.CONTINUE;
            }
        };
    }

    public static void onFinish(Action.Context<? extends Xenomorph> context) {
        NeoMoveToPosAction.onFinish(context);
    }

    private static void stall(Xenomorph xenomorph, String reason) {
        if (xenomorph.tickCount % LOG_INTERVAL_TICKS == 0) {
            com.alien.Alien.LOGGER.warn("Host carrier at {} stalled: {}", xenomorph.blockPosition(), reason);
        }
    }

    /** SURFACE vents only: a captive comes in through the front door, never a cave-mouth outpost or an interior duct. */
    private static BlockPos nearestSurfaceVent(ServerLevel level, HiveLocation location, Xenomorph xenomorph) {
        var writtenOff = UNREACHABLE_VENTS.get(xenomorph);

        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (var vent : PartyVentUtil.findSurfaceVents(level, location)) {
            if (writtenOff != null && writtenOff.contains(vent)) {
                continue; // this carrier already failed to reach this one
            }
            double distance = xenomorph.distanceToSqr(vent.getX() + 0.5, vent.getY(), vent.getZ() + 0.5);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = vent;
            }
        }
        return nearest;
    }
}