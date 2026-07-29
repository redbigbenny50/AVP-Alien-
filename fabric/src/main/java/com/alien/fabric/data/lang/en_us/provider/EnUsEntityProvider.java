package com.alien.fabric.data.lang.en_us.provider;

import com.alien.common.registry.init.AlienEntityTypes;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.world.entity.EntityType;

import java.util.HashSet;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class EnUsEntityProvider {

    private static final HashSet<EntityType<?>> TOUCHED_ENTRIES = new HashSet<>();

    public static final Consumer<FabricLanguageProvider.TranslationBuilder> CONSUMER = builder -> {
        addEntity(builder, AlienEntityTypes.ABERRANT_ADOLESCENT, "Aberrant Adolescent");
        addEntity(builder, AlienEntityTypes.ABERRANT_BOILER, "Aberrant Boiler");
        addEntity(builder, AlienEntityTypes.ABERRANT_CARRIER, "Aberrant Carrier");
        addEntity(builder, AlienEntityTypes.ABERRANT_CHESTBURSTER, "Aberrant Chestburster");
        addEntity(builder, AlienEntityTypes.ABERRANT_CHRYSALIS, "Aberrant Chrysalis");
        addEntity(builder, AlienEntityTypes.ABERRANT_CRUSHER, "Aberrant Crusher");
        addEntity(builder, AlienEntityTypes.ABERRANT_DRONE, "Aberrant Drone");
        addEntity(builder, AlienEntityTypes.ABERRANT_FACEHUGGER, "Aberrant Facehugger");
        addEntity(builder, AlienEntityTypes.ABERRANT_HARBINGER, "Aberrant Harbinger");
        addEntity(builder, AlienEntityTypes.ABERRANT_OVOMORPH, "Aberrant Ovomorph");
        addEntity(builder, AlienEntityTypes.ABERRANT_PRAETORIAN, "Aberrant Praetorian");
        addEntity(builder, AlienEntityTypes.ABERRANT_PREDALIEN, "Aberrant Predalien");
        addEntity(builder, AlienEntityTypes.ABERRANT_PREDALIEN_ADOLESCENT, "Aberrant Predalien Adolescent");
        addEntity(builder, AlienEntityTypes.ABERRANT_PREDALIEN_CHESTBURSTER, "Aberrant Predalien Chestburster");
        addEntity(builder, AlienEntityTypes.ABERRANT_PROWLER, "Aberrant Prowler");
        addEntity(builder, AlienEntityTypes.ABERRANT_RAZOR_CLAW, "Aberrant Razor Claw");
        addEntity(builder, AlienEntityTypes.ABERRANT_RAVAGER, "Aberrant Ravager");
        addEntity(builder, AlienEntityTypes.ABERRANT_QUEEN, "Aberrant Queen");
        addEntity(builder, AlienEntityTypes.ABERRANT_EMPRESS, "Aberrant Empress");
        addEntity(builder, AlienEntityTypes.ABERRANT_BURSTER, "Aberrant Burster");
        addEntity(builder, AlienEntityTypes.ABERRANT_RUNNER, "Aberrant Runner");
        addEntity(builder, AlienEntityTypes.ABERRANT_SPITTER, "Aberrant Spitter");
        addEntity(builder, AlienEntityTypes.ABERRANT_WARRIOR, "Aberrant Warrior");
        addEntity(builder, AlienEntityTypes.ACID, "Acid");
        addEntity(builder, AlienEntityTypes.ADOLESCENT, "Adolescent");
        addEntity(builder, AlienEntityTypes.BOILER, "Boiler");
        addEntity(builder, AlienEntityTypes.CARRIER, "Carrier");
        addEntity(builder, AlienEntityTypes.CHESTBURSTER, "Chestburster");
        addEntity(builder, AlienEntityTypes.CHRYSALIS, "Chrysalis");
        addEntity(builder, AlienEntityTypes.CRUSHER, "Crusher");
        addEntity(builder, AlienEntityTypes.DRONE, "Drone");
        addEntity(builder, AlienEntityTypes.EMPRESS, "Empress");
        addEntity(builder, AlienEntityTypes.HARBINGER, "Harbinger");
        addEntity(builder, AlienEntityTypes.FACEHUGGER, "Facehugger");
        addEntity(builder, AlienEntityTypes.IRRADIATED_CARRIER, "Irradiated Carrier");
        addEntity(builder, AlienEntityTypes.IRRADIATED_CHRYSALIS, "Irradiated Chrysalis");
        addEntity(builder, AlienEntityTypes.IRRADIATED_CRUSHER, "Irradiated Crusher");
        addEntity(builder, AlienEntityTypes.IRRADIATED_OVOMORPH, "Irradiated Ovomorph");
        addEntity(builder, AlienEntityTypes.IRRADIATED_FACEHUGGER, "Irradiated Facehugger");
        addEntity(builder, AlienEntityTypes.IRRADIATED_DRONE, "Irradiated Drone");
        addEntity(builder, AlienEntityTypes.IRRADIATED_PRAETORIAN, "Irradiated Praetorian");
        addEntity(builder, AlienEntityTypes.IRRADIATED_PREDALIEN, "Irradiated Predalien");
        addEntity(builder, AlienEntityTypes.IRRADIATED_PROWLER, "Irradiated Prowler");
        addEntity(builder, AlienEntityTypes.IRRADIATED_HARBINGER, "Irradiated Harbinger");
        addEntity(builder, AlienEntityTypes.IRRADIATED_RAZOR_CLAW, "Irradiated Razor Claw");
        addEntity(builder, AlienEntityTypes.IRRADIATED_RAVAGER, "Irradiated Ravager");
        addEntity(builder, AlienEntityTypes.IRRADIATED_QUEEN, "Irradiated Queen");
        addEntity(builder, AlienEntityTypes.IRRADIATED_EMPRESS, "Irradiated Empress");
        addEntity(builder, AlienEntityTypes.IRRADIATED_BURSTER, "Irradiated Burster");
        addEntity(builder, AlienEntityTypes.IRRADIATED_RUNNER, "Irradiated Runner");
        addEntity(builder, AlienEntityTypes.IRRADIATED_WARRIOR, "Irradiated Warrior");
        addEntity(builder, AlienEntityTypes.NETHER_ADOLESCENT, "Nether Adolescent");
        addEntity(builder, AlienEntityTypes.NETHER_BOILER, "Nether Boiler");
        addEntity(builder, AlienEntityTypes.NETHER_CARRIER, "Nether Carrier");
        addEntity(builder, AlienEntityTypes.NETHER_CHESTBURSTER, "Nether Chestburster");
        addEntity(builder, AlienEntityTypes.NETHER_CHRYSALIS, "Nether Chrysalis");
        addEntity(builder, AlienEntityTypes.NETHER_CRUSHER, "Nether Crusher");
        addEntity(builder, AlienEntityTypes.NETHER_DRONE, "Nether Drone");
        addEntity(builder, AlienEntityTypes.NETHER_FACEHUGGER, "Nether Facehugger");
        addEntity(builder, AlienEntityTypes.NETHER_HARBINGER, "Nether Harbinger");
        addEntity(builder, AlienEntityTypes.NETHER_OVOMORPH, "Nether Ovomorph");
        addEntity(builder, AlienEntityTypes.NETHER_PRAETORIAN, "Nether Praetorian");
        addEntity(builder, AlienEntityTypes.NETHER_PREDALIEN, "Nether Predalien");
        addEntity(builder, AlienEntityTypes.NETHER_PREDALIEN_ADOLESCENT, "Nether Predalien Adolescent");
        addEntity(builder, AlienEntityTypes.NETHER_PREDALIEN_CHESTBURSTER, "Nether Predalien Chestburster");
        addEntity(builder, AlienEntityTypes.NETHER_PROWLER, "Nether Prowler");
        addEntity(builder, AlienEntityTypes.NETHER_RAZOR_CLAW, "Nether Razor Claw");
        addEntity(builder, AlienEntityTypes.NETHER_RAVAGER, "Nether Ravager");
        addEntity(builder, AlienEntityTypes.NETHER_QUEEN, "Nether Queen");
        addEntity(builder, AlienEntityTypes.NETHER_EMPRESS, "Nether Empress");
        addEntity(builder, AlienEntityTypes.NETHER_BURSTER, "Nether Burster");
        addEntity(builder, AlienEntityTypes.NETHER_RUNNER, "Nether Runner");
        addEntity(builder, AlienEntityTypes.NETHER_SPITTER, "Nether Spitter");
        addEntity(builder, AlienEntityTypes.IRRADIATED_SPITTER, "Irradiated Spitter");
        addEntity(builder, AlienEntityTypes.NETHER_WARRIOR, "Nether Warrior");
        addEntity(builder, AlienEntityTypes.OVOMORPH, "Ovomorph");
        addEntity(builder, AlienEntityTypes.OVIPOSITOR, "Ovipositor");
        addEntity(builder, AlienEntityTypes.ROYAL_COCOON, "Royal Cocoon");
        addEntity(builder, AlienEntityTypes.ABERRANT_ROYAL_COCOON, "Aberrant Royal Cocoon");
        addEntity(builder, AlienEntityTypes.NETHER_ROYAL_COCOON, "Nether Royal Cocoon");
        addEntity(builder, AlienEntityTypes.PRAETORIAN, "Praetorian");
        addEntity(builder, AlienEntityTypes.PREDALIEN, "Predalien");
        addEntity(builder, AlienEntityTypes.PREDALIEN_ADOLESCENT, "Predalien Adolescent");
        addEntity(builder, AlienEntityTypes.PREDALIEN_CHESTBURSTER, "Predalien Chestburster");
        addEntity(builder, AlienEntityTypes.PROWLER, "Prowler");
        addEntity(builder, AlienEntityTypes.RAZOR_CLAW, "Razor Claw");
        addEntity(builder, AlienEntityTypes.RAVAGER, "Ravager");
        addEntity(builder, AlienEntityTypes.QUEEN, "Queen");
        addEntity(builder, AlienEntityTypes.ROYAL_ABERRANT_ADOLESCENT, "Royal Aberrant Adolescent");
        addEntity(builder, AlienEntityTypes.ROYAL_ABERRANT_CHESTBURSTER, "Royal Aberrant Chestburster");
        addEntity(builder, AlienEntityTypes.ROYAL_ABERRANT_FACEHUGGER, "Royal Aberrant Facehugger");
        addEntity(builder, AlienEntityTypes.ROYAL_ABERRANT_OVOMORPH, "Royal Aberrant Ovomorph");
        addEntity(builder, AlienEntityTypes.ROYAL_ADOLESCENT, "Royal Adolescent");
        addEntity(builder, AlienEntityTypes.ROYAL_CHESTBURSTER, "Royal Chestburster");
        addEntity(builder, AlienEntityTypes.ROYAL_FACEHUGGER, "Royal Facehugger");
        addEntity(builder, AlienEntityTypes.ROYAL_NETHER_ADOLESCENT, "Royal Nether Adolescent");
        addEntity(builder, AlienEntityTypes.ROYAL_NETHER_CHESTBURSTER, "Royal Nether Chestburster");
        addEntity(builder, AlienEntityTypes.ROYAL_NETHER_FACEHUGGER, "Royal Nether Facehugger");
        addEntity(builder, AlienEntityTypes.ROYAL_NETHER_OVOMORPH, "Royal Nether Ovomorph");
        addEntity(builder, AlienEntityTypes.ROYAL_OVOMORPH, "Royal Ovomorph");
        addEntity(builder, AlienEntityTypes.BURSTER, "Burster");
        addEntity(builder, AlienEntityTypes.RUNNER, "Runner");
        addEntity(builder, AlienEntityTypes.SPITTER, "Spitter");
        addEntity(builder, AlienEntityTypes.WARRIOR, "Warrior");
    };

    private static void addEntity(
        FabricLanguageProvider.TranslationBuilder translationBuilder,
        Supplier<? extends EntityType<?>> entityTypeSupplier,
        String value
    ) {
        addEntity(translationBuilder, entityTypeSupplier.get(), value);
    }

    private static void addEntity(FabricLanguageProvider.TranslationBuilder translationBuilder, EntityType<?> entityType, String value) {
        TOUCHED_ENTRIES.add(entityType);
        translationBuilder.add(entityType, value);
    }
}
