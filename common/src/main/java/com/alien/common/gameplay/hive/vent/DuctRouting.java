package com.alien.common.gameplay.hive.vent;

import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * ⭐⭐ "USE THE VENTS WHENEVER POSSIBLE" - THE SHARED SIDE OF DUCT TRAVEL.
 * <p>
 * [stated] Oct 2: "they should use the vents whenever possible so whatever jobs you can assign or allow with the vents
 * please do. thats how it was supposed to work." Duct travel ({@link HiveVents#planInteriorLeg},
 * {@link HiveVents#ductTravel}) existed, but only egg pickup, egg drop-off and breach repair used it, and the
 * reachability tests that hand out work only asked whether the job could be WALKED to - so a job reachable only through
 * the ducts was never handed out at all.
 * </p>
 * <ul>
 * <li>{@link #canReach} - "can this worker get there?" = through an interior duct OR on foot. Used wherever work is
 * assigned, so room-D jobs go to room-A workers.</li>
 * <li>{@link #stepToward} - drop-in for a GOAP mover: returns where to walk THIS tick (the entry vent's approach when a
 * leg is planned, otherwise the target) and performs the hop on arrival. Any action that moves inside the hive can
 * route through it without carrying its own leg state.</li>
 * </ul>
 * <p>
 * ⚠ INTERIOR ONLY. A leg is only taken when the traveller AND the target are both inside the same hive's claim and band
 * - never to cross the surface, never into another hive, never for queens (they are not vent-sized and have their own
 * movement).
 * </p>
 */
public final class DuctRouting {

    private DuctRouting() {}

    /** A planned leg for one traveller, keyed to the target block it was planned for. */
    private record Leg(
        BlockPos target,
        BlockPos entry,
        BlockPos exit,
        @Nullable BlockPos approach,
        long startTick
    ) {}

    private static final Map<Mob, Leg> LEGS = Collections.synchronizedMap(new WeakHashMap<>());

    /** Last time a traveller looked for a leg and found none, so a walking mob is not re-planned every tick. */
    private static final Map<Mob, Long> LAST_PLAN = Collections.synchronizedMap(new WeakHashMap<>());

    /** A target that has moved further than this from where the leg was planned invalidates the leg. */
    private static final double RETARGET_DISTANCE_SQUARED = 8.0 * 8.0;

    /** Re-plan at most once per second per traveller. */
    private static final long PLAN_COOLDOWN_TICKS = 20L;

    /** Give up on a leg whose entry vent is not reached in this long, and walk instead. Ten seconds. */
    private static final long LEG_TIMEOUT_TICKS = 200L;

    /** Close enough to slip into the entry vent - the same reach the egg haulers use. */
    private static final double VENT_REACH_SQUARED = 2.5 * 2.5;

    /**
     * Whether {@code mob} can get to {@code target}: through an interior duct (checked first - it costs a few map
     * lookups, not a path search) or on foot.
     *
     * @param requireFullPath false keeps the old lenient test (any path, even a partial one, counts)
     */
    public static boolean canReach(Mob mob, BlockPos target, boolean requireFullPath) {
        if (isInteriorTrip(mob, target) && HiveVents.planInteriorLeg(mob, target, true).isPresent()) {
            return true;
        }
        var path = mob.getNavigation().createPath(target, 0);
        return path != null && (!requireFullPath || path.canReach());
    }

    /**
     * Where {@code mob} should walk this tick on its way to {@code target}. Performs the duct hop when it arrives at
     * the entry vent. Returns {@code target} itself whenever walking is the answer.
     */
    public static Vec3 stepToward(Mob mob, Vec3 target) {
        var targetBlock = BlockPos.containing(target);
        var leg = LEGS.get(mob);
        // A moving target (a fleeing host) keeps its leg while it stays near where the leg was planned for.
        if (leg != null && leg.target().distSqr(targetBlock) > RETARGET_DISTANCE_SQUARED) {
            LEGS.remove(mob);
            leg = null;
        }

        var now = mob.level().getGameTime();
        if (leg == null) {
            var last = LAST_PLAN.get(mob);
            if (last != null && now - last < PLAN_COOLDOWN_TICKS) {
                return target;
            }
            LAST_PLAN.put(mob, now);
            if (!isInteriorTrip(mob, targetBlock)) {
                return target;
            }
            var planned = HiveVents.planInteriorLeg(mob, targetBlock, false);
            if (planned.isEmpty()) {
                return target;
            }
            var approach = HiveVents.emergencePosNear(mob.level(), planned.get().entry());
            leg = new Leg(targetBlock, planned.get().entry(), planned.get().exit(), approach, now);
            LEGS.put(mob, leg);
        }

        if (now - leg.startTick() > LEG_TIMEOUT_TICKS) {
            LEGS.remove(mob); // never trap a worker on its shortcut - walk instead
            return target;
        }

        var approach = leg.approach() != null ? Vec3.atBottomCenterOf(leg.approach()) : Vec3.atCenterOf(leg.entry());
        if (mob.distanceToSqr(approach) <= VENT_REACH_SQUARED) {
            LEGS.remove(mob);
            mob.getNavigation().stop();
            HiveVents.ductTravel(mob, leg.entry(), leg.exit());
            return target;
        }
        return approach;
    }

    /** Forget any planned leg - for actions that end or change job. */
    public static void clear(Mob mob) {
        LEGS.remove(mob);
    }

    /**
     * Both ends inside the SAME hive's claim and band, and the traveller is not a royal. The only trips the interior
     * duct network may serve.
     */
    public static boolean isInteriorTrip(Mob mob, BlockPos target) {
        if (mob instanceof com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen) {
            return false;
        }
        var dimension = mob.level().dimension();
        HiveLocation here = HiveLocationRegistry.INSTANCE.getByChunk(dimension, mob.chunkPosition());
        if (here == null || !here.withinSlab(mob.getBlockY())) {
            return false;
        }
        var there = HiveLocationRegistry.INSTANCE.getByChunk(dimension, new ChunkPos(target));
        return there == here && here.withinSlab(target.getY());
    }
}
