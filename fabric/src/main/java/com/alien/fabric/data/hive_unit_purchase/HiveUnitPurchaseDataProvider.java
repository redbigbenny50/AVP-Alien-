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

    // ---- Worker costs. Deliberately cheap: every soldier is a promoted worker, and each promotion frees the worker
    // ---- slot to be refilled, so worker cost is paid on EVERY military unit the hive ever fields.
    private static final int DRONE_BIOMASS = 10;

    private static final int RUNNER_BIOMASS = 10;

    private static final int SPITTER_BIOMASS = 15;

    /** Same price as a spitter: a predalien is egg-born the same way, not a promotion. */
    private static final int PREDALIEN_BIOMASS = 15;

    // ---- Pool caps. All of these sit OUTSIDE the hive member cap.
    private static final int SPITTER_CAP = 60;

    /**
     * Predaliens are only ever produced while avp_predator is loaded - HiveBalanceTask holds that gate, since the
     * purchase file itself is static data that ships either way.
     */
    private static final int PREDALIEN_CAP = 20;

    private static final int WARRIOR_CAP = 50;

    private static final int PROWLER_CAP = 70;

    private static final int PRAETORIAN_CAP = 20;

    private static final int CRUSHER_CAP = 20;

    private static final int BURSTER_CAP = 40;

    private static final int CHRYSALIS_CAP = 20;

    private static final int RAZOR_CLAW_CAP = 20;

    private static final int RAVAGER_CAP = 10;

    private static final int CARRIER_CAP = 10;

    private static final int HARBINGER_BIOMASS = 200;

    /** A hive must reach this size before it starts promoting workers into soldiers. */
    private static final int MILITARY_MIN_POPULATION = 50;

    private static final int HARBINGER_MIN_POPULATION = 100;

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
                AlienEntityTypes.SPITTER.get(),
                AlienEntityTypes.PREDALIEN.get()
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
                AlienEntityTypes.ABERRANT_SPITTER.get(),
                AlienEntityTypes.ABERRANT_PREDALIEN.get()
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
                AlienEntityTypes.NETHER_SPITTER.get(),
                AlienEntityTypes.NETHER_PREDALIEN.get()
            )
        );
    }

    private void addVariantPurchases(VariantEntities entities) {
        // ------------------------------------------------------------------------------------------------------
        // POOLS. Workers count against the hive's member cap; everything below has its OWN cap and sits outside it,
        // so building an army never squeezes out the drones that feed it.
        //
        // workers (drone/runner) .... member cap spitters .... 60
        // warriors .... 50 prowlers ..... 70
        // praetorians . 20 crushers ..... 20 (the queen's guard - they do not raid)
        // bursters .... 40 chrysalises .. 20 razor claws .. 20
        // ravagers .... 10 carriers ..... 10 harbinger .... 1
        //
        // Every strain (base / aberrant / nether) runs these same rules - this method is called once per family.
        // ------------------------------------------------------------------------------------------------------

        // Basic egg-born castes. Cheap and fast: they are the feedstock the whole army is promoted OUT of, and every
        // promotion consumes one, so an expensive worker makes the entire military tier expensive.
        add(
            new HiveUnitPurchase(
                entities.drone(),
                DRONE_BIOMASS,
                POPULATION_BIOMASS_COST_SCALE,
                0,
                0,
                List.of(input(entities.ovomorph())),
                List.of()
            )
        );
        add(
            new HiveUnitPurchase(
                entities.runner(),
                RUNNER_BIOMASS,
                POPULATION_BIOMASS_COST_SCALE,
                0,
                0,
                List.of(input(entities.ovomorph())),
                List.of()
            )
        );
        add(
            new HiveUnitPurchase(
                entities.spitter(),
                SPITTER_BIOMASS,
                POPULATION_BIOMASS_COST_SCALE,
                0,
                0,
                List.of(input(entities.ovomorph())),
                List.of(max(entities.spitter(), SPITTER_CAP))
            )
        );

        // Predaliens are treated as another egg-born caste rather than the canonical predator-host birth, so a hive
        // can field them without a predator ever wandering past. HiveBalanceTask only ever asks for one while
        // avp_predator is loaded, so on a predator-free install this purchase simply never fires.
        add(
            new HiveUnitPurchase(
                entities.predalien(),
                PREDALIEN_BIOMASS,
                POPULATION_BIOMASS_COST_SCALE,
                0,
                0,
                List.of(input(entities.ovomorph())),
                List.of(max(entities.predalien(), PREDALIEN_CAP))
            )
        );

        // ---- The standing army: royal jelly promotions. A hive must actually BE a hive first
        // (MILITARY_MIN_POPULATION)
        // ---- before it starts turning its workers into soldiers.
        add(
            new HiveUnitPurchase(
                entities.warrior(),
                0,
                POPULATION_BIOMASS_COST_SCALE,
                1,
                0,
                List.of(input(entities.drone())),
                List.of(new HiveUnitPurchaseCondition.MinPopulation(MILITARY_MIN_POPULATION), max(entities.warrior(), WARRIOR_CAP))
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
                List.of(new HiveUnitPurchaseCondition.MinPopulation(MILITARY_MIN_POPULATION), max(entities.prowler(), PROWLER_CAP))
            )
        );

        // ---- Elites: the queen's guard and the hive's defenders. They do NOT raid.
        // Gated on the army being half-built (50% of each cap). Because a praetorian EATS a warrior, promoting one
        // drops warriors back below the gate - so elites self-throttle: the hive must rebuild its line before it can
        // promote another. That is deliberate; it keeps them rare.
        add(
            new HiveUnitPurchase(
                entities.praetorian(),
                0,
                POPULATION_BIOMASS_COST_SCALE,
                1,
                0,
                List.of(input(entities.warrior())),
                List.of(min(entities.warrior(), WARRIOR_CAP / 2), max(entities.praetorian(), PRAETORIAN_CAP))
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
                List.of(min(entities.prowler(), PROWLER_CAP / 2), max(entities.crusher(), CRUSHER_CAP))
            )
        );

        // ---- The scourge tier: raid-only castes, paid for in SCOURGE jelly and gated on a living harbinger.
        // Kill the harbinger and this entire production line stops.
        add(scourge(entities.burster(), entities.runner(), 1, BURSTER_CAP, entities.harbinger()));
        add(scourge(entities.chrysalis(), entities.prowler(), 1, CHRYSALIS_CAP, entities.harbinger()));
        add(scourge(entities.razorClaw(), entities.runner(), 2, RAZOR_CLAW_CAP, entities.harbinger()));
        add(scourge(entities.ravager(), entities.warrior(), 1, RAVAGER_CAP, entities.harbinger()));
        add(scourge(entities.carrier(), entities.drone(), 1, CARRIER_CAP, entities.harbinger()));

        // ---- The harbinger: the raid key. A promoted praetorian, and the hive's scourge-jelly factory.
        add(
            new HiveUnitPurchase(
                entities.harbinger(),
                HARBINGER_BIOMASS,
                POPULATION_BIOMASS_COST_SCALE,
                0,
                1,
                List.of(input(entities.praetorian())),
                List.of(
                    new HiveUnitPurchaseCondition.MinPopulation(HARBINGER_MIN_POPULATION),
                    // ONE PER RAID CHAMBER. An ordinary hive builds a single raid chamber, so it fields a single
                    // harbinger - kill it and the hive cannot raid until it grows another. Only an EMPRESS-backed
                    // hive can build a second chamber, and so keep raiding after you have decapitated one.
                    new HiveUnitPurchaseCondition.MaxPerRaidChamber(entities.harbinger())
                )
            )
        );
    }

    /** A scourge caste: consumes an army/worker body, paid in scourge jelly, and needs a living harbinger. */
    private HiveUnitPurchase scourge(
        EntityType<?> output,
        EntityType<?> consumed,
        int scourgeJelly,
        int cap,
        EntityType<?> harbinger
    ) {
        return new HiveUnitPurchase(
            output,
            0,
            POPULATION_BIOMASS_COST_SCALE,
            0,
            scourgeJelly,
            List.of(input(consumed)),
            List.of(min(harbinger, 1), max(output, cap))
        );
    }

    private static HiveUnitPurchaseCondition min(EntityType<?> entity, int value) {
        return new HiveUnitPurchaseCondition.MinEntityCountInLocation(entity, value);
    }

    private static HiveUnitPurchaseCondition max(EntityType<?> entity, int value) {
        return new HiveUnitPurchaseCondition.MaxEntityCountInLocation(entity, value);
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
        EntityType<?> spitter,
        EntityType<?> predalien
    ) {}
}
