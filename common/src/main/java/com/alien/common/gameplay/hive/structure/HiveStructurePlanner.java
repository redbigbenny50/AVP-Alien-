package com.alien.common.gameplay.hive.structure;

import com.alien.common.gameplay.hive.growth.HiveLocationClaims;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.registry.init.block.AlienResinBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * Drives hive structure growth. On a coarse cadence (called from the loaded-location tick) it takes the open frontier
 * sockets, asks the matcher which catalog pieces fit each one in free chunks, and grows a single piece.
 * <p>
 * Growth rules (Phase A of the planner brain):
 * <ul>
 * <li><b>Royal halls stay on the core, and never chain.</b> A royal hallway may only attach to a royal socket, and the
 * only royal sockets exist on the queen chamber (the core has no general door). Each royal hall spends its single royal
 * door connecting to the core and exposes only general doors, so no royal can attach to another. This structurally
 * guarantees the core is framed by (at most four) royal halls and the warren hangs off those halls - in any placement
 * order - so no explicit "royal ring first" gate is needed.</li>
 * <li><b>Extent cap.</b> The hive fills a fixed square around its core (BuildPlan.maxExtentChunks) and grows no
 * further.</li>
 * <li><b>Weighted variety.</b> Survivors are chosen weighted-random by piece type: corners rare (curbs winding),
 * branchers/rooms favoured. Royal variants are equal-weighted, so the four slots are any-mix, repeats allowed.</li>
 * </ul>
 * Not yet layered on (later phases): a hard corner-run cap and arm termination (needs per-socket arm history), the
 * needs-driven chamber caps (jelly floor/ceiling, per-type maxima), and biomass/claim-cost gating.
 */
public final class HiveStructurePlanner {

    // Corridor selection weights (balanced feel): corners + straights carry the snaking, tees are the occasional
    // branch, and 4-way pieces (cross, hub) are present but uncommon so the hive doesn't read as a grid.
    private static final int WEIGHT_CORNER = 6;

    private static final int WEIGHT_STRAIGHT = 6;

    private static final int WEIGHT_TEE = 5;

    private static final int WEIGHT_CROSS = 3;

    private static final int WEIGHT_HUB = 3;

    private static final int WEIGHT_CHAMBER = 6;

    private static final int WEIGHT_ROYAL = 10;

    private static final int WEIGHT_DEFAULT = 4;

    // Corner-run cap: at most this many corner pieces may chain back-to-back before a non-corner must break the run.
    private static final int MAX_CORNER_RUN = 3;

    // Straight-run cap: at most this many straights in a row before a corridor must turn, branch, or hit a room.
    private static final int MAX_STRAIGHT_RUN = 2;

    // Hive doors are a fixed square opening, centred on the chunk's wall, from floor level up (used by the cap).
    private static final int DOOR_SIZE = 8;

    /**
     * A hive's build plan: how big it grows and how many of each room it targets. Kept in one place so a future empress
     * tier can expand a hive - bigger extent, higher room budgets, even new room types - by returning a different plan
     * from {@link #planFor}. Egg / host / jelly-vault / raid are hive_door chamber CAPS; minHost, minRaid and
     * minJunctions are FLOORS the hive is guaranteed to reach; roomSpacing keeps every room (hub or chamber) from
     * sitting next to another room, so rooms disperse across the footprint. Jelly-royal and scourge aren't listed -
     * they hang off their own special doors and are limited by those.
     */
    private record BuildPlan(
        int maxExtentChunks,
        int minJunctions,
        int roomSpacing,
        int minHost,
        int minRaid,
        float chamberChance,
        int targetEgg,
        int targetHost,
        int targetJellyVault,
        int targetRaid
    ) {}

    // 3-chunk core + maxExtentChunks out per side -> (2 * maxExtentChunks + 1) square: 9 -> 19x19.
    private static final BuildPlan BASE_PLAN = new BuildPlan(9, 3, 4, 1, 1, 0.25f, 4, 2, 5, 1);

    /**
     * The build plan for a hive. Hook: once the empress system is wired, detect an empress here and return an expanded
     * plan (larger extent, higher targets, additional room types). For now every hive uses the base plan.
     */
    private static BuildPlan planFor(HiveLocation location) {
        return BASE_PLAN;
    }

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
        var claimed = location.claimedChunks();
        java.util.function.Predicate<ChunkPos> chunkIsFree = chunk -> !claimed.contains(chunk);

        var centerChunk = new ChunkPos(location.centerPos());
        var random = level.getRandom();
        var plan = planFor(location);

        // Before growing, resolve dead-end sockets: connect two openings that meet, or wall off a doorway that
        // can't lead anywhere - so every doorway ends up either connected or sealed, never an open hole.
        resolveDeadEnds(level, location, centerChunk, claimed, plan);
        if (frontier.isEmpty()) {
            return false;
        }

        // Shuffle so growth spreads across arms instead of always extending the oldest one.
        List<FrontierSocket> sockets = new ArrayList<>(frontier);
        shuffle(sockets, random);

        for (FrontierSocket socket : sockets) {
            var matches = HivePieceMatcher.matchesFromRegistry(socket, registry, chunkIsFree);
            if (matches.isEmpty()) {
                continue;
            }

            boolean royalSocket = isRoyalDoor(socket.doorType());
            boolean cornerCapped = socket.cornerRun() >= MAX_CORNER_RUN;
            boolean straightCapped = socket.straightRun() >= MAX_STRAIGHT_RUN;

            var eligible = new ArrayList<PieceMatch>(matches.size());
            var viable = new ArrayList<PieceMatch>(matches.size());
            for (PieceMatch match : matches) {
                // Royal hallways are grand entrances: only ever off a royal socket (which only the core has), never on
                // the general corridor network. This caps them at the four core exits and stops royal-to-royal chains.
                if (!royalSocket && isRoyalHallway(match)) {
                    continue;
                }
                // Corner-run cap: never a corner once this run has already hit the max.
                if (cornerCapped && isCorner(match)) {
                    continue;
                }
                // Straight-run cap: never a straight once this run has hit the max (breaks up long straight arms).
                if (straightCapped && isStraight(match)) {
                    continue;
                }
                // Extent cap: drop any candidate that would place a chunk outside the hive's allowed square.
                if (!withinExtent(match, centerChunk, plan.maxExtentChunks())) {
                    continue;
                }
                eligible.add(match);
                // Look-ahead: a viable piece is one whose new doorways don't open straight into a claimed chunk (an
                // immediate dead end). Preferring these stops multi-door pieces like hubs being placed where they
                // strand doors, and stops arms growing into each other.
                if (!createsDeadEnd(match, socket, claimed, centerChunk, plan.maxExtentChunks())) {
                    viable.add(match);
                }
            }
            if (eligible.isEmpty()) {
                continue;
            }

            // Prefer viable candidates; fall back to all eligible only if every option would strand a door.
            var chosen = selectPiece(viable.isEmpty() ? eligible : viable, location, plan, random);
            boolean placed = HiveStructurePlacer.place(level, location, chosen, socket);
            if (placed) {
                // Claim the newly occupied chunks through HiveLocationClaims so all three sources of truth stay
                // in sync (location set, registry chunk index, and the BLib territory map the UI reads) - a direct
                // claimedChunks().add would leave the built chunks unclaimed in the territory/overlay.
                long currentTick = level.getGameTime();
                for (ChunkPos chunk : chosen.occupiedChunks()) {
                    HiveLocationClaims.claim(level, location, chunk, currentTick);
                }
                return true;
            }
        }
        return false;
    }

    /** A royal-door socket is the only place a royal hallway may attach (and only the core has royal sockets). */
    private static boolean isRoyalDoor(String doorType) {
        return doorType != null && doorType.contains("royal");
    }

    /** True if the candidate's piece is a royal hallway (lives under the hallway_royal directory). */
    private static boolean isRoyalHallway(PieceMatch match) {
        return match.piece().id().getPath().contains("hallway_royal");
    }

    /** True if every chunk the match occupies is within maxExtent (Chebyshev) of the core centre chunk. */
    private static boolean withinExtent(PieceMatch match, ChunkPos centerChunk, int maxExtent) {
        for (ChunkPos chunk : match.occupiedChunks()) {
            if (!withinExtentChunk(chunk, centerChunk, maxExtent)) {
                return false;
            }
        }
        return true;
    }

    /** Single-chunk extent test, shared by the footprint check and dead-end detection. */
    private static boolean withinExtentChunk(ChunkPos chunk, ChunkPos centerChunk, int maxExtent) {
        int dx = Math.abs(chunk.x - centerChunk.x);
        int dz = Math.abs(chunk.z - centerChunk.z);
        return Math.max(dx, dz) <= maxExtent;
    }

    /**
     * Resolves dead-end sockets before growth. A socket whose faced chunk is free and in-bounds can still grow (a 1x1
     * piece always fits there), so it is left alone. A socket that cannot grow is either connected to a matching
     * opening that faces it straight back (the two form a passage and are both consumed) or, failing that, walled off
     * with resin so it never reads as a doorway to nowhere.
     */
    private static void resolveDeadEnds(
        ServerLevel level,
        HiveLocation location,
        ChunkPos centerChunk,
        Set<ChunkPos> claimed,
        BuildPlan plan
    ) {
        Set<FrontierSocket> frontier = location.frontierSockets();
        var handled = new HashSet<FrontierSocket>();
        var toRemove = new ArrayList<FrontierSocket>();
        var toCap = new ArrayList<FrontierSocket>();

        for (FrontierSocket socket : new ArrayList<>(frontier)) {
            if (handled.contains(socket)) {
                continue;
            }
            var faced = new ChunkPos(
                socket.chunk().x + socket.facing().getStepX(),
                socket.chunk().z + socket.facing().getStepZ()
            );
            if (!withinExtentChunk(faced, centerChunk, plan.maxExtentChunks())) {
                continue; // faces beyond the hive bound - an outer entrance/exit, leave it open
            }
            if (!claimed.contains(faced)) {
                continue; // faced chunk is free and in-bounds - a live growth point
            }
            // faced chunk is claimed and in-bounds: a real interior dead end - connect or cap below.

            var aligned = findAlignedSocket(socket, faced, frontier, handled);
            if (aligned != null) {
                // Two openings meet at the same boundary: leave the passage open, consume both sockets.
                handled.add(socket);
                handled.add(aligned);
                toRemove.add(socket);
                toRemove.add(aligned);
            } else {
                handled.add(socket);
                toCap.add(socket);
            }
        }

        for (FrontierSocket socket : toRemove) {
            frontier.remove(socket);
        }
        for (FrontierSocket socket : toCap) {
            capDoorway(level, location, socket);
            frontier.remove(socket);
        }
    }

    /** An open socket in {@code faced} that opens straight back at {@code socket} with the same door type, or null. */
    private static FrontierSocket findAlignedSocket(
        FrontierSocket socket,
        ChunkPos faced,
        Set<FrontierSocket> frontier,
        Set<FrontierSocket> handled
    ) {
        Direction back = socket.facing().getOpposite();
        for (FrontierSocket other : frontier) {
            if (other.equals(socket) || handled.contains(other)) {
                continue;
            }
            if (other.chunk().equals(faced) && other.facing() == back && other.doorType().equals(socket.doorType())) {
                return other;
            }
        }
        return null;
    }

    /**
     * Walls off a doorway that cannot connect: fills the air in the socket's doorway plane (the block row at the
     * piece's facing edge, across the chunk and up the slab band) with resin. The surrounding wall is already solid, so
     * this only patches the opening. Underground it seals cleanly; on an above-ground test hive any open air above the
     * piece in that plane fills too, so a cap can read a little tall.
     */
    private static void capDoorway(ServerLevel level, HiveLocation location, FrontierSocket socket) {
        // A hive door is a fixed DOOR_SIZE x DOOR_SIZE opening centred on the chunk's wall, from the floor up. Fill
        // just that region's air with resin; the wall around it and the terrain-blend cells (open air on an
        // above-ground hive) are left alone.
        var resin = AlienResinBlocks.RIBBED_RESIN.get().defaultBlockState();
        var chunk = socket.chunk();
        var facing = socket.facing();
        int floorY = location.hiveFloorY();
        int start = (16 - DOOR_SIZE) / 2; // centre the DOOR_SIZE-wide opening on the 16-wide edge
        var pos = new BlockPos.MutableBlockPos();

        if (facing == Direction.NORTH || facing == Direction.SOUTH) {
            int z = (facing == Direction.NORTH) ? chunk.getMinBlockZ() : chunk.getMaxBlockZ();
            int x0 = chunk.getMinBlockX() + start;
            for (int x = x0; x < x0 + DOOR_SIZE; x++) {
                for (int y = floorY; y < floorY + DOOR_SIZE; y++) {
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, resin, 3);
                    }
                }
            }
        } else {
            int x = (facing == Direction.WEST) ? chunk.getMinBlockX() : chunk.getMaxBlockX();
            int z0 = chunk.getMinBlockZ() + start;
            for (int z = z0; z < z0 + DOOR_SIZE; z++) {
                for (int y = floorY; y < floorY + DOOR_SIZE; y++) {
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, resin, 3);
                    }
                }
            }
        }
    }

    /**
     * Weighted-random choice among candidates. Placements are grouped by piece first, so a piece's odds depend on its
     * type weight alone - not on how many rotations happen to fit. Without this, a rotationally-symmetric piece (e.g. a
     * 4-way hub) generates several identical placements that each get counted and it dominates the roll.
     */
    private static PieceMatch weightedPick(List<PieceMatch> matches, RandomSource random) {
        // Group placements by piece id.
        var byPiece = new LinkedHashMap<String, List<PieceMatch>>();
        for (PieceMatch match : matches) {
            byPiece.computeIfAbsent(match.piece().id().toString(), k -> new ArrayList<>()).add(match);
        }
        var groups = new ArrayList<>(byPiece.values());

        // Pick a piece weighted by its type weight (one weight per distinct piece).
        int total = 0;
        for (var group : groups) {
            total += weightFor(group.get(0));
        }
        List<PieceMatch> chosenGroup;
        if (total <= 0) {
            chosenGroup = groups.get(random.nextInt(groups.size()));
        } else {
            int roll = random.nextInt(total);
            chosenGroup = groups.get(groups.size() - 1); // fallback (unreachable in practice)
            for (var group : groups) {
                roll -= weightFor(group.get(0));
                if (roll < 0) {
                    chosenGroup = group;
                    break;
                }
            }
        }

        // Pick a random placement (rotation/origin) within the chosen piece.
        return chosenGroup.get(random.nextInt(chosenGroup.size()));
    }

    /**
     * Chooses what to build at a socket. Candidates split into corridors and chambers; a chamber is a candidate only
     * while the hive is still under its build-plan target for that type. A general (hive_door) socket always has
     * corridors available, so it places a still-needed chamber with probability chamberChance and a corridor otherwise.
     * A special socket (jelly / scourge) only fits its chamber, so that is placed. Chambers thus stay a controlled,
     * distributed set of destinations while corridors carry the rest of the footprint.
     */
    private static PieceMatch selectPiece(List<PieceMatch> eligible, HiveLocation location, BuildPlan plan, RandomSource random) {
        // Spacing: every room (hub or chamber) must sit at least roomSpacing chunks from every other room, so rooms
        // disperse across the footprint instead of clustering at the core.
        var pool = new ArrayList<PieceMatch>(eligible.size());
        for (PieceMatch match : eligible) {
            // Every room (hub or chamber) spaces off every other room; hubs additionally space off royal hallways
            // (a hub jammed against a royal entrance tangles the doorways and strands dead ends).
            if (
                isRoom(match) && !spacedFromPieces(
                    location,
                    match,
                    plan.roomSpacing(),
                    id -> id.contains("hub") || (id.contains("chamber") && !id.contains("queen_chamber"))
                )
            ) {
                continue;
            }
            if (isHub(match) && !spacedFromPieces(location, match, plan.roomSpacing(), id -> id.contains("hallway_royal"))) {
                continue;
            }
            pool.add(match);
        }

        // Floor 1: guarantee a spread-out minimum of fast-travel hub junctions.
        if (countHubPlacements(location) < plan.minJunctions()) {
            var hubs = pool.stream().filter(m -> isHub(m)).toList();
            if (!hubs.isEmpty()) {
                return weightedPick(hubs, random);
            }
        }
        // Floor 2: guarantee at least one host chamber (host parties fast-travel to it).
        if (countChamberPlacements(location, "chamber_host", 4) < plan.minHost()) {
            var hosts = pool.stream().filter(m -> m.piece().id().getPath().contains("chamber_host")).toList();
            if (!hosts.isEmpty()) {
                return weightedPick(hosts, random);
            }
        }
        // Floor 3: guarantee at least one raid chamber (a hive needs one to unlock raids).
        if (countChamberPlacements(location, "chamber_raid", 4) < plan.minRaid()) {
            var raids = pool.stream().filter(m -> m.piece().id().getPath().contains("chamber_raid")).toList();
            if (!raids.isEmpty()) {
                return weightedPick(raids, random);
            }
        }

        // Otherwise: corridor / chamber split, chambers gated by their caps.
        var corridors = new ArrayList<PieceMatch>();
        var chambers = new ArrayList<PieceMatch>();
        for (PieceMatch match : pool) {
            if (isChamber(match)) {
                if (chamberNeeded(location, match, plan)) {
                    chambers.add(match);
                }
            } else {
                corridors.add(match);
            }
        }

        boolean haveChamber = !chambers.isEmpty();
        boolean haveCorridor = !corridors.isEmpty();
        if (haveChamber && haveCorridor) {
            return random.nextFloat() < plan.chamberChance()
                ? weightedPick(chambers, random)
                : weightedPick(corridors, random);
        }
        if (haveChamber) {
            return weightedPick(chambers, random);
        }
        if (haveCorridor) {
            return weightedPick(corridors, random);
        }
        return weightedPick(pool.isEmpty() ? eligible : pool, random); // safety net - shouldn't occur
    }

    /** True if the candidate is a chamber (room) rather than a corridor piece. */
    private static boolean isChamber(PieceMatch match) {
        return match.piece().id().getPath().contains("chamber");
    }

    /** True if the candidate is a corner corridor piece (for the corner-run cap). */
    private static boolean isCorner(PieceMatch match) {
        return match.piece().id().getPath().contains("corner");
    }

    /** True if the candidate is a straight corridor piece (for the straight-run cap). */
    private static boolean isStraight(PieceMatch match) {
        return match.piece().id().getPath().contains("straight");
    }

    /** True if the candidate is a hub - a 2x2 junction room, the fast-travel node type. */
    private static boolean isHub(PieceMatch match) {
        return match.piece().id().getPath().contains("hub");
    }

    /** Number of hub placements in the hive (hub chunks / 4, since hubs are 2x2). */
    private static int countHubPlacements(HiveLocation location) {
        return countChamberPlacements(location, "hub", 4);
    }

    /** True if the candidate is a room (a hub or any chamber) - the pieces that space off one another. */
    private static boolean isRoom(PieceMatch match) {
        return isHub(match) || isChamber(match);
    }

    /**
     * True if every chunk of the match sits at least {@code spacing} chunks (Chebyshev) from every existing room chunk
     * (any hub or grown chamber; the queen core is excluded). Keeps rooms dispersed across the footprint.
     */
    /**
     * True if every chunk of the match sits at least {@code spacing} chunks (Chebyshev) from every existing chunk whose
     * piece id passes {@code isBlocker}. Spaces rooms off other rooms, and hubs off royal hallways.
     */
    private static boolean spacedFromPieces(
        HiveLocation location,
        PieceMatch match,
        int spacing,
        java.util.function.Predicate<String> isBlocker
    ) {
        for (var entry : location.structurePieceByChunk().entrySet()) {
            if (!isBlocker.test(entry.getValue())) {
                continue;
            }
            ChunkPos other = entry.getKey();
            for (ChunkPos c : match.occupiedChunks()) {
                if (Math.max(Math.abs(c.x - other.x), Math.abs(c.z - other.z)) < spacing) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Look-ahead: true if placing {@code match} off {@code socket} would open a new doorway straight into an already
     * claimed in-bounds chunk - an immediate dead end. Out-of-bounds doorways are entrances and free ones are live, so
     * neither counts against the piece.
     */
    private static boolean createsDeadEnd(
        PieceMatch match,
        FrontierSocket socket,
        java.util.Set<ChunkPos> claimed,
        ChunkPos centerChunk,
        int maxExtent
    ) {
        int connectedCellX = socket.chunk().x + socket.facing().getStepX() - match.originChunk().x;
        int connectedCellZ = socket.chunk().z + socket.facing().getStepZ() - match.originChunk().z;
        for (FrontierSocket ns : match.openFrontierSockets(connectedCellX, connectedCellZ, socket.facing().getOpposite(), 0, 0)) {
            var faced = new ChunkPos(ns.chunk().x + ns.facing().getStepX(), ns.chunk().z + ns.facing().getStepZ());
            if (withinExtentChunk(faced, centerChunk, maxExtent) && claimed.contains(faced)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if this chamber may still be placed under the build plan. Egg / host / jelly-vault / raid are capped at
     * their targets; every other chamber (jelly-royal, scourge) is ungated here and limited only by its special doors.
     */
    private static boolean chamberNeeded(HiveLocation location, PieceMatch match, BuildPlan plan) {
        String path = match.piece().id().getPath();
        int footprint = Math.max(1, match.piece().footprintChunksX() * match.piece().footprintChunksZ());
        if (path.contains("chamber_egg")) {
            return countChamberPlacements(location, "chamber_egg", footprint) < plan.targetEgg();
        }
        if (path.contains("chamber_host")) {
            return countChamberPlacements(location, "chamber_host", footprint) < plan.targetHost();
        }
        if (path.contains("chamber_jelly_vault")) {
            return countChamberPlacements(location, "chamber_jelly_vault", footprint) < plan.targetJellyVault();
        }
        if (path.contains("chamber_raid")) {
            return countChamberPlacements(location, "chamber_raid", footprint) < plan.targetRaid();
        }
        return true;
    }

    /** Counts placed chambers of a type from the recorded piece ids (matching chunks / footprint = placements). */
    private static int countChamberPlacements(HiveLocation location, String idSubstring, int footprintChunks) {
        int chunks = 0;
        for (String pieceId : location.structurePieceByChunk().values()) {
            if (pieceId.contains(idSubstring)) {
                chunks++;
            }
        }
        return chunks / footprintChunks;
    }

    /** Selection weight for a candidate, by piece type parsed from its id path. */
    private static int weightFor(PieceMatch match) {
        String path = match.piece().id().getPath();
        if (path.contains("chamber")) {
            return WEIGHT_CHAMBER;
        }
        if (path.contains("royal")) {
            return WEIGHT_ROYAL;
        }
        if (path.contains("corner")) {
            return WEIGHT_CORNER;
        }
        if (path.contains("straight")) {
            return WEIGHT_STRAIGHT;
        }
        if (path.contains("tee")) {
            return WEIGHT_TEE;
        }
        if (path.contains("cross")) {
            return WEIGHT_CROSS;
        }
        if (path.contains("hub")) {
            return WEIGHT_HUB;
        }
        return WEIGHT_DEFAULT;
    }

    /** In-place Fisher-Yates shuffle using the level RandomSource (Collections.shuffle needs a java.util.Random). */
    private static <T> void shuffle(List<T> list, RandomSource random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }
}
