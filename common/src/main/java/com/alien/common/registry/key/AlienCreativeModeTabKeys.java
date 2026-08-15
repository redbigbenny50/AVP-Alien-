package com.alien.common.registry.key;

import com.alien.AlienResources;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

public class AlienCreativeModeTabKeys {

    public static final ResourceKey<CreativeModeTab> BLOCKS_KEY = createResourceKey("alien_blocks");

    public static final ResourceKey<CreativeModeTab> FUNCTIONAL_BLOCKS_KEY = createResourceKey("alien_functional_blocks");

    public static final ResourceKey<CreativeModeTab> COMBAT_KEY = createResourceKey("alien_combat");

    public static final ResourceKey<CreativeModeTab> INGREDIENTS_KEY = createResourceKey("alien_ingredients");

    public static final ResourceKey<CreativeModeTab> SPAWN_EGGS_KEY = createResourceKey("alien_spawn_eggs");

    public static final ResourceKey<CreativeModeTab> FOOD_AND_DRINKS_KEY = createResourceKey("alien_food_and_drinks");

    public static final ResourceKey<CreativeModeTab> TOOLS_AND_UTILITIES_KEY = createResourceKey("alien_tools_and_utilities");

    public static ResourceKey<CreativeModeTab> createResourceKey(String name) {
        return ResourceKey.create(
            BuiltInRegistries.CREATIVE_MODE_TAB.key(),
            AlienResources.location(name)
        );
    }
}
