package com.alien.common.gameplay.hive.structure.carve;

import com.alien.Alien;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.structure.FrontierSocket;
import com.alien.common.gameplay.hive.structure.HivePieceRegistry;
import com.alien.common.gameplay.hive.structure.HiveRouter;
import com.alien.common.gameplay.hive.structure.HiveStructurePlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * The carve tick (construction economy STEP 3, design §2/§4/§5/§7a): advances a hive's active {@link CarveSite} -
 * clumps of the piece's authored air volume clear on a timer, resin fill trails in behind as bounded patch stamps, and
 * completion runs the one full stamp plus the bookkeeping half of placement. Called every loaded-location tick from
 * {@code HiveLocationLoadedTickTask}; self-paces on game-time timers, so most calls are free.
 * <p>
 * This is the confirmed GHOST CARVE: no worker drones exist yet (they are step 5), so the dig and fill advance on the
 * clock alone. The pacing, the outward-from-the-doorway sweep, the clump/patch shapes and the open-roof-until-done
 * behavior are all the final ones; step 5 only adds the drones standing there playing {@code walk dig} and lets their
 * count scale the clock (design §4).
 * <h2>The three moves</h2>
 * <ul>
 * <li><b>Dig step</b> (every {@link #DIG_INTERVAL_TICKS}): take the next unexcavated column in the outward work order
 * and every unexcavated neighbour within {@link #DIG_RADIUS}, and clear their authored-air cells FULL height - the base
 * is "dug", the remainder above "collapses" (design §7a). Only cells in the template's authored air volume are ever
 * touched; structure_void margins aren't in the block list, exactly as the stamp respects them.</li>
 * <li><b>Fill step</b> (every {@link #FILL_INTERVAL_TICKS}, starting once {@link #FILL_START_FRACTION} of the columns
 * are dug): take a patch of dug-but-unfilled columns within {@link #FILL_RADIUS} of the fill front and stamp it from
 * the floor to {@link CarveSite#reachableCeilingY()} via a BOUNDED {@code placeInWorld}
 * ({@code StructurePlaceSettings.setBoundingBox}) - patch fill reuses the exact vanilla placement pipeline, so it can
 * never disagree with the final stamp. The patch shrinks if its rectangle would overlap an undug column, so resin never
 * appears inside solid ground.</li>
 * <li><b>Completion</b> (every column dug AND filled): one FULL unbounded stamp - idempotent, closes the roof (the
 * cells above reach that fill never touched), and catches any straggler the steps missed - then the liquid drain,
 * {@code finalizePlacement} (roles + sockets, making the piece routable), jelly-vat growth, and the site clears.</li>
 * </ul>
 * <h2>Never-wedge rule (design §8, step-3 contract)</h2> Commission consumed the frontier socket, so an abandoned site
 * is a hive that can never grow past this spot. ANY failure therefore resolves the site instead of leaving it: a
 * missing template at hydration falls back to the legacy instant stamp; if even the stamp is impossible (template truly
 * gone), the consumed socket is restored to the frontier so the router can grow something else there. Same on load: if
 * the saved piece id no longer exists in the registry, the socket is restored from the raw tag.
 * <p>
 * On a FLAT/SURFACE build there is little or nothing to dig - excavation completes near-instantly and the show is the
 * resin creep plus the roof pass. A buried build shows the clump-collapse eating through the terrain first. Both are
 * correct.
 */
public final class CarveSiteWork {

    private CarveSiteWork() {}

    /**
     * One dig clump every ~7s. A 1x1 piece (256 columns) at radius {@link #DIG_RADIUS} lands near the §4 ~90s clock.
     */
    public static final int DIG_INTERVAL_TICKS = 140;

    /** One fill patch every ~7s, trailing the dig front. */
    public static final int FILL_INTERVAL_TICKS = 140;

    /** Fill starts once this fraction of the columns is dug (design §5: "once excavation is underway"). */
    public static final float FILL_START_FRACTION = 0.25f;

    /** While a site is unstaffed and paused, say so this often: once a minute. */
    private static final int UNSTAFFED_LOG_INTERVAL_TICKS = 1200;

    /** How many break-effect samples one dig clump plays - crunchy feedback without a machine-gun of sound. */
    private static final int DIG_SOUND_SAMPLES = 6;

    /**
     * The queen's founding-core dig cadence (design §8.8: fixed, tunable - the 90s/-30s digger curve doesn't apply to
     * her). One clump every 5s; on the 3x3-chunk chamber that lands the core carve at a few minutes of visible, solo
     * queen work, overlapping the founding biomass fill she is doing anyway.
     */
    private static final int QUEEN_DIG_INTERVAL_TICKS = 100;

    /**
     * The §8.7 empty-reserve fallback pace: if no drones exist to place the core resin, the QUEEN places it herself,
     * slowly - half a lone placer's rate. Founding can stall for lack of drones but never deadlock.
     */
    private static final int QUEEN_FALLBACK_FILL_INTERVAL_TICKS = FILL_INTERVAL_TICKS * 2;

    /** Dig clump radius in columns (chebyshev) - the ~2-block-radius sphere-erase feel of design §5, columnized. */
    public static final int DIG_RADIUS = 2;

    /** Fill patch radius in columns (chebyshev) - the ~4-block-radius creep of design §5, columnized. */
    public static final int FILL_RADIUS = 4;

    /**
     * Advance {@code location}'s active carve site by one loaded tick: lazily hydrate a site loaded from NBT, then run
     * whichever of the dig / fill / completion moves are due. No active site = immediate return, so this is free for
     * the overwhelming majority of hives and ticks.
     */
    public static void tickActive(MinecraftServer server, ServerLevel level, HiveLocation location) {
        if (!location.hasActiveCarveSite()) {
            return;
        }

        // Lazy NBT hydration (persistence option (a)): the tag was stored at load because rebuilding the PieceMatch
        // needs the piece registry, which needs the server. First loaded tick pays it.
        if (location.activeCarveSite() == null) {
            loadPendingSite(server, location);
        }
        var site = location.activeCarveSite();
        if (site == null) {
            return; // pending tag failed to load; the never-wedge fallback already restored the socket
        }

        // Template hydration: resolve the placement and the authored air volume once. On failure, resolve the site
        // via the legacy stamp (never-wedge) - hydrate() has already cleared it, so just stop.
        if (site.workOrder == null && !hydrate(level, location, site)) {
            return;
        }

        // Only work while the footprint is actually loaded - block writes on unloaded chunks would sync-load them.
        for (ChunkPos chunk : site.match().occupiedChunks()) {
            if (!level.getChunkSource().hasChunk(chunk.x, chunk.z)) {
                return;
            }
        }

        long now = level.getGameTime();

        // Step 5: the crew. Sourcing, release, steering and the dig gait all live in CarveWorkers; what comes back is
        // the ACTIVE staffing, which sets the pace. The world mutation below fires whenever its track is staffed -
        // it does NOT wait for a drone to physically arrive at the front (a stuck drone must never wedge the build;
        // the drones sell the show, the site guarantees the work).
        // One front per digger so the crew spreads across the face instead of stacking on a single column. Purely
        // where they STAND - excavation still consumes the work order in order, so throughput is the cadence below.
        var digFronts = pendingDigFronts(site, CarveWorkers.MAX_DIGGERS + 1);
        var fillFrontKey = nextColumn(site, true);
        var fillFront = fillFrontKey == null
            ? null
            : new BlockPos(fillFrontKey.x(), site.floorY() + 1, fillFrontKey.z());
        var crew = CarveWorkers.tick(level, location, site, digFronts, fillFront);

        // Zero workers = zero progress (confirmed design call §8.1/§8.2 taken to its limit): no free drones and an
        // empty reserve means the hive is in collapse; the build waits, routing waits behind it (§8.4), and the
        // moment the queen's eggs refill the reserve the crew re-sources and work resumes on its own. The FOUNDING
        // CORE is exempt: its digger is the queen (axiomatically present - she founded here) and its fill has the
        // §8.7 queen fallback, so it never pauses for staffing.
        if (!site.isFoundingCore() && crew.unstaffed() && !site.isComplete()) {
            if (now >= site.nextUnstaffedLogTick) {
                site.nextUnstaffedLogTick = now + UNSTAFFED_LOG_INTERVAL_TICKS;
                Alien.LOGGER.info(
                    "Hive at {}: carve site for {} is unstaffed - no free drones, nothing in reserve. Build paused.",
                    location.centerPos(),
                    site.match().piece().id()
                );
            }
            return;
        }

        // Founding core (step 6, design §7b): the QUEEN is the digger, at her fixed §8.8 pace - drone diggers are
        // never sourced for it. Her stand-dig sequence (start -> loop -> stop) tracks the excavation.
        if (site.isFoundingCore()) {
            tickQueenDig(level, location, site, crew.unstaffed());
        }

        var digStaffed = site.isFoundingCore() || crew.diggers() > 0;
        if (digStaffed) {
            if (site.nextDigTick == 0L) {
                site.nextDigTick = now; // first dig fires immediately - visible progress the moment work starts
            }
            if (now >= site.nextDigTick) {
                digStep(level, site);
                site.nextDigTick = now
                    + (site.isFoundingCore() ? coreDigIntervalTicks(crew.diggers()) : digIntervalTicks(crew.diggers()));
            }
        }

        // Fill: normal placer pace when staffed; on the founding core with NO placers, the queen places her own
        // resin slowly (§8.7) - founding stalls for lack of drones, never deadlocks.
        var fillInterval = crew.placers() > 0
            ? FILL_INTERVAL_TICKS / crew.placers()
            : site.isFoundingCore() ? QUEEN_FALLBACK_FILL_INTERVAL_TICKS : 0;
        // Growth pieces trail resin behind the dig front; the FOUNDING CORE resins only after the WHOLE volume is
        // dug (design §7b order: she carves, THEN the eggsack, THEN the resin). Resin creeping in mid-dig put her
        // variant resin under her feet and tripped the eggsack's "already prepared" check while she was still
        // digging - the eggsack-during-excavation bug from testing.
        var fillGateOpen = site.isFoundingCore()
            ? site.isFullyExcavated()
            : site.excavatedFraction() >= FILL_START_FRACTION;
        if (fillInterval > 0 && fillGateOpen) {
            if (site.nextFillTick == 0L) {
                site.nextFillTick = now; // fill starts the moment the gate opens
            }
            if (now >= site.nextFillTick) {
                fillStep(level, location, site);
                site.nextFillTick = now + fillInterval;
            }
        }

        if (site.isComplete()) {
            complete(level, location, site);
        }
    }

    // ---- Hydration ----

    /** Rebuilds the site object from the NBT tag stored at load. Registry-missing piece = restore socket, clear. */
    private static void loadPendingSite(MinecraftServer server, HiveLocation location) {
        var tag = location.pendingCarveSiteTag();
        if (tag == null) {
            return;
        }
        var site = CarveSite.load(tag, HivePieceRegistry.get(server), location);
        if (site == null) {
            // Never-wedge: the saved piece no longer exists (datapack changed under the save). The socket was
            // consumed at commission - restore it from the raw tag so the router can grow something else here. A
            // FOUNDING tag (no socket) instead resolves via the founding legacy fallback, so the chamber and its
            // royal ring exist even though the carve cannot resume.
            if (tag.contains(CarveSite.NBT_SOCKET)) {
                location.frontierSockets().add(FrontierSocket.fromTag(tag.getCompound(CarveSite.NBT_SOCKET)));
            } else {
                var level = server.getLevel(location.dimension());
                if (level != null) {
                    com.alien.common.gameplay.hive.structure.HiveStructureFounding.legacyFoundingFallback(
                        level,
                        location
                    );
                }
            }
            Alien.LOGGER.warn(
                "Hive at {}: saved carve site references a piece missing from the registry - site dropped, socket restored.",
                location.centerPos()
            );
            location.setActiveCarveSite(null);
            return;
        }
        location.setActiveCarveSite(site);
        Alien.LOGGER.info("Hive at {}: resumed {} from save.", location.centerPos(), site.describe());
    }

    /**
     * Resolves the template (once) into the transient tick state: the placement (same resolution the stamp uses), the
     * authored-air cells grouped by column, and the outward-from-the-doorway work order. Columns with no authored air
     * are pre-marked excavated - there is nothing to dig there (void margins, solid wall bases); re-derived after every
     * load, idempotent over the saved sets. Returns false after resolving a hydration failure (site already cleared).
     */
    private static boolean hydrate(ServerLevel level, HiveLocation location, CarveSite site) {
        var resolved = HiveStructurePlacer.resolvePlacement(level, location, site.match());
        if (resolved == null) {
            fallbackInstant(level, location, site, "template not found at hydration");
            return false;
        }

        var airByColumn = new LinkedHashMap<CarveSite.ColumnKey, List<BlockPos>>();
        for (var info : resolved.template().filterBlocks(resolved.placeAt(), resolved.settings(), Blocks.AIR)) {
            var key = new CarveSite.ColumnKey(info.pos().getX(), info.pos().getZ());
            airByColumn.computeIfAbsent(key, k -> new ArrayList<>()).add(info.pos());
        }

        // Sweep order: every footprint column, nearest-to-the-reference first. Growth pieces eat OUTWARD from the
        // connected socket (the build visibly grows away from the existing hive); the FOUNDING CORE has no socket and
        // eats outward from its own center - the queen digs the space around herself first (design §7b).
        int refX;
        int refZ;
        var socket = site.connectedTo();
        if (socket != null) {
            refX = socket.chunk().getMinBlockX() + 8 + socket.facing().getStepX() * 8;
            refZ = socket.chunk().getMinBlockZ() + 8 + socket.facing().getStepZ() * 8;
        } else {
            var chunks = site.match().occupiedChunks();
            long sumX = 0;
            long sumZ = 0;
            for (ChunkPos c : chunks) {
                sumX += c.getMinBlockX() + 8;
                sumZ += c.getMinBlockZ() + 8;
            }
            refX = (int) (sumX / Math.max(1, chunks.size()));
            refZ = (int) (sumZ / Math.max(1, chunks.size()));
        }
        var order = new ArrayList<>(site.columns().keySet());
        order.sort((a, b) -> {
            long da = (long) (a.x() - refX) * (a.x() - refX) + (long) (a.z() - refZ) * (a.z() - refZ);
            long db = (long) (b.x() - refX) * (b.x() - refX) + (long) (b.z() - refZ) * (b.z() - refZ);
            if (da != db) {
                return Long.compare(da, db);
            }
            return a.x() != b.x() ? Integer.compare(a.x(), b.x()) : Integer.compare(a.z(), b.z());
        });

        for (var entry : site.columns().entrySet()) {
            if (!airByColumn.containsKey(entry.getKey())) {
                entry.getValue().markExcavated();
            }
        }

        site.resolved = resolved;
        site.airCellsByColumn = airByColumn;
        site.workOrder = order;
        return true;
    }

    /**
     * THE NEVER-WEDGE FALLBACK: a site that cannot hydrate resolves via the instant stamp (world + bookkeeping in one
     * tick). If even the stamp is impossible - the template is truly gone - the consumed socket is restored so the
     * frontier stays routable. Either way the site clears: a half-carved hole with a consumed socket and no site must
     * never exist.
     */
    private static void fallbackInstant(ServerLevel level, HiveLocation location, CarveSite site, String reason) {
        Alien.LOGGER.warn(
            "Hive at {}: carve site for {} cannot proceed ({}) - falling back to the instant stamp.",
            location.centerPos(),
            site.match().piece().id(),
            reason
        );
        if (site.isFoundingCore()) {
            clearQueenStandDig(level, location);
            // The founding core has no socket to restore and its own legacy path: stamp the chamber + run the
            // founding tail in one call. Founding always produces a functioning chamber.
            com.alien.common.gameplay.hive.structure.HiveStructureFounding.legacyFoundingFallback(level, location);
        } else if (HiveStructurePlacer.place(level, location, site.match(), site.connectedTo())) {
            HiveRouter.growVatsIfJellyChamber(level, location, site.match());
        } else {
            location.frontierSockets().add(site.connectedTo());
            Alien.LOGGER.warn(
                "Hive at {}: fallback stamp also failed for {} - socket restored to the frontier.",
                location.centerPos(),
                site.match().piece().id()
            );
        }
        // Belt-and-braces: hydration failure means no crew was ever sourced, but disbanding an empty roster is free.
        CarveWorkers.disband(level, location, site);
        location.setActiveCarveSite(null);
    }

    // ---- The dig step (design §5 diggers / §7a column collapse) ----

    /**
     * Clears one clump: the next unexcavated column in the work order plus its unexcavated neighbours within
     * {@link #DIG_RADIUS}, authored-air cells removed FULL height - base dug, remainder collapsed.
     */
    /** Safety net: whatever ends a founding site also lowers her dig flag, so the loop can never stick. */
    private static void clearQueenStandDig(ServerLevel level, HiveLocation location) {
        var founderId = location.founderId();
        if (
            founderId != null
                && level.getEntity(
                    founderId
                ) instanceof com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen queen
                && queen.standDiggingSynced.get()
        ) {
            queen.standDiggingSynced.set(false);
        }
    }

    /**
     * Keeps the queen's synced stand-dig flag matched to the core's excavation state: TRUE while columns remain, FALSE
     * once the volume is fully dug. The client-side QueenAnimator turns the flag's edges into the digStandStart /
     * standDigging / digStandStop triptych - animation dispatch only works client-side (the AzCommand server warning),
     * so the server's whole job is this one boolean. She is resolved via the location's founder id; if she is dead or
     * unloaded the carve continues without her show (never-wedge - founding must not hinge on an animation).
     */
    private static void tickQueenDig(ServerLevel level, HiveLocation location, CarveSite site, boolean unstaffed) {
        var founderId = location.founderId();
        if (founderId == null) {
            return;
        }
        if (
            !(level.getEntity(
                founderId
            ) instanceof com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen queen)
                || !queen.isAlive()
        ) {
            return;
        }
        var shouldDig = !site.isFullyExcavated();
        if (queen.standDiggingSynced.get() != shouldDig) {
            queen.standDiggingSynced.set(shouldDig);
        }

        if (shouldDig) {
            bootstrapFoundingCrew(location, queen, unstaffed);
            recoverStuckDigger(level, location, site, queen);
        } else {
            STUCK_TICKS.remove(location.id());
            LAST_DIGGER_POS.remove(location.id());
        }
    }

    /** Locations already handed a founding crew this session. Transient - see the zero-worker gate below. */
    private static final java.util.Set<com.alien.common.gameplay.hive.id.HiveLocationId> CREW_BOOTSTRAPPED =
        new java.util.HashSet<>();

    /**
     * Give the founding queen her first workers while she digs her core.
     * <p>
     * [stated] "can we have it they appear when she is founding or in the process of digging out her chamber." Here
     * rather than at her waking, because by now her claim EXISTS - drones spawned into a claimed chunk are auto-joined
     * as members by finalizeSpawn, so they are her hive's workers immediately instead of unaffiliated strays that have
     * to be adopted later.
     * <p>
     * The zero-worker gate is what makes this safe to run every tick: it fires only when the crew came back UNSTAFFED,
     * so a hive that already has hands never gets more. The session set on top means killing the crew cannot be farmed
     * for an endless supply while the core is still open. Being transient is deliberate - a restart mid-founding SHOULD
     * be able to re-bootstrap a hive that still has nobody, which is exactly the "carve site is unstaffed - no free
     * drones, nothing in reserve" deadlock this prevents.
     */
    private static void bootstrapFoundingCrew(
        HiveLocation location,
        com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen queen,
        boolean unstaffed
    ) {
        if (!unstaffed || !CREW_BOOTSTRAPPED.add(location.id())) {
            return;
        }

        queen.spawnFoundingCrew();
        Alien.LOGGER.info("Hive at {}: founding queen had no workers - spawned her founding crew.", location.centerPos());
    }

    /** Last observed position of each founding digger, for the stuck check below. Transient by design. */
    private static final java.util.Map<com.alien.common.gameplay.hive.id.HiveLocationId, net.minecraft.world.phys.Vec3> LAST_DIGGER_POS =
        new java.util.HashMap<>();

    private static final java.util.Map<com.alien.common.gameplay.hive.id.HiveLocationId, Integer> STUCK_TICKS =
        new java.util.HashMap<>();

    /** Below this, she has not meaningfully moved since the last tick. */
    private static final double STUCK_MOVE_EPSILON = 0.05;

    /** She belongs AT the core she is digging; beyond this she is not merely standing still, she is lost. */
    private static final double AT_SITE_DISTANCE = 8.0;

    /** Ten seconds of no movement while stranded. Long enough that a brief snag never teleports her. */
    private static final int STUCK_TICKS_BEFORE_WARP = 200;

    /**
     * Warp a founding queen back to her core if she gets stranded while digging it.
     * <p>
     * [stated] "can we have while carving if the queen gets stuck she warps back to center... she digs out her core
     * chamber im watching her do it." The excavation itself is a GHOST CARVE on a timer, so it completes whether or not
     * she is present - which is exactly why being stuck goes unnoticed: the chamber finishes while she stands in a cave
     * somewhere, and nothing in the pipeline was watching her position.
     * <p>
     * STANDING STILL IS NOT THE TEST. Digging is standing still - that is the whole animation. The test is standing
     * still while FAR FROM the core: within {@link #AT_SITE_DISTANCE} she is working, beyond it she is stranded. Both
     * conditions must hold for {@link #STUCK_TICKS_BEFORE_WARP} so a snag on terrain never teleports her.
     */
    private static void recoverStuckDigger(
        ServerLevel level,
        HiveLocation location,
        CarveSite site,
        com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen queen
    ) {
        var core = site.match().originChunk().getMiddleBlockPosition(location.hiveFloorY());
        var atSite = queen.position().closerThan(net.minecraft.world.phys.Vec3.atCenterOf(core), AT_SITE_DISTANCE);
        var previous = LAST_DIGGER_POS.put(location.id(), queen.position());

        if (atSite || previous == null || previous.distanceTo(queen.position()) > STUCK_MOVE_EPSILON) {
            STUCK_TICKS.remove(location.id());
            return;
        }

        var stuckFor = STUCK_TICKS.merge(location.id(), 1, Integer::sum);
        if (stuckFor < STUCK_TICKS_BEFORE_WARP) {
            return;
        }

        STUCK_TICKS.remove(location.id());
        LAST_DIGGER_POS.remove(location.id());
        queen.teleportTo(core.getX() + 0.5, core.getY(), core.getZ() + 0.5);
        queen.getNavigation().stop();
        Alien.LOGGER.info(
            "Hive at {}: founding queen was stranded {} blocks from her core for {} ticks - warped back to {}.",
            location.centerPos(),
            (int) queen.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(core)),
            stuckFor,
            core
        );
    }

    /**
     * The dig cadence for a given ACTIVE digger count, mapping the design §4 clock (90s / 60s / 30s for 1 / 2 / 3
     * diggers on a 1x1 piece) onto the step interval: the 1-digger interval is exactly the step-3 ghost cadence, so one
     * drone digs at the pace the ghost carve was tuned to.
     */
    /**
     * The founding core's dig cadence. The QUEEN is always a digger there and is not on the crew roster, so she is the
     * implicit first pair of claws and every drone that joins her divides the interval further. Capped at the same crew
     * ceiling the growth pieces use, so the core tops out at queen + {@link CarveWorkers#MAX_DIGGERS}.
     * <p>
     * At her old solo pace that is 100 ticks; with a full crew it is 25. Her stand-dig sequence is unchanged - she
     * still visibly excavates, the crew just stops watching her do it.
     */
    private static int coreDigIntervalTicks(int helperDiggers) {
        var claws = 1 + Math.min(helperDiggers, CarveWorkers.MAX_DIGGERS);
        return Math.max(1, QUEEN_DIG_INTERVAL_TICKS / claws);
    }

    /** Up to {@code limit} columns still needing excavation, in work order - one standing spot per digger. */
    private static java.util.List<BlockPos> pendingDigFronts(CarveSite site, int limit) {
        var fronts = new java.util.ArrayList<BlockPos>(limit);
        for (CarveSite.ColumnKey key : site.workOrder) {
            var column = site.columns().get(key);
            if (column == null || column.isExcavated()) {
                continue;
            }
            fronts.add(new BlockPos(key.x(), site.floorY() + 1, key.z()));
            if (fronts.size() >= limit) {
                break;
            }
        }
        return fronts;
    }

    private static int digIntervalTicks(int diggers) {
        var d = Math.min(diggers, CarveWorkers.MAX_DIGGERS);
        return (int) Math.round(DIG_INTERVAL_TICKS * (90.0 - 30.0 * (d - 1)) / 90.0);
    }

    private static void digStep(ServerLevel level, CarveSite site) {
        var center = nextColumn(site, false);
        if (center == null) {
            return; // excavation done; fill is still catching up
        }
        var cleared = 0;
        var samplePositions = new ArrayList<BlockPos>(DIG_SOUND_SAMPLES);
        var sampleStates = new ArrayList<BlockState>(DIG_SOUND_SAMPLES);
        var pos = new BlockPos.MutableBlockPos();
        // Shared scratch for the leak check - a dig step clears hundreds of cells, so this must not allocate per cell.
        var neighbourScratch = new BlockPos.MutableBlockPos();
        for (CarveSite.ColumnKey key : site.workOrder) {
            if (cheby(key, center) > DIG_RADIUS) {
                continue;
            }
            var column = site.columns().get(key);
            if (column == null || column.isExcavated()) {
                continue;
            }
            var cells = site.airCellsByColumn.get(key);
            if (cells != null) {
                for (BlockPos cell : cells) {
                    pos.set(cell);
                    var state = level.getBlockState(pos);
                    if (!state.isAir()) {
                        // Sample a spread of the blocks actually broken for the vanilla break effect (sound +
                        // particles of THAT block) - the "block breaking sounds" of a dig, without playing hundreds.
                        if (samplePositions.size() < DIG_SOUND_SAMPLES && cleared % 8 == 0) {
                            samplePositions.add(cell.immutable());
                            sampleStates.add(state);
                        }
                        cleared++;
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        sealLiquidNeighbours(level, pos, neighbourScratch);
                    }
                }
            }
            column.markExcavated();
        }
        for (var i = 0; i < samplePositions.size(); i++) {
            level.levelEvent(2001, samplePositions.get(i), Block.getId(sampleStates.get(i)));
        }
    }

    /**
     * Plugs any liquid that the cell we just opened is now exposed to.
     * <p>
     * Excavation only ran {@code setBlock(AIR)} and moved on, so the moment a dig broke into a lava or water pocket the
     * reservoir simply poured into the workings. Nothing addressed it until the piece FINISHED - the completion drain
     * and the upkeep pass both act on completed pieces - which left an actively-carved site flooded for as long as the
     * dig took, killing workers and blocking the routes through it.
     * <p>
     * Sealing the face rather than deleting the fluid keeps the reservoir where it is instead of silently draining a
     * lava lake through the hive. The plug copies the block underneath the fluid where possible, so a pocket in
     * deepslate is sealed with deepslate rather than an obvious patch of stone. If the plug happens to sit on a cell
     * this piece will carve later, that later pass simply removes it again - correct either way.
     */
    private static void sealLiquidNeighbours(
        ServerLevel level,
        BlockPos.MutableBlockPos openedCell,
        BlockPos.MutableBlockPos neighbour
    ) {
        for (Direction direction : Direction.values()) {
            neighbour.set(openedCell).move(direction);
            if (level.getFluidState(neighbour).isEmpty()) {
                continue;
            }
            level.setBlock(neighbour.immutable(), plugFor(level, neighbour), 2);
        }
    }

    /**
     * A sealing block that blends with the surrounding rock: the block below the leak where it can be copied, else a
     * dimension-appropriate fallback - BASALT in the nether ([stated] "block up lava pouring in with basalt instead of
     * stone"; a lava-ocean leak usually has more lava below it, so the fallback is what shows), plain stone everywhere
     * else.
     */
    private static BlockState plugFor(ServerLevel level, BlockPos leak) {
        var belowPos = leak.below();
        var below = level.getBlockState(belowPos);
        if (below.isSolidRender(level, belowPos) && level.getFluidState(belowPos).isEmpty()) {
            return below;
        }
        if (level.dimension() == net.minecraft.world.level.Level.NETHER) {
            return Blocks.BASALT.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }

    // ---- The fill step (design §5 placers / §7a reachable fill) ----

    /**
     * Stamps one patch: the dug-but-unfilled columns within {@link #FILL_RADIUS} of the fill front, floor to
     * {@link CarveSite#reachableCeilingY()}, via a bounded {@code placeInWorld}. The bounding rectangle of columns
     * within chebyshev radius r of the front is itself within that radius, so every dug-unfilled column inside the
     * rectangle is in the selection; if an UNDUG column with pending air work falls inside the rectangle, the radius
     * shrinks (down to the single front column, which is always safe) so resin never stamps into unexcavated ground.
     */
    private static void fillStep(ServerLevel level, HiveLocation location, CarveSite site) {
        var center = nextColumn(site, true);
        if (center == null) {
            return; // nothing dug-and-unfilled right now; fill waits on the diggers
        }
        for (int radius = FILL_RADIUS; radius >= 0; radius--) {
            var selected = new ArrayList<CarveSite.ColumnKey>();
            int minX = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (CarveSite.ColumnKey key : site.workOrder) {
                if (cheby(key, center) > radius) {
                    continue;
                }
                var column = site.columns().get(key);
                if (column == null || !column.isExcavated() || column.isFilled()) {
                    continue;
                }
                selected.add(key);
                minX = Math.min(minX, key.x());
                minZ = Math.min(minZ, key.z());
                maxX = Math.max(maxX, key.x());
                maxZ = Math.max(maxZ, key.z());
            }
            if (selected.isEmpty()) {
                return;
            }
            if (radius > 0 && rectangleTouchesUndug(site, minX, minZ, maxX, maxZ)) {
                continue; // shrink: the patch rectangle would stamp resin into ground nobody has dug yet
            }

            // Pay-as-you-fill (design §6, step 4): this patch's share of the resin debt, priced at what's still owed
            // spread over what's still unfilled - integer-exact and self-correcting, nothing extra persisted. A
            // bounced payment freezes the FILL only (digging is free and keeps going) and flags factual starvation;
            // the next fill tick re-tries, so funds arriving resume the resin silently.
            var cost = patchCost(site, selected.size());
            if (cost > location.biomass()) {
                if (!site.starved) {
                    site.starved = true;
                    Alien.LOGGER.info(
                        "Hive at {}: carve starved - {} owed, {} in reserve. Resin frozen; expansion and caste"
                            + " purchases stand aside until this build is funded.",
                        location.centerPos(),
                        site.resinBiomassOwed(),
                        location.biomass()
                    );
                }
                return;
            }
            location.setBiomass(location.biomass() - cost);
            site.payResin(cost);
            site.starved = false;

            var patch = new BoundingBox(minX, site.floorY(), minZ, maxX, site.reachableCeilingY(), maxZ);
            HiveStructurePlacer.placeWorldPatch(level, site.resolved, patch);
            for (CarveSite.ColumnKey key : selected) {
                site.columns().get(key).markFilled();
            }
            // The resin creeping in sounds like the resin creeping in: the same spread sound the veins and node
            // cursors play (sculk-spread under the hood), once per patch at its center.
            level.playSound(
                null,
                new BlockPos((minX + maxX) / 2, site.floorY() + 1, (minZ + maxZ) / 2),
                com.alien.common.registry.init.AlienSoundEvents.BLOCK_RESIN_SPREAD.get(),
                SoundSource.BLOCKS,
                1.0F,
                1.0F
            );
            return;
        }
    }

    /**
     * The biomass price of resining {@code patchColumns} columns: remaining debt divided over remaining unfilled
     * columns, rounded up, clamped to the debt. Recomputing the per-column price from the two persisted numbers every
     * time means rounding can never strand a remainder and a mid-build reload re-derives the exact same schedule.
     */
    private static int patchCost(CarveSite site, int patchColumns) {
        var owed = site.resinBiomassOwed();
        if (owed <= 0) {
            return 0;
        }
        var unfilled = Math.max(1, site.unfilledColumnCount());
        var perColumn = (owed + unfilled - 1) / unfilled; // ceil division
        return Math.min(owed, perColumn * patchColumns);
    }

    /** True if any column inside the rectangle still has authored air to dig - the patch would jump the dig front. */
    private static boolean rectangleTouchesUndug(CarveSite site, int minX, int minZ, int maxX, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                var key = new CarveSite.ColumnKey(x, z);
                var column = site.columns().get(key);
                if (column != null && !column.isExcavated() && site.airCellsByColumn.containsKey(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---- Completion (design §2: finalizePlacement runs once, whichever way the world got built) ----

    /**
     * Every column dug and filled: one full unbounded stamp (idempotent; closes the roof above reach height, catches
     * stragglers, then drains liquids), the bookkeeping half (roles + sockets - the piece becomes routable ONLY now,
     * design §8.3), jelly-vat growth for the chamber types that grow them, and the site clears.
     */
    private static void complete(ServerLevel level, HiveLocation location, CarveSite site) {
        // Design §6: "the piece is only finished when this is fully paid." Rounding leaves at most a few units of
        // residue; settle it here. Can't pay = the roof pass waits, starved, exactly like a frozen fill patch.
        var residue = site.resinBiomassOwed();
        if (residue > 0) {
            if (residue > location.biomass()) {
                if (!site.starved) {
                    site.starved = true;
                    Alien.LOGGER.info(
                        "Hive at {}: carve starved at the finish - {} still owed. Roof pass waits for funds.",
                        location.centerPos(),
                        residue
                    );
                }
                return;
            }
            location.setBiomass(location.biomass() - residue);
            site.payResin(residue);
        }
        site.starved = false;

        if (!HiveStructurePlacer.finishWorld(level, location, site.match())) {
            // Should be impossible (the template hydrated this session), but a consumed socket must never wedge:
            // finalize anyway - the piece is progressively built minus the roof pass, and the frontier stays alive.
            Alien.LOGGER.warn(
                "Hive at {}: completion stamp failed for {} - finalizing with the progressive build as-is.",
                location.centerPos(),
                site.match().piece().id()
            );
        }
        if (site.isFoundingCore()) {
            clearQueenStandDig(level, location);
            // Founding tail (design §7b step 5): royal ring + open-socket registration + blueprint, deferred from
            // establishQueenChamber to HERE so routing could not grow halls off an uncarved chamber.
            com.alien.common.gameplay.hive.structure.HiveStructureFounding.finishFoundingStructure(
                level,
                location,
                false
            );
        } else {
            HiveStructurePlacer.finalizePlacement(level, location, site.match(), site.connectedTo());
            HiveRouter.growVatsIfJellyChamber(level, location, site.match());
        }
        // Step 5: dismiss the crew - materialized workers fold back into reserves, borrowed drones go back to
        // whatever the hive wants of them.
        CarveWorkers.disband(level, location, site);
        location.setActiveCarveSite(null);
        Alien.LOGGER.info("Hive at {}: carve complete - {}", location.centerPos(), site.describe());
    }

    // ---- Helpers ----

    /**
     * The build front: the first column in the outward work order that still needs the given kind of work - for dig,
     * the first unexcavated column; for fill, the first dug-but-unfilled one. Null when that track has nothing to do.
     */
    private static @Nullable CarveSite.ColumnKey nextColumn(CarveSite site, boolean fillFront) {
        for (CarveSite.ColumnKey key : site.workOrder) {
            var column = site.columns().get(key);
            if (column == null) {
                continue;
            }
            if (fillFront ? column.isExcavated() && !column.isFilled() : !column.isExcavated()) {
                return key;
            }
        }
        return null;
    }

    private static int cheby(CarveSite.ColumnKey a, CarveSite.ColumnKey b) {
        return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.z() - b.z()));
    }
}
