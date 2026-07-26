package com.alien.common.gameplay.block.jelly;

import com.alien.common.registry.init.block.AlienBlocks;
import com.alien.common.registry.init.item.AlienItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Maps a {@link JellyType} to its raw-jelly item and full jelly block, and back. Centralizes the item/block ↔ type
 * relationships the jelly vat uses when a player fills, empties, or breaks it, so those rules live in one place.
 */
public final class JellyItems {

    private JellyItems() {}

    /** The raw-jelly item for a type (one item = one vat level). */
    public static Item rawItem(JellyType type) {
        return type == JellyType.SCOURGE
            ? AlienItems.RAW_SCOURGE_JELLY.get()
            : AlienItems.RAW_ROYAL_JELLY.get();
    }

    /** The full jelly block for a type (one block = a full vat, 9 levels). */
    public static Block fullBlock(JellyType type) {
        return type == JellyType.SCOURGE
            ? AlienBlocks.SCOURGE_JELLY_BLOCK.get()
            : AlienBlocks.ROYAL_JELLY_BLOCK.get();
    }

    /** The jelly type a raw-jelly item represents, or null if the item isn't raw jelly. */
    @Nullable
    public static JellyType typeOfRawItem(ItemStack stack) {
        if (stack.is(AlienItems.RAW_ROYAL_JELLY.get())) {
            return JellyType.ROYAL;
        }
        if (stack.is(AlienItems.RAW_SCOURGE_JELLY.get())) {
            return JellyType.SCOURGE;
        }
        return null;
    }

    /** The jelly type a jelly block item represents, or null if the item isn't a jelly block. */
    @Nullable
    public static JellyType typeOfBlockItem(ItemStack stack) {
        if (stack.is(AlienBlocks.ROYAL_JELLY_BLOCK.get().asItem())) {
            return JellyType.ROYAL;
        }
        if (stack.is(AlienBlocks.SCOURGE_JELLY_BLOCK.get().asItem())) {
            return JellyType.SCOURGE;
        }
        return null;
    }
}
