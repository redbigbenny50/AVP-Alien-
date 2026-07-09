package com.alien.common.gameplay.hive.economy;

import com.alien.common.gameplay.block.entity.jelly.JellyVatBlockEntity;
import com.alien.common.gameplay.block.jelly.JellyType;
import com.alien.common.gameplay.block.jelly.JellyVatBlock;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.structure.HiveChamberSlots;
import com.alien.common.registry.init.block.AlienBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * The hive's PHYSICAL jelly store - the agreed bank+vats model. Production and spending live on the invisible bank;
 * each growth cycle a TRICKLE moves surplus (above a working reserve) from the bank into the vats, with a bulk catch-up
 * on the first cycle after load. The vats are a SIDE ACCOUNT the hive does not touch unless the bank cannot cover a
 * cost ({@link #coverShortfall} - vaults drain before the royal chambers; the queen's stores go last). Jelly raided
 * from a vat is genuinely stolen from the hive's savings; jelly poured in genuinely adds to them. The class also
 * maintains the furniture: missing vats regrow on their slots, strays are pruned (contents salvaged to the bank).
 */
public final class JellyVatDisplay {

    private static final int TRICKLE_PER_CYCLE = 3; // bank -> vats per growth cycle (the "interest" pacing)

    private static final int BANK_WORKING_RESERVE = 20; // liquid jelly the bank keeps; only surplus goes to storage

    /** Hives whose vats have caught up since load - the first sync bulk-fills what accrued while unloaded. */
    private static final java.util.Map<HiveLocation, Boolean> CAUGHT_UP =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private JellyVatDisplay() {}

    /** Maintains vats and trickles bank surplus into them. Runs on the growth cadence. */
    public static void sync(ServerLevel level, HiveLocation location) {
        var chambers = chambersRoyalFirst(location);
        if (chambers.isEmpty()) {
            return;
        }
        var vatBlock = AlienBlocks.JELLY_VAT.get();
        int slotRow = location.hiveFloorY() + 1;

        // Furniture maintenance: prune stray vats (salvaging their contents into the bank - never destroy jelly),
        // regrow missing vats on their slots, and keep slot vats typed royal.
        for (ChunkPos chamber : chambers) {
            if (!level.isLoaded(chamber.getWorldPosition())) {
                continue;
            }
            var slots = HiveChamberSlots.vatSlots(level, location, chamber);
            var scan = new BlockPos.MutableBlockPos();
            for (int x = chamber.getMinBlockX(); x <= chamber.getMaxBlockX(); x++) {
                for (int z = chamber.getMinBlockZ(); z <= chamber.getMaxBlockZ(); z++) {
                    scan.set(x, slotRow, z);
                    if (level.getBlockState(scan).is(vatBlock) && !slots.contains(scan)) {
                        if (level.getBlockEntity(scan) instanceof JellyVatBlockEntity stray && stray.getFillLevel() > 0) {
                            location.setRoyalJelly(location.royalJelly() + stray.getFillLevel());
                        }
                        level.setBlock(scan.immutable(), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
            for (var slot : slots) {
                var state = level.getBlockState(slot);
                if (state.isAir()) {
                    level.setBlock(slot, vatBlock.defaultBlockState(), 3);
                    state = level.getBlockState(slot);
                }
                if (state.is(vatBlock) && state.getValue(JellyVatBlock.JELLY_TYPE) != JellyType.ROYAL) {
                    level.setBlock(slot, state.setValue(JellyVatBlock.JELLY_TYPE, JellyType.ROYAL), 3);
                }
            }
        }

        // Trickle: surplus above the working reserve flows into vats with free space, royal chambers first. The first
        // sync after load has no budget cap - the catch-up fill for everything that accrued while unloaded.
        boolean catchUp = CAUGHT_UP.putIfAbsent(location, Boolean.TRUE) == null;
        int budget = catchUp ? Integer.MAX_VALUE : TRICKLE_PER_CYCLE;
        int surplus = location.royalJelly() - BANK_WORKING_RESERVE;
        if (surplus <= 0) {
            return;
        }
        int moved = 0;
        outer:
        for (ChunkPos chamber : chambers) {
            if (!level.isLoaded(chamber.getWorldPosition())) {
                continue;
            }
            for (var slot : HiveChamberSlots.vatSlots(level, location, chamber)) {
                if (moved >= budget || moved >= surplus) {
                    break outer;
                }
                if (!(level.getBlockEntity(slot) instanceof JellyVatBlockEntity vat)) {
                    continue;
                }
                int space = JellyVatBlockEntity.MAX_FILL - vat.getFillLevel();
                if (space <= 0) {
                    continue;
                }
                int add = Math.min(space, Math.min(budget, surplus) - moved);
                vat.setFillLevel(vat.getFillLevel() + add);
                moved += add;
            }
        }
        if (moved > 0) {
            location.setRoyalJelly(location.royalJelly() - moved);
        }
    }

    /**
     * The vats are the hive's LAST RESORT: when the bank cannot cover {@code needed}, withdraw the shortfall from the
     * physical store back into the bank. Vaults drain before royal chambers - the queen's stores go last. Withdrawn
     * amounts are limited to loaded chambers; a fully unloaded store cannot be tapped.
     */
    /** Overload resolving the location's level from the server (spend sites often lack a level in scope). */
    public static void coverShortfall(MinecraftServer server, HiveLocation location, int needed) {
        var level = server.getLevel(location.dimension());
        if (level != null) {
            coverShortfall(level, location, needed);
        }
    }

    public static void coverShortfall(ServerLevel level, HiveLocation location, int needed) {
        int missing = needed - location.royalJelly();
        if (missing <= 0) {
            return;
        }
        var chambers = chambersRoyalFirst(location);
        java.util.Collections.reverse(chambers); // vaults first, royal chambers last
        int withdrawn = 0;
        for (ChunkPos chamber : chambers) {
            if (withdrawn >= missing) {
                break;
            }
            if (!level.isLoaded(chamber.getWorldPosition())) {
                continue;
            }
            for (var slot : HiveChamberSlots.vatSlots(level, location, chamber)) {
                if (withdrawn >= missing) {
                    break;
                }
                if (!(level.getBlockEntity(slot) instanceof JellyVatBlockEntity vat) || vat.getFillLevel() <= 0) {
                    continue;
                }
                int take = Math.min(vat.getFillLevel(), missing - withdrawn);
                vat.setFillLevel(vat.getFillLevel() - take);
                withdrawn += take;
            }
        }
        if (withdrawn > 0) {
            location.setRoyalJelly(location.royalJelly() + withdrawn);
        }
    }

    /** Royal chambers first (the queen's premium stores fill first, drain last), then vaults; stable order. */
    private static List<ChunkPos> chambersRoyalFirst(HiveLocation location) {
        var royals = new ArrayList<ChunkPos>();
        var vaults = new ArrayList<ChunkPos>();
        for (var entry : location.structurePieceByChunk().entrySet()) {
            if (entry.getValue().contains("chamber_jelly_royal")) {
                royals.add(entry.getKey());
            } else if (entry.getValue().contains("chamber_jelly_vault")) {
                vaults.add(entry.getKey());
            }
        }
        royals.sort((a, b) -> Long.compare(a.toLong(), b.toLong()));
        vaults.sort((a, b) -> Long.compare(a.toLong(), b.toLong()));
        royals.addAll(vaults);
        return royals;
    }
}
