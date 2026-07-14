package com.alien.common.gameplay.hive.party;

import com.alien.Alien;
import com.alien.common.gameplay.hive.config.HiveConfig;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.spawning.ReserveSpawnUtil;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.entity.v1.EntityReserves;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;

/**
 * Dispatch for {@link HiveParty.SurfaceSpawn} — the vent-seeding "general surface spawn" party. Runs from
 * {@link com.alien.common.gameplay.hive.tick.HiveLocationLoadedTickTask}'s existing 20-tick (once-per-second) cadence,
 * piggybacking on the same slot as {@code CatchUpEngine}/{@code AbstractSpreadAttempt} rather than adding a new
 * scan-interval config, since dispatch is naturally self-limiting: one active party per location at a time, and a new
 * one is only attempted once the prior night's has resolved (see {@link SurfacePartyLifecycleTask}).
 */
public final class SurfacePartyDispatch {

    private static final int SPAWN_SEARCH_RADIUS_CHUNKS = 3;

    private SurfacePartyDispatch() {}

    public static void tryRun(MinecraftServer server, HiveLocation location, HiveConfig config) {
        // Three-day cooldown: a party of this kind is an EVENT, not a conveyor belt. Back-to-back dispatches
        // drained the reserves as fast as the hive could breed them, so the population never settled and
        // never got promoted into warriors or prowlers.
        var world = server.getLevel(location.dimension());
        if (world == null) {
            return;
        }
        if (HiveLocation.onPartyCooldown(location.lastSurfacePartyTick(), world.getGameTime())) {
            return;
        }

        var serverLevel = server.getLevel(location.dimension());
        if (serverLevel == null || serverLevel.isDay()) {
            return;
        }

        for (var party : location.parties()) {
            if (party instanceof HiveParty.SurfaceSpawn) {
                // Already has one out for tonight — one at a time per hive.
                return;
            }
        }

        // Size scales with claims but is CAPPED - unbounded scaling put 20+ runners on a large hive.
        var surfaceCap = com.alien.common.gameplay.hive.structure.HiveRouter.isEmpressInfluenced(location)
            ? config.surfacePartyMaxSizeEmpress()
            : config.surfacePartyMaxSize();
        var desiredSize = Math.min(
            surfaceCap,
            Math.max(
                1,
                Math.round(config.surfacePartyBaseSize() + config.surfacePartySizePerClaimedChunk() * location.claimedChunks().size())
            )
        );

        var composition = drainRunners(location, (int) desiredSize);
        if (composition.getCount() <= 0) {
            // Not enough reserves tonight — try again next night.
            return;
        }

        var spawnPos = findSurfaceSpawnPos(serverLevel, location, config);
        if (spawnPos == null) {
            refund(location, composition);
            return;
        }

        var currentTick = serverLevel.getGameTime();
        var party = new HiveParty.SurfaceSpawn(HivePartyId.fresh(), location.id(), location.dimension(), composition, currentTick);

        var spawnedCount = materialize(serverLevel, party, spawnPos);
        if (spawnedCount <= 0) {
            refund(location, composition);
            return;
        }

        location.parties().add(party);
        location.setLastSurfacePartyTick(world.getGameTime());

        Alien.LOGGER.info(
            "Hive: dispatched surface spawn party for location {} — {} runners at {}",
            location.id(),
            spawnedCount,
            spawnPos
        );
    }

    private static EntityReserves drainRunners(HiveLocation location, int desiredCount) {
        var reserves = location.localReserves();
        var composition = new EntityReserves();
        var runnerTypes = new ArrayList<EntityType<?>>();
        for (var type : reserves.getAvailableEntityTypes()) {
            if (type.is(AlienEntityTypeTags.RUNNERS)) {
                runnerTypes.add(type);
            }
        }
        if (runnerTypes.isEmpty()) {
            return composition;
        }

        var drained = 0;
        while (drained < desiredCount) {
            var progressedThisPass = false;
            for (var type : runnerTypes) {
                if (drained >= desiredCount) {
                    break;
                }
                if (reserves.trySpawn(type)) {
                    composition.add(type, 1);
                    drained++;
                    progressedThisPass = true;
                }
            }
            if (!progressedThisPass) {
                break;
            }
        }

        return composition;
    }

    private static void refund(HiveLocation location, EntityReserves composition) {
        for (var type : new ArrayList<>(composition.getAvailableEntityTypes())) {
            var count = composition.getCount(type);
            if (count <= 0) {
                continue;
            }
            location.localReserves().addReturningMember(type, count);
            composition.add(type, -count);
        }
    }

    private static BlockPos findSurfaceSpawnPos(ServerLevel level, HiveLocation location, HiveConfig config) {
        var claimed = new ArrayList<>(location.claimedChunks());
        if (claimed.isEmpty()) {
            return null;
        }
        var origin = claimed.get(level.random.nextInt(claimed.size()));
        var originBlock = origin.getMiddleBlockPosition(0);

        for (var radius = 0; radius <= SPAWN_SEARCH_RADIUS_CHUNKS; radius++) {
            for (var dx = -radius; dx <= radius; dx++) {
                for (var dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    var x = originBlock.getX() + dx * 16 + level.random.nextInt(16);
                    var z = originBlock.getZ() + dz * 16 + level.random.nextInt(16);
                    var surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    var pos = new BlockPos(x, surfaceY, z);
                    if (isValidSurfaceSpawnPos(level, pos)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isValidSurfaceSpawnPos(ServerLevel level, BlockPos pos) {
        if (!level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        var groundPos = pos.below();
        if (!level.getBlockState(groundPos).isFaceSturdy(level, groundPos, Direction.UP)) {
            return false;
        }
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
            return false;
        }
        return level.canSeeSky(pos);
    }

    private static int materialize(ServerLevel level, HiveParty.SurfaceSpawn party, BlockPos spawnPos) {
        var spawnedCount = 0;
        for (var type : new ArrayList<>(party.composition().getAvailableEntityTypes())) {
            var count = party.composition().getCount(type);
            for (var i = 0; i < count; i++) {
                var entity = type.create(level);
                if (entity == null) {
                    continue;
                }
                var jitterX = spawnPos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 4.0;
                var jitterZ = spawnPos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 4.0;
                entity.moveTo(jitterX, spawnPos.getY(), jitterZ, level.random.nextFloat() * 360.0F, 0.0F);
                if (entity instanceof Mob mob) {
                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.MOB_SUMMONED, null);
                    mob.setPersistenceRequired();
                }
                level.addFreshEntityWithPassengers(entity);
                ReserveSpawnUtil.markSpawnedFromReserves(entity);
                party.trackMaterializedMember(entity.getUUID(), type);
                party.composition().add(type, -1);
                spawnedCount++;
            }
        }
        return spawnedCount;
    }
}
