package com.alien.common.gameplay.hive.containment;

import com.alien.common.registry.init.AlienDataStoreTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ⭐⭐⭐ IS THIS XENOMORPH SOMEBODY'S EXHIBIT, OR IS IT GENUINELY STUCK?
 * <p>
 * [stated] "he made a zoo and growth supressed them. this is why i was so pushy about detecting if a xeno is in
 * containment before pulling back to reserves." A caged specimen absorbed into a hive's reserves is DELETED from the
 * world, and its owner has no way to find out where it went.
 * </p>
 * <p>
 * ⭐⭐ TWO SIGNALS, AND NEITHER WORKS ALONE.
 * <ul>
 * <li><b>Stillness.</b> [stated] "if a xeno is enclosed and cant vent it would be stuck in the same general area for a
 * long period of time where as a free xeno would be wandering." True, but a drone wedged in a ravine is equally still,
 * and that one SHOULD be recovered.</li>
 * <li><b>Player visits.</b> [stated] "if a player has been in the surrounding chunks recently". A zoo is a place
 * someone returns to; a ravine is not. This is what separates the two.</li>
 * </ul>
 * Together: still AND visited means an exhibit; still AND abandoned means stuck.
 * </p>
 * <p>
 * ⚠ MEASURED AS MAX EXCURSION FROM AN ANCHOR, NOT START-TO-END DISPLACEMENT. A xenomorph pacing a corridor ends up
 * where it began, and a two-sample comparison would call that stationary. Tracking the furthest it has been from the
 * first place we saw it catches pacing correctly.
 * </p>
 * <p>
 * ⚠ THE ANCHORS ARE IN MEMORY AND DO NOT SURVIVE AN UNLOAD, on purpose. A xenomorph whose chunk unloaded starts
 * accumulating again, so it simply is not judged contained until it has been watched long enough — which fails toward
 * today's behaviour rather than toward deleting somebody's exhibit.
 * </p>
 */
public final class ContainmentDetector {

    /** How long a xenomorph must stay put before stillness counts. Generous: idle wander drifts within minutes. */
    private static final long STILLNESS_WINDOW_TICKS = 4L * 60L * 20L;

    /**
     * How far it may stray from its anchor and still count as still.
     * <p>
     * ⚠ Wider than the cells this protects (a praetorian in 5x5x3, an empress in 7x6x9) so a specimen with room to
     * shuffle still reads as contained, and narrower than idle wander's 10-block re-roll so a free xenomorph does not.
     * </p>
     */
    private static final double STILLNESS_RADIUS_SQR = 12.0 * 12.0;

    /**
     * How long a player's visit vouches for a chunk. Two in-game days.
     * <p>
     * ⚠ MUCH longer than a visit, because a zoo owner may not come by for a real-world day. Erring long costs only that
     * a genuinely stuck xenomorph near a well-travelled area waits longer for cleanup; erring short deletes exhibits.
     * </p>
     */
    private static final long VISIT_MEMORY_TICKS = 2L * 24000L;

    /** Chunk radius searched around the xenomorph for a recent visit. 3x3 covers a zoo without covering a base. */
    private static final int VISIT_CHUNK_RADIUS = 1;

    private record Anchor(
        BlockPos pos,
        long sinceGameTime,
        double maxExcursionSqr
    ) {}

    private static final Map<UUID, Anchor> ANCHORS = new ConcurrentHashMap<>();

    private ContainmentDetector() {}

    /**
     * Records that a player is here. Called from the per-tick player sweep the hive systems already run.
     * <p>
     * ⚠ Stamps the 3x3 around the player, not just their own chunk, so standing beside a cell counts as visiting it.
     * </p>
     */
    public static void stampPlayerVisit(ServerLevel level, ChunkPos center, long gameTime) {
        // ⭐⭐⭐ ONLY STAMP WHERE A HIVE COULD ACTUALLY TAKE SOMETHING.
        //
        // ⚠⚠ WITHOUT THIS THE STORE GROWS FOREVER. Every chunk any player ever walked through gained a PERSISTED
        // entry - BLib's chunk stores are region-backed and saved - so on a long-lived server with an explored map
        // that is thousands of chunks each carrying a stamp that never expires. It would never show as TPS lag; it
        // shows as save growth and slower region I/O months later, which is exactly the sort of thing reported as a
        // vague "quality drop".
        //
        // ⭐ The stamp only ever ANSWERS a question asked by absorption, and absorption only happens near a hive. A
        // stamp a thousand blocks from the nearest hive can never be read, so writing it is pure cost.
        if (!hiveWithinReach(level, center)) {
            return;
        }

        for (var dx = -VISIT_CHUNK_RADIUS; dx <= VISIT_CHUNK_RADIUS; dx++) {
            for (var dz = -VISIT_CHUNK_RADIUS; dz <= VISIT_CHUNK_RADIUS; dz++) {
                var chunk = new ChunkPos(center.x + dx, center.z + dz);
                if (!level.hasChunk(chunk.x, chunk.z)) {
                    continue; // never force-load a chunk to write a bookkeeping stamp
                }
                com.alien.Alien.MOD
                    .storage()
                    .getChunk(level, chunk, AlienDataStoreTypes.PLAYER_VISIT)
                    .ifSome(store -> store.stamp(gameTime));
            }
        }
    }

    /**
     * Is there a live hive close enough that something here could be absorbed?
     * <p>
     * ⚠ Walks the registry rather than probing chunks: a server has a handful of live locations, and this runs once a
     * second per player. Comparing against every location is cheaper than nine chunk-store lookups, and unlike them it
     * costs nothing at all when the answer is no.
     * </p>
     */
    private static boolean hiveWithinReach(ServerLevel level, ChunkPos center) {
        for (var location : com.alien.common.gameplay.hive.location.HiveLocationRegistry.INSTANCE.all()) {
            if (!location.isAlive() || !location.dimension().equals(level.dimension())) {
                continue;
            }
            var hiveChunk = new ChunkPos(location.centerPos());
            if (
                Math.abs(hiveChunk.x - center.x) <= HIVE_PROXIMITY_CHUNKS
                    && Math.abs(hiveChunk.z - center.z) <= HIVE_PROXIMITY_CHUNKS
            ) {
                return true;
            }
        }
        return false;
    }

    /**
     * How near a hive a chunk must be to be worth stamping.
     * <p>
     * ⚠ Comfortably wider than anything that reads the stamp - the stray-adoption reach and a hive's own territory are
     * both well inside it - so a zoo at the edge of a hive's influence is still covered. Being generous costs a few
     * hundred chunks per hive; being tight would silently reintroduce the very bug this check exists to prevent.
     * </p>
     */
    private static final int HIVE_PROXIMITY_CHUNKS = 24;

    /**
     * ⭐⭐ THE GATE. True means leave this xenomorph alone — it is in somebody's containment.
     * <p>
     * ⚠ CALL IT LAST, after every other absorb condition has already passed. It is the most expensive test in the chain
     * (a chunk-store lookup per surrounding chunk) and the rarest to matter, so it belongs where it runs least.
     * </p>
     */
    public static boolean isContained(ServerLevel level, net.minecraft.world.entity.Entity entity) {
        var gameTime = level.getGameTime();
        if (!isStill(entity, gameTime)) {
            return false;
        }
        return visitedRecently(level, entity.chunkPosition(), gameTime);
    }

    /** Drops an entity's anchor. Called when it is absorbed or dies so the map cannot grow without bound. */
    public static void forget(UUID entityId) {
        ANCHORS.remove(entityId);
    }

    private static boolean isStill(net.minecraft.world.entity.Entity entity, long gameTime) {
        var anchor = ANCHORS.get(entity.getUUID());
        if (anchor == null) {
            ANCHORS.put(entity.getUUID(), new Anchor(entity.blockPosition(), gameTime, 0.0));
            return false;
        }

        var excursionSqr = Math.max(anchor.maxExcursionSqr(), anchor.pos().distSqr(entity.blockPosition()));
        if (excursionSqr > STILLNESS_RADIUS_SQR) {
            // It went somewhere. Re-anchor here and start the window again.
            ANCHORS.put(entity.getUUID(), new Anchor(entity.blockPosition(), gameTime, 0.0));
            return false;
        }

        ANCHORS.put(entity.getUUID(), new Anchor(anchor.pos(), anchor.sinceGameTime(), excursionSqr));
        return gameTime - anchor.sinceGameTime() >= STILLNESS_WINDOW_TICKS;
    }

    private static boolean visitedRecently(ServerLevel level, ChunkPos center, long gameTime) {
        for (var dx = -VISIT_CHUNK_RADIUS; dx <= VISIT_CHUNK_RADIUS; dx++) {
            for (var dz = -VISIT_CHUNK_RADIUS; dz <= VISIT_CHUNK_RADIUS; dz++) {
                var chunk = new ChunkPos(center.x + dx, center.z + dz);
                if (!level.hasChunk(chunk.x, chunk.z)) {
                    continue;
                }
                // ⚠ isSomeAnd, not map+default: a chunk with no store yet is "nobody has been here",
                // which is the safe answer - ordinary stuck-member cleanup keeps working where there is no evidence.
                var visited = com.alien.Alien.MOD
                    .storage()
                    .getChunk(level, chunk, AlienDataStoreTypes.PLAYER_VISIT)
                    .isSomeAnd(store -> store.visitedWithin(gameTime, VISIT_MEMORY_TICKS));
                if (visited) {
                    return true;
                }
            }
        }
        return false;
    }
}
