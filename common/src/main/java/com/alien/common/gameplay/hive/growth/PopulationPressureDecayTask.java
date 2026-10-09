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
        // ⭐ NO CHUNK DECAY IN BUILD-FREE MODE - [stated] "no decay in this mode keep it simple."
        //
        // ⚠ The decay floor is the FOUNDING CORE - the 3x3 a hive was born on - and that has no meaning when
        // nothing is ever carved. Territory here is a configured box the hive fills by presence and then stops,
        // so there is no growth curve for decay to push back against: the config number IS the territory.
        if (com.alien.common.gameplay.hive.config.BuildFreeMode.isEnabled()) {
            return;
        }
        var config = HiveLocationRegistry.INSTANCE.config();

        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (com.alien.common.gameplay.hive.dimension.EndStyleHiveRules.isEndStyle(server, location)) {
                continue; // END-STYLE: no decay and no siege attrition - a fixed footprint is permanent while the hive
                          // lives
            }
            if (!location.isAlive() || location.isInhibited()) {
                continue;
            }
            // ⭐ AN EMPRESS-INFLUENCED HIVE DOES NOT DECAY - FIX 3, [stated] Sep 23 "a" (option a). EmpressCaps raises
            // her
            // hives' member ceiling but populationPerChunk was never scaled with it, so a hive she had just enlarged
            // read as under-populated against its own footprint and the shave undid her. Her influence is the hive's
            // protection; siege attrition stays a matter for hives with no empress behind them.
            if (location.isEmpressInfluenced()) {
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

            // ⭐⭐ THE FLOOR IS THE FOUNDING CORE, NOT ONE CHUNK. Attrition takes back EXPANSION; a hive keeps the
            // ground it was born on until it is dead.
            var floor = foundingCoreChunkCount(config);
            while (location.claimedChunks().size() > floor && isBelowPopulationRatio(location, config)) {
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

        // ⚠ This measures FULLNESS, not decline - a hive under its ceiling reads true whether it was ground down
        // to that number or simply never reached it. That is tolerable now only because the founding-core floor
        // above means the answer can never cost a hive its homeland; the ratio itself was also lowered 0.8 -> 0.5.
        var requiredPopulation = (int) Math.ceil(cap * config.minimumPopulationRatioForClaiming());
        return CastePopulation.totalTrackedPopulation(location) < requiredPopulation;
    }

    /**
     * ⭐⭐ THE GROUND A HIVE IS BORN WITH, and the floor attrition may never shave past.
     * <p>
     * {@code HiveLocationFoundingService.claimInitialCore} claims a square of radius
     * {@code initialHiveLocationClaimRadiusChunks} (1 by default), so 3x3 = 9 chunks. Derived rather than hardcoded so
     * the two cannot drift if that radius is ever retuned.
     * </p>
     * <p>
     * ⚠⚠ THIS IS THE FIX FOR "CHUNK CULLING KILLED OFF HIVES EARLY", reported three times. The floor used to be ONE
     * chunk, and because the required population scales with the chunk count the shave loop converged low: 30 adults
     * settled at 4 chunks, 15 adults at 2, 10 adults at 2 - and 2 is exactly {@code migrationTerritoryFloorChunks}, so
     * decay delivered small hives straight onto the evacuation trigger, which then removed the location and orphaned
     * its founder queen. Decay can no longer reach that floor at all.
     * </p>
     */
    private static int foundingCoreChunkCount(com.alien.common.gameplay.hive.config.HiveConfig config) {
        var radius = Math.max(0, config.initialHiveLocationClaimRadiusChunks());
        var side = 2 * radius + 1;
        return side * side;
    }

    private static @Nullable ChunkPos pickOutermostReleasableChunk(HiveLocation location) {
        var centerChunk = new ChunkPos(location.centerPos());
        var foundingClaimTick = foundingClaimTick(location);

        return location.claimedChunks()
            .stream()
            .filter(chunk -> !chunk.equals(centerChunk))
            // ⭐ EXPANSION ONLY. Every chunk of the founding core is claimed in one call and so shares a single
            // claim tick; anything stamped with it is homeland and is never up for release. This is the precise
            // version of the count floor above - it still holds when the core came out smaller than 3x3 because a
            // neighbouring hive already owned some of those chunks.
            .filter(chunk -> location.chunkClaimTicks().getOrDefault(chunk, 0L) > foundingClaimTick)
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

    /**
     * The claim tick shared by the founding core. Anything stamped later than this was expanded into.
     * <p>
     * ⚠ FAILS CLOSED BY DESIGN. A location saved before {@code chunkClaimTicks} was populated reports 0 for every
     * chunk, so the minimum is 0, so nothing is strictly greater and NOTHING is shaved. A legacy hive keeping all its
     * territory is the safe direction to be wrong in.
     * </p>
     */
    private static long foundingClaimTick(HiveLocation location) {
        var earliest = Long.MAX_VALUE;
        for (var chunk : location.claimedChunks()) {
            earliest = Math.min(earliest, location.chunkClaimTicks().getOrDefault(chunk, 0L));
        }
        return earliest == Long.MAX_VALUE ? 0L : earliest;
    }

    private static int chebyshev(ChunkPos a, ChunkPos b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z));
    }

    private static int manhattan(ChunkPos a, ChunkPos b) {
        return Math.abs(a.x - b.x) + Math.abs(a.z - b.z);
    }
}
