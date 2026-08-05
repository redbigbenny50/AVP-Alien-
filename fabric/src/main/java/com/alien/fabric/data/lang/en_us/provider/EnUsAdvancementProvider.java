package com.alien.fabric.data.lang.en_us.provider;

import com.alien.common.data.AlienAdvancements;
import com.blib.api.common.advancement.v1.BLibAdvancement;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;

import java.util.function.Consumer;

public class EnUsAdvancementProvider {

    public static final Consumer<FabricLanguageProvider.TranslationBuilder> CONSUMER = builder -> {
        provideAlienAdvancementTranslations(builder);
    };

    private static void provideAlienAdvancementTranslations(FabricLanguageProvider.TranslationBuilder builder) {
        addAdvancement(
            builder,
            AlienAdvancements.BLOCK_SPITTER_SPIT_WITH_HEAD_SHIELD,
            "Spitshined",
            "Block a spitter's spit attack with a xenomorph head shield"
        );

        addAdvancement(
            builder,
            AlienAdvancements.DEFEAT_A_RAID,
            "Shadow Legend",
            "Defeat a xenomorph raid"
        );

        addAdvancement(
            builder,
            AlienAdvancements.DUAL_VARIANT_RAIDS,
            "Common Enemy",
            "Have raids from two different xenomorph variants hunting you at the same time"
        );

        addAdvancement(
            builder,
            AlienAdvancements.LEAD_RAID_TO_ENEMY_HIVE,
            "And Hell Followed With You",
            "Lead a raid to a hive of a different xenomorph variant"
        );

        addAdvancement(
            builder,
            AlienAdvancements.ROOT,
            "AVP: Aliens",
            "In Minecraft, no one can hear you scream"
        );

        addAdvancement(
            builder,
            AlienAdvancements.KILL_A_HIVE,
            "Hive Buster",
            "Kill an alien hive"
        );

        addAdvancement(
            builder,
            AlienAdvancements.WITHSTAND_ATTACK_PARTY,
            "Here to stay",
            "Outlast a queen's attack party"
        );

        addAdvancement(
            builder,
            AlienAdvancements.KILL_A_HARBINGER,
            "Dread Silenced",
            "Kill a harbinger"
        );

        addAdvancement(
            builder,
            AlienAdvancements.KILL_A_LINEAGE,
            "End of the Bloodline",
            "Destroy a xenomorph lineage"
        );

        addAdvancement(
            builder,
            AlienAdvancements.KILL_A_ROYAL_ALIEN,
            "Regicide",
            "Kill a royal alien"
        );

        addAdvancement(
            builder,
            AlienAdvancements.KILL_ALL_ALIENS,
            "Total Xenocide",
            "Complete every variant xenocide advancement"
        );

        addAdvancement(
            builder,
            AlienAdvancements.KILL_ALL_ABERRANT_ALIENS,
            "Aberrant Xenocide",
            "Kill one of every aberrant alien"
        );

        addAdvancement(
            builder,
            AlienAdvancements.KILL_ALL_IRRADIATED_ALIENS,
            "Irradiated Xenocide",
            "Kill one of every irradiated alien"
        );

        addAdvancement(
            builder,
            AlienAdvancements.KILL_ALL_NETHER_ALIENS,
            "Nether Xenocide",
            "Kill one of every nether alien"
        );

        addAdvancement(
            builder,
            AlienAdvancements.KILL_ALL_NORMAL_ALIENS,
            "Xenocide",
            "Kill one of every normal alien"
        );

        addAdvancement(
            builder,
            AlienAdvancements.KILL_AN_ALIEN,
            "Imperfect Organism",
            "Kill an alien and live to tell the tale"
        );

        addAdvancement(
            builder,
            AlienAdvancements.KILL_AN_EMPRESS,
            "Not Empressed",
            "Kill an empress"
        );

        addAdvancement(
            builder,
            AlienAdvancements.BROKEN_THRONE,
            "Broken Throne",
            "Kill an exiled empress on the ruin her own empire left her"
        );

        addAdvancement(
            builder,
            AlienAdvancements.WEAR_CHITIN_ARMOR,
            "Cover Me with... Uh...",
            "Equip a full set of chitin armor"
        );

        addAdvancement(
            builder,
            AlienAdvancements.SHEAR_AN_OVOMORPH,
            "Eggsploration Time",
            "Free an ovomorph from its bindings"
        );

        addAdvancement(
            builder,
            AlienAdvancements.WEAR_PLATED_CHITIN_ARMOR,
            "Kneel to the Crown",
            "Equip a full set of plated chitin armor"
        );

        addAdvancement(
            builder,
            AlienAdvancements.REMOVE_EMBRYO_WITH_CHORUS_FRUIT,
            "Eviction",
            "Remove an alien from your chest by eating chorus fruit"
        );

        addAdvancement(
            builder,
            AlienAdvancements.EAT_RAW_ROYAL_JELLY,
            "Peanut Butter... Jelly... Time?",
            "Eat raw royal jelly. Do not make a habit of it"
        );

        addAdvancement(
            builder,
            AlienAdvancements.EAT_RAW_SCOURGE_JELLY,
            "I Like When the Red Water Comes Out",
            "Eat raw scourge jelly"
        );

        addAdvancement(
            builder,
            AlienAdvancements.EAT_RAW_IRRADIATED_JELLY,
            "All That Glitters and Glows",
            "Eat raw irradiated jelly. It bites back hardest"
        );

        addAdvancement(
            builder,
            AlienAdvancements.EAT_POISON_JELLY,
            "Toxicity only Reddit could love",
            "Eat raw poison jelly. There was never an upside"
        );

        addAdvancement(
            builder,
            AlienAdvancements.EAT_EVERY_JELLY,
            "Spreading it thick",
            "Eat every kind of jelly the hive makes"
        );
    }

    private static void addAdvancement(
        FabricLanguageProvider.TranslationBuilder builder,
        BLibAdvancement bLibAdvancement,
        String title,
        String description
    ) {
        builder.add(bLibAdvancement.titleTranslationKey(), title);
        builder.add(bLibAdvancement.descriptionTranslationKey(), description);
    }
}
