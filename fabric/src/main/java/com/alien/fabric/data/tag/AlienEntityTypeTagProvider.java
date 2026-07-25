package com.alien.fabric.data.tag;

import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.alien.compatibility.avp_human.AVPHuman;
import com.alien.fabric.compatibility.stellaris.common.registry.tag.StellarisEntityTypeTags;
import com.human.common.registry.tag.HumanEntityTypeTags;
import mods.cybercat.gigeresque.common.tags.GigTags;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.EntityType;

import java.util.concurrent.CompletableFuture;

public class AlienEntityTypeTagProvider extends FabricTagProvider.EntityTypeTagProvider {

    public AlienEntityTypeTagProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> completableFuture) {
        super(output, completableFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider wrapperLookup) {
        addAberrantAliens();
        addAcidImmune();
        addCaptureChainBlacklist();
        addPredalienAdolescents();
        addAdolescents();
        addAliens();
        addAnswersXenomorphCriesForHelp();
        addPredalienChestbursters();
        addCarriers();
        addChestbursters();
        addChrysalises();
        addCrushers();
        addDrones();
        addEmpresses();
        addFacehuggers();
        addHarbingers();
        addHatedByXenomorphs();
        addHiveAliens();
        addHiveLayerSpawns();
        addScourgeAliens();
        addRunnerHosts();
        addHosts();
        addIgnoredByXenomorphs();
        addXenomorphThreatTiers();
        addIrradiatedAliens();
        addNetherAliens();
        addNormalAliens();
        addOvomorphs();
        addParasites();
        addPraetorians();
        addPredaliens();
        addProwlers();
        addQueens();
        addRavagers();
        addRazorClaws();
        addRoyalAliens();
        addRoyalXenomorphs();
        addRunners();
        addBursters();
        addSpitters();
        addWarriors();
        addXenomorphs();

        // Compatibility
        addCompatibilityTags();

        if (AVPHuman.MOD.isLoaded()) {
            addRadiationResistant();
        }
    }

    private void addAberrantAliens() {
        getOrCreateTagBuilder(AlienEntityTypeTags.ABERRANT_ALIENS)
            .add(
                AlienEntityTypes.ABERRANT_ADOLESCENT.get(),
                AlienEntityTypes.ABERRANT_BOILER.get(),
                AlienEntityTypes.ABERRANT_CARRIER.get(),
                AlienEntityTypes.ABERRANT_CHESTBURSTER.get(),
                AlienEntityTypes.ABERRANT_CHRYSALIS.get(),
                AlienEntityTypes.ABERRANT_CRUSHER.get(),
                AlienEntityTypes.ABERRANT_DRONE.get(),
                AlienEntityTypes.ABERRANT_FACEHUGGER.get(),
                AlienEntityTypes.ABERRANT_HARBINGER.get(),
                AlienEntityTypes.ABERRANT_OVOMORPH.get(),
                AlienEntityTypes.ABERRANT_PRAETORIAN.get(),
                AlienEntityTypes.ABERRANT_PREDALIEN.get(),
                AlienEntityTypes.ABERRANT_PREDALIEN_ADOLESCENT.get(),
                AlienEntityTypes.ABERRANT_PREDALIEN_CHESTBURSTER.get(),
                AlienEntityTypes.ABERRANT_PROWLER.get(),
                AlienEntityTypes.ABERRANT_RAZOR_CLAW.get(),
                AlienEntityTypes.ABERRANT_RAVAGER.get(),
                AlienEntityTypes.ABERRANT_QUEEN.get(),
                AlienEntityTypes.ABERRANT_EMPRESS.get(),
                AlienEntityTypes.ABERRANT_BURSTER.get(),
                AlienEntityTypes.ABERRANT_RUNNER.get(),
                AlienEntityTypes.ABERRANT_SPITTER.get(),
                AlienEntityTypes.ABERRANT_WARRIOR.get(),
                AlienEntityTypes.ROYAL_ABERRANT_ADOLESCENT.get(),
                AlienEntityTypes.ROYAL_ABERRANT_CHESTBURSTER.get(),
                AlienEntityTypes.ROYAL_ABERRANT_FACEHUGGER.get(),
                AlienEntityTypes.ROYAL_ABERRANT_OVOMORPH.get()
            );
    }

    private void addAcidImmune() {
        getOrCreateTagBuilder(AlienEntityTypeTags.ACID_IMMUNE)
            .addTag(AlienEntityTypeTags.ALIENS);
    }

    private void addAdolescents() {
        getOrCreateTagBuilder(AlienEntityTypeTags.ADOLESCENTS)
            .addTag(AlienEntityTypeTags.PREDALIEN_ADOLESCENTS)
            .add(
                AlienEntityTypes.ABERRANT_ADOLESCENT.get(),
                AlienEntityTypes.ADOLESCENT.get(),
                AlienEntityTypes.NETHER_ADOLESCENT.get(),
                AlienEntityTypes.ROYAL_ABERRANT_ADOLESCENT.get(),
                AlienEntityTypes.ROYAL_ADOLESCENT.get(),
                AlienEntityTypes.ROYAL_NETHER_ADOLESCENT.get()
            );
    }

    private void addAliens() {
        getOrCreateTagBuilder(AlienEntityTypeTags.ALIENS)
            .addTag(AlienEntityTypeTags.IRRADIATED_ALIENS)
            .addTag(AlienEntityTypeTags.ABERRANT_ALIENS)
            .addTag(AlienEntityTypeTags.NORMAL_ALIENS)
            .addTag(AlienEntityTypeTags.NETHER_ALIENS)
            .addTag(AlienEntityTypeTags.ROYAL_ALIENS);
    }

    private void addAnswersXenomorphCriesForHelp() {
        getOrCreateTagBuilder(AlienEntityTypeTags.ANSWERS_XENOMORPH_CRIES_FOR_HELP)
            .addTag(AlienEntityTypeTags.BURSTERS)
            .addTag(AlienEntityTypeTags.DRONES)
            .addTag(AlienEntityTypeTags.PROWLERS)
            .addTag(AlienEntityTypeTags.RUNNERS)
            .addTag(AlienEntityTypeTags.SPITTERS)
            .addTag(AlienEntityTypeTags.WARRIORS);
    }

    private void addChestbursters() {
        getOrCreateTagBuilder(AlienEntityTypeTags.CHESTBURSTERS)
            .addTag(AlienEntityTypeTags.PREDALIEN_CHESTBURSTERS)
            .add(
                AlienEntityTypes.ABERRANT_CHESTBURSTER.get(),
                AlienEntityTypes.CHESTBURSTER.get(),
                AlienEntityTypes.NETHER_CHESTBURSTER.get(),
                AlienEntityTypes.ROYAL_ABERRANT_CHESTBURSTER.get(),
                AlienEntityTypes.ROYAL_CHESTBURSTER.get(),
                AlienEntityTypes.ROYAL_NETHER_CHESTBURSTER.get()
            );
    }

    private void addCarriers() {
        getOrCreateTagBuilder(AlienEntityTypeTags.CARRIERS)
            .add(
                AlienEntityTypes.ABERRANT_CARRIER.get(),
                AlienEntityTypes.CARRIER.get(),
                AlienEntityTypes.IRRADIATED_CARRIER.get(),
                AlienEntityTypes.NETHER_CARRIER.get()
            );
    }

    private void addChrysalises() {
        getOrCreateTagBuilder(AlienEntityTypeTags.CHRYSALISES)
            .add(
                AlienEntityTypes.ABERRANT_CHRYSALIS.get(),
                AlienEntityTypes.CHRYSALIS.get(),
                AlienEntityTypes.IRRADIATED_CHRYSALIS.get(),
                AlienEntityTypes.NETHER_CHRYSALIS.get()
            );
    }

    private void addCrushers() {
        getOrCreateTagBuilder(AlienEntityTypeTags.CRUSHERS)
            .add(
                AlienEntityTypes.ABERRANT_CRUSHER.get(),
                AlienEntityTypes.CRUSHER.get(),
                AlienEntityTypes.IRRADIATED_CRUSHER.get(),
                AlienEntityTypes.NETHER_CRUSHER.get()
            );
    }

    private void addDrones() {
        getOrCreateTagBuilder(AlienEntityTypeTags.DRONES)
            .add(
                AlienEntityTypes.ABERRANT_DRONE.get(),
                AlienEntityTypes.DRONE.get(),
                AlienEntityTypes.IRRADIATED_DRONE.get(),
                AlienEntityTypes.NETHER_DRONE.get()
            );
    }

    private void addFacehuggers() {
        getOrCreateTagBuilder(AlienEntityTypeTags.FACEHUGGERS)
            .add(
                AlienEntityTypes.ABERRANT_FACEHUGGER.get(),
                AlienEntityTypes.FACEHUGGER.get(),
                AlienEntityTypes.NETHER_FACEHUGGER.get(),
                AlienEntityTypes.ROYAL_ABERRANT_FACEHUGGER.get(),
                AlienEntityTypes.ROYAL_FACEHUGGER.get(),
                AlienEntityTypes.ROYAL_NETHER_FACEHUGGER.get()
            );
    }

    private void addHarbingers() {
        getOrCreateTagBuilder(AlienEntityTypeTags.HARBINGERS)
            .add(
                AlienEntityTypes.ABERRANT_HARBINGER.get(),
                AlienEntityTypes.HARBINGER.get(),
                AlienEntityTypes.IRRADIATED_HARBINGER.get(),
                AlienEntityTypes.NETHER_HARBINGER.get()
            );
    }

    private void addCaptureChainBlacklist() {
        getOrCreateTagBuilder(AlienEntityTypeTags.CAPTURE_CHAIN_BLACKLIST)
            .add(EntityType.ENDER_DRAGON)
            .add(EntityType.WARDEN)
            .add(EntityType.WITHER);
    }

    private void addHatedByXenomorphs() {
        getOrCreateTagBuilder(AlienEntityTypeTags.HATED_BY_XENOMORPHS)
            .add(EntityType.PLAYER);
    }

    private void addHiveAliens() {
        getOrCreateTagBuilder(AlienEntityTypeTags.HIVE_ALIENS)
            .addTag(AlienEntityTypeTags.XENOMORPHS);
    }

    private void addHiveLayerSpawns() {
        getOrCreateTagBuilder(AlienEntityTypeTags.SPAWNS_IN_HIVE_WARRIOR_LAYER)
            .addTag(AlienEntityTypeTags.PROWLERS)
            .addTag(AlienEntityTypeTags.SPITTERS)
            .addTag(AlienEntityTypeTags.WARRIORS);

        getOrCreateTagBuilder(AlienEntityTypeTags.SPAWNS_IN_HIVE_DRONE_LAYER)
            .addTag(AlienEntityTypeTags.SPAWNS_IN_HIVE_WARRIOR_LAYER)
            .addTag(AlienEntityTypeTags.ADOLESCENTS)
            .addTag(AlienEntityTypeTags.CHESTBURSTERS)
            .addTag(AlienEntityTypeTags.BURSTERS)
            .addTag(AlienEntityTypeTags.DRONES)
            .addTag(AlienEntityTypeTags.RUNNERS)
            .addTag(AlienEntityTypeTags.OVOMORPHS);

        getOrCreateTagBuilder(AlienEntityTypeTags.SPAWNS_IN_HIVE_PRAETORIAN_LAYER)
            .addTag(AlienEntityTypeTags.SPAWNS_IN_HIVE_DRONE_LAYER)
            .addTag(AlienEntityTypeTags.CRUSHERS)
            .addTag(AlienEntityTypeTags.PRAETORIANS)
            .addTag(AlienEntityTypeTags.PREDALIENS);

        getOrCreateTagBuilder(AlienEntityTypeTags.SPAWNS_IN_HIVE_QUEEN_LAYER)
            .addTag(AlienEntityTypeTags.SPAWNS_IN_HIVE_PRAETORIAN_LAYER)
            .addTag(AlienEntityTypeTags.QUEENS);
    }

    private void addHosts() {
        getOrCreateTagBuilder(AlienEntityTypeTags.HOSTS)
            .addTag(AlienEntityTypeTags.RUNNER_HOSTS)
            .addOptionalTag(EntityTypeTags.ILLAGER)
            .add(
                EntityType.LLAMA,
                EntityType.PIGLIN,
                EntityType.PIGLIN_BRUTE,
                EntityType.PLAYER,
                EntityType.TRADER_LLAMA,
                EntityType.VILLAGER,
                EntityType.WANDERING_TRADER,
                EntityType.WITCH
            );
    }

    private void addIgnoredByXenomorphs() {
        getOrCreateTagBuilder(AlienEntityTypeTags.IGNORED_BY_XENOMORPHS)
            .add(
                EntityType.ALLAY,
                EntityType.AXOLOTL,
                EntityType.BAT,
                EntityType.BEE,
                EntityType.COD,
                EntityType.CREEPER,
                EntityType.GLOW_SQUID,
                EntityType.PUFFERFISH,
                EntityType.SALMON,
                EntityType.SQUID,
                EntityType.TADPOLE,
                EntityType.TROPICAL_FISH,
                EntityType.VEX
            );
    }

    private void addXenomorphThreatTiers() {
        getOrCreateTagBuilder(AlienEntityTypeTags.XENOMORPH_THREAT_1_PASSIVE)
            .addTag(AlienEntityTypeTags.HOSTS)
            .add(
                EntityType.CAMEL,
                EntityType.CAT,
                EntityType.CHICKEN,
                EntityType.COW,
                EntityType.DONKEY,
                EntityType.FOX,
                EntityType.GOAT,
                EntityType.HORSE,
                EntityType.MOOSHROOM,
                EntityType.MULE,
                EntityType.OCELOT,
                EntityType.PANDA,
                EntityType.PARROT,
                EntityType.PIG,
                EntityType.POLAR_BEAR,
                EntityType.RABBIT,
                EntityType.SHEEP,
                EntityType.SNIFFER,
                EntityType.STRIDER,
                EntityType.TURTLE
            );

        getOrCreateTagBuilder(AlienEntityTypeTags.XENOMORPH_THREAT_2_LOW_DANGER)
            .add(
                EntityType.BLAZE,
                EntityType.BOGGED,
                EntityType.BREEZE,
                EntityType.CAVE_SPIDER,
                EntityType.DROWNED,
                EntityType.ELDER_GUARDIAN,
                EntityType.ENDERMAN,
                EntityType.ENDERMITE,
                EntityType.EVOKER,
                EntityType.GHAST,
                EntityType.GIANT,
                EntityType.GUARDIAN,
                EntityType.HOGLIN,
                EntityType.HUSK,
                EntityType.ILLUSIONER,
                EntityType.IRON_GOLEM,
                EntityType.MAGMA_CUBE,
                EntityType.PHANTOM,
                EntityType.PIGLIN,
                EntityType.PIGLIN_BRUTE,
                EntityType.PILLAGER,
                EntityType.RAVAGER,
                EntityType.SHULKER,
                EntityType.SILVERFISH,
                EntityType.SKELETON,
                EntityType.SLIME,
                EntityType.SPIDER,
                EntityType.STRAY,
                EntityType.VINDICATOR,
                EntityType.WARDEN,
                EntityType.WITCH,
                EntityType.WITHER_SKELETON,
                EntityType.WOLF,
                EntityType.ZOGLIN,
                EntityType.ZOMBIE,
                EntityType.ZOMBIE_VILLAGER,
                EntityType.ZOMBIFIED_PIGLIN
            );

        getOrCreateTagBuilder(AlienEntityTypeTags.XENOMORPH_THREAT_3_HIGH_DANGER)
            .add(EntityType.PLAYER)
            .addOptional(ResourceLocation.fromNamespaceAndPath("avp_human", "marine"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("avp_predator", "predator"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("avp_predator", "yautja"))
            .addOptionalTag(ResourceLocation.fromNamespaceAndPath("avp_predator", "predators"));
    }

    private void addIrradiatedAliens() {
        getOrCreateTagBuilder(AlienEntityTypeTags.IRRADIATED_ALIENS)
            .add(
                AlienEntityTypes.IRRADIATED_CARRIER.get(),
                AlienEntityTypes.IRRADIATED_CHRYSALIS.get(),
                AlienEntityTypes.IRRADIATED_CRUSHER.get(),
                AlienEntityTypes.IRRADIATED_DRONE.get(),
                AlienEntityTypes.IRRADIATED_PRAETORIAN.get(),
                AlienEntityTypes.IRRADIATED_PREDALIEN.get(),
                AlienEntityTypes.IRRADIATED_PROWLER.get(),
                AlienEntityTypes.IRRADIATED_HARBINGER.get(),
                AlienEntityTypes.IRRADIATED_RAZOR_CLAW.get(),
                AlienEntityTypes.IRRADIATED_RAVAGER.get(),
                AlienEntityTypes.IRRADIATED_QUEEN.get(),
                AlienEntityTypes.IRRADIATED_EMPRESS.get(),
                AlienEntityTypes.IRRADIATED_BURSTER.get(),
                AlienEntityTypes.IRRADIATED_RUNNER.get(),
                AlienEntityTypes.IRRADIATED_WARRIOR.get()
            );
    }

    private void addNetherAliens() {
        getOrCreateTagBuilder(AlienEntityTypeTags.NETHER_ALIENS)
            .add(
                AlienEntityTypes.NETHER_ADOLESCENT.get(),
                AlienEntityTypes.NETHER_BOILER.get(),
                AlienEntityTypes.NETHER_CARRIER.get(),
                AlienEntityTypes.NETHER_CHESTBURSTER.get(),
                AlienEntityTypes.NETHER_CHRYSALIS.get(),
                AlienEntityTypes.NETHER_CRUSHER.get(),
                AlienEntityTypes.NETHER_DRONE.get(),
                AlienEntityTypes.NETHER_FACEHUGGER.get(),
                AlienEntityTypes.NETHER_HARBINGER.get(),
                AlienEntityTypes.NETHER_OVOMORPH.get(),
                AlienEntityTypes.NETHER_PRAETORIAN.get(),
                AlienEntityTypes.NETHER_PREDALIEN.get(),
                AlienEntityTypes.NETHER_PREDALIEN_ADOLESCENT.get(),
                AlienEntityTypes.NETHER_PREDALIEN_CHESTBURSTER.get(),
                AlienEntityTypes.NETHER_PROWLER.get(),
                AlienEntityTypes.NETHER_RAZOR_CLAW.get(),
                AlienEntityTypes.NETHER_RAVAGER.get(),
                AlienEntityTypes.NETHER_QUEEN.get(),
                AlienEntityTypes.NETHER_EMPRESS.get(),
                AlienEntityTypes.NETHER_BURSTER.get(),
                AlienEntityTypes.NETHER_RUNNER.get(),
                AlienEntityTypes.NETHER_SPITTER.get(),
                AlienEntityTypes.NETHER_WARRIOR.get(),
                AlienEntityTypes.ROYAL_NETHER_ADOLESCENT.get(),
                AlienEntityTypes.ROYAL_NETHER_CHESTBURSTER.get(),
                AlienEntityTypes.ROYAL_NETHER_OVOMORPH.get(),
                AlienEntityTypes.ROYAL_NETHER_FACEHUGGER.get()
            );
    }

    private void addNormalAliens() {
        getOrCreateTagBuilder(AlienEntityTypeTags.NORMAL_ALIENS)
            .add(
                AlienEntityTypes.ADOLESCENT.get(),
                AlienEntityTypes.BOILER.get(),
                AlienEntityTypes.CARRIER.get(),
                AlienEntityTypes.CHESTBURSTER.get(),
                AlienEntityTypes.CHRYSALIS.get(),
                AlienEntityTypes.CRUSHER.get(),
                AlienEntityTypes.DRONE.get(),
                AlienEntityTypes.FACEHUGGER.get(),
                AlienEntityTypes.HARBINGER.get(),
                AlienEntityTypes.OVOMORPH.get(),
                AlienEntityTypes.PRAETORIAN.get(),
                AlienEntityTypes.PREDALIEN.get(),
                AlienEntityTypes.PREDALIEN_ADOLESCENT.get(),
                AlienEntityTypes.PREDALIEN_CHESTBURSTER.get(),
                AlienEntityTypes.PROWLER.get(),
                AlienEntityTypes.RAZOR_CLAW.get(),
                AlienEntityTypes.RAVAGER.get(),
                AlienEntityTypes.QUEEN.get(),
                AlienEntityTypes.EMPRESS.get(),
                AlienEntityTypes.ROYAL_ADOLESCENT.get(),
                AlienEntityTypes.ROYAL_CHESTBURSTER.get(),
                AlienEntityTypes.ROYAL_FACEHUGGER.get(),
                AlienEntityTypes.ROYAL_OVOMORPH.get(),
                AlienEntityTypes.BURSTER.get(),
                AlienEntityTypes.RUNNER.get(),
                AlienEntityTypes.SPITTER.get(),
                AlienEntityTypes.WARRIOR.get()
            );
    }

    private void addOvomorphs() {
        getOrCreateTagBuilder(AlienEntityTypeTags.OVOMORPHS)
            .add(
                AlienEntityTypes.ABERRANT_OVOMORPH.get(),
                AlienEntityTypes.NETHER_OVOMORPH.get(),
                AlienEntityTypes.OVOMORPH.get(),
                AlienEntityTypes.ROYAL_ABERRANT_OVOMORPH.get(),
                AlienEntityTypes.ROYAL_NETHER_OVOMORPH.get(),
                AlienEntityTypes.ROYAL_OVOMORPH.get()
            );
    }

    private void addParasites() {
        getOrCreateTagBuilder(AlienEntityTypeTags.PARASITES)
            .addTag(AlienEntityTypeTags.FACEHUGGERS);
    }

    private void addPraetorians() {
        getOrCreateTagBuilder(AlienEntityTypeTags.PRAETORIANS)
            .add(
                AlienEntityTypes.ABERRANT_PRAETORIAN.get(),
                AlienEntityTypes.IRRADIATED_PRAETORIAN.get(),
                AlienEntityTypes.NETHER_PRAETORIAN.get(),
                AlienEntityTypes.PRAETORIAN.get()
            );
    }

    private void addPredalienAdolescents() {
        getOrCreateTagBuilder(AlienEntityTypeTags.PREDALIEN_ADOLESCENTS)
            .add(
                AlienEntityTypes.ABERRANT_PREDALIEN_ADOLESCENT.get(),
                AlienEntityTypes.NETHER_PREDALIEN_ADOLESCENT.get(),
                AlienEntityTypes.PREDALIEN_ADOLESCENT.get()
            );
    }

    private void addPredalienChestbursters() {
        getOrCreateTagBuilder(AlienEntityTypeTags.PREDALIEN_CHESTBURSTERS)
            .add(
                AlienEntityTypes.ABERRANT_PREDALIEN_CHESTBURSTER.get(),
                AlienEntityTypes.NETHER_PREDALIEN_CHESTBURSTER.get(),
                AlienEntityTypes.PREDALIEN_CHESTBURSTER.get()
            );
    }

    private void addPredaliens() {
        getOrCreateTagBuilder(AlienEntityTypeTags.PREDALIENS)
            .add(
                AlienEntityTypes.ABERRANT_PREDALIEN.get(),
                AlienEntityTypes.IRRADIATED_PREDALIEN.get(),
                AlienEntityTypes.NETHER_PREDALIEN.get(),
                AlienEntityTypes.PREDALIEN.get()
            );
    }

    private void addProwlers() {
        getOrCreateTagBuilder(AlienEntityTypeTags.PROWLERS)
            .add(
                AlienEntityTypes.ABERRANT_PROWLER.get(),
                AlienEntityTypes.IRRADIATED_PROWLER.get(),
                AlienEntityTypes.NETHER_PROWLER.get(),
                AlienEntityTypes.PROWLER.get()
            );
    }

    private void addQueens() {
        getOrCreateTagBuilder(AlienEntityTypeTags.QUEENS)
            .addTag(AlienEntityTypeTags.EMPRESSES)
            .add(
                AlienEntityTypes.ABERRANT_QUEEN.get(),
                AlienEntityTypes.IRRADIATED_QUEEN.get(),
                AlienEntityTypes.NETHER_QUEEN.get(),
                AlienEntityTypes.QUEEN.get()
            );
    }

    private void addEmpresses() {
        getOrCreateTagBuilder(AlienEntityTypeTags.EMPRESSES)
            .add(
                AlienEntityTypes.ABERRANT_EMPRESS.get(),
                AlienEntityTypes.IRRADIATED_EMPRESS.get(),
                AlienEntityTypes.NETHER_EMPRESS.get(),
                AlienEntityTypes.EMPRESS.get()
            );
    }

    private void addRavagers() {
        getOrCreateTagBuilder(AlienEntityTypeTags.RAVAGERS)
            .add(
                AlienEntityTypes.ABERRANT_RAVAGER.get(),
                AlienEntityTypes.IRRADIATED_RAVAGER.get(),
                AlienEntityTypes.NETHER_RAVAGER.get(),
                AlienEntityTypes.RAVAGER.get()
            );
    }

    private void addRazorClaws() {
        getOrCreateTagBuilder(AlienEntityTypeTags.RAZOR_CLAWS)
            .add(
                AlienEntityTypes.ABERRANT_RAZOR_CLAW.get(),
                AlienEntityTypes.IRRADIATED_RAZOR_CLAW.get(),
                AlienEntityTypes.NETHER_RAZOR_CLAW.get(),
                AlienEntityTypes.RAZOR_CLAW.get()
            );
    }

    private void addRoyalAliens() {
        getOrCreateTagBuilder(AlienEntityTypeTags.ROYAL_ALIENS)
            .addTag(AlienEntityTypeTags.ROYAL_XENOMORPHS)
            .addTag(AlienEntityTypeTags.PREDALIEN_ADOLESCENTS)
            .addTag(AlienEntityTypeTags.PREDALIEN_CHESTBURSTERS)
            .add(
                AlienEntityTypes.ROYAL_ABERRANT_ADOLESCENT.get(),
                AlienEntityTypes.ROYAL_ABERRANT_CHESTBURSTER.get(),
                AlienEntityTypes.ROYAL_ABERRANT_FACEHUGGER.get(),
                AlienEntityTypes.ROYAL_ABERRANT_OVOMORPH.get(),
                AlienEntityTypes.ROYAL_ADOLESCENT.get(),
                AlienEntityTypes.ROYAL_CHESTBURSTER.get(),
                AlienEntityTypes.ROYAL_FACEHUGGER.get(),
                AlienEntityTypes.ROYAL_NETHER_ADOLESCENT.get(),
                AlienEntityTypes.ROYAL_NETHER_CHESTBURSTER.get(),
                AlienEntityTypes.ROYAL_NETHER_FACEHUGGER.get(),
                AlienEntityTypes.ROYAL_NETHER_OVOMORPH.get(),
                AlienEntityTypes.ROYAL_OVOMORPH.get()
            );
    }

    private void addRoyalXenomorphs() {
        getOrCreateTagBuilder(AlienEntityTypeTags.ROYAL_XENOMORPHS)
            .addTag(AlienEntityTypeTags.PRAETORIANS)
            .addTag(AlienEntityTypeTags.PREDALIENS)
            .addTag(AlienEntityTypeTags.QUEENS);
    }

    private void addScourgeAliens() {
        getOrCreateTagBuilder(AlienEntityTypeTags.SCOURGE_ALIENS)
            .addTag(AlienEntityTypeTags.BURSTERS)
            .addTag(AlienEntityTypeTags.CARRIERS)
            .addTag(AlienEntityTypeTags.CHRYSALISES)
            .addTag(AlienEntityTypeTags.HARBINGERS)
            .addTag(AlienEntityTypeTags.RAVAGERS)
            .addTag(AlienEntityTypeTags.RAZOR_CLAWS);
    }

    private void addBursters() {
        getOrCreateTagBuilder(AlienEntityTypeTags.BURSTERS)
            .add(
                AlienEntityTypes.ABERRANT_BURSTER.get(),
                AlienEntityTypes.IRRADIATED_BURSTER.get(),
                AlienEntityTypes.NETHER_BURSTER.get(),
                AlienEntityTypes.BURSTER.get()
            );
    }

    private void addRunnerHosts() {
        // NOTE: Llamas are deliberately excluded here.
        getOrCreateTagBuilder(AlienEntityTypeTags.RUNNER_HOSTS)
            .add(
                EntityType.CAMEL,
                EntityType.COW,
                // The Nether's only breeding fleshy megafauna - nether cattle. Makes crimson forests real hunting
                // grounds and gives nether hives a sustainable food chain (design decision July 25). Zoglins stay
                // off the menu: undead.
                EntityType.HOGLIN,
                EntityType.DONKEY,
                EntityType.FOX,
                EntityType.GOAT,
                EntityType.HORSE,
                EntityType.MOOSHROOM,
                EntityType.MULE,
                EntityType.PANDA,
                EntityType.PIG,
                EntityType.POLAR_BEAR,
                EntityType.RAVAGER,
                EntityType.SHEEP,
                EntityType.SNIFFER,
                EntityType.WOLF
            );
    }

    private void addRunners() {
        getOrCreateTagBuilder(AlienEntityTypeTags.RUNNERS)
            .add(
                AlienEntityTypes.ABERRANT_RUNNER.get(),
                AlienEntityTypes.IRRADIATED_RUNNER.get(),
                AlienEntityTypes.NETHER_RUNNER.get(),
                AlienEntityTypes.RUNNER.get()
            );
    }

    private void addSpitters() {
        getOrCreateTagBuilder(AlienEntityTypeTags.SPITTERS)
            .add(
                AlienEntityTypes.ABERRANT_SPITTER.get(),
                AlienEntityTypes.NETHER_SPITTER.get(),
                AlienEntityTypes.SPITTER.get()
            );
    }

    private void addWarriors() {
        getOrCreateTagBuilder(AlienEntityTypeTags.WARRIORS)
            .add(
                AlienEntityTypes.ABERRANT_WARRIOR.get(),
                AlienEntityTypes.IRRADIATED_WARRIOR.get(),
                AlienEntityTypes.NETHER_WARRIOR.get(),
                AlienEntityTypes.WARRIOR.get()
            );
    }

    private void addXenomorphs() {
        getOrCreateTagBuilder(AlienEntityTypeTags.XENOMORPHS)
            .addTag(AlienEntityTypeTags.BURSTERS)
            .addTag(AlienEntityTypeTags.CARRIERS)
            .addTag(AlienEntityTypeTags.CHRYSALISES)
            .addTag(AlienEntityTypeTags.CRUSHERS)
            .addTag(AlienEntityTypeTags.DRONES)
            .addTag(AlienEntityTypeTags.HARBINGERS)
            .addTag(AlienEntityTypeTags.PRAETORIANS)
            .addTag(AlienEntityTypeTags.PREDALIENS)
            .addTag(AlienEntityTypeTags.PROWLERS)
            .addTag(AlienEntityTypeTags.QUEENS)
            .addTag(AlienEntityTypeTags.RAVAGERS)
            .addTag(AlienEntityTypeTags.RAZOR_CLAWS)
            .addTag(AlienEntityTypeTags.RUNNERS)
            .addTag(AlienEntityTypeTags.SPITTERS)
            .addTag(AlienEntityTypeTags.WARRIORS);
    }

    private void addCompatibilityTags() {
        getOrCreateTagBuilder(AlienEntityTypeTags.ACID_IMMUNE)
            .addOptionalTag(GigTags.ACID_RESISTANT_ENTITY);

        getOrCreateTagBuilder(GigTags.ACID_RESISTANT_ENTITY)
            .addTag(AlienEntityTypeTags.ALIENS);

        getOrCreateTagBuilder(GigTags.DNAIMMUNE)
            .addTag(AlienEntityTypeTags.ALIENS);

        getOrCreateTagBuilder(GigTags.FACEHUGGER_BLACKLIST)
            .addTag(AlienEntityTypeTags.ALIENS);

        getOrCreateTagBuilder(StellarisEntityTypeTags.NO_OXYGEN_NEEDED)
            .addTag(AlienEntityTypeTags.ALIENS)
            .add(AlienEntityTypes.OVIPOSITOR.get());
    }

    private void addRadiationResistant() {
        getOrCreateTagBuilder(HumanEntityTypeTags.RADIATION_RESISTANT)
            .addTag(AlienEntityTypeTags.XENOMORPHS);
    }
}
