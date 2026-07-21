package com.alien.fabric.data.recipe.impl.resin;

import com.alien.common.registry.init.block.AberrantAlienResinBlocks;
import com.alien.common.registry.init.block.AlienResinBlocks;
import com.alien.common.registry.init.block.IrradiatedAlienResinBlocks;
import com.alien.common.registry.init.block.NetherAlienResinBlocks;
import com.alien.common.registry.init.item.AlienItems;
import com.alien.fabric.compatibility.avp_human.AVPHumanFabric;
import com.blib.fabric.data.recipe.builder.RecipeBuilder;
import com.blib.fabric.data.recipe.util.RecipeUtil;
import net.minecraft.data.recipes.RecipeCategory;

public class ResinRecipeProvider {

    private static final ResinSet ABERRANT_SET = new ResinSet(
        AlienItems.ABERRANT_RESIN_BALL,
        AberrantAlienResinBlocks.ABERRANT_RESIN,
        AberrantAlienResinBlocks.ABERRANT_RESIN_SLAB,
        AberrantAlienResinBlocks.ABERRANT_RESIN_STAIRS,
        AberrantAlienResinBlocks.ABERRANT_RESIN_BRICKS,
        AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_SLAB,
        AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_STAIRS,
        AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_WALL,
        AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN,
        AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_SLAB,
        AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_STAIRS,
        AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_WALL,
        AberrantAlienResinBlocks.ABERRANT_RESIN_VEIN,
        AberrantAlienResinBlocks.ABERRANT_RESIN_WEB
    );

    private static final ResinSet IRRADIATED_SET = new ResinSet(
        AlienItems.IRRADIATED_RESIN_BALL,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_SLAB,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STAIRS,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICKS,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_SLAB,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_STAIRS,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_WALL,
        IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN,
        IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_SLAB,
        IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_STAIRS,
        IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_WALL,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_VEIN,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_WEB
    );

    private static final ResinSet NETHER_SET = new ResinSet(
        AlienItems.NETHER_RESIN_BALL,
        NetherAlienResinBlocks.NETHER_RESIN,
        NetherAlienResinBlocks.NETHER_RESIN_SLAB,
        NetherAlienResinBlocks.NETHER_RESIN_STAIRS,
        NetherAlienResinBlocks.NETHER_RESIN_BRICKS,
        NetherAlienResinBlocks.NETHER_RESIN_BRICK_SLAB,
        NetherAlienResinBlocks.NETHER_RESIN_BRICK_STAIRS,
        NetherAlienResinBlocks.NETHER_RESIN_BRICK_WALL,
        NetherAlienResinBlocks.SMOOTH_NETHER_RESIN,
        NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_SLAB,
        NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_STAIRS,
        NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_WALL,
        NetherAlienResinBlocks.NETHER_RESIN_VEIN,
        NetherAlienResinBlocks.NETHER_RESIN_WEB
    );

    private static final ResinSet BASE_SET = new ResinSet(
        AlienItems.RESIN_BALL,
        AlienResinBlocks.RESIN,
        AlienResinBlocks.RESIN_SLAB,
        AlienResinBlocks.RESIN_STAIRS,
        AlienResinBlocks.RESIN_BRICKS,
        AlienResinBlocks.RESIN_BRICK_SLAB,
        AlienResinBlocks.RESIN_BRICK_STAIRS,
        AlienResinBlocks.RESIN_BRICK_WALL,
        AlienResinBlocks.SMOOTH_RESIN,
        AlienResinBlocks.SMOOTH_RESIN_SLAB,
        AlienResinBlocks.SMOOTH_RESIN_STAIRS,
        AlienResinBlocks.SMOOTH_RESIN_WALL,
        AlienResinBlocks.RESIN_VEIN,
        AlienResinBlocks.RESIN_WEB
    );

    private static final DecorativeResinSet BASE_DECORATIVE = new DecorativeResinSet(
        AlienResinBlocks.RESIN,
        AlienResinBlocks.RESIN_DOORWAY,
        AlienResinBlocks.RESIN_SPINE,
        AlienResinBlocks.RIBBED_RESIN,
        AlienResinBlocks.RIBBED_RESIN_SLAB,
        AlienResinBlocks.RIBBED_RESIN_STAIRS,
        AlienResinBlocks.RESIN_BONE,
        AlienResinBlocks.RESIN_BONE_SLAB,
        AlienResinBlocks.RESIN_BONE_STAIRS,
        AlienResinBlocks.RESIN_ETCHED,
        AlienResinBlocks.RESIN_ETCHED_SLAB,
        AlienResinBlocks.RESIN_ETCHED_STAIRS,
        AlienResinBlocks.RESIN_STRETCHED,
        AlienResinBlocks.RESIN_STRETCHED_SLAB,
        AlienResinBlocks.RESIN_STRETCHED_STAIRS,
        AlienResinBlocks.RESIN_TENDRIL,
        AlienResinBlocks.RESIN_TENDRIL_SLAB,
        AlienResinBlocks.RESIN_TENDRIL_STAIRS
        // + the 4th set's slab/stairs
    );

    private static final DecorativeResinSet NETHER_DECORATIVE = new DecorativeResinSet(
        NetherAlienResinBlocks.NETHER_RESIN,
        NetherAlienResinBlocks.NETHER_RESIN_DOORWAY,
        NetherAlienResinBlocks.NETHER_RESIN_SPINE,
        NetherAlienResinBlocks.RIBBED_NETHER_RESIN,
        NetherAlienResinBlocks.RIBBED_NETHER_RESIN_SLAB,
        NetherAlienResinBlocks.RIBBED_NETHER_RESIN_STAIRS,
        NetherAlienResinBlocks.NETHER_RESIN_BONE,
        NetherAlienResinBlocks.NETHER_RESIN_BONE_SLAB,
        NetherAlienResinBlocks.NETHER_RESIN_BONE_STAIRS,
        NetherAlienResinBlocks.NETHER_RESIN_ETCHED,
        NetherAlienResinBlocks.NETHER_RESIN_ETCHED_SLAB,
        NetherAlienResinBlocks.NETHER_RESIN_ETCHED_STAIRS,
        NetherAlienResinBlocks.NETHER_RESIN_STRETCHED,
        NetherAlienResinBlocks.NETHER_RESIN_STRETCHED_SLAB,
        NetherAlienResinBlocks.NETHER_RESIN_STRETCHED_STAIRS,
        NetherAlienResinBlocks.NETHER_RESIN_TENDRIL,
        NetherAlienResinBlocks.NETHER_RESIN_TENDRIL_SLAB,
        NetherAlienResinBlocks.NETHER_RESIN_TENDRIL_STAIRS
    );

    private static final DecorativeResinSet ABERRANT_DECORATIVE = new DecorativeResinSet(
        AberrantAlienResinBlocks.ABERRANT_RESIN,
        AberrantAlienResinBlocks.ABERRANT_RESIN_DOORWAY,
        AberrantAlienResinBlocks.ABERRANT_RESIN_SPINE,
        AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN,
        AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN_SLAB,
        AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN_STAIRS,
        AberrantAlienResinBlocks.ABERRANT_RESIN_BONE,
        AberrantAlienResinBlocks.ABERRANT_RESIN_BONE_SLAB,
        AberrantAlienResinBlocks.ABERRANT_RESIN_BONE_STAIRS,
        AberrantAlienResinBlocks.ABERRANT_RESIN_ETCHED,
        AberrantAlienResinBlocks.ABERRANT_RESIN_ETCHED_SLAB,
        AberrantAlienResinBlocks.ABERRANT_RESIN_ETCHED_STAIRS,
        AberrantAlienResinBlocks.ABERRANT_RESIN_STRETCHED,
        AberrantAlienResinBlocks.ABERRANT_RESIN_STRETCHED_SLAB,
        AberrantAlienResinBlocks.ABERRANT_RESIN_STRETCHED_STAIRS,
        AberrantAlienResinBlocks.ABERRANT_RESIN_TENDRIL,
        AberrantAlienResinBlocks.ABERRANT_RESIN_TENDRIL_SLAB,
        AberrantAlienResinBlocks.ABERRANT_RESIN_TENDRIL_STAIRS
    );

    private static final DecorativeResinSet IRRADIATED_DECORATIVE = new DecorativeResinSet(
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_DOORWAY,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_SPINE,
        IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN,
        IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN_SLAB,
        IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN_STAIRS,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE_SLAB,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE_STAIRS,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED_SLAB,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED_STAIRS,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED_SLAB,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED_STAIRS,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL_SLAB,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL_STAIRS
    );

    public static void provide(RecipeBuilder builder) {
        createResinRecipes(builder);
    }

    private static void createResinRecipes(RecipeBuilder builder) {
        createResinRecipesFromSet(builder, BASE_SET);
        createResinRecipesFromSet(builder, NETHER_SET);
        createResinRecipesFromSet(builder, ABERRANT_SET);
        createResinRecipesFromSet(builder.withCondition(AVPHumanFabric.IS_LOADED), IRRADIATED_SET);
        createDecorativeRecipesFromSet(builder, BASE_DECORATIVE);
        createDecorativeRecipesFromSet(builder, NETHER_DECORATIVE);
        createDecorativeRecipesFromSet(builder, ABERRANT_DECORATIVE);
        createDecorativeRecipesFromSet(builder.withCondition(AVPHumanFabric.IS_LOADED), IRRADIATED_DECORATIVE);
    }

    private static void createResinRecipesFromSet(RecipeBuilder builder, ResinSet set) {
        builder.stonecut(set.resinBlock())
            .withCategory(RecipeCategory.BUILDING_BLOCKS)
            .into(4, set.vein());

        builder.stonecut(set.resinBlock())
            .withCategory(RecipeCategory.BUILDING_BLOCKS)
            .into(2, set.web());

        // Resin block
        RecipeUtil.createCompressedBlockRecipes2x2(builder, set.resinBallItem().get(), set.resinBlock().get());

        RecipeUtil.createSlabBlockManualAndStonecutterRecipes(builder, set.resinBlock().get(), set.resinBlockSlab().get());
        RecipeUtil.createStairBlockManualAndStonecutterRecipes(builder, set.resinBlock().get(), set.resinBlockStairs().get());

        builder.stonecut(set.resinBlock())
            .into(1, set.brick());

        RecipeUtil.createSlabBlockManualAndStonecutterRecipes(builder, set.resinBlock().get(), set.brickSlab().get());
        RecipeUtil.createStairBlockManualAndStonecutterRecipes(builder, set.resinBlock().get(), set.brickStairs().get());
        RecipeUtil.createWallBlockManualAndStonecutterRecipes(builder, set.resinBlock().get(), set.brickWall().get());

        builder.stonecut(set.resinBlock())
            .into(1, set.smooth());

        RecipeUtil.createSlabBlockManualAndStonecutterRecipes(builder, set.resinBlock().get(), set.smoothSlab().get());
        RecipeUtil.createStairBlockManualAndStonecutterRecipes(builder, set.resinBlock().get(), set.smoothStairs().get());
        RecipeUtil.createWallBlockManualAndStonecutterRecipes(builder, set.resinBlock().get(), set.smoothWall().get());

        // Brick resin block

        RecipeUtil.createSlabBlockManualAndStonecutterRecipes(builder, set.brick().get(), set.brickSlab().get());
        RecipeUtil.createStairBlockManualAndStonecutterRecipes(builder, set.brick().get(), set.brickStairs().get());
        RecipeUtil.createWallBlockManualAndStonecutterRecipes(builder, set.brick().get(), set.brickWall().get());

        // Smooth resin block

        builder.stonecut(set.smooth())
            .into(1, set.brick());

        RecipeUtil.createSlabBlockManualAndStonecutterRecipes(builder, set.smooth().get(), set.smoothSlab().get());
        RecipeUtil.createStairBlockManualAndStonecutterRecipes(builder, set.smooth().get(), set.smoothStairs().get());
        RecipeUtil.createWallBlockManualAndStonecutterRecipes(builder, set.smooth().get(), set.smoothWall().get());

        RecipeUtil.createSlabBlockManualAndStonecutterRecipes(builder, set.smooth().get(), set.brickSlab().get());
        RecipeUtil.createStairBlockManualAndStonecutterRecipes(builder, set.smooth().get(), set.brickStairs().get());
        RecipeUtil.createWallBlockManualAndStonecutterRecipes(builder, set.smooth().get(), set.brickWall().get());
    }

    private static void createDecorativeRecipesFromSet(RecipeBuilder builder, DecorativeResinSet set) {
        var resin = set.resinBlock();

        // base resin block -> each decorative block
        builder.stonecut(resin).withCategory(RecipeCategory.BUILDING_BLOCKS).into(1, set.doorway());
        builder.stonecut(resin).withCategory(RecipeCategory.BUILDING_BLOCKS).into(1, set.spine());
        builder.stonecut(resin).withCategory(RecipeCategory.BUILDING_BLOCKS).into(1, set.ribbed());
        builder.stonecut(resin).withCategory(RecipeCategory.BUILDING_BLOCKS).into(1, set.bone());
        builder.stonecut(resin).withCategory(RecipeCategory.BUILDING_BLOCKS).into(1, set.etched());
        builder.stonecut(resin).withCategory(RecipeCategory.BUILDING_BLOCKS).into(1, set.stretched());
        builder.stonecut(resin).withCategory(RecipeCategory.BUILDING_BLOCKS).into(1, set.tendril());

        // each decorative block -> its own slab/stairs (and from base resin too, like the existing code does)
        RecipeUtil.createSlabBlockManualAndStonecutterRecipes(builder, set.etched().get(), set.etchedSlab().get());
        RecipeUtil.createStairBlockManualAndStonecutterRecipes(builder, set.etched().get(), set.etchedStairs().get());
        RecipeUtil.createSlabBlockManualAndStonecutterRecipes(builder, set.bone().get(), set.boneSlab().get());
        RecipeUtil.createStairBlockManualAndStonecutterRecipes(builder, set.bone().get(), set.boneStairs().get());
        RecipeUtil.createSlabBlockManualAndStonecutterRecipes(builder, set.stretched().get(), set.stretchedSlab().get());
        RecipeUtil.createStairBlockManualAndStonecutterRecipes(builder, set.stretched().get(), set.stretchedStairs().get());
        RecipeUtil.createSlabBlockManualAndStonecutterRecipes(builder, set.tendril().get(), set.tendrilSlab().get());
        RecipeUtil.createStairBlockManualAndStonecutterRecipes(builder, set.tendril().get(), set.tendrilStairs().get());
        RecipeUtil.createSlabBlockManualAndStonecutterRecipes(builder, set.ribbed().get(), set.ribbedSlab().get());
        RecipeUtil.createStairBlockManualAndStonecutterRecipes(builder, set.ribbed().get(), set.ribbedStairs().get());
        // + the 4th block's slab/stairs
    }
}
