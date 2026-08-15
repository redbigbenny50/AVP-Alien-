package com.alien.common.registry.init.item.block;

import com.alien.Alien;
import com.alien.common.registry.init.block.AlienResinBlocks;
import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

public class AlienResinBlockItems {

    private static final BLibRegistry<Item> REGISTRY = Alien.MOD.registries().create(BuiltInRegistries.ITEM);

    public static final BLibHolder<BlockItem> RESIN = create("resin", AlienResinBlocks.RESIN);

    public static final BLibHolder<BlockItem> RESIN_BRICKS = create("resin_bricks", AlienResinBlocks.RESIN_BRICKS);

    public static final BLibHolder<BlockItem> RESIN_BRICK_SLAB = create(
        "resin_brick_slab",
        AlienResinBlocks.RESIN_BRICK_SLAB
    );

    public static final BLibHolder<BlockItem> RESIN_BRICK_STAIRS = create(
        "resin_brick_stairs",
        AlienResinBlocks.RESIN_BRICK_STAIRS
    );

    public static final BLibHolder<BlockItem> RESIN_BRICK_WALL = create(
        "resin_brick_wall",
        AlienResinBlocks.RESIN_BRICK_WALL
    );

    public static final BLibHolder<BlockItem> RESIN_NODE = create("resin_node", AlienResinBlocks.RESIN_NODE);

    public static final BLibHolder<BlockItem> RESIN_SLAB = create(
        "resin_slab",
        AlienResinBlocks.RESIN_SLAB
    );

    public static final BLibHolder<BlockItem> RESIN_STAIRS = create(
        "resin_stairs",
        AlienResinBlocks.RESIN_STAIRS
    );

    public static final BLibHolder<BlockItem> RESIN_VEIN = create("resin_vein", AlienResinBlocks.RESIN_VEIN);

    public static final BLibHolder<BlockItem> RESIN_VENT = create("resin_vent", AlienResinBlocks.RESIN_VENT);

    public static final BLibHolder<BlockItem> RESIN_WEB = create("resin_web", AlienResinBlocks.RESIN_WEB);

    public static final BLibHolder<BlockItem> RIBBED_RESIN = create("ribbed_resin", AlienResinBlocks.RIBBED_RESIN);

    public static final BLibHolder<BlockItem> RIBBED_RESIN_SLAB = create(
        "ribbed_resin_slab",
        AlienResinBlocks.RIBBED_RESIN_SLAB
    );

    public static final BLibHolder<BlockItem> RIBBED_RESIN_STAIRS = create(
        "ribbed_resin_stairs",
        AlienResinBlocks.RIBBED_RESIN_STAIRS
    );

    public static final BLibHolder<BlockItem> SMOOTH_RESIN = create("smooth_resin", AlienResinBlocks.SMOOTH_RESIN);

    public static final BLibHolder<BlockItem> SMOOTH_RESIN_SLAB = create(
        "smooth_resin_slab",
        AlienResinBlocks.SMOOTH_RESIN_SLAB
    );

    public static final BLibHolder<BlockItem> SMOOTH_RESIN_STAIRS = create(
        "smooth_resin_stairs",
        AlienResinBlocks.SMOOTH_RESIN_STAIRS
    );

    public static final BLibHolder<BlockItem> SMOOTH_RESIN_WALL = create(
        "smooth_resin_wall",
        AlienResinBlocks.SMOOTH_RESIN_WALL
    );

    public static final BLibHolder<BlockItem> RESIN_BONE = create("resin_bone", AlienResinBlocks.RESIN_BONE);

    public static final BLibHolder<BlockItem> RESIN_BONE_SLAB = create(
        "resin_bone_slab",
        AlienResinBlocks.RESIN_BONE_SLAB
    );

    public static final BLibHolder<BlockItem> RESIN_BONE_STAIRS = create(
        "resin_bone_stairs",
        AlienResinBlocks.RESIN_BONE_STAIRS
    );

    public static final BLibHolder<BlockItem> RESIN_DOORWAY = create("resin_doorway", AlienResinBlocks.RESIN_DOORWAY);

    public static final BLibHolder<BlockItem> RESIN_ETCHED = create("resin_etched", AlienResinBlocks.RESIN_ETCHED);

    public static final BLibHolder<BlockItem> RESIN_ETCHED_SLAB = create(
        "resin_etched_slab",
        AlienResinBlocks.RESIN_ETCHED_SLAB
    );

    public static final BLibHolder<BlockItem> RESIN_ETCHED_STAIRS = create(
        "resin_etched_stairs",
        AlienResinBlocks.RESIN_ETCHED_STAIRS
    );

    public static final BLibHolder<BlockItem> RESIN_SPINE = create("resin_spine", AlienResinBlocks.RESIN_SPINE);

    public static final BLibHolder<BlockItem> RESIN_STRETCHED = create("resin_stretched", AlienResinBlocks.RESIN_STRETCHED);

    public static final BLibHolder<BlockItem> RESIN_STRETCHED_SLAB = create(
        "resin_stretched_slab",
        AlienResinBlocks.RESIN_STRETCHED_SLAB
    );

    public static final BLibHolder<BlockItem> RESIN_STRETCHED_STAIRS = create(
        "resin_stretched_stairs",
        AlienResinBlocks.RESIN_STRETCHED_STAIRS
    );

    public static final BLibHolder<BlockItem> RESIN_TENDRIL = create("resin_tendril", AlienResinBlocks.RESIN_TENDRIL);

    public static final BLibHolder<BlockItem> RESIN_TENDRIL_SLAB = create(
        "resin_tendril_slab",
        AlienResinBlocks.RESIN_TENDRIL_SLAB
    );

    public static final BLibHolder<BlockItem> RESIN_TENDRIL_STAIRS = create(
        "resin_tendril_stairs",
        AlienResinBlocks.RESIN_TENDRIL_STAIRS
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
