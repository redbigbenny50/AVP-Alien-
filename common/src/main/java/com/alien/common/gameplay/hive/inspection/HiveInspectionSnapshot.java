package com.alien.common.gameplay.hive.inspection;

import com.alien.Alien;
import com.alien.common.gameplay.hive.config.HiveConfig;
import com.alien.common.gameplay.hive.config.HiveConfigSchema;
import com.alien.common.gameplay.hive.convoy.Convoy;
import com.alien.common.gameplay.hive.economy.CastePopulation;
import com.alien.common.gameplay.hive.economy.JellyProduction;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.faction.VariantFactionData;
import com.alien.common.gameplay.hive.growth.BiomassIncome;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.blib.api.common.entity.v1.EntityReserves;
import com.blib.api.common.faction.v1.Faction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * Server-side builder for the hive inspection panel's data feed. Produces a {@link CompoundTag} per kind of selected
 * faction so the wire format is one shape (a tag) regardless of whether the user clicked a location, lineage, or
 * variant row in the FactionBrowser. The {@code /hive inspect_location} command and the
 * {@code S2CHiveInspectionPayload} both pull from these builders, so anything the text command surfaces is also
 * available in the panel and the two can't drift apart.
 * <p>
 * Schema keys are declared as constants so the client-side reader can pull fields by name; nested lists (per-caste
 * counts, claimed chunks, owned locations) use {@link ListTag} of compounds, each with the same shape.
 */
public final class HiveInspectionSnapshot {

    public static final String KIND_LOCATION = "location";

    public static final String KIND_LINEAGE = "lineage";

    public static final String KIND_VARIANT = "variant";

    // Shared keys
    public static final String K_FACTION_ID = "FactionId";

    public static final String K_DISPLAY_NAME = "DisplayName";

    public static final String K_DIMENSION = "Dimension";

    public static final String K_AGE_TICKS = "AgeTicks";

    public static final String K_REMOVAL_REASON = "RemovalReason";

    public static final String K_HIVE_CONFIG = "HiveConfig";

    // Location keys
    public static final String K_LOCATION_ID = "LocationId";

    public static final String K_LOCATION_NUMBER = "LocationNumber";

    public static final String K_LINEAGE_FACTION_ID = "LineageFactionId";

    public static final String K_CENTER_X = "CenterX";

    public static final String K_CENTER_Y = "CenterY";

    public static final String K_CENTER_Z = "CenterZ";

    public static final String K_FOUNDER_ID = "FounderId";

    public static final String K_BIOMASS = "Biomass";

    public static final String K_BIOMASS_CAP = "BiomassCap";

    public static final String K_NEXT_CLAIM_COST = "NextClaimCost";

    public static final String K_ROYAL_JELLY = "RoyalJelly";

    public static final String K_ROYAL_JELLY_CAP = "RoyalJellyCap";

    public static final String K_ROYAL_JELLY_ACC = "RoyalJellyAcc";

    public static final String K_SCOURGE_JELLY = "ScourgeJelly";

    public static final String K_SCOURGE_JELLY_CAP = "ScourgeJellyCap";

    public static final String K_SCOURGE_QUEEN_ACC = "ScourgeQueenAcc";

    public static final String K_SCOURGE_HARBINGER_ACC = "ScourgeHarbingerAcc";

    public static final String K_CLAIMED_CHUNKS = "ClaimedChunks";

    public static final String K_CHUNKS_LOADED = "ChunksLoaded";

    public static final String K_DECORATED_CHUNKS = "DecoratedChunks";

    public static final String K_PEAK_XENO = "PeakXeno";

    public static final String K_LAST_GROWTH_TICK = "LastGrowthTick";

    public static final String K_NO_CONTACT_TICKS = "NoContactTicks";

    public static final String K_NO_CONTACT_CAP = "NoContactCap";

    public static final String K_EVACUATING_TICKS = "EvacuatingTicks";

    public static final String K_LEADER_ID = "LeaderId";

    public static final String K_TOTAL_POP = "TotalPop";

    public static final String K_POP_CAP = "PopCap";

    public static final String K_PER_CASTE = "PerCaste";

    public static final String K_LOADED_BY_TYPE = "LoadedByType";

    public static final String K_RESERVES_BY_TYPE = "ReservesByType";

    public static final String K_LINEAGE_MEMBER_COUNT = "LineageMembers";

    public static final String K_LOCATION_FACTION_MEMBER_COUNT = "LocationFactionMembers";

    // Lineage keys
    public static final String K_VARIANT_NAME = "VariantName";

    public static final String K_LINEAGE_NUMBER = "LineageNumber";

    public static final String K_NEXT_LOCATION_NUMBER = "NextLocationNumber";

    public static final String K_EMPRESS_ID = "EmpressId";

    public static final String K_PENDING_EMPRESS = "PendingEmpress";

    public static final String K_LINEAGE_MEMBER_TOTAL = "LineageMemberTotal";

    public static final String K_LOCATION_COUNT = "LocationCount";

    public static final String K_LOCAL_RESERVE_TOTAL = "LocalReserveTotal";

    public static final String K_LOADED_MEMBER_TOTAL = "LoadedMemberTotal";

    public static final String K_CONVOY_COUNT = "ConvoyCount";

    public static final String K_CONVOY_MEMBER_TOTAL = "ConvoyMemberTotal";

    public static final String K_CONVOYS = "Convoys";

    public static final String K_LOCATIONS = "Locations";

    public static final String K_AGG_BIOMASS = "AggBiomass";

    public static final String K_AGG_ROYAL_JELLY = "AggRoyalJelly";

    public static final String K_AGG_SCOURGE_JELLY = "AggScourgeJelly";

    public static final String K_AGG_TOTAL_POP = "AggTotalPop";

    public static final String K_AGG_POP_CAP = "AggPopCap";

    public static final String K_AGG_LOCAL_RESERVES = "AggLocalReserves";

    public static final String K_AGG_LOADED_MEMBERS = "AggLoadedMembers";

    public static final String K_AGG_LOCATION_MEMBERS = "AggLocationMembers";

    public static final String K_AGG_LINEAGE_MEMBERS = "AggLineageMembers";

    public static final String K_AGG_CONVOYS = "AggConvoys";

    public static final String K_AGG_CONVOY_MEMBERS = "AggConvoyMembers";

    public static final String K_AGG_CLAIMED_CHUNKS = "AggClaimedChunks";

    public static final String K_AGG_CHUNKS_LOADED = "AggChunksLoaded";

    public static final String K_AGG_LOCATIONS = "AggLocations";

    public static final String K_SPREAD_CURRENT_TICK = "SpreadCurrentTick";

    public static final String K_SPREAD_COOLDOWN_TICKS = "SpreadCooldownTicks";

    public static final String K_SPREAD_COOLDOWN_REMAINING_TICKS = "SpreadCooldownRemainingTicks";

    public static final String K_SPREAD_COOLDOWN_ELIGIBLE = "SpreadCooldownEligible";

    public static final String K_SPREAD_LAST_SUCCESS_TICK = "SpreadLastSuccessTick";

    public static final String K_SPREAD_HAS_LAST_SUCCESS = "SpreadHasLastSuccess";

    public static final String K_SPREAD_LAST_SUCCESS_AGE_TICKS = "SpreadLastSuccessAgeTicks";

    public static final String K_SPREAD_NEXT_ELIGIBLE_TICK = "SpreadNextEligibleTick";

    public static final String K_SPREAD_MAX_LOCATIONS = "SpreadMaxLocations";

    public static final String K_SPREAD_MAX_RADIUS_CHUNKS = "SpreadMaxRadiusChunks";

    public static final String K_SPREAD_MIN_DISTANCE_CHUNKS = "SpreadMinDistanceChunks";

    public static final String K_SPREAD_LAST_ATTEMPT_TICK = "SpreadLastAttemptTick";

    public static final String K_SPREAD_HAS_LAST_ATTEMPT = "SpreadHasLastAttempt";

    public static final String K_SPREAD_LAST_ATTEMPT_AGE_TICKS = "SpreadLastAttemptAgeTicks";

    public static final String K_SPREAD_LAST_ATTEMPT_RESULT = "SpreadLastAttemptResult";

    public static final String K_SPREAD_LAST_ATTEMPT_DETAIL = "SpreadLastAttemptDetail";

    public static final String K_SPREAD_LAST_ATTEMPT_CREATED_LOCATION_ID = "SpreadLastAttemptCreatedLocationId";

    public static final String K_SPREAD_LAST_ATTEMPT_CANDIDATE_CHUNK_X = "SpreadLastAttemptCandidateChunkX";

    public static final String K_SPREAD_LAST_ATTEMPT_CANDIDATE_CHUNK_Z = "SpreadLastAttemptCandidateChunkZ";

    // Variant keys
    public static final String K_NEXT_LINEAGE_NUMBER = "NextLineageNumber";

    public static final String K_LINEAGES = "Lineages";

    public static final String K_VARIANT_MEMBERS = "VariantMembers";

    public static final String K_QUEEN_MOTHERS = "QueenMothers";

    // Per-entry keys (used inside nested lists)
    public static final String K_KEY = "Key";

    public static final String K_VALUE = "Value";

    public static final String K_UUID = "Uuid";

    public static final String K_LOADED = "Loaded";

    public static final String K_TYPE = "Type";

    public static final String K_SOURCE_LOCATION_ID = "SourceLocationId";

    public static final String K_DESTINATION_LOCATION_ID = "DestinationLocationId";

    public static final String K_TARGET_PLAYER_ID = "TargetPlayerId";

    public static final String K_CURRENT_X = "CurrentX";

    public static final String K_CURRENT_Y = "CurrentY";

    public static final String K_CURRENT_Z = "CurrentZ";

    public static final String K_DEST_X = "DestinationX";

    public static final String K_DEST_Y = "DestinationY";

    public static final String K_DEST_Z = "DestinationZ";

    public static final String K_BIOMASS_PAYLOAD = "BiomassPayload";

    public static final String K_CARRIES_EMPRESS = "CarriesEmpress";

    public static final String K_DISPATCHED_TICK = "DispatchedTick";

    public static final String K_EXPIRES_AT_TICK = "ExpiresAtTick";

    private HiveInspectionSnapshot() {}

    /** Build a snapshot for a single hive location. Mirrors what {@code /hive inspect_location} prints. */
    public static CompoundTag buildLocation(HiveLocation location, @Nullable MinecraftServer server) {
        var tag = new CompoundTag();
        var config = HiveLocationRegistry.INSTANCE.config();
        var locationFactionId = location.id().value();

        putConfig(tag, config);

        tag.putString(K_FACTION_ID, locationFactionId.toString());
        tag.putString(K_LOCATION_ID, location.id().value().toString());
        tag.putLong(K_LOCATION_NUMBER, location.locationNumber());
        tag.putString(K_LINEAGE_FACTION_ID, location.lineageFactionId().toString());
        tag.putString(K_DIMENSION, location.dimension().location().toString());

        var center = location.centerPos();
        tag.putInt(K_CENTER_X, center.getX());
        tag.putInt(K_CENTER_Y, center.getY());
        tag.putInt(K_CENTER_Z, center.getZ());

        if (location.founderId() != null) {
            tag.putUUID(K_FOUNDER_ID, location.founderId());
        }

        tag.putLong(K_AGE_TICKS, location.ageInTicks());
        tag.putInt(K_BIOMASS, location.biomass());
        tag.putInt(K_BIOMASS_CAP, BiomassIncome.biomassCap(location, config));
        tag.putInt(K_NEXT_CLAIM_COST, BiomassIncome.claimCost(location, config));

        tag.putInt(K_ROYAL_JELLY, location.royalJelly());
        tag.putInt(K_ROYAL_JELLY_CAP, JellyProduction.royalJellyCap(location));
        tag.putLong(K_ROYAL_JELLY_ACC, location.royalJellyAccumulator());

        tag.putInt(K_SCOURGE_JELLY, location.scourgeJelly());
        tag.putInt(K_SCOURGE_JELLY_CAP, JellyProduction.scourgeJellyCap(location));
        tag.putLong(K_SCOURGE_QUEEN_ACC, location.queenScourgeAccumulator());
        tag.putLong(K_SCOURGE_HARBINGER_ACC, location.harbingerScourgeAccumulator());

        var claimedChunks = location.claimedChunks();
        tag.putInt(K_CLAIMED_CHUNKS, claimedChunks.size());
        tag.putInt(K_DECORATED_CHUNKS, location.decoratedChunks().size());
        var serverLevel = server != null ? server.getLevel(location.dimension()) : null;
        var chunksLoaded = countLoadedChunks(location, serverLevel);
        tag.putInt(K_CHUNKS_LOADED, chunksLoaded);

        tag.putInt(K_PEAK_XENO, location.peakXenomorphCount());
        tag.putLong(K_LAST_GROWTH_TICK, location.lastGrowthTick());
        tag.putLong(K_NO_CONTACT_TICKS, location.noContactTicksAccrued());
        tag.putLong(K_NO_CONTACT_CAP, config.locationMaxNoContactTicks());
        tag.putLong(K_EVACUATING_TICKS, location.evacuatingRemainingTicks());

        var leaderId = location.leadership().getLeaderIdOrNull();
        if (leaderId != null) {
            tag.putUUID(K_LEADER_ID, leaderId);
        }

        var totalPop = CastePopulation.totalTrackedPopulation(location);
        var popCap = config.populationPerChunk() * Math.max(1, claimedChunks.size());
        tag.putInt(K_TOTAL_POP, totalPop);
        tag.putInt(K_POP_CAP, popCap);

        var perCaste = CastePopulation.popByCaste(location);
        var perCasteList = new ListTag();
        for (var entry : perCaste.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            var row = new CompoundTag();
            row.putString(K_KEY, entry.getKey().location().toString());
            row.putInt(K_VALUE, entry.getValue());
            perCasteList.add(row);
        }
        tag.put(K_PER_CASTE, perCasteList);

        var loadedList = new ListTag();
        for (var entry : location.loadedMembersByType().entrySet()) {
            var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entry.getKey());
            var row = new CompoundTag();
            row.putString(K_KEY, typeId.toString());
            row.putInt(K_VALUE, entry.getValue().size());
            loadedList.add(row);
        }
        tag.put(K_LOADED_BY_TYPE, loadedList);

        tag.put(K_RESERVES_BY_TYPE, reserveRows(location.localReserves().underlying()));

        var locationFaction = Alien.MOD.factions().get(locationFactionId);
        var locationFactionMembers = locationFaction != null ? locationFaction.membership().getMembers().size() : 0;
        tag.putInt(K_LOCATION_FACTION_MEMBER_COUNT, locationFactionMembers);

        var lineageFaction = Alien.MOD.factions().get(location.lineageFactionId());
        var lineageMembers = lineageFaction != null ? lineageFaction.membership().getMembers().size() : 0;
        tag.putInt(K_LINEAGE_MEMBER_COUNT, lineageMembers);
        var lineageData = lineageFaction != null && lineageFaction.data() instanceof LineageFactionData lineage ? lineage : null;
        if (lineageData != null) {
            tag.putInt(K_LOCATION_COUNT, lineageData.locationsById().size());
        }
        putSpreadDiagnostics(tag, location, lineageData, config, server);

        if (location.removalReason() != null) {
            tag.putString(K_REMOVAL_REASON, location.removalReason().typeKind());
        }

        return tag;
    }

    public static CompoundTag buildMissingLocation(ResourceLocation factionId, String reason) {
        var tag = new CompoundTag();
        putConfig(tag, HiveLocationRegistry.INSTANCE.config());
        tag.putString(K_FACTION_ID, factionId.toString());
        tag.putString(K_LOCATION_ID, factionId.toString());
        tag.putString(K_REMOVAL_REASON, reason);
        return tag;
    }

    /** Build a snapshot for a lineage faction. Renders an aggregate over its owned locations. */
    public static CompoundTag buildLineage(Faction<LineageFactionData> faction, @Nullable MinecraftServer server) {
        var tag = new CompoundTag();
        var data = faction.data();
        if (data == null) {
            return tag;
        }
        var config = HiveLocationRegistry.INSTANCE.config();
        var factionId = faction.id();

        putConfig(tag, config);

        tag.putString(K_FACTION_ID, factionId.toString());
        tag.putString(K_DISPLAY_NAME, faction.name());
        tag.putString(K_VARIANT_NAME, data.variant().name());
        tag.putString(K_DIMENSION, data.dimension().location().toString());
        tag.putLong(K_AGE_TICKS, data.ageInTicks());
        tag.putLong(K_LINEAGE_NUMBER, data.lineageNumber());
        tag.putLong(K_NEXT_LOCATION_NUMBER, data.nextLocationNumber());
        if (data.founderId() != null) {
            tag.putUUID(K_FOUNDER_ID, data.founderId());
        }
        if (data.empressId() != null) {
            tag.putUUID(K_EMPRESS_ID, data.empressId());
        }
        tag.putBoolean(K_PENDING_EMPRESS, data.pendingEmpressEmergence());

        var memberCount = faction.membership().getMembers().size();
        tag.putInt(K_LINEAGE_MEMBER_TOTAL, memberCount);
        tag.putInt(K_LOCATION_COUNT, data.locationsById().size());
        tag.putInt(K_CONVOY_COUNT, data.convoys().size());
        tag.putInt(K_CONVOY_MEMBER_TOTAL, convoyMemberTotal(data));
        tag.put(K_CONVOYS, convoyRows(data));

        long aggBiomass = 0L;
        long aggRoyalJelly = 0L;
        long aggScourgeJelly = 0L;
        long aggPop = 0L;
        long aggPopCap = 0L;
        long aggLocalReserves = 0L;
        long aggLoadedMembers = 0L;
        long aggLocationMembers = 0L;
        long aggClaimedChunks = 0L;
        long aggChunksLoaded = 0L;

        var locationsList = new ListTag();
        for (var location : data.locationsById().values()) {
            aggBiomass += location.biomass();
            aggRoyalJelly += location.royalJelly();
            aggScourgeJelly += location.scourgeJelly();
            var locTotalPop = CastePopulation.totalTrackedPopulation(location);
            var locPopCap = config.populationPerChunk() * Math.max(1, location.claimedChunks().size());
            var locLocalReserves = location.localReserves().getReliableCount();
            var locLoadedMembers = loadedMemberTotal(location);
            var locLocationMembers = locationFactionMemberCount(location);
            var locServerLevel = server != null ? server.getLevel(location.dimension()) : null;
            var locChunksLoaded = countLoadedChunks(location, locServerLevel);
            aggPop += locTotalPop;
            aggPopCap += locPopCap;
            aggLocalReserves += locLocalReserves;
            aggLoadedMembers += locLoadedMembers;
            aggLocationMembers += locLocationMembers;
            aggClaimedChunks += location.claimedChunks().size();
            aggChunksLoaded += locChunksLoaded;

            var row = new CompoundTag();
            row.putString(K_LOCATION_ID, location.id().value().toString());
            row.putLong(K_LOCATION_NUMBER, location.locationNumber());
            row.putString(K_DIMENSION, location.dimension().location().toString());
            row.putInt(K_CENTER_X, location.centerPos().getX());
            row.putInt(K_CENTER_Y, location.centerPos().getY());
            row.putInt(K_CENTER_Z, location.centerPos().getZ());
            row.putLong(K_AGE_TICKS, location.ageInTicks());
            row.putInt(K_BIOMASS, location.biomass());
            row.putInt(K_ROYAL_JELLY, location.royalJelly());
            row.putInt(K_SCOURGE_JELLY, location.scourgeJelly());
            row.putInt(K_CLAIMED_CHUNKS, location.claimedChunks().size());
            row.putInt(K_CHUNKS_LOADED, locChunksLoaded);
            row.putInt(K_TOTAL_POP, locTotalPop);
            row.putInt(K_POP_CAP, locPopCap);
            row.putInt(K_LOCAL_RESERVE_TOTAL, locLocalReserves);
            row.putInt(K_LOADED_MEMBER_TOTAL, locLoadedMembers);
            row.putInt(K_LOCATION_FACTION_MEMBER_COUNT, locLocationMembers);
            row.putLong(K_NO_CONTACT_TICKS, location.noContactTicksAccrued());
            row.putLong(K_EVACUATING_TICKS, location.evacuatingRemainingTicks());
            var leaderId = location.leadership().getLeaderIdOrNull();
            if (leaderId != null) {
                row.putUUID(K_LEADER_ID, leaderId);
            }
            if (location.removalReason() != null) {
                row.putString(K_REMOVAL_REASON, location.removalReason().typeKind());
            }
            putSpreadDiagnostics(row, location, data, config, server);
            locationsList.add(row);
        }
        tag.put(K_LOCATIONS, locationsList);

        tag.putLong(K_AGG_BIOMASS, aggBiomass);
        tag.putLong(K_AGG_ROYAL_JELLY, aggRoyalJelly);
        tag.putLong(K_AGG_SCOURGE_JELLY, aggScourgeJelly);
        tag.putLong(K_AGG_TOTAL_POP, aggPop);
        tag.putLong(K_AGG_POP_CAP, aggPopCap);
        tag.putLong(K_AGG_LOCAL_RESERVES, aggLocalReserves);
        tag.putLong(K_AGG_LOADED_MEMBERS, aggLoadedMembers);
        tag.putLong(K_AGG_LOCATION_MEMBERS, aggLocationMembers);
        tag.putLong(K_AGG_CLAIMED_CHUNKS, aggClaimedChunks);
        tag.putLong(K_AGG_CHUNKS_LOADED, aggChunksLoaded);
        tag.putLong(K_AGG_LOCATIONS, data.locationsById().size());

        if (data.removalReason() != null) {
            tag.putString(K_REMOVAL_REASON, data.removalReason().typeKind());
        }

        return tag;
    }

    private static void putSpreadDiagnostics(
        CompoundTag tag,
        HiveLocation location,
        @Nullable LineageFactionData lineage,
        HiveConfig config,
        @Nullable MinecraftServer server
    ) {
        var level = server != null ? server.getLevel(location.dimension()) : null;
        var currentTick = level != null ? level.getGameTime() : -1L;
        var lastSuccessTick = location.lastAbstractSpreadTick();
        var hasLastSuccess = lastSuccessTick > 0L;
        var cooldownRemaining = spreadCooldownRemaining(location, currentTick, config);
        var nextEligibleTick = currentTick >= 0L && cooldownRemaining >= 0L ? currentTick + cooldownRemaining : -1L;

        tag.putLong(K_SPREAD_CURRENT_TICK, currentTick);
        tag.putLong(K_SPREAD_COOLDOWN_TICKS, config.lineageSpreadCooldownTicks());
        tag.putLong(K_SPREAD_COOLDOWN_REMAINING_TICKS, cooldownRemaining);
        tag.putBoolean(K_SPREAD_COOLDOWN_ELIGIBLE, currentTick >= 0L && cooldownRemaining <= 0L);
        tag.putLong(K_SPREAD_LAST_SUCCESS_TICK, lastSuccessTick);
        tag.putBoolean(K_SPREAD_HAS_LAST_SUCCESS, hasLastSuccess);
        tag.putLong(
            K_SPREAD_LAST_SUCCESS_AGE_TICKS,
            currentTick >= 0L && hasLastSuccess ? Math.max(0L, currentTick - lastSuccessTick) : -1L
        );
        tag.putLong(K_SPREAD_NEXT_ELIGIBLE_TICK, nextEligibleTick);
        tag.putInt(K_SPREAD_MAX_LOCATIONS, config.maxLocationsPerLineage());
        tag.putInt(K_LOCATION_COUNT, lineage != null ? lineage.locationsById().size() : 0);
        tag.putInt(K_SPREAD_MAX_RADIUS_CHUNKS, config.maxLineageSpreadChunks());
        tag.putInt(K_SPREAD_MIN_DISTANCE_CHUNKS, config.minimumHiveLocationDistanceChunks());

        var lastAttempt = location.lastAbstractSpreadAttempt();
        var hasLastAttempt = lastAttempt.tick() >= 0L;
        tag.putLong(K_SPREAD_LAST_ATTEMPT_TICK, lastAttempt.tick());
        tag.putBoolean(K_SPREAD_HAS_LAST_ATTEMPT, hasLastAttempt);
        tag.putLong(
            K_SPREAD_LAST_ATTEMPT_AGE_TICKS,
            currentTick >= 0L && hasLastAttempt ? Math.max(0L, currentTick - lastAttempt.tick()) : -1L
        );
        tag.putString(K_SPREAD_LAST_ATTEMPT_RESULT, lastAttempt.result());
        tag.putString(K_SPREAD_LAST_ATTEMPT_DETAIL, lastAttempt.detail());
        if (lastAttempt.createdLocationId() != null) {
            tag.putString(K_SPREAD_LAST_ATTEMPT_CREATED_LOCATION_ID, lastAttempt.createdLocationId().value().toString());
        }
        if (lastAttempt.candidateChunk() != null) {
            tag.putInt(K_SPREAD_LAST_ATTEMPT_CANDIDATE_CHUNK_X, lastAttempt.candidateChunk().x);
            tag.putInt(K_SPREAD_LAST_ATTEMPT_CANDIDATE_CHUNK_Z, lastAttempt.candidateChunk().z);
        }
    }

    private static long spreadCooldownRemaining(HiveLocation location, long currentTick, HiveConfig config) {
        var cooldownTicks = config.lineageSpreadCooldownTicks();
        var lastSuccessTick = location.lastAbstractSpreadTick();
        if (lastSuccessTick > 0L) {
            return currentTick >= 0L ? Math.max(0L, lastSuccessTick + cooldownTicks - currentTick) : -1L;
        }
        return Math.max(0L, cooldownTicks - location.ageInTicks());
    }

    /** Build a snapshot for a variant faction. Lists owned lineages plus aggregates across them. */
    public static CompoundTag buildVariant(Faction<VariantFactionData> faction, @Nullable MinecraftServer server) {
        var tag = new CompoundTag();
        var data = faction.data();
        if (data == null) {
            return tag;
        }
        var config = HiveLocationRegistry.INSTANCE.config();
        var factionId = faction.id();

        putConfig(tag, config);

        tag.putString(K_FACTION_ID, factionId.toString());
        tag.putString(K_DISPLAY_NAME, faction.name());
        tag.putString(K_VARIANT_NAME, data.variant().name());
        tag.putLong(K_AGE_TICKS, data.ageInTicks());
        tag.putLong(K_NEXT_LINEAGE_NUMBER, data.nextLineageNumber());
        tag.putInt(K_VARIANT_MEMBERS, faction.membership().getMembers().size());
        tag.put(K_QUEEN_MOTHERS, queenMotherRows(data));

        long aggBiomass = 0L;
        long aggRoyalJelly = 0L;
        long aggScourgeJelly = 0L;
        long aggPop = 0L;
        long aggPopCap = 0L;
        long aggLocalReserves = 0L;
        long aggLoadedMembers = 0L;
        long aggLocationMembers = 0L;
        long aggLineageMembers = 0L;
        long aggConvoys = 0L;
        long aggConvoyMembers = 0L;
        long aggClaimedChunks = 0L;
        long aggChunksLoaded = 0L;
        long aggLocations = 0L;
        var lineagesList = new ListTag();

        // Walk every registered faction and collect lineages whose parent variant is this one.
        for (var id : Alien.MOD.factions().getAllIds()) {
            var f = Alien.MOD.factions().get(id);
            if (f == null || !(f.data() instanceof LineageFactionData lineage)) {
                continue;
            }
            if (!factionId.equals(lineage.parentVariantFactionId())) {
                continue;
            }
            long lineageBiomass = 0L;
            long lineageRoyalJelly = 0L;
            long lineageScourgeJelly = 0L;
            long lineagePop = 0L;
            long lineagePopCap = 0L;
            long lineageLocalReserves = 0L;
            long lineageLoadedMembers = 0L;
            long lineageLocationMembers = 0L;
            long lineageClaimedChunks = 0L;
            long lineageChunksLoaded = 0L;
            for (var loc : lineage.locationsById().values()) {
                lineageBiomass += loc.biomass();
                lineageRoyalJelly += loc.royalJelly();
                lineageScourgeJelly += loc.scourgeJelly();
                aggBiomass += loc.biomass();
                aggRoyalJelly += loc.royalJelly();
                aggScourgeJelly += loc.scourgeJelly();
                var locPop = CastePopulation.totalTrackedPopulation(loc);
                var locCap = config.populationPerChunk() * Math.max(1, loc.claimedChunks().size());
                var locLocalReserves = loc.localReserves().getReliableCount();
                var locLoadedMembers = loadedMemberTotal(loc);
                var locLocationMembers = locationFactionMemberCount(loc);
                var locServerLevel = server != null ? server.getLevel(loc.dimension()) : null;
                var locChunksLoaded = countLoadedChunks(loc, locServerLevel);
                lineagePop += locPop;
                lineagePopCap += locCap;
                lineageLocalReserves += locLocalReserves;
                lineageLoadedMembers += locLoadedMembers;
                lineageLocationMembers += locLocationMembers;
                lineageClaimedChunks += loc.claimedChunks().size();
                lineageChunksLoaded += locChunksLoaded;
                aggPop += locPop;
                aggPopCap += locCap;
                aggLocalReserves += locLocalReserves;
                aggLoadedMembers += locLoadedMembers;
                aggLocationMembers += locLocationMembers;
                aggClaimedChunks += loc.claimedChunks().size();
                aggChunksLoaded += locChunksLoaded;
                aggLocations++;
            }
            var lineageMembers = f.membership().getMembers().size();
            var convoyCount = lineage.convoys().size();
            var convoyMemberTotal = convoyMemberTotal(lineage);
            aggLineageMembers += lineageMembers;
            aggConvoys += convoyCount;
            aggConvoyMembers += convoyMemberTotal;

            var row = new CompoundTag();
            row.putString(K_FACTION_ID, f.id().toString());
            row.putString(K_DISPLAY_NAME, f.name());
            row.putString(K_DIMENSION, lineage.dimension().location().toString());
            row.putLong(K_LINEAGE_NUMBER, lineage.lineageNumber());
            row.putInt(K_LOCATION_COUNT, lineage.locationsById().size());
            row.putInt(K_CLAIMED_CHUNKS, (int) lineageClaimedChunks);
            row.putInt(K_CHUNKS_LOADED, (int) lineageChunksLoaded);
            row.putLong(K_BIOMASS, lineageBiomass);
            row.putLong(K_ROYAL_JELLY, lineageRoyalJelly);
            row.putLong(K_SCOURGE_JELLY, lineageScourgeJelly);
            row.putLong(K_TOTAL_POP, lineagePop);
            row.putLong(K_POP_CAP, lineagePopCap);
            row.putInt(K_LINEAGE_MEMBER_TOTAL, lineageMembers);
            row.putInt(K_LOCAL_RESERVE_TOTAL, (int) lineageLocalReserves);
            row.putInt(K_LOADED_MEMBER_TOTAL, (int) lineageLoadedMembers);
            row.putInt(K_LOCATION_FACTION_MEMBER_COUNT, (int) lineageLocationMembers);
            row.putInt(K_CONVOY_COUNT, convoyCount);
            row.putInt(K_CONVOY_MEMBER_TOTAL, convoyMemberTotal);
            if (lineage.founderId() != null) {
                row.putUUID(K_FOUNDER_ID, lineage.founderId());
            }
            if (lineage.empressId() != null) {
                row.putUUID(K_EMPRESS_ID, lineage.empressId());
            }
            if (lineage.removalReason() != null) {
                row.putString(K_REMOVAL_REASON, lineage.removalReason().typeKind());
            }
            row.putBoolean(K_PENDING_EMPRESS, lineage.pendingEmpressEmergence());
            lineagesList.add(row);
        }
        tag.put(K_LINEAGES, lineagesList);

        tag.putLong(K_AGG_BIOMASS, aggBiomass);
        tag.putLong(K_AGG_ROYAL_JELLY, aggRoyalJelly);
        tag.putLong(K_AGG_SCOURGE_JELLY, aggScourgeJelly);
        tag.putLong(K_AGG_TOTAL_POP, aggPop);
        tag.putLong(K_AGG_POP_CAP, aggPopCap);
        tag.putLong(K_AGG_LOCAL_RESERVES, aggLocalReserves);
        tag.putLong(K_AGG_LOADED_MEMBERS, aggLoadedMembers);
        tag.putLong(K_AGG_LOCATION_MEMBERS, aggLocationMembers);
        tag.putLong(K_AGG_LINEAGE_MEMBERS, aggLineageMembers);
        tag.putLong(K_AGG_CONVOYS, aggConvoys);
        tag.putLong(K_AGG_CONVOY_MEMBERS, aggConvoyMembers);
        tag.putLong(K_AGG_CLAIMED_CHUNKS, aggClaimedChunks);
        tag.putLong(K_AGG_CHUNKS_LOADED, aggChunksLoaded);
        tag.putLong(K_AGG_LOCATIONS, aggLocations);

        return tag;
    }

    private static ListTag reserveRows(EntityReserves reserves) {
        var rows = new ListTag();
        var entries = new ArrayList<>(reserves.getBackingMap().entrySet());
        entries.sort(Comparator.comparing(entry -> BuiltInRegistries.ENTITY_TYPE.getKey(entry.getKey()).toString()));
        for (var entry : entries) {
            if (entry.getValue() <= 0) {
                continue;
            }
            var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entry.getKey());
            var row = new CompoundTag();
            row.putString(K_KEY, typeId.toString());
            row.putInt(K_VALUE, entry.getValue());
            rows.add(row);
        }
        return rows;
    }

    private static ListTag queenMotherRows(VariantFactionData data) {
        var rows = new ListTag();
        var entries = new ArrayList<>(data.queenMotherIdsByDimension().entrySet());
        entries.sort(Comparator.comparing(entry -> entry.getKey().location().toString()));
        for (var entry : entries) {
            var row = new CompoundTag();
            row.putString(K_DIMENSION, entry.getKey().location().toString());
            row.putUUID(K_UUID, entry.getValue());
            rows.add(row);
        }
        return rows;
    }

    private static int loadedMemberTotal(HiveLocation location) {
        var total = 0;
        for (var members : location.loadedMembersByType().values()) {
            total += members.size();
        }
        return total;
    }

    private static int locationFactionMemberCount(HiveLocation location) {
        var locationFaction = Alien.MOD.factions().get(location.id().value());
        return locationFaction != null ? locationFaction.membership().getMembers().size() : 0;
    }

    private static int convoyMemberTotal(LineageFactionData data) {
        var total = 0;
        for (var convoy : data.convoys()) {
            total += convoy.composition().getCount();
        }
        return total;
    }

    private static ListTag convoyRows(LineageFactionData data) {
        var rows = new ListTag();
        for (var convoy : data.convoys()) {
            var row = new CompoundTag();
            row.putString(K_TYPE, convoyType(convoy));
            row.putString(K_DIMENSION, convoy.dimension().location().toString());
            row.putInt(K_VALUE, convoy.composition().getCount());
            row.putLong(K_DISPATCHED_TICK, convoy.dispatchedTick());
            row.putInt(K_CURRENT_X, (int) Math.floor(convoy.currentPos().x));
            row.putInt(K_CURRENT_Y, (int) Math.floor(convoy.currentPos().y));
            row.putInt(K_CURRENT_Z, (int) Math.floor(convoy.currentPos().z));
            row.put(K_RESERVES_BY_TYPE, reserveRows(convoy.composition()));

            if (convoy instanceof Convoy.Reinforcement reinforcement) {
                row.putString(K_SOURCE_LOCATION_ID, reinforcement.sourceLocationId().value().toString());
                row.putString(K_DESTINATION_LOCATION_ID, reinforcement.destinationLocationId().value().toString());
                putDestination(row, reinforcement.destinationPos());
            } else if (convoy instanceof Convoy.Migration migration) {
                row.putString(K_SOURCE_LOCATION_ID, migration.sourceLocationId().value().toString());
                row.putString(K_DESTINATION_LOCATION_ID, migration.destinationLocationId().value().toString());
                row.putInt(K_BIOMASS_PAYLOAD, migration.biomassPayload());
                row.putBoolean(K_CARRIES_EMPRESS, migration.carriesEmpress());
                putDestination(row, migration.destinationPos());
            } else if (convoy instanceof Convoy.Raid raid) {
                row.putString(K_SOURCE_LOCATION_ID, raid.sourceLocationId().value().toString());
                row.putUUID(K_TARGET_PLAYER_ID, raid.targetPlayerId());
                row.putLong(K_EXPIRES_AT_TICK, raid.expiresAtTick());
                putDestination(row, raid.lastKnownTargetPos());
            }
            rows.add(row);
        }
        return rows;
    }

    private static String convoyType(Convoy convoy) {
        if (convoy instanceof Convoy.Reinforcement) {
            return "reinforcement";
        }
        if (convoy instanceof Convoy.Migration) {
            return "migration";
        }
        if (convoy instanceof Convoy.Raid) {
            return "raid";
        }
        return "unknown";
    }

    private static void putDestination(CompoundTag tag, net.minecraft.core.BlockPos pos) {
        tag.putInt(K_DEST_X, pos.getX());
        tag.putInt(K_DEST_Y, pos.getY());
        tag.putInt(K_DEST_Z, pos.getZ());
    }

    private static int countLoadedChunks(HiveLocation location, @Nullable ServerLevel level) {
        if (level == null) {
            return 0;
        }
        var loaded = 0;
        for (var chunk : location.claimedChunks()) {
            if (level.getChunkSource().hasChunk(chunk.x, chunk.z)) {
                loaded++;
            }
        }
        return loaded;
    }

    private static void putConfig(CompoundTag tag, HiveConfig config) {
        tag.put(K_HIVE_CONFIG, HiveConfigSchema.toTag(config));
    }

}
