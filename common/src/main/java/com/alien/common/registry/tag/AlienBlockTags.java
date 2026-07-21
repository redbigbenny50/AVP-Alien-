package com.alien.common.registry.tag;

import com.alien.AlienResources;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class AlienBlockTags {

    public static final TagKey<Block> ABERRANT_CHITIN = create("aberrant_chitin");

    public static final TagKey<Block> ABERRANT_RESIN = create("aberrant_resin");

    public static final TagKey<Block> ABERRANT_RESIN_REPLACEABLE = create("aberrant_resin_replaceable");

    public static final TagKey<Block> ACID_IMMUNE = create("acid_immune");

    public static final TagKey<Block> CHITIN = create("chitin");

    public static final TagKey<Block> IRRADIATED_ACID_IMMUNE = create("irradiated_acid_immune");

    public static final TagKey<Block> IRRADIATED_CHITIN = create("irradiated_chitin");

    public static final TagKey<Block> IRRADIATED_RESIN = create("irradiated_resin");

    public static final TagKey<Block> IRRADIATED_RESIN_REPLACEABLE = create("irradiated_resin_replaceable");

    public static final TagKey<Block> NETHER_ACID_IMMUNE = create("nether_acid_immune");

    public static final TagKey<Block> NETHER_CHITIN = create("nether_chitin");

    public static final TagKey<Block> NETHER_RESIN = create("nether_resin");

    public static final TagKey<Block> NETHER_RESIN_REPLACEABLE = create("nether_resin_replaceable");

    public static final TagKey<Block> NORMAL_CHITIN = create("normal_chitin");

    public static final TagKey<Block> NORMAL_RESIN = create("normal_resin");

    public static final TagKey<Block> NORMAL_RESIN_REPLACEABLE = create("normal_resin_replaceable");

    public static final TagKey<Block> RESIN = create("resin");

    public static final TagKey<Block> RESIN_BLOCKS = create("resin_blocks");

    public static final TagKey<Block> RESIN_NODES = create("resin_nodes");

    public static final TagKey<Block> RESIN_REPLACEABLE = create("resin_replaceable");

    public static final TagKey<Block> RESIN_VEINS = create("resin_veins");

    public static final TagKey<Block> RESIN_VENTS = create("resin_vents");

    public static final TagKey<Block> RESIN_WEBS = create("resin_webs");

    public static final TagKey<Block> XENOMORPH_FRENZY_BREAKABLE = create("xenomorph_frenzy_breakable");

    public static final TagKey<Block> XENOMORPH_IMMUNE = create("xenomorph_immune");

    public static final TagKey<Block> HUMAN_RAZOR_WIRE = TagKey.create(
        Registries.BLOCK,
        ResourceLocation.fromNamespaceAndPath("avp_human", "razor_wire")
    );

    private static TagKey<Block> create(String name) {
        return TagKey.create(Registries.BLOCK, AlienResources.location(name));
    }
}
