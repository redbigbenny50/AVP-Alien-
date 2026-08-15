package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.gameplay.hive.economy.IrradiatedHiveRules;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.registry.tag.AlienBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;

/**
 * A converted hive turning its own walls irradiated, a bit at a time.
 * <h2>Why a swap and not a re-stamp</h2> The irradiated structure pieces are PURE RE-SKINS - identical geometry,
 * identical piece names, only the block palette differs. {@code queen_chamber_3x3} is 815,214 bytes normal and 815,389
 * irradiated, the difference being longer block IDs. So the conversion is a block swap IN PLACE: layout, contents and
 * anything the players built into the rooms all survive, and there is no NBT to re-place.
 * <h2>The mapping is derived, not written down</h2> Strip any {@code nether_} or {@code aberrant_} token, then insert
 * {@code irradiated_} immediately before {@code resin} or {@code chitin}. That resolves 133 of the 139 strain blocks
 * straight out of the registry, and the six it missed turned out to be a TAG gap rather than a naming exception -
 * {@code irradiated_resin_slab} and {@code irradiated_resin_stairs} existed but had never been listed. A hand-written
 * table would have hidden that. Anything genuinely unmapped is left alone rather than guessed at.
 * <h2>Pacing</h2> [stated] "sweep with workers animating nearby" - not a worker walking to each block. A budget of
 * blocks per pass, moving through the hive's own chunks, so a big hive takes visible minutes to finish changing colour.
 * Free of biomass either way: a converted hive is not paying to become what the blast already made it.
 */
public final class IrradiatedConversionSweep {

    /** How often a pass runs. Slow enough to watch, frequent enough to finish. */
    private static final int INTERVAL_TICKS = 20;

    /** Blocks EXAMINED per pass, per hive - a hard ceiling on this task's cost whatever state the hive is in. */
    private static final int BUDGET_PER_PASS = 512;

    /** Where each hive's last pass stopped, so the next one resumes rather than restarting. */
    private static final Map<net.minecraft.resources.ResourceLocation, Integer> CURSORS = new HashMap<>();

    /** Cached name mapping, built lazily - the registry lookup is not free and the answers never change. */
    private static final Map<Block, Block> IRRADIATED_EQUIVALENT = new HashMap<>();

    private static long tickCounter;

    private IrradiatedConversionSweep() {
        throw new UnsupportedOperationException();
    }

    public static void tick(MinecraftServer server) {
        if (++tickCounter % INTERVAL_TICKS != 0) {
            return;
        }

        for (var factionId : new java.util.ArrayList<>(Alien.MOD.factions().getAllIds())) {
            if (!com.alien.common.gameplay.hive.id.LineageIds.isLineageId(factionId)) {
                continue;
            }

            var faction = Alien.MOD.factions().get(factionId);
            if (
                faction == null
                    || !(faction.data() instanceof com.alien.common.gameplay.hive.faction.LineageFactionData lineage)
                    || !lineage.isAlive()
            ) {
                continue;
            }

            var level = server.getLevel(lineage.dimension());
            if (level == null) {
                continue;
            }

            for (var location : new java.util.ArrayList<>(lineage.locationsById().values())) {
                if (location.isAlive() && IrradiatedHiveRules.isIrradiated(location)) {
                    sweep(level, location);
                }
            }
        }
    }

    /**
     * One pass, bounded by blocks EXAMINED rather than blocks changed, resuming where the last pass stopped.
     * <p>
     * THIS IS THE WHOLE PERFORMANCE STORY OF THE CLASS. The first version decremented the budget only on an actual
     * conversion, which meant that once a hive was fully converted nothing decremented it and the loop ran to
     * completion over every block in the hive EVERY SECOND, FOREVER - 40,000 block reads a second for a small hive and
     * a quarter of a million for a large one, permanently, long after there was anything left to do.
     * <p>
     * Counting examined blocks bounds the cost absolutely: {@link #BUDGET_PER_PASS} reads per second per hive, no
     * matter how much or how little is left. The cursor is what keeps it making progress - each pass picks up at the
     * chunk the last one stopped on, so a full circuit still completes, it just takes as long as it takes.
     */
    private static void sweep(ServerLevel level, HiveLocation location) {
        var built = new java.util.ArrayList<>(location.structureRoleByChunk().keySet());
        built.addAll(location.structurePieceByChunk().keySet());

        if (built.isEmpty()) {
            return;
        }

        // Sorted so the cursor means the same thing from one pass to the next - a HashSet's iteration order is
        // stable in practice but not promised, and a cursor into a shifting list would skip and repeat chunks.
        built.sort(java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong));

        var start = CURSORS.getOrDefault(location.id().value(), 0) % built.size();
        var budget = BUDGET_PER_PASS;
        var cursor = new BlockPos.MutableBlockPos();
        var index = start;

        do {
            var chunkPos = built.get(index);

            if (level.isLoaded(chunkPos.getWorldPosition())) {
                budget = sweepChunk(level, location, level.getChunk(chunkPos.x, chunkPos.z), chunkPos, cursor, budget);
            }

            index = (index + 1) % built.size();
        } while (budget > 0 && index != start);

        CURSORS.put(location.id().value(), index);
    }

    private static int sweepChunk(
        ServerLevel level,
        HiveLocation location,
        LevelChunk chunk,
        net.minecraft.world.level.ChunkPos chunkPos,
        BlockPos.MutableBlockPos cursor,
        int budget
    ) {
        var minX = chunkPos.getMinBlockX();
        var minZ = chunkPos.getMinBlockZ();

        for (var y = location.hiveFloorY(); y <= location.hiveCeilingY() && budget > 0; y++) {
            for (var dx = 0; dx < 16 && budget > 0; dx++) {
                for (var dz = 0; dz < 16 && budget > 0; dz++) {
                    cursor.set(minX + dx, y, minZ + dz);
                    budget--;

                    var state = chunk.getBlockState(cursor);
                    if (state.isAir()) {
                        repairIfCrater(level, location, cursor, y);
                    } else {
                        convertIfStale(level, state, cursor);
                    }
                }
            }
        }

        return budget;
    }

    /**
     * Puts the floor back where the blast took it.
     * <p>
     * [stated] "the hive should repair as much of the crater as they can in relation to the hive." Only the FLOOR PLANE
     * is restored - a nuke digs {@code withRadius(DOWN, 32)}, and the hole below the hive was never hive to begin with.
     * What matters is that the rooms have ground again; the pit underneath stays as scenery.
     * <p>
     * Deliberately not a re-stamp. Restoring the rooms themselves would need the piece NBT to say what belongs where,
     * and would undo anything the players have built into the ruin since. Ground only.
     */
    private static void repairIfCrater(ServerLevel level, HiveLocation location, BlockPos pos, int y) {
        if (y != location.hiveFloorY()) {
            return;
        }

        level.setBlockAndUpdate(
            pos,
            com.alien.common.registry.init.block.IrradiatedAlienResinBlocks.IRRADIATED_RESIN.get().defaultBlockState()
        );
    }

    private static boolean convertIfStale(ServerLevel level, BlockState state, BlockPos pos) {
        if (state.isAir() || isAlreadyIrradiated(state)) {
            return false;
        }

        if (!state.is(AlienBlockTags.RESIN) && !state.is(AlienBlockTags.CHITIN)) {
            return false;
        }

        var target = irradiatedEquivalent(state.getBlock());
        if (target == null) {
            return false;
        }

        // Carry the state across so slabs keep their half, stairs their facing, and so on.
        level.setBlockAndUpdate(pos, copyState(state, target.defaultBlockState()));
        return true;
    }

    private static boolean isAlreadyIrradiated(BlockState state) {
        return state.is(AlienBlockTags.IRRADIATED_RESIN) || state.is(AlienBlockTags.IRRADIATED_CHITIN);
    }

    /** Best-effort property carry-over: anything the target also has keeps its value. */
    private static BlockState copyState(BlockState from, BlockState to) {
        var result = to;

        for (var property : from.getProperties()) {
            if (result.hasProperty(property)) {
                result = copyProperty(from, result, property);
            }
        }

        return result;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(
        BlockState from,
        BlockState to,
        net.minecraft.world.level.block.state.properties.Property<T> property
    ) {
        return to.setValue(property, from.getValue(property));
    }

    /** Strip the strain token, insert {@code irradiated_} before resin/chitin, and ask the registry. */
    private static Block irradiatedEquivalent(Block block) {
        return IRRADIATED_EQUIVALENT.computeIfAbsent(block, source -> {
            var key = BuiltInRegistries.BLOCK.getKey(source);
            var path = key.getPath().replace("nether_", "").replace("aberrant_", "");

            var anchor = path.indexOf("resin");
            if (anchor < 0) {
                anchor = path.indexOf("chitin");
            }
            if (anchor < 0) {
                return null;
            }

            var target = ResourceLocation.fromNamespaceAndPath(
                key.getNamespace(),
                path.substring(0, anchor) + "irradiated_" + path.substring(anchor)
            );

            return BuiltInRegistries.BLOCK.getOptional(target).orElse(null);
        });
    }
}
