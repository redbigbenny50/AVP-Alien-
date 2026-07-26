package com.alien.common.registry.init.creative_mode_tab.initializer;

import com.alien.common.registry.init.item.AlienArmorItems;
import com.alien.compatibility.avp_human.AVPHuman;
import net.minecraft.world.item.CreativeModeTab;

import java.util.function.Consumer;

public class CombatCreativeModeTabInitializer {

    public static final Consumer<CreativeModeTab.Output> OUTPUT_CONSUMER = output -> {
        addBaseXenomorphArmors(output);
        addNetherXenomorphArmors(output);
        addAberrantXenomorphArmors(output);

        if (AVPHuman.MOD.isLoaded()) {
            addIrradiatedXenomorphArmors(output);
        }

        XenomorphHeadCreativeModeTabEntries.addHeadShields(output);
    };

    private static void addBaseXenomorphArmors(CreativeModeTab.Output output) {
        CreativeModeTabUtil.accept(output, AlienArmorItems.CHITIN_HELMET);
        CreativeModeTabUtil.accept(output, AlienArmorItems.CHITIN_CHESTPLATE);
        CreativeModeTabUtil.accept(output, AlienArmorItems.CHITIN_LEGGINGS);
        CreativeModeTabUtil.accept(output, AlienArmorItems.CHITIN_BOOTS);

        CreativeModeTabUtil.accept(output, AlienArmorItems.PLATED_CHITIN_HELMET);
        CreativeModeTabUtil.accept(output, AlienArmorItems.PLATED_CHITIN_CHESTPLATE);
        CreativeModeTabUtil.accept(output, AlienArmorItems.PLATED_CHITIN_LEGGINGS);
        CreativeModeTabUtil.accept(output, AlienArmorItems.PLATED_CHITIN_BOOTS);
    }

    private static void addNetherXenomorphArmors(CreativeModeTab.Output output) {
        CreativeModeTabUtil.accept(output, AlienArmorItems.NETHER_CHITIN_HELMET);
        CreativeModeTabUtil.accept(output, AlienArmorItems.NETHER_CHITIN_CHESTPLATE);
        CreativeModeTabUtil.accept(output, AlienArmorItems.NETHER_CHITIN_LEGGINGS);
        CreativeModeTabUtil.accept(output, AlienArmorItems.NETHER_CHITIN_BOOTS);

        CreativeModeTabUtil.accept(output, AlienArmorItems.PLATED_NETHER_CHITIN_HELMET);
        CreativeModeTabUtil.accept(output, AlienArmorItems.PLATED_NETHER_CHITIN_CHESTPLATE);
        CreativeModeTabUtil.accept(output, AlienArmorItems.PLATED_NETHER_CHITIN_LEGGINGS);
        CreativeModeTabUtil.accept(output, AlienArmorItems.PLATED_NETHER_CHITIN_BOOTS);
    }

    private static void addAberrantXenomorphArmors(CreativeModeTab.Output output) {
        CreativeModeTabUtil.accept(output, AlienArmorItems.ABERRANT_CHITIN_HELMET);
        CreativeModeTabUtil.accept(output, AlienArmorItems.ABERRANT_CHITIN_CHESTPLATE);
        CreativeModeTabUtil.accept(output, AlienArmorItems.ABERRANT_CHITIN_LEGGINGS);
        CreativeModeTabUtil.accept(output, AlienArmorItems.ABERRANT_CHITIN_BOOTS);

        CreativeModeTabUtil.accept(output, AlienArmorItems.PLATED_ABERRANT_CHITIN_HELMET);
        CreativeModeTabUtil.accept(output, AlienArmorItems.PLATED_ABERRANT_CHITIN_CHESTPLATE);
        CreativeModeTabUtil.accept(output, AlienArmorItems.PLATED_ABERRANT_CHITIN_LEGGINGS);
        CreativeModeTabUtil.accept(output, AlienArmorItems.PLATED_ABERRANT_CHITIN_BOOTS);
    }

    private static void addIrradiatedXenomorphArmors(CreativeModeTab.Output output) {
        CreativeModeTabUtil.accept(output, AlienArmorItems.IRRADIATED_CHITIN_HELMET);
        CreativeModeTabUtil.accept(output, AlienArmorItems.IRRADIATED_CHITIN_CHESTPLATE);
        CreativeModeTabUtil.accept(output, AlienArmorItems.IRRADIATED_CHITIN_LEGGINGS);
        CreativeModeTabUtil.accept(output, AlienArmorItems.IRRADIATED_CHITIN_BOOTS);

        CreativeModeTabUtil.accept(output, AlienArmorItems.PLATED_IRRADIATED_CHITIN_HELMET);
        CreativeModeTabUtil.accept(output, AlienArmorItems.PLATED_IRRADIATED_CHITIN_CHESTPLATE);
        CreativeModeTabUtil.accept(output, AlienArmorItems.PLATED_IRRADIATED_CHITIN_LEGGINGS);
        CreativeModeTabUtil.accept(output, AlienArmorItems.PLATED_IRRADIATED_CHITIN_BOOTS);
    }

}
