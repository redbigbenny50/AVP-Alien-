package com.alien.fabric.data.advancement;

import com.alien.AlienResources;
import com.alien.common.data.AlienAdvancements;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.block.AlienBlocks;
import com.alien.common.registry.init.block.AlienResinBlocks;
import com.alien.common.registry.init.item.AlienArmorItems;
import com.alien.common.registry.init.item.AlienItems;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.advancement.v1.BLibAdvancement;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.ImpossibleTrigger;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.KilledTrigger;
import net.minecraft.advancements.critereon.PlayerInteractTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class AlienAdvancementProvider {

    // Yes we have to do this manually.
    // No, a tag will not work (because tags only work with OR conditions, not AND).
    // No, a filter on the entity types using a tag won't work (because tags aren't loaded yet when this provider runs).
    // Yes, I was very annoyed with Mojang while writing this list out.
    private static final List<EntityType<?>> NORMAL_ALIENS_TO_KILL = List.of(
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

    private static final List<EntityType<?>> ABERRANT_ALIENS_TO_KILL = List.of(
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

    private static final List<EntityType<?>> NETHER_ALIENS_TO_KILL = List.of(
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
        AlienEntityTypes.ROYAL_NETHER_FACEHUGGER.get(),
        AlienEntityTypes.ROYAL_NETHER_OVOMORPH.get()
    );

    private static final List<EntityType<?>> IRRADIATED_ALIENS_TO_KILL = List.of(
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

    private static final List<VariantXenocide> VARIANT_XENOCIDES = List.of(
        new VariantXenocide(
            AlienAdvancements.KILL_ALL_NORMAL_ALIENS,
            AlienItems.CHITIN.get(),
            NORMAL_ALIENS_TO_KILL,
            false
        ),
        new VariantXenocide(
            AlienAdvancements.KILL_ALL_ABERRANT_ALIENS,
            AlienItems.ABERRANT_CHITIN.get(),
            ABERRANT_ALIENS_TO_KILL,
            false
        ),
        new VariantXenocide(
            AlienAdvancements.KILL_ALL_NETHER_ALIENS,
            AlienItems.NETHER_CHITIN.get(),
            NETHER_ALIENS_TO_KILL,
            false
        ),
        new VariantXenocide(
            AlienAdvancements.KILL_ALL_IRRADIATED_ALIENS,
            AlienItems.IRRADIATED_CHITIN.get(),
            IRRADIATED_ALIENS_TO_KILL,
            true
        )
    );

    public static void generateAdvancements(HolderLookup.Provider registryLookup, Consumer<AdvancementHolder> consumer) {
        generateAdvancements(registryLookup, consumer, consumer);
    }

    public static void generateAdvancements(
        HolderLookup.Provider registryLookup,
        Consumer<AdvancementHolder> consumer,
        Consumer<AdvancementHolder> avpHumanConsumer
    ) {
        var root = addRootAdvancement(consumer);

        addLifecycleAdvancements(root, consumer);
        addCombatAdvancements(root, consumer, avpHumanConsumer);
    }

    private static AdvancementHolder addRootAdvancement(Consumer<AdvancementHolder> consumer) {
        return Advancement.Builder.advancement()
            .display(
                AlienResinBlocks.RESIN.get(),
                AlienAdvancements.ROOT.titleComponent(),
                AlienAdvancements.ROOT.descriptionComponent(),
                AlienResources.location("textures/gui/advancements/backgrounds/resin.png"),
                AdvancementType.TASK,
                false,
                false,
                false
            )
            .addCriterion("crafting_table", InventoryChangeTrigger.TriggerInstance.hasItems(Blocks.CRAFTING_TABLE))
            .save(consumer, AlienAdvancements.ROOT.resourceLocation().toString());
    }

    private static void addLifecycleAdvancements(AdvancementHolder root, Consumer<AdvancementHolder> consumer) {
        addRemoveEmbryoWithChorusFruitAdvancement(root, consumer);
        addEatRawRoyalJellyAdvancement(root, consumer);
        addEatRawScourgeJellyAdvancement(root, consumer);
        addEatPoisonJellyAdvancement(root, consumer);
        addEatEveryJellyAdvancement(root, consumer);

        addEatRawIrradiatedJellyAdvancement(root, consumer);
    }

    private static void addCombatAdvancements(
        AdvancementHolder root,
        Consumer<AdvancementHolder> consumer,
        Consumer<AdvancementHolder> avpHumanConsumer
    ) {
        var alienKillerAdvancement = addAlienKillerAdvancement(root, consumer);
        addShearAnOvomorphAdvancement(alienKillerAdvancement, consumer);
        addChitinArmorAdvancements(alienKillerAdvancement, consumer);
        addBlockSpitterSpitWithHeadShieldAdvancement(alienKillerAdvancement, consumer);
        addWithstandAttackPartyAdvancement(alienKillerAdvancement, consumer);

        var royalAlienKillerAdvancement = addRoyalAlienKillerAdvancement(alienKillerAdvancement, consumer);
        addRoyalCombatAdvancements(royalAlienKillerAdvancement, consumer, avpHumanConsumer);
    }

    private static void addRoyalCombatAdvancements(
        AdvancementHolder royalAlienKillerAdvancement,
        Consumer<AdvancementHolder> consumer,
        Consumer<AdvancementHolder> avpHumanConsumer
    ) {
        addEmpressKillerAdvancement(royalAlienKillerAdvancement, consumer);
        var harbingerKillerAdvancement = addHarbingerKillerAdvancement(royalAlienKillerAdvancement, consumer);
        addRaidAdvancements(harbingerKillerAdvancement, consumer);
        addHiveDestructionAdvancements(royalAlienKillerAdvancement, consumer, avpHumanConsumer);
        addPlatedChitinArmorAdvancement(royalAlienKillerAdvancement, consumer);
    }

    private static void addRaidAdvancements(AdvancementHolder harbingerKillerAdvancement, Consumer<AdvancementHolder> consumer) {
        var raidDefeatAdvancement = addRaidDefeatAdvancement(harbingerKillerAdvancement, consumer);
        addDualVariantRaidsAdvancement(raidDefeatAdvancement, consumer);
        addLeadRaidToEnemyHiveAdvancement(raidDefeatAdvancement, consumer);
    }

    private static void addHiveDestructionAdvancements(
        AdvancementHolder royalAlienKillerAdvancement,
        Consumer<AdvancementHolder> consumer,
        Consumer<AdvancementHolder> avpHumanConsumer
    ) {
        var hiveBusterAdvancement = addHiveBusterAdvancement(royalAlienKillerAdvancement, consumer);
        var lineageKillerAdvancement = addLineageKillerAdvancement(hiveBusterAdvancement, consumer);
        addXenocideAdvancements(lineageKillerAdvancement, consumer, avpHumanConsumer);
    }

    private static void addXenocideAdvancements(
        AdvancementHolder lineageKillerAdvancement,
        Consumer<AdvancementHolder> consumer,
        Consumer<AdvancementHolder> avpHumanConsumer
    ) {
        for (var xenocide : VARIANT_XENOCIDES) {
            var xenocideConsumer = xenocide.requiresAvpHuman() ? avpHumanConsumer : consumer;

            addVariantXenocideAdvancement(
                lineageKillerAdvancement,
                xenocideConsumer,
                xenocide.advancement(),
                xenocide.icon(),
                xenocide.aliensToKill()
            );
        }

        addXenocideAdvancement(lineageKillerAdvancement, consumer);
    }

    private static AdvancementHolder addShearAnOvomorphAdvancement(
        AdvancementHolder alienKillerAdvancement,
        Consumer<AdvancementHolder> consumer
    ) {
        return Advancement.Builder.advancement()
            .parent(alienKillerAdvancement)
            .display(
                Items.SHEARS,
                AlienAdvancements.SHEAR_AN_OVOMORPH.titleComponent(),
                AlienAdvancements.SHEAR_AN_OVOMORPH.descriptionComponent(),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion(
                AlienAdvancements.SHEAR_AN_OVOMORPH.path(),
                PlayerInteractTrigger.TriggerInstance.itemUsedOnEntity(
                    ItemPredicate.Builder.item().of(Items.SHEARS),
                    Optional.of(EntityPredicate.wrap(EntityPredicate.Builder.entity().of(AlienEntityTypeTags.OVOMORPHS)))
                )
            )
            .save(consumer, AlienAdvancements.SHEAR_AN_OVOMORPH.resourceLocation().toString());
    }

    private static AdvancementHolder addBlockSpitterSpitWithHeadShieldAdvancement(
        AdvancementHolder parent,
        Consumer<AdvancementHolder> consumer
    ) {
        return Advancement.Builder.advancement()
            .addCriterion(
                "block_spitter_spit_with_head_shield",
                CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance())
            )
            .parent(parent)
            .display(
                AlienItems.QUEEN_HEAD_SHIELD.get(),
                AlienAdvancements.BLOCK_SPITTER_SPIT_WITH_HEAD_SHIELD.titleComponent(),
                AlienAdvancements.BLOCK_SPITTER_SPIT_WITH_HEAD_SHIELD.descriptionComponent(),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .save(consumer, AlienAdvancements.BLOCK_SPITTER_SPIT_WITH_HEAD_SHIELD.resourceLocation().toString());
    }

    private static AdvancementHolder addChitinArmorAdvancements(AdvancementHolder parent, Consumer<AdvancementHolder> consumer) {
        return Advancement.Builder.advancement()
            .parent(parent)
            .display(
                AlienArmorItems.CHITIN_HELMET.get(),
                AlienAdvancements.WEAR_CHITIN_ARMOR.titleComponent(),
                AlienAdvancements.WEAR_CHITIN_ARMOR.descriptionComponent(),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .addCriterion(
                "aberrant_chitin_armor",
                InventoryChangeTrigger.TriggerInstance.hasItems(
                    AlienArmorItems.ABERRANT_CHITIN_HELMET.get(),
                    AlienArmorItems.ABERRANT_CHITIN_CHESTPLATE.get(),
                    AlienArmorItems.ABERRANT_CHITIN_LEGGINGS.get(),
                    AlienArmorItems.ABERRANT_CHITIN_BOOTS.get()
                )
            )
            .addCriterion(
                "chitin_armor",
                InventoryChangeTrigger.TriggerInstance.hasItems(
                    AlienArmorItems.CHITIN_HELMET.get(),
                    AlienArmorItems.CHITIN_CHESTPLATE.get(),
                    AlienArmorItems.CHITIN_LEGGINGS.get(),
                    AlienArmorItems.CHITIN_BOOTS.get()
                )
            )
            .addCriterion(
                "nether_chitin_armor",
                InventoryChangeTrigger.TriggerInstance.hasItems(
                    AlienArmorItems.NETHER_CHITIN_HELMET.get(),
                    AlienArmorItems.NETHER_CHITIN_CHESTPLATE.get(),
                    AlienArmorItems.NETHER_CHITIN_LEGGINGS.get(),
                    AlienArmorItems.NETHER_CHITIN_BOOTS.get()
                )
            )
            .requirements(AdvancementRequirements.Strategy.OR)
            .save(consumer, AlienAdvancements.WEAR_CHITIN_ARMOR.resourceLocation().toString());
    }

    private static AdvancementHolder addPlatedChitinArmorAdvancement(AdvancementHolder parent, Consumer<AdvancementHolder> consumer) {
        return Advancement.Builder.advancement()
            .parent(parent)
            .display(
                AlienArmorItems.PLATED_CHITIN_HELMET.get(),
                AlienAdvancements.WEAR_PLATED_CHITIN_ARMOR.titleComponent(),
                AlienAdvancements.WEAR_PLATED_CHITIN_ARMOR.descriptionComponent(),
                null,
                AdvancementType.CHALLENGE,
                true,
                true,
                false
            )
            .addCriterion(
                "plated_aberrant_chitin_armor",
                InventoryChangeTrigger.TriggerInstance.hasItems(
                    AlienArmorItems.PLATED_ABERRANT_CHITIN_HELMET.get(),
                    AlienArmorItems.PLATED_ABERRANT_CHITIN_CHESTPLATE.get(),
                    AlienArmorItems.PLATED_ABERRANT_CHITIN_LEGGINGS.get(),
                    AlienArmorItems.PLATED_ABERRANT_CHITIN_BOOTS.get()
                )
            )
            .addCriterion(
                "plated_chitin_armor",
                InventoryChangeTrigger.TriggerInstance.hasItems(
                    AlienArmorItems.PLATED_CHITIN_HELMET.get(),
                    AlienArmorItems.PLATED_CHITIN_CHESTPLATE.get(),
                    AlienArmorItems.PLATED_CHITIN_LEGGINGS.get(),
                    AlienArmorItems.PLATED_CHITIN_BOOTS.get()
                )
            )
            .addCriterion(
                "plated_nether_chitin_armor",
                InventoryChangeTrigger.TriggerInstance.hasItems(
                    AlienArmorItems.PLATED_NETHER_CHITIN_HELMET.get(),
                    AlienArmorItems.PLATED_NETHER_CHITIN_CHESTPLATE.get(),
                    AlienArmorItems.PLATED_NETHER_CHITIN_LEGGINGS.get(),
                    AlienArmorItems.PLATED_NETHER_CHITIN_BOOTS.get()
                )
            )
            .requirements(AdvancementRequirements.Strategy.OR)
            .rewards(AdvancementRewards.Builder.experience(100))
            .save(consumer, AlienAdvancements.WEAR_PLATED_CHITIN_ARMOR.resourceLocation().toString());
    }

    private static AdvancementHolder addAlienKillerAdvancement(AdvancementHolder parent, Consumer<AdvancementHolder> consumer) {
        return addMobsToKill(Advancement.Builder.advancement(), "kill_an_alien", AlienEntityTypeTags.ALIENS)
            .parent(parent)
            .display(
                AlienItems.CHITIN.get(),
                AlienAdvancements.KILL_AN_ALIEN.titleComponent(),
                AlienAdvancements.KILL_AN_ALIEN.descriptionComponent(),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .requirements(AdvancementRequirements.Strategy.OR)
            .save(consumer, AlienAdvancements.KILL_AN_ALIEN.resourceLocation().toString());
    }

    private static AdvancementHolder addRoyalAlienKillerAdvancement(AdvancementHolder parent, Consumer<AdvancementHolder> consumer) {
        return addMobsToKill(Advancement.Builder.advancement(), "kill_a_royal_alien", AlienEntityTypeTags.ROYAL_XENOMORPHS)
            .parent(parent)
            .display(
                AlienItems.PLATED_CHITIN.get(),
                AlienAdvancements.KILL_A_ROYAL_ALIEN.titleComponent(),
                AlienAdvancements.KILL_A_ROYAL_ALIEN.descriptionComponent(),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .requirements(AdvancementRequirements.Strategy.OR)
            .save(consumer, AlienAdvancements.KILL_A_ROYAL_ALIEN.resourceLocation().toString());
    }

    private static AdvancementHolder addEmpressKillerAdvancement(AdvancementHolder parent, Consumer<AdvancementHolder> consumer) {
        return addMobsToKill(Advancement.Builder.advancement(), "kill_an_empress", AlienEntityTypeTags.EMPRESSES)
            .parent(parent)
            .display(
                AlienItems.RAW_ROYAL_JELLY.get(),
                AlienAdvancements.KILL_AN_EMPRESS.titleComponent(),
                AlienAdvancements.KILL_AN_EMPRESS.descriptionComponent(),
                null,
                AdvancementType.CHALLENGE,
                true,
                true,
                false
            )
            .requirements(AdvancementRequirements.Strategy.OR)
            .rewards(AdvancementRewards.Builder.experience(100))
            .save(consumer, AlienAdvancements.KILL_AN_EMPRESS.resourceLocation().toString());
    }

    private static AdvancementHolder addHarbingerKillerAdvancement(AdvancementHolder parent, Consumer<AdvancementHolder> consumer) {
        return addMobsToKill(Advancement.Builder.advancement(), "kill_a_harbinger", AlienEntityTypeTags.HARBINGERS)
            .parent(parent)
            .display(
                AlienItems.RAW_SCOURGE_JELLY.get(),
                AlienAdvancements.KILL_A_HARBINGER.titleComponent(),
                AlienAdvancements.KILL_A_HARBINGER.descriptionComponent(),
                null,
                AdvancementType.CHALLENGE,
                true,
                true,
                false
            )
            .requirements(AdvancementRequirements.Strategy.OR)
            .rewards(AdvancementRewards.Builder.experience(100))
            .save(consumer, AlienAdvancements.KILL_A_HARBINGER.resourceLocation().toString());
    }

    private static AdvancementHolder addRaidDefeatAdvancement(AdvancementHolder parent, Consumer<AdvancementHolder> consumer) {
        return Advancement.Builder.advancement()
            .addCriterion("defeat_a_raid", CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance()))
            .parent(parent)
            .display(
                AlienItems.RAW_SCOURGE_JELLY.get(),
                AlienAdvancements.DEFEAT_A_RAID.titleComponent(),
                AlienAdvancements.DEFEAT_A_RAID.descriptionComponent(),
                null,
                AdvancementType.CHALLENGE,
                true,
                true,
                false
            )
            .rewards(AdvancementRewards.Builder.experience(100))
            .save(consumer, AlienAdvancements.DEFEAT_A_RAID.resourceLocation().toString());
    }

    private static AdvancementHolder addDualVariantRaidsAdvancement(
        AdvancementHolder parent,
        Consumer<AdvancementHolder> consumer
    ) {
        return Advancement.Builder.advancement()
            .addCriterion(
                "dual_variant_raids",
                CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance())
            )
            .parent(parent)
            .display(
                AlienItems.RAW_SCOURGE_JELLY.get(),
                AlienAdvancements.DUAL_VARIANT_RAIDS.titleComponent(),
                AlienAdvancements.DUAL_VARIANT_RAIDS.descriptionComponent(),
                null,
                AdvancementType.CHALLENGE,
                true,
                true,
                false
            )
            .rewards(AdvancementRewards.Builder.experience(100))
            .save(consumer, AlienAdvancements.DUAL_VARIANT_RAIDS.resourceLocation().toString());
    }

    private static AdvancementHolder addLeadRaidToEnemyHiveAdvancement(
        AdvancementHolder parent,
        Consumer<AdvancementHolder> consumer
    ) {
        return Advancement.Builder.advancement()
            .addCriterion(
                "lead_raid_to_enemy_hive",
                CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance())
            )
            .parent(parent)
            .display(
                AlienResinBlocks.RESIN.get(),
                AlienAdvancements.LEAD_RAID_TO_ENEMY_HIVE.titleComponent(),
                AlienAdvancements.LEAD_RAID_TO_ENEMY_HIVE.descriptionComponent(),
                null,
                AdvancementType.CHALLENGE,
                true,
                true,
                false
            )
            .rewards(AdvancementRewards.Builder.experience(100))
            .save(consumer, AlienAdvancements.LEAD_RAID_TO_ENEMY_HIVE.resourceLocation().toString());
    }

    private static AdvancementHolder addVariantXenocideAdvancement(
        AdvancementHolder parent,
        Consumer<AdvancementHolder> consumer,
        BLibAdvancement advancement,
        ItemLike icon,
        List<EntityType<?>> aliensToKill
    ) {
        return addMobsToKill(Advancement.Builder.advancement(), aliensToKill)
            .parent(parent)
            .display(
                icon,
                advancement.titleComponent(),
                advancement.descriptionComponent(),
                null,
                AdvancementType.CHALLENGE,
                true,
                true,
                false
            )
            .requirements(AdvancementRequirements.Strategy.AND)
            .rewards(AdvancementRewards.Builder.experience(100))
            .save(consumer, advancement.resourceLocation().toString());
    }

    private static AdvancementHolder addXenocideAdvancement(AdvancementHolder parent, Consumer<AdvancementHolder> consumer) {
        return Advancement.Builder.advancement()
            .addCriterion("kill_all_aliens", CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance()))
            .parent(parent)
            .display(
                AlienBlocks.ROYAL_JELLY_BLOCK.get(),
                AlienAdvancements.KILL_ALL_ALIENS.titleComponent(),
                AlienAdvancements.KILL_ALL_ALIENS.descriptionComponent(),
                null,
                AdvancementType.CHALLENGE,
                true,
                true,
                false
            )
            .requirements(AdvancementRequirements.Strategy.AND)
            .rewards(AdvancementRewards.Builder.experience(100))
            .save(consumer, AlienAdvancements.KILL_ALL_ALIENS.resourceLocation().toString());
    }

    private static AdvancementHolder addHiveBusterAdvancement(AdvancementHolder parent, Consumer<AdvancementHolder> consumer) {
        return Advancement.Builder.advancement()
            .addCriterion("kill_a_hive", CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance()))
            .parent(parent)
            .display(
                AlienItems.RAW_ROYAL_JELLY.get(),
                AlienAdvancements.KILL_A_HIVE.titleComponent(),
                AlienAdvancements.KILL_A_HIVE.descriptionComponent(),
                null,
                AdvancementType.CHALLENGE,
                true,
                true,
                false
            )
            .rewards(AdvancementRewards.Builder.experience(100))
            .save(consumer, AlienAdvancements.KILL_A_HIVE.resourceLocation().toString());
    }

    private static AdvancementHolder addWithstandAttackPartyAdvancement(AdvancementHolder parent, Consumer<AdvancementHolder> consumer) {
        return Advancement.Builder.advancement()
            .addCriterion("withstand_attack_party", CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance()))
            .parent(parent)
            .display(
                AlienItems.RAW_ROYAL_JELLY.get(),
                AlienAdvancements.WITHSTAND_ATTACK_PARTY.titleComponent(),
                AlienAdvancements.WITHSTAND_ATTACK_PARTY.descriptionComponent(),
                null,
                AdvancementType.CHALLENGE,
                true,
                true,
                false
            )
            .rewards(AdvancementRewards.Builder.experience(100))
            .save(consumer, AlienAdvancements.WITHSTAND_ATTACK_PARTY.resourceLocation().toString());
    }

    private static AdvancementHolder addLineageKillerAdvancement(AdvancementHolder parent, Consumer<AdvancementHolder> consumer) {
        return Advancement.Builder.advancement()
            .addCriterion("kill_a_lineage", CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance()))
            .parent(parent)
            .display(
                AlienBlocks.ROYAL_JELLY_BLOCK.get(),
                AlienAdvancements.KILL_A_LINEAGE.titleComponent(),
                AlienAdvancements.KILL_A_LINEAGE.descriptionComponent(),
                null,
                AdvancementType.CHALLENGE,
                true,
                true,
                false
            )
            .rewards(AdvancementRewards.Builder.experience(100))
            .save(consumer, AlienAdvancements.KILL_A_LINEAGE.resourceLocation().toString());
    }

    private static AdvancementHolder addRemoveEmbryoWithChorusFruitAdvancement(
        AdvancementHolder parent,
        Consumer<AdvancementHolder> consumer
    ) {
        return Advancement.Builder.advancement()
            .addCriterion(
                "remove_embryo_with_chorus_fruit",
                CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance())
            )
            .parent(parent)
            .display(
                Items.CHORUS_FRUIT,
                AlienAdvancements.REMOVE_EMBRYO_WITH_CHORUS_FRUIT.titleComponent(),
                AlienAdvancements.REMOVE_EMBRYO_WITH_CHORUS_FRUIT.descriptionComponent(),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .save(consumer, AlienAdvancements.REMOVE_EMBRYO_WITH_CHORUS_FRUIT.resourceLocation().toString());
    }

    /**
     * Granted from {@code RawRoyalJellyItem.finishUsingItem}, so the criterion is IMPOSSIBLE and the code does the
     * awarding - the same shape the chorus fruit advancement above uses.
     */
    private static AdvancementHolder addEatRawRoyalJellyAdvancement(
        AdvancementHolder parent,
        Consumer<AdvancementHolder> consumer
    ) {
        return Advancement.Builder.advancement()
            .addCriterion(
                "eat_raw_royal_jelly",
                CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance())
            )
            .parent(parent)
            .display(
                AlienItems.RAW_ROYAL_JELLY.get(),
                AlienAdvancements.EAT_RAW_ROYAL_JELLY.titleComponent(),
                AlienAdvancements.EAT_RAW_ROYAL_JELLY.descriptionComponent(),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .save(consumer, AlienAdvancements.EAT_RAW_ROYAL_JELLY.resourceLocation().toString());
    }

    /** Granted from {@code RawScourgeJellyItem.finishUsingItem}; see the royal jelly advancement above. */
    private static AdvancementHolder addEatRawScourgeJellyAdvancement(
        AdvancementHolder parent,
        Consumer<AdvancementHolder> consumer
    ) {
        return Advancement.Builder.advancement()
            .addCriterion(
                "eat_raw_scourge_jelly",
                CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance())
            )
            .parent(parent)
            .display(
                AlienItems.RAW_SCOURGE_JELLY.get(),
                AlienAdvancements.EAT_RAW_SCOURGE_JELLY.titleComponent(),
                AlienAdvancements.EAT_RAW_SCOURGE_JELLY.descriptionComponent(),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .save(consumer, AlienAdvancements.EAT_RAW_SCOURGE_JELLY.resourceLocation().toString());
    }

    private static AdvancementHolder addEatPoisonJellyAdvancement(
        AdvancementHolder parent,
        Consumer<AdvancementHolder> consumer
    ) {
        return Advancement.Builder.advancement()
            .addCriterion(
                "eat_poison_jelly",
                CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance())
            )
            .parent(parent)
            .display(
                AlienItems.POISON_JELLY.get(),
                AlienAdvancements.EAT_POISON_JELLY.titleComponent(),
                AlienAdvancements.EAT_POISON_JELLY.descriptionComponent(),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .save(consumer, AlienAdvancements.EAT_POISON_JELLY.resourceLocation().toString());
    }

    private static AdvancementHolder addEatEveryJellyAdvancement(
        AdvancementHolder parent,
        Consumer<AdvancementHolder> consumer
    ) {
        return Advancement.Builder.advancement()
            .addCriterion(
                "eat_every_jelly",
                CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance())
            )
            .parent(parent)
            .display(
                AlienItems.RAW_ROYAL_JELLY.get(),
                AlienAdvancements.EAT_EVERY_JELLY.titleComponent(),
                AlienAdvancements.EAT_EVERY_JELLY.descriptionComponent(),
                null,
                AdvancementType.GOAL,
                true,
                true,
                false
            )
            .save(consumer, AlienAdvancements.EAT_EVERY_JELLY.resourceLocation().toString());
    }

    private static AdvancementHolder addEatRawIrradiatedJellyAdvancement(
        AdvancementHolder parent,
        Consumer<AdvancementHolder> consumer
    ) {
        return Advancement.Builder.advancement()
            .addCriterion(
                "eat_raw_irradiated_jelly",
                CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance())
            )
            .parent(parent)
            .display(
                AlienItems.RAW_IRRADIATED_JELLY.get(),
                AlienAdvancements.EAT_RAW_IRRADIATED_JELLY.titleComponent(),
                AlienAdvancements.EAT_RAW_IRRADIATED_JELLY.descriptionComponent(),
                null,
                AdvancementType.TASK,
                true,
                true,
                false
            )
            .save(consumer, AlienAdvancements.EAT_RAW_IRRADIATED_JELLY.resourceLocation().toString());
    }

    private static Advancement.Builder addMobsToKill(
        Advancement.Builder builder,
        String criterionKey,
        TagKey<EntityType<?>> entityTypeTagKey
    ) {
        builder.addCriterion(
            criterionKey,
            KilledTrigger.TriggerInstance.playerKilledEntity(EntityPredicate.Builder.entity().of(entityTypeTagKey))
        );
        return builder;
    }

    private static Advancement.Builder addMobsToKill(Advancement.Builder builder, List<EntityType<?>> list) {
        list.forEach(
            entityType -> builder.addCriterion(
                BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString(),
                KilledTrigger.TriggerInstance.playerKilledEntity(EntityPredicate.Builder.entity().of(entityType))
            )
        );
        return builder;
    }

    private record VariantXenocide(
        BLibAdvancement advancement,
        ItemLike icon,
        List<EntityType<?>> aliensToKill,
        boolean requiresAvpHuman
    ) {}
}
