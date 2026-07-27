package com.alien.common.registry.init.creative_mode_tab.initializer;

import com.alien.common.registry.init.AlienPotions;
import com.blib.api.common.registry.v1.BLibHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.Objects;
import java.util.function.Consumer;

public class FoodAndDrinksCreativeModeTabInitializer {

    public static final Consumer<CreativeModeTab.Output> OUTPUT_CONSUMER = output -> {
        acceptPotion(output, AlienPotions.BLOOD_LOSS);
        acceptPotion(output, AlienPotions.LONG_BLOOD_LOSS);
        acceptPotion(output, AlienPotions.STRONG_BLOOD_LOSS);
        acceptPotion(output, AlienPotions.METAMORPHOSIS);
        acceptPotion(output, AlienPotions.GROWTH_SUPPRESSION);
        acceptPotion(output, AlienPotions.SCOURGE);

        acceptSplashPotion(output, AlienPotions.BLOOD_LOSS);
        acceptSplashPotion(output, AlienPotions.LONG_BLOOD_LOSS);
        acceptSplashPotion(output, AlienPotions.STRONG_BLOOD_LOSS);
        acceptSplashPotion(output, AlienPotions.METAMORPHOSIS);
        acceptSplashPotion(output, AlienPotions.GROWTH_SUPPRESSION);
        acceptSplashPotion(output, AlienPotions.SCOURGE);

        acceptLingeringPotion(output, AlienPotions.BLOOD_LOSS);
        acceptLingeringPotion(output, AlienPotions.LONG_BLOOD_LOSS);
        acceptLingeringPotion(output, AlienPotions.STRONG_BLOOD_LOSS);
        acceptLingeringPotion(output, AlienPotions.METAMORPHOSIS);
        acceptLingeringPotion(output, AlienPotions.GROWTH_SUPPRESSION);
        acceptLingeringPotion(output, AlienPotions.SCOURGE);
    };

    private static void acceptPotion(CreativeModeTab.Output output, BLibHolder<Potion> potionHolder) {
        var stack = new ItemStack(Items.POTION);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Objects.requireNonNull(potionHolder.getBackingHolder())));
        output.accept(stack);
    }

    private static void acceptSplashPotion(CreativeModeTab.Output output, BLibHolder<Potion> potionHolder) {
        var stack = new ItemStack(Items.SPLASH_POTION);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Objects.requireNonNull(potionHolder.getBackingHolder())));
        output.accept(stack);
    }

    private static void acceptLingeringPotion(CreativeModeTab.Output output, BLibHolder<Potion> potionHolder) {
        var stack = new ItemStack(Items.LINGERING_POTION);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Objects.requireNonNull(potionHolder.getBackingHolder())));
        output.accept(stack);
    }
}
