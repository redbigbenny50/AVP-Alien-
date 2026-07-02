package com.alien.common.gameplay.hive.convoy;

import com.alien.Alien;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.spawning.ReserveSpawnUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ConvoyMaterialization {

    private static final int RAID_LAST_WAVE_INDEX = Convoy.Raid.WAVE_COUNT - 1;

    private static final int SPAWN_SEARCH_RADIUS_BLOCKS = 8;

    private static final int RAID_SPAWN_SEARCH_RADIUS_BLOCKS = 14;

    private static final int SPAWN_SEARCH_BLOCKS_ABOVE_ORIGIN = 12;

    private static final double RAID_SPAWN_ARC_RADIANS = Math.toRadians(115.0);

    private static final int RAID_MIN_SPAWN_DISTANCE_BLOCKS = 14;

    private static final int RAID_MAX_SPAWN_DISTANCE_BLOCKS = 24;

    private ConvoyMaterialization() {}

    static int spawnAll(ServerLevel level, Convoy convoy, BlockPos spawnPos, ServerPlayer targetPlayer) {
        return spawnEntities(level, convoy, expandComposition(convoy), spawnPos, targetPlayer);
    }

    static int spawnNextRaidWave(
        ServerLevel level,
        Convoy.Raid raid,
        RaidWaveProfile waveProfile,
        BlockPos spawnPos,
        ServerPlayer targetPlayer,
        long currentTick
    ) {
        if (raid.composition().getCount() <= 0) {
            return 0;
        }

        var waveIndex = Math.min(raid.nextWaveIndex(), RAID_LAST_WAVE_INDEX);
        var waveConfig = waveProfile.wave(waveIndex);
        if (!raid.canSpawnWave(currentTick, waveConfig.bufferTicks())) {
            if (raid.waveBreakStartedTick() < 0L && raid.materializedMembers().isEmpty()) {
                raid.startWaveBreak(currentTick);
            }
            return 0;
        }

        var wave = selectRaidWave(level, raid, waveProfile);
        if (wave.isEmpty()) {
            return 0;
        }

        var spawnedCount = spawnRaidWaveEntities(level, raid, wave, spawnPos, targetPlayer);
        if (spawnedCount > 0) {
            raid.beginWave(waveIndex, spawnedCount);
        }
        return spawnedCount;
    }

    private static List<EntityType<?>> selectRaidWave(ServerLevel level, Convoy.Raid raid, RaidWaveProfile waveProfile) {
        var waveIndex = Math.min(raid.nextWaveIndex(), RAID_LAST_WAVE_INDEX);
        var waveConfig = waveProfile.wave(waveIndex);
        var desiredCount = Math.min(waveConfig.size(), raid.composition().getCount());
        var selected = new ArrayList<EntityType<?>>(desiredCount);
        var selectedCounts = new HashMap<EntityType<?>, Integer>();
        var inventory = new RaidWaveSelection.Inventory() {

            @Override
            public Iterable<EntityType<?>> availableTypes() {
                return raid.composition().getAvailableEntityTypes();
            }

            @Override
            public int count(EntityType<?> type) {
                return Math.max(0, raid.composition().getCount(type) - selectedCounts.getOrDefault(type, 0));
            }
        };

        for (var guarantee : waveConfig.guaranteed()) {
            if (
                !selectFromPools(
                    selected,
                    selectedCounts,
                    inventory,
                    guarantee.pools(),
                    guarantee.count(),
                    level
                )
            ) {
                return List.of();
            }
        }

        if (
            !selectFromPools(
                selected,
                selectedCounts,
                inventory,
                waveConfig.pools(),
                desiredCount - selected.size(),
                level
            )
        ) {
            return List.of();
        }

        return selected;
    }

    private static boolean selectFromPools(
        List<EntityType<?>> selected,
        HashMap<EntityType<?>, Integer> selectedCounts,
        RaidWaveSelection.Inventory inventory,
        List<RaidWaveProfile.PoolEntry> pools,
        int count,
        ServerLevel level
    ) {
        var selectedByPool = new HashMap<Integer, Integer>();
        for (var i = 0; i < count; i++) {
            var type = RaidWaveSelection.chooseType(pools, inventory, selectedByPool, level.random);
            if (type == null) {
                return false;
            }
            selected.add(type);
            selectedCounts.merge(type, 1, Integer::sum);
        }

        return true;
    }

    private static List<EntityType<?>> expandComposition(Convoy convoy) {
        var expanded = new ArrayList<EntityType<?>>();
        for (var entityType : new ArrayList<>(convoy.composition().getAvailableEntityTypes())) {
            var count = convoy.composition().getCount(entityType);
            for (var i = 0; i < count; i++) {
                expanded.add(entityType);
            }
        }
        return expanded;
    }

    private static int spawnEntities(
        ServerLevel level,
        Convoy convoy,
        List<EntityType<?>> entityTypes,
        BlockPos spawnPos,
        ServerPlayer targetPlayer
    ) {
        var spawnedCount = 0;
        for (var entityType : entityTypes) {
            var spawned = spawnRelaxed(entityType, level, spawnPos, null);
            if (spawned == null) {
                continue;
            }

            ReserveSpawnUtil.markSpawnedFromReserves(spawned);
            if (spawned instanceof Mob mob) {
                mob.setTarget(targetPlayer);
                if (!(convoy instanceof Convoy.Raid)) {
                    mob.setPersistenceRequired();
                }
            }
            ConvoyMemberTracker.markSpawned(convoy, spawned);

            convoy.composition().add(entityType, -1);
            spawnedCount++;
        }
        return spawnedCount;
    }

    private static int spawnRaidWaveEntities(
        ServerLevel level,
        Convoy.Raid raid,
        List<EntityType<?>> entityTypes,
        BlockPos spawnPos,
        ServerPlayer targetPlayer
    ) {
        var origins = raidSpawnOrigins(level, raid, spawnPos, targetPlayer, entityTypes.size());
        var usedSpawnPositions = new HashSet<BlockPos>();
        var spawnedCount = 0;

        for (var i = 0; i < entityTypes.size(); i++) {
            var entityType = entityTypes.get(i);
            var origin = origins.get(i);
            var spawned = spawnRaidMember(entityType, level, origin, targetPlayer, usedSpawnPositions);
            if (spawned == null) {
                continue;
            }

            ReserveSpawnUtil.markSpawnedFromReserves(spawned);
            if (spawned instanceof Mob mob) {
                mob.setTarget(targetPlayer);
            }
            ConvoyMemberTracker.markSpawned(raid, spawned);

            raid.composition().add(entityType, -1);
            spawnedCount++;
        }

        var failedCount = entityTypes.size() - spawnedCount;
        if (failedCount > 0) {
            Alien.LOGGER.info(
                "Hive: raid {} could not place {} of {} wave member(s) near {}",
                raid.id(),
                failedCount,
                entityTypes.size(),
                targetPlayer == null ? spawnPos.toShortString() : targetPlayer.blockPosition().toShortString()
            );
        }

        return spawnedCount;
    }

    private static List<BlockPos> raidSpawnOrigins(
        ServerLevel level,
        Convoy.Raid raid,
        BlockPos fallbackOrigin,
        @Nullable ServerPlayer targetPlayer,
        int count
    ) {
        var targetPos = targetPlayer == null ? fallbackOrigin : targetPlayer.blockPosition();
        var baseAngle = raidApproachAngle(raid, targetPos, fallbackOrigin);
        var origins = new ArrayList<BlockPos>(count);
        var distanceRange = RAID_MAX_SPAWN_DISTANCE_BLOCKS - RAID_MIN_SPAWN_DISTANCE_BLOCKS;

        for (var i = 0; i < count; i++) {
            var fraction = count <= 1 ? 0.5 : i / (double) (count - 1);
            var angleJitter = (level.random.nextDouble() - 0.5) * 0.28;
            var angle = baseAngle + (fraction - 0.5) * RAID_SPAWN_ARC_RADIANS + angleJitter;
            var distance = RAID_MIN_SPAWN_DISTANCE_BLOCKS
                + (distanceRange <= 0 ? 0 : level.random.nextInt(distanceRange + 1));

            var x = targetPos.getX() + (int) Math.round(Math.cos(angle) * distance);
            var z = targetPos.getZ() + (int) Math.round(Math.sin(angle) * distance);
            origins.add(new BlockPos(x, targetPos.getY(), z));
        }

        return origins;
    }

    private static double raidApproachAngle(Convoy.Raid raid, BlockPos targetPos, BlockPos fallbackOrigin) {
        var source = HiveLocationRegistry.INSTANCE.get(raid.sourceLocationId());
        var sourcePos = source == null ? fallbackOrigin : source.centerPos();
        var dx = sourcePos.getX() - targetPos.getX();
        var dz = sourcePos.getZ() - targetPos.getZ();
        if (dx == 0 && dz == 0) {
            dx = fallbackOrigin.getX() - targetPos.getX();
            dz = fallbackOrigin.getZ() - targetPos.getZ();
        }
        if (dx == 0 && dz == 0) {
            return 0.0;
        }
        return Math.atan2(dz, dx);
    }

    private static @Nullable Entity spawnRaidMember(
        EntityType<?> entityType,
        ServerLevel level,
        BlockPos origin,
        @Nullable ServerPlayer targetPlayer,
        Set<BlockPos> usedSpawnPositions
    ) {
        var targetPos = targetPlayer == null ? origin : targetPlayer.blockPosition();
        var spawnPos = findRaidGroundSpawnPos(entityType, level, origin, targetPos, usedSpawnPositions);
        if (spawnPos == null) {
            return null;
        }

        var entity = createSpawnedEntity(entityType, level, spawnPos);
        if (entity != null) {
            usedSpawnPositions.add(spawnPos);
        }
        return entity;
    }

    private static @Nullable Entity spawnRelaxed(
        EntityType<?> entityType,
        ServerLevel level,
        BlockPos origin,
        @Nullable Set<BlockPos> usedSpawnPositions
    ) {
        var spawnPos = findGroundSpawnPos(entityType, level, origin, usedSpawnPositions);
        if (spawnPos == null) {
            return null;
        }

        var entity = createSpawnedEntity(entityType, level, spawnPos);
        if (entity != null && usedSpawnPositions != null) {
            usedSpawnPositions.add(spawnPos);
        }
        return entity;
    }

    private static @Nullable Entity createSpawnedEntity(EntityType<?> entityType, ServerLevel level, BlockPos spawnPos) {
        var entity = entityType.create(level);
        if (entity == null) {
            return null;
        }

        entity.moveTo(
            spawnPos.getX() + 0.5,
            spawnPos.getY(),
            spawnPos.getZ() + 0.5,
            level.random.nextFloat() * 360.0F,
            0.0F
        );
        if (entity instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.MOB_SUMMONED, null);
        }
        level.addFreshEntityWithPassengers(entity);
        return entity;
    }

    private static @Nullable BlockPos findRaidGroundSpawnPos(
        EntityType<?> entityType,
        ServerLevel level,
        BlockPos origin,
        BlockPos targetPos,
        Set<BlockPos> usedSpawnPositions
    ) {
        var preferred = findRaidGroundSpawnPos(entityType, level, origin, targetPos, usedSpawnPositions, true);
        if (preferred != null) {
            return preferred;
        }
        return findRaidGroundSpawnPos(entityType, level, origin, targetPos, usedSpawnPositions, false);
    }

    private static @Nullable BlockPos findRaidGroundSpawnPos(
        EntityType<?> entityType,
        ServerLevel level,
        BlockPos origin,
        BlockPos targetPos,
        Set<BlockPos> usedSpawnPositions,
        boolean requireClearApproach
    ) {
        for (var radius = 0; radius <= RAID_SPAWN_SEARCH_RADIUS_BLOCKS; radius++) {
            for (var dx = -radius; dx <= radius; dx++) {
                for (var dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }

                    var columnOrigin = origin.offset(dx, 0, dz);
                    var pos = findRaidGroundSpawnPosInColumn(
                        entityType,
                        level,
                        columnOrigin,
                        targetPos,
                        usedSpawnPositions,
                        requireClearApproach
                    );
                    if (pos != null) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private static @Nullable BlockPos findRaidGroundSpawnPosInColumn(
        EntityType<?> entityType,
        ServerLevel level,
        BlockPos origin,
        BlockPos targetPos,
        Set<BlockPos> usedSpawnPositions,
        boolean requireClearApproach
    ) {
        var minY = level.getMinBuildHeight() + 1;
        var maxY = level.getMaxBuildHeight() - 2;
        var startY = Math.clamp(origin.getY() + SPAWN_SEARCH_BLOCKS_ABOVE_ORIGIN, minY, maxY);

        for (var y = startY; y >= minY; y--) {
            var pos = new BlockPos(origin.getX(), y, origin.getZ());
            if (isUsedRaidSpawnPocket(pos, usedSpawnPositions)) {
                continue;
            }
            if (isValidRaidGroundSpawnPos(entityType, level, pos, targetPos, requireClearApproach)) {
                return pos;
            }
        }
        return null;
    }

    private static @Nullable BlockPos findGroundSpawnPos(
        EntityType<?> entityType,
        ServerLevel level,
        BlockPos origin,
        @Nullable Set<BlockPos> usedSpawnPositions
    ) {
        for (var radius = 0; radius <= SPAWN_SEARCH_RADIUS_BLOCKS; radius++) {
            for (var dx = -radius; dx <= radius; dx++) {
                for (var dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }

                    var columnOrigin = origin.offset(dx, 0, dz);
                    var pos = findGroundSpawnPosInColumn(entityType, level, columnOrigin, usedSpawnPositions);
                    if (pos != null) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private static @Nullable BlockPos findGroundSpawnPosInColumn(
        EntityType<?> entityType,
        ServerLevel level,
        BlockPos origin,
        @Nullable Set<BlockPos> usedSpawnPositions
    ) {
        var minY = level.getMinBuildHeight() + 1;
        var maxY = level.getMaxBuildHeight() - 2;
        var startY = Math.clamp(origin.getY() + SPAWN_SEARCH_BLOCKS_ABOVE_ORIGIN, minY, maxY);

        for (var y = startY; y >= minY; y--) {
            var pos = new BlockPos(origin.getX(), y, origin.getZ());
            if (usedSpawnPositions != null && usedSpawnPositions.contains(pos)) {
                continue;
            }
            if (isValidGroundSpawnPos(entityType, level, pos)) {
                return pos;
            }
        }
        return null;
    }

    private static boolean isValidGroundSpawnPos(EntityType<?> entityType, ServerLevel level, BlockPos pos) {
        if (!level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }

        var groundPos = pos.below();
        if (!level.getBlockState(groundPos).isFaceSturdy(level, groundPos, Direction.UP)) {
            return false;
        }

        return level.noCollision(entityType.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
    }

    private static boolean isValidRaidGroundSpawnPos(
        EntityType<?> entityType,
        ServerLevel level,
        BlockPos pos,
        BlockPos targetPos,
        boolean requireClearApproach
    ) {
        if (!isValidGroundSpawnPos(entityType, level, pos)) {
            return false;
        }
        if (!hasHeadroom(level, pos)) {
            return false;
        }
        if (!hasUsableEscape(entityType, level, pos)) {
            return false;
        }
        return !requireClearApproach || hasClearHorizontalApproach(entityType, level, pos, targetPos);
    }

    private static boolean hasHeadroom(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
            && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    private static boolean hasUsableEscape(EntityType<?> entityType, ServerLevel level, BlockPos pos) {
        for (var direction : Direction.Plane.HORIZONTAL) {
            if (canOccupy(entityType, level, pos.relative(direction))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasClearHorizontalApproach(
        EntityType<?> entityType,
        ServerLevel level,
        BlockPos pos,
        BlockPos targetPos
    ) {
        var dx = Integer.compare(targetPos.getX(), pos.getX());
        var dz = Integer.compare(targetPos.getZ(), pos.getZ());
        if (dx == 0 && dz == 0) {
            return true;
        }
        if (dx != 0 && canOccupy(entityType, level, pos.offset(dx, 0, 0))) {
            return true;
        }
        if (dz != 0 && canOccupy(entityType, level, pos.offset(0, 0, dz))) {
            return true;
        }
        return dx != 0 && dz != 0 && canOccupy(entityType, level, pos.offset(dx, 0, dz));
    }

    private static boolean canOccupy(EntityType<?> entityType, ServerLevel level, BlockPos pos) {
        if (!level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }

        var groundPos = pos.below();
        if (!level.getBlockState(groundPos).isFaceSturdy(level, groundPos, Direction.UP)) {
            return false;
        }

        return hasHeadroom(level, pos) && level.noCollision(spawnAabb(entityType, pos));
    }

    private static AABB spawnAabb(EntityType<?> entityType, BlockPos pos) {
        return entityType.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    private static boolean isUsedRaidSpawnPocket(BlockPos pos, Set<BlockPos> usedSpawnPositions) {
        for (var used : usedSpawnPositions) {
            if (used.distSqr(pos) <= 2.0D) {
                return true;
            }
        }
        return false;
    }
}
