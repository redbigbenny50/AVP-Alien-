package com.alien.fabric.data.lang.en_us.provider;

import com.alien.common.registry.init.AlienGameRules;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;

import java.util.function.Consumer;

/**
 * Display names and tooltips for this mod's game rules, as shown on the Edit Game Rules screen.
 * <h2>Why these were raw keys</h2> None of the rules had an entry, so the screen showed {@code gamerule.gunBalancing}
 * and friends verbatim. Commands were never affected - they use the rule id, not these strings.
 * <h2>How vanilla reads them</h2> Verified in {@code EditGameRulesScreen.addEntry} (1.21.1): the name is
 * {@code key.getDescriptionId()}, which {@code GameRules.Key} builds as {@code "gamerule." + id}; the tooltip is that
 * same key plus {@code ".description"}, shown only when it exists, and is word-wrapped at 150 px under the yellow rule
 * id and above the default value. Every id comes from the {@link AlienGameRules} key itself, so a renamed rule cannot
 * leave a stale string here.
 */
public class EnUsGameRuleProvider {

    public static final Consumer<FabricLanguageProvider.TranslationBuilder> CONSUMER = builder -> {
        var gunBalancing = AlienGameRules.GUN_BALANCING.getDescriptionId();
        builder.add(gunBalancing, "Gun Mod Balancing");
        builder.add(
            gunBalancing + ".description",
            // ⚠ Same text avp_predator writes for this key - the rule is shared between the two mods.
            "Scales TACZ and Point Blank damage against xenomorphs and yautja to match AVP: Human's guns. Turn off for their full, unmodified damage."
        );

        var totems = AlienGameRules.AVP_ALIEN_TOTEMS_PREVENT_CHESTBURSTER_DEATH.getDescriptionId();
        builder.add(totems, "Totems Prevent Chestburster Death");
        builder.add(
            totems + ".description",
            "A player holding a Totem of Undying in either hand survives a chestburster. The totem is used up."
        );

        var predaliens = AlienGameRules.AVP_ALIEN_HIVES_BREED_PREDALIENS.getDescriptionId();
        builder.add(predaliens, "Hives Breed Predaliens");
        builder.add(
            predaliens + ".description",
            "Hives may grow predaliens as part of their normal production. Requires AVP: Predator."
        );

        var hiveSpawns = AlienGameRules.HIVE_BLOCKS_MOB_SPAWNS.getDescriptionId();
        builder.add(hiveSpawns, "Hives Block Mob Spawns");
        builder.add(
            hiveSpawns + ".description",
            "When on, animals and monsters cannot spawn naturally inside a hive's ground, in every hive mode."
        );

        var queenSpawns = AlienGameRules.BLOCK_NATURAL_QUEEN_SPAWNS.getDescriptionId();
        builder.add(queenSpawns, "Block Natural Queen Spawns");
        builder.add(
            queenSpawns + ".description",
            "When on, wild queens stop spawning naturally in the world. Placed queens, spawn eggs and hive-raised daughters are unaffected."
        );
    };
}
