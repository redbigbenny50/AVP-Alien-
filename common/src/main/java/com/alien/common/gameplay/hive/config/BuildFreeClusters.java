package com.alien.common.gameplay.hive.config;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.tag.AlienBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ⭐⭐⭐ CLUSTER CHUNKS — WHAT REPLACES ROOMS WHEN NOTHING IS BUILT.
 * <p>
 * [stated] "perhaps runners/drones can place a cluster of resin_tendrils in a chunk to make that the egg or vat cluster
 * ... then the egghaul and vat spawn knows where to place themselves?"
 * </p>
 * <p>
 * ⭐⭐ HIS IDEA WORKS BECAUSE THE EXISTING PLUMBING NEVER ASKED ABOUT ROOMS IN THE FIRST PLACE.
 * {@code HiveChamberSlots.slots()} scans a chunk for blocks in the {@code RESIN_TENDRILS} tag at the hive floor with
 * air above, and hands back spaced positions. It has no idea whether a chamber exists — the tag's own doc says "chamber
 * furniture (egg beds, jelly vats) only grows on these". A structure just happened to be what laid tendrils. So a
 * tendril disc supplies the same input by another route, and egg hauling and vat spawning need no changes at all.
 * </p>
 * <p>
 * ⚠ THE DISC IS CLAMPED TO ITS CHUNK. He asked for radius 6 (13 across), which is ~113 blocks and would spill into
 * neighbouring chunks — and then "one splotch = one cluster" stops being true, so the 6-eggs and 6-vats-per-cluster
 * counts stop meaning anything. Clamping keeps a cluster a chunk and the numbers honest.
 * </p>
 */
public final class BuildFreeClusters {

    /** Requested disc radius. Clamped by the chunk edge and the wall margin, so it is a ceiling not a promise. */
    private static final int DISC_RADIUS = 6;

    /**
     * Open blocks required above a cluster cell, counting the standing cell itself.
     * <p>
     * [stated] Oct 1: "for the clusters make it 3 blocks of air to accomodate tall aliens maybe even 4 blocks. some
     * hosts are tall." ⇒ 4, and the same figure is used for build-free host spots. ⚠ Build-free only: a carved
     * chamber's slots keep their own 2-block rule, because the templates were authored for it.
     * </p>
     */
    public static final int CLUSTER_HEADROOM = 4;

    /** How long a cluster's measured floor is trusted before it is looked up again (terrain can be dug). One minute. */
    private static final long FLOOR_TTL_TICKS = 1200L;

    /** Minimum gap between re-stamps of the same cluster. Stamping is idempotent; this only bounds the scan cost. */
    private static final long RESTAMP_INTERVAL_TICKS = 600L;

    /**
     * Per hive, per cluster chunk: {floorY, measuredAtTick, lastStampTick}. Weakly keyed on the location, so a removed
     * hive takes its entries with it; nothing here is persisted (it is all re-derivable from the blocks).
     */
    private static final java.util.Map<HiveLocation, java.util.Map<Long, long[]>> FLOORS =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** Kept clear of the chunk edge so a disc never bleeds into the neighbour it does not own. */
    private static final int WALL_MARGIN = 2;

    private BuildFreeClusters() {}

    /**
     * The chunks this hive uses as egg clusters, nearest first from {@code from}.
     * <p>
     * ⭐ DETERMINISTIC FROM THE LOCATION ID AND CENTRE, so every caller — the hauler picking a bed, the diagnostic
     * counting free beds, a later vat pass — agrees on the same chunks without anything being stored. That matters
     * because the two egg-haul call sites enumerate independently, and a disagreement between them is exactly the "25
     * free beds and 0 spots on the failed list" livelock that bit the nursery once already.
     * </p>
     */
    public static List<ChunkPos> eggClusterChunks(HiveLocation location, ChunkPos from) {
        var config = HiveLocationRegistry.INSTANCE.config();
        if (!config.buildFreeAdditionalEggClusters()) {
            return List.of();
        }
        return clusterChunks(location, from, config.buildFreeEggClusterAmount(), 0L);
    }

    /** As {@link #eggClusterChunks}, for jelly vats. Offset seed so vats and eggs never land on the same chunk. */
    public static List<ChunkPos> jellyClusterChunks(HiveLocation location, ChunkPos from) {
        var config = HiveLocationRegistry.INSTANCE.config();
        return clusterChunks(location, from, config.buildFreeJellyClusterAmount(), 0x9E3779B9L);
    }

    /** As {@link #eggClusterChunks}, for the scourge vats. */
    public static List<ChunkPos> scourgeClusterChunks(HiveLocation location, ChunkPos from) {
        var config = HiveLocationRegistry.INSTANCE.config();
        return clusterChunks(location, from, config.buildFreeScourgeClusterAmount(), 0x7F4A7C15L);
    }

    /**
     * Stamps the tendril disc for a cluster chunk, if it is not already there.
     * <p>
     * ⚠ IDEMPOTENT AND CHEAP TO RE-RUN. It only writes cells that are not already this strain's tendril, so calling it
     * every time a hauler visits costs a scan and nothing else. That is deliberate: there is no "has this cluster been
     * built" flag to keep in sync, and therefore no way for such a flag to drift from the blocks on the ground.
     * </p>
     * <p>
     * ⚠ STRAIN-CORRECT. {@code HiveChamberSlots} matches the RESIN_TENDRILS **tag**, not the normal-strain block — a
     * nether hive laying normal tendrils would produce zero slots and stall exactly like the bug that once gave nether
     * hives no egg beds at all.
     * </p>
     */
    public static void stampCluster(ServerLevel level, HiveLocation location, ChunkPos chunk) {
        var entry = floorEntry(level, location, chunk);
        if (entry[0] == HiveLocation.NO_GROUND) {
            return;
        }
        var now = level.getGameTime();
        if (entry[2] != Long.MIN_VALUE && now - entry[2] < RESTAMP_INTERVAL_TICKS) {
            return; // stamped a moment ago - every hauler visit used to re-scan the whole disc
        }
        entry[2] = now;

        var tendril = strainTendril(location);
        // 🚨🚨 Oct 1: this used location.hiveFloorY() - in build-free the BOTTOM of the 48-block band, 24 blocks under
        // the queen - and wrote tendrils there unconditionally. That replaced stone, ore or a mapmaker's lower floor
        // with resin, and because a slot needs open space above, the cluster then held no eggs and grew no vats.
        var floorY = (int) entry[0];
        var centerX = chunk.getMiddleBlockX();
        var centerZ = chunk.getMiddleBlockZ();
        var radiusSq = DISC_RADIUS * DISC_RADIUS;
        var pos = new BlockPos.MutableBlockPos();

        for (var dx = -DISC_RADIUS; dx <= DISC_RADIUS; dx++) {
            for (var dz = -DISC_RADIUS; dz <= DISC_RADIUS; dz++) {
                if (dx * dx + dz * dz > radiusSq) {
                    continue;
                }
                var x = centerX + dx;
                var z = centerZ + dz;
                if (
                    x < chunk.getMinBlockX() + WALL_MARGIN
                        || x > chunk.getMaxBlockX() - WALL_MARGIN
                        || z < chunk.getMinBlockZ() + WALL_MARGIN
                        || z > chunk.getMaxBlockZ() - WALL_MARGIN
                ) {
                    continue; // clamped to its own chunk - see the class doc
                }
                pos.set(x, floorY, z);
                var state = level.getBlockState(pos);
                if (state.is(AlienBlockTags.RESIN_TENDRILS)) {
                    continue;
                }
                // ⚠ ONLY RE-SKIN EXPOSED GROUND. A solid, breakable, plain block with CLUSTER_HEADROOM open cells
                // above it. Anything else - a wall, a pit, a chest, bedrock - is left exactly as it was.
                if (
                    state.getCollisionShape(level, pos).isEmpty()
                        || !state.getFluidState().isEmpty()
                        || state.hasBlockEntity()
                        || state.getDestroySpeed(level, pos) < 0.0F
                        || !hasHeadroom(level, pos.getX(), floorY + 1, pos.getZ())
                ) {
                    continue;
                }
                level.setBlockAndUpdate(pos, tendril.defaultBlockState());
            }
        }
    }

    /**
     * The floor-block Y a cluster in {@code chunk} sits on, or {@link HiveLocation#NO_GROUND}. Measured at the chunk's
     * middle column (falling back to four nearby columns) with {@link HiveLocation#standingFloorY}, and cached for
     * {@link #FLOOR_TTL_TICKS} so slot reads and haulers do not re-scan the column every call.
     */
    public static int clusterFloorY(ServerLevel level, HiveLocation location, ChunkPos chunk) {
        return (int) floorEntry(level, location, chunk)[0];
    }

    private static long[] floorEntry(ServerLevel level, HiveLocation location, ChunkPos chunk) {
        var perHive = FLOORS.computeIfAbsent(location, key -> new java.util.concurrent.ConcurrentHashMap<>());
        var now = level.getGameTime();
        var entry = perHive.get(chunk.toLong());
        if (entry != null && now - entry[1] < FLOOR_TTL_TICKS) {
            return entry;
        }
        var floorY = HiveLocation.NO_GROUND;
        int[][] probes = { { 0, 0 }, { -3, -3 }, { 3, 3 }, { -3, 3 }, { 3, -3 } };
        for (var probe : probes) {
            floorY = location.standingFloorY(level, chunk.getMiddleBlockX() + probe[0], chunk.getMiddleBlockZ() + probe[1]);
            if (floorY != HiveLocation.NO_GROUND) {
                break;
            }
        }
        var lastStamp = entry == null ? Long.MIN_VALUE : entry[2];
        entry = new long[] { floorY, now, lastStamp };
        perHive.put(chunk.toLong(), entry);
        return entry;
    }

    /**
     * {@link #CLUSTER_HEADROOM} open, dry cells starting at the standing cell. Hive webbing and veins count as open.
     */
    public static boolean hasHeadroom(net.minecraft.world.level.Level level, int x, int standingY, int z) {
        var pos = new BlockPos.MutableBlockPos();
        for (var dy = 0; dy < CLUSTER_HEADROOM; dy++) {
            pos.set(x, standingY + dy, z);
            var state = level.getBlockState(pos);
            var open = state.isAir()
                || state.is(AlienBlockTags.RESIN_VEINS)
                || state.is(AlienBlockTags.RESIN_WEBS)
                || (state.canBeReplaced() && state.getFluidState().isEmpty());
            if (!open) {
                return false;
            }
        }
        return true;
    }

    /**
     * Picks {@code count} chunks spread through the territory, deterministically.
     * <p>
     * ⚠ SKIPS THE CENTRE CHUNK — that is the queen's own ground, and her egg ring already lives there. A cluster
     * stamped on top of her would put beds under the ovipositor's support points.
     * </p>
     */
    private static List<ChunkPos> clusterChunks(HiveLocation location, ChunkPos from, int count, long salt) {
        if (count <= 0) {
            return List.of();
        }

        var center = new ChunkPos(location.centerPos());
        var radius = BuildFreeMode.territoryRadiusChunks(location);
        var candidates = new ArrayList<ChunkPos>();

        for (var dx = -radius; dx <= radius; dx++) {
            for (var dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                var candidate = new ChunkPos(center.x + dx, center.z + dz);
                if (location.claimedChunks().contains(candidate)) {
                    candidates.add(candidate);
                }
            }
        }
        if (candidates.isEmpty()) {
            return List.of();
        }

        // Deterministic shuffle: order by a hash of the chunk and the salt, so the same hive always picks the same
        // chunks, different cluster KINDS pick different ones, and nothing has to be stored or synchronised.
        var seed = location.id().value().hashCode() + salt;
        candidates.sort(Comparator.comparingLong(c -> mix(seed, c.x, c.z)));

        var chosen = new ArrayList<>(candidates.subList(0, Math.min(count, candidates.size())));
        chosen.sort(Comparator.comparingInt(c -> Math.max(Math.abs(c.x - from.x), Math.abs(c.z - from.z))));
        return chosen;
    }

    /**
     * Stamps a jelly cluster AND grows its vats.
     * <p>
     * \u26a0\u26a0 A TENDRIL DISC ALONE IS NOT A VAT CLUSTER. {@code HiveChamberSlots.vatSlots} finds SPOTS; the vats
     * themselves are placed by {@code HiveRouter.growVatsIfJellyChamber} when a jelly chamber finishes building - and
     * in this mode no chamber ever finishes. Without this the cluster would be a patch of floor that every fill and
     * drain pass walks over and finds nothing on.
     * </p>
     * <p>
     * \u2b50 {@code adoptHiveStrain()} is called explicitly for the same reason the router does it: block-entity
     * registration order differs between loaders, so the vat wears the hive's shell the tick it grows rather than the
     * next chunk load. It is idempotent.
     * </p>
     */
    public static void stampJellyCluster(ServerLevel level, HiveLocation location, ChunkPos chunk) {
        stampCluster(level, location, chunk);

        for (var slot : com.alien.common.gameplay.hive.structure.HiveChamberSlots.vatSlots(level, location, chunk)) {
            if (level.getBlockEntity(slot) instanceof com.alien.common.gameplay.block.entity.jelly.JellyVatBlockEntity existing) {
                existing.adoptHiveStrain();
                continue;
            }
            level.setBlock(
                slot,
                com.alien.common.registry.init.block.AlienBlocks.JELLY_VAT.get().defaultBlockState(),
                3
            );
            if (level.getBlockEntity(slot) instanceof com.alien.common.gameplay.block.entity.jelly.JellyVatBlockEntity vat) {
                vat.adoptHiveStrain();
            }
        }
    }

    /**
     * The tendril block for this hive's strain.
     * <p>
     * \u26a0\u26a0 MUST BE THE STRAIN'S OWN BLOCK, not the normal one. {@code HiveChamberSlots} matches the
     * RESIN_TENDRILS **tag**, and a nether hive laid with normal tendrils produced ZERO slots - no egg beds, no vat
     * spots - which is precisely the bug that once left nether hives with nowhere to put anything. Same explicit
     * per-strain switch {@code OvipositorManager.strainResinBone} uses, for the same reason.
     * </p>
     */
    private static net.minecraft.world.level.block.Block strainTendril(HiveLocation location) {
        var type = AlienVariantTypes.getFor(location.lineageVariantOrNull());

        if (type == AlienVariantTypes.ABERRANT) {
            return com.alien.common.registry.init.block.AberrantAlienResinBlocks.ABERRANT_RESIN_TENDRIL.get();
        }
        if (type == AlienVariantTypes.NETHER) {
            return com.alien.common.registry.init.block.NetherAlienResinBlocks.NETHER_RESIN_TENDRIL.get();
        }
        if (type == AlienVariantTypes.IRRADIATED) {
            return com.alien.common.registry.init.block.IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL.get();
        }

        return com.alien.common.registry.init.block.AlienResinBlocks.RESIN_TENDRIL.get();
    }

    /** This hive's own strain of resin web - the block a build-free host is webbed into. Same switch as the tendril. */
    public static net.minecraft.world.level.block.Block strainWeb(HiveLocation location) {
        var type = AlienVariantTypes.getFor(location.lineageVariantOrNull());

        if (type == AlienVariantTypes.ABERRANT) {
            return com.alien.common.registry.init.block.AberrantAlienResinBlocks.ABERRANT_RESIN_WEB.get();
        }
        if (type == AlienVariantTypes.NETHER) {
            return com.alien.common.registry.init.block.NetherAlienResinBlocks.NETHER_RESIN_WEB.get();
        }
        if (type == AlienVariantTypes.IRRADIATED) {
            return com.alien.common.registry.init.block.IrradiatedAlienResinBlocks.IRRADIATED_RESIN_WEB.get();
        }

        return com.alien.common.registry.init.block.AlienResinBlocks.RESIN_WEB.get();
    }

    private static long mix(long seed, int x, int z) {
        var h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return h;
    }
}
