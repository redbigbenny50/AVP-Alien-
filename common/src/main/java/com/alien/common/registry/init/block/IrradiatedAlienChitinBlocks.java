package com.alien.common.registry.init.block;

import com.alien.Alien;
import com.alien.common.registry.init.block.property.AlienBlockProperties;
import com.blib.api.common.block.v1.BlockPropertyBuilder;
import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;

import java.util.function.Supplier;

public class IrradiatedAlienChitinBlocks {

    private static final BLibRegistry<Block> REGISTRY = Alien.MOD.registries().create(BuiltInRegistries.BLOCK);

    public static final BLibHolder<Block> CHISELED_IRRADIATED_CHITIN_BRICKS = create(
        "chiseled_irradiated_chitin_bricks",
        AlienBlockProperties.IRRADIATED_CHITIN
    );

    public static final BLibHolder<Block> CHISELED_IRRADIATED_CHITIN_BRICKS_EMBRYO = create(
        "chiseled_irradiated_chitin_bricks_embryo",
        AlienBlockProperties.IRRADIATED_CHITIN
    );

    public static final BLibHolder<Block> IRRADIATED_CHITIN_BLOCK = create(
        "irradiated_chitin_block",
        AlienBlockProperties.IRRADIATED_CHITIN
    );

    public static final BLibHolder<Block> IRRADIATED_CHITIN_BLOCK_SLAB = create(
        "irradiated_chitin_block_slab",
        () -> new SlabBlock(AlienBlockProperties.IRRADIATED_CHITIN.build())
    );

    public static final BLibHolder<Block> IRRADIATED_CHITIN_BLOCK_STAIRS = create(
        "irradiated_chitin_block_stairs",
        () -> new StairBlock(
            IRRADIATED_CHITIN_BLOCK.get().defaultBlockState(),
            AlienBlockProperties.IRRADIATED_CHITIN.build()
        )
    );

    public static final BLibHolder<Block> IRRADIATED_CHITIN_BLOCK_WALL = create(
        "irradiated_chitin_block_wall",
        () -> new WallBlock(AlienBlockProperties.IRRADIATED_CHITIN.build())
    );

    public static final BLibHolder<Block> IRRADIATED_CHITIN_BRICKS = create(
        "irradiated_chitin_bricks",
        AlienBlockProperties.IRRADIATED_CHITIN
    );

    public static final BLibHolder<Block> IRRADIATED_CHITIN_BRICK_SLAB = create(
        "irradiated_chitin_brick_slab",
        () -> new SlabBlock(AlienBlockProperties.IRRADIATED_CHITIN.build())
    );

    public static final BLibHolder<Block> IRRADIATED_CHITIN_BRICK_STAIRS = create(
        "irradiated_chitin_brick_stairs",
        () -> new StairBlock(
            IRRADIATED_CHITIN_BRICKS.get().defaultBlockState(),
            AlienBlockProperties.IRRADIATED_CHITIN.build()
        )
    );

    public static final BLibHolder<Block> IRRADIATED_CHITIN_BRICK_WALL = create(
        "irradiated_chitin_brick_wall",
        () -> new WallBlock(AlienBlockProperties.IRRADIATED_CHITIN.build())
    );

    public static final BLibHolder<Block> POLISHED_IRRADIATED_CHITIN = create(
        "polished_irradiated_chitin",
        AlienBlockProperties.IRRADIATED_CHITIN
    );

    public static final BLibHolder<Block> POLISHED_IRRADIATED_CHITIN_SLAB = create(
        "polished_irradiated_chitin_slab",
        () -> new SlabBlock(AlienBlockProperties.IRRADIATED_CHITIN.build())
    );

    public static final BLibHolder<Block> POLISHED_IRRADIATED_CHITIN_STAIRS = create(
        "polished_irradiated_chitin_stairs",
        () -> new StairBlock(
            POLISHED_IRRADIATED_CHITIN.get().defaultBlockState(),
            AlienBlockProperties.IRRADIATED_CHITIN.build()
        )
    );

    public static final BLibHolder<Block> POLISHED_IRRADIATED_CHITIN_WALL = create(
        "polished_irradiated_chitin_wall",
        () -> new WallBlock(AlienBlockProperties.IRRADIATED_CHITIN.build())
    );

    private static BLibHolder<Block> create(String path, BlockPropertyBuilder blockPropertyBuilder) {
        return create(path, () -> new Block(blockPropertyBuilder.build()));
    }

    private static <T extends Block> BLibHolder<T> create(String path, Supplier<T> blockSupplier) {
        return REGISTRY.createHolder(path, blockSupplier);
    }

    public static void initialize() {
        REGISTRY.registerAll();
    }
}
