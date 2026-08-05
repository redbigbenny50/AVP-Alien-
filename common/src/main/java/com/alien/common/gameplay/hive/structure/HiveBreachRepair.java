package com.alien.common.gameplay.hive.structure;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.structure.carve.CarveWorker;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Drones REPAIR breaches instead of the world silently healing itself ([stated] "can the restamp have drones 'repair'
 * it as in it sends a drone or drones to the site to do the dig animation as if they are fixing the breach?"). When
 * upkeep finds a damaged piece it no longer re-stamps on the spot - it files a repair job here. Each job recruits up to
 * {@link #CREW_SIZE} free drones/runners (the same freedom checks the carve crews use), steers them to the damaged
 * cells, holds them there in the synced dig gait (the animation the carve system already ships), and only when they
 * have visibly worked for {@link #REPAIR_WORK_TICKS} does the piece re-stamp through
 * {@link HiveStructureUpkeep#restamp}. A hive with no free workers simply leaves the wound open until hands free up -
 * repair is something the hive DOES now, not something that happens to it.
 * <p>
 * State is transient by design: jobs are not persisted, because the breach itself is. After a reload the upkeep
 * rotation re-detects the damage and files a fresh job. Workers that die, unload, or pick up a real task are dropped
 * from the roster and replaced on the next beat.
 */
public final class HiveBreachRepair {

    private HiveBreachRepair() {}

    /** Repair crews are small - a wound is not a construction site. */
    private static final int CREW_SIZE = 2;

    /** Visible work before the piece re-stamps: about 10 seconds of digging at the wound. */
    private static final int REPAIR_WORK_TICKS = 200;

    /** How close a worker must stand to a damaged cell to count as working (and to wear the dig gait). */
    private static final double WORK_RANGE_SQUARED = 4.5 * 4.5;

    private static final double STEER_SPEED = 1.0;

    /** Beat driven from the location tick. */
    public static final int TICK_INTERVAL = 20;

    /** A job dropped this many beats in a row (piece unresolvable / chunks unloaded) is quietly forgotten. */
    private static final int MAX_DEAD_BEATS = 15;

    private static final class RepairJob {

        final List<BlockPos> breachCells;

        final List<UUID> workers = new ArrayList<>();

        int workTicksDone;

        int deadBeats;

        RepairJob(List<BlockPos> breachCells) {
            this.breachCells = breachCells;
        }
    }

    /** Active jobs per location, keyed by piece origin. Weakly held - a dead location takes its jobs with it. */
    private static final Map<HiveLocation, Map<ChunkPos, RepairJob>> JOBS = new WeakHashMap<>();

    /** Files (or refreshes) a repair job for a breached piece. Called by upkeep instead of re-stamping. */
    public static void noteBreach(HiveLocation location, ChunkPos originChunk, List<BlockPos> breachCells) {
        var jobs = JOBS.computeIfAbsent(location, key -> new HashMap<>());
        var existing = jobs.get(originChunk);
        if (existing != null) {
            // Keep the crew and the progress; just point them at the freshest picture of the damage.
            existing.breachCells.clear();
            existing.breachCells.addAll(breachCells);
            return;
        }
        jobs.put(originChunk, new RepairJob(new ArrayList<>(breachCells)));
    }

    /** True when this piece already has a crew on the way - upkeep skips filing duplicates. */
    public static boolean hasJob(HiveLocation location, ChunkPos originChunk) {
        var jobs = JOBS.get(location);
        return jobs != null && jobs.containsKey(originChunk);
    }

    /** Drives every open job for this location: recruit, steer, animate, and re-stamp on completion. */
    public static void tick(ServerLevel level, HiveLocation location) {
        var jobs = JOBS.get(location);
        if (jobs == null || jobs.isEmpty()) {
            return;
        }
        for (var iterator = jobs.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            var origin = entry.getKey();
            var job = entry.getValue();
            var placement = location.builtPlacements().get(origin);
            if (placement == null || job.breachCells.isEmpty()) {
                releaseAll(level, job);
                iterator.remove(); // piece gone from the record - nothing to repair
                continue;
            }
            if (!chunksLoaded(level, origin)) {
                if (++job.deadBeats >= MAX_DEAD_BEATS) {
                    releaseAll(level, job);
                    iterator.remove(); // wandered out of the loaded world; upkeep re-detects on return
                }
                continue;
            }
            job.deadBeats = 0;

            recruit(level, location, job);

            var working = 0;
            for (var workerIterator = job.workers.iterator(); workerIterator.hasNext();) {
                var id = workerIterator.next();
                if (
                    !(level.getEntity(id) instanceof Xenomorph drone)
                        || !(drone instanceof CarveWorker worker)
                        || !drone.isAlive()
                        || drone.getTarget() != null
                ) {
                    workerIterator.remove(); // died, unloaded, or found a fight - replaced next beat
                    continue;
                }
                var cell = nearestCell(job.breachCells, drone.blockPosition());
                if (drone.distanceToSqr(cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5) <= WORK_RANGE_SQUARED) {
                    // At the wound: wear the dig gait and count as working. The gait flag IS the animation - the
                    // same synced accessor the carve crews use, read by each caste's animator.
                    if (worker.carveDigMode().get() != 1) {
                        worker.carveDigMode().set(1);
                    }
                    drone.getLookControl().setLookAt(cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5);
                    working++;
                } else {
                    if (worker.carveDigMode().get() != 0) {
                        worker.carveDigMode().set(0);
                    }
                    if (drone.getNavigation().isDone()) {
                        drone.getNavigation().moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, STEER_SPEED);
                    }
                }
            }

            if (working > 0) {
                job.workTicksDone += TICK_INTERVAL;
            }
            if (job.workTicksDone >= REPAIR_WORK_TICKS) {
                releaseAll(level, job);
                iterator.remove();
                if (HiveStructureUpkeep.restamp(level, location, origin, placement)) {
                    com.alien.Alien.LOGGER.info(
                        "Hive: breach at {} repaired by drone crew for location {}",
                        origin,
                        location.id()
                    );
                }
            }
        }
    }

    /** Tops the crew up to size from free drones/runners - the carve crews' freedom rules, minus the site coupling. */
    private static void recruit(ServerLevel level, HiveLocation location, RepairJob job) {
        if (job.workers.size() >= CREW_SIZE) {
            return;
        }
        for (var entry : location.loadedMembersByType().entrySet()) {
            if (!entry.getKey().is(AlienEntityTypeTags.DRONES) && !entry.getKey().is(AlienEntityTypeTags.RUNNERS)) {
                continue;
            }
            for (UUID id : new ArrayList<>(entry.getValue())) {
                if (job.workers.size() >= CREW_SIZE) {
                    return;
                }
                if (job.workers.contains(id)) {
                    continue;
                }
                if (!(level.getEntity(id) instanceof Xenomorph drone) || !(drone instanceof CarveWorker) || !drone.isAlive()) {
                    continue;
                }
                if (drone.getTarget() != null || hasRealTask(drone)) {
                    continue;
                }
                job.workers.add(id);
            }
        }
    }

    /** Mirror of the carve crews' "is it actually free" test. */
    private static boolean hasRealTask(Xenomorph drone) {
        return com.alien.common.gameplay.hive.party.EggDutyGuard.isOnEggDuty(drone)
            || drone.partyMembership() != null
            || drone.isVehicle()
            || drone.isMarkedForReserveReturn();
    }

    private static void releaseAll(ServerLevel level, RepairJob job) {
        for (UUID id : job.workers) {
            if (level.getEntity(id) instanceof CarveWorker worker && worker.carveDigMode().get() != 0) {
                worker.carveDigMode().set(0);
            }
        }
        job.workers.clear();
    }

    private static boolean chunksLoaded(ServerLevel level, ChunkPos origin) {
        return level.isLoaded(origin.getWorldPosition());
    }

    private static BlockPos nearestCell(List<BlockPos> cells, BlockPos from) {
        BlockPos best = cells.get(0);
        var bestDist = Double.MAX_VALUE;
        for (BlockPos cell : cells) {
            var dist = cell.distSqr(from);
            if (dist < bestDist) {
                bestDist = dist;
                best = cell;
            }
        }
        return best;
    }
}
