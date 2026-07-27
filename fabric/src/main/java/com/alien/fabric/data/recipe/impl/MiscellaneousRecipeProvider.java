package com.alien.fabric.data.recipe.impl;

import com.alien.common.registry.init.block.AlienBlocks;
import com.alien.common.registry.init.item.AlienItems;
import com.alien.common.registry.init.item.AlienXenomorphHeadItems;
import com.alien.fabric.compatibility.avp_human.AVPHumanFabric;
import com.blib.fabric.data.recipe.RecipeConstants;
import com.blib.fabric.data.recipe.builder.RecipeBuilder;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.world.item.Items;

public class MiscellaneousRecipeProvider {

    public static void provide(RecipeBuilder builder) {
        provideMiscellaneousNetherRecipes(builder);
        provideMiscellaneousAberrantRecipes(builder);
        provideMiscellaneousIrradiatedRecipes(builder.withCondition(AVPHumanFabric.IS_LOADED));

        builder.smelt(AlienItems.RESIN_BALL)
            .withCategory(RecipeCategory.MISC)
            .withExperience(RecipeConstants.VERY_COMMON_SMELT_EXPERIENCE)
            .into(Items.SLIME_BALL);

        builder.shaped()
            .withCategory(RecipeCategory.MISC)
            .define('I', AlienItems.RAW_ROYAL_JELLY)
            .pattern("III")
            .pattern("III")
            .pattern("III")
            .into(1, AlienBlocks.ROYAL_JELLY_BLOCK);

        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .requires(1, AlienBlocks.ROYAL_JELLY_BLOCK)
            .into(9, AlienItems.RAW_ROYAL_JELLY);

        builder.shaped()
            .withCategory(RecipeCategory.MISC)
            .define('I', AlienItems.RAW_SCOURGE_JELLY)
            .pattern("III")
            .pattern("III")
            .pattern("III")
            .into(1, AlienBlocks.SCOURGE_JELLY_BLOCK);

        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .requires(1, AlienBlocks.SCOURGE_JELLY_BLOCK)
            .into(9, AlienItems.RAW_SCOURGE_JELLY);

        builder.shaped()
            .withCategory(RecipeCategory.MISC)
            .define('I', AlienItems.RAW_IRRADIATED_JELLY)
            .pattern("III")
            .pattern("III")
            .pattern("III")
            .into(1, AlienBlocks.IRRADIATED_JELLY_BLOCK);

        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .requires(1, AlienBlocks.IRRADIATED_JELLY_BLOCK)
            .into(9, AlienItems.RAW_IRRADIATED_JELLY);

        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .requires(1, Items.POISONOUS_POTATO)
            .requires(1, AlienItems.RAW_ROYAL_JELLY)
            .into(1, AlienItems.POISON_JELLY);

        // Inhibitor: an iron frame (ingots top-centre + both bottom corners, nuggets mid-sides) around poison jelly.
        builder.shaped()
            .withCategory(RecipeCategory.TOOLS)
            .define('I', Items.IRON_INGOT)
            .define('N', Items.IRON_NUGGET)
            .define('J', AlienItems.POISON_JELLY)
            .pattern(" I ")
            .pattern("NJN")
            .pattern("I I")
            .into(1, AlienItems.INHIBITOR);

        // Capture chain: three vanilla chains stacked in a column.
        builder.shaped()
            .withCategory(RecipeCategory.TOOLS)
            .define('C', Items.CHAIN)
            .pattern("C")
            .pattern("C")
            .pattern("C")
            .into(2, AlienItems.CAPTURE_CHAIN);

        // Anchor: an iron ingot centred over a row of three iron blocks -> 8 anchors.
        builder.shaped()
            .withCategory(RecipeCategory.MISC)
            .define('I', Items.IRON_INGOT)
            .define('B', Items.IRON_BLOCK)
            .pattern(" I ")
            .pattern("BBB")
            .into(8, AlienItems.ANCHOR);

        // Tracker tag: redstone + amethyst shard + glowstone dust + iron ingot.
        builder.shapeless()
            .withCategory(RecipeCategory.TOOLS)
            .requires(1, Items.REDSTONE)
            .requires(1, Items.AMETHYST_SHARD)
            .requires(1, Items.GLOWSTONE_DUST)
            .requires(1, Items.IRON_INGOT)
            .into(1, AlienItems.TRACKER);

        // Tracking PDA: redstone, amethyst shard, observer stacked in a column.
        builder.shaped()
            .withCategory(RecipeCategory.TOOLS)
            .define('R', Items.REDSTONE)
            .define('A', Items.AMETHYST_SHARD)
            .define('O', Items.OBSERVER)
            .pattern("R")
            .pattern("A")
            .pattern("O")
            .into(1, AlienItems.TRACKING_PDA);

        builder.shapeless()
            .withCategory(RecipeCategory.MISC)
            .requires(9, AlienItems.ALIEN_MUSIC_DISC_1_FRAGMENT)
            .into(1, AlienItems.ALIEN_MUSIC_DISC_1);

        // Head trophy + vanilla shield -> head shield. One-way conversion: crafting commits to combat utility.
        AlienXenomorphHeadItems.ALL.forEach(
            entry -> builder.shapeless()
                .withCategory(RecipeCategory.COMBAT)
                .requires(1, entry.head())
                .requires(1, Items.SHIELD)
                .into(1, entry.headShield())
        );
    }

    private static void provideMiscellaneousNetherRecipes(RecipeBuilder builder) {
        builder.smelt(AlienItems.NETHER_RESIN_BALL)
            .withCategory(RecipeCategory.MISC)
            .withExperience(RecipeConstants.VERY_COMMON_SMELT_EXPERIENCE)
            .into(Items.SLIME_BALL);
    }

    private static void provideMiscellaneousAberrantRecipes(RecipeBuilder builder) {
        builder.smelt(AlienItems.ABERRANT_RESIN_BALL)
            .withCategory(RecipeCategory.MISC)
            .withExperience(RecipeConstants.VERY_COMMON_SMELT_EXPERIENCE)
            .into(Items.SLIME_BALL);
    }

    private static void provideMiscellaneousIrradiatedRecipes(RecipeBuilder builder) {
        builder.blast(AlienItems.IRRADIATED_CHITIN)
            .withCategory(RecipeCategory.MISC)
            .withExperience(RecipeConstants.UNCOMMON_MATERIAL_SMELT_EXPERIENCE)
            .into(AlienItems.CHITIN);

        builder.blast(AlienItems.PLATED_IRRADIATED_CHITIN)
            .withCategory(RecipeCategory.MISC)
            .withExperience(RecipeConstants.UNCOMMON_MATERIAL_SMELT_EXPERIENCE)
            .into(AlienItems.PLATED_CHITIN);

        builder.smelt(AlienItems.IRRADIATED_RESIN_BALL)
            .withCategory(RecipeCategory.MISC)
            .withExperience(RecipeConstants.VERY_COMMON_SMELT_EXPERIENCE)
            .into(Items.SLIME_BALL);
    }
}
