package com.alien.common.gameplay.hive.bootstrap;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.drone.Drone;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.faction.LocationMembership;
import com.alien.common.gameplay.hive.lifecycle.HiveLocationFoundingService;
import com.alien.common.gameplay.hive.lifecycle.SpreadZoneCheck;
import com.alien.common.gameplay.hive.lifecycle.SpreadZoneResult;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.level.saveddata.RoyalBootstrapLeakData;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienSoundEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public final class RoyalBootstrapResolver {

    private static final int TICK_INTERVAL = 20 * 30;

    private static final int ATTEMPTS_PER_VARIANT = 24;

    private static final int MIN_PLAYER_DISTANCE_CHUNKS = 8;

    private static final int MAX_PLAYER_DISTANCE_CHUNKS = 24;

    private static final int OVERWORLD_MAX_Y = -24;

    private static final int REQUIRED_AIR_HEIGHT = 6;

    private static final int REQUIRED_AIR_RADIUS = 2;

    private static final String MESSAGE = "A distant screech echoes from below. Something has taken root...";

    private RoyalBootstrapResolver() {}

    public static void tick(MinecraftServer server) {
        if (server.overworld().getGameTime() % TICK_INTERVAL != 0) {
            return;
        }

        for (var level : server.getAllLevels()) {
            RoyalBootstrapLeakData.getOrCreate(level)
                .ifSome(data -> resolveLevel(level, data));
        }
    }

    private static void resolveLevel(ServerLevel level, RoyalBootstrapLeakData data) {
        var variants = new ArrayList<>(data.getPendingVariants());

        for (var variant : variants) {
            if (data.getCount(variant) <= 0) {
                continue;
            }

            tryResolveVariant(level, data, variant);
        }
    }

    private static void tryResolveVariant(ServerLevel level, RoyalBootstrapLeakData data, AlienVariant variant) {
        var players = level.players()
            .stream()
            .filter(player -> !player.isSpectator())
            .toList();

        if (players.isEmpty()) {
            return;
        }

        for (var attempt = 0; attempt < ATTEMPTS_PER_VARIANT; attempt++) {
            var player = players.get(level.random.nextInt(players.size()));
            var candidateChunk = randomChunkNear(level, player);
            var position = findUndergroundPosition(level, candidateChunk);

            if (position == null) {
                continue;
            }

            if (spawnFounderGroup(level, data, variant, position)) {
                return;
            }
        }
    }

    private static ChunkPos randomChunkNear(ServerLevel level, ServerPlayer player) {
        var playerChunk = player.chunkPosition();
        var dx = randomOffset(level);
        var dz = randomOffset(level);

        while (Math.max(Math.abs(dx), Math.abs(dz)) < MIN_PLAYER_DISTANCE_CHUNKS) {
            dx = randomOffset(level);
            dz = randomOffset(level);
        }

        return new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
    }

    private static int randomOffset(ServerLevel level) {
        var offset = level.random.nextIntBetweenInclusive(MIN_PLAYER_DISTANCE_CHUNKS, MAX_PLAYER_DISTANCE_CHUNKS);
        return level.random.nextBoolean() ? offset : -offset;
    }

    private static BlockPos findUndergroundPosition(ServerLevel level, ChunkPos chunk) {
        var x = chunk.getMinBlockX() + 8;
        var z = chunk.getMinBlockZ() + 8;
        var minY = level.getMinBuildHeight() + 2;
        var maxY = maxSearchY(level);

        for (var y = maxY; y >= minY; y--) {
            var pos = new BlockPos(x, y, z);
            if (isSpawnSpace(level, pos)) {
                return pos;
            }
        }

        return null;
    }

    private static int maxSearchY(ServerLevel level) {
        if (level.dimension() == Level.OVERWORLD) {
            return Math.min(OVERWORLD_MAX_Y, level.getMaxBuildHeight() - REQUIRED_AIR_HEIGHT - 1);
        }
        if (level.dimension() == Level.NETHER) {
            return level.getMaxBuildHeight() - REQUIRED_AIR_HEIGHT - 8;
        }
        return Math.min(48, level.getMaxBuildHeight() - REQUIRED_AIR_HEIGHT - 1);
    }

    private static boolean isSpawnSpace(ServerLevel level, BlockPos pos) {
        var below = pos.below();
        var belowState = level.getBlockState(below);

        if (!belowState.isSolidRender(level, below) || !level.getFluidState(below).isEmpty()) {
            return false;
        }

        for (var dx = -REQUIRED_AIR_RADIUS; dx <= REQUIRED_AIR_RADIUS; dx++) {
            for (var dz = -REQUIRED_AIR_RADIUS; dz <= REQUIRED_AIR_RADIUS; dz++) {
                for (var dy = 0; dy < REQUIRED_AIR_HEIGHT; dy++) {
                    var checkPos = pos.offset(dx, dy, dz);
                    if (!level.isEmptyBlock(checkPos) || !level.getFluidState(checkPos).isEmpty()) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private static boolean spawnFounderGroup(ServerLevel level, RoyalBootstrapLeakData data, AlienVariant variant, BlockPos position) {
        var queenType = Queen.getType(variant);
        var queen = queenType.spawn(level, position, MobSpawnType.MOB_SUMMONED);

        if (!(queen instanceof Queen spawnedQueen)) {
            return false;
        }

        var spreadResult = SpreadZoneCheck.evaluate(spawnedQueen, position);
        if (!(spreadResult instanceof SpreadZoneResult.NewLineage)) {
            spawnedQueen.discard();
            return false;
        }

        var drones = spawnDrones(level, variant, position);
        if (drones.size() != 2) {
            spawnedQueen.discard();
            drones.forEach(Alien::discard);
            return false;
        }

        var locationId = HiveLocationFoundingService.foundNewLineage(spawnedQueen, position);
        var location = locationId == null ? null : HiveLocationRegistry.INSTANCE.get(locationId);
        if (location == null) {
            spawnedQueen.discard();
            drones.forEach(Alien::discard);
            return false;
        }

        spawnedQueen.setPersistenceRequired();
        drones.forEach(drone -> {
            drone.setPersistenceRequired();
            LocationMembership.join(location, drone);
        });

        data.consumeOne(variant);

        notifyPlayers(level, spawnedQueen);
        return true;
    }

    private static List<Alien> spawnDrones(ServerLevel level, AlienVariant variant, BlockPos position) {
        var droneType = Drone.getType(variant);
        var drones = new ArrayList<Alien>();

        for (var i = 0; i < 2; i++) {
            var offset = position.offset(i == 0 ? 2 : -2, 0, i == 0 ? 2 : -2);

            if (!isDroneSpawnSpace(level, offset)) {
                break;
            }

            var drone = droneType.spawn(level, offset, MobSpawnType.MOB_SUMMONED);

            if (drone != null) {
                drones.add(drone);
            }
        }

        return drones;
    }

    private static boolean isDroneSpawnSpace(ServerLevel level, BlockPos pos) {
        var below = pos.below();
        var belowState = level.getBlockState(below);

        return belowState.isSolidRender(level, below)
            && level.getFluidState(below).isEmpty()
            && level.isEmptyBlock(pos)
            && level.getFluidState(pos).isEmpty()
            && level.isEmptyBlock(pos.above())
            && level.getFluidState(pos.above()).isEmpty();
    }

    private static void notifyPlayers(ServerLevel level, Queen queen) {
        for (var player : level.players()) {
            if (player.distanceToSqr(queen) > 256.0D * 256.0D) {
                continue;
            }

            player.playNotifySound(AlienSoundEvents.ENTITY_QUEEN_SCREAM.get(), SoundSource.MASTER, 1.0F, 1.0F);
            player.sendSystemMessage(
                Component.literal(MESSAGE)
                    .withStyle(AlienVariantTypes.getFor(queen).chatColor(), ChatFormatting.ITALIC)
            );
        }
    }
}
