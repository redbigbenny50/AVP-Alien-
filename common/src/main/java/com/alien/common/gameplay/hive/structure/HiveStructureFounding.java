package com.alien.common.gameplay.hive.structure;

import com.alien.Alien;
import com.alien.common.gameplay.hive.growth.HiveLocationClaims;
import com.alien.common.gameplay.hive.location.HiveLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * Wires the structure system into hive founding. When a queen founds a location this: assigns the queen-chamber roles
 * onto the claimed core chunks, physically stamps the chamber at the hive floor, and builds the <b>royal ring</b> - one
 * royal hallway stamped on each of the chamber's royal exits, right here at founding.
 * <p>
 * Stamping the royals with the core (rather than growing them later) guarantees the four-hall ring always exists,
 * claims their chunks together with the core, and means the growth planner only ever has to grow the general warren off
 * the royals' downstream doors. Royal variant per exit is picked at random (any mix, repeats allowed). Any non-royal
 * chamber doorway (there normally aren't any) is left as an open frontier socket for the planner instead.
 */
public final class HiveStructureFounding {

    private HiveStructureFounding() {}

    /**
     * Establishes the queen chamber and its royal ring at founding. No-op (with a warning) if the queen-chamber piece
     * failed to load or the dimension isn't available. Safe to call once at founding.
     *
     * @param server      the server (to reach the parsed piece registry and structure templates)
     * @param location    the freshly founded location (its core chunks are already claimed by the caller)
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
                    // PART cells must carry the piece id too: a null id keeps them out of structurePieceByChunk,
                    // and the router (which checks that map for occupancy) would route hallways straight INTO the
                    // queen's chamber - the "corridor built itself behind her" bug.
                    location.assignStructure(chunk, HiveStructureRole.QUEEN_CHAMBER_PART, pieceId);
                }
            }
        }

        ServerLevel level = server.getLevel(location.dimension());
        if (level == null) {
            // No level to stamp/place into: fall back to just registering the chamber doorways as open sockets so the
            // planner can grow from them once the dimension is available.
            Alien.LOGGER.warn(
                "Queen chamber dimension {} not loaded; registering doorways as sockets only.",
                location.dimension().location()
            );
            for (DoorwaySocket doorway : chamber.sockets()) {
                var edgeChunk = new ChunkPos(originChunk.x + doorway.edgeChunkX(), originChunk.z + doorway.edgeChunkZ());
                location.frontierSockets().add(new FrontierSocket(edgeChunk, doorway.facing(), doorway.doorType(), 0, 0));
            }
            return;
        }

        // Construction economy step 6 (design §7b): the queen CARVES her chamber instead of it appearing whole.
        // Commission the FOUNDING CORE site - no socket (nothing upstream of the core), dug outward from its center
        // at her fixed pace, resin placed by her first drones (or by her, slowly, if none hatch - §8.7). The royal
        // ring + socket registration run at the site's COMPLETION (finishFoundingStructure), so routing cannot grow
        // halls around a chamber that is still a solid block of ground. Roles were assigned above either way - they
        // are bookkeeping, invisible until blocks exist. NO resin debt on the core: the founding biomass tank is the
        // founding cost, and double-taxing the bootstrap could starve a hive that owns nothing yet.
        if (HiveRouter.CARVE_ENABLED) {
            var site = new com.alien.common.gameplay.hive.structure.carve.CarveSite(
                new PieceMatch(chamber, Rotation.NONE, originChunk),
                null,
                location
            );
            location.setActiveCarveSite(site);
            Alien.LOGGER.info("Hive: commissioned founding core {} (queen-dug).", site.describe());
            return;
        }

        // Legacy instant path (CARVE_ENABLED=false): stamp the chamber and finish the founding structure now.
        stampQueenChamber(level, server, location, originChunk);
        finishFoundingStructure(level, location, true);
    }

    /**
     * The founding TAIL - royal ring, open-socket registration, and the blueprint roll - runs when the chamber
     * physically exists: immediately on the legacy path, at carve completion on the commission path
     * ({@code CarveSiteWork} calls this for a founding-core site instead of {@code finalizePlacement}). Self-contained
     * on purpose: it re-derives the chamber piece and origin from the location so the completion path needs nothing but
     * the level and the location.
     */
    public static void finishFoundingStructure(ServerLevel level, HiveLocation location, boolean stampRoyalRing) {
        var server = level.getServer();
        var registry = HivePieceRegistry.get(server);
        var chamber = registry.get(HivePieceCatalog.QUEEN_CHAMBER);
        if (chamber == null) {
            Alien.LOGGER.warn("Queen chamber piece not loaded at founding completion; no royal ring registered.");
            return;
        }
        var centerChunk = new ChunkPos(location.centerPos());
        int halfX = (chamber.footprintChunksX() - 1) / 2;
        int halfZ = (chamber.footprintChunksZ() - 1) / 2;
        var originChunk = new ChunkPos(centerChunk.x - halfX, centerChunk.z - halfZ);

        // Royal ring: on the INSTANT paths (legacy A/B and the never-wedge fallbacks) stamp a royal hallway on each
        // royal exit now, exactly as before. On the CARVE path, register EVERY doorway - royal included - as an open
        // frontier socket instead: the router's royal-priority pass picks the royal doors up first thing next cycle
        // and commissions each hall as an ordinary drone-staffed carve site, dug and paid like the rest of the hive.
        var random = level.getRandom();
        long currentTick = level.getGameTime();
        int royalsPlaced = 0;
        int socketsRegistered = 0;
        for (DoorwaySocket doorway : chamber.sockets()) {
            var edgeChunk = new ChunkPos(originChunk.x + doorway.edgeChunkX(), originChunk.z + doorway.edgeChunkZ());
            var socket = new FrontierSocket(edgeChunk, doorway.facing(), doorway.doorType(), 0, 0);

            if (
                stampRoyalRing
                    && isRoyalDoor(socket.doorType())
                    && placeRoyalHallway(level, location, registry, socket, random, currentTick)
            ) {
                royalsPlaced++;
            } else {
                // A royal door awaiting the router, a non-royal door, or a blocked stamp: open on the frontier.
                location.frontierSockets().add(socket);
                socketsRegistered++;
            }
        }

        Alien.LOGGER.info(
            "Queen chamber established at {} ({}x{} chunks): {} royal hallways stamped, {} open sockets.",
            centerChunk,
            chamber.footprintChunksX(),
            chamber.footprintChunksZ(),
            royalsPlaced,
            socketsRegistered
        );

        // Goal-based planner, stage 1: roll this hive's blueprint (spaced room goals + per-side exits) and log it as a
        // map so the dispersion can be eyeballed. Seeded from the founding chunk, so each hive differs and the same
        // spot regenerates the same plan. Nothing is built from it yet - the router that carves to these goals is next.
        int blueprintExtent = 9; // matches the planner's base footprint radius (19x19)
        var blueprint = HiveBlueprintGenerator.generate(centerChunk, blueprintExtent, centerChunk.toLong());
        Alien.LOGGER.info(
            "Hive blueprint for {} ({} room goals, {} exits):{}",
            centerChunk,
            blueprint.goals().size(),
            blueprint.exits().size(),
            HiveBlueprintGenerator.toAsciiMap(blueprint, centerChunk, blueprintExtent)
        );
    }

    /**
     * The founding NEVER-WEDGE fallback: the instant path in one call - stamp the chamber, then run the founding tail.
     * Used by {@code CarveSiteWork} when a founding-core site cannot hydrate or resume, so founding always produces a
     * functioning chamber even if the carve cannot.
     */
    public static void legacyFoundingFallback(ServerLevel level, HiveLocation location) {
        var server = level.getServer();
        var registry = HivePieceRegistry.get(server);
        var chamber = registry.get(HivePieceCatalog.QUEEN_CHAMBER);
        if (chamber == null) {
            Alien.LOGGER.warn("Queen chamber piece not loaded; founding fallback cannot stamp the chamber.");
            return;
        }
        var centerChunk = new ChunkPos(location.centerPos());
        int halfX = (chamber.footprintChunksX() - 1) / 2;
        int halfZ = (chamber.footprintChunksZ() - 1) / 2;
        var originChunk = new ChunkPos(centerChunk.x - halfX, centerChunk.z - halfZ);
        stampQueenChamber(level, server, location, originChunk);
        finishFoundingStructure(level, location, true);
    }

    /** A royal-door socket is the only place a royal hallway attaches (and only the core chamber has them). */
    private static boolean isRoyalDoor(String doorType) {
        return doorType != null && doorType.contains("royal");
    }

    /**
     * Picks a random royal-hallway variant that fits {@code socket} in free chunks (any mix, repeats allowed), stamps
     * it via the shared placer (which registers its downstream doors as new frontier sockets), and claims its chunks.
     * Returns false if no royal fits or placement failed.
     */
    private static boolean placeRoyalHallway(
        ServerLevel level,
        HiveLocation location,
        HivePieceRegistry registry,
        FrontierSocket socket,
        RandomSource random,
        long currentTick
    ) {
        // Free-chunk test: STRUCTURE occupancy, not claims. The ring is stamped at founding-carve COMPLETION now
        // (step 6), and by then the hive's own claim engine has been running for minutes and has usually claimed the
        // very chunks the ring wants - a claim by THIS hive on empty ground is exactly where its ring belongs. Only
        // ground already carrying structure (or claimed by a DIFFERENT hive) is off-limits.
        var matches = HivePieceMatcher.matchesFromRegistry(
            socket,
            registry,
            chunk -> {
                if (location.structurePieceByChunk().containsKey(chunk)) {
                    return false; // already carries a piece of this hive
                }
                var owner = com.alien.common.gameplay.hive.location.HiveLocationRegistry.INSTANCE
                    .getByChunk(level.dimension(), chunk);
                return owner == null || owner == location; // unclaimed, or this hive's own plain claim
            }
        );
        if (matches.isEmpty()) {
            return false;
        }
        var match = matches.get(random.nextInt(matches.size()));
        if (!HiveStructurePlacer.place(level, location, match, socket)) {
            return false;
        }
        for (ChunkPos chunk : match.occupiedChunks()) {
            HiveLocationClaims.claim(level, location, chunk, currentTick);
        }
        return true;
    }

    /**
     * Places the queen-chamber template with its (0,0,0) corner at {@code originChunk}'s min-block corner and its floor
     * on the hive floor row - the same origin/floor convention {@link HiveStructurePlacer} uses for grown pieces, so
     * the chamber and everything grown from it line up. Rotation NONE; jigsaw blocks replaced with their air
     * final_state.
     */
    private static void stampQueenChamber(
        ServerLevel level,
        MinecraftServer server,
        HiveLocation location,
        ChunkPos originChunk
    ) {
        var templateOpt = server.getStructureManager().get(HivePieceCatalog.QUEEN_CHAMBER);
        if (templateOpt.isEmpty()) {
            Alien.LOGGER.warn("Queen chamber not stamped: template {} not found.", HivePieceCatalog.QUEEN_CHAMBER);
            return;
        }

        var placeAt = new BlockPos(originChunk.getMinBlockX(), location.hiveFloorY(), originChunk.getMinBlockZ());
        var settings = new StructurePlaceSettings()
            .setRotation(Rotation.NONE)
            .setIgnoreEntities(true)
            .addProcessor(JigsawReplacementProcessor.INSTANCE);
        boolean placed = templateOpt.get().placeInWorld(level, placeAt, placeAt, settings, RandomSource.create(), 2);
        if (!placed) {
            Alien.LOGGER.warn("Queen chamber placement returned false at {}.", placeAt);
        }
    }
}
