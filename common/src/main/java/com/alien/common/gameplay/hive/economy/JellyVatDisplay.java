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
 * Keeps the physical jelly vats in step with the hive's jelly BANKS. Generalized over both jellies: the ROYAL bank
 * displays in the jelly vaults and royal chambers (royal chambers fill first, drain last - the queen's premium stores),
 * and the SCOURGE bank displays in the scourge chamber's vats. Bank surplus above a working reserve trickles into vats
 * each growth cycle (bulk catch-up on the first sync after load); stray vats are pruned with their contents salvaged
 * into the bank (jelly is never destroyed); missing vats regrow on their slots; and slot vats are kept typed to their
 * chamber's jelly. When a spend site's bank cannot cover a cost ({@link #coverShortfall} /
 * {@link #coverScourgeShortfall}), the shortfall is withdrawn from the physical store back into the bank (for royal:
 * vaults drain before the royal chambers). Jelly raided from vats is genuinely stolen from the hive.
 */
public final class JellyVatDisplay {

    private static final int TRICKLE_PER_CYCLE = 3; // bank -> vats per growth cycle (the "interest" pacing)

    // Liquid jelly the bank keeps; only surplus goes to storage. Kept SMALL: coverShortfall pulls jelly back
    // from vats on demand, and slow early production (~1 jelly per few minutes) never cleared the old 20 -
    // vats sat visibly empty for hours while the hive was in fact accruing jelly.
    private static final int BANK_WORKING_RESERVE = 5;

    /** Per-hive, per-bank: whether the first-sync bulk catch-up fill has run since load. */
    private static final java.util.Map<HiveLocation, java.util.Set<JellyType>> CAUGHT_UP =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private JellyVatDisplay() {}

    /** Maintains vats and trickles bank surplus into them, for BOTH banks. Runs on the growth cadence. */
    public static void sync(ServerLevel level, HiveLocation location) {
        syncBank(level, location, JellyType.ROYAL);
        syncBank(level, location, JellyType.SCOURGE);
    }

    private static void syncBank(ServerLevel level, HiveLocation location, JellyType type) {
        var chambers = chambersFor(location, type);
        if (chambers.isEmpty()) {
            return;
        }
        var vatBlock = AlienBlocks.JELLY_VAT.get();
        int slotRow = location.hiveFloorY() + 1;

        // Furniture maintenance: prune stray vats (salvaging their contents into this bank - never destroy jelly),
        // regrow missing vats on their slots, and keep slot vats typed to the chamber's jelly.
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
                            bankSet(location, type, bankGet(location, type) + stray.getFillLevel());
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
                if (state.is(vatBlock) && state.getValue(JellyVatBlock.JELLY_TYPE) != type) {
                    level.setBlock(slot, state.setValue(JellyVatBlock.JELLY_TYPE, type), 3);
                }
            }
        }

        // Trickle: surplus above the working reserve flows into vats with free space (royal: royal chambers first).
        // The first sync after load has no budget cap - the catch-up fill for what accrued while unloaded.
        boolean catchUp = CAUGHT_UP
            .computeIfAbsent(location, $ -> java.util.Collections.synchronizedSet(java.util.EnumSet.noneOf(JellyType.class)))
            .add(type);
        int budget = catchUp ? Integer.MAX_VALUE : TRICKLE_PER_CYCLE;
        int surplus = bankGet(location, type) - BANK_WORKING_RESERVE;
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
            bankSet(location, type, bankGet(location, type) - moved);
        }
    }

    /** Overload resolving the location's level from the server (spend sites often lack a level in scope). */
    public static void coverShortfall(MinecraftServer server, HiveLocation location, int needed) {
        var level = server.getLevel(location.dimension());
        if (level != null) {
            coverShortfall(level, location, needed);
        }
    }

    /** Royal-bank shortfall cover: vaults drain before royal chambers - the queen's stores go last. */
    public static void coverShortfall(ServerLevel level, HiveLocation location, int needed) {
        cover(level, location, needed, JellyType.ROYAL);
    }

    /** Scourge-bank shortfall cover from the scourge chamber's vats. */
    public static void coverScourgeShortfall(MinecraftServer server, HiveLocation location, int needed) {
        var level = server.getLevel(location.dimension());
        if (level != null) {
            cover(level, location, needed, JellyType.SCOURGE);
        }
    }

    private static void cover(ServerLevel level, HiveLocation location, int needed, JellyType type) {
        int missing = needed - bankGet(location, type);
        if (missing <= 0) {
            return;
        }
        var chambers = chambersFor(location, type);
        java.util.Collections.reverse(chambers); // royal: vaults first, royal chambers last
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
            bankSet(location, type, bankGet(location, type) + withdrawn);
        }
    }

    private static int bankGet(HiveLocation location, JellyType type) {
        return type == JellyType.ROYAL ? location.royalJelly() : location.scourgeJelly();
    }

    private static void bankSet(HiveLocation location, JellyType type, int value) {
        if (type == JellyType.ROYAL) {
            location.setRoyalJelly(value);
        } else {
            location.setScourgeJelly(value);
        }
    }

    /**
     * The chambers displaying a bank. ROYAL: royal chambers first (fill first, drain last), then vaults. SCOURGE: the
     * scourge chamber(s). Stable order.
     */
    private static List<ChunkPos> chambersFor(HiveLocation location, JellyType type) {
        var primary = new ArrayList<ChunkPos>();
        var secondary = new ArrayList<ChunkPos>();
        for (var entry : location.structurePieceByChunk().entrySet()) {
            if (type == JellyType.ROYAL) {
                if (entry.getValue().contains("chamber_jelly_royal")) {
                    primary.add(entry.getKey());
                } else if (entry.getValue().contains("chamber_jelly_vault")) {
                    secondary.add(entry.getKey());
                }
            } else if (entry.getValue().contains("chamber_scourge")) {
                primary.add(entry.getKey());
            }
        }
        primary.sort((a, b) -> Long.compare(a.toLong(), b.toLong()));
        secondary.sort((a, b) -> Long.compare(a.toLong(), b.toLong()));
        primary.addAll(secondary);
        return primary;
    }
}
