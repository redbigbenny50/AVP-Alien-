package com.alien.common.gameplay.hive.empress;

import com.alien.Alien;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.id.LineageIds;
import net.minecraft.server.MinecraftServer;

/**
 * Periodic scan that fires {@link EmpressEmergenceRitual#start} when conditions are met:
 * <ul>
 * <li>Lineage has 4+ locations (the design's "needs an empress" trigger — raised from an earlier 2+ per updated design;
 * a lineage caps at 8 member hives total).</li>
 * <li>Lineage has no empress.</li>
 * <li>No emergence is already in flight for this lineage.</li>
 * <li>At least one queen is loaded somewhere in the lineage's locations.</li>
 * </ul>
 * <p>
 * When all hold, {@link EmpressCandidatePicker#pickSeat} ELECTS a seat and {@code empressId} is assigned immediately,
 * from persisted data, with nothing loaded. The lineage is an empress lineage from that moment - it gets the convoys,
 * the biomass bonuses, the war projection and the sibling non-aggression - even though her body does not exist yet. She
 * is assumed to be giving orders already; the molt is just when the player finally gets to see her.
 * {@link EmpressEmergenceRitual#tryMaterialize} runs that molt whenever the elected seat next loads, and per-tick
 * advancement happens in {@link EmpressEmergenceRitual#tick}.
 * <p>
 * This scan also RELEASES a stale election: if the elected seat dies or loses its queen before she ever molts,
 * {@code empressId} is surrendered so a new seat can be elected next pass.
 * <p>
 * Per {@code HIVE_REDESIGN_07_LEADERSHIP.md} § 4.1 + § 6.
 */
public final class EmpressEmergenceTask {

    private EmpressEmergenceTask() {}

    public static void scanAndStart(MinecraftServer server) {
        var currentTick = server.overworld().getGameTime();

        for (var factionId : new java.util.ArrayList<>(Alien.MOD.factions().getAllIds())) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage) || !lineage.isAlive()) {
                continue;
            }
            // An election already stands. Either it is still valid (wait for the seat to load and her to molt) or
            // the seat has since died / been made queenless, in which case the crown is surrendered here.
            var seatId = lineage.pendingEmpressSeatId();
            if (seatId != null) {
                var seat = lineage.locationsById().get(seatId);
                if (seat != null && seat.isAlive() && seat.founderId() != null) {
                    continue;
                }
                Alien.LOGGER.info(
                    "Hive: empress election released for lineage {} - elected seat {} died or lost its queen before "
                        + "she could molt; re-electing",
                    factionId,
                    seatId
                );
                // If she was mid-molt when the seat fell over, give the queen her body back before dropping the
                // state - the invulnerable/noAi flags persist and the timer does not.
                EmpressEmergenceRitual.cancelFor(server, factionId);
                lineage.setPendingEmpressSeatId(null);
                lineage.setEmpressId(null);
                // A successor must not inherit her predecessor's exposure.
                lineage.setEmpressRevealed(false);
            }

            if (currentTick < lineage.empressCooldownUntilTick()) {
                // An empress of this lineage died recently. The hives keep running - they just cannot crown another
                // until the window closes, which is the whole point of killing her.
                continue;
            }
            if (lineage.activeLocationCount() < 4) {
                continue;
            }
            if (lineage.empressId() != null) {
                // Already crowned and materialized (a standing election was handled above).
                continue;
            }
            if (EmpressEmergenceRitual.isEmergingFor(factionId)) {
                continue;
            }

            var seat = EmpressCandidatePicker.pickSeat(lineage);
            if (seat == null) {
                // No hive in the lineage has a seated queen. Nothing to crown; retry next scan.
                continue;
            }

            // The identity is PRE-ALLOCATED rather than taken from a spawned entity, because the crown is real
            // before the body is: every live consumer of empressId only null-checks it, and the molt later forces
            // the spawned empress onto this exact UUID so the abstract and physical records never disagree.
            lineage.setEmpressId(java.util.UUID.randomUUID());
            lineage.setPendingEmpressSeatId(seat.id());
            lineage.setPendingEmpressEmergence(false);

            Alien.LOGGER.info(
                "Hive: empress ELECTED for lineage {} - seat {} ({} claims, {} members); she rules now, the molt "
                    + "happens when that hive next loads",
                factionId,
                seat.id(),
                seat.claimedChunks().size(),
                com.alien.common.gameplay.hive.economy.CastePopulation.totalTrackedPopulation(seat)
            );
        }
    }
}
