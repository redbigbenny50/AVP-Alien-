package com.alien.common.gameplay.hive.structure;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Generates a {@link HiveBlueprint}: the spaced room goals and per-side exits for a hive, chosen deterministically from
 * a per-location seed. This is the "decide the layout first" step of the goal-based planner - the router that carves
 * corridors out to these goals comes later. Because it is seeded from the founding chunk, every hive gets a distinct
 * layout while the same spot always regenerates the same one.
 */
public final class HiveBlueprintGenerator {

    private static final int CORE_RADIUS = 1; // the 3x3 queen core (centre +/- 1) - drawn on the ASCII map

    private static final int MIN_GOAL_DIST = 3; // goals sit at least this far (Chebyshev) from centre - beyond the
                                                // royal ring

    private static final int GOAL_SPACING = 3; // minimum Chebyshev distance between goal anchors

    private static final int EGG_SPACING = 5; // eggs keep extra distance from everything, spreading them wide

    private static final int RAID_RIM_MARGIN = 2; // the raid stays this far off the rim so its scourge room always fits

    private static final int PLACE_ATTEMPTS = 200; // rejection-sampling tries per goal before falling back

    private HiveBlueprintGenerator() {}

    /**
     * Builds the blueprint for a hive centred on {@code center}, filling a (2*maxExtent+1) square footprint. The base
     * room list is fixed; their positions are random-but-spaced, and the seed makes it reproducible per location.
     */
    public static HiveBlueprint generate(ChunkPos center, int maxExtent, long seed) {
        var random = RandomSource.create(seed);
        var goals = new ArrayList<HiveBlueprint.Goal>();

        // Eggs go FIRST with extra spacing: they are the most numerous room, and spreading them across the whole
        // footprint pulls corridors toward well-distributed targets (placed last they got squeezed into pockets and
        // sometimes didn't all fit). The rest fill between them at normal spacing, largest first.
        addGoals(goals, "chamber_egg", 5, center, MIN_GOAL_DIST, maxExtent, EGG_SPACING, random);
        addGoals(goals, "chamber_host", 2, center, maxExtent, random);
        // The raid keeps off the outer rings: it is 2x2 AND must fit its mandatory scourge chamber beside it - on the
        // rim the scourge door can face outside the footprint and the companion room becomes unplaceable.
        addGoals(goals, "chamber_raid", 1, center, MIN_GOAL_DIST, maxExtent - RAID_RIM_MARGIN, GOAL_SPACING, random);
        addGoals(goals, "hub_2x2_4way", 2, center, maxExtent, random);
        addGoals(goals, "hub_2x2_3way", 2, center, maxExtent, random);
        addGoals(goals, "chamber_jelly", 2, center, maxExtent, random);

        return new HiveBlueprint(goals, pickExits(center, maxExtent, random));
    }

    /**
     * Empress-tier expansion: appends extra room goals and new outer-rim exits to a hive's base blueprint WITHOUT
     * disturbing it - the base is regenerated identically (same seed/extent), and the additions roll from a salted
     * seed, placed in the new outer band (oldExtent..newExtent-1), spaced against everything already planned. Add
     * further empress-only rooms to the list below as they're designed.
     */
    public static HiveBlueprint expand(HiveBlueprint base, ChunkPos center, int oldExtent, int newExtent, long seed) {
        var random = RandomSource.create(seed ^ 0x454D50524553L); // "EMPRES" salt - independent of the base roll
        var goals = new ArrayList<>(base.goals());

        // The empress unlocks a second raid chamber, placed out in the newly claimed band.
        addGoals(goals, "chamber_raid", 1, center, oldExtent, newExtent - RAID_RIM_MARGIN, GOAL_SPACING, random);
        // Add further empress-tier rooms here (extra eggs, jelly, empress-only chambers) as they are designed.

        var exits = new ArrayList<>(base.exits());
        exits.addAll(pickExits(center, newExtent, random)); // fresh exits on the NEW rim, one per side
        return new HiveBlueprint(goals, exits);
    }

    private static void addGoals(
        List<HiveBlueprint.Goal> goals,
        String type,
        int count,
        ChunkPos center,
        int maxExtent,
        RandomSource random
    ) {
        addGoals(goals, type, count, center, MIN_GOAL_DIST, maxExtent, GOAL_SPACING, random);
    }

    private static void addGoals(
        List<HiveBlueprint.Goal> goals,
        String type,
        int count,
        ChunkPos center,
        int minFromCenter,
        int maxExtent,
        int spacing,
        RandomSource random
    ) {
        for (int i = 0; i < count; i++) {
            var chunk = pickSpacedChunk(goals, center, minFromCenter, maxExtent, spacing, random);
            if (chunk != null) {
                goals.add(new HiveBlueprint.Goal(type, chunk));
            }
        }
    }

    /**
     * Rejection-samples a chunk in the footprint ring (outside the core, inside maxExtent) spaced from existing goals.
     */
    private static ChunkPos pickSpacedChunk(
        List<HiveBlueprint.Goal> existing,
        ChunkPos center,
        int minFromCenter,
        int maxExtent,
        int spacing,
        RandomSource random
    ) {
        ChunkPos best = null;
        int bestMinDist = -1;
        for (int attempt = 0; attempt < PLACE_ATTEMPTS; attempt++) {
            int dx = random.nextInt(2 * maxExtent + 1) - maxExtent;
            int dz = random.nextInt(2 * maxExtent + 1) - maxExtent;
            int cheby = Math.max(Math.abs(dx), Math.abs(dz));
            if (cheby < minFromCenter || cheby > maxExtent) {
                continue; // too close in, or outside the allowed band
            }
            var candidate = new ChunkPos(center.x + dx, center.z + dz);
            int minDist = minDistanceTo(existing, candidate);
            if (minDist >= spacing) {
                return candidate; // a properly spaced spot
            }
            if (minDist > bestMinDist) {
                bestMinDist = minDist;
                best = candidate; // remember the most-spaced option in case nothing clears GOAL_SPACING
            }
        }
        return best; // best-effort fallback (still the furthest-apart spot we saw)
    }

    private static int minDistanceTo(List<HiveBlueprint.Goal> goals, ChunkPos c) {
        if (goals.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        int min = Integer.MAX_VALUE;
        for (var g : goals) {
            min = Math.min(min, Math.max(Math.abs(c.x - g.chunk().x), Math.abs(c.z - g.chunk().z)));
        }
        return min;
    }

    /** One exit per side, at a random chunk along that edge of the footprint. */
    private static List<ChunkPos> pickExits(ChunkPos center, int maxExtent, RandomSource random) {
        int span = 2 * maxExtent + 1;
        var exits = new ArrayList<ChunkPos>();
        exits.add(new ChunkPos(center.x + random.nextInt(span) - maxExtent, center.z - maxExtent)); // north
        exits.add(new ChunkPos(center.x + random.nextInt(span) - maxExtent, center.z + maxExtent)); // south
        exits.add(new ChunkPos(center.x - maxExtent, center.z + random.nextInt(span) - maxExtent)); // west
        exits.add(new ChunkPos(center.x + maxExtent, center.z + random.nextInt(span) - maxExtent)); // east
        return exits;
    }

    /** Renders the blueprint as an ASCII map (north up) for logging, so dispersion can be eyeballed at a glance. */
    public static String toAsciiMap(HiveBlueprint blueprint, ChunkPos center, int maxExtent) {
        int span = 2 * maxExtent + 1;
        char[][] grid = new char[span][span];
        for (char[] row : grid) {
            Arrays.fill(row, '.');
        }
        for (int dx = -CORE_RADIUS; dx <= CORE_RADIUS; dx++) {
            for (int dz = -CORE_RADIUS; dz <= CORE_RADIUS; dz++) {
                grid[dz + maxExtent][dx + maxExtent] = 'Q';
            }
        }
        for (var exit : blueprint.exits()) {
            put(grid, center, maxExtent, exit, 'X');
        }
        for (var goal : blueprint.goals()) {
            put(grid, center, maxExtent, goal.chunk(), letterFor(goal.roomType()));
        }
        var sb = new StringBuilder("\n");
        for (char[] row : grid) {
            sb.append(new String(row)).append('\n');
        }
        sb.append("Q=core  X=exit  H=host  R=raid  4=4way-hub  3=3way-hub  E=egg  J=jelly");
        return sb.toString();
    }

    private static void put(char[][] grid, ChunkPos center, int maxExtent, ChunkPos chunk, char c) {
        int col = chunk.x - center.x + maxExtent;
        int row = chunk.z - center.z + maxExtent;
        if (row >= 0 && row < grid.length && col >= 0 && col < grid[0].length) {
            grid[row][col] = c;
        }
    }

    private static char letterFor(String type) {
        if (type.contains("host")) {
            return 'H';
        }
        if (type.contains("raid")) {
            return 'R';
        }
        if (type.contains("4way")) {
            return '4';
        }
        if (type.contains("3way")) {
            return '3';
        }
        if (type.contains("egg")) {
            return 'E';
        }
        if (type.contains("jelly")) {
            return 'J';
        }
        return '?';
    }
}
