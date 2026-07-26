package com.alien.fabric.data.recipe.impl.chitin;

import com.alien.common.registry.init.block.AberrantAlienChitinBlocks;
import com.alien.common.registry.init.block.AlienChitinBlocks;
import com.alien.common.registry.init.block.IrradiatedAlienChitinBlocks;
import com.alien.common.registry.init.block.NetherAlienChitinBlocks;
import com.alien.common.registry.init.item.AlienItems;
import com.alien.fabric.compatibility.avp_human.AVPHumanFabric;
import com.blib.fabric.data.recipe.builder.RecipeBuilder;
import com.blib.fabric.data.recipe.util.RecipeUtil;

public class ChitinRecipeProvider {

    private static final ChitinSet BASE_SET = new ChitinSet(
        AlienItems.CHITIN,
        AlienItems.PLATED_CHITIN,
        AlienChitinBlocks.CHITIN_BLOCK,
        AlienChitinBlocks.CHITIN_BLOCK_SLAB,
        AlienChitinBlocks.CHITIN_BLOCK_STAIRS,
        AlienChitinBlocks.CHITIN_BLOCK_WALL,
        AlienChitinBlocks.CHITIN_BRICKS,
        AlienChitinBlocks.CHITIN_BRICK_SLAB,
        AlienChitinBlocks.CHITIN_BRICK_STAIRS,
        AlienChitinBlocks.CHITIN_BRICK_WALL,
        AlienChitinBlocks.CHISELED_CHITIN_BRICKS,
        AlienChitinBlocks.CHISELED_CHITIN_BRICKS_EMBRYO,
        AlienChitinBlocks.POLISHED_CHITIN,
        AlienChitinBlocks.POLISHED_CHITIN_SLAB,
        AlienChitinBlocks.POLISHED_CHITIN_STAIRS,
        AlienChitinBlocks.POLISHED_CHITIN_WALL
    );

    private static final ChitinSet NETHER_SET = new ChitinSet(
        AlienItems.NETHER_CHITIN,
        AlienItems.PLATED_NETHER_CHITIN,
        NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK,
        NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK_SLAB,
        NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK_STAIRS,
        NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK_WALL,
        NetherAlienChitinBlocks.NETHER_CHITIN_BRICKS,
        NetherAlienChitinBlocks.NETHER_CHITIN_BRICK_SLAB,
        NetherAlienChitinBlocks.NETHER_CHITIN_BRICK_STAIRS,
        NetherAlienChitinBlocks.NETHER_CHITIN_BRICK_WALL,
        NetherAlienChitinBlocks.CHISELED_NETHER_CHITIN_BRICKS,
        NetherAlienChitinBlocks.CHISELED_NETHER_CHITIN_BRICKS_EMBRYO,
        NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN,
        NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN_SLAB,
        NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN_STAIRS,
        NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN_WALL
    );

    private static final ChitinSet ABERRANT_SET = new ChitinSet(
        AlienItems.ABERRANT_CHITIN,
        AlienItems.PLATED_ABERRANT_CHITIN,
        AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK,
        AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK_SLAB,
        AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK_STAIRS,
        AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK_WALL,
        AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICKS,
        AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICK_SLAB,
        AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICK_STAIRS,
        AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICK_WALL,
        AberrantAlienChitinBlocks.CHISELED_ABERRANT_CHITIN_BRICKS,
        AberrantAlienChitinBlocks.CHISELED_ABERRANT_CHITIN_BRICKS_EMBRYO,
        AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN,
        AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN_SLAB,
        AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN_STAIRS,
        AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN_WALL
    );

    private static final ChitinSet IRRADIATED_SET = new ChitinSet(
        AlienItems.IRRADIATED_CHITIN,
        AlienItems.PLATED_IRRADIATED_CHITIN,
        IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK,
        IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK_SLAB,
        IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK_STAIRS,
        IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK_WALL,
        IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICKS,
        IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICK_SLAB,
        IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICK_STAIRS,
        IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICK_WALL,
        IrradiatedAlienChitinBlocks.CHISELED_IRRADIATED_CHITIN_BRICKS,
        IrradiatedAlienChitinBlocks.CHISELED_IRRADIATED_CHITIN_BRICKS_EMBRYO,
        IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN,
        IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN_SLAB,
        IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN_STAIRS,
        IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN_WALL
    );

    public static void provide(RecipeBuilder builder) {
        createChitinRecipes(builder);
    }

    private static void createChitinRecipes(RecipeBuilder builder) {
        createChitinRecipesFromSet(builder, BASE_SET);
        createChitinRecipesFromSet(builder, NETHER_SET);
        createChitinRecipesFromSet(builder, ABERRANT_SET);
        createChitinRecipesFromSet(builder.withCondition(AVPHumanFabric.IS_LOADED), IRRADIATED_SET);
    }

    private static void createChitinRecipesFromSet(RecipeBuilder builder, ChitinSet set) {
        builder.shapeless()
            .requires(1, set.platedChitinItem())
            .into(3, set.chitinItem());

        RecipeUtil.createCompressedBlockRecipes2x2(builder, set.chitinItem().get(), set.chitinBlock().get());

        // Base chitin block

        RecipeUtil.createSlabBlockManualAndStonecutterRecipes(builder, set.chitinBlock().get(), set.chitinBlockSlab().get());
        RecipeUtil.createStairBlockManualAndStonecutterRecipes(builder, set.chitinBlock().get(), set.chitinBlockStairs().get());
        RecipeUtil.createWallBlockManualAndStonecutterRecipes(builder, set.chitinBlock().get(), set.chitinBlockWall().get());

        builder.stonecut(set.chitinBlock())
            .into(1, set.polished());

        RecipeUtil.createSlabBlockManualAndStonecutterRecipes(builder, set.chitinBlock().get(), set.polishedSlab().get());
        RecipeUtil.createStairBlockManualAndStonecutterRecipes(builder, set.chitinBlock().get(), set.polishedStairs().get());
        RecipeUtil.createWallBlockManualAndStonecutterRecipes(builder, set.chitinBlock().get(), set.polishedWall().get());

        builder.stonecut(set.chitinBlock())
            .into(1, set.bricks());

        RecipeUtil.createSlabBlockManualAndStonecutterRecipes(builder, set.chitinBlock().get(), set.brickSlab().get());
        RecipeUtil.createStairBlockManualAndStonecutterRecipes(builder, set.chitinBlock().get(), set.brickStairs().get());
        RecipeUtil.createWallBlockManualAndStonecutterRecipes(builder, set.chitinBlock().get(), set.brickWall().get());

        builder.stonecut(set.chitinBlock())
            .into(1, set.chiseledBricks());
        builder.stonecut(set.chitinBlock())
            .into(1, set.chiseledBricksEmbryo());

        // Brick chitin block

        RecipeUtil.createSlabBlockManualAndStonecutterRecipes(builder, set.bricks().get(), set.brickSlab().get());
        RecipeUtil.createStairBlockManualAndStonecutterRecipes(builder, set.bricks().get(), set.brickStairs().get());
        RecipeUtil.createWallBlockManualAndStonecutterRecipes(builder, set.bricks().get(), set.brickWall().get());

        builder.stonecut(set.bricks())
            .into(1, set.chiseledBricks());
        builder.stonecut(set.bricks())
            .into(1, set.chiseledBricksEmbryo());

        // Polished chitin block

        RecipeUtil.createSlabBlockManualAndStonecutterRecipes(builder, set.polished().get(), set.polishedSlab().get());
        RecipeUtil.createStairBlockManualAndStonecutterRecipes(builder, set.polished().get(), set.polishedStairs().get());
        RecipeUtil.createWallBlockManualAndStonecutterRecipes(builder, set.polished().get(), set.polishedWall().get());

        builder.stonecut(set.polished())
            .into(1, set.bricks());

        RecipeUtil.createSlabBlockManualAndStonecutterRecipes(builder, set.polished().get(), set.brickSlab().get());
        RecipeUtil.createStairBlockManualAndStonecutterRecipes(builder, set.polished().get(), set.brickStairs().get());
        RecipeUtil.createWallBlockManualAndStonecutterRecipes(builder, set.polished().get(), set.brickWall().get());

        builder.stonecut(set.polished())
            .into(1, set.chiseledBricks());
        builder.stonecut(set.polished())
            .into(1, set.chiseledBricksEmbryo());
    }

}
