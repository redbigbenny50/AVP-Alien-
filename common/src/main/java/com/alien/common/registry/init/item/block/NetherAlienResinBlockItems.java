package com.alien.common.registry.init.item.block;

import com.alien.Alien;
import com.alien.common.registry.init.block.NetherAlienResinBlocks;
import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

public class NetherAlienResinBlockItems {

    private static final BLibRegistry<Item> REGISTRY = Alien.MOD.registries().create(BuiltInRegistries.ITEM);

    public static final BLibHolder<BlockItem> NETHER_RESIN = create(
        "nether_resin",
        NetherAlienResinBlocks.NETHER_RESIN,
        new Item.Properties().fireResistant()
    );

    public static final BLibHolder<BlockItem> NETHER_RESIN_BRICKS = create(
        "nether_resin_bricks",
        NetherAlienResinBlocks.NETHER_RESIN_BRICKS
    );

    public static final BLibHolder<BlockItem> NETHER_RESIN_BRICK_SLAB = create(
        "nether_resin_brick_slab",
        NetherAlienResinBlocks.NETHER_RESIN_BRICK_SLAB
    );

    public static final BLibHolder<BlockItem> NETHER_RESIN_BRICK_STAIRS = create(
        "nether_resin_brick_stairs",
        NetherAlienResinBlocks.NETHER_RESIN_BRICK_STAIRS
    );

    public static final BLibHolder<BlockItem> NETHER_RESIN_BRICK_WALL = create(
        "nether_resin_brick_wall",
        NetherAlienResinBlocks.NETHER_RESIN_BRICK_WALL
    );

    public static final BLibHolder<BlockItem> NETHER_RESIN_NODE = create(
        "nether_resin_node",
        NetherAlienResinBlocks.NETHER_RESIN_NODE,
        new Item.Properties().fireResistant()
    );

    public static final BLibHolder<BlockItem> NETHER_RESIN_SLAB = create(
        "nether_resin_slab",
        NetherAlienResinBlocks.NETHER_RESIN_SLAB
    );

    public static final BLibHolder<BlockItem> NETHER_RESIN_STAIRS = create(
        "nether_resin_stairs",
        NetherAlienResinBlocks.NETHER_RESIN_STAIRS
    );

    public static final BLibHolder<BlockItem> NETHER_RESIN_VEIN = create(
        "nether_resin_vein",
        NetherAlienResinBlocks.NETHER_RESIN_VEIN,
        new Item.Properties().fireResistant()
    );

    public static final BLibHolder<BlockItem> NETHER_RESIN_WEB = create(
        "nether_resin_web",
        NetherAlienResinBlocks.NETHER_RESIN_WEB,
        new Item.Properties().fireResistant()
    );

    public static final BLibHolder<BlockItem> NETHER_RESIN_VENT = create(
        "nether_resin_vent",
        NetherAlienResinBlocks.NETHER_RESIN_VENT
    );

    public static final BLibHolder<BlockItem> RIBBED_NETHER_RESIN = create(
        "ribbed_nether_resin",
        NetherAlienResinBlocks.RIBBED_NETHER_RESIN
    );

    public static final BLibHolder<BlockItem> SMOOTH_NETHER_RESIN = create(
        "smooth_nether_resin",
        NetherAlienResinBlocks.SMOOTH_NETHER_RESIN
    );

    public static final BLibHolder<BlockItem> SMOOTH_NETHER_RESIN_SLAB = create(
        "smooth_nether_resin_slab",
        NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_SLAB
    );

    public static final BLibHolder<BlockItem> SMOOTH_NETHER_RESIN_STAIRS = create(
        "smooth_nether_resin_stairs",
        NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_STAIRS
    );

    public static final BLibHolder<BlockItem> SMOOTH_NETHER_RESIN_WALL = create(
        "smooth_nether_resin_wall",
        NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_WALL
    );

    public static final BLibHolder<BlockItem> RIBBED_NETHER_RESIN_SLAB = create(
        "ribbed_nether_resin_slab",
        NetherAlienResinBlocks.RIBBED_NETHER_RESIN_SLAB
    );

    public static final BLibHolder<BlockItem> RIBBED_NETHER_RESIN_STAIRS = create(
        "ribbed_nether_resin_stairs",
        NetherAlienResinBlocks.RIBBED_NETHER_RESIN_STAIRS
    );

    public static final BLibHolder<BlockItem> NETHER_RESIN_BONE = create(
        "nether_resin_bone",
        NetherAlienResinBlocks.NETHER_RESIN_BONE
    );

    public static final BLibHolder<BlockItem> NETHER_RESIN_BONE_SLAB = create(
        "nether_resin_bone_slab",
        NetherAlienResinBlocks.NETHER_RESIN_BONE_SLAB
    );

    public static final BLibHolder<BlockItem> NETHER_RESIN_BONE_STAIRS = create(
        "nether_resin_bone_stairs",
        NetherAlienResinBlocks.NETHER_RESIN_BONE_STAIRS
    );

    public static final BLibHolder<BlockItem> NETHER_RESIN_DOORWAY = create(
        "nether_resin_doorway",
        NetherAlienResinBlocks.NETHER_RESIN_DOORWAY
    );

    public static final BLibHolder<BlockItem> NETHER_RESIN_ETCHED = create(
        "nether_resin_etched",
        NetherAlienResinBlocks.NETHER_RESIN_ETCHED
    );

    public static final BLibHolder<BlockItem> NETHER_RESIN_ETCHED_SLAB = create(
        "nether_resin_etched_slab",
        NetherAlienResinBlocks.NETHER_RESIN_ETCHED_SLAB
    );

    public static final BLibHolder<BlockItem> NETHER_RESIN_ETCHED_STAIRS = create(
        "nether_resin_etched_stairs",
        NetherAlienResinBlocks.NETHER_RESIN_ETCHED_STAIRS
    );

    public static final BLibHolder<BlockItem> NETHER_RESIN_SPINE = create(
        "nether_resin_spine",
        NetherAlienResinBlocks.NETHER_RESIN_SPINE
    );

    public static final BLibHolder<BlockItem> NETHER_RESIN_STRETCHED = create(
        "nether_resin_stretched",
        NetherAlienResinBlocks.NETHER_RESIN_STRETCHED
    );

    public static final BLibHolder<BlockItem> NETHER_RESIN_STRETCHED_SLAB = create(
        "nether_resin_stretched_slab",
        NetherAlienResinBlocks.NETHER_RESIN_STRETCHED_SLAB
    );

    public static final BLibHolder<BlockItem> NETHER_RESIN_STRETCHED_STAIRS = create(
        "nether_resin_stretched_stairs",
        NetherAlienResinBlocks.NETHER_RESIN_STRETCHED_STAIRS
    );

    public static final BLibHolder<BlockItem> NETHER_RESIN_TENDRIL = create(
        "nether_resin_tendril",
        NetherAlienResinBlocks.NETHER_RESIN_TENDRIL
    );

    public static final BLibHolder<BlockItem> NETHER_RESIN_TENDRIL_SLAB = create(
        "nether_resin_tendril_slab",
        NetherAlienResinBlocks.NETHER_RESIN_TENDRIL_SLAB
    );

    public static final BLibHolder<BlockItem> NETHER_RESIN_TENDRIL_STAIRS = create(
        "nether_resin_tendril_stairs",
        NetherAlienResinBlocks.NETHER_RESIN_TENDRIL_STAIRS
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
