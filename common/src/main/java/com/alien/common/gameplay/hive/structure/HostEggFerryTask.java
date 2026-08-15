package com.alien.common.gameplay.hive.structure;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.ovomorph.Ovomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

/**
 * Host egg ferry (host-capture arc, half b - the dispatch half).
 * <p>
 * {@link HostEggDelivery} only changes an in-flight egg's DESTINATION; something has to actually put an egg in flight.
 * Egg hauling is demand-driven: a carrier only responds to a LOOSE (un-rooted) ovomorph broadcasting a pickup request.
 * A hive whose nursery eggs are all rooted therefore has nothing in flight, so a webbed host sat waiting forever with
 * drones idling beside it.
 * <p>
 * This task closes that gap the way the design doc specifies - "workers ferry eggs storage -&gt; host chamber": when a
 * host is awaiting an egg and none is already inbound, UN-ROOT one stored nursery egg. It immediately starts
 * broadcasting for pickup, a carrier collects it, and {@code DropOffEggAction} (which ranks host delivery above nursery
 * storage) routes it to the host. No new pickup or pathing code - just the missing trigger.
 * <p>
 * Egg source: a rooted nursery egg when one exists; otherwise a rooted egg from the QUEEN'S OWN CLUTCH. An early hive
 * has not built an egg chamber yet, so nursery-only sourcing would leave a webbed host waiting forever.
 * <p>
 * [Flag for teammate review: reads the host lifecycle; releases stored eggs.]
 */
public final class HostEggFerryTask {

    private HostEggFerryTask() {}

    /** How far around the queen to look for one of her rooted clutch eggs. */
    private static final double QUEEN_CLUTCH_RADIUS = 16.0;

    /**
     * After releasing an egg for a drop cell, refuse to release another for THAT cell until this many ticks pass.
     * <p>
     * This is the drain backstop. {@code findAwaitingHostEggDrop} already skips a host with a loose/carried egg
     * inbound, but a carrier that gives up and RE-ROOTS the egg makes it neither loose nor carried, so the gate
     * re-fires and a fresh egg is released every cadence - the nursery-drain the tester saw. This cooldown is immune to
     * what happens to the egg after release: once a cell is served, it is quiet for a while regardless. Sized well
     * above a plausible cross-hive ferry time so a genuinely lost egg is eventually retried, but a working delivery is
     * never double-served.
     */
    private static final long RELEASE_COOLDOWN_TICKS = 30L * 20L; // 30 seconds

    /** Per-drop-cell time of last release, so one cell cannot be served again within the cooldown. */
    private static final Map<BlockPos, Long> LAST_RELEASE = new HashMap<>();

    /** Called on the growth cadence. Releases at most one stored egg per call. */
    public static void run(ServerLevel level, HiveLocation location) {
        var awaiting = HostEggDelivery.findAwaitingHostEggDrop(level, location);
        if (awaiting.isEmpty()) {
            return;
        }
        var dropCell = awaiting.get();

        // Drain backstop: if we served this exact cell within the cooldown, stay hands-off even if the gate thinks it
        // is still awaiting - the egg is in transit (or a carrier re-rooted it mid-haul). See RELEASE_COOLDOWN_TICKS.
        long now = level.getGameTime();
        var lastRelease = LAST_RELEASE.get(dropCell);
        if (lastRelease != null && now - lastRelease < RELEASE_COOLDOWN_TICKS) {
            return;
        }

        // findAwaitingHostEggDrop already excludes any host that has a loose/carried egg inbound. The cooldown above
        // covers the remaining case (an egg re-rooted mid-haul). If we are here the host genuinely needs an egg.
        var released = releaseStoredEgg(level, location);
        if (released == null) {
            // No nursery egg (an early hive may not have built an egg chamber yet) - take one from the queen's
            // clutch instead, so the host arc works before the first nursery exists.
            released = releaseQueenClutchEgg(level, location);
        }
        if (released == null) {
            return; // no egg anywhere to ferry - the hive has to lay/restock first
        }

        // The stamp IS the delivery: it names the exact drop cell, travels with the egg through pickups, drops,
        // hauler swaps and reloads, and is the only thing the inbound gate counts. Whoever picks this egg up
        // delivers it HERE - no fresh clutch egg or passing nursery haul can hijack or hide the destination.
        released.setHostDropTarget(dropCell);

        // Prune stale entries so this map cannot grow unbounded over a long-running server: anything older than the
        // cooldown is no longer doing any work and can be forgotten.
        LAST_RELEASE.entrySet().removeIf(e -> now - e.getValue() >= RELEASE_COOLDOWN_TICKS);
        LAST_RELEASE.put(dropCell, now);
        Alien.LOGGER.info(
            "Hive at {}: released a stored egg to ferry to a waiting host at {}.",
            location.centerPos(),
            dropCell
        );
    }

    /**
     * Un-roots one rooted egg sitting in an egg chamber so it broadcasts for pickup. Returns the released egg, or null
     * when the nurseries hold none.
     */
    private static Ovomorph releaseStoredEgg(ServerLevel level, HiveLocation location) {
        for (var entry : location.structurePieceByChunk().entrySet()) {
            if (!entry.getValue().contains("chamber_egg")) {
                continue;
            }
            var chamber = entry.getKey();
            if (!level.isLoaded(chamber.getWorldPosition())) {
                continue;
            }
            for (var bed : HiveChamberSlots.eggBedSlots(level, location, chamber)) {
                var box = new AABB(bed).inflate(1.0);
                for (var egg : level.getEntitiesOfClass(Ovomorph.class, box)) {
                    if (egg.isAlive() && egg.isRooted.get() && !egg.isPassenger()) {
                        egg.isRooted.set(false);
                        return egg;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Un-roots one rooted egg from the queen's clutch so it broadcasts for pickup. Used when the hive has no nursery
     * egg to spare - typically an early hive that has not built an egg chamber yet.
     */
    private static Ovomorph releaseQueenClutchEgg(ServerLevel level, HiveLocation location) {
        var queen = findQueen(level, location);
        if (queen == null) {
            return null;
        }
        var box = queen.getBoundingBox().inflate(QUEEN_CLUTCH_RADIUS);
        for (var egg : level.getEntitiesOfClass(Ovomorph.class, box)) {
            if (egg.isAlive() && egg.isRooted.get() && !egg.isPassenger()) {
                egg.isRooted.set(false);
                return egg;
            }
        }
        return null;
    }

    private static Queen findQueen(ServerLevel level, HiveLocation location) {
        for (var entry : location.loadedMembersByType().entrySet()) {
            if (!entry.getKey().is(AlienEntityTypeTags.QUEENS)) {
                continue;
            }
            for (var uuid : entry.getValue()) {
                if (level.getEntity(uuid) instanceof Queen queen && queen.isAlive()) {
                    return queen;
                }
            }
        }
        return null;
    }
}
