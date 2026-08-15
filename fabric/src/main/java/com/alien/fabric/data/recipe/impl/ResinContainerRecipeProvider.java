package com.alien.fabric.data.recipe.impl;

import com.alien.common.registry.init.block.AlienBlocks;
import com.alien.common.registry.init.item.AlienItems;
import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.fabric.data.recipe.builder.RecipeBuilder;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/**
 * The resin container: resin balls in the corners, chitin on the edges, a hollow centre.
 * <p>
 * [stated] "the recipe is resin balls in the 4 corners of the crafting table. the center is hollow and chitin the same
 * strain as the resin balls in the top left right and bottom of the crafting table. makes 1 container."
 * </p>
 * <p>
 * ⚠ STRAIN NEVER MIXES. Each recipe takes the resin ball AND the chitin of one strain, so you cannot build a nether
 * container out of aberrant parts. That is four separate recipes rather than one tag-based recipe, deliberately - a tag
 * would let any resin meet any chitin and silently produce whichever container the output named.
 * </p>
 */
public class ResinContainerRecipeProvider {

    private ResinContainerRecipeProvider() {}

    public static void provide(RecipeBuilder builder) {
        createContainerRecipe(builder, AlienItems.RESIN_BALL, AlienItems.CHITIN, AlienBlocks.RESIN_CONTAINER);
        createContainerRecipe(
            builder,
            AlienItems.NETHER_RESIN_BALL,
            AlienItems.NETHER_CHITIN,
            AlienBlocks.NETHER_RESIN_CONTAINER
        );
        createContainerRecipe(
            builder,
            AlienItems.ABERRANT_RESIN_BALL,
            AlienItems.ABERRANT_CHITIN,
            AlienBlocks.ABERRANT_RESIN_CONTAINER
        );
        createContainerRecipe(
            builder,
            AlienItems.IRRADIATED_RESIN_BALL,
            AlienItems.IRRADIATED_CHITIN,
            AlienBlocks.IRRADIATED_RESIN_CONTAINER
        );
    }

    private static void createContainerRecipe(
        RecipeBuilder builder,
        Supplier<? extends Item> resinBall,
        Supplier<? extends Item> chitin,
        BLibHolder<Block> container
    ) {
        builder.shaped()
            .withCategory(RecipeCategory.DECORATIONS)
            .define('R', resinBall)
            .define('C', chitin)
            // ⚠ THE SPACE IN THE MIDDLE IS THE HOLLOW - a shaped recipe treats ' ' as "must be empty", which is what
            // makes the centre a real requirement rather than a slot that accepts anything.
            .pattern("RCR")
            .pattern("C C")
            .pattern("RCR")
            .into(1, container);
    }
}
