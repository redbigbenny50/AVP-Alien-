package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenLifecyclePhaseManager;
import com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.structure.HiveStructureRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/**
 * ⭐⭐⭐ GIVES A CONVOY-DELIVERED DAUGHTER THE SAME FOUNDING EVERY QUEEN ON FOOT GETS. Oct 3.
 * <p>
 * [stated] "people were still complaining about daughter hives building resin floors over oceans instead of making a
 * hive and getting stuck at that phase. Make sure start to end from all start point and possibilities nothing stops
 * daughters from founding hives fully except for the daughter hive cap."
 * </p>
 * <h2>Why daughter hives stalled</h2>
 * <p>
 * {@code AbstractSpreadAttempt} mints the daughter location ON PAPER, before any queen exists: a random chunk in the
 * spread zone, centre at {@code getMiddleBlockPosition(64)} with the comment "Y is approximate; chunk-load corrects
 * later". NOTHING EVER CORRECTED IT - {@code HiveLocation.setCenterPos} had no caller anywhere in the mod. The founder
 * convoy then spawned the daughter exactly there and made her its founder, which makes her "established": she skips the
 * LOCATION dig entirely, so the site was never held to {@code SpreadZoneCheck}'s surface and depth rules. Over the
 * ocean Y 64 is one block above sea level - her founding floor was stamped on the water. And normal founding calls
 * {@code HiveStructureFounding.establishQueenChamber} right after claiming the core; the paper mint never did, so the
 * hive had no queen chamber, no founding dig and no doorway sockets to grow from - on land too.
 * </p>
 * <h2>What this does</h2>
 * <ol>
 * <li><b>Site.</b> If the location's centre breaks the founding rules a queen on foot is held to
 * ({@link SpreadZoneCheck#isUnfitFoundingSite} - not open to the world, below the sea-level depth line - plus no liquid
 * at her feet and, for a strain that needs it, no lava within two blocks), it searches the centre chunk for a site that
 * keeps them: her own weighted founding depth, a solid floor, a pocket she fits in that can be carved without touching
 * anything protected or letting any liquid in. Then it moves the centre there, carves the pocket and puts her in it. A
 * centre that already keeps the rules is never moved.</li>
 * <li><b>Chamber.</b> Outside build-free mode, if the location has no queen chamber and no dig in progress it
 * commissions one exactly as normal founding does. From then on every system is the ordinary one.</li>
 * </ol>
 * <h2>Modes and dimensions</h2>
 * <ul>
 * <li><b>Build-free:</b> sited by the same rules (a build-free queen still digs and Rule 0 still applies to her unless
 * {@code queenFoundsWherePlaced} is on - and that setting forces daughter slots to 0, so no convoy daughter exists
 * then). No chamber: build-free builds no structures. ⚠ Recognised only by the convoy's tag or an unfit centre - never
 * by a missing chamber, because NO build-free hive has one.</li>
 * <li><b>End-style:</b> skipped. End hives never spread ({@code AbstractSpreadAttempt} refuses them) and found in place
 * with no chamber by design.</li>
 * <li><b>Ceiling dimensions (the Nether):</b> the same rules through the dimension profile - "open" there means an open
 * shelf of air above her, and lava safety applies to strains that need it.</li>
 * <li><b>Flat worlds and found-where-standing:</b> the site rules are waived for every queen, so the centre is not
 * moved; the chamber is still commissioned.</li>
 * </ul>
 * <p>
 * It also heals daughters already stranded on a raft in an existing world - same state, with or without the tag. ⚠ The
 * site stays inside the centre chunk: the 3x3 claim core and the 3x3 chamber footprint are both centred on it.
 * </p>
 */
public final class DaughterHiveSiting {

    /** How often a candidate daughter is looked at. The first look does the work; later looks are a few lookups. */
    private static final int CHECK_INTERVAL_TICKS = 40;

    /** Wait between failed searches, so a chunk with no usable site cannot cost a search every two seconds. */
    private static final long RETRY_BACKOFF_TICKS = 1200L;

    /**
     * After this many failed searches she founds where she is. The same give-up the on-foot path has
     * ({@code SpreadZoneCheck}'s surface grace): a world with no usable ground must not strand a queen forever.
     */
    private static final int MAX_FAILED_SEARCHES = 3;

    /** Columns sampled inside the centre chunk: the middle first, then a 5x5 grid. */
    private static final int[] COLUMN_OFFSETS = { 8, 2, 5, 11, 14 };

    /** How far above the rolled depth the search may climb when everything below is unusable. */
    private static final int MAX_CLIMB_BLOCKS = 24;

    /** Bound on full pocket checks per search, so one awkward chunk cannot cost a tick spike. */
    private static final int MAX_POCKET_CHECKS = 300;

    /** Lava clearance for strains that need it - the radius every other lava-safe placement in the mod uses. */
    private static final int LAVA_SAFE_RADIUS = 2;

    private DaughterHiveSiting() {}

    /**
     * Per-tick entry point, owned by the queen.
     *
     * @param queen the queen
     */
    public static void tick(Queen queen) {
        if (queen.tickCount % CHECK_INTERVAL_TICKS != 0 || queen.isDaughterSitingDone()) {
            return;
        }
        // \u26a0 NOT gated on QueenLifecyclePhaseManager.isEnabled(): a convoy daughter never uses the lifecycle - she
        // is
        // made founder directly - so with the lifecycle switched off she would still be stranded at the placeholder.
        if (!(queen.level() instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() < queen.getDaughterSitingRetryAt()) {
            return;
        }

        var location = locationNeedingAttention(level, queen);
        if (location == null) {
            return;
        }
        if (!level.hasChunkAt(location.centerPos())) {
            return; // the column to search is not loaded yet - look again when it is
        }

        if (!exemptFromSiteRules(level) && centreBreaksRules(level, location)) {
            var site = findSite(level, queen, location);
            if (site == null) {
                queen.recordDaughterSitingFailure(level.getGameTime() + RETRY_BACKOFF_TICKS);
                if (queen.getDaughterSitingFailures() < MAX_FAILED_SEARCHES) {
                    Alien.LOGGER.warn(
                        "Daughter siting: no usable site in the centre chunk of {} yet (attempt {}/{}) - retrying",
                        location.id(),
                        queen.getDaughterSitingFailures(),
                        MAX_FAILED_SEARCHES
                    );
                    return;
                }
                // Oct 9 - NEVER FOUND ON THE SURFACE. [stated] a daughter built a full hive on the surface above the
                // ocean - "this shouldnt be happening". This used to give up after three searches and found wherever
                // she
                // stood (the surface spot she was materialised at), commissioning the whole chamber there. Now she
                // keeps
                // looking (every minute) instead: a slow daughter is a much smaller problem than a surface hive, and
                // the search above now reaches every dry depth of the chunk.
                Alien.LOGGER.warn(
                    "Daughter siting: no usable site in the centre chunk of {} after {} searches - still looking (she will "
                        + "not found on the surface)",
                    location.id(),
                    queen.getDaughterSitingFailures()
                );
                return;
            } else {
                moveCentreTo(level, queen, location, site);
            }
        }

        // Oct 5 - REPAIRS A WORLD ALREADY HIT BY CAUSE 2: a queen re-sited before this fix stands at her new depth
        // while
        // her unbuilt founding core is still waiting at the old one. Drop it and commission it where she is.
        if (hasStaleFoundingCore(location)) {
            Alien.LOGGER.info(
                "Daughter siting: the unbuilt queen chamber of {} was waiting at floor y={} while its centre is at {} - "
                    + "commissioning it again at the centre",
                location.id(),
                location.activeCarveSite().floorY(),
                location.centerPos()
            );
            location.setActiveCarveSite(null);
            com.alien.common.gameplay.hive.structure.HiveStructureFounding.establishQueenChamber(
                level.getServer(),
                location,
                new ChunkPos(location.centerPos())
            );
        }

        // Normal founding commissions the queen chamber right after claiming the core; the paper mint never did.
        // Build-free builds no structures, so it never gets one there.
        if (needsChamber(location)) {
            com.alien.common.gameplay.hive.structure.HiveStructureFounding.establishQueenChamber(
                level.getServer(),
                location,
                new ChunkPos(location.centerPos())
            );
        }
        markLineageDirty(location);
        queen.setDaughterSitingDone(true);
        queen.setPendingDaughterLocationId(null);
        Alien.LOGGER.info(
            "Daughter siting: queen {} is founding location {} at {}{}",
            queen.getUUID(),
            location.id(),
            location.centerPos(),
            com.alien.common.gameplay.hive.config.BuildFreeMode.isEnabled()
                ? " (build-free: no chamber)"
                : hasQueenChamber(location) ? " - queen chamber commissioned" : " - queen chamber could not be commissioned"
        );
    }

    /**
     * The location this daughter founds and that still needs attention, or null. Found through the convoy's tag first
     * (she may have drifted off the centre chunk while swimming), then through the chunk she stands in (daughters
     * stranded before the tag existed).
     */
    private static @Nullable HiveLocation locationNeedingAttention(ServerLevel level, Queen queen) {
        HiveLocation tagged = null;
        var tag = queen.getPendingDaughterLocationId();
        if (tag != null) {
            var id = ResourceLocation.tryParse(tag);
            tagged = id == null ? null : HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(id));
            if (tagged == null) {
                queen.setPendingDaughterLocationId(null); // the location is gone; nothing to site
            }
        }
        var location = tagged != null
            ? tagged
            : HiveLocationRegistry.INSTANCE.getByChunk(level.dimension(), new ChunkPos(queen.blockPosition()));

        if (location == null || !queen.getUUID().equals(location.founderId())) {
            return null; // not hers (yet) - nothing to settle
        }
        if (
            !location.isAlive()
                || location.reproductiveEstablished()
                || location.isEndStyleHive()
                || com.alien.common.gameplay.hive.dimension.EndStyleHiveRules.isEndStyle(level)
                || !location.dimension().equals(level.dimension())
        ) {
            queen.setDaughterSitingDone(true); // established, End, or elsewhere: never needs looking at again
            return null;
        }
        if (QueenCaptivity.isCaptive(queen)) {
            return null; // a captive founds nothing; look again once she is free
        }
        // Oct 5 - a hive whose queen chamber is already BUILT is never moved: moving her would walk her out of a
        // finished home. Only a site whose core is still unbuilt (or not yet commissioned) is ever re-sited.
        if (hasBuiltQueenChamber(location)) {
            queen.setDaughterSitingDone(true);
            return null;
        }

        // Untagged: only a centre that breaks the founding rules, or (outside build-free) a hive with no chamber and no
        // dig, marks a stranded daughter. A normally founded queen meets both, so she is left alone.
        // Oct 5 - a carve site loaded from disk is only hydrated on the hive's first tick; until then its depth cannot
        // be read, so wait rather than declare the queen settled.
        if (location.hasActiveCarveSite() && location.activeCarveSite() == null) {
            return null;
        }
        if (
            tagged == null
                && !centreBreaksRules(level, location)
                && !needsChamber(location)
                && !hasStaleFoundingCore(location)
        ) {
            queen.setDaughterSitingDone(true);
            return null;
        }
        return location;
    }

    /** Flat worlds and found-where-standing modes waive the site rules for every queen; they do here too. */
    private static boolean exemptFromSiteRules(ServerLevel level) {
        return SpreadZoneCheck.isFlatWorld(level)
            || QueenLifecyclePhaseManager.foundsWhereStanding()
            || com.alien.common.gameplay.hive.config.BuildFreeMode.queenFoundsWherePlaced();
    }

    /** The founding rules a queen on foot is held to, asked of the hive's current centre. */
    private static boolean centreBreaksRules(ServerLevel level, HiveLocation location) {
        if (exemptFromSiteRules(level)) {
            return false;
        }
        var centre = location.centerPos();
        if (!level.getFluidState(centre).isEmpty() || !level.getFluidState(centre.above()).isEmpty()) {
            return true;
        }
        if (SpreadZoneCheck.isUnfitFoundingSite(level, centre)) {
            return true;
        }
        return needsLavaSafety(level, location) && !DimensionHiveProfiles.isLavaSafe(level, centre, LAVA_SAFE_RADIUS);
    }

    /** Outside build-free, a hive with no queen chamber and no dig in progress was never given one. */
    private static boolean needsChamber(HiveLocation location) {
        return !com.alien.common.gameplay.hive.config.BuildFreeMode.isEnabled()
            && !hasQueenChamber(location)
            && !location.hasActiveCarveSite();
    }

    private static boolean hasQueenChamber(HiveLocation location) {
        return location.structureRoleByChunk().containsValue(HiveStructureRole.QUEEN_CHAMBER_CENTER);
    }

    private static boolean needsLavaSafety(ServerLevel level, HiveLocation location) {
        return DimensionHiveProfiles.needsLavaSafety(DimensionHiveProfiles.get(level), location.lineageVariantOrNull());
    }

    /**
     * Searches the centre chunk for a site a queen on foot would be allowed to found at. Middle column first; within a
     * column, from her rolled founding depth DOWN (deeper is always legal), then a little UP if nothing below works.
     */
    private static @Nullable BlockPos findSite(ServerLevel level, Queen queen, HiveLocation location) {
        var chunk = new ChunkPos(location.centerPos());
        var targetY = queen.getLifecyclePhaseManager().rollFoundingTargetY();
        var minY = level.getMinBuildHeight() + 6;
        var maxY = DimensionHiveProfiles.roofSafeFloorMax(level);
        targetY = Math.max(minY, Math.min(targetY, maxY));

        var halfWidth = Math.max(1, (int) Math.ceil(queen.getBbWidth() / 2.0));
        var height = Math.max(2, (int) Math.ceil(queen.getBbHeight()));
        var lavaSafety = needsLavaSafety(level, location);
        var budget = new int[] { MAX_POCKET_CHECKS };
        // Oct 5 - whether the CHAMBER band at a given floor Y is dry. The chamber spans the same 3x3 chunks for every
        // candidate here (they are all in the centre chunk), so the answer depends on Y alone: one sweep of the
        // footprint per search, then a lookup per candidate.
        var chamberBand = com.alien.common.gameplay.hive.config.BuildFreeMode.isEnabled()
            ? null
            : ChamberBand.scan(level, chunk, minY, Math.min(maxY, targetY + MAX_CLIMB_BLOCKS));

        // Oct 9 - DEPTH FIRST, THEN COLUMNS. The chamber band depends on the floor Y alone, but the search used to walk
        // one column from top to bottom before trying the next, spending the pocket-check budget on every Y of the
        // first
        // column - including all the Ys whose band is wet - so on a shore or over an ocean it could run out before ever
        // reaching a dry depth in another column. Now a wet band Y is skipped outright (no budget spent), and every
        // column is tried at each dry depth before going deeper.
        var upperY = Math.min(maxY, targetY + MAX_CLIMB_BLOCKS);

        for (var step = 0;; step++) {
            // targetY, targetY-1, ... minY, then targetY+1 ... upperY
            int y;

            if (targetY - step >= minY) {
                y = targetY - step;
            } else {
                var up = step - (targetY - minY);

                if (targetY + up > upperY) {
                    break;
                }

                y = targetY + up;
            }

            if (chamberBand != null && !chamberBand.isDry(y)) {
                continue;
            }

            for (var ox : COLUMN_OFFSETS) {
                for (var oz : COLUMN_OFFSETS) {
                    var feet = new BlockPos(chunk.getMinBlockX() + ox, y, chunk.getMinBlockZ() + oz);

                    if (fits(level, feet, halfWidth, height, lavaSafety, budget)) {
                        return feet;
                    }

                    if (budget[0] <= 0) {
                        return null;
                    }
                }
            }
        }

        return null;
    }

    /**
     * A queen can stand here and found: the founding rules pass, there is a solid floor under her, and the pocket she
     * occupies can be cleared without touching anything protected or letting any liquid in. Cheap tests first.
     */
    private static boolean fits(
        ServerLevel level,
        BlockPos feet,
        int halfWidth,
        int height,
        boolean lavaSafety,
        int[] budget
    ) {
        var floor = feet.below();
        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
            return false;
        }
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()) {
            return false;
        }
        if (SpreadZoneCheck.isUnfitFoundingSite(level, feet)) {
            return false;
        }

        budget[0]--;
        if (lavaSafety && !DimensionHiveProfiles.isLavaSafe(level, feet, LAVA_SAFE_RADIUS)) {
            return false;
        }
        // The pocket (cleared) plus a one-block shell around it (left standing) must both be free of liquid; the
        // pocket's own blocks must also be diggable - never carve through a block entity or an unbreakable block.
        for (var dx = -halfWidth - 1; dx <= halfWidth + 1; dx++) {
            for (var dz = -halfWidth - 1; dz <= halfWidth + 1; dz++) {
                for (var dy = -1; dy <= height; dy++) {
                    var pos = feet.offset(dx, dy, dz);
                    if (!level.getFluidState(pos).isEmpty()) {
                        return false;
                    }
                    var inPocket = Math.abs(dx) <= halfWidth && Math.abs(dz) <= halfWidth && dy >= 0 && dy < height;
                    if (inPocket && !QueenLifecyclePhaseManager.isDiggable(level, pos)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Moves the hive's centre to the site, clears her pocket and puts her in it. */
    /** Sample spacing across the chamber footprint, in blocks (48 across, so 16 samples a side). */
    private static final int BAND_SAMPLE_XZ = 3;

    /**
     * ⚠⚠ Oct 5 - THE "PLATFORM IN THE MIDDLE OF NOWHERE" BUG, CAUSE 1. {@link #fits} only checked the QUEEN'S POCKET
     * and a one-block shell - a few blocks of room. The chamber she then digs is the 3x3-chunk queen_chamber_3x3, 48
     * blocks across and a full slab ({@link HiveLocation#SLAB_HEIGHT}) tall. A field log (Oct 5, x=-616, an unwitnessed
     * daughter) shows her sited at y=37 under an ocean: her pocket was dry, the chamber was not - the dig opened into
     * the water, the centre flooded, and she was re-sited 45 blocks down, leaving the half-dug core behind as a resin
     * platform. Now the whole chamber band - its 3x3-chunk footprint, from one block under the floor to one above the
     * slab - must be free of liquid.
     * <p>
     * One sweep per search: every {@value #BAND_SAMPLE_XZ}th column of the footprint, every Y in the searched range,
     * folded into a running count so each candidate floor is a subtraction. An unloaded sample is skipped, never
     * loaded.
     * </p>
     */
    private static final class ChamberBand {

        private final int baseY;

        /** wetPrefix[i] = wet samples at Y < baseY + i. */
        private final int[] wetPrefix;

        private ChamberBand(int baseY, int[] wetPrefix) {
            this.baseY = baseY;
            this.wetPrefix = wetPrefix;
        }

        static ChamberBand scan(ServerLevel level, ChunkPos centreChunk, int minFloorY, int maxFloorY) {
            var baseY = minFloorY - 1;
            var topY = maxFloorY + HiveLocation.SLAB_HEIGHT;
            var wetAtY = new int[topY - baseY + 1];
            var minX = (centreChunk.x - 1) << 4;
            var minZ = (centreChunk.z - 1) << 4;
            var pos = new BlockPos.MutableBlockPos();

            for (var x = minX; x < minX + 48; x += BAND_SAMPLE_XZ) {
                for (var z = minZ; z < minZ + 48; z += BAND_SAMPLE_XZ) {
                    if (!level.hasChunk(x >> 4, z >> 4)) {
                        continue;
                    }

                    for (var y = baseY; y <= topY; y++) {
                        if (!level.getFluidState(pos.set(x, y, z)).isEmpty()) {
                            wetAtY[y - baseY]++;
                        }
                    }
                }
            }

            var prefix = new int[wetAtY.length + 1];

            for (var i = 0; i < wetAtY.length; i++) {
                prefix[i + 1] = prefix[i] + wetAtY[i];
            }

            return new ChamberBand(baseY, prefix);
        }

        /** {@return whether a chamber floored at {@code floorY} would have no liquid in its band} */
        boolean isDry(int floorY) {
            var from = Math.max(0, floorY - 1 - baseY);
            var to = Math.min(wetPrefix.length - 1, floorY + HiveLocation.SLAB_HEIGHT - baseY + 1);

            return from >= to || wetPrefix[to] - wetPrefix[from] == 0;
        }
    }

    /**
     * {@return whether this location's founding queen chamber is still unbuilt and commissioned at a floor other than
     * the location's own} The orphaned-core state CAUSE 2 left behind.
     */
    private static boolean hasStaleFoundingCore(HiveLocation location) {
        if (com.alien.common.gameplay.hive.config.BuildFreeMode.isEnabled() || hasBuiltQueenChamber(location)) {
            return false;
        }

        var site = location.activeCarveSite();

        return site != null
            && com.alien.common.gameplay.hive.structure.HivePieceCatalog.isQueenChamber(site.match().piece().id())
            && site.floorY() != location.hiveFloorY();
    }

    /** {@return whether this location's founding queen chamber has been built} */
    private static boolean hasBuiltQueenChamber(HiveLocation location) {
        for (var placement : location.builtPlacements().values()) {
            var id = ResourceLocation.tryParse(placement.pieceId());

            if (id != null && com.alien.common.gameplay.hive.structure.HivePieceCatalog.isQueenChamber(id)) {
                return true;
            }
        }

        return false;
    }

    private static void moveCentreTo(ServerLevel level, Queen queen, HiveLocation location, BlockPos site) {
        var previous = location.centerPos();

        // ⚠⚠ Oct 5 - CAUSE 2. A carve site captures its floor Y when it is commissioned. Moving the centre of a
        // location whose founding core was ALREADY commissioned left that site digging at the OLD depth while the
        // queen was teleported to the new one: she could never work it (it was out of reach), and what had been dug
        // stayed behind as the platform. The unbuilt core is dropped here and commissioned again at the new depth
        // just below. (The chamber chunks do not change - the new site is in the same centre chunk.)
        var recommission = !com.alien.common.gameplay.hive.config.BuildFreeMode.isEnabled()
            && hasQueenChamber(location)
            && !hasBuiltQueenChamber(location)
            && location.hasActiveCarveSite();

        if (recommission) {
            location.setActiveCarveSite(null);
        }

        location.setCenterPos(site);

        var halfWidth = Math.max(1, (int) Math.ceil(queen.getBbWidth() / 2.0));
        var height = Math.max(2, (int) Math.ceil(queen.getBbHeight()));
        for (var dx = -halfWidth; dx <= halfWidth; dx++) {
            for (var dz = -halfWidth; dz <= halfWidth; dz++) {
                for (var dy = 0; dy < height; dy++) {
                    var pos = site.offset(dx, dy, dz);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }

        queen.teleportTo(site.getX() + 0.5, site.getY(), site.getZ() + 0.5);
        queen.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        queen.resetFallDistance();
        Alien.LOGGER.info(
            "Daughter siting: location {} moved from {} to {} (founding rules met)",
            location.id(),
            previous,
            site
        );

        if (recommission) {
            Alien.LOGGER.info(
                "Daughter siting: the unbuilt queen chamber of {} was commissioned at the old depth - commissioning it again at {}",
                location.id(),
                site
            );
            com.alien.common.gameplay.hive.structure.HiveStructureFounding.establishQueenChamber(
                level.getServer(),
                location,
                new ChunkPos(location.centerPos())
            );
        }
    }

    private static void markLineageDirty(HiveLocation location) {
        var faction = Alien.MOD.factions().get(location.lineageFactionId());
        if (faction != null && faction.data() instanceof LineageFactionData lineage) {
            lineage.markDirty();
        }
    }
}
