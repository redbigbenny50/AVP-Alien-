package com.alien.common.gameplay.hive.structure;

import com.alien.Alien;
import com.alien.common.gameplay.hive.growth.BiomassIncome;
import com.alien.common.gameplay.hive.growth.HiveLocationClaims;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.init.block.AlienBlocks;
import com.alien.common.registry.init.block.AlienResinBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Goal-based hive builder (stage 2a of the goal-based planner). When {@link #ENABLED}, this replaces the greedy
 * {@link HiveStructurePlanner}: instead of extending every open socket, it rolls the hive's {@link HiveBlueprint} and
 * routes corridors from the network toward each room goal, dropping the room when a route arrives near it. One piece
 * per call, so growth stays gradual.
 * <p>
 * Scope of this stage: route to goals and place their rooms. Exits, junction placement where routes meet, the
 * connectivity flood-fill, and dynamic goals are later stages. Toggle at runtime with {@code /hive router}; off by
 * default, so the greedy planner keeps running unless you opt in.
 */
public final class HiveRouter {

    /** Runtime toggle (flip with {@code /hive router}). Off = greedy planner; on = this router. */
    public static volatile boolean ENABLED = true;

    /**
     * The construction-economy switch (design §8.6). ON (the default): pieces are COMMISSIONED - routed through
     * {@link #commission}, which creates a {@code CarveSite} and (until the progressive carve tick lands at step 3)
     * completes it immediately, so behavior is identical to the stamp while the seam is exercised for real. OFF: the
     * legacy instant stamp, kept as a one-flip fallback for A/B debugging on the tester's world - if a hive is not
     * building, flip this off; if it builds, the bug is in the carve layer, if it still does not, it is upstream in
     * routing.
     */
    public static volatile boolean CARVE_ENABLED = true;

    /**
     * Resin cost multiplier (design §6, step 4): a piece's resin debt = this factor x the hive's CURRENT per-chunk
     * claim cost x the piece's footprint chunks, fixed at commission. Riding on claimCost means the price scales
     * super-linearly with hive size for free; 1.5x makes a healthy hive never visibly stall while a drained one freezes
     * mid-corridor. Tune here.
     */
    public static final double RESIN_COST_FACTOR = 1.5;

    private static final int BASE_EXTENT = 9; // base footprint radius (19x19)

    private static final int EMPRESS_EXTENT = 11; // empress-influenced footprint radius (23x23)

    private static final int GOAL_REACH = 1; // drop the room when a socket points within this many chunks of a goal

    private static final int RELOCATE_RADIUS = 4; // if a goal's spot is taken/boxed, look this far for a free chunk

    private static final int DOOR_SIZE = 8; // hive doors are a fixed 8x8 opening centred on the wall

    private static final int STITCH_RADIUS = 8; // leftover open doors this close try to connect before sealing

    private static final int HUB_BRIDGE_GAP = 3; // stitches longer than this many chunks may bridge with a hub

    private static final int STITCH_BUDGET = 300; // hard cap on stitch-phase ticks per hive (runaway backstop)

    private static final int VIA_MIN_ROUTE = 5; // routes to goals farther than this arc through a waypoint

    private static final int VIA_OFFSET_MAX = 3; // how far sideways (2..this) a route's waypoint swings

    /** Hives that have entered the stitching phase (all rooms/exits routed), for one-time phase logging. */
    private static final java.util.Map<HiveLocation, Boolean> STITCHING = java.util.Collections.synchronizedMap(
        new java.util.WeakHashMap<>()
    );

    /**
     * Hives under empress influence: larger footprint, expanded blueprint. Wired by the empress system via
     * {@link #setEmpressInfluence}; memory-only, so that system must re-assert influence on world load.
     */
    private static final java.util.Set<HiveLocation> EMPRESS_INFLUENCED =
        java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    /** The footprint radius for the hive currently being routed (set at the top of {@link #route}). */
    private static int activeExtent = BASE_EXTENT;

    /** Stitch-phase tick counter per hive - past STITCH_BUDGET the hive seals and finishes (runaway backstop). */
    private static final java.util.Map<HiveLocation, Integer> STITCH_TICKS = java.util.Collections.synchronizedMap(
        new java.util.WeakHashMap<>()
    );

    /** Finished hives (nothing left to build/stitch) so their tick is a no-op instead of a full re-scan. */
    private static final java.util.Map<HiveLocation, Boolean> DONE = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private HiveRouter() {}

    /**
     * HOOK for the empress system: call when a hive gains (or loses) empress influence. Gaining influence expands the
     * footprint to EMPRESS_EXTENT (23x23), appends the empress blueprint additions (an extra raid chamber, plus
     * whatever else gets designed), and RESUMES construction on a finished hive - the done/stitching state is cleared
     * so the next growth tick starts routing the new goals. Influence is memory-only here; the caller owns persistence
     * and must re-assert it on load.
     */
    /** Whether this hive is currently under empress influence (also raises the member cap in the economy). */
    public static boolean isEmpressInfluenced(HiveLocation location) {
        return EMPRESS_INFLUENCED.contains(location);
    }

    public static void setEmpressInfluence(HiveLocation location, boolean influenced) {
        boolean changed = influenced ? EMPRESS_INFLUENCED.add(location) : EMPRESS_INFLUENCED.remove(location);
        if (changed && influenced) {
            DONE.remove(location);
            STITCHING.remove(location);
            STITCH_TICKS.remove(location);
            Alien.LOGGER.info(
                "Hive at {}: empress influence gained - expanding to 23x23 and resuming construction.",
                location.centerPos()
            );
        }
    }

    /** Advances the hive one piece toward its next unbuilt blueprint goal. Returns true if something was placed. */
    public static boolean route(MinecraftServer server, ServerLevel level, HiveLocation location) {
        // Construction economy step 3 (design §8.3/§8.4): a piece mid-carve blocks ALL routing. Its socket was
        // consumed at commission and finalizePlacement hasn't registered its doorways yet, so building past it is
        // structurally impossible - and this gate also pauses the merge/redirect/seal passes until the site resolves.
        // Every placement path in this class (tryDropRoom, advanceToward, placeBridgingStraight, attachAt) is private
        // and reachable only through route(), so this single gate covers all of them; the only placement callers
        // outside route() are founding (legacy until step 6) and the pre-router HiveStructurePlanner (ENABLED=false).
        if (location.hasActiveCarveSite()) {
            return false;
        }
        if (Boolean.TRUE.equals(DONE.get(location))) {
            return false; // this hive finished building and stitching - don't re-scan every cycle
        }
        var frontier = location.frontierSockets();
        if (frontier.isEmpty()) {
            return false;
        }
        var registry = HivePieceRegistry.get(server);
        var center = new ChunkPos(location.centerPos());
        long currentTick = level.getGameTime();
        var random = level.getRandom();

        activeExtent = EMPRESS_INFLUENCED.contains(location) ? EMPRESS_EXTENT : BASE_EXTENT;
        var blueprint = HiveBlueprintGenerator.generate(center, BASE_EXTENT, center.toLong());
        if (activeExtent > BASE_EXTENT) {
            // Empress tier: same base plan, plus extra rooms and new rim exits out in the expanded band.
            blueprint = HiveBlueprintGenerator.expand(blueprint, center, BASE_EXTENT, EMPRESS_EXTENT, center.toLong());
        }

        // REPAIR (live worlds): older foundings recorded the queen chamber's outer cells with a role but no
        // piece id, leaving them out of structurePieceByChunk - the occupancy map routing checks. Re-assert them
        // so corridors can never carve into the queen's chamber on existing hives either.
        for (var roleEntry : location.structureRoleByChunk().entrySet()) {
            var role = roleEntry.getValue();
            if (
                (role == HiveStructureRole.QUEEN_CHAMBER_CENTER || role == HiveStructureRole.QUEEN_CHAMBER_PART)
                    && !location.structurePieceByChunk().containsKey(roleEntry.getKey())
            ) {
                location.structurePieceByChunk().put(roleEntry.getKey(), HivePieceCatalog.QUEEN_CHAMBER.toString());
            }
        }

        // Every tick, wherever two open doors have grown to meet head-on, join them into a passage - so routes that
        // approach each other link up into loops as they build, giving an interconnected mesh rather than lone lines.
        connectAlignedSockets(location, center);

        // And wherever a corridor head has hit the SIDE of an existing hallway, upgrade that hallway in place
        // (straight/corner -> tee, tee -> cross) so they join instead of dead-ending - circuit-board style.
        mergeIntoHallways(level, registry, location, center);

        // ROYAL PRIORITY (router royal support): an open royal doorway is attempted FIRST, every single cycle,
        // before special rooms, goals, and corridors - the queen's ring outranks all other growth. The matcher's
        // door-type filter makes the pairing safe in both directions for free: a royal socket only ever yields
        // royal-hallway matches, and ordinary sockets can never yield royal pieces. SOFT priority by design: a
        // royal door with no fit THIS cycle (terrain, another hive) falls through so ordinary growth continues,
        // and because the socket stays on the frontier it is re-attempted at the top of every future cycle until
        // it fits - a standing guarantee, unlike the old stamp-once-at-founding which skipped a blocked hall
        // forever. Hard-stalling instead would let one permanently blocked door freeze the whole hive (never-wedge).
        if (placeRoyalHallways(level, registry, location, random, currentTick)) {
            return true;
        }

        // Special doors (scourge / jelly) attach their chamber DIRECTLY, the moment the door exists - these are
        // mandatory companion rooms (the raid chamber's scourge room, a royal hallway's jelly room), not routed goals.
        if (attachSpecialRooms(level, registry, location, center, currentTick)) {
            return true;
        }

        // Reserve the 3x3 around every unbuilt room goal: corridors may never claim these chunks, so a room's
        // space can't be scribbled over by routing - the cause of corridor "tumors" crowding a goal.
        var reserved = new HashSet<ChunkPos>();
        var pending = new ArrayList<HiveBlueprint.Goal>();
        var goalRank = new java.util.HashMap<String, Integer>();
        for (var goal : blueprint.goals()) {
            // Rank-based completion: the Nth goal of a type is built only once N rooms of that type exist. Proximity
            // checks let one relocated room silently satisfy two neighbouring same-type goals (the missing 5th egg).
            int rank = goalRank.merge(goal.roomType(), 1, Integer::sum) - 1;
            if (countRoomsOfType(location, goal.roomType()) > rank) {
                continue;
            }
            pending.add(goal);
            // Right-sized reserves: a 1x1 room (egg, jelly) needs exactly its chunk - reserving a 3x3 for each of
            // them walled off a third of the footprint and boxed the late routes. 2x2 rooms keep the 3x3 margin.
            boolean big = goal.roomType().contains("chamber_host")
                || goal.roomType().contains("chamber_raid") || goal.roomType().contains("hub");
            int r = big ? 1 : 0;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    reserved.add(new ChunkPos(goal.chunk().x + dx, goal.chunk().z + dz));
                }
            }
        }
        // CONDITIONAL room: harvest chambers exist only when terrain mob spawners were captured during building.
        // One chamber while any spawner is pending; another when every existing chamber's four slots are already
        // filled and spawners still wait (overflow, per spec: 4 per chamber, second chamber past that).
        if (!location.pendingHarvestSpawners().isEmpty()) {
            int builtHarvest = countRoomsOfType(location, "chamber_harvest");
            int wantedHarvest = HarvestChamberTask.isCapacityFull(location)
                ? builtHarvest + 1
                : Math.max(1, builtHarvest);
            if (wantedHarvest > builtHarvest) {
                var harvestTarget = harvestGoalChunk(location, center);
                if (harvestTarget != null) {
                    pending.add(new HiveBlueprint.Goal("chamber_harvest", harvestTarget));
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            reserved.add(new ChunkPos(harvestTarget.x + dx, harvestTarget.z + dz));
                        }
                    }
                }
            }
        }

        // Mandatory rooms route FIRST, onto pristine ground: nearest-first ordering sent far raids/hosts into a map
        // already cluttered with reserves and corridors, and they kept boxing out. Priority class, then distance.
        pending.sort(
            java.util.Comparator
                .comparingInt((HiveBlueprint.Goal g) -> goalPriority(g.roomType()))
                .thenComparingInt(g -> cheby(g.chunk(), center))
        );

        // Also reserve the chunk each unattached special door faces, so corridors can't steal a mandatory room's spot.
        for (FrontierSocket socket : frontier) {
            if (isSpecialDoor(socket.doorType())) {
                reserved.add(growthChunk(socket));
            }
        }

        // Try each target nearest-first: drop its room if a socket is already beside it, else advance one piece - but
        // only a piece that makes real progress (strictly closer, or opening a doorway that will be). A blocked target
        // is skipped for the next instead of ground against (which used to pile corridor into a tumor).
        for (var goal : pending) {
            boolean bigRoom = goal.roomType().contains("chamber_host")
                || goal.roomType().contains("chamber_raid") || goal.roomType().contains("hub");
            var eff = effectiveTarget(location, goal.chunk(), center, bigRoom ? RELOCATE_RADIUS + 2 : RELOCATE_RADIUS);
            if (eff == null) {
                continue; // boxed in - skip
            }
            // The room may use its own reserved 3x3, but never another unbuilt goal's.
            var othersReserved = new HashSet<>(reserved);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    othersReserved.remove(new ChunkPos(goal.chunk().x + dx, goal.chunk().z + dz));
                }
            }
            if (tryDropRoom(level, registry, location, goal.roomType(), eff, center, othersReserved, currentTick)) {
                return true;
            }
            // Long routes arc: swing out through a per-goal sideways waypoint first, then bend in to the goal - so
            // corridors curve across the hive instead of running one dead-straight line. Seeded, so hives differ.
            var via = viaPoint(goal.chunk(), center, reserved);
            if (via != null && !location.structurePieceByChunk().containsKey(via)) {
                if (advanceToward(level, registry, location, via, center, reserved, currentTick, random)) {
                    return true;
                }
            }
            if (advanceToward(level, registry, location, eff, center, reserved, currentTick, random)) {
                return true;
            }
        }
        // Exits are guaranteed OPENINGS, one per side: an exit only counts once its rim chunk holds a piece with an
        // outward-facing doorway. A hallway that landed there without one gets the outward door carved in; a room
        // blocking the spot slides the exit along the rim to the nearest workable chunk; otherwise we route to it.
        for (var exit : blueprint.exits()) {
            var side = exitSide(exit, center);
            if (side == null) {
                continue; // not on the current rim (e.g. an old 19x19 exit after empress expansion) - already open
            }
            var resolved = resolveExit(location, exit, side, center);
            if (resolved == null) {
                continue; // whole stretch of rim blocked by rooms - nothing workable near this exit
            }
            String pieceId = location.structurePieceByChunk().get(resolved);
            if (pieceId == null) {
                if (advanceToward(level, registry, location, resolved, center, reserved, currentTick, random)) {
                    return true;
                }
                continue;
            }
            var faces = openDoorFaces(level, location, resolved);
            if (!faces.isEmpty() && !faces.contains(side) && faces.size() < 4) {
                var desired = EnumSet.copyOf(faces);
                desired.add(side);
                if (restampHallway(level, registry, location, resolved, desired)) {
                    Alien.LOGGER.info("Hive {}: carved {} exit opening at {}.", center, side, resolved);
                    return true;
                }
            }
        }

        // Stitch pass: leftover open interior doors try to CONNECT before anything is sealed - each routes toward
        // its nearest open neighbour (midpoint target); the head-on join and hallway-merge passes fuse them on
        // arrival. This is what turns "two capped doors across a gap" into a connecting corridor.
        if (STITCHING.putIfAbsent(location, Boolean.TRUE) == null) {
            Alien.LOGGER.info("Hive {}: building complete - now stitching leftover doorways together.", center);
        }
        int stitchTicks = STITCH_TICKS.merge(location, 1, Integer::sum);
        if (stitchTicks > STITCH_BUDGET) {
            int sealedNow = finalizeHive(level, registry, location, center, currentTick);
            Alien.LOGGER.warn(
                "Hive {}: stitch budget exhausted - sealed {} remaining doorways and finished. Rooms: {}.",
                center,
                sealedNow,
                roomSummary(location)
            );
            warnMissingRooms(location, blueprint, center);
            DONE.put(location, Boolean.TRUE);
            return sealedNow > 0;
        }
        // Bonus junctions from bridges should fill the under-quota hub type first, instead of minting a fourth
        // three-way while the four-way goal starves.
        int fourWayGoals = 0;
        for (var g : blueprint.goals()) {
            if (g.roomType().contains("hub_2x2_4way")) {
                fourWayGoals++;
            }
        }
        String bridgeHubType =
            countRoomsOfType(location, "hub_2x2_4way") < fourWayGoals ? "hub_2x2_4way" : "hub";

        var interior = new ArrayList<FrontierSocket>();
        for (FrontierSocket socket : frontier) {
            if (cheby(growthChunk(socket), center) <= activeExtent) {
                interior.add(socket);
            }
        }
        for (FrontierSocket socket : interior) {
            var partner = nearestStitchPartner(interior, socket);
            if (partner == null) {
                // No open door to pair with - target the nearest hallway chunk instead: grow the connecting corridor
                // at it and the side-merge pass upgrades that hallway (straight -> tee, tee -> cross) on contact.
                var hall = nearestHallwayChunk(location, socket);
                if (
                    hall != null
                        && advanceToward(level, registry, location, hall, center, reserved, currentTick, random, socket, true)
                ) {
                    return true;
                }
                continue;
            }
            var g1 = growthChunk(socket);
            var g2 = growthChunk(partner);
            var mid = new ChunkPos((g1.x + g2.x) / 2, (g1.z + g2.z) / 2);
            // A long stitch (more than HUB_BRIDGE_GAP chunks of corridor) may bridge with a hub at the midpoint - a
            // proper junction whose spare doors invite further connections - instead of one long featureless run.
            if (
                cheby(g1, g2) > HUB_BRIDGE_GAP
                    && tryDropRoom(level, registry, location, bridgeHubType, mid, center, reserved, currentTick)
            ) {
                return true;
            }
            if (advanceToward(level, registry, location, mid, center, reserved, currentTick, random, socket, true)) {
                return true;
            }
        }

        // Nothing can progress (or everything is built): seal leftover interior doors, keep the rim entrances, and
        // remember this hive is done so its growth tick becomes a cheap no-op.
        int sealed = finalizeHive(level, registry, location, center, currentTick);
        Alien.LOGGER.info(
            "Hive {}: stitching complete - {} unconnectable doorways sealed. Hive finished. Rooms: {}.",
            center,
            sealed,
            roomSummary(location)
        );
        warnMissingRooms(location, blueprint, center);
        DONE.put(location, Boolean.TRUE);
        return sealed > 0;
    }

    /**
     * A deterministic sideways waypoint for a long route: near the route's midpoint, offset 2..VIA_OFFSET_MAX chunks
     * perpendicular to the route's dominant axis (side and size seeded per goal). Null for short routes, out-of-bounds
     * swings, or waypoints landing in reserved ground. The route grows to this first, then bends in to the goal - which
     * is what puts curves in otherwise straight corridors. Once the waypoint is claimed, routing continues to the goal
     * itself.
     */
    private static ChunkPos viaPoint(ChunkPos goal, ChunkPos center, Set<ChunkPos> reserved) {
        int dx = goal.x - center.x;
        int dz = goal.z - center.z;
        if (Math.max(Math.abs(dx), Math.abs(dz)) <= VIA_MIN_ROUTE) {
            return null; // short route - straight is fine
        }
        var rnd = net.minecraft.util.RandomSource.create(center.toLong() ^ (31L * goal.toLong()) ^ 0x9E3779B97F4A7C15L);
        int off = (2 + rnd.nextInt(VIA_OFFSET_MAX - 1)) * (rnd.nextBoolean() ? 1 : -1);
        int midX = center.x + dx / 2;
        int midZ = center.z + dz / 2;
        // Offset perpendicular to the dominant travel axis.
        var via = (Math.abs(dx) >= Math.abs(dz))
            ? new ChunkPos(midX, midZ + off)
            : new ChunkPos(midX + off, midZ);
        if (Math.max(Math.abs(via.x - center.x), Math.abs(via.z - center.z)) > activeExtent || reserved.contains(via)) {
            return null; // swing would leave the footprint or sit in a room's reserved ground - go direct instead
        }
        return via;
    }

    /** Human-readable inventory of the hive's placed rooms, e.g. "5 egg chambers, 2 host chambers, 1 raid chamber". */
    private static String roomSummary(HiveLocation location) {
        int egg = 0;
        int host = 0;
        int raid = 0;
        int jellyVault = 0;
        int jellyRoyal = 0;
        int scourge = 0;
        int hub4 = 0;
        int hub3 = 0;
        for (String id : location.structurePieceByChunk().values()) {
            if (id.contains("chamber_egg")) {
                egg++;
            } else if (id.contains("chamber_host")) {
                host++;
            } else if (id.contains("chamber_raid")) {
                raid++;
            } else if (id.contains("chamber_jelly_vault")) {
                jellyVault++;
            } else if (id.contains("chamber_jelly_royal")) {
                jellyRoyal++;
            } else if (id.contains("chamber_scourge")) {
                scourge++;
            } else if (id.contains("hub_2x2_4way")) {
                hub4++;
            } else if (id.contains("hub_2x2_3way")) {
                hub3++;
            }
        }
        var parts = new ArrayList<String>();
        addCount(parts, egg, "egg chamber"); // 1x1: chunks = placements
        addCount(parts, host / 4, "host chamber"); // 2x2: four chunks per placement
        addCount(parts, raid / 4, "raid chamber");
        addCount(parts, jellyVault, "jelly vault");
        addCount(parts, jellyRoyal, "royal jelly chamber");
        addCount(parts, scourge, "scourge chamber");
        addCount(parts, hub4 / 4, "four-way hub");
        addCount(parts, hub3 / 4, "three-way hub");
        return parts.isEmpty() ? "none" : String.join(", ", parts);
    }

    private static void addCount(java.util.List<String> parts, int count, String name) {
        if (count > 0) {
            parts.add(count + " " + name + (count == 1 ? "" : "s"));
        }
    }

    /** The nearest non-royal hallway chunk within STITCH_RADIUS of this door (excluding its own piece), or null. */
    private static ChunkPos nearestHallwayChunk(HiveLocation location, FrontierSocket socket) {
        var from = growthChunk(socket);
        ChunkPos best = null;
        int bestD = Integer.MAX_VALUE;
        for (var entry : location.structurePieceByChunk().entrySet()) {
            String id = entry.getValue();
            if (!id.contains("hallway") || id.contains("hallway_royal")) {
                continue; // 2x1s are fine targets now - the side-merge splits them into a junction + straight
            }
            ChunkPos chunk = entry.getKey();
            if (cheby(chunk, socket.chunk()) <= 1) {
                continue; // its own piece / immediate neighbour - not a stitch, a micro-loop
            }
            // Forward half-space only (see nearestStitchPartner) - behind the door is its own arm.
            if (socket.facing().getStepX() * (chunk.x - from.x) + socket.facing().getStepZ() * (chunk.z - from.z) < 0) {
                continue;
            }
            int d = cheby(from, chunk);
            if (d <= STITCH_RADIUS && d < bestD) {
                bestD = d;
                best = chunk;
            }
        }
        return best;
    }

    /** The nearest OTHER open interior door within STITCH_RADIUS (by growth-chunk distance), or null. */
    private static FrontierSocket nearestStitchPartner(java.util.List<FrontierSocket> interior, FrontierSocket socket) {
        var from = growthChunk(socket);
        FrontierSocket best = null;
        int bestD = Integer.MAX_VALUE;
        var facing = socket.facing();
        for (FrontierSocket other : interior) {
            if (other.equals(socket) || cheby(other.chunk(), socket.chunk()) <= 1) {
                continue; // not itself, and not a sibling door of the same/adjacent piece (a micro-loop, not a stitch)
            }
            var og = growthChunk(other);
            // Forward half-space only: a route can only make progress ahead/sideways of its door - behind it is its
            // own arm, and routing there is both infeasible under the progress rule and a pointless micro-loop.
            if (facing.getStepX() * (og.x - from.x) + facing.getStepZ() * (og.z - from.z) < 0) {
                continue;
            }
            int d = cheby(from, og);
            if (d <= STITCH_RADIUS && d < bestD) {
                bestD = d;
                best = other;
            }
        }
        return best;
    }

    /** Step 1: if a socket already points within GOAL_REACH of the target, place the goal's room there. */
    private static boolean tryDropRoom(
        ServerLevel level,
        HivePieceRegistry registry,
        HiveLocation location,
        String roomType,
        ChunkPos goalChunk,
        ChunkPos center,
        Set<ChunkPos> forbidden,
        long currentTick
    ) {
        // Build checks test STRUCTURE occupancy, never territory claims: the biomass economy claims ground
        // without building, and claimed-but-empty ground is exactly where the hive is allowed to build.
        var built = location.structurePieceByChunk().keySet();
        Predicate<ChunkPos> chunkIsFree = c -> !built.contains(c);
        for (FrontierSocket socket : new ArrayList<>(location.frontierSockets())) {
            if (cheby(growthChunk(socket), goalChunk) > GOAL_REACH) {
                continue;
            }
            for (PieceMatch match : HivePieceMatcher.matchesFromRegistry(socket, registry, chunkIsFree)) {
                if (
                    matchesType(match, roomType) && withinExtent(match, center)
                        && !occupiesReserved(match, forbidden)
                        && place(level, location, match, socket, currentTick)
                ) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Step 2: one corridor piece toward the target - only if it truly progresses; false = blocked, caller skips. */
    private static boolean advanceToward(
        ServerLevel level,
        HivePieceRegistry registry,
        HiveLocation location,
        ChunkPos goalChunk,
        ChunkPos center,
        Set<ChunkPos> reserved,
        long currentTick,
        net.minecraft.util.RandomSource random
    ) {
        return advanceToward(level, registry, location, goalChunk, center, reserved, currentTick, random, null, false);
    }

    /**
     * As above; {@code only} restricts growth to that single socket (stitching grows FROM the door being stitched, so
     * the door itself is consumed), and {@code plainOnly} restricts pieces to 1x1 straights/corners (no tee/cross
     * spares) - together these make stitching strictly convergent: every stitch REDUCES the open-door count, so the
     * phase must terminate instead of feeding itself new doors forever.
     */
    private static boolean advanceToward(
        ServerLevel level,
        HivePieceRegistry registry,
        HiveLocation location,
        ChunkPos goalChunk,
        ChunkPos center,
        Set<ChunkPos> reserved,
        long currentTick,
        net.minecraft.util.RandomSource random,
        FrontierSocket only,
        boolean plainOnly
    ) {
        // Build checks test STRUCTURE occupancy, never territory claims: the biomass economy claims ground
        // without building, and claimed-but-empty ground is exactly where the hive is allowed to build.
        var built = location.structurePieceByChunk().keySet();
        Predicate<ChunkPos> chunkIsFree = c -> !built.contains(c);
        var frontier = location.frontierSockets();

        // How close we already get to this target; a placement must beat it or open a doorway that will. When growth
        // is restricted to one socket (stitching), reach is measured from THAT socket - measuring globally let any
        // unrelated door that happened to sit nearer the midpoint veto the stitch, which sealed most of them.
        int currentReach = Integer.MAX_VALUE;
        if (only != null) {
            currentReach = cheby(growthChunk(only), goalChunk);
        } else {
            for (FrontierSocket socket : frontier) {
                currentReach = Math.min(currentReach, cheby(growthChunk(socket), goalChunk));
            }
        }

        int bestScore = Integer.MAX_VALUE;
        PieceMatch bestMatch = null;
        FrontierSocket bestSocket = null;
        var candidates = (only != null) ? java.util.List.of(only) : new ArrayList<>(frontier);
        for (FrontierSocket socket : candidates) {
            for (PieceMatch match : HivePieceMatcher.matchesFromRegistry(socket, registry, chunkIsFree)) {
                if (!withinExtent(match, center) || !isCorridor(match) || occupiesReserved(match, reserved)) {
                    continue;
                }
                if (plainOnly && !isPlainCorridor(match)) {
                    continue;
                }
                // Junction spacing: a routed tee/cross may not sit adjacent to an existing tee/cross - at least one
                // plain corridor piece between junctions, so they read as deliberate nodes rather than junction spam.
                // (Collision merges are exempt: an upgrade happens exactly where two corridors meet.)
                if (isBrancher(match) && nearJunction(location, match)) {
                    continue;
                }
                int dist = distanceToGoal(match, goalChunk);
                // Progress rule: the piece must land closer than the frontier already reaches, land ON the target
                // chunk itself (dist 0 - the connecting piece of a gap stitch or the last piece of an exit route,
                // where currentReach is already 0 and nothing can be "closer"), or open a doorway onto a free
                // in-bounds chunk that is closer. Without this, a blocked route lays sideways filler forever.
                if (dist != 0 && dist >= currentReach && !opensCloser(match, socket, goalChunk, currentReach, built, center)) {
                    continue;
                }
                int score = dist * 4 + pieceBias(match, random)
                    + 3 * doomedDoorways(match, socket, location, center);
                if (score < bestScore) {
                    bestScore = score;
                    bestMatch = match;
                    bestSocket = socket;
                }
            }
        }
        return bestMatch != null && place(level, location, bestMatch, bestSocket, currentTick);
    }

    private static boolean occupiesReserved(PieceMatch match, Set<ChunkPos> reserved) {
        for (ChunkPos c : match.occupiedChunks()) {
            if (reserved.contains(c)) {
                return true;
            }
        }
        return false;
    }

    /** True if the piece's new doorways include one opening onto a free in-bounds chunk closer than currentReach. */
    private static boolean opensCloser(
        PieceMatch match,
        FrontierSocket socket,
        ChunkPos goalChunk,
        int currentReach,
        Set<ChunkPos> built,
        ChunkPos center
    ) {
        int cellX = socket.chunk().x + socket.facing().getStepX() - match.originChunk().x;
        int cellZ = socket.chunk().z + socket.facing().getStepZ() - match.originChunk().z;
        for (FrontierSocket ns : match.openFrontierSockets(cellX, cellZ, socket.facing().getOpposite(), 0, 0)) {
            var faced = growthChunk(ns);
            if (faced.equals(goalChunk)) {
                return true; // a doorway opening AT the target is the point - even (especially) if it's claimed,
                // because that's how a route reaches a hallway chunk for the side-merge to fuse into
            }
            if (cheby(faced, goalChunk) < currentReach && cheby(faced, center) <= activeExtent && !built.contains(faced)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Build one routed piece. {@link #CARVE_ENABLED} picks the path: commission (the construction economy) or the
     * legacy instant stamp. Both share the pre-steps (spawner capture, vermin eviction) and the claims post-step; jelly
     * vats grow here only on the legacy path (the carve path grows them at completion, when the chamber exists).
     */
    /**
     * Attempts one open royal doorway: standard structure-occupancy free test, random fitting royal variant, and the
     * ordinary {@link #place} path - so under {@code CARVE_ENABLED} the hall is COMMISSIONED as a normal drone-staffed
     * carve site (dug, paid, resined) rather than stamped. Returns true if a hall was placed/commissioned this cycle.
     */
    private static boolean placeRoyalHallways(
        ServerLevel level,
        HivePieceRegistry registry,
        HiveLocation location,
        net.minecraft.util.RandomSource random,
        long currentTick
    ) {
        var built = location.structurePieceByChunk().keySet();
        Predicate<ChunkPos> chunkIsFree = c -> !built.contains(c);
        for (FrontierSocket socket : new ArrayList<>(location.frontierSockets())) {
            if (!isRoyalDoor(socket.doorType())) {
                continue;
            }
            var matches = HivePieceMatcher.matchesFromRegistry(socket, registry, chunkIsFree);
            if (matches.isEmpty()) {
                continue; // soft priority: no fit this cycle - ordinary growth proceeds, this door retries next cycle
            }
            var match = matches.get(random.nextInt(matches.size()));
            if (place(level, location, match, socket, currentTick)) {
                return true;
            }
        }
        return false;
    }

    /** A royal door: the grand connections off the queen chamber, buildable only with royal-hallway pieces. */
    private static boolean isRoyalDoor(String doorType) {
        return doorType != null && doorType.contains("royal");
    }

    private static boolean place(ServerLevel level, HiveLocation location, PieceMatch match, FrontierSocket socket, long tick) {
        // Capture terrain mob spawners BEFORE the stamp destroys them - they become pending harvest-chamber stock.
        HarvestSpawnerCapture.captureBeforeStamp(level, location, match.occupiedChunks());
        // And evict any hostile vermin standing in the footprint - construction does not leave cave mobs inside.
        HiveStampEviction.evict(level, location, match.occupiedChunks());
        if (CARVE_ENABLED ? !commission(level, location, match, socket) : !HiveStructurePlacer.place(level, location, match, socket)) {
            return false;
        }
        for (ChunkPos c : match.occupiedChunks()) {
            HiveLocationClaims.claim(level, location, c, tick);
        }
        // Jelly vats grow when the CHAMBER EXISTS: immediately on the legacy instant path, but at carve COMPLETION on
        // the commission path (CarveSiteWork calls growVatsIfJellyChamber then) - a commissioned chamber is still a
        // hole in the ground, and its vat slots don't exist until the structure does.
        if (!CARVE_ENABLED) {
            growVatsIfJellyChamber(level, location, match);
        }
        return true;
    }

    /**
     * Functional furniture, stage 1 of the egg/jelly systems: a completed jelly chamber (vault OR the royal chamber off
     * its special door) grows its vats on its tendril-floor slots. The vats are the physical storage; the jelly FILL
     * stays with the economy. Public because the carve tick (step 3) calls it at completion - the point where the
     * chamber physically exists on that path.
     */
    public static void growVatsIfJellyChamber(ServerLevel level, HiveLocation location, PieceMatch match) {
        if (
            match.piece().id().getPath().contains("chamber_jelly")
                || match.piece().id().getPath().contains("chamber_scourge")
        ) {
            var vats = HiveChamberSlots.vatSlots(level, location, match.originChunk());
            for (BlockPos slot : vats) {
                level.setBlock(slot, AlienBlocks.JELLY_VAT.get().defaultBlockState(), 3);
            }
            Alien.LOGGER.info("Hive: jelly chamber at {} grew {} vats.", match.originChunk(), vats.size());
        }
    }

    /**
     * COMMISSION a routed piece (construction economy step 3, design §2): create the {@code CarveSite} - the record of
     * an in-progress build, with per-column progress over the piece's footprint - consume the frontier socket, and
     * register the site as the hive's active build. NO world work happens here: {@code CarveSiteWork} advances the site
     * on the loaded-location tick (dig clumps, resin patches), and {@code finalizePlacement} runs at TRUE completion -
     * so a half-built piece exposes no doorways and the router cannot build past it (design §8.3/§8.4; enforced
     * belt-and-braces by the {@code hasActiveCarveSite} gate at the top of {@link #route}).
     * <p>
     * Claims run at commission time (the {@link #place} post-step, unchanged in position but now meaning "ground
     * reserved when work STARTS"); the ferry/defense systems treat the site's chunks as hive ground while it digs.
     * Spawner capture and vermin eviction also already ran (the {@link #place} pre-steps) - captured BEFORE the diggers
     * destroy them over the next ~90s. Persistence is the site's own NBT on the owning location; a save mid-build
     * resumes where it stopped instead of wedging the consumed socket.
     */
    private static boolean commission(ServerLevel level, HiveLocation location, PieceMatch match, FrontierSocket socket) {
        var site = new com.alien.common.gameplay.hive.structure.carve.CarveSite(match, socket, location);
        // Resin debt (design §6): claim cost is paid when ground is claimed; the resin is EXTRA, paid progressively
        // as the fill stamps in (CarveSiteWork). Fixed at commission so a price that moves mid-build (the hive keeps
        // claiming) can't reprice work already promised.
        var config = HiveLocationRegistry.INSTANCE.config();
        var resinCost = (int) Math.ceil(
            RESIN_COST_FACTOR * BiomassIncome.claimCost(location, config) * match.occupiedChunks().size()
        );
        site.setResinBiomassOwed(resinCost);
        // Consume the socket NOW: the ground is committed, and a mid-build piece must expose no routable doorway in
        // either direction. finalizePlacement re-removes it at completion (a no-op) and registers the new ones.
        location.frontierSockets().remove(socket);
        location.setActiveCarveSite(site);
        Alien.LOGGER.info("Hive: commissioned {} (resin cost {})", site.describe(), resinCost);
        return true;
    }

    /**
     * The nearest free, in-bounds chunk to {@code goalChunk} within RELOCATE_RADIUS (the goal chunk itself if it is
     * free), or null if every chunk around the goal is already claimed - i.e. the goal is boxed in and gets skipped.
     */
    private static ChunkPos effectiveTarget(HiveLocation location, ChunkPos goalChunk, ChunkPos center, int radius) {
        var built = location.structurePieceByChunk().keySet();
        ChunkPos best = null;
        int bestD = Integer.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                var c = new ChunkPos(goalChunk.x + dx, goalChunk.z + dz);
                if (cheby(c, center) > activeExtent || built.contains(c)) {
                    continue;
                }
                int d = Math.max(Math.abs(dx), Math.abs(dz));
                if (d < bestD) {
                    bestD = d;
                    best = c;
                }
            }
        }
        return best;
    }

    /**
     * Compares the blueprint's per-type goal counts against the rooms actually standing and WARNs about any shortfall.
     * Computed directly from blueprint + world state at the finish, so no routing-flow state can hide it.
     */
    private static void warnMissingRooms(HiveLocation location, HiveBlueprint blueprint, ChunkPos center) {
        var expected = new java.util.LinkedHashMap<String, Integer>();
        for (var goal : blueprint.goals()) {
            expected.merge(goal.roomType(), 1, Integer::sum);
        }
        var missing = new ArrayList<String>();
        for (var entry : expected.entrySet()) {
            int built = countRoomsOfType(location, entry.getKey());
            if (built < entry.getValue()) {
                missing.add(entry.getKey() + " (" + built + "/" + entry.getValue() + ")");
            }
        }
        if (!missing.isEmpty()) {
            Alien.LOGGER.warn(
                "Hive {}: finished WITHOUT these planned rooms: {}.",
                center,
                String.join(", ", missing)
            );
        }
    }

    /** Routing priority class: mandatory / hard-to-place rooms first, filler last. Lower routes earlier. */
    /**
     * A stable target chunk for a synthesized harvest-chamber goal: ring-scan outward from radius 3 in a
     * seed-deterministic order, first spot whose 2x2 footprint holds no structure. The router's normal relocation
     * machinery handles any later conflicts. Null when the map is unexpectedly full.
     */
    private static @Nullable ChunkPos harvestGoalChunk(HiveLocation location, ChunkPos center) {
        var built = location.structurePieceByChunk();
        long seed = center.toLong() ^ 0x4A57BEEFL;
        int startDir = (int) Math.floorMod(seed, 4L);
        for (int radius = 3; radius <= 6; radius++) {
            for (int i = 0; i < 4; i++) {
                int dir = (startDir + i) % 4;
                int dx = switch (dir) {
                    case 0 -> radius;
                    case 1 -> -radius;
                    case 2 -> 0;
                    default -> 0;
                };
                int dz = switch (dir) {
                    case 0 -> 0;
                    case 1 -> 0;
                    case 2 -> radius;
                    default -> -radius;
                };
                var candidate = new ChunkPos(center.x + dx, center.z + dz);
                boolean clear = true;
                for (int cx = 0; cx <= 1 && clear; cx++) {
                    for (int cz = 0; cz <= 1 && clear; cz++) {
                        clear = !built.containsKey(new ChunkPos(candidate.x + cx, candidate.z + cz));
                    }
                }
                if (clear) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static int goalPriority(String roomType) {
        if (roomType.contains("chamber_raid")) {
            return 0; // mandatory, 2x2, needs its scourge companion beside it - gets the emptiest map
        }
        if (roomType.contains("chamber_host")) {
            return 1; // mandatory pair, 2x2
        }
        if (roomType.contains("hub")) {
            return 2; // 2x2 junctions
        }
        if (roomType.contains("chamber_harvest")) {
            return 2; // conditional 2x2, routed with the junctions once spawners are pending
        }
        return 3; // eggs, jelly - 1x1 rooms that fit almost anywhere
    }

    /** How many rooms of this type stand in the hive (chunk count over the type's footprint). */
    /** Public so the purchase-condition system can ask "how many raid chambers does this hive have?". */
    public static int countRoomsOfType(HiveLocation location, String roomType) {
        int chunks = 0;
        for (String id : location.structurePieceByChunk().values()) {
            // The "chamber_jelly" goal means jelly VAULTS; royal-jelly chambers (special-door attachments) share the
            // substring and must not satisfy vault goals, or shortfalls go invisible.
            if (id.contains(roomType) && !(roomType.equals("chamber_jelly") && id.contains("jelly_royal"))) {
                chunks++;
            }
        }
        boolean twoByTwo = roomType.contains("chamber_host") || roomType.contains("chamber_raid")
            || roomType.contains("hub") || roomType.contains("chamber_harvest");
        return twoByTwo ? chunks / 4 : chunks;
    }

    /**
     * If this door faces a FREE chunk whose far side offers a connection - an open door facing back at us, a 1x1
     * hallway the side-merge can open, or an existing opening in that wall - place the bridging straight into the gap.
     * The follow-up merge/aligned passes then fuse the two sides. Returns true if the bridge was placed.
     */
    private static boolean fillOneChunkGap(
        ServerLevel level,
        HivePieceRegistry registry,
        HiveLocation location,
        FrontierSocket socket,
        ChunkPos center
    ) {
        var gap = growthChunk(socket);
        var built = location.structurePieceByChunk().keySet();
        if (built.contains(gap) || cheby(gap, center) > activeExtent) {
            return false;
        }
        var facing = socket.facing();
        var beyond = new ChunkPos(gap.x + facing.getStepX(), gap.z + facing.getStepZ());
        if (
            connectableFrom(level, location, beyond, facing.getOpposite())
                && placeBridgingStraight(level, registry, location, socket, beyond)
        ) {
            return true;
        }
        // Straight-through has nothing - but a corridor may run BESIDE the gap (diagonal-forward of the stub, the
        // most common surviving pattern). A corner placed in the gap turns the connection toward it.
        for (Direction lateral : new Direction[] { facing.getClockWise(), facing.getCounterClockWise() }) {
            var side = new ChunkPos(gap.x + lateral.getStepX(), gap.z + lateral.getStepZ());
            if (
                cheby(side, center) <= activeExtent
                    && connectableFrom(level, location, side, lateral.getOpposite())
                    && placeBridgingStraight(level, registry, location, socket, side)
            ) {
                return true;
            }
        }
        return false;
    }

    /** Places a plain 1x1 straight OR corner off {@code fromSocket} whose far doorway opens onto {@code farChunk}. */
    private static boolean placeBridgingStraight(
        ServerLevel level,
        HivePieceRegistry registry,
        HiveLocation location,
        FrontierSocket fromSocket,
        ChunkPos farChunk
    ) {
        var built = location.structurePieceByChunk().keySet();
        Predicate<ChunkPos> chunkIsFree = c -> !built.contains(c);
        var facing = fromSocket.facing();
        for (PieceMatch match : HivePieceMatcher.matchesFromRegistry(fromSocket, registry, chunkIsFree)) {
            String path = match.piece().id().getPath();
            if (
                (!path.contains("hallway_straight") && !path.contains("hallway_corner"))
                    || match.piece().footprintChunksX() != 1 || match.piece().footprintChunksZ() != 1
            ) {
                continue;
            }
            int cellX = fromSocket.chunk().x + facing.getStepX() - match.originChunk().x;
            int cellZ = fromSocket.chunk().z + facing.getStepZ() - match.originChunk().z;
            boolean opensBeyond = false;
            for (FrontierSocket ns : match.openFrontierSockets(cellX, cellZ, facing.getOpposite(), 0, 0)) {
                if (growthChunk(ns).equals(farChunk)) {
                    opensBeyond = true;
                    break;
                }
            }
            if (opensBeyond && place(level, location, match, fromSocket, level.getGameTime())) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code chunk} offers a connection on its {@code side} wall for a gap-filler to fuse into. */
    private static boolean connectableFrom(ServerLevel level, HiveLocation location, ChunkPos chunk, Direction side) {
        String id = location.structurePieceByChunk().get(chunk);
        if (id == null) {
            return false; // nothing there - a filler would just move the dead end one chunk over
        }
        for (FrontierSocket other : location.frontierSockets()) {
            if (other.chunk().equals(chunk) && other.facing() == side) {
                return true; // an open door already faces back into the gap
            }
        }
        if (id.contains("hallway") && !id.contains("hallway_royal")) {
            return true; // a 1x1 hallway, or a 2x1 (the side-merge splits it into a junction + straight)
        }
        if (id.contains("hub")) {
            return true; // a hub - the merge pass carves a doorway into it on contact
        }
        return openDoorFaces(level, location, chunk).contains(side); // an existing physical opening in that wall
    }

    /** The outward direction if this exit sits on the current rim, else null (e.g. old-rim exits after expansion). */
    private static Direction exitSide(ChunkPos exit, ChunkPos center) {
        int dx = exit.x - center.x;
        int dz = exit.z - center.z;
        if (dx == activeExtent) {
            return Direction.EAST;
        }
        if (dx == -activeExtent) {
            return Direction.WEST;
        }
        if (dz == activeExtent) {
            return Direction.SOUTH;
        }
        if (dz == -activeExtent) {
            return Direction.NORTH;
        }
        return null;
    }

    /**
     * The rim chunk this exit should use: the planned chunk if it's free or holds a carvable 1x1 hallway (an existing
     * outward opening also lands here and is detected by the caller), else the nearest such chunk sliding along the rim
     * (+/-4). Null when rooms block the whole stretch.
     */
    private static ChunkPos resolveExit(HiveLocation location, ChunkPos exit, Direction side, ChunkPos center) {
        boolean northSouth = (side == Direction.NORTH || side == Direction.SOUTH);
        for (int i = 0; i <= 8; i++) {
            int off = (i % 2 == 0) ? i / 2 : -(i / 2 + 1); // 0, -1, +1, -2, +2 ...
            var candidate = northSouth
                ? new ChunkPos(exit.x + off, exit.z)
                : new ChunkPos(exit.x, exit.z + off);
            if (Math.max(Math.abs(candidate.x - center.x), Math.abs(candidate.z - center.z)) != activeExtent) {
                continue; // slid off the rim (corner) - not a valid exit chunk
            }
            String id = location.structurePieceByChunk().get(candidate);
            if (id == null) {
                return candidate; // free - route to it
            }
            if (id.contains("hallway") && !id.contains("hallway_royal") && !id.contains("2x1")) {
                return candidate; // a carvable 1x1 hallway (exits keep excluding 2x1s - the carve has no split logic)
            }
        }
        return null;
    }

    private static ChunkPos growthChunk(FrontierSocket socket) {
        return new ChunkPos(socket.chunk().x + socket.facing().getStepX(), socket.chunk().z + socket.facing().getStepZ());
    }

    private static int distanceToGoal(PieceMatch match, ChunkPos goal) {
        int min = Integer.MAX_VALUE;
        for (ChunkPos c : match.occupiedChunks()) {
            min = Math.min(min, cheby(c, goal));
        }
        return min;
    }

    /**
     * True if every chunk of the match stays within the footprint (Chebyshev radius activeExtent of the core centre).
     */
    private static boolean withinExtent(PieceMatch match, ChunkPos center) {
        for (ChunkPos c : match.occupiedChunks()) {
            if (cheby(c, center) > activeExtent) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesType(PieceMatch match, String roomType) {
        return match.piece().id().getPath().contains(roomType);
    }

    /**
     * A corridor piece the router routes with: any hallway (straight, corner, tee, cross) - so routes can branch and
     * connect - but never a room, a hub, or a royal hallway.
     */
    private static boolean isCorridor(PieceMatch match) {
        String path = match.piece().id().getPath();
        return path.contains("hallway") && !path.contains("hallway_royal");
    }

    /** A branching corridor piece - a tee or a cross. */
    private static boolean isBrancher(PieceMatch match) {
        String path = match.piece().id().getPath();
        return path.contains("hallway_tee") || path.contains("hallway_cross");
    }

    /** True if any chunk of the match sits within 1 chunk (Chebyshev) of an existing tee/cross junction. */
    private static boolean nearJunction(HiveLocation location, PieceMatch match) {
        for (var entry : location.structurePieceByChunk().entrySet()) {
            String id = entry.getValue();
            if (!id.contains("hallway_tee") && !id.contains("hallway_cross")) {
                continue;
            }
            ChunkPos junction = entry.getKey();
            for (ChunkPos c : match.occupiedChunks()) {
                if (cheby(c, junction) <= 1) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A plain 1x1 straight or corner - the only pieces stitch routes may lay (they add no spare doorways). */
    private static boolean isPlainCorridor(PieceMatch match) {
        String path = match.piece().id().getPath();
        return (path.contains("hallway_straight") || path.contains("hallway_corner"))
            && match.piece().footprintChunksX() == 1 && match.piece().footprintChunksZ() == 1;
    }

    /**
     * How many of the piece's new doorways would open straight into a chamber or royal-hallway wall - doors that can
     * never connect (rooms stay intact) and would need sealing later. Routing pays 3 per doomed door, so it lays its
     * branch pieces where the spare doors have a future instead of against room walls.
     */
    private static int doomedDoorways(PieceMatch match, FrontierSocket socket, HiveLocation location, ChunkPos center) {
        int cellX = socket.chunk().x + socket.facing().getStepX() - match.originChunk().x;
        int cellZ = socket.chunk().z + socket.facing().getStepZ() - match.originChunk().z;
        int doomed = 0;
        for (FrontierSocket ns : match.openFrontierSockets(cellX, cellZ, socket.facing().getOpposite(), 0, 0)) {
            var faced = growthChunk(ns);
            String id = location.structurePieceByChunk().get(faced);
            if (id != null && (id.contains("chamber") || id.contains("hallway_royal"))) {
                doomed++;
            }
        }
        return doomed;
    }

    /**
     * Selection bias: corners cost a flat 3; straights/branchers roll 0-2; multi-chunk pieces (2x1) pay a handicap so
     * they can't monopolise routes just by covering more ground per placement - 1x1s dominate with occasional 2x1s
     * mixed in.
     */
    private static int pieceBias(PieceMatch match, net.minecraft.util.RandomSource random) {
        int bias = match.piece().id().getPath().contains("corner") ? 3 : random.nextInt(3);
        if (match.piece().footprintChunksX() * match.piece().footprintChunksZ() > 1) {
            bias += 5;
        }
        return bias;
    }

    /**
     * Called once the blueprint is fully built: caps every leftover interior doorway (a room's unused exit, or a
     * corridor head that stopped) so there are no open dead-end holes. Doorways facing the footprint rim stay open as
     * entrances. Connectivity is unaffected - these doors were unused.
     */
    private static int finalizeHive(
        ServerLevel level,
        HivePieceRegistry registry,
        HiveLocation location,
        ChunkPos center,
        long currentTick
    ) {
        var frontier = location.frontierSockets();

        // Gap-fill sweep: a dead-end door one FREE chunk away from something connectable - an open door facing back,
        // a 1x1 hallway (the side-merge can open it), or an existing opening in that wall - gets its bridging straight
        // placed instead of being sealed. Then the merge/join passes fuse everything the sweep just lined up.
        int filled = 0;
        int gapCandidates = 0;
        for (FrontierSocket socket : new ArrayList<>(frontier)) {
            if (cheby(growthChunk(socket), center) > activeExtent) {
                continue;
            }
            gapCandidates++;
            if (fillOneChunkGap(level, registry, location, socket, center)) {
                filled++;
            }
        }
        Alien.LOGGER.info(
            "Hive {}: gap-fill pass checked {} leftover doorways, bridged {}.",
            center,
            gapCandidates,
            filled
        );
        mergeIntoHallways(level, registry, location, center);
        connectAlignedSockets(location, center);

        // Redirect sweep: a 1x1 hallway whose door dead-ends but whose OTHER wall touches something connectable gets
        // re-stamped with that door rotated to face it (the classic case: an arm-end corner pointing at open ground
        // while a corridor runs right past its flank). The neighbour's side is opened to meet it.
        var redirectSkips = new java.util.LinkedHashMap<String, Integer>();
        int redirected = redirectDeadDoors(level, registry, location, center, redirectSkips);
        Alien.LOGGER.info(
            "Hive {}: redirect pass re-stamped {} pieces toward touching neighbours (skips: {}).",
            center,
            redirected,
            redirectSkips.isEmpty() ? "none" : redirectSkips.toString()
        );
        mergeIntoHallways(level, registry, location, center);
        connectAlignedSockets(location, center);

        // Cap the remaining interior dead-end doors. Rim doors stay open. A special door gets one last attach attempt
        // and a loud warning if its mandatory room truly cannot fit - that should never happen with its spot reserved.
        int sealed = 0;
        var sealReasons = new java.util.HashMap<String, Integer>();
        for (FrontierSocket socket : new ArrayList<>(frontier)) {
            if (cheby(growthChunk(socket), center) > activeExtent) {
                continue;
            }
            if (isSpecialDoor(socket.doorType())) {
                if (attachAt(level, registry, location, socket, center, currentTick)) {
                    continue; // room attached; socket consumed by the placer
                }
                Alien.LOGGER.warn(
                    "Hive {}: mandatory special room ({}) could NOT attach at {} - sealing its doorway.",
                    center,
                    socket.doorType(),
                    socket.chunk()
                );
            }
            // Never wall over a working passage: if the chunk this door faces is open toward us, the two sides
            // already form a doorway (e.g. after a redirect) - drop the socket, keep the passage.
            var facedChunk = growthChunk(socket);
            if (
                location.structurePieceByChunk().containsKey(facedChunk)
                    && openDoorFaces(level, location, facedChunk).contains(socket.facing().getOpposite())
            ) {
                frontier.remove(socket);
                continue;
            }
            capDoorway(level, location, socket);
            frontier.remove(socket);
            sealed++;
            sealReasons.merge(sealCategory(location, socket), 1, Integer::sum);
        }
        if (sealed > 0) {
            var parts = new ArrayList<String>();
            for (var entry : sealReasons.entrySet()) {
                parts.add(entry.getValue() + " " + entry.getKey());
            }
            Alien.LOGGER.info("Hive {}: sealed-door breakdown: {}.", center, String.join(", ", parts));
        }
        return sealed;
    }

    /** What a sealed door was facing - the diagnostic for why it couldn't connect. */
    private static String sealCategory(HiveLocation location, FrontierSocket socket) {
        var faced = growthChunk(socket);
        String id = location.structurePieceByChunk().get(faced);
        if (id == null) {
            return location.claimedChunks().contains(faced)
                ? "facing claimed-empty ground"
                : "facing open ground (no reachable connection ahead)";
        }
        if (id.contains("hallway_royal")) {
            return "facing a royal hallway";
        }
        if (id.contains("2x1")) {
            return "facing a 2x1 hallway";
        }
        if (id.contains("hallway")) {
            return "facing a 1x1 hallway (merge failed)";
        }
        if (id.contains("hub")) {
            return "facing a hub";
        }
        return "facing a chamber";
    }

    /** Scourge / jelly doors - doorways whose companion chamber is mandatory and attaches directly, un-routed. */
    private static boolean isSpecialDoor(String doorType) {
        return doorType != null && (doorType.contains("scourge") || doorType.contains("jelly"));
    }

    /** Attaches the matching chamber to every currently attachable special door (one placement per call). */
    private static boolean attachSpecialRooms(
        ServerLevel level,
        HivePieceRegistry registry,
        HiveLocation location,
        ChunkPos center,
        long currentTick
    ) {
        for (FrontierSocket socket : new ArrayList<>(location.frontierSockets())) {
            if (isSpecialDoor(socket.doorType()) && attachAt(level, registry, location, socket, center, currentTick)) {
                return true;
            }
        }
        return false;
    }

    /** Places whatever piece matches this socket's door type (special doors match only their chamber). */
    private static boolean attachAt(
        ServerLevel level,
        HivePieceRegistry registry,
        HiveLocation location,
        FrontierSocket socket,
        ChunkPos center,
        long currentTick
    ) {
        // Build checks test STRUCTURE occupancy, never territory claims: the biomass economy claims ground
        // without building, and claimed-but-empty ground is exactly where the hive is allowed to build.
        var built = location.structurePieceByChunk().keySet();
        Predicate<ChunkPos> chunkIsFree = c -> !built.contains(c);
        for (PieceMatch match : HivePieceMatcher.matchesFromRegistry(socket, registry, chunkIsFree)) {
            if (withinExtent(match, center) && place(level, location, match, socket, currentTick)) {
                return true;
            }
        }
        return false;
    }

    /** Joins every pair of open doors that meet head-on (aligned across a shared wall) into a passage - a loop. */
    private static boolean connectAlignedSockets(HiveLocation location, ChunkPos center) {
        var frontier = location.frontierSockets();
        var handled = new HashSet<FrontierSocket>();
        boolean joined = false;
        for (FrontierSocket socket : new ArrayList<>(frontier)) {
            if (handled.contains(socket) || cheby(growthChunk(socket), center) > activeExtent) {
                continue; // rim-facing doors stay open as entrances
            }
            var aligned = findAlignedSocket(socket, frontier, handled);
            if (aligned != null) {
                handled.add(socket);
                handled.add(aligned);
                frontier.remove(socket);
                frontier.remove(aligned); // both consumed - the coinciding openings are now a connecting passage
                joined = true;
            }
        }
        return joined;
    }

    /**
     * Side-collision merge: a corridor head whose faced chunk already holds a 1x1 hallway doesn't dead-end - the
     * hallway is upgraded in place (straight/corner -> tee, tee -> cross) so the two join. The existing hallway's doors
     * are read from the world (the 8x8 openings sit at fixed wall positions), the incoming face is added, and the
     * matching piece/rotation is re-stamped over the chunk.
     */
    private static void mergeIntoHallways(ServerLevel level, HivePieceRegistry registry, HiveLocation location, ChunkPos center) {
        var frontier = location.frontierSockets();
        for (FrontierSocket socket : new ArrayList<>(frontier)) {
            var faced = growthChunk(socket);
            if (cheby(faced, center) > activeExtent) {
                continue;
            }
            String pieceId = location.structurePieceByChunk().get(faced);
            if (pieceId == null) {
                continue;
            }
            if (pieceId.contains("hub")) {
                // Hubs are the fast-travel junctions - extra doors into them are a feature, not damage. Carve the
                // 8x8 opening in the hub's wall and the two openings form a doorway.
                carveDoorway(level, location, faced, socket.facing().getOpposite());
                frontier.remove(socket);
                continue;
            }
            if (!pieceId.contains("hallway") || pieceId.contains("hallway_royal")) {
                continue;
            }
            var existing = openDoorFaces(level, location, faced);
            if (existing.isEmpty()) {
                continue; // couldn't read the hallway's doors - leave it alone
            }
            var needed = socket.facing().getOpposite();
            if (existing.contains(needed)) {
                frontier.remove(socket); // the openings already coincide - connected as-is
                continue;
            }
            if (pieceId.contains("2x1")) {
                // A 2x1 hallway splits on side impact: the struck half becomes the tee/cross connector, the other
                // half is re-stamped as a plain 1x1 straight - two proper 1x1 pieces instead of an orphaned stub.
                if (splitTwoByOne(level, registry, location, faced, existing, needed)) {
                    frontier.remove(socket);
                }
                continue;
            }
            if (existing.size() >= 4) {
                continue; // already a cross; nothing to upgrade
            }
            var desired = EnumSet.copyOf(existing);
            desired.add(needed);
            if (restampHallway(level, registry, location, faced, desired)) {
                frontier.remove(socket); // the upgraded hallway now opens onto this doorway - joined
            }
        }
    }

    /**
     * Redirect pass: for each remaining dead door on a plain 1x1 hallway, look at the piece's OTHER walls - if one
     * touches a connectable neighbour, re-stamp the piece with the dead door rotated onto that wall and open the
     * neighbour's side to meet it. Preference order: a neighbour already open toward us, then a 1x1 hallway we can
     * upgrade, then a 2x1 we can split. Rooms/hubs without an existing opening are never punched into.
     */
    private static int redirectDeadDoors(
        ServerLevel level,
        HivePieceRegistry registry,
        HiveLocation location,
        ChunkPos center,
        java.util.Map<String, Integer> skips
    ) {
        int redirected = 0;
        for (FrontierSocket socket : new ArrayList<>(location.frontierSockets())) {
            if (cheby(growthChunk(socket), center) > activeExtent) {
                continue; // rim entrances aren't dead doors; not counted as a skip
            }
            var pieceChunk = socket.chunk();
            String pid = location.structurePieceByChunk().get(pieceChunk);
            if (pid == null) {
                skips.merge("no piece at socket chunk", 1, Integer::sum);
                continue;
            }
            if (!pid.contains("hallway") || pid.contains("hallway_royal") || pid.contains("2x1")) {
                skips.merge(pid.contains("2x1") ? "piece is a 2x1" : "piece not a plain hallway", 1, Integer::sum);
                continue; // only plain 1x1 hallway pieces re-stamp themselves
            }
            var faces = openDoorFaces(level, location, pieceChunk);
            var dead = socket.facing();
            if (!faces.contains(dead)) {
                skips.merge("doors unreadable (dead face not open in world)", 1, Integer::sum);
                continue; // can't read this piece's doors reliably - leave it alone
            }

            Direction best = null;
            int bestMode = Integer.MAX_VALUE; // 0 = already open toward us, 1 = upgradeable 1x1, 2 = splittable 2x1
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (d == dead || faces.contains(d)) {
                    continue;
                }
                var neighbour = new ChunkPos(pieceChunk.x + d.getStepX(), pieceChunk.z + d.getStepZ());
                if (cheby(neighbour, center) > activeExtent) {
                    continue;
                }
                String nid = location.structurePieceByChunk().get(neighbour);
                if (nid != null && nid.contains("hallway_royal")) {
                    continue;
                }
                int mode;
                if (nid == null) {
                    // Flank gap: the wall touches open ground, but one chunk beyond it sits something connectable -
                    // the classic "corridor runs one chunk off the stub's flank". Turn the door AND bridge the gap.
                    var beyond = new ChunkPos(neighbour.x + d.getStepX(), neighbour.z + d.getStepZ());
                    if (
                        cheby(beyond, center) <= activeExtent
                            && connectableFrom(level, location, beyond, d.getOpposite())
                    ) {
                        mode = 3;
                    } else {
                        continue;
                    }
                } else if (openDoorFaces(level, location, neighbour).contains(d.getOpposite())) {
                    mode = 0;
                } else if (nid.contains("hallway") && !nid.contains("2x1")) {
                    mode = 1;
                } else if (nid.contains("2x1")) {
                    mode = 2;
                } else {
                    continue; // a room or hub without an opening - don't punch doors into it
                }
                if (mode < bestMode) {
                    bestMode = mode;
                    best = d;
                }
            }
            if (best == null) {
                skips.merge("no connectable wall", 1, Integer::sum);
                continue;
            }

            var neighbour = new ChunkPos(pieceChunk.x + best.getStepX(), pieceChunk.z + best.getStepZ());
            if (bestMode == 3) {
                // Flank gap: place the bridging straight FIRST (from a synthetic socket on our wall - it doesn't need
                // our door open to stamp). Only if the bridge stands do we open our wall toward it below; if it can't
                // be placed, bail and let this door seal normally rather than open a hole to nowhere.
                var beyond = new ChunkPos(neighbour.x + best.getStepX(), neighbour.z + best.getStepZ());
                var synth = new FrontierSocket(pieceChunk, best, socket.doorType(), 0, 0);
                if (!placeBridgingStraight(level, registry, location, synth, beyond)) {
                    skips.merge("flank bridge failed to place", 1, Integer::sum);
                    continue;
                }
            }
            var desired = EnumSet.copyOf(faces);
            desired.remove(dead);
            desired.add(best);
            if (!restampHallway(level, registry, location, pieceChunk, desired)) {
                skips.merge("self re-stamp failed", 1, Integer::sum);
                continue; // rare; in mode 3 the bridge stands with its near door at our wall - cosmetic
            }
            if (bestMode == 1) {
                var nFaces = openDoorFaces(level, location, neighbour);
                if (!nFaces.isEmpty() && nFaces.size() < 4) {
                    var nDesired = EnumSet.copyOf(nFaces);
                    nDesired.add(best.getOpposite());
                    restampHallway(level, registry, location, neighbour, nDesired);
                }
            } else if (bestMode == 2) {
                splitTwoByOne(
                    level,
                    registry,
                    location,
                    neighbour,
                    openDoorFaces(level, location, neighbour),
                    best.getOpposite()
                );
            }
            location.frontierSockets().remove(socket);
            redirected++;
        }
        return redirected;
    }

    /**
     * Splits a 2x1 straight that a corridor head struck side-on: the struck half is re-stamped as the tee/cross the
     * junction needs, and its partner half becomes a plain 1x1 straight along the old axis. The seam between the two
     * halves is told apart from an end door by sampling outside the 8x8 door span - a seam is fully open there.
     */
    private static boolean splitTwoByOne(
        ServerLevel level,
        HivePieceRegistry registry,
        HiveLocation location,
        ChunkPos struck,
        EnumSet<Direction> existing,
        Direction needed
    ) {
        // Find the seam face: an open face whose neighbour is also 2x1 and whose wall plane is open OUTSIDE the door
        // span.
        Direction seam = null;
        ChunkPos partner = null;
        for (Direction face : existing) {
            var neighbour = new ChunkPos(struck.x + face.getStepX(), struck.z + face.getStepZ());
            String nid = location.structurePieceByChunk().get(neighbour);
            if (nid == null || !nid.contains("2x1")) {
                continue;
            }
            int y = location.hiveFloorY() + 2;
            BlockPos probe = (face == Direction.NORTH || face == Direction.SOUTH)
                ? new BlockPos(
                    struck.getMinBlockX() + 2,
                    y,
                    face == Direction.NORTH ? struck.getMinBlockZ() : struck.getMaxBlockZ()
                )
                : new BlockPos(
                    face == Direction.WEST ? struck.getMinBlockX() : struck.getMaxBlockX(),
                    y,
                    struck.getMinBlockZ() + 2
                );
            if (level.getBlockState(probe).isAir()) {
                seam = face;
                partner = neighbour;
                break;
            }
        }
        if (seam == null) {
            return false; // couldn't identify the pair - leave it alone rather than guess
        }
        var desired = EnumSet.copyOf(existing);
        desired.add(needed);
        if (!restampHallway(level, registry, location, struck, desired)) {
            return false;
        }
        // The partner becomes a plain straight along the old axis: seam side (toward the new junction) + its far end.
        var partnerDesired = EnumSet.of(seam, seam.getOpposite());
        if (!restampHallway(level, registry, location, partner, partnerDesired)) {
            Alien.LOGGER.warn("Hive: split a 2x1 at {} but could not re-stamp its partner half at {}.", struck, partner);
        }
        return true;
    }

    /** The door faces a placed 1x1 hallway actually has, read from the world (air at the 8x8 opening's centre). */
    private static EnumSet<Direction> openDoorFaces(ServerLevel level, HiveLocation location, ChunkPos chunk) {
        var faces = EnumSet.noneOf(Direction.class);
        int y = location.hiveFloorY() + 2;
        int cx = chunk.getMinBlockX() + 8;
        int cz = chunk.getMinBlockZ() + 8;
        if (level.getBlockState(new BlockPos(cx, y, chunk.getMinBlockZ())).isAir()) {
            faces.add(Direction.NORTH);
        }
        if (level.getBlockState(new BlockPos(cx, y, chunk.getMaxBlockZ())).isAir()) {
            faces.add(Direction.SOUTH);
        }
        if (level.getBlockState(new BlockPos(chunk.getMinBlockX(), y, cz)).isAir()) {
            faces.add(Direction.WEST);
        }
        if (level.getBlockState(new BlockPos(chunk.getMaxBlockX(), y, cz)).isAir()) {
            faces.add(Direction.EAST);
        }
        return faces;
    }

    /** Re-stamps {@code chunk} with the 1x1 hallway whose doors (under some rotation) equal {@code desired}. */
    private static boolean restampHallway(
        ServerLevel level,
        HivePieceRegistry registry,
        HiveLocation location,
        ChunkPos chunk,
        EnumSet<Direction> desired
    ) {
        String want;
        if (desired.size() == 4) {
            want = "hallway_cross";
        } else if (desired.size() == 3) {
            want = "hallway_tee";
        } else {
            var it = desired.iterator();
            want = (it.next().getOpposite() == it.next()) ? "hallway_straight" : "hallway_corner";
        }
        for (HivePiece piece : registry.piecesWithDoorType("avp_alien:hive_door")) {
            if (!piece.id().getPath().contains(want) || piece.footprintChunksX() != 1 || piece.footprintChunksZ() != 1) {
                continue;
            }
            for (Rotation rotation : Rotation.values()) {
                var faces = EnumSet.noneOf(Direction.class);
                for (DoorwaySocket ds : piece.socketsRotated(rotation)) {
                    faces.add(ds.facing());
                }
                if (!faces.equals(desired)) {
                    continue;
                }
                var templateOpt = level.getServer().getStructureManager().get(piece.id());
                if (templateOpt.isEmpty()) {
                    return false;
                }
                var template = templateOpt.get();
                var size = template.getSize();
                var correction = switch (rotation) {
                    case CLOCKWISE_90 -> new BlockPos(size.getZ() - 1, 0, 0);
                    case CLOCKWISE_180 -> new BlockPos(size.getX() - 1, 0, size.getZ() - 1);
                    case COUNTERCLOCKWISE_90 -> new BlockPos(0, 0, size.getX() - 1);
                    default -> BlockPos.ZERO;
                };
                var placeAt = new BlockPos(
                    chunk.getMinBlockX() + correction.getX(),
                    location.hiveFloorY(),
                    chunk.getMinBlockZ() + correction.getZ()
                );
                var settings = new StructurePlaceSettings()
                    .setRotation(rotation)
                    .setIgnoreEntities(true)
                    .addProcessor(JigsawReplacementProcessor.INSTANCE);
                if (!template.placeInWorld(level, placeAt, placeAt, settings, net.minecraft.util.RandomSource.create(), 2)) {
                    return false;
                }
                location.assignStructure(chunk, HiveStructureRole.HALLWAY, piece.id().toString());
                return true;
            }
        }
        return false;
    }

    /** An open socket in the chunk this one faces that opens straight back with the same door type (a head-on meet). */
    private static FrontierSocket findAlignedSocket(FrontierSocket socket, Set<FrontierSocket> frontier, Set<FrontierSocket> handled) {
        var faced = growthChunk(socket);
        var back = socket.facing().getOpposite();
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
     * Carves the fixed 8x8 doorway opening (centred on the chunk wall, from the floor up) into {@code side} of the
     * chunk.
     */
    private static void carveDoorway(ServerLevel level, HiveLocation location, ChunkPos chunk, Direction side) {
        int floorY = location.hiveFloorY();
        int start = (16 - DOOR_SIZE) / 2;
        var pos = new BlockPos.MutableBlockPos();
        var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        if (side == Direction.NORTH || side == Direction.SOUTH) {
            int z = (side == Direction.NORTH) ? chunk.getMinBlockZ() : chunk.getMaxBlockZ();
            int x0 = chunk.getMinBlockX() + start;
            for (int x = x0; x < x0 + DOOR_SIZE; x++) {
                for (int y = floorY; y < floorY + DOOR_SIZE; y++) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, air, 3);
                    }
                }
            }
        } else {
            int x = (side == Direction.WEST) ? chunk.getMinBlockX() : chunk.getMaxBlockX();
            int z0 = chunk.getMinBlockZ() + start;
            for (int z = z0; z < z0 + DOOR_SIZE; z++) {
                for (int y = floorY; y < floorY + DOOR_SIZE; y++) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, air, 3);
                    }
                }
            }
        }
    }

    /** Walls off a doorway: fills the fixed 8x8 opening (centred on the chunk wall, from the floor up) with resin. */
    private static void capDoorway(ServerLevel level, HiveLocation location, FrontierSocket socket) {
        var resin = AlienResinBlocks.RIBBED_RESIN.get().defaultBlockState();
        var chunk = socket.chunk();
        var facing = socket.facing();
        int floorY = location.hiveFloorY();
        int start = (16 - DOOR_SIZE) / 2;
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

    private static int cheby(ChunkPos a, ChunkPos b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z));
    }
}
