package com.alien.common.gameplay.hive.growth;

import com.alien.common.gameplay.hive.location.HiveLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * Marks a location's loaded claimed chunks as decorated (Phase 7 bookkeeping; a future phase places resin + ovomorphs
 * from it).
 * <p>
 * HISTORY - THIS USED TO BE CHUNK-LOAD-EVENT DRIVEN, AND THE EVENT ITSELF WAS THE CRASH. BLib's
 * {@code MixinChunkMap_ChunkLoadEvent} handler calls {@code level.getChunk(x, z)} SYNCHRONOUSLY before dispatching to
 * listeners, and it runs inside {@code onFullChunkStatusChange} - which vanilla calls from
 * {@code DistanceManager.runAllUpdates}'s iteration over {@code chunksToUpdateFutures}. When the chunk isn't
 * immediately ready (a nether-portal wall of promotions/demotions on an overloaded integrated server), that getChunk
 * blocks into {@code managedBlock -> pollTask -> runDistanceManagerUpdates} REENTRANTLY - seven levels deep in the
 * tester's stack - and the outer iteration dies with a {@link java.util.ConcurrentModificationException}: "going to the
 * nether made me crash". Deferring OUR listener body never helped because the fatal getChunk runs in BLib's handler
 * BEFORE any listener code. The handler's one mercy is an early-out when the listener list is EMPTY, so the fix is
 * structural: avp_alien registers NO chunk-load listener (see Alien's init comment), and this class is polled from
 * HiveLocationLoadedTickTask's 20-tick gate instead, using the NON-BLOCKING {@code getChunkNow} to test loadedness. The
 * real fix belongs in BLib (getChunkNow + defer); until it ships, nothing in this mod family may register CHUNK_LOAD.
 */
public final class ResinDecorator {

    private ResinDecorator() {}

    /**
     * Called per loaded location from HiveLocationLoadedTickTask's 20-tick gate, immediately after
     * CatchUpEngine.catchUpTo (which supplies the claim catch-up the old event path did internally). Marks every
     * loaded, still-claimed, not-yet-decorated chunk. Converges: once a chunk is marked it costs one set lookup forever
     * after, so steady state is a walk of the claimed set with no chunk-source calls.
     */
    public static void sweepLoaded(ServerLevel level, HiveLocation location) {
        for (var chunk : location.claimedChunks()) {
            if (location.decoratedChunks().contains(chunk)) {
                continue;
            }
            // Non-blocking loadedness probe - returns null rather than scheduling or waiting, so it is safe on
            // any tick path. The blocking getChunk variant is exactly what crashed the chunk system (see class doc).
            if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) {
                continue;
            }
            location.decoratedChunks().add(chunk);
        }
    }
}
