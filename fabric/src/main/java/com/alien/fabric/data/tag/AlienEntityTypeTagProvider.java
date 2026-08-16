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

        // ⚠⚠ NOT INSIDE THE AVPHuman GUARD BELOW. juvenile_prey is a list of VANILLA mobs (chicken, fox,
        // axolotl...) and has nothing to do with avp_human; putting it there meant it only generated when that mod
        // happened to be loaded during datagen, which is why two runs produced no file.
        addJuvenilePrey();

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

    // Who answers a xenomorph's cry for help. BURSTERS were dropped July 26/27: they are SCOURGE, and a cry is
    // a defence call, not a scourge sortie - answering it spent scourge stock on ordinary skirmishes and was the
    // only scourge caste in the roster (razor claws, chrysalises, carriers and ravagers were never in it).
    // PRAETORIANS were added the same day so a developed hive answers with something heavier than a young one.
    private void addAnswersXenomorphCriesForHelp() {
        getOrCreateTagBuilder(AlienEntityTypeTags.ANSWERS_XENOMORPH_CRIES_FOR_HELP)
            .addTag(AlienEntityTypeTags.DRONES)
            .addTag(AlienEntityTypeTags.PRAETORIANS)
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
                AlienEntityTypes.IRRADIATED_FACEHUGGER.get(),
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
            )
            // ⭐⭐ CROSS-MOD HOSTS, BUILT IN so the datapacks are not needed. ⚠ RE-APPLIED Aug 14 after an edit made
            // in another chat overwrote this file - the generated tag JSON still held them, so the loss was silent
            // until the next runDatagen would have deleted them.
            // ⚠ addOptional: an id that does not resolve is DROPPED at load, never a validation failure.
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "citizen"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "visitor"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "mercenary"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "barbarian"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "archerbarbarian"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "chiefbarbarian"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "pirate"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "archerpirate"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "chiefpirate"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "drownedpirate"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "drownedarcherpirate"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "drownedchiefpirate"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "mummy"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "archermummy"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "pharao"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "amazon"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "amazonspearman"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "amazonchief"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "shieldmaiden"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "norsemenarcher"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "norsemenchief"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "campbarbarian"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "camparcherbarbarian"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "campchiefbarbarian"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "camppirate"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "camparcherpirate"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "campchiefpirate"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "campdrownedpirate"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "campdrownedarcherpirate"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "campdrownedchiefpirate"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "campmummy"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "camparchermummy"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "camppharao"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "campamazon"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "campamazonspearman"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "campamazonchief"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "campshieldmaiden"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "campnorsemenarcher"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("minecolonies", "campnorsemenchief"))
            // Stellaris.
            .addOptional(ResourceLocation.fromNamespaceAndPath("stellaris", "alien"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("stellaris", "pygro"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("stellaris", "pygro_brute"));
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
            // ⚠ A turret is not a monster and was in no tier at all, so it fell through to the untagged catch-all -
            // which is LOW DANGER, and low danger is only hunted when the hive is short on biomass or the alien is in
            // a hunting party. Everywhere else the ONLY route to it was the retaliation override, i.e. after it had
            // already opened fire. That is the delay: aliens were waiting to be shot before they would answer.
            .addOptional(ResourceLocation.fromNamespaceAndPath("avp_human", "sentry_turret"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("avp_predator", "predator"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("avp_predator", "yautja"))
            .addOptionalTag(ResourceLocation.fromNamespaceAndPath("avp_predator", "predators"));
    }

    private void addIrradiatedAliens() {
        getOrCreateTagBuilder(AlienEntityTypeTags.IRRADIATED_ALIENS)
            .add(
                AlienEntityTypes.IRRADIATED_SPITTER.get(),
                AlienEntityTypes.IRRADIATED_FACEHUGGER.get(),
                AlienEntityTypes.IRRADIATED_OVOMORPH.get(),
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
            // Was missing entirely. Besides fire immunity this tag also gates nether-resin spawn validity, so the
            // cocoon was excluded from both.
            .add(AlienEntityTypes.NETHER_ROYAL_COCOON.get())
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
                AlienEntityTypes.IRRADIATED_OVOMORPH.get(),
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
            )
            // ⭐⭐ THE MARINE DOG. [stated] "the marine dog doesnt seem to be a viable host or at least its not targeted
            // as a host and hugged. i see it just standing in the host room."
            // <p>
            // It was in NEITHER hosts NOR runner_hosts, so facehuggers could not see it at all - the same shape as the
            // sentry-turret threat-tier miss: <b>untagged is not neutral, it is invisible.</b> ⚠ minecraft:wolf being
            // listed here does NOT cover it - avp_human:marine_dog is its own entity type, not a wolf variant.
            // </p>
            // <p>
            // ⚠ RUNNER_HOSTS, NOT HOSTS - a quadruped bursts a runner, which is also what the film's dog produced.
            // addOptional needs no mod-loaded gate, exactly as the marine and sentry_turret entries rely on.
            // </p>
            .addOptional(ResourceLocation.fromNamespaceAndPath("avp_human", "marine_dog"))
            // ⭐ CROSS-MOD RUNNER HOSTS - four-legged fauna, same addOptional safety.
            .addOptional(ResourceLocation.fromNamespaceAndPath("naturalist", "alligator"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("naturalist", "bear"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("naturalist", "boar"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("naturalist", "deer"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("naturalist", "elephant"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("naturalist", "giraffe"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("naturalist", "hippo"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("naturalist", "lion"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("naturalist", "rhino"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("naturalist", "tortoise"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("naturalist", "zebra"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("naturalist", "black_bear"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("naturalist", "capybara"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("naturalist", "komodo_dragon"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("naturalist", "tiger"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("naturalist", "mammoth"))
            // Stellaris.
            .addOptional(ResourceLocation.fromNamespaceAndPath("stellaris", "martian_raptor"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("stellaris", "mogler"));
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
                AlienEntityTypes.IRRADIATED_SPITTER.get(),
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

        // Nothing in the species breathes. The ALIENS tag covers every entity that extends Alien; the rest are
        // listed by hand because they are plain Mobs and no strain tag will ever hold them - the same blind spot
        // that left nether cocoons flammable. The ovipositors were remembered here and the royal cocoons were not,
        // so a royal molting in vacuum suffocated inside her own shell.
        //
        // Acid and acid spit are deliberately absent: neither is a LivingEntity, so suffocation cannot apply.
        getOrCreateTagBuilder(StellarisEntityTypeTags.NO_OXYGEN_NEEDED)
            .addTag(AlienEntityTypeTags.ALIENS)
            .add(
                AlienEntityTypes.OVIPOSITOR.get(),
                AlienEntityTypes.EMPRESS_OVIPOSITOR.get(),
                AlienEntityTypes.ROYAL_COCOON.get(),
                AlienEntityTypes.ABERRANT_ROYAL_COCOON.get(),
                AlienEntityTypes.NETHER_ROYAL_COCOON.get()
            );
    }

    /**
     * Who AVP: Human's radiation cannot touch. The rule is "every alien EXCEPT the aberrant strain".
     * <p>
     * Aberrants are the weak line, and their vulnerability to radiation is exactly why they cannot convert to
     * irradiated the way normal and nether do - a nuke or a splash of Irradiation kills them outright instead of
     * transmuting them. This used to add XENOMORPHS wholesale, which covered aberrant ones too.
     * <p>
     * The three strain tags partition the species perfectly: together they hold all 69 non-aberrant aliens and nothing
     * else, so this stays a RULE rather than a list and any alien added to a strain later inherits immunity for free.
     * Do NOT rewrite it as ALIENS plus exceptions - tags cannot subtract, and an attempt that reached for
     * ROYAL_XENOMORPHS and the predalien tags leaked six aberrants straight back in, because those are CASTE tags that
     * span every strain.
     */
    /**
     * ⭐ THE JUVENILE MENU. [stated] "chickens, cats, axolotls, baby animals, a fox is the largest thing they would
     * probably try to eat. everything else they would run from."
     * <p>
     * ⚠ THE FOX IS THE CEILING AND THE CEILING IS ABOUT SIZE. Nothing is here for being easy: no baby zombies, no
     * wolves, no hoglin calves. Baby animals are NOT listed - {@code JuvenilePrey} handles them as a rule, so a calf is
     * prey without the cow it becomes being prey.
     * </p>
     * <p>
     * ⚠ NO {@code addOptionalTag(EntityTypeTags.*)} SHORTCUT EXISTS FOR THIS. Vanilla has no "small passive" tag, and
     * the nearest thing ({@code EntityTypeTags.AQUATIC}) would hand a land-bound child a menu of things it cannot
     * reach. Explicit list, deliberately.
     * </p>
     */
    private void addJuvenilePrey() {
        getOrCreateTagBuilder(AlienEntityTypeTags.JUVENILE_PREY)
            .add(
                EntityType.AXOLOTL,
                EntityType.BAT,
                EntityType.CAT,
                EntityType.CHICKEN,
                EntityType.COD,
                EntityType.FOX,
                EntityType.FROG,
                EntityType.OCELOT,
                EntityType.PARROT,
                EntityType.RABBIT,
                EntityType.SALMON,
                EntityType.TROPICAL_FISH
            );
    }

    private void addRadiationResistant() {
        getOrCreateTagBuilder(HumanEntityTypeTags.RADIATION_RESISTANT)
            .addTag(AlienEntityTypeTags.NORMAL_ALIENS)
            .addTag(AlienEntityTypeTags.NETHER_ALIENS)
            .addTag(AlienEntityTypeTags.IRRADIATED_ALIENS);
    }
}
