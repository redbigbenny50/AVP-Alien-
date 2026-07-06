package com.alien.common.gameplay.hive.structure;

import com.alien.Alien;
import com.alien.common.gameplay.hive.location.HiveLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;

/**
 * Wires the structure system into hive founding: when a queen founds a location, this stamps the queen-chamber roles
 * onto the claimed core chunks and registers the chamber's royal-door exits as frontier sockets - the seeds the growth
 * planner later grows hallways and chambers from.
 * <p>
 * It reads the parsed queen-chamber piece from {@link HivePieceRegistry} so the exits come straight from the authored
 * .nbt (re-author the chamber and founding follows). The chamber's footprint is assumed centered on the founding center
 * chunk, matching how {@code claimInitialCore} claims a symmetric core around that center.
 */
public final class HiveStructureFounding {

    private HiveStructureFounding() {}

    /**
     * Assigns queen-chamber structure roles across the core and registers the chamber's royal exits as frontier
     * sockets. No-op (with a warning) if the queen-chamber piece failed to load. Safe to call once at founding.
     *
     * @param server      the server (to reach the parsed piece registry)
     * @param location    the freshly founded location
     * @param centerChunk the founding center chunk (the chamber's center cell)
     */
    public static void establishQueenChamber(MinecraftServer server, HiveLocation location, ChunkPos centerChunk) {
        var registry = HivePieceRegistry.get(server);
        var chamber = registry.get(HivePieceCatalog.QUEEN_CHAMBER);
        if (chamber == null) {
            Alien.LOGGER.warn("Queen chamber piece not loaded; founding without structure roles/sockets.");
            return;
        }

        // The chamber footprint is centered on centerChunk. Its cells run 0..footprint-1; the center cell is the
        // middle. originChunk maps chamber cell (0,0) to a world chunk.
        int halfX = (chamber.footprintChunksX() - 1) / 2;
        int halfZ = (chamber.footprintChunksZ() - 1) / 2;
        var originChunk = new ChunkPos(centerChunk.x - halfX, centerChunk.z - halfZ);

        String pieceId = HivePieceCatalog.QUEEN_CHAMBER.toString();

        // Roles: center cell -> CENTER (carrying the piece id), every other cell -> PART.
        for (int cx = 0; cx < chamber.footprintChunksX(); cx++) {
            for (int cz = 0; cz < chamber.footprintChunksZ(); cz++) {
                var chunk = new ChunkPos(originChunk.x + cx, originChunk.z + cz);
                boolean isCenter = (cx == halfX && cz == halfZ);
                if (isCenter) {
                    location.assignStructure(chunk, HiveStructureRole.QUEEN_CHAMBER_CENTER, pieceId);
                } else {
                    location.assignStructure(chunk, HiveStructureRole.QUEEN_CHAMBER_PART, null);
                }
            }
        }

        // Frontier sockets: each authored doorway on the chamber becomes an open frontier at its edge chunk, facing
        // out.
        int registered = 0;
        for (DoorwaySocket socket : chamber.sockets()) {
            var chunk = new ChunkPos(originChunk.x + socket.edgeChunkX(), originChunk.z + socket.edgeChunkZ());
            location.frontierSockets().add(new FrontierSocket(chunk, socket.facing(), socket.doorType()));
            registered++;
        }

        Alien.LOGGER.info(
            "Queen chamber established at {} ({}x{} chunks): {} frontier sockets registered.",
            centerChunk,
            chamber.footprintChunksX(),
            chamber.footprintChunksZ(),
            registered
        );
    }
}
