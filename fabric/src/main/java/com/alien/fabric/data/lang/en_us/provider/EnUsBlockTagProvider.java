package com.alien.fabric.data.lang.en_us.provider;

import com.alien.common.registry.tag.AlienBlockTags;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;

import java.util.function.Consumer;

public class EnUsBlockTagProvider {

    public static final Consumer<FabricLanguageProvider.TranslationBuilder> CONSUMER = builder -> {
        builder.add(AlienBlockTags.ABERRANT_CHITIN, "Aberrant Chitin");
        builder.add(AlienBlockTags.ABERRANT_RESIN, "Aberrant Resin");
        builder.add(AlienBlockTags.ABERRANT_RESIN_REPLACEABLE, "Aberrant Resin Replaceable");
        builder.add(AlienBlockTags.ACID_IMMUNE, "Acid Immune");
        builder.add(AlienBlockTags.CHITIN, "Chitins");
        builder.add(AlienBlockTags.IRRADIATED_ACID_IMMUNE, "Irradiated Acid Immune");
        builder.add(AlienBlockTags.IRRADIATED_CHITIN, "Irradiated Chitin");
        builder.add(AlienBlockTags.IRRADIATED_RESIN, "Irradiated Resin");
        builder.add(AlienBlockTags.IRRADIATED_RESIN_REPLACEABLE, "Irradiated Resin Replaceable");
        builder.add(AlienBlockTags.NETHER_ACID_IMMUNE, "Nether Acid Immune");
        builder.add(AlienBlockTags.NETHER_CHITIN, "Nether Chitin");
        builder.add(AlienBlockTags.NETHER_RESIN, "Nether Resin");
        builder.add(AlienBlockTags.NETHER_RESIN_REPLACEABLE, "Nether Resin Replaceable");
        builder.add(AlienBlockTags.NORMAL_CHITIN, "Chitin");
        builder.add(AlienBlockTags.NORMAL_RESIN, "Resin");
        builder.add(AlienBlockTags.NORMAL_RESIN_REPLACEABLE, "Resin Replaceable");
        builder.add(AlienBlockTags.RESIN, "Resins");
        builder.add(AlienBlockTags.RESIN_BLOCKS, "Resin Blocks");
        builder.add(AlienBlockTags.RESIN_NODES, "Resin Nodes");
        builder.add(AlienBlockTags.RESIN_REPLACEABLE, "Resins Replaceable");
        builder.add(AlienBlockTags.RESIN_VEINS, "Resin Veins");
        builder.add(AlienBlockTags.RESIN_WEBS, "Resin Webs");
        builder.add(AlienBlockTags.XENOMORPH_FRENZY_BREAKABLE, "Xenomorph Frenzy Breakable");
        builder.add(AlienBlockTags.XENOMORPH_IMMUNE, "Xenomorph Immune");
    };
}
