package com.alien.common.gameplay.hive.structure.carve;

import com.alien.common.gameplay.block.entity.container.ResinContainerBlockEntity;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.registry.tag.AlienBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps what the hive would otherwise delete.
 * <p>
 * [stated] "when the hive is carving its structures and stamping its deleting alot of ores and inventories from
 * dungeons or other loot chests. So we want to put those ores and contents into these chests. each queen chamber will
 * have 8 of them."
 * </p>
 * <h2>⚠⚠ THIS RUNS ON EVERY CLEARED BLOCK, SO THE FIRST LINE DECIDES THE COST</h2> A dig step clears hundreds of cells
 * a tick. The tag test is a single flag read on the block state and rejects stone, dirt and resin immediately -
 * everything expensive is behind it, and the overwhelming majority of calls never get past the first branch.
 * <h2>⚠ NO PERSISTED POOL, AND THAT IS DELIBERATE</h2> Salvage is deposited into the chamber's containers AT THE MOMENT
 * OF CAPTURE, not queued. A pending pool would need NBT on the location, a drain schedule, and a decision about what
 * happens when a hive is destroyed holding one - all to solve a problem that does not exist, because the queen chamber
 * is always built before the carving that follows it.
 */
public final class HiveSalvage {

    /**
     * ⚠ A CACHE, NOT STATE. Rebuilt on demand and safe to lose - it holds container positions per location so a dig
     * step does not rescan the chamber for every ore it finds. Nothing here needs saving.
     */
    private static final Map<CacheKey, CachedContainers> CONTAINER_CACHE = new HashMap<>();

    /** How long a container list is trusted before it is rebuilt, in game ticks. */
    private static final long CACHE_LIFETIME_TICKS = 200L;

    private HiveSalvage() {}

    /**
     * Offer a block that is about to be cleared. Anything worth keeping is moved into the hive's containers.
     * <p>
     * ⚠ CALL THIS BEFORE {@code setBlock(AIR)}, never after - it reads the block entity for chest contents, and by the
     * time the block is air that is gone.
     * </p>
     */
    public static void capture(ServerLevel level, HiveLocation location, BlockPos pos, BlockState state) {
        var salvageable = state.is(AlienBlockTags.HIVE_SALVAGE);
        var blockEntity = level.getBlockEntity(pos);
        var isContainer = blockEntity instanceof Container;

        if (!salvageable && !isContainer) {
            return;
        }

        var haul = new ArrayList<ItemStack>();

        if (salvageable) {
            // ⚠ THE BLOCK'S OWN ITEM, NOT ITS LOOT TABLE. [stated] "its storing the ore blocks themselves not the raw
            // items so it would store say iron ore not raw iron." This also sidesteps fortune, silk touch and any
            // randomness in the drop table entirely - a deepslate iron ore stores as deepslate iron ore.
            var item = state.getBlock().asItem();

            if (item != net.minecraft.world.item.Items.AIR) {
                haul.add(new ItemStack(item));
            }
        }

        if (isContainer) {
            drain((Container) blockEntity, haul);
        }

        if (haul.isEmpty()) {
            return;
        }

        deposit(level, location, haul);
    }

    /**
     * ⭐⭐ THE STAMP'S SALVAGE PASS. The carve clears cell by cell and can be hooked per block; the STAMP does not -
     * {@code placeInWorld} overwrites the whole footprint in one call with no per-block callback. So the box is swept
     * once, immediately before, and anything worth keeping is taken out of the world first.
     * <p>
     * ⚠ BOUNDED AND ONE-SHOT. This is a piece-sized box scanned once per stamp, not a per-tick cost - and the tag test
     * rejects the overwhelming majority of cells on a single flag read.
     * </p>
     * <p>
     * ⚠ IT DOES NOT CLEAR WHAT IT TAKES. The stamp is about to overwrite every one of these cells anyway, so removing
     * the block here would be wasted work - except for CONTAINERS, whose contents {@code capture} drains so the same
     * items cannot also spill out when the block is replaced.
     * </p>
     */
    public static void sweepBeforeStamp(ServerLevel level, HiveLocation location, BoundingBox box) {
        var pos = new BlockPos.MutableBlockPos();

        for (var x = box.minX(); x <= box.maxX(); x++) {
            for (var y = box.minY(); y <= box.maxY(); y++) {
                for (var z = box.minZ(); z <= box.maxZ(); z++) {
                    pos.set(x, y, z);

                    var state = level.getBlockState(pos);

                    if (state.isAir()) {
                        continue;
                    }

                    capture(level, location, pos.immutable(), state);
                }
            }
        }
    }

    /**
     * ⚠ EMPTIES THE SOURCE AS IT READS IT. If the deposit later fails and the overflow is voided, the chest must not
     * ALSO drop its contents when the block is cleared - that would duplicate everything the hive failed to store.
     */
    private static void drain(Container container, List<ItemStack> haul) {
        for (var slot = 0; slot < container.getContainerSize(); slot++) {
            var stack = container.getItem(slot);

            if (stack.isEmpty()) {
                continue;
            }

            haul.add(stack.copy());
            container.setItem(slot, ItemStack.EMPTY);
        }
    }

    /**
     * Push a haul into the hive's containers, in order, until it fits or runs out of room.
     * <p>
     * ⚠ OVERFLOW IS VOIDED, per [stated] "I would say void it 8 doublechests of loot should be enough". It is dropped
     * on the floor by NOBODY: a carve that scattered items into a half-dug tunnel would litter the hive with entities
     * and, worse, put them somewhere a hopper could farm.
     * </p>
     */
    private static void deposit(ServerLevel level, HiveLocation location, List<ItemStack> haul) {
        var containers = containersFor(level, location);

        if (containers.isEmpty()) {
            return; // no chamber, no containers, nothing to do - the haul is voided
        }

        for (var stack : haul) {
            var remainder = stack;

            for (var containerPos : containers) {
                if (remainder.isEmpty()) {
                    break;
                }

                if (level.getBlockEntity(containerPos) instanceof ResinContainerBlockEntity container) {
                    remainder = container.offerSalvage(remainder);
                }
            }
            // Whatever is left over falls out of scope here - voided, deliberately.
        }
    }

    /**
     * The location's resin containers, cached.
     * <p>
     * ⚠ ONLY LOADED CHUNKS ARE SCANNED. Forcing a chunk load to find a container would drag the queen chamber into
     * memory every time a distant dig turned up an ore, which is the opposite of what a cache is for. An unloaded
     * chamber simply means this dig's salvage is voided.
     * </p>
     */
    private static List<BlockPos> containersFor(ServerLevel level, HiveLocation location) {
        var key = CacheKey.of(location);
        var cached = CONTAINER_CACHE.get(key);
        var now = level.getGameTime();

        if (cached != null && now - cached.builtAtTick < CACHE_LIFETIME_TICKS) {
            return cached.positions;
        }

        var positions = findContainers(level, location);
        CONTAINER_CACHE.put(key, new CachedContainers(positions, now));

        return positions;
    }

    /**
     * The hive's deposit chests: the resin containers in its core chamber.
     * <p>
     * ⚠⚠ THE QUEEN CHAMBER ONLY - THE **OUTPUT** SIDE IS NARROW ON PURPOSE. [stated] "the input chests are only in the
     * core chamber." Do not confuse this with the capture side: what the hive SALVAGES is already hive-wide, every ore
     * and every loot chest the carve or the stamp destroys anywhere in the footprint. This is only about where the haul
     * is DEPOSITED, and the answer is the eight chests in the core.
     * </p>
     * <p>
     * ⚠ THAT ALSO MEANS A CONTAINER A PLAYER PLACES ELSEWHERE IN THE HIVE IS THEIRS. The hive will not post dungeon
     * loot into a chest someone stashed in a corridor, which is the behaviour you want the moment a player starts
     * building inside a hive they have cleared.
     * </p>
     * <p>
     * ⚠ THE SLAB CHECK IS KEPT even though the chamber filter mostly subsumes it. A hive claims a COLUMN, not a plane,
     * so a queen-chamber chunk still extends from bedrock to sky - without it, a chest a player puts on the surface
     * directly above the core, or in a mine beneath it, would start receiving the hive's loot.
     * </p>
     */
    private static List<BlockPos> findContainers(ServerLevel level, HiveLocation location) {
        var positions = new ArrayList<BlockPos>();

        for (var entry : location.structurePieceByChunk().entrySet()) {
            if (!entry.getValue().contains("queen_chamber")) {
                continue;
            }

            collectFromChunk(level, entry.getKey(), location, positions);
        }

        return positions;
    }

    private static void collectFromChunk(
        ServerLevel level,
        ChunkPos chunkPos,
        HiveLocation location,
        List<BlockPos> positions
    ) {
        if (!level.hasChunk(chunkPos.x, chunkPos.z)) {
            return;
        }

        var chunk = level.getChunk(chunkPos.x, chunkPos.z);

        for (var blockEntity : chunk.getBlockEntities().values()) {
            if (!(blockEntity instanceof ResinContainerBlockEntity)) {
                continue;
            }

            // ⚠ INSIDE THE HIVE'S OWN VERTICAL BAND ONLY - see findContainers.
            if (!location.withinSlab(blockEntity.getBlockPos().getY())) {
                continue;
            }

            positions.add(blockEntity.getBlockPos());
        }
    }

    /** Drops a location's cached list, so the next salvage rebuilds it. Called when a chamber is stamped. */
    public static void invalidate(HiveLocation location) {
        CONTAINER_CACHE.remove(CacheKey.of(location));
    }

    private record CachedContainers(
        List<BlockPos> positions,
        long builtAtTick
    ) {}

    /**
     * ⚠ DIMENSION + CENTRE, because HiveLocation exposes no id of its own. Two locations cannot share a centre in a
     * dimension, so this is as unique as an id would be - and it costs nothing to derive.
     */
    private record CacheKey(
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
        BlockPos center
    ) {

        static CacheKey of(HiveLocation location) {
            return new CacheKey(location.dimension(), location.centerPos());
        }
    }
}
