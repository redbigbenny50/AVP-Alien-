package com.alien.fabric.data.hive_unit_purchase;

import com.alien.common.data.HiveUnitPurchaseReloadListener;
import com.alien.common.gameplay.hive.economy.HiveUnitPurchase;
import com.alien.common.gameplay.hive.economy.HiveUnitPurchaseCondition;
import com.alien.common.registry.init.AlienEntityTypes;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class HiveUnitPurchaseDataProvider implements DataProvider {

    private static final double POPULATION_BIOMASS_COST_SCALE = HiveUnitPurchase.DEFAULT_POPULATION_BIOMASS_COST_SCALE;

    private final FabricDataOutput output;

    private final Map<ResourceLocation, HiveUnitPurchase> purchasesById = new LinkedHashMap<>();

    public HiveUnitPurchaseDataProvider(FabricDataOutput output) {
        this.output = output;
    }

    private void generate() {
        addVariantPurchases(
            new VariantEntities(
                AlienEntityTypes.DRONE.get(),
                AlienEntityTypes.RUNNER.get(),
                AlienEntityTypes.WARRIOR.get(),
                AlienEntityTypes.PRAETORIAN.get(),
                AlienEntityTypes.PROWLER.get(),
                AlienEntityTypes.CRUSHER.get(),
                AlienEntityTypes.RAVAGER.get(),
                AlienEntityTypes.RAZOR_CLAW.get(),
                AlienEntityTypes.BURSTER.get(),
                AlienEntityTypes.CARRIER.get(),
                AlienEntityTypes.CHRYSALIS.get(),
                AlienEntityTypes.HARBINGER.get(),
                AlienEntityTypes.OVOMORPH.get(),
                AlienEntityTypes.SPITTER.get()
            )
        );
        addVariantPurchases(
            new VariantEntities(
                AlienEntityTypes.ABERRANT_DRONE.get(),
                AlienEntityTypes.ABERRANT_RUNNER.get(),
                AlienEntityTypes.ABERRANT_WARRIOR.get(),
                AlienEntityTypes.ABERRANT_PRAETORIAN.get(),
                AlienEntityTypes.ABERRANT_PROWLER.get(),
                AlienEntityTypes.ABERRANT_CRUSHER.get(),
                AlienEntityTypes.ABERRANT_RAVAGER.get(),
                AlienEntityTypes.ABERRANT_RAZOR_CLAW.get(),
                AlienEntityTypes.ABERRANT_BURSTER.get(),
                AlienEntityTypes.ABERRANT_CARRIER.get(),
                AlienEntityTypes.ABERRANT_CHRYSALIS.get(),
                AlienEntityTypes.ABERRANT_HARBINGER.get(),
                AlienEntityTypes.ABERRANT_OVOMORPH.get(),
                AlienEntityTypes.ABERRANT_SPITTER.get()
            )
        );
        addVariantPurchases(
            new VariantEntities(
                AlienEntityTypes.NETHER_DRONE.get(),
                AlienEntityTypes.NETHER_RUNNER.get(),
                AlienEntityTypes.NETHER_WARRIOR.get(),
                AlienEntityTypes.NETHER_PRAETORIAN.get(),
                AlienEntityTypes.NETHER_PROWLER.get(),
                AlienEntityTypes.NETHER_CRUSHER.get(),
                AlienEntityTypes.NETHER_RAVAGER.get(),
                AlienEntityTypes.NETHER_RAZOR_CLAW.get(),
                AlienEntityTypes.NETHER_BURSTER.get(),
                AlienEntityTypes.NETHER_CARRIER.get(),
                AlienEntityTypes.NETHER_CHRYSALIS.get(),
                AlienEntityTypes.NETHER_HARBINGER.get(),
                AlienEntityTypes.NETHER_OVOMORPH.get(),
                AlienEntityTypes.NETHER_SPITTER.get()
            )
        );
    }

    private void addVariantPurchases(VariantEntities entities) {
        // Basic egg-born castes: each consumes one of its variant's ovomorphs (reserve first; stored nursery
        // eggs cover shortfalls). The spitter is the rarer third basic - production substitutes it 1-in-4.
        add(
            new HiveUnitPurchase(entities.drone(), 50, POPULATION_BIOMASS_COST_SCALE, 0, 0, List.of(input(entities.ovomorph())), List.of())
        );
        add(
            new HiveUnitPurchase(entities.runner(), 40, POPULATION_BIOMASS_COST_SCALE, 0, 0, List.of(input(entities.ovomorph())), List.of())
        );
        add(
            new HiveUnitPurchase(
                entities.spitter(),
                50,
                POPULATION_BIOMASS_COST_SCALE,
                0,
                0,
                List.of(input(entities.ovomorph())),
                List.of()
            )
        );
        add(
            new HiveUnitPurchase(
                entities.warrior(),
                0,
                POPULATION_BIOMASS_COST_SCALE,
                1,
                0,
                List.of(input(entities.drone())),
                List.of()
            )
        );
        add(
            new HiveUnitPurchase(
                entities.praetorian(),
                0,
                POPULATION_BIOMASS_COST_SCALE,
                1,
                0,
                List.of(input(entities.warrior())),
                List.of()
            )
        );
        add(
            new HiveUnitPurchase(
                entities.prowler(),
                0,
                POPULATION_BIOMASS_COST_SCALE,
                1,
                0,
                List.of(input(entities.runner())),
                List.of()
            )
        );
        add(
            new HiveUnitPurchase(
                entities.crusher(),
                0,
                POPULATION_BIOMASS_COST_SCALE,
                1,
                0,
                List.of(input(entities.prowler())),
                List.of()
            )
        );
        add(
            new HiveUnitPurchase(
                entities.ravager(),
                0,
                POPULATION_BIOMASS_COST_SCALE,
                0,
                1,
                List.of(input(entities.warrior())),
                List.of(harbingerRequired(entities))
            )
        );
        add(
            new HiveUnitPurchase(
                entities.razorClaw(),
                0,
                POPULATION_BIOMASS_COST_SCALE,
                0,
                2,
                List.of(input(entities.runner())),
                List.of(harbingerRequired(entities))
            )
        );
        add(
            new HiveUnitPurchase(
                entities.burster(),
                0,
                POPULATION_BIOMASS_COST_SCALE,
                0,
                1,
                List.of(input(entities.runner())),
                List.of(harbingerRequired(entities))
            )
        );
        add(
            new HiveUnitPurchase(
                entities.carrier(),
                0,
                POPULATION_BIOMASS_COST_SCALE,
                0,
                1,
                List.of(input(entities.drone())),
                List.of(harbingerRequired(entities))
            )
        );
        add(
            new HiveUnitPurchase(
                entities.chrysalis(),
                0,
                POPULATION_BIOMASS_COST_SCALE,
                0,
                1,
                List.of(input(entities.prowler())),
                List.of(harbingerRequired(entities))
            )
        );
        add(
            new HiveUnitPurchase(
                entities.harbinger(),
                200,
                POPULATION_BIOMASS_COST_SCALE,
                0,
                1,
                List.of(input(entities.praetorian())),
                List.of(
                    new HiveUnitPurchaseCondition.MinPopulation(100),
                    new HiveUnitPurchaseCondition.MaxEntityCountInLocation(entities.harbinger(), 1)
                )
            )
        );
    }

    private static HiveUnitPurchase.InputEntity input(EntityType<?> entityType) {
        return new HiveUnitPurchase.InputEntity(entityType, 1);
    }

    private static HiveUnitPurchaseCondition.MinEntityCountInLocation harbingerRequired(VariantEntities entities) {
        return new HiveUnitPurchaseCondition.MinEntityCountInLocation(entities.harbinger(), 1);
    }

    private void add(HiveUnitPurchase purchase) {
        purchasesById.put(BuiltInRegistries.ENTITY_TYPE.getKey(purchase.outputEntity()), purchase);
    }

    @Override
    public final @NotNull CompletableFuture<?> run(CachedOutput cached) {
        generate();

        var pathProvider = output.createPathProvider(
            PackOutput.Target.DATA_PACK,
            HiveUnitPurchaseReloadListener.DIRECTORY_NAME
        );
        var futures = new ArrayList<CompletableFuture<?>>();

        for (var entry : purchasesById.entrySet()) {
            var filePath = pathProvider.json(entry.getKey());
            var jsonElement = HiveUnitPurchase.CODEC.encodeStart(JsonOps.INSTANCE, entry.getValue()).getOrThrow();
            futures.add(DataProvider.saveStable(cached, jsonElement, filePath));
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    @Override
    public final @NotNull String getName() {
        return "Hive Unit Purchases";
    }

    private record VariantEntities(
        EntityType<?> drone,
        EntityType<?> runner,
        EntityType<?> warrior,
        EntityType<?> praetorian,
        EntityType<?> prowler,
        EntityType<?> crusher,
        EntityType<?> ravager,
        EntityType<?> razorClaw,
        EntityType<?> burster,
        EntityType<?> carrier,
        EntityType<?> chrysalis,
        EntityType<?> harbinger,
        EntityType<?> ovomorph,
        EntityType<?> spitter
    ) {}
}
