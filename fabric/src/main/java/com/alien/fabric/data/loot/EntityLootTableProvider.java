package com.alien.fabric.data.loot;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.data.loot.BursterLootTable;
import com.alien.common.data.loot.CarrierLootTable;
import com.alien.common.data.loot.ChrysalisLootTable;
import com.alien.common.data.loot.CrusherLootTable;
import com.alien.common.data.loot.DroneLootTable;
import com.alien.common.data.loot.EmpressLootTable;
import com.alien.common.data.loot.HarbingerLootTable;
import com.alien.common.data.loot.OvipositorLootTable;
import com.alien.common.data.loot.PraetorianLootTable;
import com.alien.common.data.loot.PredalienLootTable;
import com.alien.common.data.loot.QueenLootTable;
import com.alien.common.data.loot.RavagerLootTable;
import com.alien.common.data.loot.RazorClawLootTable;
import com.alien.common.data.loot.WarriorLootTable;
import com.alien.common.registry.init.AlienEntityTypes;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.SimpleFabricLootTableProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class EntityLootTableProvider extends SimpleFabricLootTableProvider {

    private final HolderLookup.Provider provider;

    public EntityLootTableProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(output, registryLookup, LootContextParamSets.ENTITY);
        this.provider = registryLookup.join();
    }

    @Override
    public void generate(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> biConsumer) {
        biConsumer.accept(AlienEntityTypes.OVIPOSITOR.get().getDefaultLootTable(), OvipositorLootTable.create(provider));

        // Normal
        biConsumer.accept(
            AlienEntityTypes.CARRIER.get().getDefaultLootTable(),
            CarrierLootTable.create(provider, AlienVariantTypes.NORMAL)
        );
        biConsumer.accept(
            AlienEntityTypes.CHRYSALIS.get().getDefaultLootTable(),
            ChrysalisLootTable.create(provider, AlienVariantTypes.NORMAL)
        );
        biConsumer.accept(
            AlienEntityTypes.HARBINGER.get().getDefaultLootTable(),
            HarbingerLootTable.create(provider, AlienVariantTypes.NORMAL)
        );
        biConsumer.accept(AlienEntityTypes.BOILER.get().getDefaultLootTable(), WarriorLootTable.create(provider, AlienVariantTypes.NORMAL));
        biConsumer.accept(AlienEntityTypes.DRONE.get().getDefaultLootTable(), DroneLootTable.create(provider, AlienVariantTypes.NORMAL));
        biConsumer.accept(
            AlienEntityTypes.WARRIOR.get().getDefaultLootTable(),
            WarriorLootTable.create(provider, AlienVariantTypes.NORMAL)
        );
        biConsumer.accept(
            AlienEntityTypes.PRAETORIAN.get().getDefaultLootTable(),
            PraetorianLootTable.create(provider, AlienVariantTypes.NORMAL)
        );
        biConsumer.accept(
            AlienEntityTypes.PREDALIEN.get().getDefaultLootTable(),
            PredalienLootTable.create(provider, AlienVariantTypes.NORMAL)
        );
        biConsumer.accept(
            AlienEntityTypes.PROWLER.get().getDefaultLootTable(),
            WarriorLootTable.create(provider, AlienVariantTypes.NORMAL)
        );
        biConsumer.accept(
            AlienEntityTypes.RAZOR_CLAW.get().getDefaultLootTable(),
            RazorClawLootTable.create(provider, AlienVariantTypes.NORMAL)
        );
        biConsumer.accept(
            AlienEntityTypes.RAVAGER.get().getDefaultLootTable(),
            RavagerLootTable.create(provider, AlienVariantTypes.NORMAL)
        );
        biConsumer.accept(
            AlienEntityTypes.CRUSHER.get().getDefaultLootTable(),
            CrusherLootTable.create(provider, AlienVariantTypes.NORMAL)
        );
        biConsumer.accept(
            AlienEntityTypes.BURSTER.get().getDefaultLootTable(),
            BursterLootTable.create(provider, AlienVariantTypes.NORMAL)
        );
        biConsumer.accept(
            AlienEntityTypes.QUEEN.get().getDefaultLootTable(),
            QueenLootTable.createLootTableBuilder(provider, AlienVariantTypes.NORMAL)
        );
        biConsumer.accept(
            AlienEntityTypes.EMPRESS.get().getDefaultLootTable(),
            EmpressLootTable.createLootTableBuilder(provider, AlienVariantTypes.NORMAL)
        );
        biConsumer.accept(AlienEntityTypes.RUNNER.get().getDefaultLootTable(), DroneLootTable.create(provider, AlienVariantTypes.NORMAL));
        biConsumer.accept(
            AlienEntityTypes.SPITTER.get().getDefaultLootTable(),
            WarriorLootTable.create(provider, AlienVariantTypes.NORMAL)
        );

        // Nether
        biConsumer.accept(
            AlienEntityTypes.NETHER_CARRIER.get().getDefaultLootTable(),
            CarrierLootTable.create(provider, AlienVariantTypes.NETHER)
        );
        biConsumer.accept(
            AlienEntityTypes.NETHER_CHRYSALIS.get().getDefaultLootTable(),
            ChrysalisLootTable.create(provider, AlienVariantTypes.NETHER)
        );
        biConsumer.accept(
            AlienEntityTypes.NETHER_HARBINGER.get().getDefaultLootTable(),
            HarbingerLootTable.create(provider, AlienVariantTypes.NETHER)
        );
        biConsumer.accept(
            AlienEntityTypes.NETHER_BOILER.get().getDefaultLootTable(),
            WarriorLootTable.create(provider, AlienVariantTypes.NETHER)
        );
        biConsumer.accept(
            AlienEntityTypes.NETHER_DRONE.get().getDefaultLootTable(),
            DroneLootTable.create(provider, AlienVariantTypes.NETHER)
        );
        biConsumer.accept(
            AlienEntityTypes.NETHER_WARRIOR.get().getDefaultLootTable(),
            WarriorLootTable.create(provider, AlienVariantTypes.NETHER)
        );
        biConsumer.accept(
            AlienEntityTypes.NETHER_PRAETORIAN.get().getDefaultLootTable(),
            PraetorianLootTable.create(provider, AlienVariantTypes.NETHER)
        );
        biConsumer.accept(
            AlienEntityTypes.NETHER_PREDALIEN.get().getDefaultLootTable(),
            PredalienLootTable.create(provider, AlienVariantTypes.NETHER)
        );
        biConsumer.accept(
            AlienEntityTypes.NETHER_PROWLER.get().getDefaultLootTable(),
            WarriorLootTable.create(provider, AlienVariantTypes.NETHER)
        );
        biConsumer.accept(
            AlienEntityTypes.NETHER_RAZOR_CLAW.get().getDefaultLootTable(),
            RazorClawLootTable.create(provider, AlienVariantTypes.NETHER)
        );
        biConsumer.accept(
            AlienEntityTypes.NETHER_RAVAGER.get().getDefaultLootTable(),
            RavagerLootTable.create(provider, AlienVariantTypes.NETHER)
        );
        biConsumer.accept(
            AlienEntityTypes.NETHER_CRUSHER.get().getDefaultLootTable(),
            CrusherLootTable.create(provider, AlienVariantTypes.NETHER)
        );
        biConsumer.accept(
            AlienEntityTypes.NETHER_BURSTER.get().getDefaultLootTable(),
            BursterLootTable.create(provider, AlienVariantTypes.NETHER)
        );
        biConsumer.accept(
            AlienEntityTypes.NETHER_QUEEN.get().getDefaultLootTable(),
            QueenLootTable.createLootTableBuilder(provider, AlienVariantTypes.NETHER)
        );
        biConsumer.accept(
            AlienEntityTypes.NETHER_EMPRESS.get().getDefaultLootTable(),
            EmpressLootTable.createLootTableBuilder(provider, AlienVariantTypes.NETHER)
        );
        biConsumer.accept(
            AlienEntityTypes.NETHER_RUNNER.get().getDefaultLootTable(),
            DroneLootTable.create(provider, AlienVariantTypes.NETHER)
        );
        biConsumer.accept(
            AlienEntityTypes.NETHER_SPITTER.get().getDefaultLootTable(),
            WarriorLootTable.create(provider, AlienVariantTypes.NETHER)
        );
        biConsumer.accept(
            AlienEntityTypes.IRRADIATED_SPITTER.get().getDefaultLootTable(),
            WarriorLootTable.create(provider, AlienVariantTypes.IRRADIATED)
        );

        // Aberrant
        biConsumer.accept(
            AlienEntityTypes.ABERRANT_CARRIER.get().getDefaultLootTable(),
            CarrierLootTable.create(provider, AlienVariantTypes.ABERRANT)
        );
        biConsumer.accept(
            AlienEntityTypes.ABERRANT_CHRYSALIS.get().getDefaultLootTable(),
            ChrysalisLootTable.create(provider, AlienVariantTypes.ABERRANT)
        );
        biConsumer.accept(
            AlienEntityTypes.ABERRANT_HARBINGER.get().getDefaultLootTable(),
            HarbingerLootTable.create(provider, AlienVariantTypes.ABERRANT)
        );
        biConsumer.accept(
            AlienEntityTypes.ABERRANT_BOILER.get().getDefaultLootTable(),
            WarriorLootTable.create(provider, AlienVariantTypes.ABERRANT)
        );
        biConsumer.accept(
            AlienEntityTypes.ABERRANT_DRONE.get().getDefaultLootTable(),
            DroneLootTable.create(provider, AlienVariantTypes.ABERRANT)
        );
        biConsumer.accept(
            AlienEntityTypes.ABERRANT_WARRIOR.get().getDefaultLootTable(),
            WarriorLootTable.create(provider, AlienVariantTypes.ABERRANT)
        );
        biConsumer.accept(
            AlienEntityTypes.ABERRANT_PRAETORIAN.get().getDefaultLootTable(),
            PraetorianLootTable.create(provider, AlienVariantTypes.ABERRANT)
        );
        biConsumer.accept(
            AlienEntityTypes.ABERRANT_PREDALIEN.get().getDefaultLootTable(),
            PredalienLootTable.create(provider, AlienVariantTypes.ABERRANT)
        );
        biConsumer.accept(
            AlienEntityTypes.ABERRANT_PROWLER.get().getDefaultLootTable(),
            WarriorLootTable.create(provider, AlienVariantTypes.ABERRANT)
        );
        biConsumer.accept(
            AlienEntityTypes.ABERRANT_RAZOR_CLAW.get().getDefaultLootTable(),
            RazorClawLootTable.create(provider, AlienVariantTypes.ABERRANT)
        );
        biConsumer.accept(
            AlienEntityTypes.ABERRANT_RAVAGER.get().getDefaultLootTable(),
            RavagerLootTable.create(provider, AlienVariantTypes.ABERRANT)
        );
        biConsumer.accept(
            AlienEntityTypes.ABERRANT_CRUSHER.get().getDefaultLootTable(),
            CrusherLootTable.create(provider, AlienVariantTypes.ABERRANT)
        );
        biConsumer.accept(
            AlienEntityTypes.ABERRANT_BURSTER.get().getDefaultLootTable(),
            BursterLootTable.create(provider, AlienVariantTypes.ABERRANT)
        );
        biConsumer.accept(
            AlienEntityTypes.ABERRANT_QUEEN.get().getDefaultLootTable(),
            QueenLootTable.createLootTableBuilder(provider, AlienVariantTypes.ABERRANT)
        );
        biConsumer.accept(
            AlienEntityTypes.ABERRANT_EMPRESS.get().getDefaultLootTable(),
            EmpressLootTable.createLootTableBuilder(provider, AlienVariantTypes.ABERRANT)
        );
        biConsumer.accept(
            AlienEntityTypes.ABERRANT_RUNNER.get().getDefaultLootTable(),
            DroneLootTable.create(provider, AlienVariantTypes.ABERRANT)
        );
        biConsumer.accept(
            AlienEntityTypes.ABERRANT_SPITTER.get().getDefaultLootTable(),
            WarriorLootTable.create(provider, AlienVariantTypes.ABERRANT)
        );

        // Irradiated
        biConsumer.accept(
            AlienEntityTypes.IRRADIATED_CARRIER.get().getDefaultLootTable(),
            CarrierLootTable.create(provider, AlienVariantTypes.IRRADIATED)
        );
        biConsumer.accept(
            AlienEntityTypes.IRRADIATED_CHRYSALIS.get().getDefaultLootTable(),
            ChrysalisLootTable.create(provider, AlienVariantTypes.IRRADIATED)
        );
        biConsumer.accept(
            AlienEntityTypes.IRRADIATED_HARBINGER.get().getDefaultLootTable(),
            HarbingerLootTable.create(provider, AlienVariantTypes.IRRADIATED)
        );
        biConsumer.accept(
            AlienEntityTypes.IRRADIATED_DRONE.get().getDefaultLootTable(),
            DroneLootTable.create(provider, AlienVariantTypes.IRRADIATED)
        );
        biConsumer.accept(
            AlienEntityTypes.IRRADIATED_WARRIOR.get().getDefaultLootTable(),
            WarriorLootTable.create(provider, AlienVariantTypes.IRRADIATED)
        );
        biConsumer.accept(
            AlienEntityTypes.IRRADIATED_PRAETORIAN.get().getDefaultLootTable(),
            PraetorianLootTable.create(provider, AlienVariantTypes.IRRADIATED)
        );
        biConsumer.accept(
            AlienEntityTypes.IRRADIATED_PREDALIEN.get().getDefaultLootTable(),
            PredalienLootTable.create(provider, AlienVariantTypes.IRRADIATED)
        );
        biConsumer.accept(
            AlienEntityTypes.IRRADIATED_PROWLER.get().getDefaultLootTable(),
            WarriorLootTable.create(provider, AlienVariantTypes.IRRADIATED)
        );
        biConsumer.accept(
            AlienEntityTypes.IRRADIATED_RAZOR_CLAW.get().getDefaultLootTable(),
            RazorClawLootTable.create(provider, AlienVariantTypes.IRRADIATED)
        );
        biConsumer.accept(
            AlienEntityTypes.IRRADIATED_RAVAGER.get().getDefaultLootTable(),
            RavagerLootTable.create(provider, AlienVariantTypes.IRRADIATED)
        );
        biConsumer.accept(
            AlienEntityTypes.IRRADIATED_CRUSHER.get().getDefaultLootTable(),
            CrusherLootTable.create(provider, AlienVariantTypes.IRRADIATED)
        );
        biConsumer.accept(
            AlienEntityTypes.IRRADIATED_BURSTER.get().getDefaultLootTable(),
            BursterLootTable.create(provider, AlienVariantTypes.IRRADIATED)
        );
        biConsumer.accept(
            AlienEntityTypes.IRRADIATED_QUEEN.get().getDefaultLootTable(),
            QueenLootTable.createLootTableBuilder(provider, AlienVariantTypes.IRRADIATED)
        );
        biConsumer.accept(
            AlienEntityTypes.IRRADIATED_EMPRESS.get().getDefaultLootTable(),
            EmpressLootTable.createLootTableBuilder(provider, AlienVariantTypes.IRRADIATED)
        );
        biConsumer.accept(
            AlienEntityTypes.IRRADIATED_RUNNER.get().getDefaultLootTable(),
            DroneLootTable.create(provider, AlienVariantTypes.IRRADIATED)
        );
    }
}
