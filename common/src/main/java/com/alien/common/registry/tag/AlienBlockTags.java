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

    /**
     * ⭐⭐ WHAT THE HIVE SALVAGES INTO ITS RESIN CONTAINERS when a carve or a stamp would otherwise delete it.
     * <p>
     * ⚠ IT IS A BLOCK TAG, NOT AN ITEM TAG, because the salvage happens at the moment a BLOCK is cleared - [stated]
     * "its storing the ore blocks themselves not the raw items so it would store say iron ore not raw iron". Reading
     * the block and taking {@code asItem()} sidesteps loot tables, fortune and silk touch entirely.
     * </p>
     * <p>
     * ⚠ ALMOST EVERYTHING HE ASKED FOR IS ALREADY INSIDE {@code c:ores}: ancient debris is
     * {@code c:ores/netherite_scrap}, nether quartz is {@code c:ores/quartz}, and nether gold is inside
     * {@code c:ores/gold}. avp_human populates {@code c:ores} too, so its ores come along for free. Only gilded
     * blackstone is in no ore tag anywhere and has to be named.
     * </p>
     */
    public static final TagKey<Block> HIVE_SALVAGE = create("hive_salvage");

    public static final TagKey<Block> ACID_IMMUNE = create("acid_immune");

    public static final TagKey<Block> CHITIN = create("chitin");

    /**
     * Blocks the HARBINGER's front kick refuses to break. Her kick goes through material ordinary xenomorph digging
     * cannot, so it needs its own - much shorter - blacklist rather than {@link #XENOMORPH_IMMUNE}. Negative-hardness
     * blocks (bedrock, barrier, end portal frame, command blocks) are already unbreakable and need no entry; this tag
     * is for blocks that ARE breakable in principle but should never fall to a mob, such as reinforced deepslate. Add
     * to it by datapack - no code change needed.
     */
    public static final TagKey<Block> HARBINGER_UNBREAKABLE = create("harbinger_unbreakable");

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

    /** Every strain's floor tendril. Chamber furniture (egg beds, jelly vats) only grows on these. */
    public static final TagKey<Block> RESIN_TENDRILS = create("resin_tendrils");

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
