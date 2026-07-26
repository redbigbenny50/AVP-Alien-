package com.alien.common.gameplay.hive.structure.carve;

import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.structure.FrontierSocket;
import com.alien.common.gameplay.hive.structure.HivePieceRegistry;
import com.alien.common.gameplay.hive.structure.HiveStructurePlacer;
import com.alien.common.gameplay.hive.structure.PieceMatch;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * An in-progress hive build - one structure piece being physically dug out and resined, instead of instant-stamped.
 * <p>
 * STEP 3 of the construction economy (see {@code AVP_Hive_Construction_Economy_Design.md}): the site now really ticks.
 * {@code HiveRouter.commission} creates it (consuming the frontier socket, registering nothing), and
 * {@link CarveSiteWork} advances it on the loaded-location tick - clumps of the piece's authored air volume clear on a
 * timer, resin trails in behind as bounded patch stamps, and completion runs the one full stamp + the bookkeeping half
 * ({@code finalizePlacement}). Worker drones (the dispatch, the {@code walk dig} animation) are step 5; until then this
 * is the confirmed "ghost carve" - the same pacing and visuals with nobody visibly digging.
 * <h2>Why columns, not cells (design §7a - the column-collapse model)</h2> A hive piece fills a 16-block-tall slab. A
 * floor-standing drone or queen can only reach the lower part of a column; the ceiling and upper walls are out of
 * reach, and wall-crawling does not exist. So the UNIT OF WORK is the vertical {@code (x,z)} column, not the individual
 * block:
 * <ul>
 * <li><b>Excavation:</b> a digger clears the reachable base of a column; the unreachable remainder above "collapses"
 * (implied to have fallen as the material was dug out from under it). The whole column is then excavated.</li>
 * <li><b>Resin fill:</b> a placer fills the reachable base with the authored resin; once every reachable area of the
 * piece is filled, the ceiling / upper blocks "fill in" as a final pass (implied to be the drones' finishing
 * work).</li>
 * </ul>
 * So each column tracks two independent booleans - excavated and filled - and the piece is done when every column is
 * both. This is both the right fiction and simpler bookkeeping than per-cell.
 * <h2>The two sets (design §2, §5)</h2> The real stamp ({@code HiveStructurePlacer}) does two things: it CLEARS the
 * piece's air volume, then PLACES the template's authored blocks. Those map to the site's two concerns:
 * <ul>
 * <li><b>Excavation volume</b> = the template's authored AIR cells, resolved once at hydration via
 * {@code filterBlocks(placeAt, settings, Blocks.AIR)} - exactly the cells the stamp would clear, in world coordinates.
 * structure_void margins are not in the template's block list, so they are never touched, exactly as the stamp respects
 * them today.</li>
 * <li><b>Resin cells</b> = everything else the template authors. The carve tick never enumerates these per cell: fill
 * is a BOUNDED {@code placeInWorld} over a patch of dug columns (floor to reachable height), so patch fill reuses the
 * exact vanilla placement pipeline and can never disagree with the final stamp.</li>
 * </ul>
 * <h2>Persistence (step 3, option (a))</h2> The site saves to NBT on its owning {@link HiveLocation}: piece id,
 * rotation, origin chunk, the consumed socket, the slab band, and the dug/filled column sets. Everything derived from
 * the template (the resolved placement, the air-cell map, the work order) plus the step timers is TRANSIENT - re-built
 * by {@link CarveSiteWork} on the first tick after load. Without this, a save mid-build meant a wedged hive: the socket
 * consumed, the site gone, and the router unable to ever grow past the half-carved hole.
 * <h2>Staffing (design §8.1) - recorded here, dispatched later</h2> Up to 3 diggers and 2 placers, sourced loaded-first
 * then from reserves. The slots are held here; the dispatch and the loaded-vs-reserve sourcing land in step 5.
 */
public final class CarveSite {

    /** Max workers per role (design §5). */
    public static final int MAX_DIGGERS = 3;

    public static final int MAX_PLACERS = 2;

    /**
     * How many blocks above the floor a floor-standing worker can work before the rest of the column collapses (dig) or
     * waits for the roof pass (fill). Design §7a / §8.9 - a tunable constant; 4 is the starting value.
     */
    public static final int REACH_HEIGHT = 4;

    // ---- NBT keys (saved on the owning HiveLocation under its ActiveCarveSite tag) ----

    private static final String NBT_PIECE = "Piece";

    private static final String NBT_ROTATION = "Rotation";

    private static final String NBT_ORIGIN_X = "OriginX";

    private static final String NBT_ORIGIN_Z = "OriginZ";

    /** Public: the never-wedge fallback reads the socket straight off a raw tag when the piece itself won't load. */
    public static final String NBT_SOCKET = "Socket";

    private static final String NBT_FLOOR_Y = "FloorY";

    private static final String NBT_CEILING_Y = "CeilingY";

    private static final String NBT_RESIN_OWED = "ResinOwed";

    private static final String NBT_DUG_COLUMNS = "DugColumns";

    private static final String NBT_FILLED_COLUMNS = "FilledColumns";

    /** Per-column build state: has the column been dug out, and has its resin been placed. */
    public static final class ColumnProgress {

        private boolean excavated;

        private boolean filled;

        public boolean isExcavated() {
            return excavated;
        }

        public boolean isFilled() {
            return filled;
        }

        public void markExcavated() {
            this.excavated = true;
        }

        public void markFilled() {
            this.filled = true;
        }

        /** A column is complete only when both dug and resined. */
        public boolean isComplete() {
            return excavated && filled;
        }
    }

    // ---- What/where: captured verbatim from the router, unchanged (design §3) ----

    private final PieceMatch match;

    private final @Nullable FrontierSocket connectedTo;

    /** The slab band this piece occupies vertically: [floorY, ceilingY). Captured at commission from the hive. */
    private final int floorY;

    private final int ceilingY;

    // ---- Progress: one entry per (x,z) column of the footprint (design §7a) ----

    private final Map<ColumnKey, ColumnProgress> columns;

    /** Resin biomass still owed for this piece (design §6). Set when the economy wires in (step 4); 0 until then. */
    private int resinBiomassOwed;

    // ---- Transient carve-tick state (step 3). NEVER persisted; re-derived by CarveSiteWork.hydrate on the first
    // tick after commission or load. Package-private on purpose: only CarveSiteWork (same package) drives these. ----

    /**
     * The resolved template + placement offset + settings - the same resolution the stamp uses. Null until hydrated.
     */
    @Nullable
    HiveStructurePlacer.ResolvedPlacement resolved;

    /**
     * The template's authored AIR cells (world coords), grouped by the (x,z) column they fall in. Null until hydrated.
     */
    @Nullable
    Map<ColumnKey, List<BlockPos>> airCellsByColumn;

    /**
     * Every footprint column, ordered OUTWARD from the connected socket - the dig/fill sweep order. Null until
     * hydrated.
     */
    @Nullable
    List<ColumnKey> workOrder;

    /** Game time of the next dig step / fill step. 0 = not yet scheduled (first tick schedules them). */
    long nextDigTick;

    long nextFillTick;

    /**
     * FACTUAL starvation (design §6, step 4): true only after a fill patch actually failed to pay - not a prediction
     * about a low balance. Set by CarveSiteWork when a payment bounces, cleared the moment one succeeds (or the site
     * completes). Transient on purpose: a reload re-attempts the fill within seconds and re-derives the truth.
     */
    boolean starved;

    /**
     * The live crew (step 5): worker UUID -> role, insertion-ordered. Transient like the timers - workers are
     * re-sourced after a reload rather than persisted, per design §8.2 ("a build is never lost, only slowed"). Managed
     * exclusively by {@link CarveWorkers}.
     */
    final Map<UUID, CarveWorkers.Role> workers = new LinkedHashMap<>();

    /**
     * The subset of {@link #workers} that was materialized from reserves (vs borrowed loaded drones). These fold back
     * into reserves at completion, and are exempt from the working-adult member cap while assigned (design §8.5).
     */
    final Set<UUID> materializedWorkers = new HashSet<>();

    /**
     * Founding-core animation state (step 6): whether the queen's stand-dig sequence has started (digStandStart fired)
     * and whether it has been stopped (digStandStop fired after full excavation). Transient - a reload mid-dig just
     * restarts the loop.
     */
    boolean queenDigStarted;

    boolean queenDigStopped;

    /** Game time of the next crew top-up / steering pass, and of the next unstaffed warning. 0 = immediately. */
    long nextCrewTick;

    long nextUnstaffedLogTick;

    /**
     * Build a site for a piece the router has chosen. Enumerates the footprint into per-column progress trackers, all
     * starting un-excavated and un-filled. No world reads, no block changes - pure setup.
     *
     * @param match       the piece + rotation + origin, straight from the router
     * @param connectedTo the frontier socket this piece attaches to (needed at completion, not before). NULL marks the
     *                    FOUNDING CORE (step 6): the queen chamber has no upstream doorway - the queen digs it around
     *                    herself, ordered outward from its center, and completion runs the founding tail (royal ring +
     *                    socket registration) instead of {@code finalizePlacement}.
     * @param location    the hive (only to read its floor/ceiling band - not mutated)
     */
    public CarveSite(PieceMatch match, @Nullable FrontierSocket connectedTo, HiveLocation location) {
        this(match, connectedTo, location.hiveFloorY(), location.hiveCeilingY());
    }

    /** Shared by the commission path and the NBT loader: same setup, the band passed explicitly. */
    private CarveSite(PieceMatch match, @Nullable FrontierSocket connectedTo, int floorY, int ceilingY) {
        this.match = match;
        this.connectedTo = connectedTo;
        this.floorY = floorY;
        this.ceilingY = ceilingY;
        this.columns = new LinkedHashMap<>();
        seedColumns();
    }

    /**
     * One ColumnProgress per block column across every occupied chunk (16x16 columns per chunk), all fresh. The NBT
     * loader RE-APPLIES the saved dug/filled sets on top of this re-seeding, so progress survives a save mid-build.
     */
    private void seedColumns() {
        for (ChunkPos chunk : match.occupiedChunks()) {
            int minX = chunk.getMinBlockX();
            int minZ = chunk.getMinBlockZ();
            for (int x = minX; x < minX + 16; x++) {
                for (int z = minZ; z < minZ + 16; z++) {
                    columns.put(new ColumnKey(x, z), new ColumnProgress());
                }
            }
        }
    }

    // ---- Persistence (design §3 / step-3 option (a)) ----

    /** Serializes what/where + per-column progress. Transient tick state (template data, timers) is NOT saved. */
    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString(NBT_PIECE, match.piece().id().toString());
        tag.putString(NBT_ROTATION, match.rotation().name());
        tag.putInt(NBT_ORIGIN_X, match.originChunk().x);
        tag.putInt(NBT_ORIGIN_Z, match.originChunk().z);
        if (connectedTo != null) {
            tag.put(NBT_SOCKET, connectedTo.toTag());
        }
        tag.putInt(NBT_FLOOR_Y, floorY);
        tag.putInt(NBT_CEILING_Y, ceilingY);
        if (resinBiomassOwed > 0) {
            tag.putInt(NBT_RESIN_OWED, resinBiomassOwed);
        }
        var dug = new ArrayList<Long>();
        var filled = new ArrayList<Long>();
        for (var entry : columns.entrySet()) {
            long packed = pack(entry.getKey());
            if (entry.getValue().isExcavated()) {
                dug.add(packed);
            }
            if (entry.getValue().isFilled()) {
                filled.add(packed);
            }
        }
        tag.putLongArray(NBT_DUG_COLUMNS, dug);
        tag.putLongArray(NBT_FILLED_COLUMNS, filled);
        return tag;
    }

    /**
     * Rebuilds a site from NBT: piece looked up in the registry by id, columns re-seeded, then the saved dug/filled
     * sets re-applied. Returns null if the piece no longer exists in the registry (datapack changed under the save) -
     * the caller MUST then apply the never-wedge fallback (restore the consumed socket) rather than dropping the tag
     * silently.
     */
    public static @Nullable CarveSite load(CompoundTag tag, HivePieceRegistry registry, HiveLocation location) {
        var pieceId = ResourceLocation.parse(tag.getString(NBT_PIECE));
        var piece = registry.get(pieceId);
        if (piece == null) {
            return null;
        }
        Rotation rotation;
        try {
            rotation = Rotation.valueOf(tag.getString(NBT_ROTATION));
        } catch (IllegalArgumentException e) {
            return null;
        }
        var origin = new ChunkPos(tag.getInt(NBT_ORIGIN_X), tag.getInt(NBT_ORIGIN_Z));
        var socket = tag.contains(NBT_SOCKET) ? FrontierSocket.fromTag(tag.getCompound(NBT_SOCKET)) : null;
        int floor = tag.contains(NBT_FLOOR_Y) ? tag.getInt(NBT_FLOOR_Y) : location.hiveFloorY();
        int ceiling = tag.contains(NBT_CEILING_Y) ? tag.getInt(NBT_CEILING_Y) : location.hiveCeilingY();
        var site = new CarveSite(new PieceMatch(piece, rotation, origin), socket, floor, ceiling);
        site.resinBiomassOwed = tag.getInt(NBT_RESIN_OWED);
        for (long packed : tag.getLongArray(NBT_DUG_COLUMNS)) {
            var column = site.columns.get(unpack(packed));
            if (column != null) {
                column.markExcavated();
            }
        }
        for (long packed : tag.getLongArray(NBT_FILLED_COLUMNS)) {
            var column = site.columns.get(unpack(packed));
            if (column != null) {
                column.markFilled();
            }
        }
        return site;
    }

    private static long pack(ColumnKey key) {
        return ((long) key.x() << 32) | (key.z() & 0xFFFFFFFFL);
    }

    private static ColumnKey unpack(long packed) {
        return new ColumnKey((int) (packed >> 32), (int) packed);
    }

    public PieceMatch match() {
        return match;
    }

    public @Nullable FrontierSocket connectedTo() {
        return connectedTo;
    }

    /**
     * The FOUNDING CORE (step 6, design §7b): the queen chamber, dug by the queen herself. No upstream socket, dig
     * ordered outward from the chamber center, fixed queen pace, eggsack gated on {@link #isFullyExcavated()}, and
     * completion runs the founding tail (royal ring, socket registration) instead of {@code finalizePlacement}.
     */
    public boolean isFoundingCore() {
        return connectedTo == null;
    }

    public int floorY() {
        return floorY;
    }

    public int ceilingY() {
        return ceilingY;
    }

    /** The highest Y a floor-standing worker can reach in this piece: floor + reach, capped at the ceiling. */
    public int reachableCeilingY() {
        return Math.min(ceilingY, floorY + REACH_HEIGHT);
    }

    public Map<ColumnKey, ColumnProgress> columns() {
        return columns;
    }

    public ColumnProgress column(int x, int z) {
        return columns.get(new ColumnKey(x, z));
    }

    public int resinBiomassOwed() {
        return resinBiomassOwed;
    }

    public void setResinBiomassOwed(int owed) {
        this.resinBiomassOwed = owed;
    }

    /** Pays part of the resin debt. Clamped - the debt never goes negative. */
    public void payResin(int amount) {
        this.resinBiomassOwed = Math.max(0, this.resinBiomassOwed - amount);
    }

    /**
     * Whether the last fill attempt bounced for lack of biomass (design §6 starvation). While true, the hive's
     * discretionary spends (expansion claims, caste purchases) stand aside so income finishes this build first.
     */
    public boolean isStarved() {
        return starved;
    }

    /** Reserve-materialized workers currently assigned - exempt from the member cap while digging (design §8.5). */
    public int materializedWorkerCount() {
        return materializedWorkers.size();
    }

    /** Columns not yet resined - the denominator of the pay-as-you-fill price (step 4). */
    public int unfilledColumnCount() {
        var count = 0;
        for (ColumnProgress c : columns.values()) {
            if (!c.isFilled()) {
                count++;
            }
        }
        return count;
    }

    // ---- Progress queries ----

    /** Every column dug out - excavation phase done, placers can finish whatever is left. */
    public boolean isFullyExcavated() {
        for (ColumnProgress c : columns.values()) {
            if (!c.isExcavated()) {
                return false;
            }
        }
        return true;
    }

    /** Fraction of columns excavated, 0..1 - gates when the resin fill starts trailing in (design §5). */
    public float excavatedFraction() {
        if (columns.isEmpty()) {
            return 1.0f;
        }
        int done = 0;
        for (ColumnProgress c : columns.values()) {
            if (c.isExcavated()) {
                done++;
            }
        }
        return (float) done / columns.size();
    }

    /** Every column both dug and resined - the piece is built and ready for {@code finalizePlacement}. */
    public boolean isComplete() {
        for (ColumnProgress c : columns.values()) {
            if (!c.isComplete()) {
                return false;
            }
        }
        return true;
    }

    /** Fraction of columns fully complete, 0..1 - for debug readouts and progress display. */
    public float completionFraction() {
        if (columns.isEmpty()) {
            return 1.0f;
        }
        int done = 0;
        for (ColumnProgress c : columns.values()) {
            if (c.isComplete()) {
                done++;
            }
        }
        return (float) done / columns.size();
    }

    /** A column address within the piece. Value-equal so it works as a map key. */
    public record ColumnKey(
        int x,
        int z
    ) {}

    /** A one-line summary for debug readouts - which piece, where, how far along. */
    public String describe() {
        return "CarveSite[" + match.piece().id() + " @ " + match.originChunk()
            + ", columns=" + columns.size()
            + ", " + String.format("%.0f%%", completionFraction() * 100) + " complete]";
    }
}
