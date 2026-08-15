package com.alien.common.registry.init.item.block;

import com.alien.Alien;
import com.alien.common.registry.init.block.IrradiatedAlienResinBlocks;
import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

public class IrradiatedAlienResinBlockItems {

    private static final BLibRegistry<Item> REGISTRY = Alien.MOD.registries().create(BuiltInRegistries.ITEM);

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN = create(
        "irradiated_resin",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN
    );

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN_BRICKS = create(
        "irradiated_resin_bricks",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICKS
    );

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN_BRICK_SLAB = create(
        "irradiated_resin_brick_slab",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_SLAB
    );

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN_BRICK_STAIRS = create(
        "irradiated_resin_brick_stairs",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_STAIRS
    );

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN_BRICK_WALL = create(
        "irradiated_resin_brick_wall",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_WALL
    );

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN_NODE = create(
        "irradiated_resin_node",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_NODE
    );

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN_SLAB = create(
        "irradiated_resin_slab",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_SLAB
    );

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN_STAIRS = create(
        "irradiated_resin_stairs",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STAIRS
    );

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN_VEIN = create(
        "irradiated_resin_vein",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_VEIN
    );

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN_WEB = create(
        "irradiated_resin_web",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_WEB
    );

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN_VENT = create(
        "irradiated_resin_vent",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_VENT
    );

    public static final BLibHolder<BlockItem> RIBBED_IRRADIATED_RESIN = create(
        "ribbed_irradiated_resin",
        IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN
    );

    public static final BLibHolder<BlockItem> SMOOTH_IRRADIATED_RESIN = create(
        "smooth_irradiated_resin",
        IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN
    );

    public static final BLibHolder<BlockItem> SMOOTH_IRRADIATED_RESIN_SLAB = create(
        "smooth_irradiated_resin_slab",
        IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_SLAB
    );

    public static final BLibHolder<BlockItem> SMOOTH_IRRADIATED_RESIN_STAIRS = create(
        "smooth_irradiated_resin_stairs",
        IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_STAIRS
    );

    public static final BLibHolder<BlockItem> SMOOTH_IRRADIATED_RESIN_WALL = create(
        "smooth_irradiated_resin_wall",
        IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_WALL
    );

    public static final BLibHolder<BlockItem> RIBBED_IRRADIATED_RESIN_SLAB = create(
        "ribbed_irradiated_resin_slab",
        IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN_SLAB
    );

    public static final BLibHolder<BlockItem> RIBBED_IRRADIATED_RESIN_STAIRS = create(
        "ribbed_irradiated_resin_stairs",
        IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN_STAIRS
    );

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN_BONE = create(
        "irradiated_resin_bone",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE
    );

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN_BONE_SLAB = create(
        "irradiated_resin_bone_slab",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE_SLAB
    );

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN_BONE_STAIRS = create(
        "irradiated_resin_bone_stairs",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE_STAIRS
    );

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN_DOORWAY = create(
        "irradiated_resin_doorway",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_DOORWAY
    );

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN_ETCHED = create(
        "irradiated_resin_etched",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED
    );

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN_ETCHED_SLAB = create(
        "irradiated_resin_etched_slab",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED_SLAB
    );

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN_ETCHED_STAIRS = create(
        "irradiated_resin_etched_stairs",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED_STAIRS
    );

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN_SPINE = create(
        "irradiated_resin_spine",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_SPINE
    );

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN_STRETCHED = create(
        "irradiated_resin_stretched",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED
    );

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN_STRETCHED_SLAB = create(
        "irradiated_resin_stretched_slab",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED_SLAB
    );

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN_STRETCHED_STAIRS = create(
        "irradiated_resin_stretched_stairs",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED_STAIRS
    );

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN_TENDRIL = create(
        "irradiated_resin_tendril",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL
    );

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN_TENDRIL_SLAB = create(
        "irradiated_resin_tendril_slab",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL_SLAB
    );

    public static final BLibHolder<BlockItem> IRRADIATED_RESIN_TENDRIL_STAIRS = create(
        "irradiated_resin_tendril_stairs",
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL_STAIRS
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
