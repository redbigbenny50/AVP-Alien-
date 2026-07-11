package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.faction.FactionAesthetics;
import com.alien.common.gameplay.hive.faction.FactionNaming;
import com.alien.common.gameplay.hive.faction.HiveLocationFactionProvisioner;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.faction.LocationMembership;
import com.alien.common.gameplay.hive.faction.VariantFactionRegistry;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.alien.common.gameplay.hive.id.HiveLocationIds;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.init.AlienFactionDataTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

/**
 * Production-path founding for the new hive system. Two entry points:
 * <ul>
 * <li>{@link #foundNewLineage} — the queen is a forager on fresh ground. Mints a new lineage faction (parented to her
 * variant faction) and her first location.</li>
 * <li>{@link #foundNewLocation} — the queen is already in a lineage and has settled within its spread zone. Just adds
 * another location to that existing lineage.</li>
 * </ul>
 * <p>
 * Either path: claims the queen's chunk, registers the location with {@link HiveLocationRegistry}, sets
 * {@code founderId = queen.uuid}, and adds the queen to the lineage's BLib membership.
 * <p>
 * Implements the rules from {@code HIVE_REDESIGN_02_FACTION_LIFECYCLES.md} § 2 and
 * {@code HIVE_REDESIGN_03_LOCATIONS.md} § 9. Empress emergence (if the lineage now hits 2+ locations) is parked for
 * Phase 10; this phase only sets the {@code pendingEmpressEmergence} flag so Phase 10's listener can pick it up.
 */
public final class HiveLocationFoundingService {

    private HiveLocationFoundingService() {}

    /**
     * Mints a new {@link LineageFactionData} (parented to the queen's variant faction) and her first
     * {@link HiveLocation} at {@code position}. Returns the new location id.
     */
    public static HiveLocationId foundNewLineage(Queen queen, BlockPos position) {
        // Default: a founding queen raises her physical chamber. The inhibited-claim path passes false - a captive
        // queen still gets a lineage + claim (for her chained eggsack and the autonomy gate) but NO built hive.
        return foundNewLineage(queen, position, true);
    }

    public static HiveLocationId foundNewLineage(Queen queen, BlockPos position, boolean buildStructure) {
        var level = queen.level();
        var dimension = level.dimension();
        var variant = queen.getVariant();

        var variantFaction = VariantFactionRegistry.getOrCreate(variant);
        var lineageId = LineageIds.create();
        var lineageFaction = Alien.MOD.factions().getOrCreate(lineageId, AlienFactionDataTypes.LINEAGE);
        var lineageData = lineageFaction.data();

        if (lineageData == null) {
            throw new IllegalStateException("LineageFactionData was null after getOrCreate for " + lineageId);
        }

        FactionAesthetics.applyDefaults(lineageFaction, variant, FactionAesthetics.Tier.LINEAGE);
        lineageData.setFactionId(lineageId);

        // Allocate a monotonic per-variant index and name the lineage faction xenos/{variant}/lin{N}.
        var lineageNumber = variantFaction.data() != null
            ? variantFaction.data().allocateLineageNumber()
            : 0L;
        lineageData.setLineageNumber(lineageNumber);
        lineageFaction.setName(FactionNaming.forLineage(variant, lineageNumber));

        lineageData.setVariant(variant);
        lineageData.setParentVariantFactionId(variantFaction.id());
        lineageData.setDimension(dimension);
        lineageData.setFounderId(queen.getUUID());

        var location = mintLocation(queen, lineageId, position, level.getGameTime(), lineageData, buildStructure);

        // Adds the queen to both the lineage faction (idempotent) and the new location faction.
        LocationMembership.join(location, queen);

        Alien.LOGGER.info(
            "Hive: queen {} founded new lineage {} at {} (variant {}) with first location {}",
            queen.getUUID(),
            lineageId,
            position,
            variant,
            location.id()
        );

        return location.id();
    }

    /**
     * Adds a new {@link HiveLocation} to an existing lineage at {@code position}. Returns the new location id. If the
     * lineage now has 2+ locations, sets the {@code pendingEmpressEmergence} flag for Phase 10.
     */
    public static HiveLocationId foundNewLocation(Queen queen, ResourceLocation lineageFactionId, BlockPos position) {
        var level = queen.level();
        var faction = Alien.MOD.factions().get(lineageFactionId);

        if (faction == null || !(faction.data() instanceof LineageFactionData lineageData)) {
            throw new IllegalStateException("Lineage " + lineageFactionId + " missing or wrong type at founding time");
        }

        var location = mintLocation(queen, lineageFactionId, position, level.getGameTime(), lineageData, true);

        // Adds the queen to both the lineage faction (idempotent) and the new location faction.
        LocationMembership.join(location, queen);

        if (lineageData.locationsById().size() >= 2 && lineageData.empressId() == null) {
            // Phase 10 will pick this up and run the empress emergence ritual.
            lineageData.setPendingEmpressEmergence(true);
        }

        Alien.LOGGER.info(
            "Hive: queen {} founded location {} in existing lineage {} at {} (lineage now has {} locations)",
            queen.getUUID(),
            location.id(),
            lineageFactionId,
            position,
            lineageData.locationsById().size()
        );

        return location.id();
    }

    private static HiveLocation mintLocation(
        Queen queen,
        ResourceLocation lineageFactionId,
        BlockPos position,
        long currentGameTime,
        LineageFactionData lineageData,
        boolean buildStructure
    ) {
        var locationId = HiveLocationIds.create();
        var centerChunk = new ChunkPos(position);
        var location = new HiveLocation(
            locationId,
            lineageFactionId,
            queen.level().dimension(),
            position,
            queen.getUUID()
        );

        // Allocate the location's per-lineage index before adding so the path name reflects it.
        var locationNumber = lineageData.allocateLocationNumber();
        location.setLocationNumber(locationNumber);
        location.setLastGrowthTick(currentGameTime);
        location.setLastPassiveClaimTick(currentGameTime);

        lineageData.addLocation(location);
        HiveLocationRegistry.INSTANCE.register(location);

        HiveLocationFactionProvisioner.ensure(location, lineageData);

        // Claim the initial core through HiveLocationClaims so all three sources of truth (location set,
        // registry byChunk index, BLib territory map) stay synchronized. Direct claimedChunks().add(...)
        // would miss the BLib territory addClaim and leave the core chunks unclaimed in the UI.
        if (queen.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            claimInitialCore(serverLevel, location, centerChunk, currentGameTime, buildStructure);
        } else {
            addInitialCoreOffline(location, centerChunk, currentGameTime);
        }

        return location;
    }

    private static void claimInitialCore(
        net.minecraft.server.level.ServerLevel level,
        HiveLocation location,
        ChunkPos centerChunk,
        long currentGameTime,
        boolean buildStructure
    ) {
        var radius = HiveLocationRegistry.INSTANCE.config().initialHiveLocationClaimRadiusChunks();
        for (var dx = -radius; dx <= radius; dx++) {
            for (var dz = -radius; dz <= radius; dz++) {
                var chunk = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                if (HiveLocationRegistry.INSTANCE.getByChunk(level.dimension(), chunk) != null) {
                    continue;
                }
                com.alien.common.gameplay.hive.growth.HiveLocationClaims.claim(
                    level,
                    location,
                    chunk,
                    currentGameTime
                );
            }
        }

        // Structure system: stamp queen-chamber roles onto the claimed core and register royal exits as frontier
        // sockets for the planner. Skipped for logical-only claims (an inhibited captive queen never builds a hive).
        if (buildStructure) {
            com.alien.common.gameplay.hive.structure.HiveStructureFounding.establishQueenChamber(
                level.getServer(),
                location,
                centerChunk
            );
        }
    }

    private static void addInitialCoreOffline(HiveLocation location, ChunkPos centerChunk, long currentGameTime) {
        var radius = HiveLocationRegistry.INSTANCE.config().initialHiveLocationClaimRadiusChunks();
        for (var dx = -radius; dx <= radius; dx++) {
            for (var dz = -radius; dz <= radius; dz++) {
                var chunk = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                location.claimedChunks().add(chunk);
                location.chunkClaimTicks().put(chunk, currentGameTime);
            }
        }
    }

    /**
     * Convenience wrapper: looks at the {@link SpreadZoneResult} and runs the matching founding action. Returns the new
     * location id when something was minted, or null when blocked.
     */
    public static @Nullable HiveLocationId foundFromResult(Queen queen, BlockPos position, SpreadZoneResult result) {
        if (result instanceof SpreadZoneResult.NewLineage) {
            return foundNewLineage(queen, position);
        }

        if (result instanceof SpreadZoneResult.NewLocation newLocation) {
            return foundNewLocation(queen, newLocation.lineageFactionId(), position);
        }

        // Blocked — caller already knows why; nothing to do.
        return null;
    }
}
