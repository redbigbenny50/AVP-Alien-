package com.alien.common.gameplay.hive.empress;

import com.alien.Alien;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Which lineages belong to an empress's CORRIDOR NETWORK.
 * <p>
 * [stated] design: hives chain through connected territory from her seat. Chosen over a plain radius (nothing to cut)
 * and over bloodline descent (distance-independent, also nothing to cut) precisely because a corridor gives the player
 * a second way in - take a hive in the middle and everything beyond it is severed from her without anyone ever finding
 * her. Losing her costs those lineages the biomass bonuses, the convoys, the raised caps and the stat buff at once.
 * <p>
 * Membership IS `lineage.empressId()`. Adopting a connected lineage onto her id is both the network mechanism and the
 * switch for {@code AlienTerritoryWarSystem.shareEmpressAuthority} - two lineages sharing an empress stop being
 * hostile, which is dead code today for want of anything that could ever give them the same id.
 * <p>
 * A RECONCILE, like {@link EmpressInfluenceSync}: recompute reachability every sweep and set or clear accordingly.
 * Severing a corridor drops the far lineages on the next pass with no event to hook and nothing to forget. It is also
 * the only honest way to model it, since a claim can be lost to decay or a nuke with no notification anywhere.
 */
public final class EmpressNetworkSync {

    private EmpressNetworkSync() {}

    /**
     * Two locations are corridor-linked when their claimed territory comes within this many chunks. Compared on
     * claimed-chunk BOUNDING BOXES rather than the full sets - a box is derived in one pass over the claims and the
     * test is four integer comparisons, where an exact set-adjacency check would be quadratic in claim count on hives
     * holding up to {@code maxChunksPerLocation} (256) each.
     */
    private static final int CORRIDOR_GAP_CHUNKS = 8;

    /** Reconcile every empress network in the world. */
    public static void syncAll(MinecraftServer server) {
        var lineages = collectLineages();
        if (lineages.isEmpty()) {
            return;
        }

        // Her seat lineage is the one holding the location she is founder of. After the molt the ritual repoints that
        // location's founderId at her, so this identifies her ORIGIN rather than any lineage she has since adopted -
        // which matters, because the walk has to start from her seat and not from wherever the network happens to
        // reach today.
        var seats = new HashMap<UUID, HiveLocation>();
        for (var lineage : lineages) {
            var empressId = lineage.empressId();
            if (empressId == null) {
                continue;
            }
            for (var location : lineage.locationsById().values()) {
                if (empressId.equals(location.founderId()) && location.isAlive() && !location.isExiled()) {
                    seats.putIfAbsent(empressId, location);
                }
            }
        }

        for (var seat : seats.entrySet()) {
            reconcileNetwork(lineages, seat.getKey(), seat.getValue());
        }

        pruneRescueBudgets(lineages);
    }

    /**
     * Drop rescue-budget entries for empresses nobody answers to any more.
     * <p>
     * Done HERE, on a reconcile, rather than at each of the five places that null an empressId - the task's release,
     * the ritual's release, exile, her death, and a corridor severing above. Expecting all five to also clean up is the
     * kind of bookkeeping that survives right up until someone adds a sixth. An id orphaned by any route, including one
     * not yet invented, disappears on the next sweep.
     */
    private static void pruneRescueBudgets(List<LineageFactionData> lineages) {
        var inUse = new HashSet<UUID>();
        for (var lineage : lineages) {
            var empressId = lineage.empressId();
            if (empressId != null) {
                inUse.add(empressId);
            }
        }

        for (var factionId : Alien.MOD.factions().getAllIds()) {
            var faction = Alien.MOD.factions().get(factionId);
            if (
                faction != null
                    && faction.data() instanceof com.alien.common.gameplay.hive.faction.VariantFactionData variantData
            ) {
                variantData.pruneEmpressRescues(inUse);
            }
        }
    }

    private static void reconcileNetwork(List<LineageFactionData> lineages, UUID empressId, HiveLocation seat) {
        var dimension = seat.dimension();
        var reachable = walkCorridor(lineages, seat, dimension);

        for (var lineage : lineages) {
            if (!dimension.equals(lineage.dimension())) {
                continue;
            }

            var joined = false;
            for (var location : lineage.locationsById().values()) {
                if (reachable.contains(location)) {
                    joined = true;
                    break;
                }
            }

            var current = lineage.empressId();

            if (joined) {
                // A lineage that already answers to a DIFFERENT empress is left alone. Two empresses do not fight over
                // territory here - whoever reached it first keeps it, and the loser simply has a smaller network.
                if (current == null) {
                    lineage.setEmpressId(empressId);
                    Alien.LOGGER.info("Hive: lineage {} joined the empress network of {}", lineage.factionId(), empressId);
                }
            } else if (empressId.equals(current)) {
                // The corridor was cut, or the claims that carried it are gone.
                lineage.setEmpressId(null);
                lineage.setPendingEmpressSeatId(null);
                Alien.LOGGER.info("Hive: lineage {} severed from the empress network of {}", lineage.factionId(), empressId);
            }
        }
    }

    /** Breadth-first over corridor links, starting at her seat. */
    private static Set<HiveLocation> walkCorridor(
        List<LineageFactionData> lineages,
        HiveLocation seat,
        ResourceKey<Level> dimension
    ) {
        // Hoisted: resolving her variant inside the loop made this quadratic in lineages, each pass doing a
        // containsValue scan of a location map.
        var variant = seatVariant(lineages, seat);

        var candidates = new ArrayList<HiveLocation>();
        for (var lineage : lineages) {
            if (!dimension.equals(lineage.dimension()) || lineage.variant() != variant) {
                continue;
            }
            for (var location : lineage.locationsById().values()) {
                // A remnant is written off and cannot carry a corridor through itself.
                if (location.isAlive() && !location.isExiled() && !location.claimedChunks().isEmpty()) {
                    candidates.add(location);
                }
            }
        }

        var boxes = new HashMap<HiveLocation, int[]>();
        for (var candidate : candidates) {
            boxes.put(candidate, claimBox(candidate));
        }

        var reached = new HashSet<HiveLocation>();
        var queue = new ArrayDeque<HiveLocation>();
        reached.add(seat);
        queue.add(seat);

        while (!queue.isEmpty()) {
            var from = queue.poll();
            var fromBox = boxes.get(from);
            if (fromBox == null) {
                continue;
            }

            for (var candidate : candidates) {
                if (reached.contains(candidate)) {
                    continue;
                }
                if (linked(fromBox, boxes.get(candidate))) {
                    reached.add(candidate);
                    queue.add(candidate);
                }
            }
        }

        return reached;
    }

    private static com.alien.common.model.alien.variant.AlienVariant seatVariant(
        List<LineageFactionData> lineages,
        HiveLocation seat
    ) {
        for (var lineage : lineages) {
            if (lineage.locationsById().containsValue(seat)) {
                return lineage.variant();
            }
        }
        return null;
    }

    /** {minX, minZ, maxX, maxZ} over claimed chunks. */
    private static int[] claimBox(HiveLocation location) {
        var minX = Integer.MAX_VALUE;
        var minZ = Integer.MAX_VALUE;
        var maxX = Integer.MIN_VALUE;
        var maxZ = Integer.MIN_VALUE;

        for (var chunk : location.claimedChunks()) {
            minX = Math.min(minX, chunk.x);
            minZ = Math.min(minZ, chunk.z);
            maxX = Math.max(maxX, chunk.x);
            maxZ = Math.max(maxZ, chunk.z);
        }

        return new int[] { minX, minZ, maxX, maxZ };
    }

    /** Boxes within CORRIDOR_GAP_CHUNKS on both axes. */
    private static boolean linked(int[] a, int[] b) {
        if (a == null || b == null) {
            return false;
        }
        var gapX = Math.max(0, Math.max(a[0] - b[2], b[0] - a[2]));
        var gapZ = Math.max(0, Math.max(a[1] - b[3], b[1] - a[3]));

        return gapX <= CORRIDOR_GAP_CHUNKS && gapZ <= CORRIDOR_GAP_CHUNKS;
    }

    private static List<LineageFactionData> collectLineages() {
        var out = new ArrayList<LineageFactionData>();
        for (var factionId : Alien.MOD.factions().getAllIds()) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction != null && faction.data() instanceof LineageFactionData lineage && lineage.isAlive()) {
                out.add(lineage);
            }
        }
        return out;
    }
}
