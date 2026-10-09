package com.alien.common.gameplay.hive.growth;

import com.alien.Alien;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.id.VariantIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Atomic claim/release helpers for {@link HiveLocation}. Each call:
 * <ul>
 * <li>Updates the location's {@code claimedChunks} set and {@code chunkClaimTicks} map</li>
 * <li>Calls BLib's {@link com.blib.api.common.mod.v1.model.access.BLibTerritoryAccess} to register the claim under the
 * location faction id</li>
 * <li>Updates the {@link HiveLocationRegistry} {@code byChunk} index</li>
 * </ul>
 * <p>
 * Use these instead of direct {@code claimedChunks().add(...)} calls so the three sources of truth (location record,
 * BLib territory, in-memory chunk index) stay synchronized.
 */
public final class HiveLocationClaims {

    private static final int[][] CARDINAL_OFFSETS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    private HiveLocationClaims() {}

    /**
     * Claims {@code chunk} for {@code location}. Returns true if the claim was newly added; false if the chunk was
     * already in the location's set.
     */
    public static boolean claim(ServerLevel level, HiveLocation location, ChunkPos chunk, long currentTick) {
        if (!location.claimedChunks().add(chunk)) {
            return false;
        }

        // ⭐⭐ ADOPT ANY RUINED VENTS IN THIS CHUNK. A vent whose hive died goes DORMANT and stops ticking, which means
        // it cannot notice a new hive by itself - so the new hive notices IT, here, exactly once per claim. Ruins left
        // by an old hive come back to life when a new one grows over them.
        reviveDormantVents(level, chunk);

        location.chunkClaimTicks().put(chunk, currentTick);
        HiveLocationRegistry.INSTANCE.onChunkClaimed(location, chunk);

        syncTerritoryClaim(level, location, chunk);

        return true;
    }

    /**
     * Releases {@code chunk} from {@code location}. Returns true if the claim was actually removed; false if the chunk
     * wasn't in the location's set.
     */
    public static boolean release(ServerLevel level, HiveLocation location, ChunkPos chunk) {
        return release(level, location, chunk, false);
    }

    /**
     * Releases {@code chunk} from {@code location}. Center chunks are protected unless {@code allowCenterChunk} is
     * true, which is reserved for full location removal.
     */
    public static boolean release(
        ServerLevel level,
        HiveLocation location,
        ChunkPos chunk,
        boolean allowCenterChunk
    ) {
        return release(level, location, chunk, allowCenterChunk, !allowCenterChunk);
    }

    private static boolean release(
        ServerLevel level,
        HiveLocation location,
        ChunkPos chunk,
        boolean allowCenterChunk,
        boolean pruneDisconnected
    ) {
        if (!allowCenterChunk && chunk.equals(new ChunkPos(location.centerPos()))) {
            return false;
        }

        if (!location.claimedChunks().remove(chunk)) {
            return false;
        }

        location.chunkClaimTicks().remove(chunk);
        HiveLocationRegistry.INSTANCE.onChunkReleased(location, chunk);

        Alien.MOD.territory().removeClaim(level, chunk, location.id().value());
        removeNonLocationTierClaims(level, chunk);

        if (pruneDisconnected && location.claimedChunks().contains(new ChunkPos(location.centerPos()))) {
            releaseDisconnectedClaims(level, location);
        }

        return true;
    }

    public static void syncTerritoryClaim(ServerLevel level, HiveLocation location, ChunkPos chunk) {
        removeNonLocationTierClaims(level, chunk);
        Alien.MOD.territory().addClaim(level, chunk, location.id().value());
    }

    private static void removeNonLocationTierClaims(ServerLevel level, ChunkPos chunk) {
        for (var claimantId : new ArrayList<>(Alien.MOD.territory().getClaimants(level, chunk))) {
            if (LineageIds.isLineageId(claimantId) || VariantIds.isVariantId(claimantId)) {
                Alien.MOD.territory().removeClaim(level, chunk, claimantId);
            }
        }
    }

    /**
     * Releases any claimed chunk that no longer has a cardinally-adjacent path back to the location center. Returns the
     * number of extra chunks released.
     * <p>
     * STRUCTURE CHUNKS ARE EXEMPT. [stated] "when they start construction/carving it claims those chunks so the claims
     * grow with the building" - a chunk holding a built piece is the hive itself, not abstract territory, and it is
     * physically connected through the halls whatever the CLAIM graph momentarily looks like. Before this exemption,
     * one mid-corridor release (a contested-chunk strip, an inhibition sever, a decay shave) broke the cardinal path
     * and this pruner then amputated every claim beyond the break INCLUDING built rooms - the "rooms with no claims
     * connecting them to the queen room" testers screenshotted. The chamber systems (jelly, eggs, vents) all key on
     * claimed structure chunks, so an amputated room also silently stopped functioning.
     */
    public static int releaseDisconnectedClaims(ServerLevel level, HiveLocation location) {
        // \u2b50 BUILD-FREE: NO CONTIGUITY PRUNE - [stated] "the prune doesnt need to be".
        //
        // \u26a0 Claims here are made by a xenomorph WALKING somewhere inside the territory box, so an isolated
        // pocket is normal and temporary: a scout reaches a far corner before the chunks between it and the hive are
        // filled in. Pruning it would release ground that is about to be reclaimed, churning the claim set and the
        // BLib territory writes behind it for no gain. The radius box already bounds everything, which is the job
        // contiguity was doing.
        if (com.alien.common.gameplay.hive.config.BuildFreeMode.isEnabled()) {
            return 0;
        }

        var centerChunk = new ChunkPos(location.centerPos());
        if (!location.claimedChunks().contains(centerChunk)) {
            return 0;
        }

        var connected = connectedClaims(location, centerChunk);
        if (connected.size() == location.claimedChunks().size()) {
            return 0;
        }

        var disconnected = new ArrayList<ChunkPos>();
        for (var chunk : location.claimedChunks()) {
            if (!connected.contains(chunk) && !location.structurePieceByChunk().containsKey(chunk)) {
                disconnected.add(chunk);
            }
        }

        for (var chunk : disconnected) {
            release(level, location, chunk, false, false);
        }

        if (!disconnected.isEmpty()) {
            Alien.LOGGER.info(
                "Hive: released {} disconnected chunk claims from location {}",
                disconnected.size(),
                location.id().value()
            );
        }

        return disconnected.size();
    }

    public static boolean wouldRemainConnectedAfterRelease(HiveLocation location, ChunkPos releasedChunk) {
        var centerChunk = new ChunkPos(location.centerPos());
        if (releasedChunk.equals(centerChunk)) {
            return false;
        }
        if (!location.claimedChunks().contains(releasedChunk)) {
            return true;
        }
        if (!location.claimedChunks().contains(centerChunk)) {
            return false;
        }

        var remainingClaims = location.claimedChunks().size() - 1;
        if (remainingClaims <= 1) {
            return true;
        }

        return connectedClaims(location, centerChunk, releasedChunk).size() == remainingClaims;
    }

    private static HashSet<ChunkPos> connectedClaims(HiveLocation location, ChunkPos centerChunk) {
        return connectedClaims(location, centerChunk, null);
    }

    private static HashSet<ChunkPos> connectedClaims(
        HiveLocation location,
        ChunkPos centerChunk,
        ChunkPos excludedChunk
    ) {
        var connected = new HashSet<ChunkPos>();
        if (centerChunk.equals(excludedChunk) || !location.claimedChunks().contains(centerChunk)) {
            return connected;
        }

        var queue = new ArrayDeque<ChunkPos>();
        queue.add(centerChunk);
        connected.add(centerChunk);

        while (!queue.isEmpty()) {
            var current = queue.removeFirst();
            for (var offset : CARDINAL_OFFSETS) {
                var neighbor = new ChunkPos(current.x + offset[0], current.z + offset[1]);
                if (
                    neighbor.equals(excludedChunk)
                        || connected.contains(neighbor)
                        || !location.claimedChunks().contains(neighbor)
                ) {
                    continue;
                }
                connected.add(neighbor);
                queue.add(neighbor);
            }
        }

        return connected;
    }

    /**
     * Claims every chunk that holds a built structure piece but has somehow lost its claim. The claim is created at
     * piece placement (planner, router, founding, catch-up all claim on place), so under normal operation this finds
     * nothing - it exists to HEAL: worlds where the old disconnection pruner amputated room claims get them back, and
     * any future release path that touches a structure chunk is undone on the next scan. [stated] "build it into the
     * structure itself instead of relying on surface party for the actual claims solely."
     * <p>
     * Skips chunks currently claimed by a DIFFERENT registered location (e.g. a hall conquered in a war) - healing must
     * not re-ignite settled territory fights. Returns the number of chunks reclaimed.
     */
    public static int reclaimStructureChunks(ServerLevel level, HiveLocation location, long currentTick) {
        var reclaimed = 0;
        for (var chunk : new ArrayList<>(location.structurePieceByChunk().keySet())) {
            if (location.claimedChunks().contains(chunk)) {
                continue;
            }
            var owner = HiveLocationRegistry.INSTANCE.getByChunk(location.dimension(), chunk);
            if (owner != null && owner != location) {
                continue;
            }
            if (claim(level, location, chunk, currentTick)) {
                reclaimed++;
            }
        }
        if (reclaimed > 0) {
            Alien.LOGGER.info(
                "Hive: reclaimed {} structure chunk(s) for location {} - built halls carry their own claims",
                reclaimed,
                location.id().value()
            );
        }
        return reclaimed;
    }

    /**
     * Total claimed-chunk count across every location of {@code lineage}. Used by claim attempts to enforce the
     * per-lineage chunk cap.
     */
    /**
     * Wakes any dormant (hiveless) resin vents in a chunk that has just been claimed.
     * <p>
     * ⚠ Only touches chunks that are ALREADY LOADED - a claim of an unloaded chunk leaves its ruins asleep, which is
     * correct: they will simply stay scenery until something loads and re-claims them, and this must never force a
     * chunk load.
     * </p>
     */
    /**
     * Wakes dormant vents across every loaded chunk this location owns.
     * <p>
     * ⚠ THE SAFETY NET, not the normal path. A vent normally revives the moment its chunk is claimed; this exists for
     * the case where one went dormant WRONGLY - a startup race where the location registry was still loading when the
     * block entity began ticking - on a hive that has finished claiming and would therefore never call claim() again.
     * </p>
     * <p>
     * ⚠ Called on a slow interval from HiveLocationLoadedTickTask, and skips unloaded chunks, so a large hive costs a
     * short walk of already-resident chunks and nothing else.
     * </p>
     */
    public static void reviveDormantVentsForLocation(ServerLevel level, HiveLocation location) {
        for (var chunk : location.claimedChunks()) {
            reviveDormantVents(level, chunk);
        }
    }

    private static void reviveDormantVents(ServerLevel level, ChunkPos chunk) {
        if (!level.hasChunk(chunk.x, chunk.z)) {
            return;
        }

        var loaded = level.getChunk(chunk.x, chunk.z);

        for (var pos : new java.util.ArrayList<>(loaded.getBlockEntitiesPos())) {
            var state = level.getBlockState(pos);

            if (
                state.getBlock() instanceof com.alien.common.gameplay.block.resin.vent.ResinVentBlock
                    && state.getValue(com.alien.common.gameplay.block.resin.vent.ResinVentBlock.DORMANT)
            ) {
                level.setBlock(
                    pos,
                    state.setValue(com.alien.common.gameplay.block.resin.vent.ResinVentBlock.DORMANT, false),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS
                );
            }
        }
    }

    public static int totalChunksFor(LineageFactionData lineage) {
        var total = 0;
        for (var location : lineage.locationsById().values()) {
            total += location.claimedChunks().size();
        }
        return total;
    }
}
