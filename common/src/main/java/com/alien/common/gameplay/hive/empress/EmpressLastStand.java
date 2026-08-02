package com.alien.common.gameplay.hive.empress;

import com.alien.Alien;
import com.alien.common.gameplay.hive.economy.CastePopulation;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Her answer to being found: a levy on the whole network to fill her own hive to its ceiling.
 * <p>
 * [stated] "the player earned finding her now she has to boost her defense and try to weather the storm", and "she
 * fills her hives caps all of them by taking some from all her hives". She does not run. She calls everything in.
 * <p>
 * This is the same trade as the rescue transfer one layer up, and deliberately so: she cannot CREATE strength, only
 * MOVE it. Every hive in her corridor gives up part of its garrison and is left thinner for it, and the player who
 * pushed her into revealing herself has, in the same stroke, hollowed out everything else she holds. Cut her network
 * first and there is less to levy - which is what makes the corridor worth cutting rather than merely worth knowing
 * about.
 * <p>
 * SOME FROM ALL, not everything from the nearest: each donor is taken from in proportion to what it can spare, and
 * {@code DONOR_FLOOR} is left behind so the levy strips garrisons rather than killing hives outright. A hive that
 * starves itself to defend her would just be a slower way of losing the network.
 */
public final class EmpressLastStand {

    private EmpressLastStand() {}

    /** Members every donor keeps, so a levy thins the network rather than collapsing it. */
    private static final int DONOR_FLOOR = 40;

    /**
     * Fill her seat's CASTE BANKS to their caps by drawing the same castes from every other hive she holds.
     * <p>
     * Per-caste, not merely a headcount. The per-caste ceiling is the {@code max_entity_count_in_location} condition on
     * that caste's hive unit purchase, run through {@link EmpressCaps} so her seat gets the empress-scaled figure - the
     * same number {@code HiveBalanceTask} enforces when the hive buys one normally. Filling only the aggregate member
     * cap would hand her 375 drones; filling the banks hands her a full complement of every caste the network can
     * supply, which is the difference between a crowd and a garrison.
     *
     * @return how many members were moved, for logging by the caller.
     */
    public static int fortify(net.minecraft.server.level.ServerLevel seatLevel, HiveLocation seat, UUID empressId) {
        if (com.alien.common.gameplay.hive.dimension.EndStyleHiveRules.forbidsApexEconomy(seatLevel)) {
            // END-STYLE HARD GATE: the levy moves banked members between hives, and End banks are the PLAYER'S
            // hand-fed property. The trigger chain (reveal <- rescue) is already dead here, but this stays so no
            // future change reopens it by accident.
            return 0;
        }
        var donors = donorsFor(empressId, seat);
        if (donors.isEmpty()) {
            return 0;
        }

        var moved = 0;

        for (var purchase : com.alien.common.registry.HiveUnitPurchaseRegistry.all()) {
            var type = purchase.outputEntity();
            var cap = casteCap(purchase, seat);
            if (cap <= 0) {
                continue;
            }

            var shortfall = cap - CastePopulation.countEntity(seat, type);
            if (shortfall <= 0) {
                continue;
            }

            moved += levy(donors, seat, type, shortfall);
        }

        Alien.LOGGER.info(
            "Hive: empress {} LEVIES her network - {} members drawn from {} hives to fill the caste banks at {}",
            empressId,
            moved,
            donors.size(),
            seat.id()
        );

        return moved;
    }

    /**
     * Move up to {@code shortfall} of one caste into the seat, ROUND-ROBIN across donors.
     * <p>
     * "Some from all her hives" is the mechanic, and round-robin also stops whichever hive happened to be iterated
     * first from being uniquely stripped. Each donor keeps {@code DONOR_FLOOR} members so the levy thins the network
     * rather than collapsing it - a hive that starved itself to defend her would just be a slower way of losing the
     * corridor.
     */
    private static int levy(List<HiveLocation> donors, HiveLocation seat, EntityType<?> type, int shortfall) {
        var moved = 0;
        var progress = true;

        while (moved < shortfall && progress) {
            progress = false;

            for (var donor : donors) {
                if (moved >= shortfall) {
                    break;
                }
                if (
                    donor.localReserves().getCount(type) <= 0
                        || CastePopulation.totalTrackedPopulation(donor) <= DONOR_FLOOR
                ) {
                    continue;
                }

                donor.localReserves().underlying().add(type, -1);
                seat.localReserves().underlying().add(type, 1);
                moved++;
                progress = true;
            }
        }

        return moved;
    }

    /** That caste's per-location ceiling at her seat, empress-scaled. 0 when the caste has no such cap. */
    private static int casteCap(com.alien.common.gameplay.hive.economy.HiveUnitPurchase purchase, HiveLocation seat) {
        for (var condition : purchase.conditions()) {
            if (condition instanceof com.alien.common.gameplay.hive.economy.HiveUnitPurchaseCondition.MaxEntityCountInLocation max) {
                return EmpressCaps.scale(seat, max.value());
            }
        }
        return 0;
    }

    /** The caste this donor has most of, so a levy takes its surplus rather than its last specialist. */
    private static EntityType<?> richestReserveType(HiveLocation donor) {
        EntityType<?> best = null;
        var bestCount = 0;

        for (var type : donor.localReserves().getAvailableEntityTypes()) {
            var count = donor.localReserves().getCount(type);
            if (count > bestCount) {
                best = type;
                bestCount = count;
            }
        }

        return best;
    }

    /** Every hive she holds except the seat itself, and never a written-off remnant. */
    private static List<HiveLocation> donorsFor(UUID empressId, HiveLocation seat) {
        var donors = new ArrayList<HiveLocation>();

        for (var factionId : Alien.MOD.factions().getAllIds()) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
                continue;
            }
            if (!empressId.equals(lineage.empressId())) {
                continue;
            }

            for (var candidate : lineage.locationsById().values()) {
                if (candidate != seat && candidate.isAlive() && !candidate.isExiled()) {
                    donors.add(candidate);
                }
            }
        }

        return donors;
    }
}
