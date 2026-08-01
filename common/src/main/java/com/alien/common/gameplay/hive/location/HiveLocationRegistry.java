package com.alien.common.gameplay.hive.location;

import com.alien.Alien;
import com.alien.common.gameplay.hive.config.HiveConfig;
import com.alien.common.gameplay.hive.faction.HiveLocationFactionProvisioner;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.growth.HiveLocationClaims;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.alien.common.gameplay.hive.id.HiveLocationIds;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.tick.HiveLocationLoadedTickTask;
import com.alien.common.gameplay.hive.tick.HiveLocationSlowTickTask;
import com.alien.common.gameplay.hive.tick.LineageConvoyTickTask;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Server-wide registry of {@link HiveLocation}s. Builds three indexes — by id, by lineage, by (level, chunk) — plus a
 * per-dim spatial bucket index for "nearest location" queries.
 * <p>
 * None of these indexes are persisted to disk. They're rebuilt on server start by walking every
 * {@link LineageFactionData}'s nested locations.
 * <p>
 * {@link #tick(MinecraftServer)} runs once per server tick (driven from {@code Alien.tickHiveRegistry}). It iterates
 * every registered location across every dimension and dispatches them to {@link HiveLocationLoadedTickTask}; bounded
 * randomized slow-location work is also sampled every tick. Every {@link HiveConfig#lineageScanIntervalTicks()} ticks
 * it fires coarse lineage lifecycle work.
 * <p>
 * See {@code HIVE_REDESIGN_03_LOCATIONS.md} § 1 and {@code HIVE_REDESIGN_12_PERFORMANCE.md} § 3.
 */
public final class HiveLocationRegistry {

    public static final HiveLocationRegistry INSTANCE = new HiveLocationRegistry();

    /** 32-chunk buckets — coarse enough that nearest-by-dim only scans a handful of buckets. */
    private static final int SPATIAL_BUCKET_CHUNKS = 32;

    private final Map<HiveLocationId, HiveLocation> byId = new HashMap<>();

    private final Map<ResourceLocation, Set<HiveLocationId>> byLineage = new HashMap<>();

    private final Map<ResourceKey<Level>, Map<ChunkPos, HiveLocationId>> byChunk = new HashMap<>();

    private final Map<ResourceKey<Level>, Map<Long, Set<HiveLocationId>>> byCenterDim = new HashMap<>();

    private HiveConfig config = HiveConfig.defaults();

    /**
     * False until {@link #rebuildFromFactions()} has completed at least once this server session.
     * <p>
     * The registry is not persisted - it is rebuilt from BLib faction data on {@code onFactionsLoaded}. Entity
     * persistence ({@code Alien.isPersistenceRequired}) depends on this registry: a hive member is persistent because
     * the registry can place it in a hive. But on world load, entities can tick - and run their vanilla despawn check -
     * BEFORE BLib has loaded and this rebuild has run. In that window every member resolves to "no hive", reads as
     * non-persistent, and vanilla despawns it. That is the "on join, every xeno but the queen vanished" bug: the queen
     * has her own registry-independent persistence, the rank and file do not. This latch lets members hold persistent
     * through the load window until the registry is genuinely ready to answer.
     */
    private boolean hasRebuilt = false;

    private long ticksSinceLastScan = 0L;

    /** Reinforcement dispatcher fires on a coarser-than-tick cadence — every 5 seconds is plenty. */
    private static final long REINFORCEMENT_DISPATCH_INTERVAL_TICKS = 20L * 5L;

    private long ticksSinceLastDispatch = 0L;

    private long ticksSinceLastHiveSpawn = 0L;

    private HiveLocationRegistry() {}

    /**
     * Replaces the in-use config object. Phase 1 ships {@link HiveConfig#defaults()} — call this from a setup hook if
     * you want to pin custom tunables.
     */
    public void setConfig(HiveConfig config) {
        this.config = config;
    }

    public HiveConfig config() {
        return config;
    }

    /**
     * Inserts a location into all four indexes. Caller is responsible for persisting the location into its owning
     * {@link LineageFactionData}; this registry only maintains in-memory views.
     */
    public void register(HiveLocation location) {
        if (byId.containsKey(location.id())) {
            Alien.LOGGER.warn("HiveLocationRegistry.register called for already-registered id {}", location.id());
            return;
        }

        byId.put(location.id(), location);

        byLineage
            .computeIfAbsent(location.lineageFactionId(), $ -> new LinkedHashSet<>())
            .add(location.id());

        var byChunkForDim = byChunk.computeIfAbsent(location.dimension(), $ -> new HashMap<>());
        for (var chunk : location.claimedChunks()) {
            byChunkForDim.put(chunk, location.id());
        }

        addToSpatialIndex(location);
    }

    /**
     * Removes a location from every index. Doesn't touch the owning {@link LineageFactionData}.
     */
    public void unregister(HiveLocationId id) {
        var location = byId.remove(id);

        if (location == null) {
            return;
        }

        location.onUnregistered();

        var siblings = byLineage.get(location.lineageFactionId());
        if (siblings != null) {
            siblings.remove(id);
            if (siblings.isEmpty()) {
                byLineage.remove(location.lineageFactionId());
            }
        }

        var byChunkForDim = byChunk.get(location.dimension());
        if (byChunkForDim != null) {
            for (var chunk : location.claimedChunks()) {
                if (id.equals(byChunkForDim.get(chunk))) {
                    byChunkForDim.remove(chunk);
                }
            }
            if (byChunkForDim.isEmpty()) {
                byChunk.remove(location.dimension());
            }
        }

        removeFromSpatialIndex(location);
    }

    public @Nullable HiveLocation get(HiveLocationId id) {
        return byId.get(id);
    }

    public Collection<HiveLocation> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public Set<HiveLocationId> byLineage(ResourceLocation lineageFactionId) {
        return Collections.unmodifiableSet(
            byLineage.getOrDefault(lineageFactionId, Set.of())
        );
    }

    public @Nullable HiveLocation getByChunk(ResourceKey<Level> dimension, ChunkPos pos) {
        var byChunkForDim = byChunk.get(dimension);
        if (byChunkForDim == null) {
            return null;
        }

        var id = byChunkForDim.get(pos);
        return id == null ? null : byId.get(id);
    }

    /**
     * Linear-over-spatial-bucket nearest lookup. With 32-chunk buckets and ~10000 locations expected at v1 max, this
     * scans at most a few dozen candidates per call.
     */
    public @Nullable HiveLocation findNearestInDim(ResourceKey<Level> dimension, BlockPos pos) {
        var dimBuckets = byCenterDim.get(dimension);
        if (dimBuckets == null || dimBuckets.isEmpty()) {
            return null;
        }

        var queryChunk = new ChunkPos(pos);
        var queryBucketX = bucketCoord(queryChunk.x);
        var queryBucketZ = bucketCoord(queryChunk.z);

        HiveLocation nearest = null;
        var nearestDistanceSqr = Double.MAX_VALUE;

        // Expand outward by bucket rings until a candidate is found, then check
        // the next ring too (a closer center might live in an adjacent bucket).
        for (var ring = 0; ring <= 64; ring++) {
            var foundInThisRing = false;

            for (var dx = -ring; dx <= ring; dx++) {
                for (var dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }

                    var bucketKey = bucketKey(queryBucketX + dx, queryBucketZ + dz);
                    var bucket = dimBuckets.get(bucketKey);
                    if (bucket == null) {
                        continue;
                    }

                    for (var locationId : bucket) {
                        var location = byId.get(locationId);
                        if (location == null) {
                            continue;
                        }

                        var distSqr = location.centerPos().distSqr(pos);
                        if (distSqr < nearestDistanceSqr) {
                            nearestDistanceSqr = distSqr;
                            nearest = location;
                            foundInThisRing = true;
                        }
                    }
                }
            }

            // Stop one ring after first hit so a closer candidate in an
            // adjacent bucket isn't missed.
            if (nearest != null && !foundInThisRing) {
                break;
            }
        }

        return nearest;
    }

    /**
     * Returns any live location in {@code dimension} whose center is closer than {@code minimumDistanceChunks} to
     * {@code candidate}, measured in Chebyshev chunk distance. Uses the per-dimension spatial bucket index so abstract
     * settlement checks only inspect nearby location centers instead of every hive location in the world.
     */
    public @Nullable HiveLocation findTooCloseToCenter(
        ResourceKey<Level> dimension,
        ChunkPos candidate,
        int minimumDistanceChunks
    ) {
        if (minimumDistanceChunks <= 0) {
            return null;
        }

        var dimBuckets = byCenterDim.get(dimension);
        if (dimBuckets == null || dimBuckets.isEmpty()) {
            return null;
        }

        var maxOffset = minimumDistanceChunks - 1;
        var minBucketX = bucketCoord(candidate.x - maxOffset);
        var maxBucketX = bucketCoord(candidate.x + maxOffset);
        var minBucketZ = bucketCoord(candidate.z - maxOffset);
        var maxBucketZ = bucketCoord(candidate.z + maxOffset);

        for (var bucketX = minBucketX; bucketX <= maxBucketX; bucketX++) {
            for (var bucketZ = minBucketZ; bucketZ <= maxBucketZ; bucketZ++) {
                var bucket = dimBuckets.get(bucketKey(bucketX, bucketZ));
                if (bucket == null) {
                    continue;
                }

                for (var locationId : bucket) {
                    var location = byId.get(locationId);
                    if (location == null || !location.isAlive()) {
                        continue;
                    }

                    if (
                        HiveLocationSpacing.chunkDistance(new ChunkPos(location.centerPos()), candidate) < minimumDistanceChunks
                    ) {
                        return location;
                    }
                }
            }
        }

        return null;
    }

    public void onChunkClaimed(HiveLocation location, ChunkPos chunk) {
        byChunk
            .computeIfAbsent(location.dimension(), $ -> new HashMap<>())
            .put(chunk, location.id());
    }

    public void onChunkReleased(HiveLocation location, ChunkPos chunk) {
        var byChunkForDim = byChunk.get(location.dimension());
        if (byChunkForDim != null && location.id().equals(byChunkForDim.get(chunk))) {
            byChunkForDim.remove(chunk);
        }
    }

    /**
     * Walk every loaded lineage's nested locations and (re)build all four indexes from scratch. Intended for the
     * server-started callback.
     */
    /** True once the registry has been built from faction data at least once - see {@link #hasRebuilt}. */
    public boolean hasRebuilt() {
        return hasRebuilt;
    }

    public void rebuildFromFactions() {
        byId.clear();
        byLineage.clear();
        byChunk.clear();
        byCenterDim.clear();
        ticksSinceLastScan = 0L;
        ticksSinceLastDispatch = 0L;
        ticksSinceLastHiveSpawn = 0L;
        HiveLocationSlowTickTask.reset();
        hasRebuilt = true;

        var allIds = Alien.MOD.factions().getAllIds();
        var lineageIdCount = 0;
        var locationCount = 0;

        for (var factionId : allIds) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }

            lineageIdCount++;
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null) {
                Alien.LOGGER.warn("HiveLocationRegistry rebuild: lineage id {} resolves to null faction", factionId);
                continue;
            }

            var data = faction.data();
            if (data == null) {
                Alien.LOGGER.warn(
                    "HiveLocationRegistry rebuild: lineage id {} has null data — BLib may not have loaded yet",
                    factionId
                );
                continue;
            }

            if (!(data instanceof LineageFactionData lineageData)) {
                Alien.LOGGER.warn(
                    "HiveLocationRegistry rebuild: lineage id {} has data type {}, expected LineageFactionData",
                    factionId,
                    data.getClass().getName()
                );
                continue;
            }

            // Backfill the own-faction-id so reactive variant guards work on saves that predate the field.
            if (lineageData.factionId() == null) {
                lineageData.setFactionId(factionId);
            }
            com.alien.common.gameplay.hive.faction.FactionAesthetics.ensureClaimMapStyle(faction, lineageData.variant());

            // Backfill lineage path name + monotonic number.
            if (lineageData.lineageNumber() < 0) {
                var variantFaction = lineageData.parentVariantFactionId() != null
                    ? Alien.MOD.factions().get(lineageData.parentVariantFactionId())
                    : null;
                var allocated = variantFaction != null
                    && variantFaction.data() instanceof com.alien.common.gameplay.hive.faction.VariantFactionData variantData
                        ? variantData.allocateLineageNumber()
                        : 0L;
                lineageData.setLineageNumber(allocated);
                faction.setName(
                    com.alien.common.gameplay.hive.faction.FactionNaming.forLineage(lineageData.variant(), allocated)
                );
            }

            var locations = lineageData.locationsById();
            Alien.LOGGER.info(
                "HiveLocationRegistry rebuild: lineage {} has {} nested locations to register",
                factionId,
                locations.size()
            );

            for (var location : locations.values()) {
                register(location);
                locationCount++;

                HiveLocationFactionProvisioner.ensure(location, lineageData);
            }
        }

        // Backfill variant faction names for saves predating FactionNaming.
        for (var variant : com.alien.common.model.alien.variant.AlienVariant.VALUES) {
            var variantId = com.alien.common.gameplay.hive.id.VariantIds.of(variant);
            var variantFaction = Alien.MOD.factions().get(variantId);
            if (variantFaction == null) {
                continue;
            }
            var expectedName = com.alien.common.gameplay.hive.faction.FactionNaming.forVariant(variant);
            if (!expectedName.equals(variantFaction.name())) {
                variantFaction.setName(expectedName);
            }
            com.alien.common.gameplay.hive.faction.FactionAesthetics.ensureClaimMapStyle(variantFaction, variant);
        }

        // Self-heal: delete any LocationFactionData whose backing HiveLocation is missing. These orphans accumulate
        // when older code paths removed a HiveLocation without deleting its BLib faction; left in place they show up
        // in the FactionBrowser and make the inspector hang on "(loading…)" since the request handler can't resolve
        // them. (Fixed at the source in LocationRemovalHelper.remove(); this is the catch-up pass for existing saves.)
        var orphanLocationFactions = 0;
        for (var factionId : new java.util.ArrayList<>(allIds)) {
            if (!HiveLocationIds.isHiveLocationId(factionId)) {
                continue;
            }
            if (byId.containsKey(HiveLocationId.of(factionId))) {
                continue;
            }
            Alien.LOGGER.warn(
                "HiveLocationRegistry rebuild: deleting orphan location faction {} (no backing HiveLocation)",
                factionId
            );
            Alien.MOD.factions().remove(factionId);
            orphanLocationFactions++;
        }

        Alien.LOGGER.info(
            "HiveLocationRegistry rebuilt: scanned {} factions, found {} lineage ids, registered {} locations across "
                + "{} lineages, pruned {} orphan location factions",
            allIds.size(),
            lineageIdCount,
            locationCount,
            byLineage.size(),
            orphanLocationFactions
        );
    }

    public void repairTerritoryClaims(MinecraftServer server) {
        var reconciledChunks = 0;

        for (var location : byId.values()) {
            var level = server.getLevel(location.dimension());
            if (level == null) {
                continue;
            }

            // Load-time healing: re-claim any built-structure chunk whose claim was lost (the old disconnection
            // pruner amputated room claims in existing worlds - this repairs those saves on their next load). Runs
            // BEFORE the sync loop below so the reclaimed chunks are in claimedChunks() when it iterates, and so
            // reclaim's own additions never mutate the set mid-iteration.
            com.alien.common.gameplay.hive.growth.HiveLocationClaims.reclaimStructureChunks(
                level,
                location,
                level.getGameTime()
            );

            for (var chunk : location.claimedChunks()) {
                // DIAGNOSTIC (queen bug 2 - "relog makes the claim contested"). syncTerritoryClaim strips only
                // LINEAGE- and VARIANT-tier claims before adding this location's own, so any OTHER location-tier
                // claimant already sitting on the chunk survives and the chunk ends up with two -> contested.
                // This names both ids at the moment it happens. Remove once the culprit is identified.
                var priorClaimants = Alien.MOD.territory().getClaimants(level, chunk);
                if (
                    priorClaimants.size() > 1
                        || (priorClaimants.size() == 1 && !priorClaimants.contains(location.id().value()))
                ) {
                    Alien.LOGGER.warn(
                        "CLAIM-DIAG repair {} chunk {} incoming={} priorClaimants={} (foreign={})",
                        level.dimension().location(),
                        chunk,
                        location.id().value(),
                        priorClaimants,
                        priorClaimants.stream()
                            .filter(id -> !id.equals(location.id().value()))
                            .map(ResourceLocation::toString)
                            .toList()
                    );
                }

                HiveLocationClaims.syncTerritoryClaim(level, location, chunk);

                var afterClaimants = Alien.MOD.territory().getClaimants(level, chunk);
                if (afterClaimants.size() > 1) {
                    Alien.LOGGER.warn(
                        "CLAIM-DIAG repair LEFT {} CLAIMANTS on {} chunk {}: {} - this chunk is now CONTESTED",
                        afterClaimants.size(),
                        level.dimension().location(),
                        chunk,
                        afterClaimants
                    );
                }

                reconciledChunks++;
            }
        }

        if (reconciledChunks > 0) {
            Alien.LOGGER.info(
                "HiveLocationRegistry: reconciled {} BLib territory claims under location faction ids",
                reconciledChunks
            );
        }
    }

    /**
     * Per-server-tick entry point. Iterates every registered location across every dimension (fixes the legacy
     * Overworld-only bug, see {@code HIVE_SYSTEM_ANALYSIS.md} § 9.1.2), dispatches loaded-location fast work, samples
     * bounded slow-location work, and periodically invokes coarse lineage lifecycle scans.
     */
    public void tick(MinecraftServer server) {
        if (!byId.isEmpty()) {
            for (var location : new ArrayList<>(byId.values())) {
                if (!location.isAlive()) {
                    continue;
                }

                location.incrementAge();
                HiveLocationLoadedTickTask.run(server, location);
            }
        }

        // Phase 8: convoy travel + arrival every tick. Sparse — most lineages have zero convoys.
        LineageConvoyTickTask.run(server);

        HiveLocationSlowTickTask.run(server);

        // Phase 10: empress emergence per-tick advancement. Cheap when no queens are emerging.
        com.alien.common.gameplay.hive.empress.EmpressEmergenceRitual.tick(server);

        // Location + lineage death checks run every tick — no throttling. See HIVE_REDESIGN_02_FACTION_LIFECYCLES.
        // Order matters: location dormancy first so per-location rules fire before lineage-empty cleanup picks up
        // newly-zero-location lineages this tick.
        com.alien.common.gameplay.hive.lifecycle.LocationDormancyTask.scanAll(server);
        com.alien.common.gameplay.hive.lifecycle.LineageDeathHandler.scanAndKill(server);

        // An empress collecting on a hive lost to a nuke. Cheap when nothing is pending, which is almost always.
        com.alien.common.gameplay.hive.lifecycle.NukeRetribution.tick(server);

        // Irradiated ground, walls and cargo leaking into whoever is near them. Throttled to once a second inside.
        com.alien.common.gameplay.radiation.IrradiatedExposureTask.tick(server);

        // A converted hive turning its own walls irradiated, a budget of blocks at a time.
        com.alien.common.gameplay.hive.lifecycle.IrradiatedConversionSweep.tick(server);

        // A newborn irradiated hive coming for everyone who made it, two days on.
        com.alien.common.gameplay.hive.lifecycle.IrradiatedBirthRaid.tick(server);

        // § 13 economy: jelly production then balance buys. Per-tick, no throttling.
        com.alien.common.gameplay.hive.economy.JellyProduction.scanAndProduce(server);
        com.alien.common.gameplay.hive.economy.HiveBalanceTask.scanAll(server);

        ticksSinceLastHiveSpawn++;
        if (ticksSinceLastHiveSpawn >= config.hiveSpawnerIntervalTicks()) {
            ticksSinceLastHiveSpawn = 0L;
            com.alien.common.gameplay.hive.spawning.HiveLoadedSpawner.scanAndSpawn(server);
        }

        ticksSinceLastDispatch++;
        if (ticksSinceLastDispatch >= REINFORCEMENT_DISPATCH_INTERVAL_TICKS) {
            ticksSinceLastDispatch = 0L;
            com.alien.common.gameplay.hive.convoy.ReinforcementDispatcher.scanAndDispatch(server);
            com.alien.common.gameplay.hive.convoy.MigrationDispatch.scanAndDispatch(server);
            com.alien.common.gameplay.hive.convoy.RaidDispatch.scanAndDispatch(server);
            com.alien.common.gameplay.hive.empress.EmpressEmergenceTask.scanAndStart(server);
            // Corridor membership BEFORE influence: the network decides which lineages carry her empressId, and
            // influence is derived from that id. Reversed, a lineage severed this sweep would keep her buffs for
            // one more pass.
            com.alien.common.gameplay.hive.empress.EmpressNetworkSync.syncAll(server);
            // Re-assert the router's memory-only empress-influence set. Reconciled rather than pushed, so it
            // survives restarts and needs no hook on every event that could change the answer.
            com.alien.common.gameplay.hive.empress.EmpressInfluenceSync.syncAll(server);
        }

        if (server.overworld().getGameTime() % Math.max(1L, config.contestTickWindow()) == 0L) {
            com.alien.common.gameplay.hive.war.AlienTerritoryWarSystem.scanAndApply(server);
        }

        ticksSinceLastScan++;
        if (ticksSinceLastScan >= config.lineageScanIntervalTicks()) {
            ticksSinceLastScan = 0L;
            com.alien.common.gameplay.hive.growth.PopulationPressureDecayTask.scanAll(server);
            // Phase 11: full lifecycle dispatch (dormancy, contests, lineage death) layered on top of
            // variant-mismatch invariants.
            com.alien.common.gameplay.hive.faction.LineageInvariantTask.scanAllWithLifecycle(server);
        }
    }

    /**
     * Cross-tier sanity check. Logs (does not crash) any inconsistency:
     * <ul>
     * <li>Orphan lineages — registered locations whose owning lineage faction no longer exists in BLib. Their locations
     * are unregistered.</li>
     * <li>Orphan location factions — BLib {@code LocationFactionData} factions whose backing {@link HiveLocation} is
     * missing. Deleted so the FactionBrowser stops listing them and inspector requests don't hang.</li>
     * <li>{@code byChunk} drift — every chunk in every location's {@code claimedChunks} should be reflected in the
     * per-dim chunk index. Missing entries are repaired.</li>
     * <li>{@code byCenterDim} drift — every location should be in the per-dim spatial bucket. Missing entries are
     * repaired.</li>
     * </ul>
     * <p>
     * Per {@code HIVE_REDESIGN_11_IMPLEMENTATION.md} § 2.
     */
    public void validate() {
        var orphanLineages = new HashSet<ResourceLocation>();

        for (var entry : byLineage.entrySet()) {
            var lineageId = entry.getKey();
            if (!Alien.MOD.factions().exists(lineageId)) {
                orphanLineages.add(lineageId);
            }
        }

        for (var lineageId : orphanLineages) {
            // REPAIR FIRST, delete last: a hard crash can roll the BLib faction store and the hive-location
            // store back to different moments, leaving real, structure-bearing hives "orphaned". Deleting them
            // amplifies a one-tick save race into permanent hive loss (a queen left seated on her ovipositor
            // with no claim under her). If any location of the lineage still has substance (structure or
            // claims), the missing lineage faction is RECREATED instead; only true husks are removed.
            boolean substance = false;
            for (var orphan : byLineage.get(lineageId)) {
                var orphanLocation = byId.get(orphan);
                if (
                    orphanLocation != null
                        && (!orphanLocation.structurePieceByChunk().isEmpty() || !orphanLocation.claimedChunks().isEmpty())
                ) {
                    substance = true;
                    break;
                }
            }
            if (substance) {
                Alien.LOGGER.warn(
                    "HiveLocationRegistry.validate: lineage {} missing but its locations still have substance - recreating the lineage faction.",
                    lineageId
                );
                Alien.MOD.factions().getOrCreate(lineageId, com.alien.common.registry.init.AlienFactionDataTypes.LINEAGE);
                continue;
            }
            Alien.LOGGER.warn(
                "HiveLocationRegistry.validate: lineage {} has {} orphaned husk locations; cleaning up.",
                lineageId,
                byLineage.get(lineageId).size()
            );
            for (var orphan : Set.copyOf(byLineage.get(lineageId))) {
                unregister(orphan);
            }
        }

        var orphanLocationFactions = 0;
        for (var factionId : new java.util.ArrayList<>(Alien.MOD.factions().getAllIds())) {
            if (!HiveLocationIds.isHiveLocationId(factionId)) {
                continue;
            }
            if (byId.containsKey(HiveLocationId.of(factionId))) {
                continue;
            }
            Alien.LOGGER.warn(
                "HiveLocationRegistry.validate: deleting orphan location faction {} (no backing HiveLocation)",
                factionId
            );
            Alien.MOD.factions().remove(factionId);
            orphanLocationFactions++;
        }
        if (orphanLocationFactions > 0) {
            Alien.LOGGER.warn(
                "HiveLocationRegistry.validate: pruned {} orphan location factions",
                orphanLocationFactions
            );
        }

        var chunkRepairs = 0;
        var spatialRepairs = 0;

        for (var location : byId.values()) {
            var lineageFaction = Alien.MOD.factions().get(location.lineageFactionId());
            if (lineageFaction != null && lineageFaction.data() instanceof LineageFactionData lineageData) {
                HiveLocationFactionProvisioner.ensure(location, lineageData);
            }

            var byChunkForDim = byChunk.get(location.dimension());
            for (var chunk : location.claimedChunks()) {
                if (byChunkForDim == null || !location.id().equals(byChunkForDim.get(chunk))) {
                    if (byChunkForDim == null) {
                        byChunkForDim = byChunk.computeIfAbsent(location.dimension(), $ -> new HashMap<>());
                    }
                    byChunkForDim.put(chunk, location.id());
                    chunkRepairs++;
                }
            }

            var dimBuckets = byCenterDim.get(location.dimension());
            var bucketX = bucketCoord(location.centerPos().getX() >> 4);
            var bucketZ = bucketCoord(location.centerPos().getZ() >> 4);
            var key = bucketKey(bucketX, bucketZ);
            var bucket = dimBuckets == null ? null : dimBuckets.get(key);

            if (bucket == null || !bucket.contains(location.id())) {
                addToSpatialIndex(location);
                spatialRepairs++;
            }
        }

        if (chunkRepairs > 0 || spatialRepairs > 0) {
            Alien.LOGGER.warn(
                "HiveLocationRegistry.validate: repaired {} byChunk entries and {} byCenterDim entries.",
                chunkRepairs,
                spatialRepairs
            );
        }

        Alien.LOGGER.info(
            "HiveLocationRegistry.validate: {} locations across {} lineages OK.",
            byId.size(),
            byLineage.size()
        );
    }

    public void clear() {
        for (var location : byId.values()) {
            location.onUnregistered();
        }
        byId.clear();
        byLineage.clear();
        byChunk.clear();
        byCenterDim.clear();
        ticksSinceLastScan = 0L;
        ticksSinceLastDispatch = 0L;
        ticksSinceLastHiveSpawn = 0L;
        hasRebuilt = false;
    }

    public int locationCount() {
        return byId.size();
    }

    public int lineageCount() {
        return byLineage.size();
    }

    private void addToSpatialIndex(HiveLocation location) {
        var bucketX = bucketCoord(location.centerPos().getX() >> 4);
        var bucketZ = bucketCoord(location.centerPos().getZ() >> 4);
        var key = bucketKey(bucketX, bucketZ);

        byCenterDim
            .computeIfAbsent(location.dimension(), $ -> new HashMap<>())
            .computeIfAbsent(key, $ -> new LinkedHashSet<>())
            .add(location.id());
    }

    private void removeFromSpatialIndex(HiveLocation location) {
        var dimBuckets = byCenterDim.get(location.dimension());
        if (dimBuckets == null) {
            return;
        }

        var bucketX = bucketCoord(location.centerPos().getX() >> 4);
        var bucketZ = bucketCoord(location.centerPos().getZ() >> 4);
        var key = bucketKey(bucketX, bucketZ);

        var bucket = dimBuckets.get(key);
        if (bucket == null) {
            return;
        }

        bucket.remove(location.id());
        if (bucket.isEmpty()) {
            dimBuckets.remove(key);
        }
        if (dimBuckets.isEmpty()) {
            byCenterDim.remove(location.dimension());
        }
    }

    private static int bucketCoord(int chunkCoord) {
        return Math.floorDiv(chunkCoord, SPATIAL_BUCKET_CHUNKS);
    }

    private static long bucketKey(int x, int z) {
        return (((long) x) << 32) | (z & 0xFFFFFFFFL);
    }
}
