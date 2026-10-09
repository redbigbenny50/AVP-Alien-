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
    int maxDaughterHivesPerLocation,
    int queenPromotionJellyCost,
    long queenPromotionMoltTicks,
    long localLeaderPickCadenceTicks,
    int empressCandidateMinMembers,
    long empressCrowningCooldownTicks,
    int empressCapPercent,
    int empressRescuesPerHive,
    int empressRescueBudget,
    long firewallCooldownTicks,
    long firewallStabilityScanIntervalTicks,
    int firewallJellyFloor,
    int firewallCrowningJellyCost,
    int minRoyalJellyCap,

    // ---------- § 9 Lineage spread ----------
    int maxLineageSpreadChunks,

    /**
     * How far a brand-new lineage must be founded from any existing hive, in chunks. 0 disables the rule.
     * <p>
     * ⚠⚠ THE MOD BOUNDS EACH LINEAGE BUT NOT HOW MANY EXIST. Every wild queen that reaches unclaimed ground mints a
     * fresh lineage with its own eight-hive allowance, so on a long-running world hive networks accumulate without
     * limit even though no single one misbehaves.
     * </p>
     * <p>
     * ⭐ SPACING RATHER THAN A HARD COUNT, on purpose: a cap makes queens silently stop founding and looks like a bug,
     * whereas spacing just sends her further afield - which is also how territorial animals actually behave.
     * </p>
     * <p>
     * ⚠ DEFAULT 0 = DISABLED, so nothing changes for anyone who does not opt in. This is a server-owner dial, not a
     * balance change.
     * </p>
     */
    int minLineageSpacingChunks,
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
    int maxClaimsPerScan,
    long resinFullDensityTicks,
    int maxPassiveClaimsPerUnloadedScan,

    // ---------- § 11 Biomass ----------
    int baseChunkCost,
    int ovipositorCreationBiomassCost,
    int resinSpreadBiomassCost,
    double growthFactor,
    double baseUnloadedBiomassPerChunkPerSec,

    /**
     * A flat biomass income every unloaded hive earns, on top of the per-chunk rate.
     * <p>
     * [stated] "the intent is to get new hives a boost and closer faster to being able to catch hosts and such."
     * </p>
     * <p>
     * ⚠ WHY A FLOOR RATHER THAN A BIGGER PER-CHUNK RATE. Per-chunk income compounds: raising it enough to give a
     * 9-chunk hive 8/sec would give a 100-chunk hive 90/sec - more unwatched than a watched hive with an empress, so
     * growth would be fastest precisely when nobody is looking. A floor lifts the young hive, which is the one that
     * needs it, and leaves the curve above it alone.
     * </p>
     */
    double unloadedBiomassFlatPerSec,
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
    /**
     * ONE THRESHOLD, BOTH DIRECTIONS: a location may claim a new chunk only while its adult population is at least
     * {@code claimedChunks x populationPerChunk x this}, and {@code PopulationPressureDecayTask} shaves territory while
     * it is below it.
     * <p>
     * ⚠⚠ LOWERED 0.8 -> 0.5 (Aug 17), [stated] "we need to lower it so its less harsh on new hives". At 0.8 a hive
     * needed 58 ADULTS just to hold the 9-chunk core it is founded with - a figure no young hive is anywhere near - so
     * every new hive read as permanently under-populated: it could never claim a tenth chunk, and the 15-minute siege
     * clock was the only thing standing between it and being shaved. 0.5 puts the bar at 36.
     * </p>
     * <p>
     * No hysteresis gap between the two uses is needed: growth tests {@code >=} and decay tests {@code <}, so they are
     * mutually exclusive at the boundary, and the founding-core floor in the decay task means the two can never fight
     * over the same chunk anyway.
     * </p>
     */
    double minimumPopulationRatioForClaiming,
    int hiveSpawnerMinimumLoadedXenomorphs,
    boolean reserveSpawnsCanIgnoreResin,
    long hiveSpawnerIntervalTicks,
    int hiveSpawnerMaxSpawnAttemptsPerLocation,
    int hiveSpawnerMaxSpawnsPerLocation,
    int combatRespiteKillThreshold,
    long combatRespiteMinTicks,
    long combatRespiteMaxTicks,
    long royalJellyTicksPerProduction,
    long scourgeJellyTicksPerQueenProduction,
    long scourgeJellyTicksPerHarbingerProduction,

    /**
     * Hives stop repairing damage to their own structure, so player-made changes inside a hive survive.
     * <p>
     * [stated] "raz wants to be able to place blocks inside the hive and break resin etc ... an option to turn off the
     * hive repair for the air blocks and the structure blocks. that way if people want to build things inside of hives
     * without worrying about the aliens removing them".
     * </p>
     * <p>
     * WARNING: this switches off BOTH halves of upkeep, and that is the point. Normally a hive re-stamps its own blocks
     * back over damage AND clears anything found in an authored air cell - player-placed blocks included, so that
     * nobody can permanently seal a passage. With this on it does neither, so a corridor CAN be walled off and the hive
     * will not reopen it.
     * </p>
     * <p>
     * WARNING: only upkeep stops. Hives still CARVE new rooms, spread resin and claim ground - this is not a build
     * freeze. Anything a hive builds after this is enabled is equally unmaintained.
     * </p>
     */
    boolean noMaintenance,

    // ---------- § 14 Queen lifecycle ----------
    boolean queenFrontEndPhasesEnabled,

    /**
     * A queen founds WHERE SHE IS STANDING instead of locating an anchor and digging to it - in ORDINARY play.
     * <p>
     * [stated] "someone was asking if we can have a config setting to let queens naturally found where they are
     * standing."
     * </p>
     * <p>
     * ⚠⚠ NOT THE SAME FLAG AS {@code buildFreeQueenFoundsWherePlaced}, AND DELIBERATELY SO. That one reads
     * {@code isEnabled() && ...}, so it does nothing at all unless build-free mode is on - which is why an open-world
     * server could not have this. It also FORCES DAUGHTER SLOTS TO ZERO, because a build-free daughter cannot dig and
     * would strand herself. Neither is true here: an ordinary daughter founding where she stands is fine, so this flag
     * must never feed {@code BuildFreeMode.daughterSlots}.
     * </p>
     * <p>
     * ⚠ SURFACE HIVES ARE THE POINT OF THIS SETTING, so it also waives the depth rule in SpreadZoneCheck - a server
     * that turns this on is asking for queens that settle above ground. Territory spacing still applies; this cannot
     * overlap hives.
     * </p>
     */
    boolean queenFoundsWhereStanding,

    // ---------- § 15 Parties ----------
    int surfacePartyBaseSize,
    double surfacePartySizePerClaimedChunk,
    int surfacePartyMaxSize,
    double surfacePartyVentDropChance,
    int surfacePartyMaxVentsPerClaim,

    /**
     * Minimum gap, in chunks, between two chunks that hold surface vents. 0 disables the rule.
     * <p>
     * ⭐ AT THE DEFAULT OF 1 THIS PRODUCES A CHECKERBOARD: vented chunks may touch at the CORNERS but never edge to
     * edge. Measured with MANHATTAN distance rather than Chebyshev precisely for that reason - a diagonal neighbour is
     * distance 2 and allowed, an orthogonal neighbour is distance 1 and refused.
     * </p>
     * <p>
     * ⚠ THE PER-CHUNK CAP IS A SEPARATE RULE. surfacePartyMaxVentsPerClaim still limits how many vents may share ONE
     * chunk; this limits which chunks may hold any at all. Together they bound surface vent density in both axes.
     * </p>
     */
    int surfacePartyVentChunkGap,
    int surfacePartySurfaceBandBlocks,
    int biomassHuntingPartyBaseSize,
    double biomassHuntingPartySizePerClaimedChunk,
    int biomassHuntingPartyMaxSize,
    int hostHuntPartyBaseSize,
    double hostHuntPartySizePerClaimedChunk,
    int hostHuntPartyMaxSize,
    int hostHuntPartyDurationTicks,
    int biomassHuntingPartyBonusSpitterCount,
    long biomassHuntingPartyDurationTicks,
    int attackPartyBaseSize,
    double attackPartySizePerClaimedChunk,
    int attackPartyMaxSize,
    long attackPartyCooldownTicks,
    long attackPartyWave1DelayTicks,
    long attackIntrusionDwellTicks,
    long attackPartyDurationTicks,

    // ---------- § 16 Build-free mode ----------
    /**
     * ⭐⭐⭐ THE MASTER SWITCH. Every {@code buildFree*} field below is INERT while this is false.
     * <p>
     * [stated] "build mode is a custom mode for adventure maps and peoples personal play styles" — a hive that claims,
     * spreads resin, lays eggs and fights, but never CARVES or STAMPS a structure. The audience is a mapmaker who has
     * already built the space (a colony, a derelict, a bunker) and wants it infested, not rebuilt.
     * </p>
     * <p>
     * ⚠ It changes GATES, not systems. Rooms are almost never gated on — exactly one purchase condition reads the room
     * registry, and it already falls back gracefully — so switching this on mostly means the structure planner never
     * runs, and a handful of things that used to key off a chamber key off the slab instead.
     * </p>
     */
    boolean buildFreeModeEnabled,

    /**
     * Territory half-width in chunks. Default 9 ⇒ 19×19, which is exactly today's {@code maxTerritoryRadiusChunks}, so
     * an ordinary hive's footprint is unchanged; only the way it is FILLED differs.
     */
    int buildFreeTerritoryRadiusChunks,

    /** Territory half-width for an empress-influenced hive. Default 11 ⇒ 23×23. */
    int buildFreeEmpressTerritoryRadiusChunks,

    /**
     * ⭐⭐ HALF the slab band, so the hive extends this far ABOVE AND BELOW the queen — default 24 ⇒ a 48-block band
     * centred on her.
     * <p>
     * ⚠⚠ A NORMAL HIVE'S SLAB IS UP-ONLY: {@code hiveFloorY()} returns her own Y and the band runs to +16. Put a queen
     * on the third floor of a prebuilt base under that rule and EVERY FLOOR BELOW HER IS OUTSIDE THE HIVE — no resin,
     * no spawning, no vents. Centring is why the reactor-room case works. End-style hives already do exactly this,
     * which is where the shape is borrowed from.
     * </p>
     */
    int buildFreeSlabHalfHeight,

    /**
     * Ambient loaded-xenomorph ceiling per hive, replacing {@code hiveSpawnerMinimumLoadedXenomorphs} (whose name is a
     * misnomer — it is a MAXIMUM) while build-free mode is on.
     * <p>
     * 20 is right for a chamber cluster and nearly empty across a 48-block slab and a 19×19 territory. Parties, raids
     * and vent defenders still spawn ON TOP of this, so a fight goes well above it; this is the resting population.
     * </p>
     */
    int buildFreeActiveXenomorphs,

    /**
     * Lifetime daughter queens. ⚠ FORCED TO 0 when {@link #buildFreeQueenFoundsWherePlaced} is true — a daughter cannot
     * found inside her mother's claim (the spread-zone check refuses) and cannot dig, so she would wander until
     * something killed her.
     */
    int buildFreeDaughterSlots,

    /**
     * ⭐ The queen founds WHERE SHE IS PLACED instead of locating an anchor and digging to it.
     * <p>
     * The mapmaker's switch: she goes in the reactor room and the hive rises around her. An open-world player leaves
     * this false and gets a queen who still travels and settles on her own, just without carving.
     * </p>
     */
    boolean buildFreeQueenFoundsWherePlaced,

    /** Raids at all. */
    boolean buildFreeRaidsEnabled,

    /**
     * The three SURFACE parties, separately. ⚠ A prebuilt interior has no meaningful surface — the heightmap reports
     * the ROOF — so an indoor map wants all three off or the hive spends itself on the skyline.
     */
    boolean buildFreeSurfacePartiesEnabled,

    boolean buildFreeHostSurfacePartiesEnabled,

    boolean buildFreeBiomassSurfacePartiesEnabled,

    /** Eggs ringing the queen herself. Default 10, max 30. */
    int buildFreeQueenEggClusterSize,

    /** Whether egg clusters are also seeded out in the territory, away from her. */
    boolean buildFreeAdditionalEggClusters,

    /** Eggs per outlying cluster. Default 6, max 6. */
    int buildFreeEggClusterSize,

    /** How many outlying egg clusters. Default 4, max 8. */
    int buildFreeEggClusterAmount,

    /**
     * Jelly vat clusters scattered through the territory, 6 vats each. Default/max 6.
     * <p>
     * ⭐ NOT decoration: with no vault chamber there would be no vats anywhere, and [stated] "this would also make
     * irradiated conversion still possible" — the conversion needs physical vats in the world to retint. They double as
     * the mode's loot.
     * </p>
     */
    int buildFreeJellyClusterAmount,

    /** Scourge vat clusters. Default/max 1. */
    int buildFreeScourgeClusterAmount,

    /** Vertical gap between vents in one chunk column ⇒ tiers = slab height / this. */

    /** Vents allowed per chunk PER TIER. */

    /**
     * Chunks that must separate two vent-bearing chunks, applied PER TIER so floors stagger independently rather than
     * stacking their doors in one column.
     */

    /**
     * Hard ceiling on vents per hive — the safety valve behind the spacing rules.
     * <p>
     * ⚠ THE SPACING RULES ALONE DO NOT BOUND THIS. 3 per chunk with a 1-chunk gap over a 19×19 territory computes to
     * roughly 300 vents. [stated] the fear is the old mod's "30 vents in a chunk all over the floor and walls".
     * </p>
     */

    /**
     * Biomass an INVADING hive pays per chunk to claim ground beyond its own territory box — the beachhead. Its own
     * members claim inside the box for free.
     */
    int buildFreeInvasionClaimBiomassCost,

    /** How far beyond its own radius a hive may buy an invasion claim. */
    int buildFreeInvasionReachChunks,

    /**
     * ⭐⭐ HOW MANY BEACHHEAD CHUNKS ONE INVADER MAY HOLD INSIDE A RIVAL'S TERRITORY.
     * <p>
     * [stated] "how many claims invaders are allowed to be lowest would be 0 for not allowing them to and max i would
     * say is 6. default should be 2."
     * </p>
     * <p>
     * ⚠ ZERO DISABLES INVASION ENTIRELY - it is the off switch for the whole feature, not merely a tight budget.
     * Clamped 0..6 on read.
     * </p>
     */
    int buildFreeInvasionBeachheadChunks,

    /** Radius around the queen within which an intruder provokes the harbinger. */
    int buildFreeHarbingerRevealRadius,

    /**
     * STRUCTURELESS ONLY. Height of one vent "storey", in blocks. The 48-block band is split into storeys of this
     * height, and the per-chunk limit below applies to each storey separately, so floors stagger independently.
     * <p>
     * ⚠⚠ Oct 1 - THE VENT RULES WERE SPECIFIED, DESCRIBED IN HiveConfigDescriptions, AND NEVER ADDED HERE. Drone vents
     * inside a build-free claim are classed STRUCTURE, while the only cap ({@code VentSensors}) counted FRONTIER vents
     * - so there was no limit at all, and every drone placed a vent each cooldown. These four fields are that rule.
     * </p>
     */
    int buildFreeVentVerticalGap,

    /** STRUCTURELESS ONLY. [stated] "max 3 vents IN a chunk" - per storey. */
    int buildFreeVentsPerChunkPerTier,

    /**
     * STRUCTURELESS ONLY. [stated] "a 1-chunk gap between CHUNKS that contain vents" - per storey. 0 lets them touch.
     */
    int buildFreeVentChunkGap,

    /** STRUCTURELESS ONLY. Hard ceiling on a hive's drone-made vents, whatever the spacing allows. */
    int buildFreeVentMaxPerHive
) {

    private static final int TICKS_PER_SECOND = 20;

    private static final long TICKS_PER_MINECRAFT_DAY = 24000L;

    private static final long TICKS_PER_MINUTE = 60L * TICKS_PER_SECOND;

    // NOTE: there is deliberately no TICKS_PER_HOUR. One existed (60L * TICKS_PER_MINUTE = 72000t) and was a REAL
    // hour, so every "24L * TICKS_PER_HOUR" written to mean one day was actually SEVENTY-TWO Minecraft days. Four
    // knobs were wrong by that factor. Durations meant in game-days use TICKS_PER_MINECRAFT_DAY; anything meant in
    // wall-clock time uses TICKS_PER_MINUTE explicitly.

    /**
     * ⭐⭐⭐ CATCHES A SHUFFLED ARGUMENT LIST, WHICH THE COMPILER CANNOT.
     * <p>
     * ⚠⚠ THIS RECORD TAKES 149 POSITIONAL ARGUMENTS AND MOST OF THEM ARE {@code int}. Passing three of them in the
     * wrong order compiles perfectly and ships silently - which is exactly what happened in 0.2.3: capPercent,
     * rescuesPerHive and rescueBudget were passed 2, 6, 150 in the wrong order, so every empress in the wild ran
     * capPercent=2, LOWERING every ceiling she exists to raise.
     * </p>
     * <p>
     * ⚠ These are not tuning limits - they are IMPOSSIBILITY checks, deliberately far outside any value anyone would
     * choose, so tightening a dial can never trip them. They only fire when a number has landed somewhere it cannot
     * belong, and they log rather than throw: a wrong default should not stop the game booting.
     * </p>
     */
    private static void assertDefaultsSane(HiveConfig config) {
        if (config.empressCapPercent() < 100) {
            com.alien.Alien.LOGGER.error(
                "Hive config: empressCapPercent is {} - it RAISES ceilings, so it can never be below 100."
                    + " The defaults list is out of order with the record.",
                config.empressCapPercent()
            );
        }
        if (config.minimumHiveLocationDistanceChunks() <= 0 || config.maxTerritoryRadiusChunks() <= 0) {
            com.alien.Alien.LOGGER.error(
                "Hive config: hive spacing/{}territory radius came out non-positive ({} / {}) - defaults are"
                    + " misaligned, and hives will be able to found on top of each other.",
                "",
                config.minimumHiveLocationDistanceChunks(),
                config.maxTerritoryRadiusChunks()
            );
        }
    }

    public static HiveConfig defaults() {
        var built = buildDefaults();
        assertDefaultsSane(built);
        return built;
    }

    private static HiveConfig buildDefaults() {
        return new HiveConfig(
            // § 1 Faction lifecycle
            5L * TICKS_PER_MINUTE, // protoHiveStageInterval: 5 min between drone→warrior→praetorian→queen advances

            // § 2 Locations
            10L * TICKS_PER_SECOND, // settlementTicks: 10s
            14L * TICKS_PER_MINECRAFT_DAY, // locationMaxNoContactTicks: LocationDormancyTask rule 3. Accrues ONLY
            // while a claimed chunk is loaded AND not one of this location's members is standing in the claim;
            // pauses entirely when nothing is loaded, and RESETS to zero the moment any member is home. So an
            // unvisited hive can never die this way - it only catches a claim held open with nobody in it.
            // Deliberately generous (14 MC days of ticks = ~4.7 real hours of that condition) because the failure
            // mode is DESTRUCTIVE: a false positive deletes a legitimate hive. Rule 2 already reaps a genuinely
            // empty hive in 30s, so rule 3 only needs to catch the narrow "population on paper, nobody on the
            // ground" case - e.g. a hive that sent its whole roster out on parties while a player parked nearby
            // holds the chunks open.
            30L * TICKS_PER_MINUTE, // locationBootstrapGraceTicks: protect newborn locations for 30 min
            96, // bossBarDisplayRadiusBlocks
            60L * TICKS_PER_SECOND, // angryGraceTicks: 60s
            60L * TICKS_PER_SECOND, // contestTickWindow: 60s
            17, // minimumHiveLocationDistanceChunks: 17 so two hive CENTERS are >=17 chunks apart, guaranteeing a
            // full 16-chunk gap between them (16 would leave centers 16 apart = a <16 edge-to-edge gap).
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
            5L * TICKS_PER_MINECRAFT_DAY, // perSourceRaidCooldownTicks (5 MC days - a raid is a campaign, not a habit)
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
            2, // maxDaughterHivesPerLocation: a hive may seed exactly two daughters in its whole life. With the
               // 8-hive lineage cap this is the same constraint said twice - 1 -> 3 -> 7 -> 8 converges exactly on
               // maxLocationsPerLineage - which is what stops one hive quietly taking the world.
            50, // queenPromotionJellyCost: royal jelly to raise a praetorian (or, failing that, a crusher) into a
            // founding queen. ⚠ [stated] Aug 14, LOWERED 100 -> 50, so it is NO LONGER matched to
            // firewallCrowningJellyCost (still 100) - that pairing is history, do not "restore" it. The vats now back
            // this spend (see QueenPromotionService), so 50 is what a hive can realistically hold aside.
            30L * TICKS_PER_SECOND, // queenPromotionMoltTicks: same window as the empress molt
            5L * TICKS_PER_SECOND, // localLeaderPickCadenceTicks (100 ticks)
            0, // empressCandidateMinMembers: population floor for empress eligibility. WAS 250, which no hive could
               // ever reach in practice - 250 is also exactly HiveBalanceTask.MEMBER_CAP, the working-adult ceiling, so
               // a
               // hive had to be pegged at its absolute cap to nominate anyone. Field-confirmed dead: a 5-location
               // lineage
               // peaked at 19 members per hive and no empress ever emerged. The design gate is HIVE COUNT (4+
               // locations),
               // not population, and member count is a RANKING factor (see EmpressCandidatePicker), so 0 = off is the
               // spec-faithful default. Raise this if you later want a size floor as well as a count gate.
            5L * TICKS_PER_MINECRAFT_DAY, // empressCrowningCooldownTicks: after an empress DIES, her lineage cannot
            // crown another for 5 Minecraft days. Killing her is meant to buy the players breathing room, not to
            // start a countdown to the next one.
            // \u26a0\u26a0 ORDER MATTERS AND IT WAS WRONG IN 0.2.3. These three are all `int`, so passing them in
            // the wrong order compiled silently: the record declares capPercent, rescuesPerHive,
            // rescueBudget but the values were passed 2, 6, 150 in the order rescuesPerHive, rescueBudget,
            // capPercent. Every empress in the wild has been running capPercent=2 - LOWERING every ceiling
            // she is supposed to raise by half - with a rescue budget of 150 instead of 6.
            150, // empressCapPercent: a BLANKET percentage on every ceiling an empress raises - one rule instead of a
            // hand-tuned twin per cap. It covers the working population (250 -> 375), all four party sizes (attack
            // 8 -> 12, biomass hunting 7 -> 11, surface 5 -> 8, host hunt 4 -> 6), AND every per-caste ceiling in the
            // hive unit purchase data - warriors, praetorians, crushers, spitters and the rest all grow by the same
            // half again. The harbinger is the one deliberate exception and needs no exclusion logic: it is capped
            // per raid chamber, and she raises it by granting the hive a second chamber. Anything below 100 is
            // clamped in EmpressCaps so a misconfiguration can never punish a hive for having an empress.
            2, // empressRescuesPerHive: how many times she will refill ONE hive's firewall fund before writing it
               // off. Two extra lives, not immortality - die a third time and the hive dies like any other.
            6, // empressRescueBudget: NETWORK-WIDE cap on transfers for one empress, keyed on her empressId. This is
               // what makes broad pressure viable: without it the only answer to her is to besiege a single hive to its
               // third death, because she could rescue everywhere forever. A fresh empress gets a fresh budget.
            7L * TICKS_PER_MINECRAFT_DAY, // firewallCooldownTicks: 7 MC days of accrued stability to refill the fund
            5L * TICKS_PER_MINUTE, // firewallStabilityScanIntervalTicks: cadence for both the stability check and the
            // biomass income-rate sample
            50, // firewallJellyFloor: jelly reserve floor for the "has jelly reserves" stability condition
            100, // firewallCrowningJellyCost: jelly cost paid when a queen is crowned via QueenlessMaturationTask
            128, // minRoyalJellyCap: floor under the royal jelly ceiling, which is otherwise the hive's CLAIMED CHUNK
            // COUNT. That coupling quietly made both 100-jelly costs - crowning a successor and promoting a founder
            // queen - unpayable for any hive holding under 100 chunks: it could never bank the price, so a small
            // hive that lost its queen could never crown one and could never seed a daughter. Territory still buys
            // capacity above this floor; the floor only guarantees the essential royal costs stay reachable. Keep it
            // above the largest royal-jelly cost in the game (currently 100) or the hole reopens.

            // § 9 Lineage spread
            32, // maxLineageSpreadChunks
            // ⭐ OFF BY DEFAULT. Existing worlds keep behaving exactly as they always have; this exists so a server
            // owner who sees hive networks accumulating can spread them out without waiting for a mod update.
            // A useful starting value is 48 (768 blocks), comfortably clear of one lineage's own 19x19 footprint.
            0, // minLineageSpacingChunks
            30L * TICKS_PER_MINUTE, // lineageSpreadCooldownTicks
            8, // maxLocationsPerLineage: a lineage can have at most 8 member hives
            8, // maxLocationsUnderEmpress: an empress controls up to 8 hives (her origin included) - matched to
               // maxLocationsPerLineage so the empress (emerging at 4 hives) never freezes lineage growth short of
               // the 8-hive cap. Was 5, which silently capped every empress-led lineage at 5.
            70, // minimumPopulationForHiveSpread: ⚠ [stated] Aug 14, LOWERED 100 -> 70. SHARED with
                // AbstractSpreadAttempt, so this loosens the ABSTRACT spread path too, not just the visible promotion.
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
               // ⚠⚠ DERIVED FROM THE OTHER TWO SETTINGS, NOT PICKED. maxLocationsPerLineage (8) at the empress
               // territory radius (11 -> 23x23 -> 529 chunks) needs 4232 chunks for a lineage at full size. A lower cap
               // does not bound growth, it silently REDESIGNS the mod: at 1000 an empress lineage could never exceed
               // two
               // hives and maxLocationsPerLineage would be unreachable.
               //
               // ⚠ IF EITHER OF THOSE CHANGES, THIS MUST TOO: locations * (2*radius+1)^2, plus headroom.
            4500, // maxChunksPerLineage
            64, // maxClaimsPerScan (enough to complete a 7x7 ring boundary in one scan, so partial fills
            // align with ring boundaries instead of breaking mid-ring)
            1L * TICKS_PER_MINECRAFT_DAY, // resinFullDensityTicks: 1 MC day
            1, // maxPassiveClaimsPerUnloadedScan

            // § 11 Biomass
            25, // baseChunkCost
            100, // ovipositorCreationBiomassCost
            5, // resinSpreadBiomassCost
            0.05, // growthFactor
            // ⚠⚠ CUT FROM 0.5 BACK TO 0.1 AFTER A LIVE SERVER REPORT. 0.5 was a tenfold rise on the original 0.05 and
            // it worked as intended - unwatched hives became genuinely dangerous - but it compounds on a server that
            // runs for days: faster claiming means more resin, more block entities, more scheduled ticks and more
            // memory. A dedicated server reached 8 MiB free of 12 GB with 498,053 pending block ticks.
            //
            // ⭐ THE FLAT FLOOR BELOW IS WHAT KEEPS YOUNG HIVES VIABLE, so this can be cut without putting new hives
            // back to being nearly frozen: a 9-chunk hive still earns 4.9/s against the original 0.45/s.
            0.3, // baseUnloadedBiomassPerChunkPerSec: [stated] Oct 8 raised 0.1 -> 0.3 (with the flat rate below)
            6.0, // unloadedBiomassFlatPerSec: [stated] Oct 8 "yes 6 is fine" (was 4.0) - a 9-chunk hive now banks
                 // ~8.7/s
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
            0.5, // minimumPopulationRatioForClaiming
            20, // hiveSpawnerMinimumLoadedXenomorphs
            true, // reserveSpawnsCanIgnoreResin: reserves can materialize without a resin floor
            TICKS_PER_SECOND, // hiveSpawnerIntervalTicks
            32, // hiveSpawnerMaxSpawnAttemptsPerLocation
            4, // hiveSpawnerMaxSpawnsPerLocation
            40, // combatRespiteKillThreshold: 2x hiveSpawnerMinimumLoadedXenomorphs default, intentionally not coupled
            10L * TICKS_PER_SECOND, // combatRespiteMinTicks
            TICKS_PER_MINUTE, // combatRespiteMaxTicks
            TICKS_PER_MINUTE, // royalJellyTicksPerProduction (1 game-min per queen)
            100L * TICKS_PER_MINUTE, // scourgeJellyTicksPerQueenProduction (100 game-min per queen)
            TICKS_PER_MINUTE, // scourgeJellyTicksPerHarbingerProduction (1 game-min per harbinger)

            false, // noMaintenance: hives repair their own structure as normal

            // § 14 Queen lifecycle
            true, // queenFrontEndPhasesEnabled: run developing->location->hibernation before founding
            false, // queenFoundsWhereStanding: ordinary-play "found where standing"; NOT the build-free flag

            // § 15 Parties
            2, // surfacePartyBaseSize
            0.15, // surfacePartySizePerClaimedChunk
            5, // surfacePartyMaxSize: hard ceiling - size scaled with claims unbounded (20+ on a big hive)
            0.35, // surfacePartyVentDropChance: roll on dawn despawn. Was 0.10, which (combined with a placement
            // bug that silently aborted on sloped ground) meant testers ran party after party and never saw
            // a vent. Vents gate the whole vent-dependent trio, so a hive that cannot seed one is stuck.
            3, // surfacePartyMaxVentsPerClaim
               // ⭐ 1 = CHECKERBOARD, which is the intended layout: surface vents never in orthogonally touching chunks.
            1, // surfacePartyVentChunkGap: cap counted against near-surface vents only, not the full column
            6, // surfacePartySurfaceBandBlocks: vertical margin around the terrain heightmap counted as "surface"
               // (also reused by biomass hunting party's vent-spawn-point lookup)
            2, // biomassHuntingPartyBaseSize
            0.15, // biomassHuntingPartySizePerClaimedChunk
            7, // biomassHuntingPartyMaxSize: hard ceiling (bonus spitters ride on top)
            2, // hostHuntPartyBaseSize
            0.1, // hostHuntPartySizePerClaimedChunk
            4, // hostHuntPartyMaxSize: drones sent to fetch hosts
            6000, // hostHuntPartyDurationTicks: 5 minutes to find a host, then give up and refund
            1, // biomassHuntingPartyBonusSpitterCount: extra spitters beyond the base budget, only if reserves allow
            10L * TICKS_PER_MINUTE, // biomassHuntingPartyDurationTicks: active duration before returning home via vent
            2, // attackPartyBaseSize
            0.15, // attackPartySizePerClaimedChunk
            8, // attackPartyMaxSize: hard ceiling - size scaled with claims unbounded (~20 on a big hive)
            3L * TICKS_PER_MINECRAFT_DAY, // attackPartyCooldownTicks: gap between wave 1 and wave 2 (3 MC days)
            1L * TICKS_PER_MINECRAFT_DAY, // attackPartyWave1DelayTicks: wave 1 fires ~1 MC day after the intrusion
            30L * TICKS_PER_SECOND, // attackIntrusionDwellTicks: in-claim-while-hostile dwell before a campaign arms
            10L * TICKS_PER_MINUTE, // attackPartyDurationTicks: active duration hunting the target before giving up

            // § 16 Build-free mode — every value below is inert until buildFreeModeEnabled is true
            false, // buildFreeModeEnabled
            9, // buildFreeTerritoryRadiusChunks: 19x19, same footprint as maxTerritoryRadiusChunks
            11, // buildFreeEmpressTerritoryRadiusChunks: 23x23
            24, // buildFreeSlabHalfHeight: 48-block band CENTRED on the queen (normal hives are +16 up-only)
            40, // buildFreeActiveXenomorphs: replaces the 20 loaded cap, which is sparse across a whole building
            2, // buildFreeDaughterSlots: max 4; forced to 0 when the queen founds where placed
            false, // buildFreeQueenFoundsWherePlaced
            true, // buildFreeRaidsEnabled
            true, // buildFreeSurfacePartiesEnabled
            true, // buildFreeHostSurfacePartiesEnabled
            true, // buildFreeBiomassSurfacePartiesEnabled
            10, // buildFreeQueenEggClusterSize: max 30
            true, // buildFreeAdditionalEggClusters
            6, // buildFreeEggClusterSize: max 6
            4, // buildFreeEggClusterAmount: max 8
            6, // buildFreeJellyClusterAmount: 6 vats each
            1, // buildFreeScourgeClusterAmount
            50, // buildFreeInvasionClaimBiomassCost
            // ⚠ 19, NOT 3. [stated] "max of just over the distance. which is 17 chunks so the default i would set
            // that to is 19 so it allows for a rival to invade even with a gap by 2 chunks." Two touching 19x19
            // territories put their centres ~18 chunks apart, so 19 lets a rival reach across a 2-chunk gap without
            // being able to strike from arbitrary range.
            19, // buildFreeInvasionReachChunks
            2, // buildFreeInvasionBeachheadChunks: 0 disables invasion, max 6
            16, // buildFreeHarbingerRevealRadius
            12, // buildFreeVentVerticalGap: four storeys in the default 48-block band
            3, // buildFreeVentsPerChunkPerTier: [stated] max 3 vents in a chunk
            1, // buildFreeVentChunkGap: [stated] a 1-chunk gap between vent chunks
            48 // buildFreeVentMaxPerHive
        );
    }
}
