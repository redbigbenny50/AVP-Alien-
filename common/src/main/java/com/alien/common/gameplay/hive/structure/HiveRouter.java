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
     * Resin cost multiplier (design §6, step 4): a piece's resin debt = this factor x the hive's CURRENT per-chunk
     * claim cost x the piece's footprint chunks, fixed at commission. Riding on claimCost means the price scales
     * super-linearly with hive size for free; 1.5x makes a healthy hive never visibly stall while a drained one freezes
     * mid-corridor. Tune here.
     */
    public static final double RESIN_COST_FACTOR = 1.5;

    public static final int BASE_EXTENT = 9; // base footprint radius (19x19)

    public static final int EMPRESS_EXTENT = 11; // empress-influenced footprint radius (23x23)

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
    private static final Set<HiveLocation> EMPRESS_INFLUENCED =
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
                location.structurePieceByChunk()
                    .put(
                        roleEntry.getKey(),
                        HivePieceCatalog.queenChamber(location.lineageVariantOrNull()).toString()
                    );
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
            // [stated] raid rule 1: "max two raid chambers per hive - 1 normally and 2 only under empress." A
            // hard cap independent of how many goals the blueprint carries, so count drift or legacy blueprints
            // can never overshoot the doctrine.
            if (
                goal.roomType().contains("chamber_raid")
                    && countRoomsOfType(location, "chamber_raid") >= raidChamberCap(location)
            ) {
                continue;
            }
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
                .comparingInt((HiveBlueprint.Goal g) -> goalPriority(location, g.roomType()))
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
            // ⭐⭐ RIM-REFUSED ROOMS RETRY FURTHER IN. A raid chamber may only sit one ring inside the border, so on
            // a hive whose free ground is all out at the edge every socket near the goal gets refused and the room
            // simply never appears - worse than placing it badly, because a raid chamber with no scourge companion
            // at least existed. Pulling the target one ring toward the centre and re-trying keeps the rule while
            // still getting the room built.
            // ⚠ ONLY for types that carry a rim margin, and only while the target is still outside the core -
            // walking any room inward on failure would drag the whole hive toward its own centre.
            if (rimMarginFor(goal.roomType()) > 0) {
                var pulledIn = eff;

                for (int step = 0; step < RIM_RETRY_STEPS; step++) {
                    pulledIn = stepTowardCenter(pulledIn, center);

                    if (pulledIn == null) {
                        break; // reached the core - nowhere further in to try
                    }

                    if (
                        tryDropRoom(
                            level,
                            registry,
                            location,
                            goal.roomType(),
                            pulledIn,
                            center,
                            othersReserved,
                            currentTick
                        )
                    ) {
                        return true;
                    }
                }
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
            for (PieceMatch match : HivePieceMatcher.matchesFromRegistry(socket, registry, chunkIsFree, location.lineageVariantOrNull())) {
                if (
                    matchesType(match, roomType) && withinExtent(match, center, rimMarginFor(roomType))
                        && !occupiesReserved(match, forbidden)
                        && !(roomType.contains("chamber_raid") && tooCloseToRaidChamber(location, match))
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
            for (PieceMatch match : HivePieceMatcher.matchesFromRegistry(socket, registry, chunkIsFree, location.lineageVariantOrNull())) {
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
     * Attempts one open royal doorway: standard structure-occupancy free test, random fitting royal variant, and the
     * ordinary {@link #place} path - the hall is COMMISSIONED as a normal drone-staffed carve site (dug, paid,
     * resined). Returns true if a hall was commissioned this cycle.
     */
    private static boolean placeRoyalHallways(
        ServerLevel level,
        HivePieceRegistry registry,
        HiveLocation location,
        net.minecraft.util.RandomSource random,
        long currentTick
    ) {
        // [stated] "theres only a max of 4 royal hallways allowed" - the hard ceiling, whatever the sockets say.
        // The core authors 4 royal DOORWAYS as 8 jigsaw blocks (two per doorway, straddling the chunk seam), so
        // socket bookkeeping alone can invite up to 8 halls; the cap is the rule the doors serve.
        if (countRoomsOfType(location, "hallway_royal") >= MAX_ROYAL_HALLWAYS) {
            return false;
        }
        var built = location.structurePieceByChunk().keySet();
        Predicate<ChunkPos> chunkIsFree = c -> !built.contains(c);
        for (FrontierSocket socket : new ArrayList<>(location.frontierSockets())) {
            if (!isRoyalDoor(socket.doorType())) {
                continue;
            }
            // Royal halls attach ONLY to the queen chamber's own doors. A royal socket hosted by any other piece
            // is a leftover from the pre-fix chain bug (worlds saved mid-runaway persist them) - HEAL it into an
            // ordinary hive door in place, so the runaway hall's far end finally grows the tunnels and rooms it
            // was supposed to, instead of the next royal hall.
            var hostPieceId = location.structurePieceByChunk().get(socket.chunk());
            if (hostPieceId == null || !hostPieceId.contains("core")) {
                location.frontierSockets().remove(socket);
                location.frontierSockets()
                    .add(
                        new FrontierSocket(
                            socket.chunk(),
                            socket.facing(),
                            socket.doorType().replace("_royal_door", "_door"),
                            socket.cornerRun(),
                            socket.straightRun()
                        )
                    );
                continue;
            }
            var matches = HivePieceMatcher.matchesFromRegistry(socket, registry, chunkIsFree, location.lineageVariantOrNull());
            // [stated] rule 2: "royal hallways cannot be built next to eachother" - drop every candidate whose
            // chunks touch an existing royal hall's chunks (chebyshev <= 1, diagonals included). Holds whatever
            // the socket bookkeeping does: two halls can never stand side by side.
            var standaloneMatches = new ArrayList<PieceMatch>(matches.size());
            for (PieceMatch candidate : matches) {
                if (!touchesRoyalHall(location, candidate)) {
                    standaloneMatches.add(candidate);
                }
            }
            if (standaloneMatches.isEmpty()) {
                continue; // soft priority: no fit this cycle - ordinary growth proceeds, this door retries next cycle
            }
            var match = standaloneMatches.get(random.nextInt(standaloneMatches.size()));
            if (place(level, location, match, socket, currentTick)) {
                return true;
            }
        }
        return false;
    }

    /** A royal door: the grand connections off the queen chamber, buildable only with royal-hallway pieces. */
    /** [stated] raid rule 1: one raid chamber normally, a second only under empress influence. */
    private static int raidChamberCap(HiveLocation location) {
        return location.isEmpressInfluenced() ? 2 : 1;
    }

    /**
     * [stated] raid rule 2: "raid chambers can not be within 2 chunks of eachother." True when any chunk of this
     * candidate sits within chebyshev 2 of an existing raid chamber's chunks.
     */
    private static boolean tooCloseToRaidChamber(HiveLocation location, PieceMatch match) {
        for (var entry : location.structurePieceByChunk().entrySet()) {
            if (!entry.getValue().contains("chamber_raid")) {
                continue;
            }
            for (ChunkPos candidate : match.occupiedChunks()) {
                if (candidate.getChessboardDistance(entry.getKey()) <= 2) {
                    return true;
                }
            }
        }
        return nearActiveCarveSite(location, match, "chamber_raid", 2);
    }

    /** True when any chunk of this candidate touches (chebyshev <= 1) a chunk of an already-placed royal hall. */
    private static boolean touchesRoyalHall(HiveLocation location, PieceMatch match) {
        for (var entry : location.structurePieceByChunk().entrySet()) {
            if (!entry.getValue().contains("hallway_royal")) {
                continue;
            }
            for (ChunkPos candidate : match.occupiedChunks()) {
                if (candidate.getChessboardDistance(entry.getKey()) <= 1) {
                    return true;
                }
            }
        }
        return nearActiveCarveSite(location, match, "hallway_royal", 1);
    }

    /**
     * The ONE chamber a special doorway may grow, by door type. Returns null for ordinary doors (no restriction).
     * <p>
     * Door types are shared by more than their companion chamber - a royal hallway carries a jelly door so its jelly
     * chamber can hang off it, a raid chamber carries a scourge door for its scourge room - so matching on the door
     * type alone lets a special socket regrow the very piece that authored it.
     */
    private static @Nullable String companionRoomFor(@Nullable String doorType) {
        if (doorType == null) {
            return null;
        }
        if (doorType.contains("scourge")) {
            return "chamber_scourge";
        }
        if (doorType.contains("jelly")) {
            return "chamber_jelly_royal";
        }
        return null;
    }

    /**
     * THE choke point for piece-class rules. Every placement path in this class - the goal room drop, the corridor
     * advance, the royal hallway routine, the gap-filling bridge and the special-door attach - funnels through
     * {@link #place}, so a rule enforced here cannot be walked around by a path that never heard of it.
     * <p>
     * That is exactly how the caps were beaten before: the royal ceiling lived inside placeRoyalHallways and the raid
     * cap inside the pending-goal filter, while attachAt called place() directly with neither in scope.
     */
    private static boolean allowedByPieceClassRules(
        HiveLocation location,
        PieceMatch match,
        @Nullable FrontierSocket socket
    ) {
        var path = match.piece().id().getPath();
        var doorType = socket == null ? null : socket.doorType();
        if (path.contains("hallway_royal")) {
            // [stated] royal rule 1: a royal hallway is a grand connection off the QUEEN'S chamber. Anywhere else
            // it is a piece that merely fits a doorway.
            if (!isRoyalDoor(doorType)) {
                return refusePlacement(match, "royal hallways grow only from a royal doorway");
            }
            var hostPieceId = socket == null ? null : location.structurePieceByChunk().get(socket.chunk());
            if (hostPieceId == null || !hostPieceId.contains("core")) {
                return refusePlacement(match, "that royal doorway is not on the queen's chamber");
            }
            // [stated] royal rule 3: "not two, not 5, 1 only" per direction - four doorways, four halls, full stop.
            if (committedRoomsOfType(location, "hallway_royal") >= MAX_ROYAL_HALLWAYS) {
                return refusePlacement(match, "hive already has its " + MAX_ROYAL_HALLWAYS + " royal hallways");
            }
            // [stated] royal rule 2: "royal hallways cannot be built next to eachother".
            if (touchesRoyalHall(location, match)) {
                return refusePlacement(match, "would stand beside an existing royal hallway");
            }
        } else if (path.contains("chamber_raid")) {
            // [stated] raid rule 1: one raid chamber, two only under empress influence - and the cap denies the
            // piece no matter which routine asked for it.
            int cap = raidChamberCap(location);
            if (committedRoomsOfType(location, "chamber_raid") >= cap) {
                return refusePlacement(match, "hive already has its " + cap + " raid chamber(s)");
            }
            // [stated] raid rule 2: raid chambers stay more than 2 chunks apart.
            if (tooCloseToRaidChamber(location, match)) {
                return refusePlacement(match, "would stand within 2 chunks of an existing raid chamber");
            }
        }
        return true;
    }

    /** Refusal with its reason, at debug level: legitimate routing retries constantly and must not spam the log. */
    private static boolean refusePlacement(PieceMatch match, String reason) {
        Alien.LOGGER.debug("Hive: refused {} at {} - {}.", match.piece().id(), match.originChunk(), reason);
        return false;
    }

    /**
     * Rooms of this type the hive has COMMITTED to: placed pieces plus the one currently being carved. A commissioned
     * site is not in builtPlacements until its carve completes, so counting only finished rooms would let a cap be
     * judged against a hive that is missing its newest one. Routing is gated on hasActiveCarveSite today, which closes
     * that window by accident - the cap should not depend on it.
     */
    private static int committedRoomsOfType(HiveLocation location, String roomType) {
        int rooms = countRoomsOfType(location, roomType);
        var site = location.activeCarveSite();
        if (site != null && site.match().piece().id().getPath().contains(roomType)) {
            rooms++;
        }
        return rooms;
    }

    /** The adjacency tests above, applied to the piece currently under the claws rather than one already standing. */
    private static boolean nearActiveCarveSite(HiveLocation location, PieceMatch match, String roomType, int range) {
        var site = location.activeCarveSite();
        if (site == null || !site.match().piece().id().getPath().contains(roomType)) {
            return false;
        }
        for (ChunkPos inFlight : site.match().occupiedChunks()) {
            for (ChunkPos candidate : match.occupiedChunks()) {
                if (candidate.getChessboardDistance(inFlight) <= range) {
                    return true;
                }
            }
        }
        return false;
    }

    /** [stated] hard limit on royal hallways per hive. */
    private static final int MAX_ROYAL_HALLWAYS = 4;

    private static boolean isRoyalDoor(String doorType) {
        return doorType != null && doorType.contains("royal");
    }

    private static boolean place(ServerLevel level, HiveLocation location, PieceMatch match, FrontierSocket socket, long tick) {
        // Piece-class rules (royal hallway ceiling, raid chamber cap, both spacing rules) are enforced HERE and
        // nowhere else, because this is the one function every placement path calls. It does not matter which
        // routine wants the piece or why - an illegal one is refused before a single chunk is committed.
        if (!allowedByPieceClassRules(location, match, socket)) {
            return false;
        }
        // Capture terrain mob spawners BEFORE the stamp destroys them - they become pending harvest-chamber stock.
        HarvestSpawnerCapture.captureBeforeStamp(level, location, match.occupiedChunks());
        // And evict any hostile vermin standing in the footprint - construction does not leave cave mobs inside.
        HiveStampEviction.evict(level, location, match.occupiedChunks());
        if (!commission(level, location, match, socket)) {
            return false;
        }
        for (ChunkPos c : match.occupiedChunks()) {
            HiveLocationClaims.claim(level, location, c, tick);
        }
        // Jelly vats grow when the CHAMBER EXISTS - at carve COMPLETION (CarveSiteWork calls growVatsIfJellyChamber
        // then), because a freshly commissioned chamber is still a hole in the ground with no vat slots.
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
                // Strain shell: onLoad already adopts, but the ordering of block-entity registration differs
                // between loaders - the explicit call is idempotent and guarantees the vat wears the hive's
                // shell the tick it grows rather than the next chunk load.
                if (level.getBlockEntity(slot) instanceof com.alien.common.gameplay.block.entity.jelly.JellyVatBlockEntity vat) {
                    vat.adoptHiveStrain();
                }
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
        // ⭐⭐ IRRADIATED HIVES REBUILD FOR FREE. [stated] "they would do this free of biomass cost but not an instant
        // stamp - have them still work towards 'placing' the resin", and again: "irradiated xenos rebuild for free no
        // biomass cost".
        // <p>
        // ⚠ FREE, NOT INSTANT. Only the DEBT is waived - the site is still commissioned, still dug, still filled at
        // the ordinary pace by an ordinary crew. A white dwarf can still lay resin; what it cannot do is pay for it,
        // because its biomass only ever comes from kills and its jelly never refills at all.
        // </p>
        // <p>
        // ⚠ IrradiatedHiveRules was already consulted by HiveBalanceTask (1 biomass + 1 jelly promotions),
        // JellyProduction and JellyVatDisplay - the ECONOMY - but nothing in the STRUCTURE path ever asked. So a
        // converted hive was being charged full price to rebuild its own crater.
        // </p>
        var resinCost = com.alien.common.gameplay.hive.economy.IrradiatedHiveRules.isIrradiated(location)
            ? 0
            : (int) Math.ceil(
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

    /**
     * Ordering for pending goals; lower routes first, ties broken by distance from centre.
     * <p>
     * THE FIRST EGG CHAMBER OUTRANKS EVERYTHING. A hive with no nursery is in a deadlock it cannot dig out of: eggs
     * have nowhere to be hauled, so no new aliens hatch, so there are no drones to carve the egg chamber. Razorem's log
     * shows exactly that - six commissions, all six "unstaffed - no free drones, nothing in reserve", and six "Egg haul
     * STUCK ... has 0 egg chamber(s)" while the queen still had 40+ free clutch cells. Eggs used to sit in the LAST
     * tier, tied with jelly, and lose the distance tiebreak every time because the blueprint deliberately spreads egg
     * goals widest (EGG_SPACING). So the hive built jelly vaults and hubs while its own nursery never came up.
     * <p>
     * It jumps the raid chamber, which normally wants the emptiest map, and that is an accepted cost: an egg chamber is
     * 1x1 with a zero-chunk reserve margin, so one of them early barely marks the footprint. The promotion applies ONLY
     * while the count is zero - the second onward go back to the normal tier.
     * <p>
     * Jelly vaults also drop BELOW eggs generally, since they were the rooms winning that tie.
     */
    private static int goalPriority(HiveLocation location, String roomType) {
        if (roomType.contains("chamber_egg") && countRoomsOfType(location, "chamber_egg") == 0) {
            return -1; // existential - the hive cannot grow a workforce without one
        }
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
        if (roomType.contains("chamber_jelly")) {
            return 4; // demoted below eggs - jelly was winning the old shared tier on distance alone
        }
        return 3; // eggs and anything unlisted - 1x1 rooms that fit almost anywhere
    }

    /** How many rooms of this type stand in the hive (chunk count over the type's footprint). */
    /** Public so the purchase-condition system can ask "how many raid chambers does this hive have?". */
    public static int countRoomsOfType(HiveLocation location, String roomType) {
        // Counted from builtPlacements - ONE ENTRY PER PLACED PIECE - not from per-chunk bookkeeping divided by
        // footprint. The old chunks/4 math undercounted whenever a single chunk of a 2x2 failed to record, and an
        // undercounted goal re-fires: the plausible root of a hive building a SECOND raid chamber seconds after
        // its first completed. A placement either exists or it does not.
        int rooms = 0;
        for (var placement : location.builtPlacements().values()) {
            var id = placement.pieceId();
            // The "chamber_jelly" goal means jelly VAULTS; royal-jelly chambers (special-door attachments) share the
            // substring and must not satisfy vault goals, or shortfalls go invisible.
            if (id.contains(roomType) && !(roomType.equals("chamber_jelly") && id.contains("jelly_royal"))) {
                rooms++;
            }
        }
        return rooms;
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
    /**
     * ⭐ [stated] "needs to be at a minimum 1 in from the border." One ring, so the scourge companion always has a chunk
     * to occupy on the outward side.
     */
    private static final int RAID_PLACEMENT_RIM_MARGIN = 1;

    /** How many rings inward a rim-refused room may walk its target before giving up for this pass. */
    private static final int RIM_RETRY_STEPS = 3;

    /** The core ring goals never cross - mirrors HiveBlueprintGenerator.MIN_GOAL_DIST, which is private there. */
    private static final int MIN_GOAL_RING = 3;

    private static boolean withinExtent(PieceMatch match, ChunkPos center) {
        return withinExtent(match, center, 0);
    }

    /**
     * ⭐⭐ THE SAME EXTENT TEST, BUT ALLOWED TO KEEP A PIECE OFF THE RIM.
     * <p>
     * [stated] "any raid chamber cant be the border structure and needs to be at a minimum 1 in from the border." A
     * raid chamber on the last ring has nowhere to hang its MANDATORY scourge companion room - the scourge door ends up
     * facing outside the footprint and the companion becomes unplaceable, so the raid room exists and its scourge room
     * never does. That is exactly the empress hive he found: a second raid chamber on the rim of a 23x23, with no
     * scourge chamber beside it.
     * </p>
     * <p>
     * ⚠⚠ THE BLUEPRINT ALREADY TRIED TO PREVENT THIS AND IT WAS NOT ENOUGH. `RAID_RIM_MARGIN` keeps the GOAL off the
     * rim, but a goal is only a target - the router places the actual piece at whatever socket it can reach, and a 2x2
     * room anchored one ring in still occupies the rim. **The margin has to be enforced where the piece lands, not
     * where the goal was rolled.**
     * </p>
     */
    private static boolean withinExtent(PieceMatch match, ChunkPos center, int rimMargin) {
        var limit = activeExtent - rimMargin;

        for (ChunkPos c : match.occupiedChunks()) {
            if (cheby(c, center) > limit) {
                return false;
            }
        }
        return true;
    }

    /**
     * How far off the rim a room of this type must stay. ⚠ EVERY chunk the piece occupies is tested, so a 2x2 raid
     * chamber with one corner on the rim is refused just as a 1x1 on the rim would be.
     */
    /**
     * One ring closer to the centre, along whichever axis is currently furthest out. Null once the target reaches the
     * core, so the caller stops rather than piling every retry onto the queen's own chunk.
     */
    private static @Nullable ChunkPos stepTowardCenter(ChunkPos from, ChunkPos center) {
        var dx = from.x - center.x;
        var dz = from.z - center.z;

        if (Math.max(Math.abs(dx), Math.abs(dz)) <= MIN_GOAL_RING) {
            return null;
        }

        // Move on the dominant axis so the target walks straight in rather than drifting diagonally across rooms
        // that are already built.
        if (Math.abs(dx) >= Math.abs(dz)) {
            return new ChunkPos(from.x - Integer.signum(dx), from.z);
        }

        return new ChunkPos(from.x, from.z - Integer.signum(dz));
    }

    private static int rimMarginFor(String roomType) {
        return roomType.contains("chamber_raid") ? RAID_PLACEMENT_RIM_MARGIN : 0;
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
            // ⭐⭐ A DEAD END IS SPARE NURSERY SPACE. [stated] "if they didnt build all the eggcells they can turn
            // one of the dead ends into an eggcell cant they?" - yes, and it is a straight swap because
            // chamber_egg_1x1 has the SAME 1x1 footprint as the hallways that dead-end. A hive that finished
            // "WITHOUT chamber_egg (3/5)" ran out of ROUTE, not out of room; this is the last chance to spend the
            // leftover space on the shortfall instead of walling it off.
            // ⚠ Recount every iteration - each success changes the answer, or one dead end would authorise many.
            if (
                countRoomsOfType(location, "chamber_egg") < HiveStructurePlanner.targetEggChambers(location)
                    && attachEggChamberAt(level, registry, location, socket, center, currentTick)
            ) {
                Alien.LOGGER.info(
                    "Hive {}: converted a dead end at {} into an egg chamber ({}/{}) instead of sealing it.",
                    center,
                    growthChunk(socket),
                    countRoomsOfType(location, "chamber_egg"),
                    HiveStructurePlanner.targetEggChambers(location)
                );
                continue; // socket consumed by the placer
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
            if (!isSpecialDoor(socket.doorType())) {
                continue;
            }
            // [stated] raid rule 3: each raid chamber gets ITS scourge jelly chamber - exactly one. A scourge
            // socket whose doorway already has a scourge chamber standing beside it is the leftover TWIN jigsaw
            // (persisted from before twin consumption existed; "the two scourge sockets were connected") - remove
            // it instead of growing a second room. Same-doorway test: a scourge chamber within chebyshev 2.
            if (
                socket.doorType() != null && socket.doorType().contains("scourge")
                    && hasRoomOfTypeNear(location, socket.chunk(), "chamber_scourge", 2)
            ) {
                location.frontierSockets().remove(socket);
                continue;
            }
            if (attachAt(level, registry, location, socket, center, currentTick)) {
                return true;
            }
        }
        return false;
    }

    /** True when a placed piece of this room type stands within {@code range} chunks (chebyshev) of {@code near}. */
    private static boolean hasRoomOfTypeNear(HiveLocation location, ChunkPos near, String roomType, int range) {
        for (var entry : location.structurePieceByChunk().entrySet()) {
            if (entry.getValue().contains(roomType) && near.getChessboardDistance(entry.getKey()) <= range) {
                return true;
            }
        }
        return false;
    }

    /** Attaches a special door's ONE companion chamber - and nothing else that happens to share the door type. */
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
        var companion = companionRoomFor(socket.doorType());
        for (PieceMatch match : HivePieceMatcher.matchesFromRegistry(socket, registry, chunkIsFree, location.lineageVariantOrNull())) {
            // The registry offers EVERY piece carrying this door type, and the special-door pieces are not only
            // the chambers: hallway_royal_2x1_a/_b carry a hive_jelly_door and chamber_raid_2x2 carries a
            // hive_scourge_door. Unfiltered, a royal hall's own jelly doorway grew a FIFTH royal hall and a raid
            // chamber's scourge doorway grew a SECOND raid chamber. Take the companion or take nothing; the loop
            // keeps walking, so the real chamber still gets built on the same pass.
            if (companion != null && !match.piece().id().getPath().contains(companion)) {
                continue;
            }
            if (withinExtent(match, center) && place(level, location, match, socket, currentTick)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Attaches an EGG CHAMBER specifically, for the dead-end conversion above.
     * <p>
     * Same shape as {@link #attachAt} but the match loop is filtered to {@code chamber_egg_1x1} instead of running the
     * ordinary weighted selection - a dead end that could take any room must not become a random one; the whole point
     * is to spend it on the shortfall. There is no companion-room rule here because an egg chamber carries only
     * ordinary hive doors.
     * </p>
     * <p>
     * ⚠ The socket may simply not fit one (extent, occupancy, a door facing that no rotation satisfies). Returning
     * false is normal and the caller falls straight through to capping, exactly as before.
     * </p>
     */
    private static boolean attachEggChamberAt(
        ServerLevel level,
        HivePieceRegistry registry,
        HiveLocation location,
        FrontierSocket socket,
        ChunkPos center,
        long currentTick
    ) {
        var built = location.structurePieceByChunk().keySet();
        Predicate<ChunkPos> chunkIsFree = c -> !built.contains(c);

        for (
            PieceMatch match : HivePieceMatcher.matchesFromRegistry(
                socket,
                registry,
                chunkIsFree,
                location.lineageVariantOrNull()
            )
        ) {
            if (!match.piece().id().getPath().contains("chamber_egg")) {
                continue;
            }
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
    /**
     * Which faces of this chunk already have an open doorway.
     * <p>
     * ⚠⚠ A FLOODED DOORWAY IS STILL A DOORWAY. This tested {@code isAir()}, and water is NOT air - so an exit that
     * filled with water read as CLOSED forever. The router re-stamped the hallway to "carve" a door that was already
     * there, the water flowed straight back in, and the next pass saw it closed again: an endless re-stamp of the same
     * chunk, 45 times in one log at [-10, 2]. [stated] "the hive seems to not be able to replace a breach if waters
     * there. they keep repairing the same spot over and over and not building."
     * </p>
     * <p>
     * ⚠ AND THE LOOP BLOCKED EVERYTHING BEHIND IT. This pass returns true on a successful re-stamp, so the router spent
     * every tick on the same doorway and never reached the stitching pass below - which is why the hive stopped
     * BUILDING rather than merely wasting effort.
     * </p>
     * <p>
     * ⚠ THE TEST IS NOW "CAN A XENOMORPH GET THROUGH", not "is it air": anything replaceable counts, which covers
     * water, lava, and the resin web the hive puts over its own openings. Xenomorphs already walk through all three.
     * </p>
     */
    private static EnumSet<Direction> openDoorFaces(ServerLevel level, HiveLocation location, ChunkPos chunk) {
        var faces = EnumSet.noneOf(Direction.class);
        int y = location.hiveFloorY() + 2;
        int cx = chunk.getMinBlockX() + 8;
        int cz = chunk.getMinBlockZ() + 8;
        if (isDoorwayOpen(level, new BlockPos(cx, y, chunk.getMinBlockZ()))) {
            faces.add(Direction.NORTH);
        }
        if (isDoorwayOpen(level, new BlockPos(cx, y, chunk.getMaxBlockZ()))) {
            faces.add(Direction.SOUTH);
        }
        if (isDoorwayOpen(level, new BlockPos(chunk.getMinBlockX(), y, cz))) {
            faces.add(Direction.WEST);
        }
        if (isDoorwayOpen(level, new BlockPos(chunk.getMaxBlockX(), y, cz))) {
            faces.add(Direction.EAST);
        }
        return faces;
    }

    /**
     * ⚠ Open means PASSABLE, not empty. Air, water, lava and resin web all count - a xenomorph walks through every one
     * of them, so treating a flooded or webbed opening as solid rock is what created the re-stamp loop.
     */
    private static boolean isDoorwayOpen(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);

        return state.isAir() || !state.getFluidState().isEmpty() || state.canBeReplaced();
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
        for (HivePiece piece : registry.piecesWithDoorType("avp_alien:hive_door", location.lineageVariantOrNull())) {
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
