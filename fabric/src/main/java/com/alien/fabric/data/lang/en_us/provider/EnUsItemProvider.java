package com.alien.fabric.data.lang.en_us.provider;

import com.alien.common.registry.init.item.AlienArmorItems;
import com.alien.common.registry.init.item.AlienItems;
import com.alien.common.registry.init.item.AlienSpawnEggItems;
import com.alien.common.registry.init.item.AlienXenomorphHeadItems;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.world.item.Item;

import java.util.HashSet;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class EnUsItemProvider {

    private static final HashSet<Item> TOUCHED_ENTRIES = new HashSet<>();

    public static final Consumer<FabricLanguageProvider.TranslationBuilder> CONSUMER = builder -> {
        addItem(builder, AlienArmorItems.ABERRANT_CHITIN_BOOTS, "Aberrant Chitin Boots");
        addItem(builder, AlienArmorItems.ABERRANT_CHITIN_CHESTPLATE, "Aberrant Chitin Chestplate");
        addItem(builder, AlienArmorItems.ABERRANT_CHITIN_HELMET, "Aberrant Chitin Helmet");
        addItem(builder, AlienArmorItems.ABERRANT_CHITIN_LEGGINGS, "Aberrant Chitin Leggings");
        addItem(builder, AlienArmorItems.CHITIN_BOOTS, "Chitin Boots");
        addItem(builder, AlienArmorItems.CHITIN_CHESTPLATE, "Chitin Chestplate");
        addItem(builder, AlienArmorItems.CHITIN_HELMET, "Chitin Helmet");
        addItem(builder, AlienArmorItems.CHITIN_LEGGINGS, "Chitin Leggings");
        addItem(builder, AlienArmorItems.IRRADIATED_CHITIN_BOOTS, "Irradiated Chitin Boots");
        addItem(builder, AlienArmorItems.IRRADIATED_CHITIN_CHESTPLATE, "Irradiated Chitin Chestplate");
        addItem(builder, AlienArmorItems.IRRADIATED_CHITIN_HELMET, "Irradiated Chitin Helmet");
        addItem(builder, AlienArmorItems.IRRADIATED_CHITIN_LEGGINGS, "Irradiated Chitin Leggings");
        addItem(builder, AlienArmorItems.NETHER_CHITIN_BOOTS, "Nether Chitin Boots");
        addItem(builder, AlienArmorItems.NETHER_CHITIN_CHESTPLATE, "Nether Chitin Chestplate");
        addItem(builder, AlienArmorItems.NETHER_CHITIN_HELMET, "Nether Chitin Helmet");
        addItem(builder, AlienArmorItems.NETHER_CHITIN_LEGGINGS, "Nether Chitin Leggings");
        addItem(builder, AlienArmorItems.PLATED_ABERRANT_CHITIN_BOOTS, "Plated Aberrant Chitin Boots");
        addItem(builder, AlienArmorItems.PLATED_ABERRANT_CHITIN_CHESTPLATE, "Plated Aberrant Chitin Chestplate");
        addItem(builder, AlienArmorItems.PLATED_ABERRANT_CHITIN_HELMET, "Plated Aberrant Chitin Helmet");
        addItem(builder, AlienArmorItems.PLATED_ABERRANT_CHITIN_LEGGINGS, "Plated Aberrant Chitin Leggings");
        addItem(builder, AlienArmorItems.PLATED_CHITIN_BOOTS, "Plated Chitin Boots");
        addItem(builder, AlienArmorItems.PLATED_CHITIN_CHESTPLATE, "Plated Chitin Chestplate");
        addItem(builder, AlienArmorItems.PLATED_CHITIN_HELMET, "Plated Chitin Helmet");
        addItem(builder, AlienArmorItems.PLATED_CHITIN_LEGGINGS, "Plated Chitin Leggings");
        addItem(builder, AlienArmorItems.PLATED_IRRADIATED_CHITIN_BOOTS, "Plated Irradiated Chitin Boots");
        addItem(builder, AlienArmorItems.PLATED_IRRADIATED_CHITIN_CHESTPLATE, "Plated Irradiated Chitin Chestplate");
        addItem(builder, AlienArmorItems.PLATED_IRRADIATED_CHITIN_HELMET, "Plated Irradiated Chitin Helmet");
        addItem(builder, AlienArmorItems.PLATED_IRRADIATED_CHITIN_LEGGINGS, "Plated Irradiated Chitin Leggings");
        addItem(builder, AlienArmorItems.PLATED_NETHER_CHITIN_BOOTS, "Plated Nether Chitin Boots");
        addItem(builder, AlienArmorItems.PLATED_NETHER_CHITIN_CHESTPLATE, "Plated Nether Chitin Chestplate");
        addItem(builder, AlienArmorItems.PLATED_NETHER_CHITIN_HELMET, "Plated Nether Chitin Helmet");
        addItem(builder, AlienArmorItems.PLATED_NETHER_CHITIN_LEGGINGS, "Plated Nether Chitin Leggings");

        // UNSORTED
        addItem(builder, AlienItems.ALIEN_MUSIC_DISC_1, "Music Disc");
        addItem(builder, AlienItems.CAPTURE_CHAIN, "Capture Chain");
        addItem(builder, AlienItems.INHIBITOR, "Inhibitor");
        addItem(builder, AlienItems.TRACKER, "Tracker");
        addItem(builder, AlienItems.TRACKING_PDA, "Tracking PDA");
        addItem(builder, AlienItems.ALIEN_MUSIC_DISC_1_FRAGMENT, "Disc Fragment");
        builder.add(AlienItems.ALIEN_MUSIC_DISC_1_FRAGMENT.get().getDescriptionId() + ".desc", "Music Disc - Silver Smile");
        addItem(builder, AlienItems.CHITIN, "Chitin");
        addItem(builder, AlienItems.NETHER_CHITIN, "Nether Chitin");
        addItem(builder, AlienItems.NETHER_RESIN_BALL, "Nether Resin Ball");
        addItem(builder, AlienItems.OVOID_POTTERY_SHERD, "Ovoid Pottery Sherd");
        addItem(builder, AlienItems.PARASITE_POTTERY_SHERD, "Parasite Pottery Sherd");
        addItem(builder, AlienItems.ROYALTY_POTTERY_SHERD, "Royalty Pottery Sherd");
        addItem(builder, AlienItems.PLATED_CHITIN, "Plated Chitin");
        addItem(builder, AlienItems.PLATED_NETHER_CHITIN, "Plated Nether Chitin");
        addItem(builder, AlienItems.RAW_ROYAL_JELLY, "Raw Royal Jelly");
        addItem(builder, AlienItems.RAW_SCOURGE_JELLY, "Raw Scourge Jelly");
        addItem(builder, AlienItems.POISON_JELLY, "Poison Jelly");
        addItem(builder, AlienItems.RESIN_BALL, "Resin Ball");
        addItem(builder, AlienItems.VECTOR_POTTERY_SHERD, "Vector Pottery Sherd");
        addItem(builder, AlienItems.ABERRANT_RESIN_BALL, "Aberrant Resin Ball");
        addItem(builder, AlienItems.ABERRANT_CHITIN, "Aberrant Chitin");
        addItem(builder, AlienItems.PLATED_ABERRANT_CHITIN, "Plated Aberrant Chitin");
        addItem(builder, AlienItems.IRRADIATED_RESIN_BALL, "Irradiated Resin Ball");
        addItem(builder, AlienItems.IRRADIATED_CHITIN, "Irradiated Chitin");
        addItem(builder, AlienItems.PLATED_IRRADIATED_CHITIN, "Plated Irradiated Chitin");
        AlienXenomorphHeadItems.ALL.forEach(entry -> {
            addItem(builder, entry.head(), entry.displayName() + " Head");
            addItem(builder, entry.headShield(), entry.displayName() + " Head Shield");
        });

        // Spawn Egg Items
        addItem(builder, AlienSpawnEggItems.ABERRANT_ADOLESCENT_SPAWN_EGG, "Aberrant Adolescent Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ABERRANT_BOILER_SPAWN_EGG, "Aberrant Boiler Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ABERRANT_CARRIER_SPAWN_EGG, "Aberrant Carrier Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ABERRANT_CHESTBURSTER_SPAWN_EGG, "Aberrant Chestburster Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ABERRANT_CHRYSALIS_SPAWN_EGG, "Aberrant Chrysalis Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ABERRANT_CRUSHER_SPAWN_EGG, "Aberrant Crusher Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ABERRANT_DRONE_SPAWN_EGG, "Aberrant Drone Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ABERRANT_FACEHUGGER_SPAWN_EGG, "Aberrant Facehugger Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ABERRANT_HARBINGER_SPAWN_EGG, "Aberrant Harbinger Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ABERRANT_OVOMORPH_SPAWN_EGG, "Aberrant Ovomorph Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ABERRANT_PRAETORIAN_SPAWN_EGG, "Aberrant Praetorian Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ABERRANT_PREDALIEN_ADOLESCENT_SPAWN_EGG, "Aberrant Predalien Adolescent Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ABERRANT_PREDALIEN_CHESTBURSTER_SPAWN_EGG, "Aberrant Predalien Chestburster Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ABERRANT_PREDALIEN_SPAWN_EGG, "Aberrant Predalien Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ABERRANT_PROWLER_SPAWN_EGG, "Aberrant Prowler Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ABERRANT_WARRIOR_SPAWN_EGG, "Aberrant Warrior Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ABERRANT_RAZOR_CLAW_SPAWN_EGG, "Aberrant Razor Claw Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ABERRANT_RAVAGER_SPAWN_EGG, "Aberrant Ravager Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ABERRANT_QUEEN_SPAWN_EGG, "Aberrant Queen Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ABERRANT_EMPRESS_SPAWN_EGG, "Aberrant Empress Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ABERRANT_BURSTER_SPAWN_EGG, "Aberrant Burster Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ABERRANT_RUNNER_SPAWN_EGG, "Aberrant Runner Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ABERRANT_SPITTER_SPAWN_EGG, "Aberrant Spitter Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ADOLESCENT_SPAWN_EGG, "Adolescent Spawn Egg");
        addItem(builder, AlienSpawnEggItems.BOILER_SPAWN_EGG, "Boiler Spawn Egg");
        addItem(builder, AlienSpawnEggItems.CARRIER_SPAWN_EGG, "Carrier Spawn Egg");
        addItem(builder, AlienSpawnEggItems.CHESTBURSTER_SPAWN_EGG, "Chestburster Spawn Egg");
        addItem(builder, AlienSpawnEggItems.CHRYSALIS_SPAWN_EGG, "Chrysalis Spawn Egg");
        addItem(builder, AlienSpawnEggItems.CRUSHER_SPAWN_EGG, "Crusher Spawn Egg");
        addItem(builder, AlienSpawnEggItems.DRONE_SPAWN_EGG, "Drone Spawn Egg");
        addItem(builder, AlienSpawnEggItems.EMPRESS_SPAWN_EGG, "Empress Spawn Egg");
        addItem(builder, AlienSpawnEggItems.HARBINGER_SPAWN_EGG, "Harbinger Spawn Egg");
        addItem(builder, AlienSpawnEggItems.FACEHUGGER_SPAWN_EGG, "Facehugger Spawn Egg");
        addItem(builder, AlienSpawnEggItems.NETHER_ADOLESCENT_SPAWN_EGG, "Nether Adolescent Spawn Egg");
        addItem(builder, AlienSpawnEggItems.NETHER_BOILER_SPAWN_EGG, "Nether Boiler Spawn Egg");
        addItem(builder, AlienSpawnEggItems.NETHER_CHESTBURSTER_SPAWN_EGG, "Nether Chestburster Spawn Egg");
        addItem(builder, AlienSpawnEggItems.NETHER_CARRIER_SPAWN_EGG, "Nether Carrier Spawn Egg");
        addItem(builder, AlienSpawnEggItems.NETHER_CHRYSALIS_SPAWN_EGG, "Nether Chrysalis Spawn Egg");
        addItem(builder, AlienSpawnEggItems.NETHER_CRUSHER_SPAWN_EGG, "Nether Crusher Spawn Egg");
        addItem(builder, AlienSpawnEggItems.NETHER_DRONE_SPAWN_EGG, "Nether Drone Spawn Egg");
        addItem(builder, AlienSpawnEggItems.NETHER_FACEHUGGER_SPAWN_EGG, "Nether Facehugger Spawn Egg");
        addItem(builder, AlienSpawnEggItems.NETHER_HARBINGER_SPAWN_EGG, "Nether Harbinger Spawn Egg");
        addItem(builder, AlienSpawnEggItems.NETHER_OVOMORPH_SPAWN_EGG, "Nether Ovomorph Spawn Egg");
        addItem(builder, AlienSpawnEggItems.NETHER_PRAETORIAN_SPAWN_EGG, "Nether Praetorian Spawn Egg");
        addItem(builder, AlienSpawnEggItems.NETHER_PREDALIEN_ADOLESCENT_SPAWN_EGG, "Nether Predalien Adolescent Spawn Egg");
        addItem(builder, AlienSpawnEggItems.NETHER_PREDALIEN_CHESTBURSTER_SPAWN_EGG, "Nether Predalien Chestburster Spawn Egg");
        addItem(builder, AlienSpawnEggItems.NETHER_PREDALIEN_SPAWN_EGG, "Nether Predalien Spawn Egg");
        addItem(builder, AlienSpawnEggItems.NETHER_PROWLER_SPAWN_EGG, "Nether Prowler Spawn Egg");
        addItem(builder, AlienSpawnEggItems.NETHER_BURSTER_SPAWN_EGG, "Nether Burster Spawn Egg");
        addItem(builder, AlienSpawnEggItems.NETHER_RUNNER_SPAWN_EGG, "Nether Runner Spawn Egg");
        addItem(builder, AlienSpawnEggItems.NETHER_SPITTER_SPAWN_EGG, "Nether Spitter Spawn Egg");
        addItem(builder, AlienSpawnEggItems.NETHER_WARRIOR_SPAWN_EGG, "Nether Warrior Spawn Egg");
        addItem(builder, AlienSpawnEggItems.NETHER_RAZOR_CLAW_SPAWN_EGG, "Nether Razor Claw Spawn Egg");
        addItem(builder, AlienSpawnEggItems.NETHER_RAVAGER_SPAWN_EGG, "Nether Ravager Spawn Egg");
        addItem(builder, AlienSpawnEggItems.NETHER_QUEEN_SPAWN_EGG, "Nether Queen Spawn Egg");
        addItem(builder, AlienSpawnEggItems.NETHER_EMPRESS_SPAWN_EGG, "Nether Empress Spawn Egg");
        addItem(builder, AlienSpawnEggItems.IRRADIATED_CARRIER_SPAWN_EGG, "Irradiated Carrier Spawn Egg");
        addItem(builder, AlienSpawnEggItems.IRRADIATED_CHRYSALIS_SPAWN_EGG, "Irradiated Chrysalis Spawn Egg");
        addItem(builder, AlienSpawnEggItems.IRRADIATED_CRUSHER_SPAWN_EGG, "Irradiated Crusher Spawn Egg");
        addItem(builder, AlienSpawnEggItems.IRRADIATED_DRONE_SPAWN_EGG, "Irradiated Drone Spawn Egg");
        addItem(builder, AlienSpawnEggItems.IRRADIATED_PRAETORIAN_SPAWN_EGG, "Irradiated Praetorian Spawn Egg");
        addItem(builder, AlienSpawnEggItems.IRRADIATED_PREDALIEN_SPAWN_EGG, "Irradiated Predalien Spawn Egg");
        addItem(builder, AlienSpawnEggItems.IRRADIATED_PROWLER_SPAWN_EGG, "Irradiated Prowler Spawn Egg");
        addItem(builder, AlienSpawnEggItems.IRRADIATED_HARBINGER_SPAWN_EGG, "Irradiated Harbinger Spawn Egg");
        addItem(builder, AlienSpawnEggItems.IRRADIATED_RAZOR_CLAW_SPAWN_EGG, "Irradiated Razor Claw Spawn Egg");
        addItem(builder, AlienSpawnEggItems.IRRADIATED_RAVAGER_SPAWN_EGG, "Irradiated Ravager Spawn Egg");
        addItem(builder, AlienSpawnEggItems.IRRADIATED_QUEEN_SPAWN_EGG, "Irradiated Queen Spawn Egg");
        addItem(builder, AlienSpawnEggItems.IRRADIATED_EMPRESS_SPAWN_EGG, "Irradiated Empress Spawn Egg");
        addItem(builder, AlienSpawnEggItems.IRRADIATED_BURSTER_SPAWN_EGG, "Irradiated Burster Spawn Egg");
        addItem(builder, AlienSpawnEggItems.IRRADIATED_RUNNER_SPAWN_EGG, "Irradiated Runner Spawn Egg");
        addItem(builder, AlienSpawnEggItems.IRRADIATED_WARRIOR_SPAWN_EGG, "Irradiated Warrior Spawn Egg");
        addItem(builder, AlienSpawnEggItems.OVOMORPH_SPAWN_EGG, "Ovomorph Spawn Egg");
        addItem(builder, AlienSpawnEggItems.PRAETORIAN_SPAWN_EGG, "Praetorian Spawn Egg");
        addItem(builder, AlienSpawnEggItems.PREDALIEN_ADOLESCENT_SPAWN_EGG, "Predalien Adolescent Spawn Egg");
        addItem(builder, AlienSpawnEggItems.PREDALIEN_CHESTBURSTER_SPAWN_EGG, "Predalien Chestburster Spawn Egg");
        addItem(builder, AlienSpawnEggItems.PREDALIEN_SPAWN_EGG, "Predalien Spawn Egg");
        addItem(builder, AlienSpawnEggItems.PROWLER_SPAWN_EGG, "Prowler Spawn Egg");
        addItem(builder, AlienSpawnEggItems.RAZOR_CLAW_SPAWN_EGG, "Razor Claw Spawn Egg");
        addItem(builder, AlienSpawnEggItems.RAVAGER_SPAWN_EGG, "Ravager Spawn Egg");
        addItem(builder, AlienSpawnEggItems.QUEEN_SPAWN_EGG, "Queen Spawn Egg");
        addItem(builder, AlienSpawnEggItems.BURSTER_SPAWN_EGG, "Burster Spawn Egg");
        addItem(builder, AlienSpawnEggItems.RUNNER_SPAWN_EGG, "Runner Spawn Egg");
        addItem(builder, AlienSpawnEggItems.SPITTER_SPAWN_EGG, "Spitter Spawn Egg");
        addItem(builder, AlienSpawnEggItems.WARRIOR_SPAWN_EGG, "Warrior Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ROYAL_ABERRANT_ADOLESCENT_SPAWN_EGG, "Royal Aberrant Adolescent Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ROYAL_ADOLESCENT_SPAWN_EGG, "Royal Adolescent Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ROYAL_NETHER_ADOLESCENT_SPAWN_EGG, "Royal Nether Adolescent Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ROYAL_OVOMORPH_SPAWN_EGG, "Royal Ovomorph Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ROYAL_FACEHUGGER_SPAWN_EGG, "Royal Facehugger Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ROYAL_CHESTBURSTER_SPAWN_EGG, "Royal Chestburster Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ROYAL_NETHER_OVOMORPH_SPAWN_EGG, "Royal Nether Ovomorph Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ROYAL_NETHER_FACEHUGGER_SPAWN_EGG, "Royal Nether Facehugger Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ROYAL_NETHER_CHESTBURSTER_SPAWN_EGG, "Royal Nether Chestburster Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ROYAL_ABERRANT_OVOMORPH_SPAWN_EGG, "Royal Aberrant Ovomorph Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ROYAL_ABERRANT_FACEHUGGER_SPAWN_EGG, "Royal Aberrant Facehugger Spawn Egg");
        addItem(builder, AlienSpawnEggItems.ROYAL_ABERRANT_CHESTBURSTER_SPAWN_EGG, "Royal Aberrant Chestburster Spawn Egg");
    };

    private static void addItem(
        FabricLanguageProvider.TranslationBuilder translationBuilder,
        Supplier<? extends Item> itemSupplier,
        String value
    ) {
        addItem(translationBuilder, itemSupplier.get(), value);
    }

    private static void addItem(FabricLanguageProvider.TranslationBuilder translationBuilder, Item item, String value) {
        TOUCHED_ENTRIES.add(item);
        translationBuilder.add(item, value);
    }

}
