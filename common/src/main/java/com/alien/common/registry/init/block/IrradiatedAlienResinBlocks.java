package com.alien.common.registry.init.block;

import com.alien.Alien;
import com.alien.common.gameplay.block.resin.IrradiatedResinBlock;
import com.alien.common.gameplay.block.resin.node.IrradiatedResinNodeBlock;
import com.alien.common.gameplay.block.resin.vein.IrradiatedResinVeinBlock;
import com.alien.common.gameplay.block.resin.vein.ResinVeinBlock;
import com.alien.common.gameplay.block.resin.vent.ResinVentBlock;
import com.alien.common.gameplay.block.resin.web.IrradiatedResinWebBlock;
import com.alien.common.registry.init.block.property.AlienBlockProperties;
import com.blib.api.common.block.v1.BlockPropertyBuilder;
import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;

import java.util.function.Supplier;

public class IrradiatedAlienResinBlocks {

    private static final BLibRegistry<Block> REGISTRY = Alien.MOD.registries().create(BuiltInRegistries.BLOCK);

    public static final BLibHolder<Block> IRRADIATED_RESIN = create(
        "irradiated_resin",
        () -> new IrradiatedResinBlock(AlienBlockProperties.IRRADIATED_RESIN.build())
    );

    public static final BLibHolder<Block> IRRADIATED_RESIN_BRICKS = create(
        "irradiated_resin_bricks",
        AlienBlockProperties.IRRADIATED_RESIN
    );

    public static final BLibHolder<Block> IRRADIATED_RESIN_BRICK_SLAB = create(
        "irradiated_resin_brick_slab",
        () -> new SlabBlock(AlienBlockProperties.IRRADIATED_RESIN.build())
    );

    public static final BLibHolder<Block> IRRADIATED_RESIN_BRICK_STAIRS = create(
        "irradiated_resin_brick_stairs",
        () -> new StairBlock(
            IRRADIATED_RESIN_BRICKS.get().defaultBlockState(),
            AlienBlockProperties.IRRADIATED_RESIN.build()
        )
    );

    public static final BLibHolder<Block> IRRADIATED_RESIN_BRICK_WALL = create(
        "irradiated_resin_brick_wall",
        () -> new WallBlock(AlienBlockProperties.IRRADIATED_RESIN.build())
    );

    public static final BLibHolder<Block> IRRADIATED_RESIN_STAIRS = create(
        "irradiated_resin_stairs",
        () -> new StairBlock(
            IRRADIATED_RESIN.get().defaultBlockState(),
            AlienBlockProperties.IRRADIATED_RESIN.build()
        )
    );

    public static final BLibHolder<Block> IRRADIATED_RESIN_SLAB = create(
        "irradiated_resin_slab",
        () -> new SlabBlock(AlienBlockProperties.IRRADIATED_RESIN.build())
    );

    public static final BLibHolder<Block> IRRADIATED_RESIN_NODE = create(
        "irradiated_resin_node",
        () -> new IrradiatedResinNodeBlock(AlienBlockProperties.IRRADIATED_RESIN.build())
    );

    public static final BLibHolder<ResinVeinBlock> IRRADIATED_RESIN_VEIN = create(
        "irradiated_resin_vein",
        () -> new IrradiatedResinVeinBlock(AlienBlockProperties.IRRADIATED_RESIN_VEIN.build())
    );

    public static final BLibHolder<ResinVentBlock> IRRADIATED_RESIN_VENT = create(
        "irradiated_resin_vent",
        () -> new ResinVentBlock(AlienBlockProperties.IRRADIATED_RESIN.build())
    );

    public static final BLibHolder<Block> IRRADIATED_RESIN_WEB = create(
        "irradiated_resin_web",
        () -> new IrradiatedResinWebBlock(AlienBlockProperties.IRRADIATED_RESIN_WEB.build())
    );

    public static final BLibHolder<Block> RIBBED_IRRADIATED_RESIN = create(
        "ribbed_irradiated_resin",
        () -> new RotatedPillarBlock(AlienBlockProperties.IRRADIATED_RESIN.build())
    );

    public static final BLibHolder<Block> SMOOTH_IRRADIATED_RESIN = create(
        "smooth_irradiated_resin",
        AlienBlockProperties.IRRADIATED_RESIN
    );

    public static final BLibHolder<Block> SMOOTH_IRRADIATED_RESIN_SLAB = create(
        "smooth_irradiated_resin_slab",
        () -> new SlabBlock(AlienBlockProperties.IRRADIATED_RESIN.build())
    );

    public static final BLibHolder<Block> SMOOTH_IRRADIATED_RESIN_STAIRS = create(
        "smooth_irradiated_resin_stairs",
        () -> new StairBlock(
            SMOOTH_IRRADIATED_RESIN.get().defaultBlockState(),
            AlienBlockProperties.IRRADIATED_RESIN.build()
        )
    );

    public static final BLibHolder<Block> SMOOTH_IRRADIATED_RESIN_WALL = create(
        "smooth_irradiated_resin_wall",
        () -> new WallBlock(AlienBlockProperties.IRRADIATED_RESIN.build())
    );

    public static final BLibHolder<Block> RIBBED_IRRADIATED_RESIN_SLAB = create(
        "ribbed_irradiated_resin_slab",
        () -> new SlabBlock(AlienBlockProperties.IRRADIATED_RESIN.build())
    );

    public static final BLibHolder<Block> RIBBED_IRRADIATED_RESIN_STAIRS = create(
        "ribbed_irradiated_resin_stairs",
        () -> new StairBlock(
            RIBBED_IRRADIATED_RESIN.get().defaultBlockState(),
            AlienBlockProperties.IRRADIATED_RESIN.build()
        )
    );

    public static final BLibHolder<Block> IRRADIATED_RESIN_BONE = create("irradiated_resin_bone", AlienBlockProperties.IRRADIATED_RESIN);

    public static final BLibHolder<Block> IRRADIATED_RESIN_BONE_SLAB = create(
        "irradiated_resin_bone_slab",
        () -> new SlabBlock(AlienBlockProperties.IRRADIATED_RESIN.build())
    );

    public static final BLibHolder<Block> IRRADIATED_RESIN_BONE_STAIRS = create(
        "irradiated_resin_bone_stairs",
        () -> new StairBlock(
            IRRADIATED_RESIN_BONE.get().defaultBlockState(),
            AlienBlockProperties.IRRADIATED_RESIN.build()
        )
    );

    public static final BLibHolder<Block> IRRADIATED_RESIN_DOORWAY = create(
        "irradiated_resin_doorway",
        () -> new RotatedPillarBlock(AlienBlockProperties.IRRADIATED_RESIN.build())
    );

    public static final BLibHolder<Block> IRRADIATED_RESIN_ETCHED = create(
        "irradiated_resin_etched",
        () -> new RotatedPillarBlock(AlienBlockProperties.IRRADIATED_RESIN.build())
    );

    public static final BLibHolder<Block> IRRADIATED_RESIN_ETCHED_SLAB = create(
        "irradiated_resin_etched_slab",
        () -> new SlabBlock(AlienBlockProperties.IRRADIATED_RESIN.build())
    );

    public static final BLibHolder<Block> IRRADIATED_RESIN_ETCHED_STAIRS = create(
        "irradiated_resin_etched_stairs",
        () -> new StairBlock(
            IRRADIATED_RESIN_ETCHED.get().defaultBlockState(),
            AlienBlockProperties.IRRADIATED_RESIN.build()
        )
    );

    public static final BLibHolder<Block> IRRADIATED_RESIN_SPINE = create(
        "irradiated_resin_spine",
        () -> new RotatedPillarBlock(AlienBlockProperties.IRRADIATED_RESIN.build())
    );

    public static final BLibHolder<Block> IRRADIATED_RESIN_STRETCHED = create(
        "irradiated_resin_stretched",
        () -> new RotatedPillarBlock(AlienBlockProperties.IRRADIATED_RESIN.build())
    );

    public static final BLibHolder<Block> IRRADIATED_RESIN_STRETCHED_SLAB = create(
        "irradiated_resin_stretched_slab",
        () -> new SlabBlock(AlienBlockProperties.IRRADIATED_RESIN.build())
    );

    public static final BLibHolder<Block> IRRADIATED_RESIN_STRETCHED_STAIRS = create(
        "irradiated_resin_stretched_stairs",
        () -> new StairBlock(
            IRRADIATED_RESIN_STRETCHED.get().defaultBlockState(),
            AlienBlockProperties.IRRADIATED_RESIN.build()
        )
    );

    public static final BLibHolder<Block> IRRADIATED_RESIN_TENDRIL = create(
        "irradiated_resin_tendril",
        () -> new RotatedPillarBlock(AlienBlockProperties.IRRADIATED_RESIN.build())
    );

    public static final BLibHolder<Block> IRRADIATED_RESIN_TENDRIL_SLAB = create(
        "irradiated_resin_tendril_slab",
        () -> new SlabBlock(AlienBlockProperties.IRRADIATED_RESIN.build())
    );

    public static final BLibHolder<Block> IRRADIATED_RESIN_TENDRIL_STAIRS = create(
        "irradiated_resin_tendril_stairs",
        () -> new StairBlock(
            IRRADIATED_RESIN_TENDRIL.get().defaultBlockState(),
            AlienBlockProperties.IRRADIATED_RESIN.build()
        )
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
