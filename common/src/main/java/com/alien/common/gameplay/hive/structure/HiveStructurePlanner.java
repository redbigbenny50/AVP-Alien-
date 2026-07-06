package com.alien.common.gameplay.hive.structure;

import com.alien.common.gameplay.hive.location.HiveLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives hive structure growth. On a coarse cadence (called from the loaded-location tick), it takes one open frontier
 * socket, asks the matcher which catalog pieces fit there in free chunks, picks one, and hands it to the placer to
 * carve and stamp. The placer then updates the frontier set - consuming the socket it grew from and registering the new
 * piece's remaining doorways - so the hive grows outward doorway by doorway.
 * <p>
 * This first version grows one piece per successful call and picks the first workable match. The needs-driven selection
 * (what the hive actually wants - more egg chambers, a jelly vault, a hub to branch) and the biomass/claim-cost gating
 * layer on top here later; for now the goal is correct, visible outward growth from the queen chamber.
 * <p>
 * Pure-ish and bounded: it evaluates one socket per call against the catalog, does no world search, and stops as soon
 * as it places a piece (or finds nothing to do). Growth pacing is the caller's cadence.
 */
public final class HiveStructurePlanner {

    private HiveStructurePlanner() {}

    /**
     * Attempts to grow the hive by one piece. Returns true if a piece was placed.
     *
     * @param server   the server
     * @param level    the (loaded) level the hive is in
     * @param location the hive location to grow
     */
    public static boolean tryGrow(MinecraftServer server, ServerLevel level, HiveLocation location) {
        var frontier = location.frontierSockets();
        if (frontier.isEmpty()) {
            return false;
        }

        var registry = HivePieceRegistry.get(server);

        // "Free" = a chunk not already claimed by this location. The matcher tests candidate footprints against this.
        var claimed = location.claimedChunks();
        java.util.function.Predicate<ChunkPos> chunkIsFree = chunk -> !claimed.contains(chunk);

        // Try each open socket until one yields a placeable match. Copy the set first: placing mutates the frontier.
        List<FrontierSocket> sockets = new ArrayList<>(frontier);
        for (FrontierSocket socket : sockets) {
            var matches = HivePieceMatcher.matchesFromRegistry(socket, registry, chunkIsFree);
            if (matches.isEmpty()) {
                continue;
            }

            // First workable match for now; needs-driven ranking comes later.
            var chosen = matches.get(0);

            boolean placed = HiveStructurePlacer.place(level, location, chosen, socket);
            if (placed) {
                // Claim the newly occupied chunks so subsequent growth treats them as taken.
                for (ChunkPos chunk : chosen.occupiedChunks()) {
                    location.claimedChunks().add(chunk);
                }
                return true;
            }
        }
        return false;
    }
}
