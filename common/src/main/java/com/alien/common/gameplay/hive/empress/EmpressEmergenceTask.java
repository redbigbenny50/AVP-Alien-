package com.alien.common.gameplay.hive.empress;

import com.alien.Alien;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.id.LineageIds;
import net.minecraft.server.MinecraftServer;

/**
 * Periodic scan that fires {@link EmpressEmergenceRitual#start} when conditions are met:
 * <ul>
 * <li>Lineage has 4+ locations (the design's "needs an empress" trigger — raised from an earlier 2+ per updated
 * design; a lineage caps at 8 member hives total).</li>
 * <li>Lineage has no empress.</li>
 * <li>No emergence is already in flight for this lineage.</li>
 * <li>At least one queen is loaded somewhere in the lineage's locations.</li>
 * </ul>
 * <p>
 * When all hold, {@link EmpressCandidatePicker} picks the best queen and the ritual begins. Per-tick advancement
 * happens in {@link EmpressEmergenceRitual#tick}.
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
            if (lineage.locationsById().size() < 4) {
                continue;
            }
            if (lineage.empressId() != null) {
                continue;
            }
            if (EmpressEmergenceRitual.isEmergingFor(factionId)) {
                continue;
            }

            var candidate = EmpressCandidatePicker.pick(server, lineage);
            if (candidate == null) {
                // No queen loaded right now — the pendingEmpressEmergence flag stays set; we'll retry next scan
                // when a queen becomes available. Per HIVE_REDESIGN_08_LINEAGE_SPREAD.md § 5.
                continue;
            }

            EmpressEmergenceRitual.start(candidate, factionId, currentTick);
        }
    }
}