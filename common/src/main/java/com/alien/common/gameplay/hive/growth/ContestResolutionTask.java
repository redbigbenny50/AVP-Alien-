package com.alien.common.gameplay.hive.growth;

import com.alien.Alien;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.alien.common.gameplay.hive.id.HiveLocationIds;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.id.VariantIds;
import com.alien.common.gameplay.hive.lifecycle.HiveLocationMergeHandler;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Resolves contested chunks per {@code HIVE_REDESIGN_03_LOCATIONS.md} § 3.
 * <p>
 * Every {@code contestTickWindow} (default 60 seconds), scans every level's contested chunks via
 * {@code TerritoryManager.getAllContestedChunks(level)}. Same-lineage location claimants are merged before hostile
 * contest resolution. For each remaining contested chunk, counts the number of loaded xenomorphs in that chunk per
 * claimant location's lineage; the side with more wins. The losers' location claims on that chunk are removed.
 * <p>
 * If only one xenomorph is present (or zero), the resolution is deferred — no decisive winner. This avoids flipping an
 * empty contested chunk back and forth.
 */
public final class ContestResolutionTask {

    private ContestResolutionTask() {}

    public static void scanAll(MinecraftServer server) {
        for (var dim : server.levelKeys()) {
            var level = server.getLevel(dim);
            if (level == null) {
                continue;
            }
            scanLevel(level);
        }
    }

    private static void scanLevel(ServerLevel level) {
        var contestedChunks = Alien.MOD.territory().getAllContestedChunks(level);
        if (contestedChunks.isEmpty()) {
            return;
        }

        for (var chunk : contestedChunks) {
            resolveChunk(level, chunk);
        }
    }

    private static void resolveChunk(ServerLevel level, ChunkPos chunk) {
        var claimants = new ArrayList<>(Alien.MOD.territory().getClaimants(level, chunk));
        if (claimants.size() < 2) {
            return;
        }

        var validLocationClaimants = new ArrayList<HiveLocation>();
        for (var claimantId : claimants) {
            if (!HiveLocationIds.isHiveLocationId(claimantId)) {
                if (LineageIds.isLineageId(claimantId) || VariantIds.isVariantId(claimantId)) {
                    Alien.MOD.territory().removeClaim(level, chunk, claimantId);
                }
                continue;
            }

            var location = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(claimantId));
            if (location == null || !location.claimedChunks().contains(chunk)) {
                Alien.MOD.territory().removeClaim(level, chunk, claimantId);
                continue;
            }

            // Inhibited (severed contained-breeder) locations don't participate in contests — their single
            // follow-chunk claim is managed by the queen, not won or stripped by a territory dispute.
            if (location.isInhibited()) {
                continue;
            }

            validLocationClaimants.add(location);
        }

        if (mergeSameLineageClaimants(level, validLocationClaimants)) {
            return;
        }

        // Count xenomorphs per claimant location within this chunk.
        var counts = new HashMap<ResourceLocation, Integer>();
        for (var location : validLocationClaimants) {
            counts.put(location.id().value(), countXenomorphsIn(level, chunk, location.lineageFactionId()));
        }

        if (counts.size() < 2) {
            return;
        }

        ResourceLocation winner = null;
        var bestCount = -1;
        var tied = false;
        for (var entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                winner = entry.getKey();
                tied = false;
            } else if (entry.getValue() == bestCount) {
                tied = true;
            }
        }

        // No decisive winner — leave the contest pending.
        if (winner == null || bestCount == 0 || tied) {
            return;
        }

        // Remove every loser's location claim on this chunk.
        for (var entry : counts.entrySet()) {
            var loserId = entry.getKey();
            if (loserId.equals(winner)) {
                continue;
            }
            releaseChunkFromLocation(level, loserId, chunk);
        }

        Alien.LOGGER.info(
            "Hive: contest resolved at {} → winner {} ({} losers released)",
            chunk,
            winner,
            counts.size() - 1
        );
    }

    private static int countXenomorphsIn(ServerLevel level, ChunkPos chunk, ResourceLocation lineageId) {
        var faction = Alien.MOD.factions().get(lineageId);
        if (faction == null) {
            return 0;
        }

        var minX = chunk.getMinBlockX();
        var minZ = chunk.getMinBlockZ();
        var maxX = chunk.getMaxBlockX();
        var maxZ = chunk.getMaxBlockZ();

        var box = new AABB(minX, level.getMinBuildHeight(), minZ, maxX + 1, level.getMaxBuildHeight(), maxZ + 1);

        var xenomorphCount = 0;
        for (var entity : level.getEntitiesOfClass(com.alien.common.gameplay.entity.living.alien.Alien.class, box)) {
            if (!entity.getType().is(AlienEntityTypeTags.XENOMORPHS)) {
                continue;
            }
            // Membership check — only count entities that actually belong to this lineage.
            if (Alien.MOD.factions().getFactionIds(entity.getUUID()).contains(lineageId)) {
                xenomorphCount++;
            }
        }
        return xenomorphCount;
    }

    private static boolean mergeSameLineageClaimants(ServerLevel level, ArrayList<HiveLocation> claimants) {
        var byLineage = new HashMap<ResourceLocation, List<HiveLocation>>();
        for (var location : claimants) {
            byLineage.computeIfAbsent(location.lineageFactionId(), $ -> new ArrayList<>()).add(location);
        }

        var merged = false;
        for (var entry : byLineage.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            if (HiveLocationMergeHandler.mergeSameLineage(level, entry.getKey(), entry.getValue()) != null) {
                merged = true;
            }
        }
        return merged;
    }

    private static void releaseChunkFromLocation(ServerLevel level, ResourceLocation locationFactionId, ChunkPos chunk) {
        HiveLocation owningLocation = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(locationFactionId));

        if (owningLocation == null) {
            Alien.MOD.territory().removeClaim(level, chunk, locationFactionId);
            return;
        }

        HiveLocationClaims.release(level, owningLocation, chunk);
    }
}
