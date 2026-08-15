package com.alien.fabric.data.recipe;

import com.alien.Alien;
import com.alien.fabric.data.recipe.impl.ArmorRecipeProvider;
import com.alien.fabric.data.recipe.impl.MiscellaneousRecipeProvider;
import com.alien.fabric.data.recipe.impl.ResinContainerRecipeProvider;
import com.alien.fabric.data.recipe.impl.chitin.ChitinRecipeProvider;
import com.alien.fabric.data.recipe.impl.resin.ResinRecipeProvider;
import com.blib.fabric.data.recipe.builder.RecipeBuilder;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeOutput;

import java.util.concurrent.CompletableFuture;

public class RecipeProvider extends FabricRecipeProvider {

    public RecipeProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    public void buildRecipes(RecipeOutput recipeOutput) {
        var builder = RecipeBuilder.with(Alien.MOD, recipeOutput, this::withConditions);
        ArmorRecipeProvider.provide(builder);
        MiscellaneousRecipeProvider.provide(builder);
        ChitinRecipeProvider.provide(builder);
        ResinRecipeProvider.provide(builder);
        ResinContainerRecipeProvider.provide(builder);
    }

}
