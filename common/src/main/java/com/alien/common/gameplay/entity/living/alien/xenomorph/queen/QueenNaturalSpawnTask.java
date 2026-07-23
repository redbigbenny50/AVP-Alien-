package com.alien.common.gameplay.entity.living.alien.xenomorph.queen;

import com.alien.Alien;
import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.hive.economy.CasteResolver;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.level.saveddata.QueenSpawnChunkData;
import com.alien.common.gameplay.level.saveddata.StrainLeakData;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;

/**
 * Exploration-driven natural queen spawning. Each chunk gets exactly ONE roll, ever (consumed chunks go into the
 * permanent {@link QueenSpawnChunkData} blacklist), so wild queens appear as players push into fresh territory —
 * never on ground that has already been walked and judged.
 *
 * <p>A roll can only produce a queen when every rule holds:</p>
 * <ul>
 *   <li><b>Strain-dimension rules:</b> normal queens spawn only in the overworld, nether queens only in the Nether.
 *       Leaked strains (see {@link StrainLeakData}) spawn in the dimension their leak was recorded in; normal-strain
 *       queens additionally get a rare AMBIENT chance in the overworld with no leak required.</li>
 *   <li><b>Space:</b> no hive location within {@link #MIN_DISTANCE_FROM_HIVES_CHUNKS} chunks (matches the wild
 *       adoption reach), no other wild queen within {@link #MIN_DISTANCE_BETWEEN_WILD_QUEENS_CHUNKS} chunks, no
 *       player within {@link #MIN_PLAYER_DISTANCE_BLOCKS} blocks.</li>
 *   <li><b>Placement:</b> the existing {@link QueenSpawning#checkSpawnRules} (darkness, difficulty, unclaimed
 *       territory) plus the deep-Y band (Y &le; -24 outside the Nether).</li>
 * </ul>
 *
 * <p>On success the queen takes root via {@code beginWildHibernation()} — found sleeping, roused by solid hits,
 * permanently woken only by accumulated player activity (3 loaded Minecraft days) or lineage adoption — and the
 * world-level 5-minute spawn cooldown stamps to prevent bursts.</p>
 */
public class QueenNaturalSpawnTask {

    private static final int RUN_INTERVAL_TICKS = 100;

    private static final int CHUNK_SAMPLES_PER_RUN = 8;

    /** One-in-N chance that a fresh chunk's single lifetime roll even attempts a queen. */
    private static final int SPAWN_ROLL_ONE_IN = 300;

    /** Sub-roll for AMBIENT normal-strain eligibility (applied on top of the chunk roll; leak-gated strains skip it). */
    private static final int AMBIENT_NORMAL_ONE_IN = 4;

    private static final int MIN_DISTANCE_FROM_HIVES_CHUNKS = 32;

    private static final int MIN_DISTANCE_BETWEEN_WILD_QUEENS_CHUNKS = 64;

    private static final double MIN_PLAYER_DISTANCE_BLOCKS = 48.0;

    private static final int MAX_OVERWORLD_SPAWN_Y = -24;

    private static final int POSITION_SAMPLES_PER_CHUNK = 24;

    public static void tick(ServerLevel serverLevel) {
        if (serverLevel.getGameTime() % RUN_INTERVAL_TICKS != 0 || serverLevel.players().isEmpty()) {
            return;
        }

        var data = QueenSpawnChunkData.getOrCreate(serverLevel).unwrap();
        if (data.getSpawnCooldown().isActive()) {
            return;
        }

        for (var i = 0; i < CHUNK_SAMPLES_PER_RUN; i++) {
            if (tryConsumeChunk(serverLevel, data)) {
                return; // one spawn per run at most; cooldown is stamped anyway
            }
        }
    }

    /** Picks a loaded chunk near a random player; if it has never been rolled, spends its single lifetime roll. */
    private static boolean tryConsumeChunk(ServerLevel serverLevel, QueenSpawnChunkData data) {
        var players = serverLevel.players();
        var player = players.get(serverLevel.random.nextInt(players.size()));
        var playerChunk = player.chunkPosition();
        var chunkPos = new ChunkPos(
            playerChunk.x + serverLevel.random.nextInt(15) - 7,
            playerChunk.z + serverLevel.random.nextInt(15) - 7
        );

        if (!serverLevel.hasChunk(chunkPos.x, chunkPos.z) || data.isChunkBlacklisted(chunkPos)) {
            return false;
        }

        // Evaluate BEFORE blacklisting (the placement rules consult the blacklist), then consume the chunk
        // regardless of outcome — every chunk gets exactly one roll, ever.
        var spawned = attemptSpawn(serverLevel, data, chunkPos);
        data.addChunkToBlacklist(chunkPos);
        return spawned;
    }

    private static boolean attemptSpawn(ServerLevel serverLevel, QueenSpawnChunkData data, ChunkPos chunkPos) {
        if (serverLevel.random.nextInt(SPAWN_ROLL_ONE_IN) != 0) {
            return false;
        }

        if (!data.isFarFromWildQueenChunks(chunkPos, MIN_DISTANCE_BETWEEN_WILD_QUEENS_CHUNKS)) {
            return false;
        }

        if (!isFarFromHiveLocations(serverLevel, chunkPos)) {
            return false;
        }

        var variant = chooseVariant(serverLevel);
        if (variant == null) {
            return false;
        }

        var entityType = CasteResolver.entityTypeForCaste(variant, AlienEntityTypeTags.QUEENS);
        if (entityType == null) {
            return false;
        }

        var maxY = serverLevel.dimension() == Level.NETHER
            ? serverLevel.dimensionType().logicalHeight()
            : MAX_OVERWORLD_SPAWN_Y;
        var minY = serverLevel.getMinBuildHeight() + 5;
        if (maxY <= minY) {
            return false;
        }

        for (var i = 0; i < POSITION_SAMPLES_PER_CHUNK; i++) {
            var x = chunkPos.getMinBlockX() + serverLevel.random.nextInt(16);
            var z = chunkPos.getMinBlockZ() + serverLevel.random.nextInt(16);
            var y = minY + serverLevel.random.nextInt(maxY - minY);
            var pos = new BlockPos(x, y, z);

            if (
                !serverLevel.getBlockState(pos).isAir()
                    || !serverLevel.getBlockState(pos.above()).isAir()
                    || !serverLevel.getBlockState(pos.below()).isFaceSturdy(serverLevel, pos.below(), Direction.UP)
            ) {
                continue;
            }

            if (serverLevel.hasNearbyAlivePlayer(x + 0.5, y, z + 0.5, MIN_PLAYER_DISTANCE_BLOCKS)) {
                continue;
            }

            // Darkness, difficulty, and unclaimed-territory rules. Deliberately checkSpawnRules and not the full
            // PREDICATE: the leak gate was already applied in chooseVariant, where ambient normal queens are
            // intentionally exempt from it.
            if (!QueenSpawning.checkSpawnRules(cast(entityType), serverLevel, MobSpawnType.NATURAL, pos, serverLevel.random)) {
                continue;
            }

            var entity = entityType.create(serverLevel);
            if (!(entity instanceof Queen queen)) {
                return false;
            }

            queen.moveTo(x + 0.5, y, z + 0.5, serverLevel.random.nextFloat() * 360.0F, 0.0F);
            queen.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
            queen.setPersistenceRequired();
            serverLevel.addFreshEntityWithPassengers(queen);
            queen.getLifecyclePhaseManager().beginWildHibernation();

            data.addWildQueenChunk(chunkPos);
            data.getSpawnCooldown().reset();

            Alien.LOGGER.info(
                "Queen spawning: wild {} queen took root at {} in {} — sleeping until disturbed, adopted, or worn awake",
                variant,
                pos,
                serverLevel.dimension().location()
            );
            return true;
        }

        return false;
    }

    private static boolean isFarFromHiveLocations(ServerLevel serverLevel, ChunkPos chunkPos) {
        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (!location.dimension().equals(serverLevel.dimension())) {
                continue;
            }
            for (var chunk : location.claimedChunks()) {
                if (
                    Math.max(Math.abs(chunk.x - chunkPos.x), Math.abs(chunk.z - chunkPos.z))
                        <= MIN_DISTANCE_FROM_HIVES_CHUNKS
                ) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Leaked strains eligible for this dimension, plus the rare ambient normal-queen chance in the overworld.
     * Dimension rules: NORMAL only in the overworld, NETHER only in the Nether, other strains wherever their leak
     * was recorded (leak data is already per-dimension).
     */
    private static AlienVariant chooseVariant(ServerLevel serverLevel) {
        var candidates = new ArrayList<AlienVariant>();
        var isOverworld = serverLevel.dimension() == Level.OVERWORLD;
        var isNether = serverLevel.dimension() == Level.NETHER;

        var strainLeakDataOption = StrainLeakData.getOrCreate(serverLevel);
        if (strainLeakDataOption.isSome()) {
            for (var variant : strainLeakDataOption.unwrap().getVariants()) {
                var type = AlienVariantTypes.getFor(variant);
                var isNormal = type == AlienVariantTypes.NORMAL;
                var isNetherStrain = type == AlienVariantTypes.NETHER;
                if (isNormal && !isOverworld) {
                    continue;
                }
                if (isNetherStrain && !isNether) {
                    continue;
                }
                candidates.add(variant);
            }
        }

        if (
            isOverworld
                && !candidates.contains(AlienVariantTypes.NORMAL.variant())
                && serverLevel.random.nextInt(AMBIENT_NORMAL_ONE_IN) == 0
        ) {
            candidates.add(AlienVariantTypes.NORMAL.variant());
        }

        return candidates.isEmpty() ? null : candidates.get(serverLevel.random.nextInt(candidates.size()));
    }

    @SuppressWarnings("unchecked")
    private static net.minecraft.world.entity.EntityType<Queen> cast(net.minecraft.world.entity.EntityType<?> type) {
        return (net.minecraft.world.entity.EntityType<Queen>) type;
    }
}
