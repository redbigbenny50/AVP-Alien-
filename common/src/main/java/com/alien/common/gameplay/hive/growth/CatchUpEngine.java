package com.alien.common.gameplay.hive.growth;

import com.alien.common.gameplay.hive.economy.CastePopulation;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import net.minecraft.server.level.ServerLevel;

/**
 * The deferred-simulation core. Catches a single {@link HiveLocation} up to {@code currentTick} by:
 * <ol>
 * <li>Adding biomass for the elapsed period using {@link BiomassIncome#unloadedPerSecond} (the "abstract" formula that
 * doesn't read entity state — see {@code HIVE_REDESIGN_09_GROWTH.md} § 5).</li>
 * <li>Looping claim attempts while the location can afford the next chunk's cost. Loaded calls use explorer-driven
 * claims; unloaded calls use a slower passive random claim mode.</li>
 * <li>Bumping {@code lastGrowthTick} to {@code currentTick}.</li>
 * </ol>
 * <p>
 * Idempotent — calling twice in the same tick is a no-op (elapsed = 0 → no income, claim loop runs but cost gates
 * everything). The {@link com.alien.common.gameplay.hive.tick.HiveLocationSlowTickTask} samples unloaded locations
 * through this path, while {@link com.alien.common.gameplay.hive.tick.HiveLocationLoadedTickTask} also calls it for
 * loaded locations on its faster cadence.
 */
public final class CatchUpEngine {

    private CatchUpEngine() {}

    public static void catchUpTo(ServerLevel level, HiveLocation location, LineageFactionData lineage, long currentTick) {
        catchUpTo(level, location, lineage, currentTick, false);
    }

    public static void catchUpUnloadedTo(ServerLevel level, HiveLocation location, LineageFactionData lineage, long currentTick) {
        catchUpTo(level, location, lineage, currentTick, true);
    }

    private static void catchUpTo(
        ServerLevel level,
        HiveLocation location,
        LineageFactionData lineage,
        long currentTick,
        boolean passiveClaims
    ) {
        var config = HiveLocationRegistry.INSTANCE.config();
        HiveLocationClaims.releaseDisconnectedClaims(level, location);

        var elapsed = currentTick - location.lastGrowthTick();

        if (elapsed > 0) {
            var perSec = BiomassIncome.unloadedPerSecond(location, lineage, config);
            var income = (int) Math.round(perSec * elapsed / 20.0);

            if (income > 0) {
                addBiomassClamped(location, income, config);
            }

            location.setLastGrowthTick(currentTick);
        }

        if (!passiveClaims) {
            location.setLastPassiveClaimTick(currentTick);
        }

        // Skip claim attempts on angry locations per HIVE_REDESIGN_04_BOSS_BAR § 4.
        // (Boss bar may not exist yet on a newly-minted location — treat that as "not angry".)
        var bossBar = location.bossBar();
        if (bossBar != null && bossBar.isAngry()) {
            return;
        }

        var claimLimit = passiveClaims ? passiveClaimLimit(location, currentTick, config) : loadedClaimLimit(config);
        if (claimLimit <= 0) {
            return;
        }

        runClaimLoop(level, location, lineage, currentTick, config, passiveClaims, claimLimit);
    }

    private static void addBiomassClamped(HiveLocation location, int income, com.alien.common.gameplay.hive.config.HiveConfig config) {
        var cap = BiomassIncome.biomassCap(location, config);
        var newBiomass = Math.min(cap, location.biomass() + income);
        location.setBiomass(newBiomass);
    }

    private static void runClaimLoop(
        ServerLevel level,
        HiveLocation location,
        LineageFactionData lineage,
        long currentTick,
        com.alien.common.gameplay.hive.config.HiveConfig config,
        boolean passiveClaims,
        int claimLimit
    ) {
        var claimsThisRun = 0;
        var lineageTotal = HiveLocationClaims.totalChunksFor(lineage);

        while (
            claimsThisRun < claimLimit
                && location.claimedChunks().size() < config.maxChunksPerLocation()
                && lineageTotal < config.maxChunksPerLineage()
        ) {
            // Founding-priority gate: a queen-founded hive does not spend biomass on expansion until its queen is
            // reproductive (has created her ovipositor). This stops the hive bankrupting itself claiming chunks before
            // it can pay the ovipositor cost. Queenless hives (no founder) are unaffected and claim normally.
            if (location.founderId() != null && !location.reproductiveEstablished()) {
                return;
            }

            // Starvation priority (design §6, step 4): a frozen half-built piece is the hive's top financial
            // priority. While the active carve site is starved, expansion claims stand aside so incoming biomass
            // finishes the build first. Factual flag - set only when a resin payment actually bounced.
            if (location.isConstructionStarved()) {
                return;
            }

            if (!hasEnoughPopulationToClaim(location, config)) {
                return;
            }

            var cost = BiomassIncome.claimCost(location, config);
            if (location.biomass() < cost) {
                return;
            }

            var nextChunk = passiveClaims
                ? ChunkPicker.pickPassiveChunk(level, location, config)
                : ChunkPicker.pickNextChunk(level, location, config);
            if (nextChunk == null) {
                return;
            }

            location.setBiomass(location.biomass() - cost);
            HiveLocationClaims.claim(level, location, nextChunk, currentTick);
            claimsThisRun++;
            lineageTotal++;
        }
    }

    private static int passiveClaimLimit(
        HiveLocation location,
        long currentTick,
        com.alien.common.gameplay.hive.config.HiveConfig config
    ) {
        var perWindow = Math.max(0, config.maxPassiveClaimsPerUnloadedScan());
        if (perWindow <= 0) {
            location.setLastPassiveClaimTick(currentTick);
            return 0;
        }

        var interval = Math.max(1L, config.lineageScanIntervalTicks());
        var lastPassiveTick = location.lastPassiveClaimTick();
        if (lastPassiveTick <= 0L || currentTick <= lastPassiveTick) {
            location.setLastPassiveClaimTick(currentTick);
            return 0;
        }

        var elapsed = currentTick - lastPassiveTick;
        var elapsedWindows = elapsed / interval;
        if (elapsedWindows <= 0L) {
            return 0;
        }

        var maxWindows = Math.max(1, config.passiveClaimCatchUpWindowCap());
        var windowsToApply = Math.min(elapsedWindows, maxWindows);
        if (elapsedWindows > maxWindows) {
            location.setLastPassiveClaimTick(currentTick);
        } else {
            location.setLastPassiveClaimTick(lastPassiveTick + windowsToApply * interval);
        }

        var limit = windowsToApply * (long) perWindow;
        return limit > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) limit;
    }

    private static int loadedClaimLimit(com.alien.common.gameplay.hive.config.HiveConfig config) {
        return Math.max(0, config.maxClaimsPerScan());
    }

    private static boolean hasEnoughPopulationToClaim(
        HiveLocation location,
        com.alien.common.gameplay.hive.config.HiveConfig config
    ) {
        var cap = location.claimedChunks().size() * config.populationPerChunk();
        if (cap <= 0) {
            return false;
        }

        var requiredPopulation = (int) Math.ceil(cap * config.minimumPopulationRatioForClaiming());
        return CastePopulation.totalTrackedPopulation(location) >= requiredPopulation;
    }
}
