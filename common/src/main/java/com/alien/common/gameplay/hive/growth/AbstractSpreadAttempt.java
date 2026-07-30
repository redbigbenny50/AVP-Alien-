package com.alien.common.gameplay.hive.growth;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.drone.Drone;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.entity.living.alien.xenomorph.runner.Runner;
import com.alien.common.gameplay.hive.config.HiveConfig;
import com.alien.common.gameplay.hive.convoy.Convoy;
import com.alien.common.gameplay.hive.convoy.ConvoyId;
import com.alien.common.gameplay.hive.economy.CastePopulation;
import com.alien.common.gameplay.hive.faction.HiveLocationFactionProvisioner;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.alien.common.gameplay.hive.id.HiveLocationIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.entity.v1.EntityReserves;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * Unloaded spread per {@code HIVE_REDESIGN_08_LINEAGE_SPREAD.md} § 3.
 * <p>
 * Drives "the lineage conquers an unattended dimension over real-world weeks" without forcing queen entities into
 * unloaded chunks. Folded into {@link com.alien.common.gameplay.hive.tick.HiveLocationSlowTickTask}'s randomized
 * location scheduler when chunks are not loaded, and the loaded-location fast path otherwise.
 * <p>
 * Conditions (must all hold):
 * <ul>
 * <li>Source location is old enough or past its own {@code lineageSpreadCooldownTicks} spread cooldown.</li>
 * <li>Lineage hasn't hit {@code maxLocationsPerLineage}.</li>
 * </ul>
 * <p>
 * The candidate position is picked uniformly within the spread zone of the source location. We reject if the
 * candidate's chunk is already in any existing location's claimed territory or has been previously decorated.
 * Otherwise: found a new location with bootstrap reserves and bump the source location's spread tick.
 */
public final class AbstractSpreadAttempt {

    private static final Random RANDOM = new Random();

    private static final String RESULT_COOLDOWN = "cooldown";

    private static final String RESULT_MAX_LOCATIONS = "max_locations";

    private static final String RESULT_DIMENSION_UNLOADED = "dimension_unloaded";

    private static final String RESULT_SOURCE_INACTIVE = "source_inactive";

    private static final String RESULT_SPREAD_DISABLED = "spread_disabled";

    private static final String RESULT_INSUFFICIENT_POPULATION = "insufficient_population";

    private static final String RESULT_INSUFFICIENT_FOUNDER_POPULATION = "insufficient_founder_population";

    private static final String RESULT_DAUGHTER_LIMIT = "daughter_limit";

    private static final String RESULT_INSUFFICIENT_JELLY = "insufficient_jelly";

    private static final String RESULT_OCCUPIED = "occupied";

    private static final String RESULT_CORE_OVERLAP = "core_overlap";

    private static final String RESULT_TOO_CLOSE = "too_close";

    private static final String RESULT_DECORATED = "decorated";

    private static final String RESULT_SUCCESS = "success";

    private AbstractSpreadAttempt() {}

    /**
     * Attempts an abstract spread for {@code lineage}. Returns the minted location id on success, or null when
     * conditions or the candidate check failed.
     */
    public static @Nullable HiveLocationId tryRun(
        MinecraftServer server,
        ResourceLocation lineageId,
        LineageFactionData lineage,
        HiveLocation sourceLocation,
        long currentTick
    ) {
        var config = HiveLocationRegistry.INSTANCE.config();

        // Cooldown.
        var remainingCooldownTicks = remainingCooldownTicks(sourceLocation, currentTick, config);
        if (remainingCooldownTicks > 0L) {
            record(
                sourceLocation,
                lineage,
                currentTick,
                RESULT_COOLDOWN,
                null,
                null,
                "Next eligible in " + remainingCooldownTicks + " ticks."
            );
            return null;
        }

        // Max locations cap.
        if (lineage.activeLocationCount() >= config.maxLocationsPerLineage()) {
            record(
                sourceLocation,
                lineage,
                currentTick,
                RESULT_MAX_LOCATIONS,
                null,
                null,
                "Lineage has " + lineage.locationsById().size() + "/" + config.maxLocationsPerLineage() + " locations."
            );
            return null;
        }

        // Empress hive-count cap. An empress can only control up to maxLocationsUnderEmpress hives (including her
        // own origin hive) — a stricter, empress-specific ceiling below the general per-lineage cap above. Once she's
        // present, growth stops well short of maxLocationsPerLineage unless that config is tightened to match.
        if (lineage.empressId() != null && lineage.activeLocationCount() >= config.maxLocationsUnderEmpress()) {
            record(
                sourceLocation,
                lineage,
                currentTick,
                RESULT_MAX_LOCATIONS,
                null,
                null,
                "Empress-led lineage has "
                    + lineage.locationsById().size()
                    + "/"
                    + config.maxLocationsUnderEmpress()
                    + " hives (empress cap)."
            );
            return null;
        }

        var serverLevel = server.getLevel(lineage.dimension());
        if (serverLevel == null) {
            record(
                sourceLocation,
                lineage,
                currentTick,
                RESULT_DIMENSION_UNLOADED,
                null,
                null,
                "Dimension " + lineage.dimension().location() + " is not loaded."
            );
            return null;
        }

        if (!sourceLocation.isAlive()) {
            record(sourceLocation, lineage, currentTick, RESULT_SOURCE_INACTIVE, null, null, "Source location is not alive.");
            return null;
        }

        var sourcePopulation = CastePopulation.totalTrackedPopulation(sourceLocation);
        if (sourcePopulation < config.minimumPopulationForHiveSpread()) {
            record(
                sourceLocation,
                lineage,
                currentTick,
                RESULT_INSUFFICIENT_POPULATION,
                null,
                null,
                "Source population is " + sourcePopulation + "/" + config.minimumPopulationForHiveSpread() + "."
            );
            return null;
        }

        // A hive seeds exactly two daughters in its lifetime and then stops forever, whatever else it has.
        if (sourceLocation.daughterHivesFounded() >= config.maxDaughterHivesPerLocation()) {
            record(
                sourceLocation,
                lineage,
                currentTick,
                RESULT_DAUGHTER_LIMIT,
                null,
                null,
                "Source has seeded "
                    + sourceLocation.daughterHivesFounded()
                    + "/"
                    + config.maxDaughterHivesPerLocation()
                    + " daughter hives."
            );
            return null;
        }

        // The founding queen is MADE, not found: a praetorian (or failing that a crusher) is raised into one, and
        // that costs royal jelly. This is why the old founder party could never assemble - it wanted a queen sitting
        // in reserves, and queens are explicitly excluded from reserves as unique identity entities, so nothing
        // could ever put one there.
        if (sourceLocation.royalJelly() < config.queenPromotionJellyCost()) {
            record(
                sourceLocation,
                lineage,
                currentTick,
                RESULT_INSUFFICIENT_JELLY,
                null,
                null,
                "Royal jelly is "
                    + sourceLocation.royalJelly()
                    + "/"
                    + config.queenPromotionJellyCost()
                    + " for the queen promotion."
            );
            return null;
        }

        var founderParty = FounderParty.forLineage(lineage, config);
        if (founderParty == null) {
            record(
                sourceLocation,
                lineage,
                currentTick,
                RESULT_SPREAD_DISABLED,
                null,
                null,
                "Could not resolve queen/drone/runner founder types for variant " + lineage.variant() + "."
            );
            return null;
        }
        if (!founderParty.availableIn(sourceLocation)) {
            record(
                sourceLocation,
                lineage,
                currentTick,
                RESULT_INSUFFICIENT_FOUNDER_POPULATION,
                null,
                null,
                founderParty.missingDetail(sourceLocation)
            );
            return null;
        }

        var candidateChunk = pickCandidateInSpreadZone(sourceLocation, config);
        if (candidateChunk == null) {
            record(
                sourceLocation,
                lineage,
                currentTick,
                RESULT_SPREAD_DISABLED,
                null,
                null,
                "maxLineageSpreadChunks is " + config.maxLineageSpreadChunks() + "."
            );
            return null;
        }

        var candidateValidation = validateCandidate(lineage, candidateChunk, config);
        if (!candidateValidation.valid()) {
            record(
                sourceLocation,
                lineage,
                currentTick,
                candidateValidation.result(),
                candidateChunk,
                null,
                candidateValidation.detail()
            );
            return null;
        }

        // Found.
        var founderGroup = founderParty.drainFrom(sourceLocation);
        var location = mintAbstractLocation(serverLevel, lineage, lineageId, candidateChunk, currentTick);
        dispatchFounderConvoy(lineage, lineageId, sourceLocation, location, founderGroup, currentTick);
        var locationId = location.id();

        sourceLocation.setLastAbstractSpreadTick(currentTick);
        sourceLocation.setRoyalJelly(sourceLocation.royalJelly() - config.queenPromotionJellyCost());
        sourceLocation.setDaughterHivesFounded(sourceLocation.daughterHivesFounded() + 1);
        record(
            sourceLocation,
            lineage,
            currentTick,
            RESULT_SUCCESS,
            candidateChunk,
            locationId,
            "Dispatched " + founderGroup.composition().getCount() + " founder reserves by convoy to the new location."
        );

        Alien.LOGGER.info(
            "Hive: abstract spread for lineage {}: minted location {} at chunk {} (sourced from {})",
            lineageId,
            locationId,
            candidateChunk,
            sourceLocation.id()
        );

        return locationId;
    }

    private static long remainingCooldownTicks(HiveLocation sourceLocation, long currentTick, HiveConfig config) {
        var lastSpreadTick = sourceLocation.lastAbstractSpreadTick();
        if (lastSpreadTick > 0L) {
            return Math.max(0L, lastSpreadTick + config.lineageSpreadCooldownTicks() - currentTick);
        }
        return Math.max(0L, config.lineageSpreadCooldownTicks() - sourceLocation.ageInTicks());
    }

    private static @Nullable ChunkPos pickCandidateInSpreadZone(HiveLocation source, HiveConfig config) {
        var maxSpread = config.maxLineageSpreadChunks();
        if (maxSpread <= 0) {
            return null;
        }

        var sourceChunk = new ChunkPos(source.centerPos());
        // Uniform random offset within [-maxSpread, +maxSpread] in both axes.
        var dx = RANDOM.nextInt(2 * maxSpread + 1) - maxSpread;
        var dz = RANDOM.nextInt(2 * maxSpread + 1) - maxSpread;
        return new ChunkPos(sourceChunk.x + dx, sourceChunk.z + dz);
    }

    private static CandidateValidation validateCandidate(LineageFactionData lineage, ChunkPos candidate, HiveConfig config) {
        // Reject any chunk owned by any existing location.
        var occupant = HiveLocationRegistry.INSTANCE.getByChunk(lineage.dimension(), candidate);
        if (occupant != null) {
            return CandidateValidation.reject(
                RESULT_OCCUPIED,
                "Candidate chunk is already claimed by " + occupant.id().value() + "."
            );
        }

        var coreOverlap = findInitialCoreOverlap(lineage, candidate);
        if (coreOverlap != null) {
            return CandidateValidation.reject(
                RESULT_CORE_OVERLAP,
                "Initial claim footprint would overlap " + coreOverlap.occupant().id().value()
                    + " at chunk " + coreOverlap.chunk() + "."
            );
        }

        var tooClose = HiveLocationRegistry.INSTANCE.findTooCloseToCenter(
            lineage.dimension(),
            candidate,
            config.minimumHiveLocationDistanceChunks()
        );
        if (tooClose != null) {
            return CandidateValidation.reject(
                RESULT_TOO_CLOSE,
                "Candidate is within "
                    + config.minimumHiveLocationDistanceChunks()
                    + " chunks of existing hive location "
                    + tooClose.id().value()
                    + "."
            );
        }

        // Reject if the chunk has the decorated_by_hive flag from any of THIS lineage's previously-killed locations.
        // (Soft cooldown — a player who clears a hive gets a brief reprieve before it tries to come back.)
        for (var location : lineage.locationsById().values()) {
            if (location.decoratedChunks().contains(candidate)) {
                return CandidateValidation.reject(
                    RESULT_DECORATED,
                    "Candidate chunk was previously decorated by " + location.id().value() + "."
                );
            }
        }

        return CandidateValidation.accept();
    }

    private static @Nullable CoreOverlap findInitialCoreOverlap(LineageFactionData lineage, ChunkPos candidate) {
        var radius = HiveLocationRegistry.INSTANCE.config().initialHiveLocationClaimRadiusChunks();
        for (var dx = -radius; dx <= radius; dx++) {
            for (var dz = -radius; dz <= radius; dz++) {
                var chunk = new ChunkPos(candidate.x + dx, candidate.z + dz);
                var occupant = HiveLocationRegistry.INSTANCE.getByChunk(lineage.dimension(), chunk);
                if (occupant != null) {
                    return new CoreOverlap(chunk, occupant);
                }
            }
        }
        return null;
    }

    private static HiveLocation mintAbstractLocation(
        ServerLevel level,
        LineageFactionData lineage,
        ResourceLocation lineageId,
        ChunkPos candidate,
        long currentTick
    ) {
        var locationId = HiveLocationIds.create();
        var centerPos = candidate.getMiddleBlockPosition(64); // Y is approximate; chunk-load corrects later
        var location = new HiveLocation(
            locationId,
            lineageId,
            lineage.dimension(),
            centerPos,
            null // No founder — minted abstractly without a queen entity.
        );
        location.setLocationNumber(lineage.allocateLocationNumber());
        HiveLocationFactionProvisioner.ensure(location, lineage);

        claimInitialCore(level, location, candidate, currentTick);

        location.setBiomass(0);
        location.setLastGrowthTick(currentTick);
        location.setLastPassiveClaimTick(currentTick);

        lineage.addLocation(location);
        HiveLocationRegistry.INSTANCE.register(location);

        return location;
    }

    private static void dispatchFounderConvoy(
        LineageFactionData lineage,
        ResourceLocation lineageId,
        HiveLocation sourceLocation,
        HiveLocation destinationLocation,
        FounderGroup founderGroup,
        long currentTick
    ) {
        var convoy = new Convoy.Reinforcement(
            ConvoyId.fresh(),
            lineageId,
            sourceLocation.dimension(),
            sourceLocation.id(),
            destinationLocation.id(),
            centerOf(sourceLocation.centerPos()),
            destinationLocation.centerPos(),
            founderGroup.composition(),
            currentTick
        );

        lineage.convoys().add(convoy);
        lineage.markDirty();
    }

    private static Vec3 centerOf(net.minecraft.core.BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private static void claimInitialCore(ServerLevel level, HiveLocation location, ChunkPos centerChunk, long currentTick) {
        var radius = HiveLocationRegistry.INSTANCE.config().initialHiveLocationClaimRadiusChunks();
        for (var dx = -radius; dx <= radius; dx++) {
            for (var dz = -radius; dz <= radius; dz++) {
                var chunk = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                if (HiveLocationRegistry.INSTANCE.getByChunk(level.dimension(), chunk) != null) {
                    continue;
                }
                HiveLocationClaims.claim(level, location, chunk, currentTick);
            }
        }
    }

    private static void record(
        HiveLocation sourceLocation,
        LineageFactionData lineage,
        long currentTick,
        String result,
        @Nullable ChunkPos candidateChunk,
        @Nullable HiveLocationId createdLocationId,
        String detail
    ) {
        sourceLocation.recordAbstractSpreadAttempt(
            currentTick,
            result,
            candidateChunk,
            createdLocationId,
            detail
        );
        lineage.markDirty();
    }

    private record CandidateValidation(
        boolean valid,
        String result,
        String detail
    ) {

        private static CandidateValidation accept() {
            return new CandidateValidation(true, "", "");
        }

        private static CandidateValidation reject(String result, String detail) {
            return new CandidateValidation(false, result, detail);
        }
    }

    private record CoreOverlap(
        ChunkPos chunk,
        HiveLocation occupant
    ) {}

    private record FounderParty(
        EntityType<?> queenType,
        EntityType<?> praetorianType,
        EntityType<?> crusherType,
        EntityType<?> droneType,
        EntityType<?> runnerType,
        int minSize,
        int maxSize
    ) {

        private static @Nullable FounderParty forLineage(LineageFactionData lineage, HiveConfig config) {
            var queenType = Queen.getType(lineage.variant());
            var droneType = Drone.getType(lineage.variant());
            var runnerType = Runner.getType(lineage.variant());
            if (queenType == null || droneType == null || runnerType == null) {
                return null;
            }
            var minSize = Math.max(3, config.abstractSpreadMinFounderGroupSize());
            var maxSize = Math.max(minSize, config.abstractSpreadMaxFounderGroupSize());
            return new FounderParty(
                (EntityType<?>) queenType,
                (EntityType<?>) com.alien.common.gameplay.entity.living.alien.xenomorph.praetorian.Praetorian
                    .getType(lineage.variant()),
                (EntityType<?>) com.alien.common.gameplay.entity.living.alien.xenomorph.crusher.Crusher
                    .getType(lineage.variant()),
                (EntityType<?>) droneType,
                (EntityType<?>) runnerType,
                minSize,
                maxSize
            );
        }

        private boolean availableIn(HiveLocation location) {
            return promotableIn(location) != null
                && location.localReserves().getCount(droneType) >= 1
                && location.localReserves().getCount(runnerType) >= 1
                && eligibleReserveCount(location) >= minSize;
        }

        /** The heavy this hive will raise into its founding queen. PRAETORIAN FIRST, crusher only if there is none. */
        private @Nullable EntityType<?> promotableIn(HiveLocation location) {
            if (praetorianType != null && location.localReserves().getCount(praetorianType) >= 1) {
                return praetorianType;
            }
            if (crusherType != null && location.localReserves().getCount(crusherType) >= 1) {
                return crusherType;
            }
            return null;
        }

        private FounderGroup drainFrom(HiveLocation location) {
            var targetSize = Math.min(maxSize, eligibleReserveCount(location));
            var composition = new EntityReserves();

            // The heavy is SPENT and does not travel; what boards the convoy is the queen she was raised into, so
            // the queen is added to the composition rather than drained from reserves (nothing could drain her -
            // she did not exist a moment ago).
            var promotable = promotableIn(location);
            if (promotable != null) {
                location.localReserves().underlying().add(promotable, -1);
            }
            composition.add(queenType, 1);

            drainOne(location, composition, droneType);
            drainOne(location, composition, runnerType);
            drainFillers(location, composition, targetSize - composition.getCount());

            return new FounderGroup(composition);
        }

        private String missingDetail(HiveLocation location) {
            return "Source reserves need promotable-heavy/drone/runner founder party; have "
                + (promotableIn(location) == null ? 0 : 1)
                + "/"
                + location.localReserves().getCount(droneType)
                + "/"
                + location.localReserves().getCount(runnerType)
                + " and "
                + eligibleReserveCount(location)
                + "/"
                + minSize
                + " eligible founder reserves.";
        }

        private int eligibleReserveCount(HiveLocation location) {
            var count = promotableIn(location) != null ? 1 : 0;
            for (var type : location.localReserves().getAvailableEntityTypes()) {
                if (isFounderFillerEligible(type)) {
                    count += location.localReserves().getCount(type);
                }
            }
            return count;
        }

        private static void drainOne(HiveLocation location, EntityReserves composition, EntityType<?> type) {
            if (location.localReserves().trySpawn(type)) {
                composition.add(type, 1);
            }
        }

        private static void drainFillers(HiveLocation location, EntityReserves composition, int count) {
            var remaining = count;
            var available = new java.util.ArrayList<>(
                location.localReserves()
                    .getAvailableEntityTypes()
                    .stream()
                    .filter(FounderParty::isFounderFillerEligible)
                    .toList()
            );

            while (remaining > 0 && !available.isEmpty()) {
                var iterator = available.iterator();
                while (iterator.hasNext() && remaining > 0) {
                    var type = iterator.next();
                    if (location.localReserves().trySpawn(type)) {
                        composition.add(type, 1);
                        remaining--;
                        if (location.localReserves().getCount(type) <= 0) {
                            iterator.remove();
                        }
                    } else {
                        iterator.remove();
                    }
                }
            }
        }

        private static boolean isFounderFillerEligible(EntityType<?> type) {
            return type.is(AlienEntityTypeTags.DRONES)
                || type.is(AlienEntityTypeTags.RUNNERS)
                || type.is(AlienEntityTypeTags.WARRIORS)
                || type.is(AlienEntityTypeTags.PROWLERS)
                || type.is(AlienEntityTypeTags.PRAETORIANS)
                || type.is(AlienEntityTypeTags.CRUSHERS)
                || type.is(AlienEntityTypeTags.RAVAGERS)
                || type.is(AlienEntityTypeTags.RAZOR_CLAWS)
                || type.is(AlienEntityTypeTags.BURSTERS)
                || type.is(AlienEntityTypeTags.CARRIERS)
                || type.is(AlienEntityTypeTags.CHRYSALISES)
                || type.is(AlienEntityTypeTags.SPITTERS);
        }
    }

    private record FounderGroup(EntityReserves composition) {}

}
