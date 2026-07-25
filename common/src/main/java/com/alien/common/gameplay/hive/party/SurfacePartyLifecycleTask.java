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
        // Hive-rhythm day (synthetic in fixed-time dimensions like the Nether, or dawn would never come and
        // parties would never resolve).
        var isDay = com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.isHiveDay(serverLevel);

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
        BlockPos lastKnownPos = null;

        for (var entry : new ArrayList<>(party.materializedMembers().entrySet())) {
            var entity = serverLevel.getEntity(entry.getKey());
            party.untrackMaterializedMember(entry.getKey());
            if (entity == null) {
                // NOT loaded - and not dead: real deaths are untracked at the moment of death (PartyMemberDeath).
                // Writing off every out-of-range member is what quietly drained the hive on each dispatch.
                location.localReserves().addReturningMember(entry.getValue(), 1);
                continue;
            }
            if (!entity.isAlive()) {
                continue; // died this tick, before its death hook untracked it
            }
            lastKnownChunk = new ChunkPos(entity.blockPosition());
            // The member is STANDING here, so this spot is by definition reachable-and-standable ground - the vent
            // should anchor to it, not to the chunk's heightmap crest (which in spiky terrain is the top of an ice
            // spire the party ran along the base of but could never climb).
            lastKnownPos = entity.blockPosition();
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
            maybeDropVentAndResin(serverLevel, location, config, lastKnownChunk, lastKnownPos);
            dropSurfaceResinPatch(serverLevel, location, lastKnownChunk);
        }

        Alien.LOGGER.info("Hive: surface spawn party resolved at dawn for location {}", location.id());
    }

    private static void maybeDropVentAndResin(
        ServerLevel serverLevel,
        HiveLocation location,
        HiveConfig config,
        ChunkPos chunk,
        BlockPos anchor
    ) {
        // Count the SURFACE vents actually recorded in this chunk. This used to re-derive "near the surface" from the
        // heightmap, which also counted interior and frontier vents that happened to sit high in their own column.
        var surfaceVentCount = 0;
        for (var pos : location.ventManager().getVentsWithinChunk(chunk)) {
            if (location.ventManager().isKind(pos, com.alien.common.gameplay.hive.vent.VentKind.SURFACE)) {
                surfaceVentCount++;
            }
        }
        if (surfaceVentCount >= config.surfacePartyMaxVentsPerClaim()) {
            Alien.LOGGER.info(
                "Hive: surface party made NO vent at {} for {} - already {} surface vents there (cap {}).",
                chunk,
                location.id(),
                surfaceVentCount,
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
        // PRIORITY STRUCTURE VENT (100% - skips the drop roll): a profile-tagged structure within reach of the
        // party (Nether: bastion remnants - the piglin larder) gets a GUARANTEED vent so host parties gain a
        // permanent door into it. One vent per structure: skipped while any SURFACE vent already sits inside the
        // structure's bounds, so the guarantee never stacks. Deliberately ignores the per-claim vent cap - the
        // structure chunk is its own claim, and the one-per-structure guard is the real limiter.
        var dimensionProfile = com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.get(serverLevel);
        if (
            dimensionProfile.priorityVentStructures() != null
                && tryPlacePriorityStructureVent(serverLevel, location, chunk, anchor, dimensionProfile)
        ) {
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
        var ventPos = findSurfaceVentSpot(serverLevel, chunk, anchor, location.lineageVariantOrNull());
        if (ventPos == null) {
            Alien.LOGGER.info(
                "Hive: surface party made NO vent at {} for {} - no sturdy, open surface column anywhere in the chunk.",
                chunk,
                location.id()
            );
            return;
        }

        // Same geometry as every other vent now: it sits on the surface of the ground, webbed on every side touching
        // air, with resin creeping over the rock around it. The old ring-of-resin collar is gone.
        com.alien.common.gameplay.hive.vent.VentPlacement.place(
            serverLevel,
            ventPos,
            variantType,
            com.alien.common.gameplay.hive.vent.VentKind.SURFACE,
            location
        );

        Alien.LOGGER.info("Hive: surface party dropped a SURFACE vent at {} for location {}", ventPos, location.id());
    }

    /**
     * How far, in Y, a vent column may sit from where the party actually stood. A surface vent is a DOORWAY between the
     * hive and the world; a party member reached the anchor on foot, so a vent within a few blocks of that Y is
     * reachable too. A column whose surface is far above (an ice spire, a cliff the party skirted the base of) is
     * rejected: the aliens could get to the anchor but never climb to the vent, and every host-carry would strand at
     * its foot. Generous enough to allow a normal hillside step-up.
     */
    private static final int MAX_VENT_Y_DROP = 5;

    /**
     * Player base protection (vanilla-style spawn rules for the scout party and the hive's doorways). Neither the
     * runner scout party ({@code SurfacePartyDispatch}) nor a surface vent may appear where BLOCK light exceeds this
     * (torches, lanterns, glowstone - deliberately not sky light, or daylight would forbid all surface activity
     * everywhere), nor within {@link #VENT_PROTECTED_PLAYER_RADIUS} blocks of a player. Since every later surface party
     * (host, biomass, attack, defense) emerges through surface vents, lighting an area to 8+ keeps the scouts, the
     * hive's doors, and therefore its parties out of it permanently.
     */
    static final int MAX_VENT_BLOCK_LIGHT = 7;

    static final double VENT_PROTECTED_PLAYER_RADIUS = 32.0;

    /** How far out (chunks, chebyshev) a wrapping party looks for a priority structure to vent. */
    private static final int PRIORITY_STRUCTURE_SEARCH_RADIUS_CHUNKS = 3;

    /**
     * The 100% structure vent: searches the ring around the party's wrap-up chunk for a piece of a profile-tagged
     * structure (probing several Y levels per column - bastions are tall), refuses if any SURFACE vent already sits
     * inside that structure's bounds, and otherwise plants a guaranteed vent on a legal spot inside the structure chunk
     * (all normal vent rules - shelf surface, base protection, lava safety for non-fireproof strains - still apply).
     * Returns true only when a vent was actually placed.
     */
    private static boolean tryPlacePriorityStructureVent(
        ServerLevel serverLevel,
        com.alien.common.gameplay.hive.location.HiveLocation location,
        ChunkPos partyChunk,
        @org.jetbrains.annotations.Nullable BlockPos anchor,
        com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.Profile profile
    ) {
        var variant = location.lineageVariantOrNull();
        if (variant == null) {
            return false;
        }
        var nearY = anchor != null ? anchor.getY() : location.centerPos().getY();
        var midY = (profile.depthMinY() + profile.depthMaxY()) / 2;
        int[] probeYs = { nearY, midY, profile.depthMaxY() - 12, profile.depthMinY() + 12 };

        for (var radius = 0; radius <= PRIORITY_STRUCTURE_SEARCH_RADIUS_CHUNKS; radius++) {
            for (var dx = -radius; dx <= radius; dx++) {
                for (var dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    var probeChunk = new ChunkPos(partyChunk.x + dx, partyChunk.z + dz);
                    if (!serverLevel.hasChunk(probeChunk.x, probeChunk.z)) {
                        continue;
                    }

                    net.minecraft.world.level.levelgen.structure.StructureStart hit = null;
                    for (var probeY : probeYs) {
                        var probe = probeChunk.getMiddleBlockPosition(probeY);
                        var start = serverLevel.structureManager()
                            .getStructureWithPieceAt(probe, profile.priorityVentStructures());
                        if (start.isValid()) {
                            hit = start;
                            break;
                        }
                    }
                    if (hit == null) {
                        continue;
                    }

                    // One door per larder: any existing SURFACE vent inside the structure's bounds means it is
                    // already served - fall through to the ordinary drop roll instead.
                    var bounds = hit.getBoundingBox();
                    for (var vent : location.ventManager().ventsOfKind(com.alien.common.gameplay.hive.vent.VentKind.SURFACE)) {
                        if (bounds.isInside(vent)) {
                            return false;
                        }
                    }

                    var anchorY = com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.surfaceY(
                        serverLevel,
                        profile,
                        probeChunk.getMiddleBlockPosition(0).getX(),
                        probeChunk.getMiddleBlockPosition(0).getZ(),
                        nearY
                    );
                    if (anchorY == com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.NO_SURFACE) {
                        continue;
                    }
                    var structureAnchor = new BlockPos(
                        probeChunk.getMiddleBlockPosition(0).getX(),
                        anchorY,
                        probeChunk.getMiddleBlockPosition(0).getZ()
                    );
                    var ventPos = findSurfaceVentSpot(serverLevel, probeChunk, structureAnchor, variant);
                    if (ventPos == null) {
                        continue;
                    }

                    com.alien.common.gameplay.hive.vent.VentPlacement.place(
                        serverLevel,
                        ventPos,
                        AlienVariantTypes.getFor(variant),
                        com.alien.common.gameplay.hive.vent.VentKind.SURFACE,
                        location
                    );
                    Alien.LOGGER.info(
                        "Hive: surface party planted a GUARANTEED structure vent at {} for {} (priority structure at {})",
                        ventPos,
                        location.id(),
                        probeChunk
                    );
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A sturdy, open surface column near where the party actually stood. Rings outward from the ANCHOR (a live member's
     * position), not the chunk centre, and rejects any column more than {@link #MAX_VENT_Y_DROP} off the anchor's Y so
     * the vent can never land atop an unreachable spire.
     */
    private static @org.jetbrains.annotations.Nullable BlockPos findSurfaceVentSpot(
        ServerLevel serverLevel,
        ChunkPos chunk,
        @org.jetbrains.annotations.Nullable BlockPos anchor,
        @org.jetbrains.annotations.Nullable com.alien.common.model.alien.variant.AlienVariant variant
    ) {
        var centre = anchor != null ? anchor : chunk.getMiddleBlockPosition(0);
        var profile = com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.get(serverLevel);
        var lavaSafety = com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.needsLavaSafety(profile, variant);
        for (int radius = 0; radius <= 7; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    int x = centre.getX() + dx;
                    int z = centre.getZ() + dz;
                    int y = com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.surfaceY(
                        serverLevel,
                        profile,
                        x,
                        z,
                        centre.getY()
                    );
                    if (y == com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.NO_SURFACE) {
                        continue;
                    }
                    // Reject a column whose surface is far off the anchor's Y - a spire top or a deep pit the party
                    // could reach but not climb between. Skipped when there is no anchor (legacy callers).
                    if (anchor != null && Math.abs(y - anchor.getY()) > MAX_VENT_Y_DROP) {
                        continue;
                    }
                    var candidate = new BlockPos(x, y, z);
                    // Base protection: lit-up ground (block light 8+) and ground within 32 blocks of a player
                    // never receives a vent - see MAX_VENT_BLOCK_LIGHT.
                    if (serverLevel.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, candidate) > MAX_VENT_BLOCK_LIGHT) {
                        continue;
                    }
                    if (serverLevel.hasNearbyAlivePlayer(x + 0.5, y, z + 0.5, VENT_PROTECTED_PLAYER_RADIUS)) {
                        continue;
                    }
                    // Lava safety: a non-fireproof strain's DOORWAY never opens beside lava.
                    if (
                        lavaSafety && !com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.isLavaSafe(serverLevel, candidate, 2)
                    ) {
                        continue;
                    }
                    var ground = candidate.below();
                    if (!serverLevel.getBlockState(ground).isFaceSturdy(serverLevel, ground, Direction.UP)) {
                        continue;
                    }
                    var state = serverLevel.getBlockState(candidate);
                    // Surface parties RESIN the ground they claim, so the next party finds that ground covered in
                    // the hive's own resin. Treating resin as an obstruction meant a resined hillside could never
                    // host a vent again - the party spread resin every time and dropped a vent never.
                    if (
                        !state.isAir()
                            && !state.canBeReplaced()
                            && !state.is(com.alien.common.registry.init.block.AlienResinBlocks.RESIN_VEIN.get())
                            && !state.is(com.alien.common.registry.init.block.AlienResinBlocks.RESIN_WEB.get())
                    ) {
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
        var profile = com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.get(serverLevel);
        var lavaSafety = com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.needsLavaSafety(profile, variant);

        // The node at the heart of the patch (skipped if something already occupies the spot - repeated parties
        // in the same chunk just thicken the resin around the existing node).
        var centerY = com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.surfaceY(
            serverLevel,
            profile,
            centerBlock.getX(),
            centerBlock.getZ(),
            location.centerPos().getY()
        );
        if (centerY == com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.NO_SURFACE) {
            return;
        }
        var nodePos = new BlockPos(centerBlock.getX(), centerY, centerBlock.getZ());
        var nodeGround = nodePos.below();
        if (
            serverLevel.getBlockState(nodePos).isAir()
                && serverLevel.getBlockState(nodeGround).isFaceSturdy(serverLevel, nodeGround, Direction.UP)
                && !(lavaSafety && !com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.isLavaSafe(serverLevel, nodePos, 2))
        ) {
            serverLevel.setBlock(nodePos, variantType.resinNode().get().defaultBlockState(), 3);
        }

        int placed = 0;
        for (int i = 0; i < RESIN_PATCH_ATTEMPTS; i++) {
            int x = centerBlock.getX() + serverLevel.random.nextInt(RESIN_PATCH_RADIUS * 2 + 1) - RESIN_PATCH_RADIUS;
            int z = centerBlock.getZ() + serverLevel.random.nextInt(RESIN_PATCH_RADIUS * 2 + 1) - RESIN_PATCH_RADIUS;
            int y = com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.surfaceY(serverLevel, profile, x, z, centerY);
            if (y == com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.NO_SURFACE) {
                continue;
            }
            var pos = new BlockPos(x, y, z);
            if (lavaSafety && !com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.isLavaSafe(serverLevel, pos, 1)) {
                continue;
            }
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
