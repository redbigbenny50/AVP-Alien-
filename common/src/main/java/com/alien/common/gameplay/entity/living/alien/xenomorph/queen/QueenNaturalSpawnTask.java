package com.alien.common.gameplay.entity.living.alien.xenomorph.queen;

import com.alien.Alien;
import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.level.saveddata.QueenSpawnChunkData;
import com.alien.common.gameplay.level.saveddata.StrainLeakData;
import com.alien.common.model.alien.variant.AlienVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;

/**
 * Exploration-driven natural queen spawning. Each chunk gets exactly ONE roll, ever (consumed chunks go into the
 * permanent {@link QueenSpawnChunkData} blacklist), so wild queens appear as players push into fresh territory — never
 * on ground that has already been walked and judged.
 * <p>
 * A roll can only produce a queen when every rule holds:
 * </p>
 * <ul>
 * <li><b>Strain-dimension rules:</b> normal queens spawn only in the overworld, nether queens only in the Nether.
 * Leaked strains (see {@link StrainLeakData}) spawn in the dimension their leak was recorded in; normal-strain queens
 * additionally get a rare AMBIENT chance in the overworld with no leak required.</li>
 * <li><b>Space:</b> no hive location within {@link #MIN_DISTANCE_FROM_HIVES_CHUNKS} chunks (matches the wild adoption
 * reach), no other wild queen within {@link #MIN_DISTANCE_BETWEEN_WILD_QUEENS_CHUNKS} chunks, no player within
 * {@link #MIN_PLAYER_DISTANCE_BLOCKS} blocks.</li>
 * <li><b>Placement:</b> the existing {@link QueenSpawning#checkSpawnRules} (darkness, difficulty, unclaimed territory)
 * plus the deep-Y band (Y &le; -24 outside the Nether).</li>
 * </ul>
 * <p>
 * On success the queen takes root via {@code beginWildHibernation()} — found sleeping, roused by solid hits,
 * permanently woken only by accumulated player activity (3 loaded Minecraft days) or lineage adoption — and the
 * world-level 5-minute spawn cooldown stamps to prevent bursts.
 * </p>
 */
public class QueenNaturalSpawnTask {

    private static final int RUN_INTERVAL_TICKS = 100;

    private static final int CHUNK_SAMPLES_PER_RUN = 8;

    /** One-in-N chance that a fresh chunk's single lifetime roll even attempts a queen. */
    private static final int SPAWN_ROLL_ONE_IN = 100;

    /**
     * Sub-roll for AMBIENT normal-strain eligibility (applied on top of the chunk roll; leak-gated strains skip it).
     */
    private static final int AMBIENT_NORMAL_ONE_IN = 2;

    private static final int MIN_DISTANCE_FROM_HIVES_CHUNKS = 32;

    private static final int MIN_DISTANCE_BETWEEN_WILD_QUEENS_CHUNKS = 64;

    private static final double MIN_PLAYER_DISTANCE_BLOCKS = 48.0;

    /**
     * Wild spawn depth envelope outside the Nether: the queen's own LOCATION-phase weighted dig bands (RARE_Y_MIN ..
     * VERY_RARE_Y_MAX in {@code QueenLifecyclePhaseManager}) - she takes root anywhere she would normally dig to. "Deep
     * underground" is enforced by the no-sky check on the sampled spot, not by darkness.
     */
    private static final int WILD_SPAWN_MIN_Y = -50;

    private static final int WILD_SPAWN_MAX_Y = 45;

    private static final int POSITION_SAMPLES_PER_CHUNK = 32;

    /**
     * First-queen guarantee: if no wild queen exists this long after the overworld first ticks with players, force one.
     */
    private static final long FIRST_QUEEN_DEADLINE_TICKS = 5L * 60L * 20L;

    /** Forced first queen lands outside this ring around the chosen player... */
    private static final int FORCED_SPAWN_MIN_CHUNKS = 6;

    /** ...but no further out than this. */
    private static final int FORCED_SPAWN_MAX_CHUNKS = 12;

    private static final int FORCED_SPAWN_CHUNK_TRIES = 24;

    /** Diagnostic heartbeat: while the first-queen guarantee is armed and unfulfilled, log a summary this often. */
    private static final int DIAGNOSTIC_LOG_INTERVAL_TICKS = 600;

    // Diagnostic counters (reset each heartbeat). Server thread only.
    private static int diagChunkTries;

    private static int diagNoChunk;

    private static int diagTypeNull;

    private static int diagNoValidPosition;

    private static int diagPlayerTooClose;

    private static int diagSpawnRulesFailed;

    private static long diagLastLogGameTime = -1L;

    private static boolean diagPeacefulWarned;

    public static void tick(ServerLevel serverLevel) {
        if (serverLevel.getGameTime() % RUN_INTERVAL_TICKS != 0 || serverLevel.players().isEmpty()) {
            return;
        }

        var data = QueenSpawnChunkData.getOrCreate(serverLevel).unwrap();

        handleFirstQueenGuarantee(serverLevel, data);

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

        return spawnQueenInChunk(serverLevel, data, chunkPos, variant);
    }

    /** Finds a legal spot in the chunk (deep, dark, unclaimed, clear of players) and roots a sleeping queen there. */
    private static boolean spawnQueenInChunk(
        ServerLevel serverLevel,
        QueenSpawnChunkData data,
        ChunkPos chunkPos,
        AlienVariant variant
    ) {
        // Direct variant→queen mapping. Deliberately NOT CasteResolver.entityTypeForCaste(variant, QUEENS): the
        // QUEENS tag lists the #empresses sub-tag FIRST, and empresses are also variant-tagged, so tag iteration
        // returned the EMPRESS type - whose class does not extend Queen - and every otherwise-valid spawn died
        // silently on the instanceof at the end of this method (proven by the field heartbeat: ~450 uncounted
        // valid floors per 30s). Field report + log: July 23.
        var entityType = queenTypeFor(variant);
        if (entityType == null) {
            diagTypeNull++;
            return false;
        }

        var maxY = serverLevel.dimension() == Level.NETHER
            ? serverLevel.dimensionType().logicalHeight()
            : WILD_SPAWN_MAX_Y;
        var minY = serverLevel.dimension() == Level.NETHER
            ? serverLevel.getMinBuildHeight() + 5
            : Math.max(serverLevel.getMinBuildHeight() + 5, WILD_SPAWN_MIN_Y);
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
                diagNoValidPosition++;
                continue;
            }

            if (serverLevel.hasNearbyAlivePlayer(x + 0.5, y, z + 0.5, MIN_PLAYER_DISTANCE_BLOCKS)) {
                diagPlayerTooClose++;
                continue;
            }

            // UNDERGROUND, not merely low: a ravine floor open to the sky is not a den. (Always false in the Nether.)
            if (serverLevel.canSeeSky(pos)) {
                diagNoValidPosition++;
                continue;
            }

            // Unclaimed ground + non-peaceful. Deliberately NOT the vanilla monster rules: no darkness gate - a
            // lava-lit cave is a perfectly good den, and light gating starved spawning in the field. The leak gate
            // was already applied in chooseVariant, where ambient normal queens are intentionally exempt from it.
            if (!QueenSpawning.checkWildSpawnRules(serverLevel, pos)) {
                diagSpawnRulesFailed++;
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

            // THE FIRST queen of a world (natural inside the guarantee window or forced at its deadline) founds
            // RIGHT AWAY and announces herself - the world's opening act. Every later wild queen takes root
            // sleeping and is discovered, not announced.
            var isFirstQueen = serverLevel.dimension() == Level.OVERWORLD && !data.isFirstQueenSpawned();
            if (isFirstQueen) {
                queen.getLifecyclePhaseManager().beginWildImmediateFounding();
            } else {
                queen.getLifecyclePhaseManager().beginWildHibernation();
            }

            data.addWildQueenChunk(chunkPos);
            data.getSpawnCooldown().reset();
            if (serverLevel.dimension() == Level.OVERWORLD) {
                data.markFirstQueenSpawned();
            }

            Alien.LOGGER.info(
                "Queen spawning: wild {} queen took root at {} in {} — {}",
                variant,
                pos,
                serverLevel.dimension().location(),
                isFirstQueen
                    ? "the FIRST queen: founding immediately and announcing"
                    : "sleeping until disturbed, adopted, or worn awake"
            );
            return true;
        }

        return false;
    }

    /**
     * First-queen guarantee (overworld only): a fresh world must not stay queen-less just because the natural rolls
     * came up empty. The clock arms the first time the overworld ticks with players present; if it expires with no wild
     * queen ever having taken root, a normal-strain queen is FORCED into a random chunk outside a
     * {@link #FORCED_SPAWN_MIN_CHUNKS}-chunk ring around a player but within {@link #FORCED_SPAWN_MAX_CHUNKS} chunks.
     * The forced path skips the lifetime roll and the spacing margins (a guarantee outranks them) but still demands a
     * legal spot - deep, dark, unclaimed - so it retries every cycle until the terrain cooperates.
     */
    private static void handleFirstQueenGuarantee(ServerLevel serverLevel, QueenSpawnChunkData data) {
        if (serverLevel.dimension() != Level.OVERWORLD || data.isFirstQueenSpawned()) {
            return;
        }

        if (data.getFirstQueenDeadlineGameTime() < 0L) {
            data.setFirstQueenDeadlineGameTime(serverLevel.getGameTime() + FIRST_QUEEN_DEADLINE_TICKS);
            Alien.LOGGER.info(
                "Queen spawning: first-queen clock ARMED — forced spawn begins in {} ticks (difficulty={})",
                FIRST_QUEEN_DEADLINE_TICKS,
                serverLevel.getDifficulty()
            );
            return;
        }

        if (serverLevel.getGameTime() < data.getFirstQueenDeadlineGameTime()) {
            return;
        }

        if (serverLevel.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
            if (!diagPeacefulWarned) {
                diagPeacefulWarned = true;
                Alien.LOGGER.warn(
                    "Queen spawning: difficulty is PEACEFUL — queens are monsters and CANNOT spawn (guarantee and natural spawning both blocked by vanilla monster rules)."
                );
            }
            return;
        }

        maybeLogDiagnostics(serverLevel);

        var players = serverLevel.players();
        var player = players.get(serverLevel.random.nextInt(players.size()));
        var playerChunk = player.chunkPosition();

        for (var i = 0; i < FORCED_SPAWN_CHUNK_TRIES; i++) {
            var dx = serverLevel.random.nextInt(FORCED_SPAWN_MAX_CHUNKS * 2 + 1) - FORCED_SPAWN_MAX_CHUNKS;
            var dz = serverLevel.random.nextInt(FORCED_SPAWN_MAX_CHUNKS * 2 + 1) - FORCED_SPAWN_MAX_CHUNKS;
            var distance = Math.max(Math.abs(dx), Math.abs(dz));
            if (distance <= FORCED_SPAWN_MIN_CHUNKS || distance > FORCED_SPAWN_MAX_CHUNKS) {
                continue;
            }

            var chunkPos = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
            diagChunkTries++;
            if (!serverLevel.hasChunk(chunkPos.x, chunkPos.z)) {
                diagNoChunk++;
                continue;
            }

            if (spawnQueenInChunk(serverLevel, data, chunkPos, AlienVariantTypes.NORMAL.variant())) {
                data.addChunkToBlacklist(chunkPos);
                Alien.LOGGER.info(
                    "Queen spawning: first-queen guarantee forced a wild queen near player {} in chunk {}",
                    player.getName().getString(),
                    chunkPos
                );
                return;
            }
        }
    }

    /**
     * While the guarantee is overdue and unfulfilled, log one summary line per interval so a debug.log names the
     * blocker outright: which stage eats the attempts (unloaded chunks, no deep floor, darkness/claimed rules, or a
     * null queen type). Counters reset each heartbeat.
     */
    private static void maybeLogDiagnostics(ServerLevel serverLevel) {
        var now = serverLevel.getGameTime();
        if (diagLastLogGameTime >= 0L && now - diagLastLogGameTime < DIAGNOSTIC_LOG_INTERVAL_TICKS) {
            return;
        }
        if (diagLastLogGameTime >= 0L) {
            Alien.LOGGER.info(
                "Queen spawning: first-queen guarantee still unfulfilled — last {}t: chunkTries={} (unloaded={}), samples failed: noFloor/sky={}, playerNear={}, rules(claimed/peaceful)={}, typeNull={} (difficulty={})",
                now - diagLastLogGameTime,
                diagChunkTries,
                diagNoChunk,
                diagNoValidPosition,
                diagPlayerTooClose,
                diagSpawnRulesFailed,
                diagTypeNull,
                serverLevel.getDifficulty()
            );
        }
        diagLastLogGameTime = now;
        diagChunkTries = 0;
        diagNoChunk = 0;
        diagTypeNull = 0;
        diagNoValidPosition = 0;
        diagPlayerTooClose = 0;
        diagSpawnRulesFailed = 0;
    }

    private static boolean isFarFromHiveLocations(ServerLevel serverLevel, ChunkPos chunkPos) {
        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (!location.dimension().equals(serverLevel.dimension())) {
                continue;
            }
            for (var chunk : location.claimedChunks()) {
                if (
                    Math.max(Math.abs(chunk.x - chunkPos.x), Math.abs(chunk.z - chunkPos.z)) <= MIN_DISTANCE_FROM_HIVES_CHUNKS
                ) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Leaked strains eligible for this dimension, plus the rare ambient normal-queen chance in the overworld. Dimension
     * rules: NORMAL only in the overworld, NETHER only in the Nether, other strains wherever their leak was recorded
     * (leak data is already per-dimension).
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

    /** The strain's QUEEN entity type - direct and deterministic, immune to tag ordering. */
    private static net.minecraft.world.entity.EntityType<?> queenTypeFor(AlienVariant variant) {
        var type = AlienVariantTypes.getFor(variant);
        if (type == AlienVariantTypes.NORMAL) {
            return com.alien.common.registry.init.AlienEntityTypes.QUEEN.get();
        }
        if (type == AlienVariantTypes.NETHER) {
            return com.alien.common.registry.init.AlienEntityTypes.NETHER_QUEEN.get();
        }
        if (type == AlienVariantTypes.ABERRANT) {
            return com.alien.common.registry.init.AlienEntityTypes.ABERRANT_QUEEN.get();
        }
        if (type == AlienVariantTypes.IRRADIATED) {
            return com.alien.common.registry.init.AlienEntityTypes.IRRADIATED_QUEEN.get();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static net.minecraft.world.entity.EntityType<Queen> cast(net.minecraft.world.entity.EntityType<?> type) {
        return (net.minecraft.world.entity.EntityType<Queen>) type;
    }
}
