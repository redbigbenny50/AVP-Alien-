package com.alien.fabric.data.model;

import com.alien.common.registry.init.block.AlienResinBlocks;
import com.alien.common.registry.init.block.NetherAlienResinBlocks;
import com.alien.common.registry.init.item.AlienArmorItems;
import com.alien.common.registry.init.item.AlienItems;
import com.alien.common.registry.init.item.AlienSpawnEggItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.data.models.BlockModelGenerators;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.data.models.model.ModelTemplate;
import net.minecraft.data.models.model.ModelTemplates;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class ItemModelProvider extends FabricModelProvider {

    public ItemModelProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockModelGenerators generators) {
        generators.createSimpleFlatItemModel(NetherAlienResinBlocks.NETHER_RESIN_WEB.get());
        generators.createSimpleFlatItemModel(AlienResinBlocks.RESIN_WEB.get());
    }

    @Override
    public void generateItemModels(ItemModelGenerators generators) {
        generateStandardItem(generators, AlienArmorItems.ABERRANT_CHITIN_BOOTS);
        generateStandardItem(generators, AlienArmorItems.ABERRANT_CHITIN_CHESTPLATE);
        generateStandardItem(generators, AlienArmorItems.ABERRANT_CHITIN_HELMET);
        generateStandardItem(generators, AlienArmorItems.ABERRANT_CHITIN_LEGGINGS);
        generateStandardItem(generators, AlienArmorItems.CHITIN_BOOTS);
        generateStandardItem(generators, AlienArmorItems.CHITIN_CHESTPLATE);
        generateStandardItem(generators, AlienArmorItems.CHITIN_HELMET);
        generateStandardItem(generators, AlienArmorItems.CHITIN_LEGGINGS);
        generateStandardItem(generators, AlienArmorItems.IRRADIATED_CHITIN_BOOTS);
        generateStandardItem(generators, AlienArmorItems.IRRADIATED_CHITIN_CHESTPLATE);
        generateStandardItem(generators, AlienArmorItems.IRRADIATED_CHITIN_HELMET);
        generateStandardItem(generators, AlienArmorItems.IRRADIATED_CHITIN_LEGGINGS);
        generateStandardItem(generators, AlienArmorItems.NETHER_CHITIN_BOOTS);
        generateStandardItem(generators, AlienArmorItems.NETHER_CHITIN_CHESTPLATE);
        generateStandardItem(generators, AlienArmorItems.NETHER_CHITIN_HELMET);
        generateStandardItem(generators, AlienArmorItems.NETHER_CHITIN_LEGGINGS);
        generateStandardItem(generators, AlienArmorItems.PLATED_ABERRANT_CHITIN_BOOTS);
        generateStandardItem(generators, AlienArmorItems.PLATED_ABERRANT_CHITIN_CHESTPLATE);
        generateStandardItem(generators, AlienArmorItems.PLATED_ABERRANT_CHITIN_HELMET);
        generateStandardItem(generators, AlienArmorItems.PLATED_ABERRANT_CHITIN_LEGGINGS);
        generateStandardItem(generators, AlienArmorItems.PLATED_CHITIN_BOOTS);
        generateStandardItem(generators, AlienArmorItems.PLATED_CHITIN_CHESTPLATE);
        generateStandardItem(generators, AlienArmorItems.PLATED_CHITIN_HELMET);
        generateStandardItem(generators, AlienArmorItems.PLATED_CHITIN_LEGGINGS);
        generateStandardItem(generators, AlienArmorItems.PLATED_IRRADIATED_CHITIN_BOOTS);
        generateStandardItem(generators, AlienArmorItems.PLATED_IRRADIATED_CHITIN_CHESTPLATE);
        generateStandardItem(generators, AlienArmorItems.PLATED_IRRADIATED_CHITIN_HELMET);
        generateStandardItem(generators, AlienArmorItems.PLATED_IRRADIATED_CHITIN_LEGGINGS);
        generateStandardItem(generators, AlienArmorItems.PLATED_NETHER_CHITIN_BOOTS);
        generateStandardItem(generators, AlienArmorItems.PLATED_NETHER_CHITIN_CHESTPLATE);
        generateStandardItem(generators, AlienArmorItems.PLATED_NETHER_CHITIN_HELMET);
        generateStandardItem(generators, AlienArmorItems.PLATED_NETHER_CHITIN_LEGGINGS);
        generateStandardItem(generators, AlienItems.IRRADIATED_CHITIN);
        generateStandardItem(generators, AlienItems.PLATED_IRRADIATED_CHITIN);
        generateStandardItem(generators, AlienItems.IRRADIATED_RESIN_BALL);
        generateStandardItem(generators, AlienItems.ABERRANT_CHITIN);
        generateStandardItem(generators, AlienItems.ABERRANT_RESIN_BALL);
        generateStandardItem(generators, AlienItems.PLATED_ABERRANT_CHITIN);
        generateStandardItem(generators, AlienItems.ALIEN_MUSIC_DISC_1);
        generateStandardItem(generators, AlienItems.ALIEN_MUSIC_DISC_1_FRAGMENT);
        generateStandardItem(generators, AlienItems.CHITIN);
        generateStandardItem(generators, AlienItems.NETHER_CHITIN);
        generateStandardItem(generators, AlienItems.NETHER_RESIN_BALL);
        generateStandardItem(generators, AlienItems.OVOID_POTTERY_SHERD);
        generateStandardItem(generators, AlienItems.PARASITE_POTTERY_SHERD);
        generateStandardItem(generators, AlienItems.PLATED_CHITIN);
        generateStandardItem(generators, AlienItems.PLATED_NETHER_CHITIN);
        generateStandardItem(generators, AlienItems.RAW_ROYAL_JELLY);
        generateStandardItem(generators, AlienItems.RAW_SCOURGE_JELLY);
        generateStandardItem(generators, AlienItems.RESIN_BALL);
        generateStandardItem(generators, AlienItems.ROYALTY_POTTERY_SHERD);
        generateStandardItem(generators, AlienItems.VECTOR_POTTERY_SHERD);
        generateStandardItem(generators, AlienItems.POISON_JELLY);
        generateStandardItem(generators, AlienItems.TRACKING_PDA);

        AlienSpawnEggItems.REGISTRY.getAll()
            .forEach(holder -> generateStandardItem(generators, holder));
    }

    private void generateHandheldItem(ItemModelGenerators generators, Supplier<? extends Item> itemSupplier) {
        generateHandheldItem(generators, itemSupplier.get());
    }

    private void generateHandheldItem(ItemModelGenerators generators, Item item) {
        generateStandardItem(generators, item, ModelTemplates.FLAT_HANDHELD_ITEM);
    }

    private void generateStandardItem(ItemModelGenerators generators, Supplier<? extends Item> itemSupplier) {
        generateStandardItem(generators, itemSupplier.get());
    }

    private void generateStandardItem(ItemModelGenerators generators, Item item) {
        generateStandardItem(generators, item, ModelTemplates.FLAT_ITEM);
    }

    private void generateStandardItem(ItemModelGenerators generators, Item item, ModelTemplate modelTemplate) {
        generators.generateFlatItem(item, modelTemplate);
    }

    @Override
    public @NotNull String getName() {
        return "Item Model Definitions";
    }
}
