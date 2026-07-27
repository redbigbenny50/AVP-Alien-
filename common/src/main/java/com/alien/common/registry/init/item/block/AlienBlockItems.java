package com.alien.common.registry.init.item.block;

import com.alien.Alien;
import com.alien.common.gameplay.item.RoyalJellyBlockItem;
import com.alien.common.registry.init.block.AlienBlocks;
import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

public class AlienBlockItems {

    private static final BLibRegistry<Item> REGISTRY = Alien.MOD.registries().create(BuiltInRegistries.ITEM);

    public static final BLibHolder<BlockItem> ROYAL_JELLY_BLOCK = createWithSupplier(
        "royal_jelly_block",
        RoyalJellyBlockItem::new
    );

    public static final BLibHolder<BlockItem> SCOURGE_JELLY_BLOCK = createWithSupplier(
        "scourge_jelly_block",
        () -> new BlockItem(AlienBlocks.SCOURGE_JELLY_BLOCK.get(), new Item.Properties().stacksTo(64))
    );

    public static final BLibHolder<BlockItem> IRRADIATED_JELLY_BLOCK = createWithSupplier(
        "irradiated_jelly_block",
        () -> new BlockItem(AlienBlocks.IRRADIATED_JELLY_BLOCK.get(), new Item.Properties().stacksTo(64))
    );

    private static BLibHolder<BlockItem> createWithSupplier(String id, Supplier<BlockItem> blockItemSupplier) {
        return REGISTRY.createHolder(id, blockItemSupplier);
    }

    public static void initialize() {
        REGISTRY.registerAll();
    }
}
