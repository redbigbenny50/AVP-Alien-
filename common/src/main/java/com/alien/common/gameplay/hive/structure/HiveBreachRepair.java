package com.alien.common.gameplay.hive.structure;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.spawning.HiveLoadedSpawner;
import com.alien.common.gameplay.hive.spawning.ReserveSpawnUtil;
import com.alien.common.gameplay.hive.structure.carve.CarveWorker;
import com.alien.common.gameplay.hive.vent.HiveVents;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
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

    /** One reserve call per breach per 15s - the bank is the hive's last labour, not a tap to be held open. */
    private static final long RESERVE_CALL_COOLDOWN_TICKS = 300L;

    private static final int RESERVE_VENT_SEARCH_CHUNKS = 2;

    /** A chunk's width plus a margin, squared - "still in that chunk" without a hard edge to stand behind. */
    private static final double PLAYER_HOLD_RANGE_SQUARED = 20.0 * 20.0;

    /**
     * ⭐ THE DIAL. How long a piece is left alone after a successful re-stamp: 2 minutes.
     * <p>
     * [stated] "yeah there should be a cool down between repairs." A room the world keeps re-breaking - one built into
     * an aquifer or a lava lake, where the fluid seeps straight back the moment the masonry lands - could otherwise
     * take the crew again within seconds of finishing, and the tester's log showed exactly that: origin [6, 1]
     * re-repaired TWELVE SECONDS after a successful stamp. Repair and construction draw on one pool of drones (see
     * {@link #isOnRepairCrew}), so a single leaking room was able to hold the hive's entire workforce and the carve
     * sites logged "unstaffed" all session.
     * </p>
     * <p>
     * ⚠ THIS IS A RATE LIMIT, NOT SELF-HEAL. [stated] "the hive shouldnt self heal it needs workers its part of
     * attrition" still stands - nothing here repairs anything on its own. The wound simply waits its turn. <b>Raise
     * it</b> if a leaky hive still feels like it is spending all its labour on the same room; <b>lower it</b> if
     * players find they can breach, wait, and walk in before the crew arrives.
     * </p>
     */
    private static final long REPAIR_COOLDOWN_TICKS = 2400L;

    /** Retired cells per piece, bounded so a pathological piece cannot grow this without limit. */
    private static final int MAX_RETIRED_CELLS_PER_PIECE = 64;

    private static final class RepairJob {

        final List<BlockPos> breachCells;

        final List<UUID> workers = new ArrayList<>();

        int workTicksDone;

        int deadBeats;

        /** Workers already given their one duct ride to this breach - a shortcut, not a teleport loop. */
        final java.util.Set<UUID> ducted = new java.util.HashSet<>();

        /** Game time the reserves were last tapped for this breach, so a hole cannot drain the bank. */
        long lastReserveCallAt = Long.MIN_VALUE;

        RepairJob(List<BlockPos> breachCells) {
            this.breachCells = breachCells;
        }
    }

    /** Active jobs per location, keyed by piece origin. Weakly held - a dead location takes its jobs with it. */
    private static final Map<HiveLocation, Map<ChunkPos, RepairJob>> JOBS = new WeakHashMap<>();

    /** Game time each piece was last successfully re-stamped, so a wound cannot be re-taken the moment it heals. */
    private static final Map<HiveLocation, Map<ChunkPos, Long>> LAST_REPAIRED = new WeakHashMap<>();

    /** Cells a re-stamp has proven it cannot fix - see {@code HiveStructureUpkeep.markUnfixableCells}. */
    private static final Map<HiveLocation, Map<ChunkPos, java.util.Set<BlockPos>>> RETIRED_CELLS = new WeakHashMap<>();

    /**
     * True while this piece is inside its post-repair cooldown. Consulted by the detector BEFORE it scans, so a cooling
     * piece costs nothing at all rather than costing a scan whose result is thrown away.
     */
    public static boolean isCoolingDown(ServerLevel level, HiveLocation location, ChunkPos originChunk) {
        var byChunk = LAST_REPAIRED.get(location);
        if (byChunk == null) {
            return false;
        }
        var last = byChunk.get(originChunk);
        return last != null && level.getGameTime() - last < REPAIR_COOLDOWN_TICKS;
    }

    /**
     * The cells this piece has given up on. Returned as a live view for the detector to test against; empty for the
     * overwhelming majority of pieces, which is the steady state.
     */
    public static java.util.Set<BlockPos> retiredCells(HiveLocation location, ChunkPos originChunk) {
        var byChunk = RETIRED_CELLS.get(location);
        if (byChunk == null) {
            return java.util.Set.of();
        }
        var cells = byChunk.get(originChunk);
        return cells == null ? java.util.Set.of() : cells;
    }

    /**
     * Records cells that survived a re-stamp still reading as damaged, so the detector stops reporting them.
     * <p>
     * ⚠ TRANSIENT ON PURPOSE, like the jobs themselves. A reload costs one re-learning repair per affected piece and
     * nothing worse, and it means a piece whose template is later re-exported with the gap authored properly starts
     * being repaired again without any migration.
     * </p>
     */
    public static void retireUnfixableCells(HiveLocation location, ChunkPos originChunk, List<BlockPos> cells) {
        var byChunk = RETIRED_CELLS.computeIfAbsent(location, key -> new HashMap<>());
        var retired = byChunk.computeIfAbsent(originChunk, key -> new java.util.HashSet<>());
        for (var cell : cells) {
            if (retired.size() >= MAX_RETIRED_CELLS_PER_PIECE) {
                return;
            }
            retired.add(cell.immutable());
        }
    }

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

            // ⚠ DO NOT SEND A CREW WHILE A PLAYER IS STOOD IN THE HOLE.
            // [stated] "the repair shouldnt happen when a player is still nearby only because they can use that as a
            // trap to keep feeding workers and shoot them when they appear." The breach stays FLAGGED and the job
            // stays ALIVE - deadBeats is deliberately not advanced here, so camping the hole postpones the repair
            // rather than cancelling it. Any crew already dispatched is recalled so it is not fed in piecemeal.
            if (playerHolding(level, job)) {
                releaseAll(level, job);
                continue;
            }

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
                        // Take a duct first if one shortens the trip. A repair crew used to WALK the whole hive, which
                        // on a finished one is slow enough to look broken - egg haulers have been ducting all along.
                        // ONE ride per worker per breach (job.ducted): the leg is a shortcut, not a way to teleport a
                        // drone onto the wall it is supposed to walk up to.
                        if (!job.ducted.contains(id)) {
                            job.ducted.add(id);
                            var leg = HiveVents.planInteriorLeg(drone, cell, false);
                            if (leg.isPresent() && HiveVents.ductTravel(drone, leg.get().entry(), leg.get().exit())) {
                                continue; // emerged elsewhere - steer from the new spot next beat
                            }
                        }
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
                    // Stamp the cooldown BEFORE logging, and only on a stamp that actually landed: a re-stamp that
                    // bailed (chunks gone, template missing) has repaired nothing and must not buy quiet time.
                    LAST_REPAIRED
                        .computeIfAbsent(location, key -> new HashMap<>())
                        .put(origin, level.getGameTime());
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
    /**
     * True when this drone is already on a repair crew anywhere.
     * <p>
     * A HOLE IN THE WALL OUTRANKS A NEW CORRIDOR. Repair and construction draw on the same free-drone pool, so
     * {@code CarveWorkers.hasRealTask} consults this — which does both halves in one move: a carve site will not
     * recruit a drone that is patching a wound, and a drone drafted onto a repair crew is RELEASED from its carve site
     * on that site's next tick, exactly as egg duty, a party or a carried passenger already outrank construction.
     * </p>
     * <p>
     * The repair recruiter deliberately makes NO reciprocal check, and that asymmetry is what gives repair first claim:
     * repair takes the drone, construction lets go. Fast-pathed on the empty map, which is the steady state.
     * </p>
     */
    public static boolean isOnRepairCrew(UUID droneId) {
        if (JOBS.isEmpty()) {
            return false;
        }

        for (var byChunk : JOBS.values()) {
            for (var job : byChunk.values()) {
                if (job.workers.contains(droneId)) {
                    return true;
                }
            }
        }
        return false;
    }

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

        callUpReserves(level, location, job);
    }

    /**
     * Pulls a worker out of the hive's reserves at a vent by the breach when nobody loaded is free.
     * <p>
     * [stated] "repair work should search for workers across the whole hive loaded or unloaded and use the vents for
     * fast traveling. no xeno is stranded in a hive." An unloaded xenomorph is not an entity and cannot be steered, so
     * the reserves ARE the handle on it - this is the same tap {@code VentDefenseTask} uses to answer a cry for help,
     * and it emerges the same way, at a vent mouth.
     * </p>
     * <p>
     * GUARDRAILS, mirroring the defence wave for the same reason: one call per {@link #RESERVE_CALL_COOLDOWN_TICKS} per
     * breach, and only ever enough to reach {@code CREW_SIZE}. A hole in the wall must not be able to empty the bank,
     * and a player breaking blocks repeatedly must not be able to pump it dry either.
     * </p>
     */
    private static void callUpReserves(ServerLevel level, HiveLocation location, RepairJob job) {
        if (job.workers.size() >= CREW_SIZE || job.breachCells.isEmpty()) {
            return;
        }

        var now = level.getGameTime();
        if (now - job.lastReserveCallAt < RESERVE_CALL_COOLDOWN_TICKS) {
            return;
        }

        var breach = job.breachCells.get(0);
        var vents = HiveVents.ventsNear(location.ventManager(), breach, RESERVE_VENT_SEARCH_CHUNKS, null);
        if (vents.isEmpty()) {
            return; // nowhere for them to come out - the hive has no vent near this wound
        }

        for (var vent : vents) {
            if (job.workers.size() >= CREW_SIZE) {
                return;
            }

            var emergence = HiveVents.emergencePosNear(level, vent);
            if (emergence == null) {
                continue; // that mouth is blocked
            }

            var type = pickReserveWorkerType(location);
            if (type == null) {
                return; // the bank holds no drones or runners
            }

            var worker = HiveLoadedSpawner.trySpawnIdentityReserve(level, location, type, emergence);
            if (worker == null) {
                continue;
            }

            ReserveSpawnUtil.markSpawnedFromReserves(worker);
            job.lastReserveCallAt = now;
            job.workers.add(worker.getUUID());
            com.alien.Alien.LOGGER.info(
                "Hive at {}: called a worker up from reserves at {} to repair a breach.",
                location.centerPos(),
                emergence
            );
        }
    }

    /** A reserve caste that can actually carve - only Drone and Runner implement CarveWorker. */
    private static @org.jetbrains.annotations.Nullable EntityType<?> pickReserveWorkerType(HiveLocation location) {
        var variant = location.lineageVariantOrNull();
        if (variant == null) {
            return null;
        }

        // Drones first, runners second - the same two castes that implement CarveWorker, resolved for THIS strain so a
        // nether hive calls up nether drones. Same CasteResolver route VentDefenseTask uses for its defenders.
        for (var caste : java.util.List.of(AlienEntityTypeTags.DRONES, AlienEntityTypeTags.RUNNERS)) {
            var type = com.alien.common.gameplay.hive.economy.CasteResolver.entityTypeForCaste(variant, caste);

            if (type != null && location.localReserves().getCount(type) > 0) {
                return type;
            }
        }
        return null;
    }

    /** Mirror of the carve crews' "is it actually free" test. */
    private static boolean hasRealTask(Xenomorph drone) {
        return com.alien.common.gameplay.hive.party.EggDutyGuard.isOnEggDuty(drone)
            || drone.partyMembership() != null
            || drone.isVehicle()
            || drone.isMarkedForReserveReturn();
    }

    /**
     * Whether a player is close enough to the breach to be farming it.
     * <p>
     * "In that chunk" as specified, plus a small margin so standing just over the chunk line does not work as a
     * loophole. A player who is BEING FOUGHT does not hold the breach - the defence teams are the answer to a player in
     * the hive, and repair should resume behind them while they are busy.
     * </p>
     */
    private static boolean playerHolding(ServerLevel level, RepairJob job) {
        for (var cell : job.breachCells) {
            for (var player : level.players()) {
                // [stated] "if the player is in creative or spectator the rule about avoiding the player doesnt count".
                // The guard exists because a survival player can farm the crew at the hole; neither of these can -
                // a spectator cannot interact at all, and a creative player has nothing to gain and nothing at risk.
                // Letting them hold the breach would just mean a builder standing in their own hive stalls it forever.
                if (player.isSpectator() || player.isCreative() || !player.isAlive()) {
                    continue;
                }

                // Already engaged: the garrison has them, so the crew can work behind the fight.
                if (player.getLastHurtByMob() != null || player.getLastHurtMob() != null) {
                    continue;
                }

                if (player.distanceToSqr(cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5) <= PLAYER_HOLD_RANGE_SQUARED) {
                    return true;
                }
            }
        }
        return false;
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
