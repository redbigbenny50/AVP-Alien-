package com.alien.common.registry.init.creative_mode_tab;

import com.alien.Alien;
import com.alien.common.registry.init.block.AlienResinBlocks;
import com.alien.common.registry.init.creative_mode_tab.initializer.BlocksCreativeModeTabInitializer;
import com.alien.common.registry.init.creative_mode_tab.initializer.CombatCreativeModeTabInitializer;
import com.alien.common.registry.init.creative_mode_tab.initializer.FoodAndDrinksCreativeModeTabInitializer;
import com.alien.common.registry.init.creative_mode_tab.initializer.FunctionalBlocksCreativeModeTabInitializer;
import com.alien.common.registry.init.creative_mode_tab.initializer.IngredientsCreativeModeTabInitializer;
import com.alien.common.registry.init.creative_mode_tab.initializer.SpawnEggsCreativeModeTabInitializer;
import com.alien.common.registry.init.creative_mode_tab.initializer.ToolsAndUtilitiesCreativeModeTabInitializer;
import com.alien.common.registry.init.item.AlienArmorItems;
import com.alien.common.registry.init.item.AlienItems;
import com.alien.common.registry.init.item.AlienSpawnEggItems;
import com.alien.common.registry.key.AlienCreativeModeTabKeys;
import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class AlienCreativeModeTabs {

    private static final BLibRegistry<CreativeModeTab> REGISTRY = Alien.MOD.registries().create(BuiltInRegistries.CREATIVE_MODE_TAB);

    private static final String BASE_PATH = "creativeModeTab";

    public static final BLibHolder<CreativeModeTab> BLOCKS = create(
        AlienCreativeModeTabKeys.BLOCKS_KEY,
        () -> new ItemStack(AlienResinBlocks.RESIN.get()),
        BlocksCreativeModeTabInitializer.OUTPUT_CONSUMER
    );

    public static final BLibHolder<CreativeModeTab> FUNCTIONAL_BLOCKS = create(
        AlienCreativeModeTabKeys.FUNCTIONAL_BLOCKS_KEY,
        () -> new ItemStack(AlienItems.QUEEN_HEAD.get()),
        FunctionalBlocksCreativeModeTabInitializer.OUTPUT_CONSUMER
    );

    public static final BLibHolder<CreativeModeTab> COMBAT = create(
        AlienCreativeModeTabKeys.COMBAT_KEY,
        () -> new ItemStack(AlienArmorItems.CHITIN_HELMET.get()),
        CombatCreativeModeTabInitializer.OUTPUT_CONSUMER
    );

    public static final BLibHolder<CreativeModeTab> INGREDIENTS = create(
        AlienCreativeModeTabKeys.INGREDIENTS_KEY,
        () -> new ItemStack(AlienItems.CHITIN.get()),
        IngredientsCreativeModeTabInitializer.OUTPUT_CONSUMER
    );

    public static final BLibHolder<CreativeModeTab> SPAWN_EGGS = create(
        AlienCreativeModeTabKeys.SPAWN_EGGS_KEY,
        () -> new ItemStack(AlienSpawnEggItems.QUEEN_SPAWN_EGG.get()),
        SpawnEggsCreativeModeTabInitializer.OUTPUT_CONSUMER
    );

    public static final BLibHolder<CreativeModeTab> FOOD_AND_DRINKS = create(
        AlienCreativeModeTabKeys.FOOD_AND_DRINKS_KEY,
        () -> new ItemStack(Items.POTION),
        FoodAndDrinksCreativeModeTabInitializer.OUTPUT_CONSUMER
    );

    public static final BLibHolder<CreativeModeTab> TOOLS_AND_UTILITIES = create(
        AlienCreativeModeTabKeys.TOOLS_AND_UTILITIES_KEY,
        () -> new ItemStack(AlienItems.ALIEN_MUSIC_DISC_1.get()),
        ToolsAndUtilitiesCreativeModeTabInitializer.OUTPUT_CONSUMER
    );

    private static BLibHolder<CreativeModeTab> create(
        ResourceKey<CreativeModeTab> resourceKey,
        Supplier<ItemStack> iconSupplier,
        Consumer<CreativeModeTab.Output> outputConsumer
    ) {
        var path = resourceKey.location().getPath();

        return REGISTRY.createHolder(
            path,
            () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                .icon(iconSupplier)
                .title(Component.translatable(BASE_PATH + "." + Alien.MOD_ID + "." + path))
                .displayItems((itemDisplayParameters, output) -> outputConsumer.accept(output))
                .build()
        );
    }

    public static void initialize() {
        REGISTRY.registerAll();
    }
}
