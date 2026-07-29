package com.alien.common.registry.init.creative_mode_tab.initializer;

import com.alien.common.registry.init.item.AlienSpawnEggItems;
import com.alien.compatibility.avp_human.AVPHuman;
import com.alien.compatibility.avp_predator.AVPPredator;
import net.minecraft.world.item.CreativeModeTab;

import java.util.function.Consumer;

public class SpawnEggsCreativeModeTabInitializer {

    public static final Consumer<CreativeModeTab.Output> OUTPUT_CONSUMER = output -> {
        addBaseXenomorphSpawnEggs(output);
        addNetherXenomorphSpawnEggs(output);
        addAberrantXenomorphSpawnEggs(output);

        if (AVPHuman.MOD.isLoaded()) {
            addIrradiatedXenomorphSpawnEggs(output);
        }
    };

    private static void addBaseXenomorphSpawnEggs(CreativeModeTab.Output output) {
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.OVOMORPH_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ROYAL_OVOMORPH_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.FACEHUGGER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ROYAL_FACEHUGGER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.CHESTBURSTER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ROYAL_CHESTBURSTER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ADOLESCENT_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ROYAL_ADOLESCENT_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.DRONE_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.WARRIOR_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.PRAETORIAN_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.QUEEN_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.EMPRESS_SPAWN_EGG);

        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.RUNNER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.BURSTER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.PROWLER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.CRUSHER_SPAWN_EGG);

        if (AVPPredator.MOD.isLoaded()) {
            CreativeModeTabUtil.accept(output, AlienSpawnEggItems.PREDALIEN_CHESTBURSTER_SPAWN_EGG);
            CreativeModeTabUtil.accept(output, AlienSpawnEggItems.PREDALIEN_ADOLESCENT_SPAWN_EGG);
            CreativeModeTabUtil.accept(output, AlienSpawnEggItems.PREDALIEN_SPAWN_EGG);
        }

        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.BOILER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.SPITTER_SPAWN_EGG);

        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.CHRYSALIS_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.RAZOR_CLAW_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.CARRIER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.RAVAGER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.HARBINGER_SPAWN_EGG);
    }

    private static void addNetherXenomorphSpawnEggs(CreativeModeTab.Output output) {
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.NETHER_OVOMORPH_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ROYAL_NETHER_OVOMORPH_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.NETHER_FACEHUGGER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ROYAL_NETHER_FACEHUGGER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.NETHER_CHESTBURSTER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ROYAL_NETHER_CHESTBURSTER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.NETHER_ADOLESCENT_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ROYAL_NETHER_ADOLESCENT_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.NETHER_DRONE_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.NETHER_WARRIOR_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.NETHER_PRAETORIAN_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.NETHER_QUEEN_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.NETHER_EMPRESS_SPAWN_EGG);

        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.NETHER_RUNNER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.NETHER_BURSTER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.NETHER_PROWLER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.NETHER_CRUSHER_SPAWN_EGG);

        if (AVPPredator.MOD.isLoaded()) {
            CreativeModeTabUtil.accept(output, AlienSpawnEggItems.NETHER_PREDALIEN_CHESTBURSTER_SPAWN_EGG);
            CreativeModeTabUtil.accept(output, AlienSpawnEggItems.NETHER_PREDALIEN_ADOLESCENT_SPAWN_EGG);
            CreativeModeTabUtil.accept(output, AlienSpawnEggItems.NETHER_PREDALIEN_SPAWN_EGG);
        }

        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.NETHER_BOILER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.NETHER_SPITTER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.IRRADIATED_SPITTER_SPAWN_EGG);

        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.NETHER_CHRYSALIS_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.NETHER_RAZOR_CLAW_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.NETHER_CARRIER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.NETHER_RAVAGER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.NETHER_HARBINGER_SPAWN_EGG);
    }

    private static void addAberrantXenomorphSpawnEggs(CreativeModeTab.Output output) {
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ABERRANT_OVOMORPH_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ROYAL_ABERRANT_OVOMORPH_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ABERRANT_FACEHUGGER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ROYAL_ABERRANT_FACEHUGGER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ABERRANT_CHESTBURSTER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ROYAL_ABERRANT_CHESTBURSTER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ABERRANT_ADOLESCENT_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ROYAL_ABERRANT_ADOLESCENT_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ABERRANT_DRONE_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ABERRANT_WARRIOR_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ABERRANT_PRAETORIAN_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ABERRANT_QUEEN_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ABERRANT_EMPRESS_SPAWN_EGG);

        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ABERRANT_RUNNER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ABERRANT_BURSTER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ABERRANT_PROWLER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ABERRANT_CRUSHER_SPAWN_EGG);

        if (AVPPredator.MOD.isLoaded()) {
            CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ABERRANT_PREDALIEN_CHESTBURSTER_SPAWN_EGG);
            CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ABERRANT_PREDALIEN_ADOLESCENT_SPAWN_EGG);
            CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ABERRANT_PREDALIEN_SPAWN_EGG);
        }

        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ABERRANT_BOILER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ABERRANT_SPITTER_SPAWN_EGG);

        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ABERRANT_CHRYSALIS_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ABERRANT_RAZOR_CLAW_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ABERRANT_CARRIER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ABERRANT_RAVAGER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.ABERRANT_HARBINGER_SPAWN_EGG);
    }

    private static void addIrradiatedXenomorphSpawnEggs(CreativeModeTab.Output output) {
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.IRRADIATED_OVOMORPH_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.IRRADIATED_FACEHUGGER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.IRRADIATED_DRONE_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.IRRADIATED_WARRIOR_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.IRRADIATED_PRAETORIAN_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.IRRADIATED_QUEEN_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.IRRADIATED_EMPRESS_SPAWN_EGG);

        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.IRRADIATED_RUNNER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.IRRADIATED_BURSTER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.IRRADIATED_PROWLER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.IRRADIATED_CRUSHER_SPAWN_EGG);

        if (AVPPredator.MOD.isLoaded()) {
            CreativeModeTabUtil.accept(output, AlienSpawnEggItems.IRRADIATED_PREDALIEN_SPAWN_EGG);
        }

        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.IRRADIATED_CHRYSALIS_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.IRRADIATED_RAZOR_CLAW_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.IRRADIATED_CARRIER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.IRRADIATED_RAVAGER_SPAWN_EGG);
        CreativeModeTabUtil.accept(output, AlienSpawnEggItems.IRRADIATED_HARBINGER_SPAWN_EGG);
    }
}
