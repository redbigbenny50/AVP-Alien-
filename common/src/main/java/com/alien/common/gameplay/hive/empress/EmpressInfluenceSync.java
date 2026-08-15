package com.alien.common.gameplay.hive.empress;

import com.alien.Alien;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.structure.HiveRouter;

import net.minecraft.server.MinecraftServer;

/**
 * Keeps {@link HiveRouter}'s empress-influence set honest.
 * <p>
 * The router's flag is the switch behind the whole raised-ceiling half of the empress: the 23x23 footprint, the second
 * raid chamber (and therefore the second harbinger), construction resuming on a finished hive, the member cap, every
 * party ceiling, and the nuke scan extent. All of that was written and none of it could ever fire, because
 * {@code setEmpressInfluence} had no callers anywhere in the tree.
 * <p>
 * It is wired as a RECONCILE rather than as events, for two reasons. The router keeps influence in a memory-only
 * WeakHashMap and its own contract says the caller must re-assert on load - a reconcile does that for free, where a
 * one-shot call at emergence would silently evaporate on the next restart. And one derived rule covers every way the
 * answer can change: she is elected, she dies, she is exiled, a location joins or leaves the lineage. There is nothing
 * to forget to hook up.
 * <p>
 * Influence follows {@code empressId}, so it lands at ELECTION, not at the molt - consistent with the rest of the
 * abstract-first design, where she is already giving orders before she has a body. Exiled remnants are excluded: the
 * empire has written that hive off, and it should not keep building itself a palace.
 */
public final class EmpressInfluenceSync {

    private EmpressInfluenceSync() {}

    /** Reconcile every location of every lineage, loaded or not. */
    public static void syncAll(MinecraftServer server) {
        for (var factionId : Alien.MOD.factions().getAllIds()) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
                continue;
            }
            for (var location : lineage.locationsById().values()) {
                sync(location, lineage);
            }
        }
    }

    /**
     * Reconcile one location. Cheap and idempotent.
     * <p>
     * Writes BOTH the router's set (which owns the transition side effect - resetting DONE/STITCHING so a finished hive
     * resumes building at the larger footprint) and the read-hot mirror on the location itself. Everything that merely
     * ASKS the question reads the mirror; only this method touches the synchronized set.
     */
    public static void sync(HiveLocation location, LineageFactionData lineage) {
        var shouldBeInfluenced = lineage.empressId() != null && !location.isExiled();
        // Reconcile against BOTH stores, not just the persisted mirror. The router's set is memory-only and starts
        // EMPTY after a restart while the mirror comes back true - the old mirror-only comparison then skipped the
        // write, and an influenced hive silently ran at BASE extent (no expansion, no second raid chamber) until
        // something flipped the mirror. The router's contract line - "that system must re-assert influence on
        // world load" - is exactly this call doing its job.
        if (
            location.isEmpressInfluenced() != shouldBeInfluenced
                || HiveRouter.isEmpressInfluenced(location) != shouldBeInfluenced
        ) {
            HiveRouter.setEmpressInfluence(location, shouldBeInfluenced);
            location.setEmpressInfluenced(shouldBeInfluenced);
        }
    }
}
