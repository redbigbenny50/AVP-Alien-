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

    /**
     * The stall detector's second, DISTANCE-BASED net. The NO_PATH counter above only catches a navigator that admits
     * defeat - but a quarry on an unreachable shelf produces MOVING almost every tick: the navigator happily paths TO
     * THE CLIFF EDGE, reports success, and re-paths there forever, resetting the counter each time (tester log
     * latest-3: 71 minutes of drones pacing above a piglin pack 20 blocks below, distSq pinned ~1500, move=MOVING
     * navDone=true - and zero hosts gathered). Motion is not progress; CLOSING is. Per captor we track the best
     * distance achieved on the current quarry and the last time it meaningfully improved; a chase that has not closed
     * in {@link #STALL_TIMEOUT_TICKS} is written off exactly like an admitted NO_PATH, and the sensor hands the drone a
     * different host next tick.
     */
    private static final Map<Xenomorph, ChaseProgress> CHASE_PROGRESS = new WeakHashMap<>();

    /** A chase that has not gotten closer in this long is pinned at an obstacle, whatever the navigator claims. */
    private static final long STALL_TIMEOUT_TICKS = 600L;

    /** "Meaningfully improved": at least ~3 blocks closer, so a wandering quarry's jitter never counts as progress. */
    private static final double PROGRESS_EPSILON_SQUARED = 9.0D;

    /** Best distance achieved toward one specific quarry, and when it last improved. */
    private record ChaseProgress(
        java.util.UUID targetId,
        double bestDistSquared,
        long lastProgressGameTime
    ) {}

    /**
     * VENT DESCENT ([stated] "the host party would spawn in and detect hosts on the level below them and then use the
     * vent to go down to that level and grab those hosts"): before a stalled hunter writes a quarry off as unreachable,
     * it checks the duct network - a hive vent within {@link #DUCT_TO_QUARRY_RANGE_SQUARED} of the quarry, while the
     * hunter itself stands in its hive's territory or near any vent, means the prey IS reachable: through the walls.
     * The hunter ducts to that vent's emergence spot and the chase restarts from there. One duct per quarry - a second
     * stall on the same prey means the vent didn't help, and the ordinary write-off proceeds.
     */
    private static final Map<Xenomorph, java.util.UUID> DUCTED_FOR_QUARRY = new WeakHashMap<>();

    /** A vent this close to the quarry counts as "on its shelf". */
    private static final double DUCT_TO_QUARRY_RANGE_SQUARED = 16.0 * 16.0;

    /** The hunter must be plausibly on the network: within territory, or this close to any of its hive's vents. */
    private static final double DUCT_ENTRY_RANGE_SQUARED = 32.0 * 32.0;

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
            CHASE_PROGRESS.remove(xenomorph);
            FAILED_PATH_TICKS.remove(xenomorph);
            DUCTED_FOR_QUARRY.remove(xenomorph);
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
                    if (!tryDuctToQuarry(xenomorph, target)) {
                        giveUpOnQuarry(context, xenomorph, target);
                    } else {
                        FAILED_PATH_TICKS.remove(xenomorph);
                        CHASE_PROGRESS.remove(xenomorph);
                    }
                    return Action.Signal.CONTINUE;
                }
            }
        }
        // Distance-based stall net: MOVING at a cliff edge is not progress. See CHASE_PROGRESS.
        var distanceSquared = xenomorph.distanceToSqr(target);
        var gameTime = xenomorph.level().getGameTime();
        var progress = CHASE_PROGRESS.get(xenomorph);
        if (
            progress == null
                || !progress.targetId().equals(target.getUUID())
                || distanceSquared <= progress.bestDistSquared() - PROGRESS_EPSILON_SQUARED
        ) {
            CHASE_PROGRESS.put(xenomorph, new ChaseProgress(target.getUUID(), distanceSquared, gameTime));
        } else if (gameTime - progress.lastProgressGameTime() >= STALL_TIMEOUT_TICKS) {
            if (!tryDuctToQuarry(xenomorph, target)) {
                giveUpOnQuarry(context, xenomorph, target);
            } else {
                FAILED_PATH_TICKS.remove(xenomorph);
                CHASE_PROGRESS.remove(xenomorph);
            }
        }

        return Action.Signal.CONTINUE;
    }

    /**
     * Try the duct network before giving up: teleport to the hive vent nearest the quarry if one sits on its shelf.
     * True = ducted (chase restarts from the vent); false = the network doesn't reach, write it off.
     */
    private static boolean tryDuctToQuarry(Xenomorph xenomorph, LivingEntity target) {
        if (target.getUUID().equals(DUCTED_FOR_QUARRY.get(xenomorph))) {
            return false; // already ducted for this prey - the vent didn't help
        }
        if (!(xenomorph.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return false;
        }
        var location = com.alien.common.gameplay.hive.location.HiveLocationRegistry.INSTANCE.findNearestInDim(
            serverLevel.dimension(),
            xenomorph.blockPosition()
        );
        if (location == null) {
            return false;
        }
        var vents = com.alien.common.gameplay.hive.party.PartyVentUtil.findSurfaceVents(serverLevel, location);
        if (vents.isEmpty()) {
            return false;
        }
        net.minecraft.core.BlockPos ventNearQuarry = null;
        var bestDist = DUCT_TO_QUARRY_RANGE_SQUARED;
        var onNetwork = location.claimedChunks().contains(new net.minecraft.world.level.ChunkPos(xenomorph.blockPosition()));
        for (var vent : vents) {
            var toQuarry = target.distanceToSqr(vent.getX() + 0.5, vent.getY() + 0.5, vent.getZ() + 0.5);
            if (toQuarry <= bestDist) {
                bestDist = toQuarry;
                ventNearQuarry = vent;
            }
            if (
                !onNetwork
                    && xenomorph.distanceToSqr(vent.getX() + 0.5, vent.getY() + 0.5, vent.getZ() + 0.5) <= DUCT_ENTRY_RANGE_SQUARED
            ) {
                onNetwork = true;
            }
        }
        if (ventNearQuarry == null || !onNetwork) {
            return false;
        }
        var emerge = com.alien.common.gameplay.hive.party.PartyVentUtil.surfaceEmergeSpot(serverLevel, ventNearQuarry);
        var landing = emerge != null ? emerge : ventNearQuarry.above();
        xenomorph.teleportTo(landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5);
        DUCTED_FOR_QUARRY.put(xenomorph, target.getUUID());
        com.alien.Alien.LOGGER.info(
            "[hostdbg] ducted through vent {} to reach quarry {} at {} - chase restarting",
            ventNearQuarry,
            target.getType().getDescriptionId(),
            target.blockPosition()
        );
        return true;
    }

    /**
     * Write this quarry off as unreachable and clear both stall trackers - the sensor hands the drone a different host
     * next tick. Give up on the HOST, never on hunting.
     */
    private static void giveUpOnQuarry(Action.Context context, Xenomorph xenomorph, LivingEntity target) {
        FAILED_PATH_TICKS.remove(xenomorph);
        CHASE_PROGRESS.remove(xenomorph);
        HostClaims.writeOffUnreachable(target, xenomorph);
        NeoMoveToPosAction.onFinish(context);
        com.alien.Alien.LOGGER.info(
            "[hostdbg] gave up on unreachable quarry {} at {} - looking for another",
            target.getType().getDescriptionId(),
            target.blockPosition()
        );
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
