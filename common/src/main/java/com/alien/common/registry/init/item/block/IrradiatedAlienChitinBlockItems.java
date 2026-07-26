package com.alien.common.registry.init.item.block;

import com.alien.Alien;
import com.alien.common.registry.init.block.IrradiatedAlienChitinBlocks;
import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

public class IrradiatedAlienChitinBlockItems {

    private static final BLibRegistry<Item> REGISTRY = Alien.MOD.registries().create(BuiltInRegistries.ITEM);

    public static final BLibHolder<BlockItem> CHISELED_IRRADIATED_CHITIN_BRICKS = create(
        "chiseled_irradiated_chitin_bricks",
        IrradiatedAlienChitinBlocks.CHISELED_IRRADIATED_CHITIN_BRICKS
    );

    public static final BLibHolder<BlockItem> CHISELED_IRRADIATED_CHITIN_BRICKS_EMBRYO = create(
        "chiseled_irradiated_chitin_bricks_embryo",
        IrradiatedAlienChitinBlocks.CHISELED_IRRADIATED_CHITIN_BRICKS_EMBRYO
    );

    public static final BLibHolder<BlockItem> IRRADIATED_CHITIN_BLOCK = create(
        "irradiated_chitin_block",
        IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK
    );

    public static final BLibHolder<BlockItem> IRRADIATED_CHITIN_BLOCK_SLAB = create(
        "irradiated_chitin_block_slab",
        IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK_SLAB
    );

    public static final BLibHolder<BlockItem> IRRADIATED_CHITIN_BLOCK_STAIRS = create(
        "irradiated_chitin_block_stairs",
        IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK_STAIRS
    );

    public static final BLibHolder<BlockItem> IRRADIATED_CHITIN_BLOCK_WALL = create(
        "irradiated_chitin_block_wall",
        IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK_WALL
    );

    public static final BLibHolder<BlockItem> IRRADIATED_CHITIN_BRICKS = create(
        "irradiated_chitin_bricks",
        IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICKS
    );

    public static final BLibHolder<BlockItem> IRRADIATED_CHITIN_BRICK_SLAB = create(
        "irradiated_chitin_brick_slab",
        IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICK_SLAB
    );

    public static final BLibHolder<BlockItem> IRRADIATED_CHITIN_BRICK_STAIRS = create(
        "irradiated_chitin_brick_stairs",
        IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICK_STAIRS
    );

    public static final BLibHolder<BlockItem> IRRADIATED_CHITIN_BRICK_WALL = create(
        "irradiated_chitin_brick_wall",
        IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICK_WALL
    );

    public static final BLibHolder<BlockItem> POLISHED_IRRADIATED_CHITIN = create(
        "polished_irradiated_chitin",
        IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN
    );

    public static final BLibHolder<BlockItem> POLISHED_IRRADIATED_CHITIN_SLAB = create(
        "polished_irradiated_chitin_slab",
        IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN_SLAB
    );

    public static final BLibHolder<BlockItem> POLISHED_IRRADIATED_CHITIN_STAIRS = create(
        "polished_irradiated_chitin_stairs",
        IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN_STAIRS
    );

    public static final BLibHolder<BlockItem> POLISHED_IRRADIATED_CHITIN_WALL = create(
        "polished_irradiated_chitin_wall",
        IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN_WALL
    );

    private static BLibHolder<BlockItem> create(String id, Supplier<? extends Block> blockSupplier) {
        return create(id, blockSupplier, new Item.Properties());
    }

    private static BLibHolder<BlockItem> create(String id, Supplier<? extends Block> blockSupplier, Item.Properties properties) {
        return createWithSupplier(id, () -> new BlockItem(blockSupplier.get(), properties));
    }

    private static BLibHolder<BlockItem> createWithSupplier(String id, Supplier<BlockItem> blockItemSupplier) {
        return REGISTRY.createHolder(id, blockItemSupplier);
    }

    public static void initialize() {
        REGISTRY.registerAll();
    }
}
