package com.alien.common.gameplay.hive.structure;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.ovomorph.Ovomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

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

    /** How far around the host's egg-drop cell an already-loose egg counts as "one is on the way". */
    private static final double INBOUND_EGG_RADIUS = 48.0;

    /** How far around the queen to look for one of her rooted clutch eggs. */
    private static final double QUEEN_CLUTCH_RADIUS = 16.0;

    /** Called on the growth cadence. Releases at most one stored egg per call. */
    public static void run(ServerLevel level, HiveLocation location) {
        var awaiting = HostEggDelivery.findAwaitingHostEggDrop(level, location);
        if (awaiting.isEmpty()) {
            return;
        }
        var dropCell = awaiting.get();

        // One in flight at a time: a loose egg (or one already being carried) near the host means help is coming.
        var inboundBox = new AABB(dropCell).inflate(INBOUND_EGG_RADIUS);
        boolean inbound = !level.getEntitiesOfClass(
            Ovomorph.class,
            inboundBox,
            egg -> egg.isAlive() && (!egg.isRooted.get() || egg.isPassenger())
        ).isEmpty();
        if (inbound) {
            return;
        }

        var released = releaseStoredEgg(level, location);
        if (released == null) {
            // No nursery egg (an early hive may not have built an egg chamber yet) - take one from the queen's
            // clutch instead, so the host arc works before the first nursery exists.
            released = releaseQueenClutchEgg(level, location);
        }
        if (released == null) {
            return; // no egg anywhere to ferry - the hive has to lay/restock first
        }

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
