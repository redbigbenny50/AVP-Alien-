package com.alien.fabric.data.recipe.impl;

import com.alien.common.registry.init.item.AlienArmorItems;
import com.alien.common.registry.init.item.AlienItems;
import com.blib.fabric.data.recipe.RecipeTemplates;
import com.blib.fabric.data.recipe.builder.RecipeBuilder;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

public class ArmorRecipeProvider {

    /**
     * Convention tag for lead ingots ({@code c:ingots/lead} - AVPHuman's lead_ingot is a member). Referenced by tag so
     * no cross-mod class or item id is hard-wired here.
     */
    private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> LEAD_INGOTS =
        net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ITEM,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "ingots/lead")
        );

    public static void provide(RecipeBuilder builder) {
        createPlatedChitinArmorSetRecipes(builder);
        createPlatedNetherChitinArmorSetRecipes(builder);
        createPlatedAberrantChitinArmorSetRecipes(builder);
        createPlatedIrradiatedChitinArmorSetRecipes(builder);

        createStandardArmorSetRecipes(
            builder,
            AlienItems.CHITIN.get(),
            AlienArmorItems.CHITIN_HELMET.get(),
            AlienArmorItems.CHITIN_CHESTPLATE.get(),
            AlienArmorItems.CHITIN_LEGGINGS.get(),
            AlienArmorItems.CHITIN_BOOTS.get()
        );
        createStandardArmorSetRecipes(
            builder,
            AlienItems.NETHER_CHITIN.get(),
            AlienArmorItems.NETHER_CHITIN_HELMET.get(),
            AlienArmorItems.NETHER_CHITIN_CHESTPLATE.get(),
            AlienArmorItems.NETHER_CHITIN_LEGGINGS.get(),
            AlienArmorItems.NETHER_CHITIN_BOOTS.get()
        );
        createStandardArmorSetRecipes(
            builder,
            AlienItems.ABERRANT_CHITIN.get(),
            AlienArmorItems.ABERRANT_CHITIN_HELMET.get(),
            AlienArmorItems.ABERRANT_CHITIN_CHESTPLATE.get(),
            AlienArmorItems.ABERRANT_CHITIN_LEGGINGS.get(),
            AlienArmorItems.ABERRANT_CHITIN_BOOTS.get()
        );
        createIrradiatedChitinArmorSetRecipes(builder);
    }

    private static void createPlatedAberrantChitinArmorSetRecipes(RecipeBuilder builder) {
        builder.shapeless()
            .withCategory(RecipeCategory.COMBAT)
            .requires(1, AlienArmorItems.ABERRANT_CHITIN_HELMET)
            .requires(1, AlienItems.PLATED_ABERRANT_CHITIN)
            .into(1, AlienArmorItems.PLATED_ABERRANT_CHITIN_HELMET);
        builder.shapeless()
            .withCategory(RecipeCategory.COMBAT)
            .requires(1, AlienArmorItems.ABERRANT_CHITIN_CHESTPLATE)
            .requires(1, AlienItems.PLATED_ABERRANT_CHITIN)
            .into(1, AlienArmorItems.PLATED_ABERRANT_CHITIN_CHESTPLATE);
        builder.shapeless()
            .withCategory(RecipeCategory.COMBAT)
            .requires(1, AlienArmorItems.ABERRANT_CHITIN_LEGGINGS)
            .requires(1, AlienItems.PLATED_ABERRANT_CHITIN)
            .into(1, AlienArmorItems.PLATED_ABERRANT_CHITIN_LEGGINGS);
        builder.shapeless()
            .withCategory(RecipeCategory.COMBAT)
            .requires(1, AlienArmorItems.ABERRANT_CHITIN_BOOTS)
            .requires(1, AlienItems.PLATED_ABERRANT_CHITIN)
            .into(1, AlienArmorItems.PLATED_ABERRANT_CHITIN_BOOTS);
    }

    /**
     * Lead-lined irradiated chitin. Unlike every other family these are NOT the stock armor templates: each piece takes
     * a single lead ingot, placed where the lining would sit against the wearer - the CENTRE of the grid for helmet,
     * leggings and boots, and the TOP-MIDDLE for the chestplate (the collar of the piece). The lead is what makes the
     * suit safe to wear: the irradiated armors are injected into AVPHuman's radiation_resistant_armors tag, so a full
     * set grants radiation immunity on top of the usual chitin protection.
     * <p>
     * The lead is taken from the CONVENTION tag rather than avp_human's item directly, so the recipe needs no hard
     * dependency and any mod's lead ingot works. Without a lead-providing mod the tag is empty and these simply cannot
     * be crafted - which is correct, since the irradiated family is AVPHuman-gated content anyway.
     * </p>
     * <p>
     * The PLATED upgrades deliberately take no second ingot: plating is added to the outside of a piece whose lining is
     * already there.
     * </p>
     */
    private static void createIrradiatedChitinArmorSetRecipes(RecipeBuilder builder) {
        builder.shaped()
            .withCategory(RecipeCategory.COMBAT)
            .define('C', AlienItems.IRRADIATED_CHITIN)
            .define('L', LEAD_INGOTS)
            .pattern("CCC")
            .pattern("CLC")
            .into(1, AlienArmorItems.IRRADIATED_CHITIN_HELMET);
        builder.shaped()
            .withCategory(RecipeCategory.COMBAT)
            .define('C', AlienItems.IRRADIATED_CHITIN)
            .define('L', LEAD_INGOTS)
            .pattern("CLC")
            .pattern("CCC")
            .pattern("CCC")
            .into(1, AlienArmorItems.IRRADIATED_CHITIN_CHESTPLATE);
        builder.shaped()
            .withCategory(RecipeCategory.COMBAT)
            .define('C', AlienItems.IRRADIATED_CHITIN)
            .define('L', LEAD_INGOTS)
            .pattern("CCC")
            .pattern("CLC")
            .pattern("C C")
            .into(1, AlienArmorItems.IRRADIATED_CHITIN_LEGGINGS);
        builder.shaped()
            .withCategory(RecipeCategory.COMBAT)
            .define('C', AlienItems.IRRADIATED_CHITIN)
            .define('L', LEAD_INGOTS)
            .pattern("CLC")
            .pattern("C C")
            .into(1, AlienArmorItems.IRRADIATED_CHITIN_BOOTS);
    }

    private static void createPlatedIrradiatedChitinArmorSetRecipes(RecipeBuilder builder) {
        builder.shapeless()
            .withCategory(RecipeCategory.COMBAT)
            .requires(1, AlienArmorItems.IRRADIATED_CHITIN_HELMET)
            .requires(1, AlienItems.PLATED_IRRADIATED_CHITIN)
            .into(1, AlienArmorItems.PLATED_IRRADIATED_CHITIN_HELMET);
        builder.shapeless()
            .withCategory(RecipeCategory.COMBAT)
            .requires(1, AlienArmorItems.IRRADIATED_CHITIN_CHESTPLATE)
            .requires(1, AlienItems.PLATED_IRRADIATED_CHITIN)
            .into(1, AlienArmorItems.PLATED_IRRADIATED_CHITIN_CHESTPLATE);
        builder.shapeless()
            .withCategory(RecipeCategory.COMBAT)
            .requires(1, AlienArmorItems.IRRADIATED_CHITIN_LEGGINGS)
            .requires(1, AlienItems.PLATED_IRRADIATED_CHITIN)
            .into(1, AlienArmorItems.PLATED_IRRADIATED_CHITIN_LEGGINGS);
        builder.shapeless()
            .withCategory(RecipeCategory.COMBAT)
            .requires(1, AlienArmorItems.IRRADIATED_CHITIN_BOOTS)
            .requires(1, AlienItems.PLATED_IRRADIATED_CHITIN)
            .into(1, AlienArmorItems.PLATED_IRRADIATED_CHITIN_BOOTS);
    }

    private static void createPlatedNetherChitinArmorSetRecipes(RecipeBuilder builder) {
        builder.shapeless()
            .withCategory(RecipeCategory.COMBAT)
            .requires(1, AlienArmorItems.NETHER_CHITIN_HELMET)
            .requires(1, AlienItems.PLATED_NETHER_CHITIN)
            .into(1, AlienArmorItems.PLATED_NETHER_CHITIN_HELMET);
        builder.shapeless()
            .withCategory(RecipeCategory.COMBAT)
            .requires(1, AlienArmorItems.NETHER_CHITIN_CHESTPLATE)
            .requires(1, AlienItems.PLATED_NETHER_CHITIN)
            .into(1, AlienArmorItems.PLATED_NETHER_CHITIN_CHESTPLATE);
        builder.shapeless()
            .withCategory(RecipeCategory.COMBAT)
            .requires(1, AlienArmorItems.NETHER_CHITIN_LEGGINGS)
            .requires(1, AlienItems.PLATED_NETHER_CHITIN)
            .into(1, AlienArmorItems.PLATED_NETHER_CHITIN_LEGGINGS);
        builder.shapeless()
            .withCategory(RecipeCategory.COMBAT)
            .requires(1, AlienArmorItems.NETHER_CHITIN_BOOTS)
            .requires(1, AlienItems.PLATED_NETHER_CHITIN)
            .into(1, AlienArmorItems.PLATED_NETHER_CHITIN_BOOTS);
    }

    private static void createPlatedChitinArmorSetRecipes(RecipeBuilder builder) {
        builder.shapeless()
            .withCategory(RecipeCategory.COMBAT)
            .requires(1, AlienArmorItems.CHITIN_HELMET)
            .requires(1, AlienItems.PLATED_CHITIN)
            .into(1, AlienArmorItems.PLATED_CHITIN_HELMET);
        builder.shapeless()
            .withCategory(RecipeCategory.COMBAT)
            .requires(1, AlienArmorItems.CHITIN_CHESTPLATE)
            .requires(1, AlienItems.PLATED_CHITIN)
            .into(1, AlienArmorItems.PLATED_CHITIN_CHESTPLATE);
        builder.shapeless()
            .withCategory(RecipeCategory.COMBAT)
            .requires(1, AlienArmorItems.CHITIN_LEGGINGS)
            .requires(1, AlienItems.PLATED_CHITIN)
            .into(1, AlienArmorItems.PLATED_CHITIN_LEGGINGS);
        builder.shapeless()
            .withCategory(RecipeCategory.COMBAT)
            .requires(1, AlienArmorItems.CHITIN_BOOTS)
            .requires(1, AlienItems.PLATED_CHITIN)
            .into(1, AlienArmorItems.PLATED_CHITIN_BOOTS);
    }

    private static void createStandardArmorSetRecipes(
        RecipeBuilder builder,
        ItemLike base,
        Item helmet,
        Item chestplate,
        Item leggings,
        Item boots
    ) {
        builder.shaped()
            .apply(RecipeTemplates.HELMET.apply(base))
            .into(1, helmet);

        builder.shaped()
            .apply(RecipeTemplates.CHESTPLATE.apply(base))
            .into(1, chestplate);

        builder.shaped()
            .apply(RecipeTemplates.LEGGINGS.apply(base))
            .into(1, leggings);

        builder.shaped()
            .apply(RecipeTemplates.BOOTS.apply(base))
            .into(1, boots);
    }
}
