package com.alien.common.gameplay.hive.faction;

import com.alien.Alien;
import com.alien.common.gameplay.hive.growth.ContestResolutionTask;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.lifecycle.FirewallStabilityTask;
import com.alien.common.gameplay.hive.lifecycle.QueenlessMaturationTask;
import com.blib.api.common.faction.v1.FactionMember;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Sanity-check pass for lineage faction state. Runs every {@code lineageScanIntervalTicks} (default 5 minutes)
 * alongside the growth scan. Three responsibilities:
 * <ul>
 * <li><b>Variant matching</b>: for each lineage, scan its loaded members across all owned locations. Members whose
 * entity-type variant doesn't match the lineage's variant are evicted from BLib membership. (Phase 9.)</li>
 * <li><b>Lifecycle</b>: drive location contests and lineage death (Phase 11).</li>
 * </ul>
 * <p>
 * Per {@code HIVE_REDESIGN_01_FACTIONS.md} § 2 + § 7 and {@code HIVE_REDESIGN_02_FACTION_LIFECYCLES.md} § 3–§ 5.
 */
public final class LineageInvariantTask {

    private LineageInvariantTask() {}

    public static void scanAll() {
        scanVariantInvariants();
    }

    /**
     * Slow-cadence scan: variant invariants, maturation, contests. Called from
     * {@link com.alien.common.gameplay.hive.location.HiveLocationRegistry#tick} every {@code lineageScanIntervalTicks}.
     * Per-tick death checks (location dormancy, lineage death) run independently every tick from
     * {@code HiveLocationRegistry.tick} directly — they are NOT routed through this method.
     */
    public static void scanAllWithLifecycle(MinecraftServer server) {
        // 1. Variant invariants — evict variant-mismatched members.
        scanVariantInvariants();

        // 1b. Rescue campaigns — promote/dispatch/resolve recovery for captured queens BEFORE maturation, so a
        // resolved-or-exhausted campaign no longer blocks the firewall crowning this same scan.
        com.alien.common.gameplay.hive.party.RescueCampaignTask.scanAll(server);

        // 2. Queenless lineage maturation — lets queenless lineages advance their leader through the queen-track
        // growth stages over time.
        QueenlessMaturationTask.scanAll(server);

        // 2b. Firewall fund tracking — for locations whose queen-replacement fund is currently spent, accrues
        // stability progress toward refill (or pauses under sustained pressure). Runs after maturation so a queen
        // crowned this same scan starts its fund-spent tracking fresh next cycle rather than double-counting.
        FirewallStabilityTask.scanAll(server);

        // 3. Contested chunk resolution.
        ContestResolutionTask.scanAll(server);
    }

    private static void scanVariantInvariants() {
        for (var factionId : new ArrayList<>(Alien.MOD.factions().getAllIds())) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage) || !lineage.isAlive()) {
                continue;
            }
            scanLineage(faction.membership(), lineage);
        }
    }

    private static void scanLineage(
            com.blib.api.common.faction.v1.FactionMembership membership,
            LineageFactionData lineage
    ) {
        var lineageVariant = lineage.variant();
        var mismatchedUuids = new HashSet<UUID>();
        var removedReserveEntries = 0;

        for (var location : new ArrayList<>(lineage.locationsById().values())) {
            for (var entry : location.loadedMembersByType().entrySet()) {
                if (FactionVariantPolicy.variantMatches(entry.getKey(), lineageVariant)) {
                    continue;
                }
                mismatchedUuids.addAll(entry.getValue());
            }
            removedReserveEntries += location.localReserves().removeVariantMismatches(lineageVariant);
        }

        if (removedReserveEntries > 0) {
            lineage.markDirty();
            Alien.LOGGER.info(
                    "Hive: LineageInvariantTask removed {} variant-mismatched reserve entries from lineage variant={}",
                    removedReserveEntries,
                    lineageVariant
            );
        }

        if (mismatchedUuids.isEmpty()) {
            return;
        }

        evictAll(membership, mismatchedUuids, lineage);
    }

    private static void evictAll(
            com.blib.api.common.faction.v1.FactionMembership membership,
            Set<UUID> uuids,
            LineageFactionData lineage
    ) {
        // Snapshot to avoid concurrent-modification when removeMember fires onMemberRemoved which mutates
        // location loadedMembersByType.
        var snapshot = new ArrayList<>(uuids);
        for (var uuid : snapshot) {
            membership.removeMember(FactionMember.entity(uuid));
        }

        Alien.LOGGER.info(
                "Hive: LineageInvariantTask evicted {} variant-mismatched member(s) from lineage variant={}",
                snapshot.size(),
                lineage.variant()
        );
    }
}