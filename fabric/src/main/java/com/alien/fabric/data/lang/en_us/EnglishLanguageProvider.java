package com.alien.fabric.data.lang.en_us;

import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienMobEffects;
import com.alien.fabric.data.lang.en_us.provider.EnUsAdvancementProvider;
import com.alien.fabric.data.lang.en_us.provider.EnUsBiomeTagProvider;
import com.alien.fabric.data.lang.en_us.provider.EnUsBlockProvider;
import com.alien.fabric.data.lang.en_us.provider.EnUsBlockTagProvider;
import com.alien.fabric.data.lang.en_us.provider.EnUsConfigProvider;
import com.alien.fabric.data.lang.en_us.provider.EnUsCreativeModeTabProvider;
import com.alien.fabric.data.lang.en_us.provider.EnUsDamageTypeTagProvider;
import com.alien.fabric.data.lang.en_us.provider.EnUsEntityProvider;
import com.alien.fabric.data.lang.en_us.provider.EnUsEntityTypeTagProvider;
import com.alien.fabric.data.lang.en_us.provider.EnUsItemProvider;
import com.alien.fabric.data.lang.en_us.provider.EnUsItemTagProvider;
import com.alien.fabric.data.lang.en_us.provider.EnUsMobEffectTagProvider;
import com.alien.fabric.data.lang.en_us.provider.EnUsSoundEventProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.core.HolderLookup;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class EnglishLanguageProvider extends FabricLanguageProvider {

    public EnglishLanguageProvider(FabricDataOutput dataOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(dataOutput, "en_us", registryLookup);
    }

    @Override
    public void generateTranslations(HolderLookup.Provider registryLookup, TranslationBuilder builder) {
        // Blocks
        EnUsBlockProvider.CONSUMER.accept(builder);

        // Creative Mode Tabs
        EnUsCreativeModeTabProvider.CONSUMER.accept(builder);

        // Death messages
        // Boss bar shown while a queen is DOWN. This lived only in the generated en_us.json and would have been
        // wiped by the next datagen run, leaving the raw key on screen.
        builder.add("boss.avp_alien.queen_incapacitated", "Queen \u2014 Incapacitated");
        builder.add(
            "message.avp_alien.inhibitor.not_subdued",
            "The queen isn't subdued \u2014 down her, catch her hibernating, or chain her fully"
        );
        builder.add("message.avp_alien.inhibitor.already_attached", "This queen already has an inhibitor");

        builder.add("death.attack.acid", "%1$s vaporized in acid");
        builder.add("death.attack.chestbursting", "%1$s gave birth");
        builder.add("death.attack.harbinger_backhand", "%1$s was swatted aside");
        builder.add("death.attack.harbinger_backhand.player", "%1$s was swatted aside by %2$s");
        builder.add("death.attack.harbinger_kick", "%1$s was punted");
        builder.add("death.attack.harbinger_kick.player", "%1$s was punted by %2$s");
        builder.add("death.attack.harbinger_slam", "%1$s was flattened");
        builder.add("death.attack.harbinger_slam.player", "%1$s was flattened by %2$s");
        builder.add("death.attack.radiation_sickness", "%1$s was welcomed to the wasteland");
        builder.add("death.attack.ravager_claw", "%1$s was torn apart");
        builder.add("death.attack.ravager_claw.player", "%1$s was torn apart by %2$s");
        builder.add("death.attack.ravager_special", "%1$s was eviscerated");
        builder.add("death.attack.ravager_special.player", "%1$s was eviscerated by %2$s");
        builder.add("death.attack.smothering", "%1$s was smothered to death");

        // Effects
        builder.add(AlienMobEffects.getBloodLossHolder().value(), "Blood Loss");
        builder.add(AlienMobEffects.getFrenzyHolder().value(), "Frenzy");
        builder.add(AlienMobEffects.getMetamorphosisHolder().value(), "Metamorphosis");
        builder.add(AlienMobEffects.getScourgeHolder().value(), "Scourge");
        builder.add(AlienMobEffects.getMarkedForDeathHolder().value(), "Marked for Death");
        // The six that were missing - tooltips showed raw keys ([stated] "the effects listed on the potion seems
        // to have long form not the normal name").
        builder.add(AlienMobEffects.getRadiationResistanceHolder().value(), "Radiation Resistance");
        builder.add(AlienMobEffects.getGlowingTalonsHolder().value(), "Glowing Talons");
        builder.add(AlienMobEffects.getRadiationSicknessHolder().value(), "Radiation Sickness");
        builder.add(AlienMobEffects.getChitinousAuraHolder().value(), "Chitinous Aura");
        builder.add(AlienMobEffects.getHivesBaneHolder().value(), "Hive's Bane");
        builder.add(AlienMobEffects.getImmovableHolder().value(), "Immovable");

        // Potions
        builder.add("item.minecraft.potion.effect.metamorphosis", "Potion of Metamorphosis");
        builder.add("item.minecraft.splash_potion.effect.metamorphosis", "Splash Potion of Metamorphosis");
        builder.add("item.minecraft.lingering_potion.effect.metamorphosis", "Lingering Potion of Metamorphosis");
        builder.add("item.minecraft.tipped_arrow.effect.metamorphosis", "Arrow of Metamorphosis");
        builder.add("item.minecraft.potion.effect.growth_suppression", "Potion of Growth Suppression");
        builder.add("item.minecraft.splash_potion.effect.growth_suppression", "Splash Potion of Growth Suppression");
        builder.add("item.minecraft.lingering_potion.effect.growth_suppression", "Lingering Potion of Growth Suppression");
        builder.add("item.minecraft.tipped_arrow.effect.growth_suppression", "Arrow of Growth Suppression");
        builder.add("effect.avp_alien.growth_suppression", "Growth Suppression");
        builder.add("effect.avp_alien.jelly_sickness", "Jelly Sickness");
        builder.add("item.minecraft.potion.effect.scourge", "Potion of Scourge");
        builder.add("item.minecraft.splash_potion.effect.scourge", "Splash Potion of Scourge");
        builder.add("item.minecraft.lingering_potion.effect.scourge", "Lingering Potion of Scourge");

        builder.add("item.minecraft.potion.effect.irradiation", "Potion of Irradiation");
        builder.add("item.minecraft.splash_potion.effect.irradiation", "Splash Potion of Irradiation");
        builder.add("item.minecraft.lingering_potion.effect.irradiation", "Lingering Potion of Irradiation");
        builder.add("item.minecraft.tipped_arrow.effect.irradiation", "Arrow of Irradiation");
        builder.add("item.minecraft.tipped_arrow.effect.scourge", "Arrow of Scourge");

        // Entities
        EnUsEntityProvider.CONSUMER.accept(builder);

        // Items
        EnUsItemProvider.CONSUMER.accept(builder);

        // Sounds
        EnUsSoundEventProvider.CONSUMER.accept(builder);

        // Jukebox Sounds
        builder.add("jukebox_song.avp.alien_music_1", "Rotch Gwylt - Silver Smile");

        // Advancements
        EnUsAdvancementProvider.CONSUMER.accept(builder);

        // Hive boss bars — per-variant title key, format must match HiveLocationBossBar.VARIANT_TITLE_KEYS.
        for (var alienVariant : AlienVariant.values()) {
            var prefix = switch (alienVariant) {
                case ABERRANT -> "Aberrant ";
                case IRRADIATED -> "Irradiated ";
                case NETHER -> "Nether ";
                case NORMAL -> "";
            };
            var translationKey = "bossbar.avp.hive." + alienVariant.name().toLowerCase(Locale.US) + ".title";
            builder.add(translationKey, prefix + "Hive");
        }

        // Configs
        EnUsConfigProvider.CONSUMER.accept(builder);

        // Tags
        EnUsBiomeTagProvider.CONSUMER.accept(builder);
        EnUsBlockTagProvider.CONSUMER.accept(builder);
        EnUsDamageTypeTagProvider.CONSUMER.accept(builder);
        EnUsEntityTypeTagProvider.CONSUMER.accept(builder);
        EnUsItemTagProvider.CONSUMER.accept(builder);
        EnUsMobEffectTagProvider.CONSUMER.accept(builder);
    }
}
