package com.alien.common.gameplay.hive.structure.carve;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.party.EggDutyGuard;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/**
 * The carve crew (construction economy step 5, design \u00a78.1/\u00a78.2): sources, steers, and releases the drones
 * that visually dig and resin a {@link CarveSite}. Everything here is presentation and pacing input - the site's world
 * mutation ({@link CarveSiteWork}) fires on its own cadence whenever the site is STAFFED, whether or not a drone has
 * physically pathed to the front. Gating block-work on drone pathing would let one stuck drone wedge the whole build,
 * which the never-wedge rule forbids; the drones sell the show, the site guarantees the work.
 * <h2>Sourcing - hybrid, loaded-first (design \u00a78.1)</h2> Idle loaded drones that pass the free-of-a-task test are
 * borrowed first; only the shortfall is materialized from reserves (the party {@code trySpawn} idiom, spawned at the
 * site's doorway). Construction is the LOWEST-priority claim on a drone: a borrowed worker that acquires a real task -
 * a target, a party, egg duty, a passenger - is released immediately and replaced on the next top-up, so building never
 * starves hauling or defense. A materialized transient that merely picks up an attack target (defending itself) PAUSES
 * instead: it keeps its slot, stops counting toward pace, and resumes when the fight ends; if it joins a party or egg
 * duty it is released outright and simply becomes a normal member (it earned its place). Workers who are still
 * materialized at completion fold back into reserves identity-intact ({@code addReturningIdentityMember}), the same
 * absorb idiom the brood bank uses.
 * <h2>Zero workers (confirmed design call)</h2> No free drones AND nothing in reserves = no progress, full stop, with a
 * periodic log. No ghost fallback: a hive that cannot field a single drone is a hive in collapse, and the moment the
 * queen's eggs refill the reserve the build resumes on its own. Routing correctly waits behind the unfinished piece
 * (design \u00a78.4).
 */
public final class CarveWorkers {

    /** What a crew member does. Diggers clear clumps (dig pace); placers trail resin (fill pace). */
    public enum Role {
        DIGGER,
        PLACER
    }

    /** The staffing snapshot for one tick: how many ACTIVE (not paused, not fighting) workers each track has. */
    public record Crew(
        int diggers,
        int placers
    ) {

        public boolean unstaffed() {
            return diggers == 0 && placers == 0;
        }
    }

    /** Up to 3 diggers (design \u00a74: -30s each off the 90s clock) and 2 placers (design \u00a75). */
    static final int MAX_DIGGERS = 3;

    static final int MAX_PLACERS = 2;

    /** Crew maintenance cadence: top-up, release checks, steering nudges, animation re-dispatch. ~3s. */
    private static final int CREW_INTERVAL_TICKS = 60;

    /** Placers play {@code walk dig} at 50% - slower than the diggers' 70% (design \u00a75). */
    private static final float PLACER_DIG_ANIMATION_SPEED = 0.5F;

    /** Steering speed for the navigation nudge (the BroodBankTask idiom - GOAP may fight it; it converges). */
    private static final double STEER_SPEED = 1.0;

    private CarveWorkers() {}

    /**
     * One crew pass: validate the roster (drop the dead and the drafted), top up from loaded drones then reserves,
     * nudge everyone toward their front, keep the dig gait playing. Returns the ACTIVE staffing for pacing. Cheap on
     * most ticks: between maintenance beats it only resolves and counts the (at most 5) rostered workers.
     */
    static Crew tick(
        ServerLevel level,
        HiveLocation location,
        CarveSite site,
        @Nullable BlockPos digFront,
        @Nullable BlockPos fillFront
    ) {
        long now = level.getGameTime();
        var maintenance = now >= site.nextCrewTick;
        if (maintenance) {
            site.nextCrewTick = now + CREW_INTERVAL_TICKS;
        }

        var activeDiggers = 0;
        var activePlacers = 0;

        // Roster pass: resolve every rostered UUID, drop or pause per the release rules, steer and count the rest.
        for (var iterator = site.workers.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            var worker = level.getEntity(entry.getKey());
            if (!(worker instanceof Xenomorph drone) || !(drone instanceof CarveWorker) || !drone.isAlive()) {
                // Dead, despawned, or wandered out of loaded range: off the roster (design \u00a78.2 - replace, and the
                // clock just slows meanwhile). A materialized one that died stays dead; no reserve refund for a corpse.
                iterator.remove();
                site.materializedWorkers.remove(entry.getKey());
                continue;
            }

            var materialized = site.materializedWorkers.contains(entry.getKey());
            if (drone.getTarget() != null) {
                if (materialized) {
                    // Paused: defending itself. Keeps the slot, doesn't count toward pace, resumes after - and drops
                    // the dig gait while the fight lasts (the attack animations own the body).
                    if (((CarveWorker) drone).carveDigMode().get() != 0) {
                        ((CarveWorker) drone).carveDigMode().set(0);
                    }
                    continue;
                }
                release(drone, site, iterator);
                continue;
            }
            if (hasRealTask(drone)) {
                // A party, egg duty, or a passenger outranks construction for EVERYONE. A materialized worker that
                // gets drafted stops being a transient and becomes a normal member - it earned its place.
                release(drone, site, iterator);
                continue;
            }

            if (entry.getValue() == Role.DIGGER) {
                activeDiggers++;
            } else {
                activePlacers++;
            }

            if (maintenance) {
                var front = entry.getValue() == Role.DIGGER ? digFront : fillFront;
                if (front != null && drone.getNavigation().isDone()) {
                    drone.getNavigation().moveTo(front.getX() + 0.5, front.getY(), front.getZ() + 0.5, STEER_SPEED);
                }
            }
            // The gait rides a synced mode flag, not a server dispatch (animation commands are client-side only -
            // the AzCommand warning). Each caste's animator folds the mode into its own locomotion selection.
            var mode = entry.getValue() == Role.DIGGER ? 1 : 2;
            if (((CarveWorker) drone).carveDigMode().get() != mode) {
                ((CarveWorker) drone).carveDigMode().set(mode);
            }
        }

        if (maintenance) {
            // The founding core's digger is the QUEEN (design §7b) - drones are only ever its placers, so digger
            // sourcing is skipped entirely for it.
            var neededDiggers = site.isFoundingCore() ? 0 : MAX_DIGGERS - countRole(site, Role.DIGGER);
            var neededPlacers = MAX_PLACERS - countRole(site, Role.PLACER);
            if (neededDiggers > 0 || neededPlacers > 0) {
                var borrowed = borrowLoadedWorkers(level, location, site, neededDiggers, neededPlacers);
                neededDiggers -= borrowed[0];
                neededPlacers -= borrowed[1];
                var shortfall = neededDiggers + neededPlacers;
                if (shortfall > 0) {
                    materializeFromReserves(level, location, site, neededDiggers, neededPlacers);
                }
            }
        }

        return new Crew(activeDiggers, activePlacers);
    }

    /**
     * The free-of-a-task test from design \u00a78.1, minus the attack-target clause (handled separately so materialized
     * workers can pause instead of release): no egg duty, no party, no passenger (an egg on the back or a host in the
     * claws IS the higher-priority job).
     */
    private static boolean hasRealTask(Xenomorph drone) {
        return EggDutyGuard.isOnEggDuty(drone) || drone.partyMembership() != null || drone.isVehicle();
    }

    private static void release(Xenomorph drone, CarveSite site, java.util.Iterator<Map.Entry<UUID, Role>> iterator) {
        iterator.remove();
        site.materializedWorkers.remove(drone.getUUID());
        // Lower the synced gait mode; the client animator returns to normal locomotion on its own.
        if (((CarveWorker) drone).carveDigMode().get() != 0) {
            ((CarveWorker) drone).carveDigMode().set(0);
        }
    }

    private static int countRole(CarveSite site, Role role) {
        var count = 0;
        for (Role r : site.workers.values()) {
            if (r == role) {
                count++;
            }
        }
        return count;
    }

    /** Borrow idle loaded workers (drones or runners) that pass the free test. Returns {diggersAdded, placersAdded}. */
    private static int[] borrowLoadedWorkers(
        ServerLevel level,
        HiveLocation location,
        CarveSite site,
        int neededDiggers,
        int neededPlacers
    ) {
        var added = new int[] { 0, 0 };
        for (var entry : location.loadedMembersByType().entrySet()) {
            // Both worker castes crew a site. Runners were always intended for this - they simply had no dig
            // animation until now, so nothing could show them doing it.
            if (!entry.getKey().is(AlienEntityTypeTags.DRONES) && !entry.getKey().is(AlienEntityTypeTags.RUNNERS)) {
                continue;
            }
            for (UUID id : new ArrayList<>(entry.getValue())) {
                if (added[0] >= neededDiggers && added[1] >= neededPlacers) {
                    return added;
                }
                if (site.workers.containsKey(id)) {
                    continue; // already on this site
                }
                if (!(level.getEntity(id) instanceof Xenomorph drone) || !(drone instanceof CarveWorker) || !drone.isAlive()) {
                    continue;
                }
                if (drone.getTarget() != null || hasRealTask(drone)) {
                    continue;
                }
                assign(site, drone.getUUID(), added, neededDiggers, neededPlacers, false);
            }
        }
        return added;
    }

    /**
     * Materialize the shortfall from reserves at the site's doorway - the party {@code trySpawn} idiom. Spawns just
     * inside the CONNECTED piece (already-built, open space), so workers walk in through the doorway like they belong.
     */
    private static void materializeFromReserves(
        ServerLevel level,
        HiveLocation location,
        CarveSite site,
        int neededDiggers,
        int neededPlacers
    ) {
        var reserves = location.localReserves();
        // Either worker caste can be materialized for a crew - runners build alongside drones.
        var workerTypes = new ArrayList<EntityType<?>>();
        for (var type : reserves.getAvailableEntityTypes()) {
            if (type.is(AlienEntityTypeTags.DRONES) || type.is(AlienEntityTypeTags.RUNNERS)) {
                workerTypes.add(type);
            }
        }
        if (workerTypes.isEmpty()) {
            return;
        }

        BlockPos spawnPos;
        var socket = site.connectedTo();
        if (socket != null) {
            var facing = socket.facing();
            var refX = socket.chunk().getMinBlockX() + 8 + facing.getStepX() * 8;
            var refZ = socket.chunk().getMinBlockZ() + 8 + facing.getStepZ() * 8;
            spawnPos = new BlockPos(refX - facing.getStepX() * 3, site.floorY() + 1, refZ - facing.getStepZ() * 3);
        } else {
            // Founding core: no doorway yet - the first drones hatch inside the chamber the queen has dug, beside
            // her (fill only starts once a quarter of the volume is open, so there is room to stand).
            spawnPos = new BlockPos(location.centerPos().getX(), site.floorY() + 1, location.centerPos().getZ());
        }

        var added = new int[] { 0, 0 };
        var progressed = true;
        while (progressed && (added[0] < neededDiggers || added[1] < neededPlacers)) {
            progressed = false;
            for (var type : workerTypes) {
                if (added[0] >= neededDiggers && added[1] >= neededPlacers) {
                    return;
                }
                if (!reserves.trySpawn(type)) {
                    continue;
                }
                var entity = type.create(level);
                if (entity == null) {
                    continue;
                }
                var jitterX = spawnPos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 2.0;
                var jitterZ = spawnPos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 2.0;
                entity.moveTo(jitterX, spawnPos.getY(), jitterZ, level.random.nextFloat() * 360.0F, 0.0F);
                if (entity instanceof Mob mob) {
                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.MOB_SUMMONED, null);
                    mob.setPersistenceRequired();
                }
                level.addFreshEntityWithPassengers(entity);
                com.alien.common.gameplay.hive.spawning.ReserveSpawnUtil.markSpawnedFromReserves(entity);
                assign(site, entity.getUUID(), added, neededDiggers, neededPlacers, true);
                progressed = true;
            }
        }
    }

    private static void assign(
        CarveSite site,
        UUID id,
        int[] added,
        int neededDiggers,
        int neededPlacers,
        boolean materialized
    ) {
        // Diggers first: excavation leads and the fill can't outrun it anyway (the patch shrink rule).
        if (added[0] < neededDiggers) {
            site.workers.put(id, Role.DIGGER);
            added[0]++;
        } else if (added[1] < neededPlacers) {
            site.workers.put(id, Role.PLACER);
            added[1]++;
        } else {
            return;
        }
        if (materialized) {
            site.materializedWorkers.add(id);
        }
    }

    /**
     * Completion (or never-wedge fallback): everyone still materialized folds back into reserves identity-intact - the
     * brood-bank absorb idiom, {@code BEEHIVE_ENTER} chirp and all. Borrowed workers just get their gait back and
     * return to whatever the hive AI wants of them.
     */
    static void disband(ServerLevel level, HiveLocation location, CarveSite site) {
        for (UUID id : new ArrayList<>(site.workers.keySet())) {
            if (!(level.getEntity(id) instanceof Xenomorph drone) || !(drone instanceof CarveWorker) || !drone.isAlive()) {
                continue;
            }
            if (site.materializedWorkers.contains(id)) {
                if (location.localReserves().addReturningIdentityMember(drone)) {
                    level.playSound(
                        null,
                        drone.getX(),
                        drone.getY(),
                        drone.getZ(),
                        SoundEvents.BEEHIVE_ENTER,
                        SoundSource.HOSTILE,
                        0.6F,
                        0.9F
                    );
                    drone.discard();
                    continue;
                }
                // Variant mismatch (shouldn't happen for the hive's own reserves) - leave it in the world as a member.
                Alien.LOGGER.warn(
                    "Hive at {}: could not fold carve worker {} back into reserves - leaving it as a member.",
                    location.centerPos(),
                    id
                );
            }
            if (((CarveWorker) drone).carveDigMode().get() != 0) {
                ((CarveWorker) drone).carveDigMode().set(0);
            }
        }
        site.workers.clear();
        site.materializedWorkers.clear();
    }
}
