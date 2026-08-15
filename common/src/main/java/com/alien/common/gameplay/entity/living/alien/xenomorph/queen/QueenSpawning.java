package com.alien.common.gameplay.entity.living.alien.xenomorph.queen;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.level.saveddata.QueenSpawnChunkData;
import com.alien.common.gameplay.level.saveddata.StrainLeakData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

public class QueenSpawning {

    private static final int MAX_OVERWORLD_Y_LEVEL = -24;

    public static final SpawnPlacements.SpawnPredicate<Queen> PREDICATE = (
        entityType,
        serverLevelAccessor,
        mobSpawnType,
        blockPos,
        randomSource
    ) -> {
        var serverLevel = serverLevelAccessor.getLevel();

        if (
            QueenSpawnChunkData.getOrCreate(serverLevel).isSomeAnd(queenSpawnChunkData -> queenSpawnChunkData.getSpawnCooldown().isActive())
        ) {
            return false;
        }

        var queenSpawnChunkDataOption = QueenSpawnChunkData.getOrCreate(serverLevel);
        var isChunkSpawnAvailable = queenSpawnChunkDataOption
            .isSomeAnd(queenSpawnChunkData -> !queenSpawnChunkData.isChunkBlacklisted(blockPos));

        if (!isChunkSpawnAvailable) {
            return false;
        }

        int maxYLevelForDimension;

        if (serverLevel.dimension() == Level.NETHER) {
            maxYLevelForDimension = serverLevel.dimensionType().logicalHeight();
        } else {
            maxYLevelForDimension = MAX_OVERWORLD_Y_LEVEL;
        }

        return blockPos.getY() <= maxYLevelForDimension
            && canStrainSpawnInLevel(serverLevel, entityType)
            && checkSpawnRules(entityType, serverLevelAccessor, mobSpawnType, blockPos, randomSource);
    };

    private static boolean canStrainSpawnInLevel(ServerLevel serverLevel, EntityType<Queen> entityType) {
        var strainLeakDataOption = StrainLeakData.getOrCreate(serverLevel);
        var alienVariantTypeOption = AlienVariantTypes.getFor(entityType);

        return alienVariantTypeOption.isSomeAnd(
            alienVariantType -> strainLeakDataOption
                .isSomeAnd(strainLeakData -> strainLeakData.hasVariant(alienVariantType.variant()))
        );
    }

    public static boolean checkSpawnRules(
        EntityType<? extends Monster> entityType,
        ServerLevelAccessor serverLevelAccessor,
        MobSpawnType mobSpawnType,
        BlockPos blockPos,
        RandomSource randomSource
    ) {
        return Monster.checkMonsterSpawnRules(
            entityType,
            serverLevelAccessor,
            mobSpawnType,
            blockPos,
            randomSource
        )
            && isQueenSpawnSpatiallyAllowed(serverLevelAccessor, blockPos);
    }

    /**
     * Placement rules for WILD (exploration-driven) queen spawning: unclaimed ground and a non-peaceful world.
     * Deliberately NOT the vanilla monster rules - a wild queen needs deep ground per the lifecycle dig bands, not
     * darkness: caves lit by lava or amethyst are exactly the kind of place she takes root, and light-level gating
     * silently starved spawning in the field. Depth and the no-sky requirement live with the position sampling in
     * {@code QueenNaturalSpawnTask}.
     */
    public static boolean checkWildSpawnRules(ServerLevelAccessor serverLevelAccessor, BlockPos blockPos) {
        return serverLevelAccessor.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL
            && isQueenSpawnSpatiallyAllowed(serverLevelAccessor, blockPos);
    }

    /**
     * Hive: a queen can only spawn into chunks that are NOT inside any existing location's claimed territory. The
     * design says queen-distance is 0–0 (per {@code HIVE_REDESIGN_03_LOCATIONS.md} § 4) — but for natural spawning, the
     * practical constraint is "fresh ground only." Once a queen settles, the founding service mints a new location at
     * her chunk; she's then the unique queen of that center chunk, and another queen wandering in won't displace her.
     */
    private static boolean isQueenSpawnSpatiallyAllowed(ServerLevelAccessor serverLevelAccessor, BlockPos blockPos) {
        var level = serverLevelAccessor.getLevel();
        var occupant = HiveLocationRegistry.INSTANCE.getByChunk(level.dimension(), new ChunkPos(blockPos));
        // No existing location → fresh ground, queen spawn allowed.
        // Existing location → block (queens don't natural-spawn into already-claimed territory).
        return occupant == null;
    }
}
