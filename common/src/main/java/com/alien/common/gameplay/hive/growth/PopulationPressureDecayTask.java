package com.alien.common.gameplay.hive.growth;

import com.alien.common.gameplay.hive.economy.CastePopulation;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationBootstrapProtection;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public final class PopulationPressureDecayTask {

    private PopulationPressureDecayTask() {}

    public static void scanAll(MinecraftServer server) {
        var config = HiveLocationRegistry.INSTANCE.config();

        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (!location.isAlive() || location.isInhibited() || location.claimedChunks().size() <= 1) {
                continue;
            }
            if (HiveLocationBootstrapProtection.isProtected(location, config)) {
                continue;
            }

            var level = server.getLevel(location.dimension());
            if (level == null) {
                continue;
            }

            HiveLocationClaims.releaseDisconnectedClaims(level, location);

            while (location.claimedChunks().size() > 1 && isBelowPopulationRatio(location, config)) {
                var chunk = pickOutermostReleasableChunk(location);
                if (chunk == null) {
                    break;
                }
                HiveLocationClaims.release(level, location, chunk);
            }
        }
    }

    private static boolean isBelowPopulationRatio(
        HiveLocation location,
        com.alien.common.gameplay.hive.config.HiveConfig config
    ) {
        var cap = location.claimedChunks().size() * config.populationPerChunk();
        if (cap <= 0) {
            return false;
        }

        var requiredPopulation = (int) Math.ceil(cap * config.minimumPopulationRatioForClaiming());
        return CastePopulation.totalTrackedPopulation(location) < requiredPopulation;
    }

    private static @Nullable ChunkPos pickOutermostReleasableChunk(HiveLocation location) {
        var centerChunk = new ChunkPos(location.centerPos());

        return location.claimedChunks()
            .stream()
            .filter(chunk -> !chunk.equals(centerChunk))
            // Never decay a chunk holding BUILT STRUCTURE: population pressure shaves abstract territory
            // only. A population dip (raid losses, economy stalls) must not de-claim the hive's own halls -
            // the chamber systems (jelly, eggs, vents) all key on claimed structure chunks.
            .filter(chunk -> !location.structurePieceByChunk().containsKey(chunk))
            .filter(chunk -> HiveLocationClaims.wouldRemainConnectedAfterRelease(location, chunk))
            .max(
                Comparator.<ChunkPos>comparingInt(chunk -> chebyshev(chunk, centerChunk))
                    .thenComparingInt(chunk -> manhattan(chunk, centerChunk))
                    .thenComparingLong(chunk -> location.chunkClaimTicks().getOrDefault(chunk, 0L))
                    .thenComparingInt(chunk -> chunk.x)
                    .thenComparingInt(chunk -> chunk.z)
            )
            .orElse(null);
    }

    private static int chebyshev(ChunkPos a, ChunkPos b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z));
    }

    private static int manhattan(ChunkPos a, ChunkPos b) {
        return Math.abs(a.x - b.x) + Math.abs(a.z - b.z);
    }
}
