package com.alien.common.gameplay.hive.structure;

import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.init.block.AlienBlocks;
import com.alien.common.registry.tag.AlienBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps a finished hive piece matching the shape it was built as.
 * <p>
 * A stamped piece is only correct at the instant it is placed. Afterwards the world edits it: a neighbouring carve
 * opens a lava pocket that floods a chamber, gravel falls into a hallway, terrain shifts, or a player walls off a
 * corridor. Nothing put any of it back, so a hive slowly degraded and - worst of all - passages that the routing layer
 * still believed were open became impassable. Egg haulers then read perfectly good nursery beds as unreachable.
 * <p>
 * The pass re-stamps one built piece at a time. The stamp is idempotent (see {@link HiveStructurePlacer#placeWorld}):
 * it rewrites the authored structure blocks, writes AIR back into the piece's air cells - which is what removes
 * intruding blocks and reopens blocked passages - and drains liquids.
 * <p>
 * <b>Exemptions.</b> A blind re-stamp would also erase what the hive itself put inside those air cells, so anything on
 * the hive's own allowlist is snapshotted before the stamp and restored after:
 * <ul>
 * <li>Jelly vats - placed into chamber slots after the stamp, and they carry contents worth preserving.</li>
 * <li>Resin veins and webs - the aliens grow these into open space; they occupy air cells by design.</li>
 * <li>Capture anchors - a player restraint, deliberately left standing. Clearing them here would quietly undo a capture
 * setup on a timer; whether an anchor survives inside a hive is for the aliens to settle by attacking it, not for
 * masonry upkeep to decide.</li>
 * </ul>
 * Everything else in an air cell is treated as debris and cleared, player-placed blocks included: that is deliberate,
 * so a player cannot permanently seal a hive passage.
 */
public final class HiveStructureUpkeep {

    /**
     * Update flags for bulk hive placement: clients only, NO neighbour updates.
     * <p>
     * 🚨🚨 NEIGHBOUR UPDATES ARE WHAT KILLED A DEDICATED SERVER. Flag 3 includes UPDATE_NEIGHBORS, so every block the
     * hive carved or stamped told its neighbours to re-evaluate - and vanilla blocks respond by SCHEDULING A TICK:
     * leaves check decay, sand checks support, water checks flow. A hive spreading through terrain therefore queued a
     * scheduled tick per disturbed neighbour, and a crash report showed block_ticks: 498,053 pending with the server
     * stuck in LevelTicks.sortContainersToTick.
     * </p>
     * <p>
     * ⭐ This is what VANILLA STRUCTURE GENERATION uses for the same reason. The hive is placing terrain in bulk, not
     * operating a redstone contraption - it does not need the cascade.
     * </p>
     * <p>
     * ⚠ TRADE-OFF, AND IT IS THE ONE WE WANT: water and lava no longer flow into freshly carved space and sand no
     * longer falls into it. Interactive single placements - jelly vats, spawners, harvest capture - keep flag 3 and are
     * untouched.
     * </p>
     */
    private static final int BULK_HIVE_BLOCK_FLAGS = net.minecraft.world.level.block.Block.UPDATE_CLIENTS;

    private HiveStructureUpkeep() {}

    /** A hive-owned block held across the re-stamp. */
    private record Preserved(
        BlockPos pos,
        BlockState state,
        CompoundTag blockEntity
    ) {}

    /**
     * Re-stamps one finished piece. No-op when the piece is unknown, its chunks aren't loaded, or the placement can't
     * be resolved - upkeep is best-effort and must never be the reason a tick throws.
     */
    public static void tickPiece(ServerLevel level, HiveLocation location, ChunkPos originChunk, HiveLocation.BuiltPlacement placement) {
        // NO MAINTENANCE: the hive leaves its structure exactly as it finds it.
        //
        // Guarded at the very TOP of both entry points, not inside the repair crew: this is the DETECTOR, so
        // nothing is scanned, no breach recorded, no job filed and no drone drafted. Stopping it further down would
        // still cost the scan every beat and still pull workers off their jobs.
        //
        // WARNING: this turns off BOTH halves - re-stamping damaged hive blocks AND clearing whatever is found in
        // an authored air cell. The second half is what normally stops a player sealing a passage permanently, so
        // with this on a corridor CAN be walled off for good. That is the trade the setting exists to make.
        if (HiveLocationRegistry.INSTANCE.config().noMaintenance()) {
            return;
        }

        var registry = HivePieceRegistry.get(level.getServer());
        if (registry == null) {
            return;
        }
        var pieceId = ResourceLocation.tryParse(placement.pieceId());
        if (pieceId == null) {
            return;
        }
        var piece = registry.get(pieceId);
        if (piece == null) {
            return;
        }

        var match = new PieceMatch(piece, placement.rotation(), originChunk);
        var chunks = match.occupiedChunks();
        for (ChunkPos chunk : chunks) {
            if (!level.isLoaded(chunk.getWorldPosition())) {
                return; // never edit a piece we can only half see
            }
        }

        // CHECK BEFORE REPAIRING. Re-stamping blindly every beat would rewrite an entire piece - thousands of
        // setBlock calls, each with the lighting and client-sync cost that implies - to fix nothing at all,
        // several times a minute, per hive. Resolving the placement is pure (no world access) and the authored
        // air cells come straight from the template palette, so the steady-state cost of upkeep is a read-only
        // scan of just those cells and NO writes whatsoever. The expensive path runs only when something has
        // actually got into the piece.
        var resolved = HiveStructurePlacer.resolvePlacement(level, location, match);
        if (resolved == null) {
            return;
        }
        // Oct 6 - clear floating growth on the same beat (see sweepFloatingDecor).
        sweepFloatingDecor(level, resolved);
        if (HiveBreachRepair.hasJob(location, originChunk)) {
            return; // crew already dispatched - the detector's work is done until they finish
        }
        if (HiveBreachRepair.isCoolingDown(level, location, originChunk)) {
            return; // just repaired - see HiveBreachRepair.REPAIR_COOLDOWN_TICKS
        }
        // Damage is no longer healed on the spot: the pass COLLECTS the wounded cells and files a repair job, and
        // drones travel there and visibly work before the re-stamp runs ([stated] "sends a drone or drones to the
        // site to do the dig animation as if they are fixing the breach"). See HiveBreachRepair.
        var breachCells = collectBreachCells(level, location, originChunk, resolved);
        if (breachCells.isEmpty()) {
            return;
        }
        HiveBreachRepair.noteBreach(location, originChunk, breachCells);
    }

    /**
     * The actual masonry, invoked by {@link HiveBreachRepair} once a crew has finished working the wound. Same
     * reconstruction and snapshot-stamp-restore dance the instant path used.
     */
    /**
     * Puts back hive blocks that were broken outside any structure piece - corridors, shafts, patched walls.
     * <p>
     * ⭐⭐ THE CHEAPEST REPAIR THERE IS. Every cell here came from an actual break, which recorded the exact position and
     * the exact state, so this is a map walk and a setBlock - no scanning, no template diff, no volume search. Its cost
     * is proportional to blocks broken, not to hive size, so a large hive with no damage costs one emptiness check per
     * pass.
     * </p>
     * <p>
     * ⚠ RESTORES ONLY INTO AIR OR FLUID. A cell a player has since filled with their own block is dropped rather than
     * overwritten - the hive reclaims its own damage, it does not bulldoze someone's repair or their doorway.
     * </p>
     * <p>
     * ⚠ Suppressed and client-only-updated, like every other hive placement: putting resin back must not itself
     * register as a breach, and neighbour updates on hive blocks are what once filled a server's tick queue.
     * </p>
     */
    public static void repairLooseCells(ServerLevel level, HiveLocation location, int budget) {
        var cells = location.flaggedBreachCells();

        if (cells.isEmpty()) {
            return;
        }

        var iterator = cells.entrySet().iterator();
        var repaired = 0;

        while (iterator.hasNext() && repaired < budget) {
            var entry = iterator.next();
            var pos = entry.getKey();

            if (!level.isLoaded(pos)) {
                continue; // Not our chunk right now - leave it for a pass when it is loaded.
            }

            iterator.remove();

            // Oct 6 - veins and webs are no longer recorded as breaches (MixinBlockBehaviour_HiveBreachFlag); any
            // queued before that change are dropped here rather than put back into the air.
            if (entry.getValue().is(AlienBlockTags.RESIN_VEINS) || entry.getValue().is(AlienBlockTags.RESIN_WEBS)) {
                continue;
            }

            var current = level.getBlockState(pos);

            if (!current.isAir() && current.getFluidState().isEmpty()) {
                continue; // Someone filled it. Their block, their business.
            }

            HiveBuildSuppression.without(() -> {
                level.setBlock(pos, entry.getValue(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                return null;
            });

            repaired++;
        }
    }

    public static boolean restamp(ServerLevel level, HiveLocation location, ChunkPos originChunk, HiveLocation.BuiltPlacement placement) {
        // 🚨🚨 THE HIVE MUST NOT FLAG ITSELF. Re-stamping replaces blocks, and any resin that goes to air during the
        // stamp fires the breach hook - so a repair created the next breach and the crew looped forever on one
        // chunk. See HiveBuildSuppression.
        return HiveBuildSuppression.without(() -> restampUnsuppressed(level, location, originChunk, placement));
    }

    private static boolean restampUnsuppressed(
        ServerLevel level,
        HiveLocation location,
        ChunkPos originChunk,
        HiveLocation.BuiltPlacement placement
    ) {
        // NO MAINTENANCE: the hive leaves its structure exactly as it finds it.
        //
        // Guarded at the very TOP of both entry points, not inside the repair crew: this is the DETECTOR, so
        // nothing is scanned, no breach recorded, no job filed and no drone drafted. Stopping it further down would
        // still cost the scan every beat and still pull workers off their jobs.
        //
        // WARNING: this turns off BOTH halves - re-stamping damaged hive blocks AND clearing whatever is found in
        // an authored air cell. The second half is what normally stops a player sealing a passage permanently, so
        // with this on a corridor CAN be walled off for good. That is the trade the setting exists to make.
        if (HiveLocationRegistry.INSTANCE.config().noMaintenance()) {
            return false;
        }

        var registry = HivePieceRegistry.get(level.getServer());
        if (registry == null) {
            return false;
        }
        var pieceId = ResourceLocation.tryParse(placement.pieceId());
        if (pieceId == null) {
            return false;
        }

        // ⭐⭐ A CONVERTED HIVE REPAIRS ITSELF IN ITS OWN COLOURS. [stated] "irradiated werent repairing their hive or
        // overwriting the former strain with their own".
        // <p>
        // BuiltPlacement.pieceId is FROZEN AT BUILD TIME, so a hive built normal and later nuked into irradiated was
        // re-stamping the NORMAL piece on every repair - forever. It was repairing perfectly well; it was repairing
        // itself back to the strain it used to be, which reads as "not overwriting the former strain". NukeConversion
        // retints existing blocks once at detonation, but nothing kept new masonry honest afterwards.
        // </p>
        // <p>
        // ⚠ FALL BACK TO THE STORED ID, never fail. If a strain has no mirror of this piece the registry lookup misses
        // and we stamp what was actually built - a wrong-coloured room beats a hole in the wall.
        // </p>
        var variant = location.lineageVariantOrNull();
        if (!HivePieceCatalog.belongsToStrain(pieceId, variant)) {
            var restrained = HivePieceCatalog.forStrain(pieceId, variant);
            if (registry.get(restrained) != null) {
                pieceId = restrained;
            }
        }

        var piece = registry.get(pieceId);
        if (piece == null) {
            return false;
        }
        var match = new PieceMatch(piece, placement.rotation(), originChunk);
        var chunks = match.occupiedChunks();
        for (ChunkPos chunk : chunks) {
            if (!level.isLoaded(chunk.getWorldPosition())) {
                return false;
            }
        }
        var preserved = snapshotHiveBlocks(level, location, chunks);
        if (!HiveStructurePlacer.placeWorld(level, location, match)) {
            return false;
        }
        restore(level, preserved);
        // Oct 6 - a re-stamp can take away what restored growth hung from (an intruding block, a vent in an air cell):
        // clear whatever the restore left floating before the cells are judged.
        var restamped = HiveStructurePlacer.resolvePlacement(level, location, match);
        if (restamped != null) {
            sweepFloatingDecor(level, restamped);
        }
        markUnfixableCells(level, location, originChunk, match);
        return true;
    }

    /**
     * ⚠⚠ THE PHANTOM-HOLE VALVE. Anything the stamp could not fix is never reported again.
     * <p>
     * A saved structure OMITS its {@code structure_void} cells - {@code fillFromWorld} is told to ignore them - so a
     * template does NOT author every cell inside its own bounding box. {@code hallway_straight_1x1} authors 1116 of
     * 4096. The carve digs the whole slab, so every unauthored cell is genuinely AIR in the world, and
     * {@link #collectBreachCells} used to read each one as a missing wall: job filed, crew works, re-stamp places
     * NOTHING there (the template has nothing to place), cell is still air, re-detected. Forever. Measured across the
     * 80 piece NBTs the worst offenders are hallway_royal_2x1_a (420 such cells), chamber_host_2x2 (112),
     * chamber_raid_2x2 (70) and hallway_tee_1x1 (2) - which is exactly the set a mature hive keeps re-repairing.
     * </p>
     * <p>
     * The template's authored-cell set is not reachable through public API ({@code StructureTemplate.palettes} is
     * private and {@code filterBlocks} only answers per-block), so rather than guess at it this asks the world: run the
     * detector again the instant the stamp finishes. A cell the masonry just rewrote and STILL reports as damaged
     * cannot be fixed by masonry, so it is retired. That covers structure_void, {@code minecraft:jigsaw} cells (their
     * {@code final_state} is air, so an authored jigsaw is air in the world too) and any future authoring quirk,
     * without needing to enumerate any of them.
     * </p>
     * <p>
     * ⚠ IT MUST RUN IMMEDIATELY, IN THE SAME TICK. Fluid flow is SCHEDULED, so water that will seep back into a
     * genuinely breached room has not moved yet - a real leak reads as fixed here and is correctly NOT retired. Only
     * cells that are broken the very instant the stamp lands are phantoms.
     * </p>
     */
    private static void markUnfixableCells(ServerLevel level, HiveLocation location, ChunkPos originChunk, PieceMatch match) {
        var resolved = HiveStructurePlacer.resolvePlacement(level, location, match);
        if (resolved == null) {
            return;
        }
        var residual = collectBreachCells(level, location, originChunk, resolved);
        if (!residual.isEmpty()) {
            HiveBreachRepair.retireUnfixableCells(location, originChunk, residual);
        }
    }

    /** Enough cells to aim a crew and prove the wound; collection stops here. */
    private static final int MAX_BREACH_CELLS = 8;

    /**
     * Every damaged cell the hive can see, two kinds: INTRUSIONS (something solid or liquid in an authored air cell -
     * the original detector) and HOLES (an authored SOLID on the interior boundary that is now GONE). Broken walls and
     * floors were invisible before: the old scan only read the air cells, so a removed block never registered and
     * player damage stood forever. The boundary is derived from the air cells - each neighbor of an authored air cell
     * that is not itself authored air is treated as an authored solid (wall, floor, ceiling). Neighbors on the
     * template's outermost shell are skipped, so doorway mouths that run to the edge (where structure_void margins
     * begin) can never read as false damage.
     * <p>
     * ⚠⚠ THAT BOUNDARY DERIVATION IS AN APPROXIMATION AND IT OVER-REPORTS - see {@link #markUnfixableCells}. Cells the
     * template never authored at all look identical to authored solids from this side, so the retired set is what keeps
     * the over-report from becoming a permanent repair loop.
     * </p>
     * <p>
     * ⚠⚠ A WATERLOGGED WALL IS STILL A WALL. The hole test used to be {@code isAir() || !getFluidState().isEmpty()},
     * and a waterlogged block reports a fluid state while being perfectly present - so in a flooded hive every
     * waterloggable authored solid touching a room read as a missing wall. These pieces are full of them:
     * chamber_host_2x2 has 1313 resin webs against its air cells, queen_chamber_3x3 has 963 webs plus 126 stairs,
     * hallway_straight_1x1 has 96 ribbed stairs. The stamp writes the dry state, water flows back, the next scan sees
     * it again - the same endless re-stamp the flooded-doorway bug produced one system over. The test is now the same
     * one {@code HiveRouter.isDoorwayOpen} settled on: <b>can a xenomorph pass through the cell</b>. A liquid BLOCK is
     * replaceable and counts as missing; a waterlogged solid is not and does not.
     * </p>
     */
    private static List<BlockPos> collectBreachCells(
        ServerLevel level,
        HiveLocation location,
        ChunkPos originChunk,
        HiveStructurePlacer.ResolvedPlacement resolved
    ) {
        var breached = new ArrayList<BlockPos>();
        var airCells = resolved.template().filterBlocks(resolved.placeAt(), resolved.settings(), Blocks.AIR);
        var airSet = new java.util.HashSet<BlockPos>(Math.max(16, airCells.size() * 2));
        for (var cell : airCells) {
            airSet.add(cell.pos());
        }
        var retired = HiveBreachRepair.retiredCells(location, originChunk);
        var bounds = resolved.template().getBoundingBox(resolved.settings(), resolved.placeAt());
        for (var cell : airCells) {
            var state = level.getBlockState(cell.pos());
            if (!state.isAir() && !isPreserved(state)) {
                breached.add(cell.pos()); // intrusion
                if (breached.size() >= MAX_BREACH_CELLS) {
                    return breached;
                }
            }
            for (var direction : net.minecraft.core.Direction.values()) {
                var neighbor = cell.pos().relative(direction);
                if (airSet.contains(neighbor)) {
                    continue; // interior air, not boundary
                }
                if (
                    neighbor.getX() <= bounds.minX() || neighbor.getX() >= bounds.maxX()
                        || neighbor.getY() <= bounds.minY() || neighbor.getY() >= bounds.maxY()
                        || neighbor.getZ() <= bounds.minZ() || neighbor.getZ() >= bounds.maxZ()
                ) {
                    continue; // outermost shell - structure_void territory
                }
                if (retired.contains(neighbor)) {
                    continue; // a re-stamp already proved masonry cannot fill this one
                }
                var neighborState = level.getBlockState(neighbor);
                if (neighborState.isAir() || neighborState.canBeReplaced()) {
                    breached.add(neighbor); // hole: the authored solid is gone, not merely wet
                    if (breached.size() >= MAX_BREACH_CELLS) {
                        return breached;
                    }
                }
            }
        }
        return breached;
    }

    /** Collects the hive's own blocks inside the piece so the stamp can't eat them. */
    private static List<Preserved> snapshotHiveBlocks(ServerLevel level, HiveLocation location, List<ChunkPos> chunks) {
        var preserved = new ArrayList<Preserved>();
        var pos = new BlockPos.MutableBlockPos();
        int floorY = location.hiveFloorY();
        int maxY = location.hiveCeilingY() - 1;
        for (ChunkPos chunk : chunks) {
            int minX = chunk.getMinBlockX();
            int minZ = chunk.getMinBlockZ();
            for (int x = minX; x < minX + 16; x++) {
                for (int z = minZ; z < minZ + 16; z++) {
                    for (int y = floorY; y <= maxY; y++) {
                        pos.set(x, y, z);
                        var state = level.getBlockState(pos);
                        if (!isPreserved(state)) {
                            continue;
                        }
                        CompoundTag beTag = null;
                        var blockEntity = level.getBlockEntity(pos);
                        if (blockEntity != null) {
                            beTag = blockEntity.saveWithFullMetadata(level.registryAccess());
                        }
                        preserved.add(new Preserved(pos.immutable(), state, beTag));
                    }
                }
            }
        }
        return preserved;
    }

    /** Puts the hive's own blocks back, contents and all. */
    private static void restore(ServerLevel level, List<Preserved> preserved) {
        for (var entry : preserved) {
            // Only refill cells the stamp actually cleared - never overwrite structure it just (re)built.
            if (!level.getBlockState(entry.pos()).isAir()) {
                continue;
            }
            // ⭐ Oct 6 - A VEIN COMES BACK ONLY ON FACES THAT STILL HAVE A WALL. The snapshot is taken before the stamp
            // and the stamp can remove what a vein grew on; restoring it verbatim is one way veins ended up in midair.
            var restoredState = entry.state();
            if (restoredState.getBlock() instanceof net.minecraft.world.level.block.MultifaceBlock) {
                restoredState = supportedVeinStateOrNull(level, entry.pos(), restoredState);
                if (restoredState == null) {
                    continue;
                }
            }
            level.setBlock(entry.pos(), restoredState, BULK_HIVE_BLOCK_FLAGS);
            if (entry.blockEntity() == null) {
                continue;
            }
            var blockEntity = level.getBlockEntity(entry.pos());
            if (blockEntity != null) {
                blockEntity.loadWithComponents(entry.blockEntity(), level.registryAccess());
                blockEntity.setChanged();
            }
        }
    }

    /**
     * The allowlist: blocks the upkeep pass must not clear. Mostly the hive's own (tag-driven for resin, so every
     * variant is covered and new resin blocks inherit the exemption for free), plus the capture anchor, which is left
     * for the aliens to settle rather than being tidied away by masonry.
     */
    /**
     * {@code state} (a vein) with every face that no longer has something to cling to switched off, or null when none
     * is left - the same {@code canAttachTo} test vanilla's multiface blocks use when they are placed.
     */
    private static @org.jetbrains.annotations.Nullable BlockState supportedVeinStateOrNull(
        ServerLevel level,
        BlockPos pos,
        BlockState state
    ) {
        var anyFace = false;
        var result = state;

        for (var direction : net.minecraft.core.Direction.values()) {
            var property = net.minecraft.world.level.block.MultifaceBlock.getFaceProperty(direction);

            if (!result.hasProperty(property) || !result.getValue(property)) {
                continue;
            }

            var supportPos = pos.relative(direction);

            // ⚠ Review pass: never read a block in an unloaded chunk - ServerLevel.getBlockState would LOAD it
            // synchronously. A wall we cannot see is assumed to still be there.
            if (!level.isLoaded(supportPos)) {
                anyFace = true;
                continue;
            }

            if (net.minecraft.world.level.block.MultifaceBlock.canAttachTo(level, direction, supportPos, level.getBlockState(supportPos))) {
                anyFace = true;
            } else {
                result = result.setValue(property, Boolean.FALSE);
            }
        }

        return anyFace ? result : null;
    }

    /** Most web cells a floating cluster may hold before the sweep stops following it (and leaves it alone). */
    private static final int MAX_WEB_CLUSTER = 256;

    /**
     * ⭐⭐ Oct 6 - CLEARS FLOATING HIVE GROWTH INSIDE A BUILT PIECE. [stated] "floating clusters of veins and web ...
     * almost like they were ... making a vent in midair", in the raid ("spawner") room and others; [stated] "some of
     * those veins were infact not touching the floor".
     * <p>
     * Only the piece's AUTHORED AIR CELLS are looked at - the open space of the room - so nothing the template itself
     * built is ever touched (all 90 templates were checked: every authored vein faces an authored wall and every
     * authored web cluster is anchored; no authored web stands alone). In that open space:
     * </p>
     * <ul>
     * <li>A VEIN keeps only the faces that still have something to cling to, and goes when none are left. The hive's
     * own carving and stamping send no neighbour updates (that is what once filled a server's tick queue), so a vein
     * whose wall the hive removed was never told.</li>
     * <li>A WEB CLUSTER stays only if it is part of something: it touches a vent, it touches a web the room was built
     * with, or it is holding a captive. Otherwise it is the ring a vent left behind when the vent itself was cleared
     * from the room - the clumps standing on room floors in his screenshots - and it goes.</li>
     * </ul>
     * <p>
     * Runs on the upkeep beat for one piece at a time and only reads open cells, so the cost is a block read per open
     * cell plus a few reads per piece of growth found. Removals are suppressed (never flagged as damage) and send no
     * neighbour updates, like every other hive edit.
     * </p>
     */
    static void sweepFloatingDecor(ServerLevel level, HiveStructurePlacer.ResolvedPlacement resolved) {
        var airCells = resolved.template().filterBlocks(resolved.placeAt(), resolved.settings(), Blocks.AIR);

        if (airCells.isEmpty()) {
            return;
        }

        var airSet = new java.util.HashSet<BlockPos>(Math.max(16, airCells.size() * 2));
        for (var cell : airCells) {
            airSet.add(cell.pos());
        }

        var webs = new java.util.HashSet<BlockPos>();

        for (var cell : airCells) {
            var pos = cell.pos();

            if (!level.isLoaded(pos)) {
                continue;
            }

            var state = level.getBlockState(pos);

            if (state.is(AlienBlockTags.RESIN_WEBS)) {
                webs.add(pos);
                continue;
            }

            if (state.is(AlienBlockTags.RESIN_VEINS) && state.getBlock() instanceof net.minecraft.world.level.block.MultifaceBlock) {
                var supported = supportedVeinStateOrNull(level, pos, state);

                if (supported != state) {
                    // ⚠ Review pass: a waterlogged vein leaves its water behind, not a dry hole in a flooded room.
                    var waterlogged = state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
                        && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED);
                    var replacement = supported != null
                        ? supported
                        : waterlogged ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    HiveBuildSuppression.without(() -> {
                        level.setBlock(pos, replacement, BULK_HIVE_BLOCK_FLAGS);
                        return null;
                    });
                }
            }
        }

        if (webs.isEmpty()) {
            return;
        }

        var visited = new java.util.HashSet<BlockPos>();

        for (var start : webs) {
            if (visited.contains(start)) {
                continue;
            }

            var cluster = new ArrayList<BlockPos>();
            var queue = new java.util.ArrayDeque<BlockPos>();
            queue.add(start);
            visited.add(start);
            var anchored = false;

            while (!queue.isEmpty()) {
                var pos = queue.poll();
                cluster.add(pos);

                if (cluster.size() > MAX_WEB_CLUSTER) {
                    anchored = true; // too big to judge cheaply - leave it be
                    break;
                }

                for (var direction : net.minecraft.core.Direction.values()) {
                    var neighbour = pos.relative(direction);

                    // ⚠ Review pass: a neighbour in an unloaded chunk is never read (that would load it); the cluster
                    // is treated as anchored - leaving a web is always the safe mistake.
                    if (!level.isLoaded(neighbour)) {
                        anchored = true;
                        continue;
                    }

                    var neighbourState = level.getBlockState(neighbour);

                    if (neighbourState.is(AlienBlockTags.RESIN_VENTS)) {
                        anchored = true; // a live vent's webbing
                    } else if (neighbourState.is(AlienBlockTags.RESIN_WEBS)) {
                        if (!airSet.contains(neighbour)) {
                            anchored = true; // joined to webbing the room was built with
                        } else if (webs.contains(neighbour) && visited.add(neighbour)) {
                            queue.add(neighbour);
                        }
                    }
                }
            }

            if (anchored) {
                continue;
            }

            var holdsCaptive = false;
            for (var pos : cluster) {
                if (HostParking.holdsCaptive(level, pos)) {
                    holdsCaptive = true;
                    break;
                }
            }

            if (holdsCaptive) {
                continue;
            }

            HiveBuildSuppression.without(() -> {
                for (var pos : cluster) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), BULK_HIVE_BLOCK_FLAGS);
                }
                return null;
            });
        }
    }

    private static boolean isPreserved(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        return state.is(AlienBlocks.JELLY_VAT.get())
            || state.is(AlienBlocks.ANCHOR.get())
            // ⭐⭐ RESIN CONTAINERS SURVIVE A RE-STAMP - the second half of "they lose their contents". A re-stamp lays
            // the template back over the whole piece, so anything not on this list is gone; the harvest chamber's
            // containers sit in a router-placed piece, so ANY breach repair there wiped the room's containers and the
            // hive's whole salvage haul. snapshotHiveBlocks already captures saveWithFullMetadata for what it
            // preserves, so listing them restores their CONTENTS too, not just the block.
            // ⚠ ALL FOUR STRAIN VARIANTS - no block tag covers them, and a converted hive holds whichever strain it
            // was built in; missing one would silently wipe that strain's containers only.
            // ⚠ This predicate ALSO gates the breach detector's INTRUSION test, correctly: a container in an authored
            // air cell is something the hive placed on purpose, not damage to repair.
            || state.is(AlienBlocks.RESIN_CONTAINER.get())
            || state.is(AlienBlocks.NETHER_RESIN_CONTAINER.get())
            || state.is(AlienBlocks.ABERRANT_RESIN_CONTAINER.get())
            || state.is(AlienBlocks.IRRADIATED_RESIN_CONTAINER.get())
            || state.is(AlienBlockTags.RESIN_VEINS)
            || state.is(AlienBlockTags.RESIN_WEBS);
    }
}
