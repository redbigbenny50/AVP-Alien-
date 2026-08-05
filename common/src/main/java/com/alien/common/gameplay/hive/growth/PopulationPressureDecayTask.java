package com.alien.common.gameplay.hive.growth;

import com.alien.common.gameplay.hive.economy.CastePopulation;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationBootstrapProtection;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public final class PopulationPressureDecayTask {

    private PopulationPressureDecayTask() {}

    /** Sustained combat required before territory attrition starts: 15 minutes. */
    private static final long SIEGE_THRESHOLD_TICKS = 15L * 60L * 20L;

    /**
     * Siege clock ceiling: 30 minutes. Since the clock drains at the accrual rate once fighting stops, the ceiling also
     * bounds the post-combat pruning window to at most ~15 minutes past the threshold.
     */
    private static final long SIEGE_CLOCK_CAP_TICKS = 30L * 60L * 20L;

    public static void scanAll(MinecraftServer server) {
        var config = HiveLocationRegistry.INSTANCE.config();

        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (com.alien.common.gameplay.hive.dimension.EndStyleHiveRules.isEndStyle(server, location)) {
                continue; // END-STYLE: no decay and no siege attrition - a fixed footprint is permanent while the hive
                          // lives
            }
            if (!location.isAlive() || location.isInhibited() || location.claimedChunks().size() <= 1) {
                continue;
            }
            if (HiveLocationBootstrapProtection.isProtected(location, config)) {
                continue;
            }

            var level = server.getLevel(location.dimension());
            if (level == null) {
                continue;
            }

            // Heal before shaving: any built chunk that lost its claim (old amputation bug, or a future release
            // path touching structure) gets it back on the same cadence decay runs. The structure IS the claim.
            HiveLocationClaims.reclaimStructureChunks(level, location, level.getGameTime());

            HiveLocationClaims.releaseDisconnectedClaims(level, location);

            // SIEGE GATE. [stated] "maybe the decay is too punishing perhaps have it occur during combat or post
            // combat ... it needs to be prolonged combat for a long duration over 15 minutes or so of active
            // combat/aggro with a player or enemy faction" - so the population shave below no longer runs as a
            // background tax. The siege clock accrues one scan interval whenever a qualifying hit (see the hurt hook
            // in the Alien entity base) landed since the previous scan, and drains at the same rate when none did -
            // so a lull mid-siege doesn't reset a raid's progress, and the clock staying above threshold for a while
            // AFTER the fight ends is the "post combat" pruning window. Brief surface aggro never accrues 15
            // minutes; a real raid does, and its attrition becomes visible territory loss.
            var now = level.getGameTime();
            var scanInterval = Math.max(1L, config.lineageScanIntervalTicks());
            var hitSinceLastScan = location.lastCombatDamageTick() > 0L
                && now - location.lastCombatDamageTick() <= scanInterval;
            var siegeClockBefore = location.siegeCombatTicks();
            var siegeClock = hitSinceLastScan
                ? Math.min(SIEGE_CLOCK_CAP_TICKS, siegeClockBefore + scanInterval)
                : Math.max(0L, siegeClockBefore - scanInterval);
            location.setSiegeCombatTicks(siegeClock);

            if (siegeClock < SIEGE_THRESHOLD_TICKS) {
                continue;
            }
            if (siegeClockBefore < SIEGE_THRESHOLD_TICKS) {
                com.alien.Alien.LOGGER.info(
                    "Hive: location {} has endured {} min of sustained combat - territory attrition is now active",
                    location.id().value(),
                    siegeClock / (20L * 60L)
                );
            }

            while (location.claimedChunks().size() > 1 && isBelowPopulationRatio(location, config)) {
                var chunk = pickOutermostReleasableChunk(location);
                if (chunk == null) {
                    break;
                }
                HiveLocationClaims.release(level, location, chunk);
            }
        }
    }

    private static boolean isBelowPopulationRatio(
        HiveLocation location,
        com.alien.common.gameplay.hive.config.HiveConfig config
    ) {
        var cap = location.claimedChunks().size() * config.populationPerChunk();
        if (cap <= 0) {
            return false;
        }

        var requiredPopulation = (int) Math.ceil(cap * config.minimumPopulationRatioForClaiming());
        return CastePopulation.totalTrackedPopulation(location) < requiredPopulation;
    }

    private static @Nullable ChunkPos pickOutermostReleasableChunk(HiveLocation location) {
        var centerChunk = new ChunkPos(location.centerPos());

        return location.claimedChunks()
            .stream()
            .filter(chunk -> !chunk.equals(centerChunk))
            // Never decay a chunk holding BUILT STRUCTURE: population pressure shaves abstract territory
            // only. A population dip (raid losses, economy stalls) must not de-claim the hive's own halls -
            // the chamber systems (jelly, eggs, vents) all key on claimed structure chunks.
            .filter(chunk -> !location.structurePieceByChunk().containsKey(chunk))
            .filter(chunk -> HiveLocationClaims.wouldRemainConnectedAfterRelease(location, chunk))
            .max(
                Comparator.<ChunkPos>comparingInt(chunk -> chebyshev(chunk, centerChunk))
                    .thenComparingInt(chunk -> manhattan(chunk, centerChunk))
                    .thenComparingLong(chunk -> location.chunkClaimTicks().getOrDefault(chunk, 0L))
                    .thenComparingInt(chunk -> chunk.x)
                    .thenComparingInt(chunk -> chunk.z)
            )
            .orElse(null);
    }

    private static int chebyshev(ChunkPos a, ChunkPos b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z));
    }

    private static int manhattan(ChunkPos a, ChunkPos b) {
        return Math.abs(a.x - b.x) + Math.abs(a.z - b.z);
    }
}
