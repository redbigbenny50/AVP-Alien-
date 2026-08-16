package com.alien.common.registry.tag;

import com.alien.AlienResources;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public class AlienItemTags {

    public static final TagKey<Item> ABERRANT_CHITIN_ARMOR = create("aberrant_chitin_armor");

    public static final TagKey<Item> ACID_IMMUNE = create("acid_immune");

    /** Every strain's resin ball. Lets the field manual recipe accept any variant. */
    public static final TagKey<Item> RESIN_BALLS = create("resin_balls");

    public static final TagKey<Item> CHITIN_ARMORS = create("chitin_armors");

    public static final TagKey<Item> FACEHUGGER_RESISTANT_HELMETS = create("facehugger_resistant_helmets");

    public static final TagKey<Item> IRRADIATED_CHITIN_ARMOR = create("irradiated_chitin_armor");

    public static final TagKey<Item> NETHER_CHITIN_ARMOR = create("nether_chitin_armor");

    public static final TagKey<Item> NORMAL_CHITIN_ARMOR = create("normal_chitin_armor");

    public static final TagKey<Item> PLATED_ABERRANT_CHITIN_ARMOR = create("plated_aberrant_chitin_armor");

    public static final TagKey<Item> PLATED_CHITIN_ARMORS = create("plated_chitin_armors");

    public static final TagKey<Item> PLATED_IRRADIATED_CHITIN_ARMOR = create("plated_irradiated_chitin_armor");

    public static final TagKey<Item> PLATED_NETHER_CHITIN_ARMOR = create("plated_nether_chitin_armor");

    public static final TagKey<Item> PLATED_NORMAL_CHITIN_ARMOR = create("plated_normal_chitin_armor");

    private static TagKey<Item> create(String name) {
        return TagKey.create(Registries.ITEM, AlienResources.location(name));
    }
}
