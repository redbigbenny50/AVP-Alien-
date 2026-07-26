package com.alien.common.registry.init.block;

import com.alien.Alien;
import com.alien.common.gameplay.block.resin.ResinBlock;
import com.alien.common.gameplay.block.resin.node.ResinNodeBlock;
import com.alien.common.gameplay.block.resin.vein.ResinVeinBlock;
import com.alien.common.gameplay.block.resin.vent.ResinVentBlock;
import com.alien.common.gameplay.block.resin.web.ResinWebBlock;
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

public class NetherAlienResinBlocks {

    private static final BLibRegistry<Block> REGISTRY = Alien.MOD.registries().create(BuiltInRegistries.BLOCK);

    public static final BLibHolder<Block> NETHER_RESIN = create(
        "nether_resin",
        () -> new ResinBlock(AlienBlockProperties.NETHER_RESIN.build())
    );

    public static final BLibHolder<Block> NETHER_RESIN_BRICKS = create(
        "nether_resin_bricks",
        AlienBlockProperties.NETHER_RESIN
    );

    public static final BLibHolder<Block> NETHER_RESIN_BRICK_SLAB = create(
        "nether_resin_brick_slab",
        () -> new SlabBlock(AlienBlockProperties.NETHER_RESIN.build())
    );

    public static final BLibHolder<Block> NETHER_RESIN_BRICK_STAIRS = create(
        "nether_resin_brick_stairs",
        () -> new StairBlock(
            NETHER_RESIN_BRICKS.get().defaultBlockState(),
            AlienBlockProperties.NETHER_RESIN.build()
        )
    );

    public static final BLibHolder<Block> NETHER_RESIN_BRICK_WALL = create(
        "nether_resin_brick_wall",
        () -> new WallBlock(AlienBlockProperties.NETHER_RESIN.build())
    );

    public static final BLibHolder<Block> NETHER_RESIN_STAIRS = create(
        "nether_resin_stairs",
        () -> new StairBlock(
            NETHER_RESIN.get().defaultBlockState(),
            AlienBlockProperties.NETHER_RESIN.build()
        )
    );

    public static final BLibHolder<Block> NETHER_RESIN_SLAB = create(
        "nether_resin_slab",
        () -> new SlabBlock(AlienBlockProperties.NETHER_RESIN.build())
    );

    public static final BLibHolder<Block> NETHER_RESIN_NODE = create(
        "nether_resin_node",
        () -> new ResinNodeBlock(AlienBlockProperties.NETHER_RESIN.build())
    );

    public static final BLibHolder<ResinVeinBlock> NETHER_RESIN_VEIN = create(
        "nether_resin_vein",
        () -> new ResinVeinBlock(AlienBlockProperties.NETHER_RESIN_VEIN.build())
    );

    public static final BLibHolder<ResinVentBlock> NETHER_RESIN_VENT = create(
        "nether_resin_vent",
        () -> new ResinVentBlock(AlienBlockProperties.NETHER_RESIN.build())
    );

    public static final BLibHolder<Block> NETHER_RESIN_WEB = create(
        "nether_resin_web",
        () -> new ResinWebBlock(AlienBlockProperties.NETHER_RESIN_WEB.build())
    );

    public static final BLibHolder<Block> RIBBED_NETHER_RESIN = create(
        "ribbed_nether_resin",
        () -> new RotatedPillarBlock(AlienBlockProperties.NETHER_RESIN.build())
    );

    public static final BLibHolder<Block> SMOOTH_NETHER_RESIN = create(
        "smooth_nether_resin",
        AlienBlockProperties.NETHER_RESIN
    );

    public static final BLibHolder<Block> SMOOTH_NETHER_RESIN_SLAB = create(
        "smooth_nether_resin_slab",
        () -> new SlabBlock(AlienBlockProperties.NETHER_RESIN.build())
    );

    public static final BLibHolder<Block> SMOOTH_NETHER_RESIN_STAIRS = create(
        "smooth_nether_resin_stairs",
        () -> new StairBlock(
            SMOOTH_NETHER_RESIN.get().defaultBlockState(),
            AlienBlockProperties.NETHER_RESIN.build()
        )
    );

    public static final BLibHolder<Block> SMOOTH_NETHER_RESIN_WALL = create(
        "smooth_nether_resin_wall",
        () -> new WallBlock(AlienBlockProperties.NETHER_RESIN.build())
    );

    public static final BLibHolder<Block> RIBBED_NETHER_RESIN_SLAB = create(
        "ribbed_nether_resin_slab",
        () -> new SlabBlock(AlienBlockProperties.NETHER_RESIN.build())
    );

    public static final BLibHolder<Block> RIBBED_NETHER_RESIN_STAIRS = create(
        "ribbed_nether_resin_stairs",
        () -> new StairBlock(
            RIBBED_NETHER_RESIN.get().defaultBlockState(),
            AlienBlockProperties.NETHER_RESIN.build()
        )
    );

    public static final BLibHolder<Block> NETHER_RESIN_BONE = create("nether_resin_bone", AlienBlockProperties.NETHER_RESIN);

    public static final BLibHolder<Block> NETHER_RESIN_BONE_SLAB = create(
        "nether_resin_bone_slab",
        () -> new SlabBlock(AlienBlockProperties.NETHER_RESIN.build())
    );

    public static final BLibHolder<Block> NETHER_RESIN_BONE_STAIRS = create(
        "nether_resin_bone_stairs",
        () -> new StairBlock(
            NETHER_RESIN_BONE.get().defaultBlockState(),
            AlienBlockProperties.NETHER_RESIN.build()
        )
    );

    public static final BLibHolder<Block> NETHER_RESIN_DOORWAY = create(
        "nether_resin_doorway",
        () -> new RotatedPillarBlock(AlienBlockProperties.NETHER_RESIN.build())
    );

    public static final BLibHolder<Block> NETHER_RESIN_ETCHED = create(
        "nether_resin_etched",
        () -> new RotatedPillarBlock(AlienBlockProperties.NETHER_RESIN.build())
    );

    public static final BLibHolder<Block> NETHER_RESIN_ETCHED_SLAB = create(
        "nether_resin_etched_slab",
        () -> new SlabBlock(AlienBlockProperties.NETHER_RESIN.build())
    );

    public static final BLibHolder<Block> NETHER_RESIN_ETCHED_STAIRS = create(
        "nether_resin_etched_stairs",
        () -> new StairBlock(
            NETHER_RESIN_ETCHED.get().defaultBlockState(),
            AlienBlockProperties.NETHER_RESIN.build()
        )
    );

    public static final BLibHolder<Block> NETHER_RESIN_SPINE = create(
        "nether_resin_spine",
        () -> new RotatedPillarBlock(AlienBlockProperties.NETHER_RESIN.build())
    );

    public static final BLibHolder<Block> NETHER_RESIN_STRETCHED = create(
        "nether_resin_stretched",
        () -> new RotatedPillarBlock(AlienBlockProperties.NETHER_RESIN.build())
    );

    public static final BLibHolder<Block> NETHER_RESIN_STRETCHED_SLAB = create(
        "nether_resin_stretched_slab",
        () -> new SlabBlock(AlienBlockProperties.NETHER_RESIN.build())
    );

    public static final BLibHolder<Block> NETHER_RESIN_STRETCHED_STAIRS = create(
        "nether_resin_stretched_stairs",
        () -> new StairBlock(
            NETHER_RESIN_STRETCHED.get().defaultBlockState(),
            AlienBlockProperties.NETHER_RESIN.build()
        )
    );

    public static final BLibHolder<Block> NETHER_RESIN_TENDRIL = create(
        "nether_resin_tendril",
        () -> new RotatedPillarBlock(AlienBlockProperties.NETHER_RESIN.build())
    );

    public static final BLibHolder<Block> NETHER_RESIN_TENDRIL_SLAB = create(
        "nether_resin_tendril_slab",
        () -> new SlabBlock(AlienBlockProperties.NETHER_RESIN.build())
    );

    public static final BLibHolder<Block> NETHER_RESIN_TENDRIL_STAIRS = create(
        "nether_resin_tendril_stairs",
        () -> new StairBlock(
            NETHER_RESIN_TENDRIL.get().defaultBlockState(),
            AlienBlockProperties.NETHER_RESIN.build()
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
