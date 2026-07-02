package com.alien.common.gameplay.hive.growth;

import com.alien.Alien;
import com.alien.common.gameplay.hive.config.HiveConfig;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.blib.api.common.faction.v1.FactionMember;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * Picks chunks a location should try to claim. Loaded growth walks the cardinal-adjacent frontier of its current
 * {@code claimedChunks}, filters out chunks owned by any other registered location, and returns the candidate closest
 * to the location's center (Chebyshev distance). Passive unloaded growth uses the same frontier/occupancy checks but
 * selects a random candidate without requiring the chunk or an explorer xenomorph to be loaded.
 * <p>
 * The "loaded xeno present in the candidate" gate means hive growth is xenomorph-driven: the hive can't passively print
 * territory; an actual unit has to be out exploring an adjacent unclaimed chunk for it to be claimed.
 * <p>
 * See {@code HIVE_REDESIGN_09_GROWTH.md} § 3.2. Same-variant cross-lineage <em>contests</em> per {@code 03_LOCATIONS} §
 * 3 are deferred; until then no overlapping claims are minted.
 */
public final class ChunkPicker {

    private static final int[][] CARDINAL_OFFSETS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    private ChunkPicker() {}

    public static @Nullable ChunkPos pickNextChunk(ServerLevel level, HiveLocation location, HiveConfig config) {
        if (location.claimedChunks().size() >= config.maxChunksPerLocation()) {
            return null;
        }

        var centerChunk = new ChunkPos(location.centerPos());
        var frontier = collectFrontier(level, location, true, config);

        if (frontier.isEmpty()) {
            return null;
        }

        // Tiebreak (Chebyshev, Manhattan, x, z): within a ring, axial extremes (e.g., (2,0)) come before
        // corners (e.g., (2,2)), so partial fills are rotationally symmetric — a cross filling out toward a
        // square, rather than a square with one column missing.
        return frontier
                .stream()
                .min(
                        Comparator.<ChunkPos>comparingInt(c -> chebyshev(c, centerChunk))
                                .thenComparingInt(c -> manhattan(c, centerChunk))
                                .thenComparingInt(c -> c.x)
                                .thenComparingInt(c -> c.z)
                )
                .orElse(null);
    }

    public static @Nullable ChunkPos pickPassiveChunk(ServerLevel level, HiveLocation location, HiveConfig config) {
        if (location.claimedChunks().size() >= config.maxChunksPerLocation()) {
            return null;
        }

        var frontier = new ArrayList<>(collectFrontier(level, location, false, config));
        if (frontier.isEmpty()) {
            return null;
        }

        return frontier.get(level.random.nextInt(frontier.size()));
    }

    private static Set<ChunkPos> collectFrontier(
            ServerLevel level,
            HiveLocation location,
            boolean requireLoadedMember,
            HiveConfig config
    ) {
        var dimension = location.dimension();
        var frontier = new HashSet<ChunkPos>();
        var locationFaction = Alien.MOD.factions().get(location.id().value());
        var centerChunk = new ChunkPos(location.centerPos());
        var maxRadius = config.maxTerritoryRadiusChunks();

        for (var owned : location.claimedChunks()) {
            for (var offset : CARDINAL_OFFSETS) {
                var candidate = new ChunkPos(owned.x + offset[0], owned.z + offset[1]);

                if (location.claimedChunks().contains(candidate)) {
                    continue;
                }

                // Reject any candidate outside the max territory footprint (Chebyshev distance from the queen's
                // center chunk) — a 3x3 core (radius 1) extending out to radius maxRadius, e.g. radius 9 => 19x19.
                // This is a spatial bound distinct from maxChunksPerLocation's simple count cap, and prevents thin
                // "snake" claims from sprawling arbitrarily far from the hive's seat. Empress-influenced hives are
                // intended to expand this cap later (parked).
                if (chebyshev(candidate, centerChunk) > maxRadius) {
                    continue;
                }

                // Reject any chunk owned by another location (any lineage).
                var occupant = HiveLocationRegistry.INSTANCE.getByChunk(dimension, candidate);
                if (occupant != null) {
                    continue;
                }

                if (requireLoadedMember) {
                    // Candidate must be loaded.
                    if (!level.getChunkSource().hasChunk(candidate.x, candidate.z)) {
                        continue;
                    }

                    // At least one location-faction member must be standing in the candidate chunk. This makes loaded
                    // hive growth xenomorph-driven: an actual unit has to be exploring an adjacent unclaimed chunk.
                    if (!hasMemberInChunk(level, locationFaction, candidate)) {
                        continue;
                    }
                }

                frontier.add(candidate);
            }
        }

        return frontier;
    }

    private static boolean hasMemberInChunk(
            ServerLevel level,
            @Nullable com.blib.api.common.faction.v1.Faction<?> locationFaction,
            ChunkPos chunk
    ) {
        if (locationFaction == null) {
            return false;
        }
        for (var member : locationFaction.membership().getMembers()) {
            if (!(member instanceof FactionMember.Entity entityMember)) {
                continue;
            }
            var entity = level.getEntity(entityMember.uuid());
            if (entity == null) {
                continue;
            }
            if (new ChunkPos(entity.blockPosition()).equals(chunk)) {
                return true;
            }
        }
        return false;
    }

    private static int chebyshev(ChunkPos a, ChunkPos b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z));
    }

    private static int manhattan(ChunkPos a, ChunkPos b) {
        return Math.abs(a.x - b.x) + Math.abs(a.z - b.z);
    }
}