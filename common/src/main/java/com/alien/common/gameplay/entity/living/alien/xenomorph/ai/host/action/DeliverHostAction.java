package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host.action;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host.InteriorSweepDuty;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.party.HostCaptureTask;
import com.alien.common.gameplay.hive.party.PartyVentUtil;
import com.alien.common.gameplay.hive.structure.HostChamberSlots;
import com.alien.common.gameplay.hive.vent.HiveVents;
import com.alien.common.gameplay.hive.vent.VentKind;
import com.alien.common.gameplay.hive.vent.VentPlacement;
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
    /** A carrier may use a vent from ANY side within this many blocks on each axis (a box, not a sphere). */
    private static final int VENT_USE_RANGE = 2;

    /** Close enough to the chamber spot to web the host into it. */
    private static final double CHAMBER_REACH_SQUARED = 2.5 * 2.5;

    /** Stall reasons are logged on this cadence, not every tick. */
    private static final int LOG_INTERVAL_TICKS = 100;

    /**
     * Vents this carrier could not reach, so it stops hammering the same one.
     * <p>
     * "Near-surface" is measured against the heightmap of the vent's OWN column, so a vent under a hill or in a dip can
     * qualify while sitting well below the ground the carrier is standing on - and a carrier outside the hive cannot
     * walk down to it. Picking strictly the closest such vent and never reconsidering means a permanent stall with a
     * live host on its back. Instead: fail once, write it off, try the next nearest.
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

        // ALREADY INSIDE THE HIVE? Then walk it straight to the chamber. A drone that picked a cow up in a corridor
        // must not carry it OUT of the hive to a surface vent just to duct it back in again.
        if (InteriorSweepDuty.isInsideHive(location, xenomorph)) {
            return deliverFromInside(context, xenomorph, carried, serverLevel, location);
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
        var approachPos = approach != null ? approach : vent;
        var ventTarget = Vec3.atBottomCenterOf(approachPos);

        // Arrival is tested against the SPOT WE PATHED TO, not the raw vent block. emergencePosNear can hand back a
        // standable cell two blocks out and one up from the vent; the carrier would then stand exactly on its target
        // and still be >VENT_USE_RANGE from the vent on some axis, so the old isAtVent(vent) test never fired - it
        // walked right up to the vent and then past it, holding the host forever. We are "at the vent" when we have
        // reached the approach spot (or, as a fallback, are genuinely within use-range of the vent itself).
        if (isAt(xenomorph, approachPos) || isAtVent(xenomorph, vent)) {
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

                // A vent the carrier reached the foot of but cannot CLIMB to (a SURFACE vent stamped on a spire top
                // before the placement Y-guard existed) is not a transient failure - it is unreachable forever. If
                // this carrier is standing far below such a vent, relocate the vent down to where the carrier IS: it
                // is standing on reachable, standable ground by definition. This heals the bad vents already stamped
                // on existing worlds, which the placement fix alone cannot move.
                maybeRelocateUnreachableVent(serverLevel, location, xenomorph, vent);

                stall(xenomorph, "cannot path to the vent at " + vent + " - writing it off, trying the next nearest");
                yield Action.Signal.CONTINUE;
            }
        };
    }

    /**
     * Delivery for a carrier that is already inside the hive: walk to the free chamber spot and web the host up there.
     * No vent involved - there is nothing to duct through, we are already home.
     * <p>
     * The drone paths to the cell IN FRONT of the spot (the same cell the host's egg is later set in), because the spot
     * itself is a web block in a wall and is not somewhere anything can stand.
     */
    private static Action.Signal deliverFromInside(
        Action.Context<? extends Xenomorph> context,
        Xenomorph xenomorph,
        net.minecraft.world.entity.LivingEntity carried,
        ServerLevel serverLevel,
        HiveLocation location
    ) {
        var spot = HostChamberSlots.firstFreeSpot(serverLevel, location);
        if (spot == null) {
            // The larder filled up while we were carrying this one. Hold it; a spot may free up.
            stall(xenomorph, "no free host-chamber spot - holding the host");
            return Action.Signal.CONTINUE;
        }

        var approach = HostChamberSlots.eggDropFor(spot);
        var target = Vec3.atBottomCenterOf(approach);

        if (xenomorph.distanceToSqr(target) <= CHAMBER_REACH_SQUARED) {
            NeoMoveToPosAction.onFinish(context);
            HostCaptureTask.deliverToHostChamber(xenomorph, carried, serverLevel, location);
            return Action.Signal.CONTINUE;
        }

        var result = NeoMoveToPosAction.perform(context, target, 0.5);
        return switch (result) {
            case MOVING, FINISHED -> Action.Signal.CONTINUE;
            default -> {
                stall(xenomorph, "cannot path to the host chamber at " + approach + " - holding the host");
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

    /**
     * SURFACE vents only: a captive comes in through the front door, never a cave-mouth outpost or an interior duct.
     */
    /**
     * Close enough to USE the vent: within {@link #VENT_USE_RANGE} blocks on EVERY axis - beside it, above it, below
     * it, diagonally, all count. Vents are embedded in walls and floors, so a straight-line distance to the vent could
     * fail even while the carrier stood right next to it.
     */
    /** A vent this far above the stalled carrier is a climb it will never make - relocate it, do not just retry it. */
    private static final int UNREACHABLE_VENT_Y_GAP = 6;

    /**
     * If a SURFACE vent sits far above a carrier that cannot path to it, move the vent down to the carrier.
     * <p>
     * The carrier reached its current spot on foot, so that spot is reachable-and-standable; the vent, stamped on a
     * spire top by the old heightmap placement, is not. Only fires for a genuine vertical gap
     * ({@link #UNREACHABLE_VENT_Y_GAP}) and only for SURFACE vents - a frontier/structure vent underground is SUPPOSED
     * to be below the carrier and must never be dragged to the surface.
     */
    private static void maybeRelocateUnreachableVent(
        ServerLevel level,
        HiveLocation location,
        Xenomorph xenomorph,
        BlockPos vent
    ) {
        // isKind is also the spam guard: the moment we relocate, removeVent() clears this vent's kind, so a carrier
        // that keeps stalling finds it is no longer a known SURFACE vent and does not re-place it.
        if (!location.ventManager().isKind(vent, VentKind.SURFACE)) {
            return; // only the surface "front door" relocates (and an already-relocated vent no longer matches)
        }
        var carrierPos = xenomorph.blockPosition();
        if (vent.getY() - carrierPos.getY() < UNREACHABLE_VENT_Y_GAP) {
            return; // not a big climb - this is an ordinary transient path failure, just retry the vent
        }
        // The destination must be a genuine vent cell: an OPEN space resting against a solid face - the same contract
        // VentPlacement expects. The carrier is standing here, but "standing" is not enough (it could be on a slab, on
        // web, or mid-step over a gap). If its feet cell does not qualify, do not stamp a floating vent - just leave
        // the vent written off and let the carrier try the next one.
        if (!VentPlacement.isOpen(level, carrierPos) || !VentPlacement.restsOnSolidFace(level, carrierPos)) {
            return;
        }
        var variant = location.lineageVariantOrNull();
        if (variant == null) {
            return;
        }
        // Move the record and the blocks: drop the old vent, stamp a fresh SURFACE vent where the carrier stands.
        location.ventManager().removeVent(vent);
        // place() now registers into the manager itself, so no separate addVent is needed.
        VentPlacement.place(level, carrierPos, AlienVariantTypes.getFor(variant), VentKind.SURFACE, location);
        var writtenOff = UNREACHABLE_VENTS.get(xenomorph);
        if (writtenOff != null) {
            writtenOff.remove(vent); // the replacement is reachable; do not carry the old write-off forward
        }
        com.alien.Alien.LOGGER.info(
            "Hive: relocated an unreachable SURFACE vent from {} down to {} where a host carrier could stand.",
            vent,
            carrierPos
        );
    }

    /** Standing on (or within one block of) a specific cell - used to confirm arrival at the approach spot. */
    private static boolean isAt(Xenomorph xenomorph, BlockPos target) {
        var pos = xenomorph.blockPosition();
        return Math.abs(pos.getX() - target.getX()) <= 1
            && Math.abs(pos.getY() - target.getY()) <= 1
            && Math.abs(pos.getZ() - target.getZ()) <= 1;
    }

    private static boolean isAtVent(Xenomorph xenomorph, BlockPos vent) {
        var pos = xenomorph.blockPosition();
        return Math.abs(pos.getX() - vent.getX()) <= VENT_USE_RANGE
            && Math.abs(pos.getY() - vent.getY()) <= VENT_USE_RANGE
            && Math.abs(pos.getZ() - vent.getZ()) <= VENT_USE_RANGE;
    }

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
