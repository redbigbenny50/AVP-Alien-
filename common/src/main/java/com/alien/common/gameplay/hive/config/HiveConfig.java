package com.alien.common.gameplay.hive.config;

/**
 * Single-source-of-truth tunables for the new hive system. A constructor config object — not abstract method overrides
 * — per the project's preference for explicit, top-down configuration.
 * <p>
 * Phase 1 ships sensible defaults from {@code HIVE_REDESIGN_13_CONFIGURATION.md}. Later phases consume each value as
 * they implement the corresponding mechanic. Where the design doc lists a value as "not yet specified — recommend X",
 * the recommended value is used here.
 * <p>
 * To tune, swap {@link #defaults()} for a custom instance at the call site (typically in
 * {@link com.alien.common.gameplay.hive.location.HiveLocationRegistry} or in the per-tick task constructors).
 */
public record HiveConfig(
    // ---------- § 1 Faction lifecycle ----------
    long protoHiveStageInterval,

    // ---------- § 2 Locations ----------
    long settlementTicks,
    long locationMaxNoContactTicks,
    long locationBootstrapGraceTicks,
    int bossBarDisplayRadiusBlocks,
    long angryGraceTicks,
    long contestTickWindow,
    int minimumHiveLocationDistanceChunks,
    int initialHiveLocationClaimRadiusChunks,

    // ---------- § 3 Reserves ----------
    long shedGraceTicks,
    long minLineageAgeForShedding,

    // ---------- § 4 Convoys (common) ----------
    double convoySpeedBlocksPerSecond,
    int arrivalRadiusBlocks,
    int manifestDistanceBlocks,
    int convoyInterceptRadiusBlocks,
    double reinforcementSpeedMultiplier,
    double migrationSpeedMultiplier,
    double raidSpeedMultiplier,

    // ---------- § 5 Reinforcement convoys ----------
    long reinforcementSourceCooldownTicks,
    int reinforcementMinSize,
    int reinforcementMaxSize,

    // ---------- § 6 Migration convoys ----------
    long resettleGraceTicks,
    long migrationBiomassDecayTicks,
    int migrationTerritoryFloorChunks,
    long migrationRallyTicks,
    int migrationBiomassPayloadCap,

    // ---------- § 7 Raid convoys ----------
    int raidThresholdKills,
    long raidAggroWindowTicks,
    int raidMinLocationSizeChunks,
    long perSourceRaidCooldownTicks,
    long raidExpiryTicks,
    int baseRaidSize,
    double raidSizePerClaimedChunk,
    int raidEngageRadiusBlocks,
    int raidFrenzyExtraMemberCap,
    long raidLossDeathWindowTicks,
    int raidLossDeathThreshold,
    long raidLossDownGraceTicks,
    long raidLossAftermathTicks,

    // ---------- § 8 Leadership ----------
    long empressMoltDurationTicks,
    long localLeaderPickCadenceTicks,
    int empressCandidateMinMembers,
    long firewallCooldownTicks,
    long firewallStabilityScanIntervalTicks,
    int firewallJellyFloor,
    int firewallCrowningJellyCost,

    // ---------- § 9 Lineage spread ----------
    int maxLineageSpreadChunks,
    long lineageSpreadCooldownTicks,
    int maxLocationsPerLineage,
    int maxLocationsUnderEmpress,
    int minimumPopulationForHiveSpread,
    int abstractSpreadMinFounderGroupSize,
    int abstractSpreadMaxFounderGroupSize,
    long foragerJoinTicks,

    // ---------- § 10 Growth ----------
    long claimActivityWindowTicks,
    long perLocationClaimCooldownTicks,
    long lineageScanIntervalTicks,
    int maxChunksPerLocation,
    int maxTerritoryRadiusChunks,
    int maxChunksPerLineage,
    int maxLineagesPerDimensionPerVariant,
    int maxClaimsPerScan,
    long resinFullDensityTicks,
    int maxPassiveClaimsPerUnloadedScan,

    // ---------- § 11 Biomass ----------
    int baseChunkCost,
    int ovipositorCreationBiomassCost,
    int resinSpreadBiomassCost,
    double growthFactor,
    double baseUnloadedBiomassPerChunkPerSec,
    double unloadedEmpressBonusPerSec,
    double loadedBiomassPerLoadedXenomorphPerSec,
    int loadedBiomassPerNonAlienKill,
    int loadedBiomassPerResinBlockPlaced,
    double loadedBiomassPerOvomorphPerSec,
    double loadedBiomassEmpressPresentBonusPerSec,
    double loadedBiomassIdleBonusPerSec,
    int biomassAccumulationCapMultiplier,

    // ---------- § 12 Persistence and performance ----------
    int biomassDirtyThreshold,
    long lastGrowthTickDirtyThreshold,
    int slowPathLocationUpdatesPerTick,
    int passiveClaimCatchUpWindowCap,

    // ---------- § 13 Population, spawning + jelly economy ----------
    int populationPerChunk,
    double minimumPopulationRatioForClaiming,
    int hiveSpawnerMinimumLoadedXenomorphs,
    boolean reserveSpawnsCanIgnoreResin,
    long hiveSpawnerIntervalTicks,
    int hiveSpawnerMaxSpawnAttemptsPerLocation,
    int hiveSpawnerMaxSpawnsPerLocation,
    int combatRespiteKillThreshold,
    long combatRespiteMinTicks,
    long combatRespiteMaxTicks,
    int maxOvomorphsPerHiveLocation,
    long royalJellyTicksPerProduction,
    long scourgeJellyTicksPerQueenProduction,
    long scourgeJellyTicksPerHarbingerProduction,

    // ---------- § 14 Queen lifecycle ----------
    boolean queenFrontEndPhasesEnabled,

    // ---------- § 15 Parties ----------
    int surfacePartyBaseSize,
    double surfacePartySizePerClaimedChunk,
    double surfacePartyVentDropChance,
    int surfacePartyMaxVentsPerClaim,
    int surfacePartySurfaceBandBlocks,
    int biomassHuntingPartyBaseSize,
    double biomassHuntingPartySizePerClaimedChunk,
    int biomassHuntingPartyBonusSpitterCount,
    long biomassHuntingPartyDurationTicks,
    int attackPartyBaseSize,
    double attackPartySizePerClaimedChunk,
    long attackPartyCooldownTicks,
    long attackPartyWave1DelayTicks,
    long attackIntrusionDwellTicks,
    long attackPartyDurationTicks
) {

    private static final int TICKS_PER_SECOND = 20;

    private static final long TICKS_PER_MINUTE = 60L * TICKS_PER_SECOND;

    private static final long TICKS_PER_HOUR = 60L * TICKS_PER_MINUTE;

    public static HiveConfig defaults() {
        return new HiveConfig(
            // § 1 Faction lifecycle
            5L * TICKS_PER_MINUTE, // protoHiveStageInterval: 5 min between drone→warrior→praetorian→queen advances

            // § 2 Locations
            10L * TICKS_PER_SECOND, // settlementTicks: 10s
            7L * 24L * TICKS_PER_HOUR, // locationMaxNoContactTicks: 7 game-days of loaded-no-contact time
            30L * TICKS_PER_MINUTE, // locationBootstrapGraceTicks: protect newborn locations for 30 min
            96, // bossBarDisplayRadiusBlocks
            60L * TICKS_PER_SECOND, // angryGraceTicks: 60s
            60L * TICKS_PER_SECOND, // contestTickWindow: 60s
            16, // minimumHiveLocationDistanceChunks
            1, // initialHiveLocationClaimRadiusChunks: 3x3

            // § 3 Reserves
            5L * TICKS_PER_MINUTE, // shedGraceTicks: 5 min
            30L * TICKS_PER_MINUTE, // minLineageAgeForShedding: 30 min

            // § 4 Convoys (common)
            10.0, // convoySpeedBlocksPerSecond
            16, // arrivalRadiusBlocks
            80, // manifestDistanceBlocks
            32, // convoyInterceptRadiusBlocks
            1.0, // reinforcementSpeedMultiplier
            0.7, // migrationSpeedMultiplier
            1.5, // raidSpeedMultiplier

            // § 5 Reinforcement convoys
            5L * TICKS_PER_MINUTE, // reinforcementSourceCooldownTicks
            4, // reinforcementMinSize
            10, // reinforcementMaxSize

            // § 6 Migration convoys
            5L * TICKS_PER_MINUTE, // resettleGraceTicks
            30L * TICKS_PER_MINUTE, // migrationBiomassDecayTicks
            2, // migrationTerritoryFloorChunks
            30L * TICKS_PER_SECOND, // migrationRallyTicks
            500, // migrationBiomassPayloadCap

            // § 7 Raid convoys
            5, // raidThresholdKills
            10L * TICKS_PER_MINUTE, // raidAggroWindowTicks
            8, // raidMinLocationSizeChunks
            20L * TICKS_PER_MINUTE, // perSourceRaidCooldownTicks
            30L * TICKS_PER_MINUTE, // raidExpiryTicks
            4, // baseRaidSize
            0.25, // raidSizePerClaimedChunk
            32, // raidEngageRadiusBlocks
            6, // raidFrenzyExtraMemberCap: max extra members that can frenzy-join an in-progress raid
            30L * TICKS_PER_SECOND, // raidLossDeathWindowTicks: window for counting repeated target deaths
            3, // raidLossDeathThreshold: target deaths within the window that confirm a raid loss
            60L * TICKS_PER_SECOND, // raidLossDownGraceTicks: how long the target stays down before loss is confirmed
            5L * TICKS_PER_MINUTE, // raidLossAftermathTicks: aftermath duration after a raid loss is confirmed

            // § 8 Leadership
            30L * TICKS_PER_SECOND, // empressMoltDurationTicks
            5L * TICKS_PER_SECOND, // localLeaderPickCadenceTicks (100 ticks)
            250, // empressCandidateMinMembers: a queen's hive must have at least this many members to be an
            // empress candidate at all (eligibility floor, not a ranking factor)
            7L * 24L * TICKS_PER_HOUR, // firewallCooldownTicks: 7 game-days of accrued stability to refill the fund
            5L * TICKS_PER_MINUTE, // firewallStabilityScanIntervalTicks: cadence for both the stability check and the
            // biomass income-rate sample
            50, // firewallJellyFloor: jelly reserve floor for the "has jelly reserves" stability condition
            100, // firewallCrowningJellyCost: jelly cost paid when a queen is crowned via QueenlessMaturationTask

            // § 9 Lineage spread
            32, // maxLineageSpreadChunks
            30L * TICKS_PER_MINUTE, // lineageSpreadCooldownTicks
            8, // maxLocationsPerLineage: a lineage can have at most 8 member hives
            5, // maxLocationsUnderEmpress: an empress can control up to 5 hives, including her own origin hive.
            100, // minimumPopulationForHiveSpread
            4, // abstractSpreadMinFounderGroupSize
            10, // abstractSpreadMaxFounderGroupSize
            30L * TICKS_PER_SECOND, // foragerJoinTicks

            // § 10 Growth
            30L * TICKS_PER_SECOND, // claimActivityWindowTicks
            30L * TICKS_PER_SECOND, // perLocationClaimCooldownTicks
            5L * TICKS_PER_MINUTE, // lineageScanIntervalTicks
            256, // maxChunksPerLocation
            9, // maxTerritoryRadiusChunks: 3x3 core (radius 1) + 8 chunks outward = radius 9 => 19x19 max footprint.
               // Empress-influenced hives are intended to expand this later (parked).
            10000, // maxChunksPerLineage
            16, // maxLineagesPerDimensionPerVariant
            64, // maxClaimsPerScan (enough to complete a 7x7 ring boundary in one scan, so partial fills
            // align with ring boundaries instead of breaking mid-ring)
            24L * TICKS_PER_HOUR, // resinFullDensityTicks
            1, // maxPassiveClaimsPerUnloadedScan

            // § 11 Biomass
            25, // baseChunkCost
            100, // ovipositorCreationBiomassCost
            5, // resinSpreadBiomassCost
            0.05, // growthFactor
            0.05, // baseUnloadedBiomassPerChunkPerSec
            1.0, // unloadedEmpressBonusPerSec
            1.0, // loadedBiomassPerLoadedXenomorphPerSec
            25, // loadedBiomassPerNonAlienKill
            5, // loadedBiomassPerResinBlockPlaced
            0.5, // loadedBiomassPerOvomorphPerSec
            5.0, // loadedBiomassEmpressPresentBonusPerSec
            0.1, // loadedBiomassIdleBonusPerSec
            100, // biomassAccumulationCapMultiplier

            // § 12 Persistence and performance
            10, // biomassDirtyThreshold (±10)
            30L * TICKS_PER_MINUTE, // lastGrowthTickDirtyThreshold (30 min)
            4, // slowPathLocationUpdatesPerTick
            4, // passiveClaimCatchUpWindowCap

            // § 13 Population, spawning + jelly economy
            8, // populationPerChunk
            0.8, // minimumPopulationRatioForClaiming
            20, // hiveSpawnerMinimumLoadedXenomorphs
            true, // reserveSpawnsCanIgnoreResin: reserves can materialize without a resin floor
            TICKS_PER_SECOND, // hiveSpawnerIntervalTicks
            32, // hiveSpawnerMaxSpawnAttemptsPerLocation
            4, // hiveSpawnerMaxSpawnsPerLocation
            40, // combatRespiteKillThreshold: 2x hiveSpawnerMinimumLoadedXenomorphs default, intentionally not coupled
            10L * TICKS_PER_SECOND, // combatRespiteMinTicks
            TICKS_PER_MINUTE, // combatRespiteMaxTicks
            30, // maxOvomorphsPerHiveLocation
            TICKS_PER_MINUTE, // royalJellyTicksPerProduction (1 game-min per queen)
            100L * TICKS_PER_MINUTE, // scourgeJellyTicksPerQueenProduction (100 game-min per queen)
            TICKS_PER_MINUTE, // scourgeJellyTicksPerHarbingerProduction (1 game-min per harbinger)

            // § 14 Queen lifecycle
            true, // queenFrontEndPhasesEnabled: run developing->location->hibernation before founding

            // § 15 Parties
            2, // surfacePartyBaseSize
            0.15, // surfacePartySizePerClaimedChunk
            0.10, // surfacePartyVentDropChance: 10% roll on dawn despawn
            3, // surfacePartyMaxVentsPerClaim: cap counted against near-surface vents only, not the full column
            6, // surfacePartySurfaceBandBlocks: vertical margin around the terrain heightmap counted as "surface"
               // (also reused by biomass hunting party's vent-spawn-point lookup)
            2, // biomassHuntingPartyBaseSize
            0.15, // biomassHuntingPartySizePerClaimedChunk
            1, // biomassHuntingPartyBonusSpitterCount: extra spitters beyond the base budget, only if reserves allow
            10L * TICKS_PER_MINUTE, // biomassHuntingPartyDurationTicks: active duration before returning home via vent
            2, // attackPartyBaseSize
            0.15, // attackPartySizePerClaimedChunk
            3L * 24L * TICKS_PER_HOUR, // attackPartyCooldownTicks: gap between wave 1 and wave 2 (3 game-days)
            24000L, // attackPartyWave1DelayTicks: wave 1 fires ~1 MC day after the intrusion is logged
            30L * TICKS_PER_SECOND, // attackIntrusionDwellTicks: in-claim-while-hostile dwell before a campaign arms
            10L * TICKS_PER_MINUTE // attackPartyDurationTicks: active duration hunting the target before giving up
        );
    }
}
