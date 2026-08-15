package com.alien.fabric.data.lang.en_us.provider;

import com.alien.common.registry.key.AlienCreativeModeTabKeys;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;

import java.util.function.Consumer;

public class EnUsCreativeModeTabProvider {

    public static final Consumer<FabricLanguageProvider.TranslationBuilder> CONSUMER = builder -> {
        builder.add(AlienCreativeModeTabKeys.BLOCKS_KEY, "Blocks (AVP: Alien)");
        builder.add(AlienCreativeModeTabKeys.FUNCTIONAL_BLOCKS_KEY, "Functional Blocks (AVP: Alien)");
        builder.add(AlienCreativeModeTabKeys.COMBAT_KEY, "Combat (AVP: Alien)");
        builder.add(AlienCreativeModeTabKeys.INGREDIENTS_KEY, "Ingredients (AVP: Alien)");
        builder.add(AlienCreativeModeTabKeys.SPAWN_EGGS_KEY, "Spawn Eggs (AVP: Alien)");
        builder.add(AlienCreativeModeTabKeys.FOOD_AND_DRINKS_KEY, "Food & Drinks (AVP: Alien)");
        builder.add(AlienCreativeModeTabKeys.TOOLS_AND_UTILITIES_KEY, "Tools & Utilities (AVP: Alien)");
    };
}
