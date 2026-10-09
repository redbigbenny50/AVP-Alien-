package com.alien.common.gameplay.hive.config;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ⭐⭐ PLAIN-LANGUAGE DESCRIPTIONS FOR CONFIG FIELDS.
 * <p>
 * [stated] "can we put a short message that says what each choice does so normal people know what to change it to."
 * </p>
 * <p>
 * ⚠⚠ JSON HAS NO COMMENTS, which is why these live here rather than in the file itself. Sibling {@code _comment} keys
 * would double the file's length and put explanatory text in the same namespace as real settings, where a typo silently
 * becomes an "unknown field". Instead these are written to a companion reference file next to the config and printed by
 * {@code /avp hive config get}, so the explanation is always beside the value without ever being parsed.
 * </p>
 * <p>
 * ⚠ NOT EVERY FIELD IS COVERED, AND THAT IS DELIBERATE. There are ~150, and a wrong or vague description is worse than
 * none - it invites someone to change a value on a false understanding. Covered: the whole structureless block (which
 * is what a mapmaker actually edits), and the general fields most likely to be reached for. Anything missing falls back
 * to its humanised name, which is honest about telling you nothing.
 * </p>
 */
public final class HiveConfigDescriptions {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        // ---------- Structureless Mode ----------
        put(
            "buildFreeModeEnabled",
            "THE MASTER SWITCH FOR STRUCTURELESS MODE - every other setting in this section does nothing while this is false. Hives claim, spread resin, lay eggs and fight, but never dig or build. For adventure "
                + "maps and prebuilt bases. Everything below only matters when this is true."
        );
        put(
            "buildFreeQueenFoundsWherePlaced",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. true: the queen founds exactly where you put her - use this for a set piece. false: she still travels "
                + "and picks her own spot, she just never carves. NOTE: true forces daughter queens to 0, because a "
                + "daughter could neither found inside her mother's territory nor dig elsewhere."
        );
        put(
            "buildFreeTerritoryRadiusChunks",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. How far the hive's territory reaches, in chunks from the queen. 9 gives a 19x19 area."
        );
        put(
            "buildFreeEmpressTerritoryRadiusChunks",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. Same, for a hive under an empress. 11 gives 23x23."
        );
        put(
            "buildFreeSlabHalfHeight",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. How far the hive reaches ABOVE and BELOW the queen, in blocks. 24 gives a 48-block band centred on her - "
                + "raise it for a tall building so upper and lower floors are inside the hive."
        );
        put(
            "buildFreeActiveXenomorphs",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. How many xenomorphs wander the hive at rest. Raise it for a big building, lower it for a small one. "
                + "Fights spawn extra on top of this, so it is not a hard limit on what you will face."
        );
        put(
            "buildFreeDaughterSlots",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. How many daughter queens this hive may ever send out to found their own. 0 keeps it a single hive. "
                + "Max 4. Forced to 0 if the queen founds where placed."
        );
        put(
            "buildFreeRaidsEnabled",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. Whether hives send raids at players who have angered them. Turn off for a map where you want the "
                + "aliens to stay where you put them."
        );
        put(
            "buildFreeSurfacePartiesEnabled",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. Whether hives send scouting parties to the surface. Turn OFF for an indoor map - the game reads the "
                + "roof as the surface, so they will spend themselves on the skyline."
        );
        put(
            "buildFreeHostSurfacePartiesEnabled",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. Whether hives send parties out to capture hosts. Same advice: off for indoor maps."
        );
        put(
            "buildFreeBiomassSurfacePartiesEnabled",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. Whether hives send parties out to gather biomass. Same advice: off for indoor maps."
        );
        put(
            "buildFreeQueenEggClusterSize",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. Eggs laid in a ring around the queen herself. Max 30."
        );
        put(
            "buildFreeAdditionalEggClusters",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. Whether extra egg clusters appear out in the territory as well as around the queen."
        );
        put(
            "buildFreeEggClusterSize",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. Eggs in each of those outlying clusters. Max 6."
        );
        put(
            "buildFreeEggClusterAmount",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. How many outlying egg clusters. Max 8."
        );
        put(
            "buildFreeJellyClusterAmount",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. Clusters of royal jelly vats scattered through the territory - the hive's stores, and worth raiding. "
                + "Max 6."
        );
        put(
            "buildFreeScourgeClusterAmount",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. Clusters of scourge jelly vats. Max 1."
        );
        put(
            "buildFreeVentVerticalGap",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. Minimum vertical spacing between vents in blocks. Roughly one vent level per storey."
        );
        put(
            "buildFreeVentsPerChunkPerTier",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. How many vents may share one chunk at the same height."
        );
        put(
            "buildFreeVentChunkGap",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. How many chunks must separate vent-bearing chunks. Higher means fewer, more spread out doors."
        );
        put(
            "buildFreeVentMaxPerHive",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. Hard ceiling on vents per hive, whatever the spacing rules allow. Lower it if the walls look "
                + "perforated."
        );
        put(
            "buildFreeInvasionClaimBiomassCost",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. What a rival hive pays, in biomass, to seize a chunk near your hive as a foothold to attack from."
        );
        put(
            "buildFreeInvasionReachChunks",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. How far beyond its own territory a rival hive may buy that foothold."
        );
        put(
            "buildFreeHarbingerRevealRadius",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. How close an intruder must get to the queen before the harbinger appears to defend her."
        );
        put(
            "buildFreeWorkerRetreatVentTimeoutSeconds",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. How long a retreating worker tries to reach a vent before hiding where it stands."
        );

        // ---------- General fields people actually reach for ----------
        put(
            "populationPerChunk",
            "How much population each claimed chunk supports. Raises or lowers the ceiling on every hive in the "
                + "world."
        );
        put(
            "minimumHiveLocationDistanceChunks",
            "Minimum distance between any two hives, in chunks - daughter hives of the same network as well as "
                + "rival ones. This is the main control on how densely hives pack into an area, and every hive "
                + "costs a server resin, block entities and ticks. Raise it to spread them out; see also "
                + "minLineageSpacingChunks, which governs the distance between separate hive NETWORKS."
        );
        put(
            "maxTerritoryRadiusChunks",
            "How far a normal (non structureless) hive's territory can reach from its centre."
        );
        put(
            "maxChunksPerLocation",
            "Hard cap on how many chunks one hive may own, whatever its radius allows."
        );
        put(
            "hiveSpawnerMinimumLoadedXenomorphs",
            "Despite the name this is a MAXIMUM: how many xenomorphs wander a normal hive at rest."
        );
        put(
            "royalJellyTicksPerProduction",
            "How long a queen takes to make one royal jelly, in ticks. 1200 is one in-game minute. Lower is faster."
        );
        put(
            "queenPromotionJellyCost",
            "Royal jelly needed to raise a praetorian into a new queen."
        );
        put(
            "raidExpiryTicks",
            "How long a raid waits for a target who has logged out before giving up."
        );
        put(
            "settlementTicks",
            "How long a queen must be undisturbed before she settles and founds a hive."
        );
        put(
            "minimumPopulationRatioForClaiming",
            "How full a hive must be before it claims more ground, and below which it starts losing ground."
        );
        put(
            "queenFrontEndPhasesEnabled",
            "LEAVE THIS TRUE. It is a development switch: false stops queens developing, settling and founding "
                + "entirely, so no hive is ever created."
        );
        put(
            "queenFoundsWhereStanding",
            "Force queens to build a hive at their current location. true: a queen builds her hive exactly where "
                + "she is standing, above ground and all - she never travels or digs down to find a spot. false "
                + "(default): she wanders, digs down, and builds underground the normal way. Hives still cannot "
                + "overlap - a queen standing in another hive's territory is refused either way."
        );
        put(
            "buildFreeInvasionBeachheadChunks",
            "STRUCTURELESS MODE ONLY - ignored unless Structureless Mode is on. How many chunks a hive claims "
                + "immediately when it lands in a new area, as its starting foothold."
        );
        put(
            "protoHiveStageInterval",
            "How long (in ticks) a brand-new hive spends in each early growth stage before advancing to the "
                + "next. Higher = slower start."
        );
        put(
            "locationMaxNoContactTicks",
            "How long (in ticks) a hive can go with nobody visiting or loading it before it is treated as out "
                + "of contact and allowed to go dormant."
        );
        put(
            "locationBootstrapGraceTicks",
            "A protection window (in ticks) after a hive is founded during which it cannot lose ground or be "
                + "culled. Gives a new hive time to establish."
        );
        put(
            "bossBarDisplayRadiusBlocks",
            "How close (in blocks) you must be for a hive's health bar to appear on your screen."
        );
        put(
            "angryGraceTicks",
            "How long (in ticks) a hive stays agitated after being provoked, before it settles back down."
        );
        put(
            "contestTickWindow",
            "How long (in ticks) two hives keep fighting over the same chunk before the contest is resolved "
                + "one way or the other."
        );
        put(
            "initialHiveLocationClaimRadiusChunks",
            "How much ground a hive claims the moment it is founded, as a radius in chunks. 1 = a 3x3 of "
                + "chunks."
        );
        put(
            "shedGraceTicks",
            "How long (in ticks) a xenomorph keeps its old lineage ties after leaving, before they are "
                + "dropped."
        );
        put(
            "minLineageAgeForShedding",
            "How old (in ticks) a lineage must be before its members can shed it at all. Stops brand-new "
                + "lineages falling apart."
        );
        put(
            "convoySpeedBlocksPerSecond",
            "How fast a convoy travels between hives, in blocks per second, while it is not loaded."
        );
        put(
            "arrivalRadiusBlocks",
            "How close (in blocks) a convoy must get to its destination to count as arrived."
        );
        put(
            "manifestDistanceBlocks",
            "How close (in blocks) a player must be for a convoy's members to be spawned as real entities "
                + "instead of travelling as numbers."
        );
        put(
            "convoyInterceptRadiusBlocks",
            "How close (in blocks) a player must get to a convoy to intercept it."
        );
        put(
            "reinforcementSpeedMultiplier",
            "Speed multiplier applied to reinforcement convoys. Above 1 = faster than the base convoy speed."
        );
        put(
            "migrationSpeedMultiplier",
            "Speed multiplier applied to a hive evacuating to a new home. Above 1 = faster than the base "
                + "convoy speed."
        );
        put(
            "raidSpeedMultiplier",
            "Speed multiplier applied to raid convoys heading for a target. Above 1 = faster than the base "
                + "convoy speed."
        );
        put(
            "reinforcementSourceCooldownTicks",
            "How long (in ticks) a hive must wait before it can send another batch of reinforcements."
        );
        put(
            "reinforcementMinSize",
            "The fewest xenomorphs a reinforcement convoy will carry."
        );
        put(
            "reinforcementMaxSize",
            "The most xenomorphs a reinforcement convoy will carry."
        );
        put(
            "resettleGraceTicks",
            "How long (in ticks) after evacuating before a hive is allowed to migrate again. Stops a hive "
                + "bouncing between homes."
        );
        put(
            "migrationBiomassDecayTicks",
            "How long (in ticks) the biomass a migrating hive is carrying survives before it starts to spoil."
        );
        put(
            "migrationTerritoryFloorChunks",
            "A hive that shrinks to this many chunks or fewer gives up and evacuates to a better spot."
        );
        put(
            "migrationRallyTicks",
            "How long (in ticks) a hive gathers its members before the evacuation actually departs."
        );
        put(
            "migrationBiomassPayloadCap",
            "The most biomass an evacuating hive can carry with it. Anything above this is lost."
        );
        put(
            "raidThresholdKills",
            "How many of a hive's members you must kill before it starts raiding you back."
        );
        put(
            "raidAggroWindowTicks",
            "The window (in ticks) those kills must happen inside to count toward a raid. Kill slowly enough "
                + "and the count fades."
        );
        put(
            "raidMinLocationSizeChunks",
            "How big a hive must be, in claimed chunks, before it is capable of raiding at all."
        );
        put(
            "perSourceRaidCooldownTicks",
            "How long (in ticks) a hive waits after sending a raid before it can send another."
        );
        put(
            "baseRaidSize",
            "How many xenomorphs a raid starts with before size bonuses are added."
        );
        put(
            "raidSizePerClaimedChunk",
            "Extra raid members granted per chunk the hive owns. Bigger territory = bigger raids."
        );
        put(
            "raidEngageRadiusBlocks",
            "How close (in blocks) a raid must get to its target before it switches from travelling to "
                + "attacking."
        );
        put(
            "raidFrenzyExtraMemberCap",
            "The most extra members a hive can throw into a raid when it is worked up beyond its normal size."
        );
        put(
            "raidLossDeathWindowTicks",
            "The window (in ticks) over which raider deaths are counted toward the raid giving up."
        );
        put(
            "raidLossDeathThreshold",
            "How many raiders must die inside that window before the raid breaks off."
        );
        put(
            "raidLossDownGraceTicks",
            "How long (in ticks) a raid holds on after taking heavy losses before it actually retreats."
        );
        put(
            "raidLossAftermathTicks",
            "How long (in ticks) a hive stays shaken after losing a raid, before returning to normal."
        );
        put(
            "combatRespiteKillThreshold",
            "How many kills in a fight it takes before a hive backs off and gives you a breather."
        );
        put(
            "combatRespiteMinTicks",
            "The shortest breather (in ticks) a hive will give you after heavy fighting."
        );
        put(
            "combatRespiteMaxTicks",
            "The longest breather (in ticks) a hive will give you after heavy fighting."
        );
        put(
            "empressMoltDurationTicks",
            "How long (in ticks) a queen spends in her royal cocoon becoming an empress. She is helpless "
                + "while it runs."
        );
        put(
            "localLeaderPickCadenceTicks",
            "How often (in ticks) a hive re-picks which of its members is acting as the local leader."
        );
        put(
            "empressCandidateMinMembers",
            "How many members a hive needs before one of its royals can be considered for empress."
        );
        put(
            "empressCrowningCooldownTicks",
            "How long (in ticks) a lineage must wait after crowning an empress before it can crown another."
        );
        put(
            "empressCapPercent",
            "How much an empress raises her lineage's population ceilings, as a percentage. 150 = ceilings "
                + "are half again as high while she lives. This can never be below 100, because an empress must "
                + "never LOWER a ceiling."
        );
        put(
            "empressRescuesPerHive",
            "How many times an empress will bail out any one struggling hive with a transfer of members."
        );
        put(
            "empressRescueBudget",
            "The total number of rescue transfers one empress can make across her whole network, ever."
        );
        put(
            "maxDaughterHivesPerLocation",
            "How many daughter hives a single hive is allowed to spawn off."
        );
        put(
            "queenPromotionMoltTicks",
            "How long (in ticks) a praetorian spends in its cocoon becoming a queen."
        );
        put(
            "firewallCooldownTicks",
            "How long (in ticks) between checks on a lineage that has been walled off from the rest of its "
                + "network."
        );
        put(
            "firewallStabilityScanIntervalTicks",
            "How often (in ticks) the mod checks whether a cut-off part of a lineage has stabilised on its "
                + "own."
        );
        put(
            "firewallJellyFloor",
            "The least royal jelly a cut-off hive must hold before it is allowed to crown its own royal."
        );
        put(
            "firewallCrowningJellyCost",
            "How much royal jelly a cut-off hive spends to crown its own royal."
        );
        put(
            "minLineageSpacingChunks",
            "DEFAULT 0 = OFF. How far a brand-new hive NETWORK must be from any existing hive before a wandering "
                + "queen may found one, in chunks. Each network is already limited in size, but nothing limits "
                + "how MANY networks can exist, so on a long-running world they slowly accumulate and every one "
                + "costs the server resin, block entities and ticks. Raise this if hives are getting too dense - "
                + "48 (768 blocks) is a good starting point. A refused queen is not stuck: she simply keeps "
                + "wandering and founds somewhere further out. This does NOT affect an existing network "
                + "spreading its own daughter hives, so it can never stop a hive you already have from growing "
                + "or building. For the distance between individual hives, see minimumHiveLocationDistanceChunks."
        );
        put(
            "maxLineageSpreadChunks",
            "How far (in chunks) a lineage's hives may spread from each other before a new hive counts as a "
                + "separate bloodline."
        );
        put(
            "lineageSpreadCooldownTicks",
            "How long (in ticks) a lineage waits between founding new hives."
        );
        put(
            "maxLocationsPerLineage",
            "The most hives one lineage may have at once without an empress."
        );
        put(
            "maxLocationsUnderEmpress",
            "The most hives one lineage may have at once while an empress is alive. Normally higher than the "
                + "figure above."
        );
        put(
            "minimumPopulationForHiveSpread",
            "How many members a hive needs before it can afford to send founders off to start another."
        );
        put(
            "abstractSpreadMinFounderGroupSize",
            "The fewest xenomorphs sent along to found a new hive."
        );
        put(
            "abstractSpreadMaxFounderGroupSize",
            "The most xenomorphs sent along to found a new hive."
        );
        put(
            "foragerJoinTicks",
            "How long (in ticks) a wandering xenomorph must linger near a hive before it is adopted as a "
                + "member."
        );
        put(
            "claimActivityWindowTicks",
            "How recently (in ticks) a hive must have been active for it to keep taking new ground."
        );
        put(
            "perLocationClaimCooldownTicks",
            "How long (in ticks) a hive waits between claiming one chunk and the next."
        );
        put(
            "lineageScanIntervalTicks",
            "How often (in ticks) a lineage reviews its territory and decides where to expand."
        );
        put(
            "maxChunksPerLineage",
            "The most ground one lineage may hold in total, in chunks, across all its hives. Every claimed chunk "
                + "carries resin, block entities and scheduled ticks, so this is the main lever on how much a "
                + "server has to carry for one hive network. The default allows eight hives at full empress "
                + "territory (8 x 23x23 = 4232) with a little headroom - set it below that and hives stop "
                + "expanding before they reach their normal size."
        );
        put(
            "maxClaimsPerScan",
            "The most chunks a hive may claim in a single review. Lower = slower, steadier expansion."
        );
        put(
            "resinFullDensityTicks",
            "How long (in ticks) it takes a claimed chunk to fill in with resin completely."
        );
        put(
            "maxPassiveClaimsPerUnloadedScan",
            "The most chunks a hive may claim in one go while nobody is nearby to watch it happen."
        );
        put(
            "biomassDirtyThreshold",
            "How much a hive's biomass must change before it is written back to disk. Higher = fewer saves, "
                + "slightly staler data if the server crashes."
        );
        put(
            "lastGrowthTickDirtyThreshold",
            "How many ticks of growth may pass before that progress is written back to disk."
        );
        put(
            "slowPathLocationUpdatesPerTick",
            "How many hives are brought up to date each tick on the background pass. Lower = gentler on the "
                + "server, slower catch-up."
        );
        put(
            "passiveClaimCatchUpWindowCap",
            "The most time (in ticks) a hive may catch up on in one go after being unloaded. Stops a long "
                + "absence causing a huge burst of expansion the moment you return."
        );
        put(
            "reserveSpawnsCanIgnoreResin",
            "true: banked xenomorphs may emerge from any suitable spot. false: they must come out of resin."
        );
        put(
            "hiveSpawnerIntervalTicks",
            "How often (in ticks) a hive tries to bring banked members out into the world."
        );
        put(
            "hiveSpawnerMaxSpawnAttemptsPerLocation",
            "How many times a hive will try to find a spot for each member before giving up for now."
        );
        put(
            "hiveSpawnerMaxSpawnsPerLocation",
            "The most xenomorphs one hive may bring out in a single attempt."
        );
        put(
            "scourgeJellyTicksPerQueenProduction",
            "How long (in ticks) a queen takes to produce one unit of scourge jelly."
        );
        put(
            "scourgeJellyTicksPerHarbingerProduction",
            "How long (in ticks) a harbinger takes to produce one unit of scourge jelly."
        );
        put(
            "minRoyalJellyCap",
            "The least royal jelly a hive will always be allowed to stockpile, whatever its size."
        );
        put(
            "surfacePartyBaseSize",
            "How many xenomorphs a surface party starts with, before size bonuses. Surface parties go up top "
                + "to spread resin and open vents."
        );
        put(
            "surfacePartySizePerClaimedChunk",
            "Extra surface party members per chunk the hive owns."
        );
        put(
            "surfacePartyMaxSize",
            "The most members a surface party may have."
        );
        put(
            "surfacePartyMaxSizeEmpress",
            "The most members a surface party may have while an empress rules the lineage. Normally higher."
        );
        put(
            "surfacePartyVentDropChance",
            "The chance (0 to 1) that a surface party leaves a working vent behind at a spot it visits."
        );
        put(
            "surfacePartyVentChunkGap",
            "Minimum gap between chunks that hold surface vents, in chunks. At the default of 1 surface vents form "
                + "a CHECKERBOARD: vented chunks touch at the corners but never side by side, so vents cannot carpet "
                + "an area. Raise it to spread them further apart, or set 0 to allow vents in every chunk. This is "
                + "separate from surfacePartyMaxVentsPerClaim, which limits how many vents share a single chunk."
        );
        put(
            "surfacePartyMaxVentsPerClaim",
            "The most vents that may exist in one claimed chunk near the surface."
        );
        put(
            "surfacePartySurfaceBandBlocks",
            "How deep (in blocks) below the surface still counts as 'surface' for these parties."
        );
        put(
            "biomassHuntingPartyBaseSize",
            "How many xenomorphs a hunting party starts with, before size bonuses. Hunting parties go out to "
                + "kill for biomass."
        );
        put(
            "biomassHuntingPartySizePerClaimedChunk",
            "Extra hunting party members per chunk the hive owns."
        );
        put(
            "biomassHuntingPartyMaxSize",
            "The most members a hunting party may have."
        );
        put(
            "biomassHuntingPartyMaxSizeEmpress",
            "The most members a hunting party may have while an empress rules the lineage. Normally higher."
        );
        put(
            "biomassHuntingPartyBonusSpitterCount",
            "How many extra spitters are added to a hunting party for ranged support."
        );
        put(
            "biomassHuntingPartyDurationTicks",
            "How long (in ticks) a hunting party stays out before returning home."
        );
        put(
            "hostHuntPartyBaseSize",
            "How many xenomorphs a host hunt starts with, before size bonuses. Host hunts go out to capture "
                + "live hosts for eggs."
        );
        put(
            "hostHuntPartySizePerClaimedChunk",
            "Extra host hunt members per chunk the hive owns."
        );
        put(
            "hostHuntPartyMaxSize",
            "The most members a host hunt may have."
        );
        put(
            "hostHuntPartyMaxSizeEmpress",
            "The most members a host hunt may have while an empress rules the lineage. Normally higher."
        );
        put(
            "hostHuntPartyDurationTicks",
            "How long (in ticks) a host hunt stays out before returning home."
        );
        put(
            "attackPartyBaseSize",
            "How many xenomorphs an attack party starts with, before size bonuses. Attack parties are sent at "
                + "an intruder."
        );
        put(
            "attackPartySizePerClaimedChunk",
            "Extra attack party members per chunk the hive owns."
        );
        put(
            "attackPartyMaxSize",
            "The most members an attack party may have."
        );
        put(
            "attackPartyMaxSizeEmpress",
            "The most members an attack party may have while an empress rules the lineage. Normally higher."
        );
        put(
            "attackPartyCooldownTicks",
            "How long (in ticks) a hive waits between sending attack parties."
        );
        put(
            "attackPartyWave1DelayTicks",
            "How long (in ticks) after being provoked before the first attack wave sets out."
        );
        put(
            "attackIntrusionDwellTicks",
            "How long (in ticks) you must stay inside a hive's territory before it decides you are an "
                + "intruder worth attacking."
        );
        put(
            "attackPartyDurationTicks",
            "How long (in ticks) an attack party keeps hunting before giving up and going home."
        );
        put(
            "baseChunkCost",
            "How much biomass a hive spends to claim one more chunk of ground."
        );
        put(
            "ovipositorCreationBiomassCost",
            "How much biomass a queen spends growing her eggsack."
        );
        put(
            "resinSpreadBiomassCost",
            "How much biomass a hive spends per resin block it spreads."
        );
        put(
            "growthFactor",
            "How much more expensive each chunk gets as a hive grows. 1.0 = every chunk costs the same; above "
                + "1.0 = large hives pay steeply more to keep expanding."
        );
        put(
            "unloadedBiomassFlatPerSec",
            "A flat amount of biomass every hive earns each second while nobody is nearby, added on top of the "
                + "per-chunk rate above. This is what gets a brand-new hive going: at the default 4 a fresh 9-chunk "
                + "hive earns about 8.5 a second instead of 4.5, so it can start catching hosts far sooner."
        );
        put(
            "baseUnloadedBiomassPerChunkPerSec",
            "Biomass earned per claimed chunk each second while nobody is nearby. This is how hives grow "
                + "off-screen. Kept small on purpose: it multiplies by territory, so a large hive network compounds "
                + "quickly on a long-running server. The flat rate below is what makes young hives viable."
        );
        put(
            "unloadedEmpressBonusPerSec",
            "Extra biomass per second, while unloaded, for a lineage that has a living empress."
        );
        put(
            "loadedBiomassPerLoadedXenomorphPerSec",
            "Biomass earned each second for every one of the hive's own xenomorphs that is loaded and active."
        );
        put(
            "loadedBiomassPerNonAlienKill",
            "Biomass earned for each creature the hive kills that is not one of its own."
        );
        put(
            "loadedBiomassPerResinBlockPlaced",
            "Biomass earned for each resin block the hive lays down."
        );
        put(
            "loadedBiomassPerOvomorphPerSec",
            "Biomass earned each second for every egg the hive is keeping."
        );
        put(
            "loadedBiomassEmpressPresentBonusPerSec",
            "Extra biomass per second while an empress is physically present at the hive."
        );
        put(
            "loadedBiomassIdleBonusPerSec",
            "Extra biomass per second while the hive is calm and unbothered. Rewards being left alone."
        );
        put(
            "biomassAccumulationCapMultiplier",
            "How much biomass a hive may bank, as a multiple of what it currently needs. Stops an old hive "
                + "hoarding without limit."
        );
        put(
            "noMaintenance",
            "Stop hives repairing their own structure, so anything you build or break inside one stays that way. "
                + "true: aliens ignore damage to hive blocks and leave player-placed blocks alone, even in "
                + "corridors - which means a passage CAN be sealed permanently. false (default): a hive repairs "
                + "its own blocks and clears anything left in its corridors. Hives still dig new rooms and spread "
                + "resin either way; this only stops upkeep of what is already built."
        );
    }

    private HiveConfigDescriptions() {}

    /** The description for a field, or {@code null} if none is written. */
    public static @Nullable String of(String fieldName) {
        return DESCRIPTIONS.get(fieldName);
    }

    private static void put(String field, String description) {
        DESCRIPTIONS.put(field, description);
    }
}
