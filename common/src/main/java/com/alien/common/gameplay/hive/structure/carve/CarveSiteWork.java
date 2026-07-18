package com.alien.common.gameplay.hive.structure.carve;

import com.alien.Alien;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.structure.FrontierSocket;
import com.alien.common.gameplay.hive.structure.HivePieceRegistry;
import com.alien.common.gameplay.hive.structure.HiveRouter;
import com.alien.common.gameplay.hive.structure.HiveStructurePlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
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

        if (site.nextDigTick == 0L) {
            site.nextDigTick = now; // first dig fires immediately - visible progress the moment work starts
        }
        if (now >= site.nextDigTick) {
            digStep(level, site);
            site.nextDigTick = now + DIG_INTERVAL_TICKS;
        }

        if (site.excavatedFraction() >= FILL_START_FRACTION) {
            if (site.nextFillTick == 0L) {
                site.nextFillTick = now; // fill starts the moment the gate opens
            }
            if (now >= site.nextFillTick) {
                fillStep(level, location, site);
                site.nextFillTick = now + FILL_INTERVAL_TICKS;
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
            // consumed at commission - restore it from the raw tag so the router can grow something else here.
            if (tag.contains(CarveSite.NBT_SOCKET)) {
                location.frontierSockets().add(FrontierSocket.fromTag(tag.getCompound(CarveSite.NBT_SOCKET)));
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

        // Sweep order: every footprint column, nearest-to-the-doorway first, so both dig and fill eat OUTWARD from
        // the connected socket - the build visibly grows away from the existing hive rather than popping randomly.
        var socket = site.connectedTo();
        int refX = socket.chunk().getMinBlockX() + 8 + socket.facing().getStepX() * 8;
        int refZ = socket.chunk().getMinBlockZ() + 8 + socket.facing().getStepZ() * 8;
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
     * THE NEVER-WEDGE FALLBACK: a site that cannot hydrate resolves via the legacy instant stamp (world + bookkeeping
     * in one tick, exactly the {@code CARVE_ENABLED=false} path). If even the stamp is impossible - the template is
     * truly gone - the consumed socket is restored so the frontier stays routable. Either way the site clears: a
     * half-carved hole with a consumed socket and no site must never exist.
     */
    private static void fallbackInstant(ServerLevel level, HiveLocation location, CarveSite site, String reason) {
        Alien.LOGGER.warn(
            "Hive at {}: carve site for {} cannot proceed ({}) - falling back to the instant stamp.",
            location.centerPos(),
            site.match().piece().id(),
            reason
        );
        if (HiveStructurePlacer.place(level, location, site.match(), site.connectedTo())) {
            HiveRouter.growVatsIfJellyChamber(level, location, site.match());
        } else {
            location.frontierSockets().add(site.connectedTo());
            Alien.LOGGER.warn(
                "Hive at {}: fallback stamp also failed for {} - socket restored to the frontier.",
                location.centerPos(),
                site.match().piece().id()
            );
        }
        location.setActiveCarveSite(null);
    }

    // ---- The dig step (design §5 diggers / §7a column collapse) ----

    /**
     * Clears one clump: the next unexcavated column in the work order plus its unexcavated neighbours within
     * {@link #DIG_RADIUS}, authored-air cells removed FULL height - base dug, remainder collapsed.
     */
    private static void digStep(ServerLevel level, CarveSite site) {
        var center = nextColumn(site, false);
        if (center == null) {
            return; // excavation done; fill is still catching up
        }
        var pos = new BlockPos.MutableBlockPos();
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
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            column.markExcavated();
        }
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
        HiveStructurePlacer.finalizePlacement(level, location, site.match(), site.connectedTo());
        HiveRouter.growVatsIfJellyChamber(level, location, site.match());
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
