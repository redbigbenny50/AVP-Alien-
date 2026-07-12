package com.alien.common.gameplay.hive.party;

import com.alien.Alien;
import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.hive.config.HiveConfig;
import com.alien.common.gameplay.hive.growth.BiomassIncome;
import com.alien.common.gameplay.hive.growth.HiveLocationClaims;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;

/**
 * Per-tick (piggybacking {@code HiveLocationLoadedTickTask}'s 20-tick cadence) resolution for
 * {@link HiveParty.SurfaceSpawn} parties on a location:
 * <ul>
 * <li>While active at night — periodically re-evaluates {@link HiveParty.EconomyBias} against the source hive's current
 * biomass (dynamic, not locked in at dispatch), and while {@code EXPAND}, attempts to claim the chunk a live member
 * currently occupies (subject to the same frontier-adjacency, territory-radius, and biomass-cost gates every other
 * claim path already respects).</li>
 * <li>On the day transition — surviving members refund to the source hive's reserves, a vent+resin drop is rolled (10%
 * by default, capped against near-surface vents in that chunk only), and the party is removed.</li>
 * </ul>
 */
public final class SurfacePartyLifecycleTask {

    private static final long ECONOMY_RECHECK_INTERVAL_TICKS = 200L; // 10s

    private SurfacePartyLifecycleTask() {}

    public static void run(MinecraftServer server, HiveLocation location, HiveConfig config) {
        if (location.parties().isEmpty()) {
            return;
        }

        var serverLevel = server.getLevel(location.dimension());
        if (serverLevel == null) {
            return;
        }

        var currentTick = serverLevel.getGameTime();
        var isDay = serverLevel.isDay();

        var iterator = location.parties().iterator();
        while (iterator.hasNext()) {
            var party = iterator.next();
            if (!(party instanceof HiveParty.SurfaceSpawn surfaceSpawn)) {
                continue;
            }

            if (isDay) {
                resolveAtDawn(serverLevel, location, surfaceSpawn, config);
                iterator.remove();
                continue;
            }

            tickActive(serverLevel, location, surfaceSpawn, config, currentTick);
        }
    }

    private static void tickActive(
        ServerLevel serverLevel,
        HiveLocation location,
        HiveParty.SurfaceSpawn party,
        HiveConfig config,
        long currentTick
    ) {
        if (currentTick - party.lastEconomyCheckTick() < ECONOMY_RECHECK_INTERVAL_TICKS) {
            return;
        }
        party.setLastEconomyCheckTick(currentTick);

        var bias = com.alien.common.util.AlienPredicates.isLocationLowOnBiomass(location)
            ? HiveParty.EconomyBias.HARVEST
            : HiveParty.EconomyBias.EXPAND;
        party.setEconomyBias(bias);

        if (bias != HiveParty.EconomyBias.EXPAND) {
            return;
        }

        var representative = firstLiveMember(serverLevel, party);
        if (representative == null) {
            return;
        }

        tryOpportunisticClaim(serverLevel, location, config, new ChunkPos(representative.blockPosition()), currentTick);
    }

    private static Entity firstLiveMember(ServerLevel serverLevel, HiveParty.SurfaceSpawn party) {
        for (var uuid : party.materializedMembers().keySet()) {
            var entity = serverLevel.getEntity(uuid);
            if (entity != null && entity.isAlive()) {
                return entity;
            }
        }
        return null;
    }

    private static void tryOpportunisticClaim(
        ServerLevel serverLevel,
        HiveLocation location,
        HiveConfig config,
        ChunkPos candidate,
        long currentTick
    ) {
        if (location.claimedChunks().contains(candidate)) {
            return;
        }
        if (!isFrontierAdjacent(location, candidate)) {
            return;
        }
        var centerChunk = new ChunkPos(location.centerPos());
        if (chebyshev(candidate, centerChunk) > config.maxTerritoryRadiusChunks()) {
            return;
        }
        var owner = HiveLocationRegistry.INSTANCE.getByChunk(location.dimension(), candidate);
        if (owner != null) {
            return;
        }

        var cost = BiomassIncome.claimCost(location, config);
        if (location.biomass() < cost) {
            return;
        }

        if (HiveLocationClaims.claim(serverLevel, location, candidate, currentTick)) {
            location.setBiomass(location.biomass() - cost);
            Alien.LOGGER.info(
                "Hive: surface party opportunistic claim at {} for location {} (cost {})",
                candidate,
                location.id(),
                cost
            );
        }
    }

    private static boolean isFrontierAdjacent(HiveLocation location, ChunkPos candidate) {
        for (var owned : location.claimedChunks()) {
            var dx = Math.abs(owned.x - candidate.x);
            var dz = Math.abs(owned.z - candidate.z);
            if ((dx == 1 && dz == 0) || (dx == 0 && dz == 1)) {
                return true;
            }
        }
        return false;
    }

    private static int chebyshev(ChunkPos a, ChunkPos b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z));
    }

    private static void resolveAtDawn(
        ServerLevel serverLevel,
        HiveLocation location,
        HiveParty.SurfaceSpawn party,
        HiveConfig config
    ) {
        ChunkPos lastKnownChunk = null;

        for (var entry : new ArrayList<>(party.materializedMembers().entrySet())) {
            var entity = serverLevel.getEntity(entry.getKey());
            party.untrackMaterializedMember(entry.getKey());
            if (entity == null || !entity.isAlive()) {
                // Lost in the field — nothing to refund.
                continue;
            }
            lastKnownChunk = new ChunkPos(entity.blockPosition());
            location.localReserves().addReturningMember(entry.getValue(), 1);
            // EGG DUTY: a carrier is refunded but NEVER discarded - discarding it mid-haul vanished the
            // worker and dropped its egg. It stays alive to finish the delivery.
            if (EggDutyGuard.isOnEggDuty(entity)) {
                continue;
            }
            entity.discard();
        }

        // Any never-materialized remainder in composition also refunds.
        for (var type : new ArrayList<>(party.composition().getAvailableEntityTypes())) {
            var count = party.composition().getCount(type);
            if (count <= 0) {
                continue;
            }
            location.localReserves().addReturningMember(type, count);
            party.composition().add(type, -count);
        }

        if (lastKnownChunk != null) {
            maybeDropVentAndResin(serverLevel, location, config, lastKnownChunk);
            dropSurfaceResinPatch(serverLevel, location, lastKnownChunk);
        }

        Alien.LOGGER.info("Hive: surface spawn party resolved at dawn for location {}", location.id());
    }

    private static void maybeDropVentAndResin(
        ServerLevel serverLevel,
        HiveLocation location,
        HiveConfig config,
        ChunkPos chunk
    ) {
        var centerBlock = chunk.getMiddleBlockPosition(0);
        var surfaceY = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerBlock.getX(), centerBlock.getZ());
        var band = config.surfacePartySurfaceBandBlocks();

        var nearSurfaceCount = 0;
        for (var pos : location.ventManager().getVentsWithinChunk(chunk)) {
            if (Math.abs(pos.getY() - surfaceY) <= band) {
                nearSurfaceCount++;
            }
        }
        if (nearSurfaceCount >= config.surfacePartyMaxVentsPerClaim()) {
            Alien.LOGGER.info(
                "Hive: surface party made NO vent at {} for {} - already {} near-surface vents there (cap {}).",
                chunk,
                location.id(),
                nearSurfaceCount,
                config.surfacePartyMaxVentsPerClaim()
            );
            return;
        }

        var variant = location.lineageVariantOrNull();
        if (variant == null) {
            Alien.LOGGER.info(
                "Hive: surface party made NO vent at {} for {} - the location has no lineage variant.",
                chunk,
                location.id()
            );
            return;
        }
        // Roll LAST: burning the 10% chance and THEN bailing on a bad spot wasted the drop entirely.
        if (serverLevel.random.nextDouble() >= config.surfacePartyVentDropChance()) {
            Alien.LOGGER.info(
                "Hive: surface party made NO vent at {} for {} - lost the {}% drop roll.",
                chunk,
                location.id(),
                (int) (config.surfacePartyVentDropChance() * 100)
            );
            return;
        }

        var variantType = AlienVariantTypes.getFor(variant);

        // Search the whole chunk for a placeable surface column, not just the exact centre block: on a hillside
        // (or under a tree / in water) the centre column is rarely sturdy, so the drop silently aborted.
        var ventPos = findSurfaceVentSpot(serverLevel, chunk);
        if (ventPos == null) {
            Alien.LOGGER.info(
                "Hive: surface party made NO vent at {} for {} - no sturdy, open surface column anywhere in the chunk.",
                chunk,
                location.id()
            );
            return;
        }

        serverLevel.setBlock(ventPos, variantType.resinVent().get().defaultBlockState(), 3);
        for (var direction : Direction.Plane.HORIZONTAL) {
            var resinPos = ventPos.relative(direction);
            if (serverLevel.getBlockState(resinPos).isAir()) {
                serverLevel.setBlock(resinPos, variantType.resin().get().defaultBlockState(), 3);
            }
        }

        Alien.LOGGER.info("Hive: surface party dropped a vent + resin at {} for location {}", ventPos, location.id());
    }

    /** A sturdy, open surface column somewhere in the chunk - rings outward from the centre. */
    private static @org.jetbrains.annotations.Nullable BlockPos findSurfaceVentSpot(
        ServerLevel serverLevel,
        ChunkPos chunk
    ) {
        var centre = chunk.getMiddleBlockPosition(0);
        for (int radius = 0; radius <= 7; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    int x = centre.getX() + dx;
                    int z = centre.getZ() + dz;
                    int y = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    var candidate = new BlockPos(x, y, z);
                    var ground = candidate.below();
                    if (!serverLevel.getBlockState(ground).isFaceSturdy(serverLevel, ground, Direction.UP)) {
                        continue;
                    }
                    var state = serverLevel.getBlockState(candidate);
                    if (!state.isAir() && !state.canBeReplaced()) {
                        continue;
                    }
                    return candidate;
                }
            }
        }
        return null;
    }

    private static final int RESIN_PATCH_RADIUS = 5; // blocks around the party's chunk centre

    private static final int RESIN_PATCH_ATTEMPTS = 28; // scatter attempts (~half land on valid ground)

    /**
     * Every surface party leaves its mark: a scattered patch of variant resin across the surface around where it
     * roamed, with a RESIN NODE at the heart - the node is the living spreader (sculk-style), so activity around the
     * site (fighting, movement, death) keeps the infestation creeping outward long after the party is gone. Runs on
     * every wrap-up, independent of the vent roll; repeated parties in an area thicken the patch.
     */
    private static void dropSurfaceResinPatch(ServerLevel serverLevel, HiveLocation location, ChunkPos chunk) {
        var variant = location.lineageVariantOrNull();
        if (variant == null) {
            return;
        }
        var variantType = AlienVariantTypes.getFor(variant);
        var centerBlock = chunk.getMiddleBlockPosition(0);

        // The node at the heart of the patch (skipped if something already occupies the spot - repeated parties
        // in the same chunk just thicken the resin around the existing node).
        var centerY = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerBlock.getX(), centerBlock.getZ());
        var nodePos = new BlockPos(centerBlock.getX(), centerY, centerBlock.getZ());
        var nodeGround = nodePos.below();
        if (
            serverLevel.getBlockState(nodePos).isAir()
                && serverLevel.getBlockState(nodeGround).isFaceSturdy(serverLevel, nodeGround, Direction.UP)
        ) {
            serverLevel.setBlock(nodePos, variantType.resinNode().get().defaultBlockState(), 3);
        }

        int placed = 0;
        for (int i = 0; i < RESIN_PATCH_ATTEMPTS; i++) {
            int x = centerBlock.getX() + serverLevel.random.nextInt(RESIN_PATCH_RADIUS * 2 + 1) - RESIN_PATCH_RADIUS;
            int z = centerBlock.getZ() + serverLevel.random.nextInt(RESIN_PATCH_RADIUS * 2 + 1) - RESIN_PATCH_RADIUS;
            int y = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            var pos = new BlockPos(x, y, z);
            var ground = pos.below();
            if (
                !serverLevel.getBlockState(pos).isAir()
                    || !serverLevel.getBlockState(ground).isFaceSturdy(serverLevel, ground, Direction.UP)
            ) {
                continue;
            }
            serverLevel.setBlock(pos, variantType.resin().get().defaultBlockState(), 3);
            placed++;
        }
        if (placed > 0) {
            Alien.LOGGER.info(
                "Hive: surface party spread {} resin across the surface at {} for location {}",
                placed,
                chunk,
                location.id()
            );
        }
    }
}
