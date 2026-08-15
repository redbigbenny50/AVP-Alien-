package com.alien.common.registry.init.block;

import com.alien.Alien;
import com.alien.common.gameplay.block.ResinContainerBlock;
import com.alien.common.gameplay.block.capture.anchor.AnchorBlock;
import com.alien.common.gameplay.block.crusher.CrusherHeadBlock;
import com.alien.common.gameplay.block.crusher.CrusherHeadVariant;
import com.alien.common.gameplay.block.crusher.CrusherWallHeadBlock;
import com.alien.common.gameplay.block.jelly.JellyBlock;
import com.alien.common.gameplay.block.jelly.JellyVatBlock;
import com.alien.common.gameplay.block.queen.QueenHeadBlock;
import com.alien.common.gameplay.block.queen.QueenHeadVariant;
import com.alien.common.gameplay.block.queen.QueenWallHeadBlock;
import com.alien.common.gameplay.block.xenomorph.head.XenomorphHeadBlock;
import com.alien.common.gameplay.block.xenomorph.head.XenomorphWallHeadBlock;
import com.alien.common.registry.init.block.property.AlienBlockProperties;
import com.alien.common.registry.init.item.AlienXenomorphHeadItems;
import com.blib.api.common.block.v1.BlockPropertyBuilder;
import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Supplier;

public class AlienBlocks {

    public static final BLibRegistry<Block> REGISTRY = Alien.MOD.registries().create(BuiltInRegistries.BLOCK);

    /**
     * ⭐⭐ THE RESIN CONTAINERS, one per strain. [stated] "made from resin and chitin of its strain type... acid proof
     * and explosion proof."
     * <p>
     * ⚠ THE IMMUNITIES ARE PROPERTIES AND TAGS, NOT CODE. `explosionResistance` above vanilla obsidian covers the nuke
     * ([stated] "yes"), and acid is a tag membership - see AlienBlockTags.ACID_IMMUNE and its strain variants, which
     * the acid system already consults. Neither needed a line in the block class.
     * </p>
     */
    public static final BLibHolder<Block> RESIN_CONTAINER = create(
        "resin_container",
        () -> new ResinContainerBlock(resinContainerProperties(AlienBlockProperties.RESIN))
    );

    public static final BLibHolder<Block> NETHER_RESIN_CONTAINER = create(
        "nether_resin_container",
        () -> new ResinContainerBlock(resinContainerProperties(AlienBlockProperties.NETHER_RESIN))
    );

    public static final BLibHolder<Block> ABERRANT_RESIN_CONTAINER = create(
        "aberrant_resin_container",
        () -> new ResinContainerBlock(resinContainerProperties(AlienBlockProperties.ABERRANT_RESIN))
    );

    public static final BLibHolder<Block> IRRADIATED_RESIN_CONTAINER = create(
        "irradiated_resin_container",
        () -> new ResinContainerBlock(resinContainerProperties(AlienBlockProperties.IRRADIATED_RESIN))
    );

    /**
     * ⚠ 1200 BLAST RESISTANCE puts it above obsidian (1200) territory deliberately: [stated] the nuke must not take
     * one. ⚠ noOcclusion because the ribs open outward and would otherwise cull the faces behind them.
     */
    private static BlockBehaviour.Properties resinContainerProperties(BlockPropertyBuilder strain) {
        return strain.build()
            .strength(6.0F, 1200.0F)
            .noOcclusion()
            .sound(SoundType.SLIME_BLOCK);
    }

    public static final BLibHolder<Block> ROYAL_JELLY_BLOCK = create(
        "royal_jelly_block",
        () -> new JellyBlock(AlienBlockProperties.JELLY.build().speedFactor(0.4F).jumpFactor(0.5F))
    );

    public static final BLibHolder<Block> SCOURGE_JELLY_BLOCK = create(
        "scourge_jelly_block",
        () -> new JellyBlock(AlienBlockProperties.JELLY.build().speedFactor(0.4F).jumpFactor(0.5F))
    );

    /** The irradiated strain's jelly. Gated at the tab and the recipe, not the registry - see AlienModGates. */
    public static final BLibHolder<Block> IRRADIATED_JELLY_BLOCK = create(
        "irradiated_jelly_block",
        () -> new JellyBlock(AlienBlockProperties.JELLY.build().speedFactor(0.4F).jumpFactor(0.5F))
    );

    public static final BLibHolder<AnchorBlock> ANCHOR = create(
        "anchor",
        () -> new AnchorBlock(anchorProperties())
    );

    public static final BLibHolder<JellyVatBlock> JELLY_VAT = create(
        "jelly_vat",
        () -> new JellyVatBlock(AlienBlockProperties.RESIN.build().noOcclusion())
    );

    public static final BLibHolder<QueenHeadBlock> QUEEN_HEAD = create(
        "queen_head",
        () -> new QueenHeadBlock(QueenHeadVariant.QUEEN, queenHeadProperties())
    );

    public static final BLibHolder<QueenWallHeadBlock> QUEEN_WALL_HEAD = create(
        "queen_wall_head",
        () -> new QueenWallHeadBlock(QueenHeadVariant.QUEEN, queenHeadProperties())
    );

    public static final BLibHolder<QueenHeadBlock> ABERRANT_QUEEN_HEAD = create(
        "aberrant_queen_head",
        () -> new QueenHeadBlock(QueenHeadVariant.ABERRANT, queenHeadProperties())
    );

    public static final BLibHolder<QueenWallHeadBlock> ABERRANT_QUEEN_WALL_HEAD = create(
        "aberrant_queen_wall_head",
        () -> new QueenWallHeadBlock(QueenHeadVariant.ABERRANT, queenHeadProperties())
    );

    public static final BLibHolder<QueenHeadBlock> IRRADIATED_QUEEN_HEAD = create(
        "irradiated_queen_head",
        () -> new QueenHeadBlock(QueenHeadVariant.IRRADIATED, queenHeadProperties())
    );

    public static final BLibHolder<QueenWallHeadBlock> IRRADIATED_QUEEN_WALL_HEAD = create(
        "irradiated_queen_wall_head",
        () -> new QueenWallHeadBlock(QueenHeadVariant.IRRADIATED, queenHeadProperties())
    );

    public static final BLibHolder<QueenHeadBlock> NETHER_QUEEN_HEAD = create(
        "nether_queen_head",
        () -> new QueenHeadBlock(QueenHeadVariant.NETHER, queenHeadProperties())
    );

    public static final BLibHolder<QueenWallHeadBlock> NETHER_QUEEN_WALL_HEAD = create(
        "nether_queen_wall_head",
        () -> new QueenWallHeadBlock(QueenHeadVariant.NETHER, queenHeadProperties())
    );

    public static final BLibHolder<CrusherHeadBlock> CRUSHER_HEAD = create(
        "crusher_head",
        () -> new CrusherHeadBlock(CrusherHeadVariant.CRUSHER, crusherHeadProperties())
    );

    public static final BLibHolder<CrusherWallHeadBlock> CRUSHER_WALL_HEAD = create(
        "crusher_wall_head",
        () -> new CrusherWallHeadBlock(CrusherHeadVariant.CRUSHER, crusherHeadProperties())
    );

    public static final BLibHolder<CrusherHeadBlock> ABERRANT_CRUSHER_HEAD = create(
        "aberrant_crusher_head",
        () -> new CrusherHeadBlock(CrusherHeadVariant.ABERRANT, crusherHeadProperties())
    );

    public static final BLibHolder<CrusherWallHeadBlock> ABERRANT_CRUSHER_WALL_HEAD = create(
        "aberrant_crusher_wall_head",
        () -> new CrusherWallHeadBlock(CrusherHeadVariant.ABERRANT, crusherHeadProperties())
    );

    public static final BLibHolder<CrusherHeadBlock> IRRADIATED_CRUSHER_HEAD = create(
        "irradiated_crusher_head",
        () -> new CrusherHeadBlock(CrusherHeadVariant.IRRADIATED, crusherHeadProperties())
    );

    public static final BLibHolder<CrusherWallHeadBlock> IRRADIATED_CRUSHER_WALL_HEAD = create(
        "irradiated_crusher_wall_head",
        () -> new CrusherWallHeadBlock(CrusherHeadVariant.IRRADIATED, crusherHeadProperties())
    );

    public static final BLibHolder<CrusherHeadBlock> NETHER_CRUSHER_HEAD = create(
        "nether_crusher_head",
        () -> new CrusherHeadBlock(CrusherHeadVariant.NETHER, crusherHeadProperties())
    );

    public static final BLibHolder<CrusherWallHeadBlock> NETHER_CRUSHER_WALL_HEAD = create(
        "nether_crusher_wall_head",
        () -> new CrusherWallHeadBlock(CrusherHeadVariant.NETHER, crusherHeadProperties())
    );

    /**
     * Block properties shared by all queen-head variants — strength values mirror vanilla skull blocks (1.0F to break
     * by hand in ~1.5s, no resistance, instrument NONE), and {@code noOcclusion} since the BE renderer paints a
     * non-cube shape that doesn't fill the full 1x1x1 voxel.
     */
    private static BlockBehaviour.Properties anchorProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.5F, 6.0F)
            .sound(SoundType.METAL)
            .noOcclusion();
    }

    private static BlockBehaviour.Properties queenHeadProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GRAY)
            .strength(1.0F)
            .sound(SoundType.BONE_BLOCK)
            .noOcclusion();
    }

    /**
     * Block properties shared by all crusher-head variants. Same shape as {@link #queenHeadProperties()} — skull-like
     * strength, bone-block sound, no occlusion. Kept as a separate helper so the two head families can diverge later
     * without disturbing each other (e.g. if the crusher head should weigh more or sound different).
     */
    private static BlockBehaviour.Properties crusherHeadProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GRAY)
            .strength(1.0F)
            .sound(SoundType.BONE_BLOCK)
            .noOcclusion();
    }

    public static BLibHolder<XenomorphHeadBlock> createXenomorphHeadBlock(String path) {
        return create(path, () -> new XenomorphHeadBlock(path, xenomorphHeadProperties()));
    }

    public static BLibHolder<XenomorphWallHeadBlock> createXenomorphWallHeadBlock(String path, String itemPath) {
        return create(path, () -> new XenomorphWallHeadBlock(itemPath, xenomorphHeadProperties()));
    }

    private static BlockBehaviour.Properties xenomorphHeadProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GRAY)
            .strength(1.0F)
            .sound(SoundType.BONE_BLOCK)
            .noOcclusion();
    }

    private static BLibHolder<Block> create(String path, BlockPropertyBuilder blockPropertyBuilder) {
        return create(path, () -> new Block(blockPropertyBuilder.build()));
    }

    private static <T extends Block> BLibHolder<T> create(String path, Supplier<T> blockSupplier) {
        return REGISTRY.createHolder(path, blockSupplier);
    }

    public static void initialize() {
        AlienXenomorphHeadItems.initialize();
        REGISTRY.registerAll();
    }
}
