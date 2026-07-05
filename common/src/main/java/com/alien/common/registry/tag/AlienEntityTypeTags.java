package com.alien.common.registry.tag;

import com.alien.AlienResources;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public class AlienEntityTypeTags {

    public static final TagKey<EntityType<?>> ABERRANT_ALIENS = create("aberrant_aliens");

    public static final TagKey<EntityType<?>> ACID_IMMUNE = create("acid_immune");

    public static final TagKey<EntityType<?>> ADOLESCENTS = create("adolescents");

    public static final TagKey<EntityType<?>> ALIENS = create("aliens");

    /** Mobs the capture chain refuses to grab or chain — bosses and the like. Datapack-overridable. */
    public static final TagKey<EntityType<?>> CAPTURE_CHAIN_BLACKLIST = create("capture_chain_blacklist");

    public static final TagKey<EntityType<?>> ANSWERS_XENOMORPH_CRIES_FOR_HELP = create("answers_xenomorph_cries_for_help");

    public static final TagKey<EntityType<?>> CHESTBURSTERS = create("chestbursters");

    public static final TagKey<EntityType<?>> CARRIERS = create("carriers");

    public static final TagKey<EntityType<?>> CHRYSALISES = create("chrysalises");

    public static final TagKey<EntityType<?>> CRUSHERS = create("crushers");

    public static final TagKey<EntityType<?>> DRONES = create("drones");

    public static final TagKey<EntityType<?>> EMPRESSES = create("empresses");

    public static final TagKey<EntityType<?>> FACEHUGGERS = create("facehuggers");

    public static final TagKey<EntityType<?>> HARBINGERS = create("harbingers");

    public static final TagKey<EntityType<?>> HATED_BY_XENOMORPHS = create("hated_by_xenomorphs");

    public static final TagKey<EntityType<?>> HIVE_ALIENS = create("hive_aliens");

    public static final TagKey<EntityType<?>> HOSTS = create("hosts");

    public static final TagKey<EntityType<?>> IGNORED_BY_XENOMORPHS = create("ignored_by_xenomorphs");

    public static final TagKey<EntityType<?>> XENOMORPH_THREAT_1_PASSIVE = create("xenomorph_threat_1_passive");

    public static final TagKey<EntityType<?>> XENOMORPH_THREAT_2_LOW_DANGER = create("xenomorph_threat_2_low_danger");

    public static final TagKey<EntityType<?>> XENOMORPH_THREAT_3_HIGH_DANGER = create("xenomorph_threat_3_high_danger");

    public static final TagKey<EntityType<?>> IRRADIATED_ALIENS = create("irradiated_aliens");

    public static final TagKey<EntityType<?>> NETHER_ALIENS = create("nether_aliens");

    public static final TagKey<EntityType<?>> NORMAL_ALIENS = create("normal_aliens");

    public static final TagKey<EntityType<?>> OVOMORPHS = create("ovomorphs");

    public static final TagKey<EntityType<?>> PARASITES = create("parasites");

    public static final TagKey<EntityType<?>> PRAETORIANS = create("praetorians");

    public static final TagKey<EntityType<?>> PREDALIEN_ADOLESCENTS = create("predalien_adolescents");

    public static final TagKey<EntityType<?>> PREDALIEN_CHESTBURSTERS = create("predalien_chestbursters");

    public static final TagKey<EntityType<?>> PREDALIENS = create("predaliens");

    public static final TagKey<EntityType<?>> PROWLERS = create("prowlers");

    public static final TagKey<EntityType<?>> QUEENS = create("queens");

    public static final TagKey<EntityType<?>> RAVAGERS = create("ravagers");

    public static final TagKey<EntityType<?>> RAZOR_CLAWS = create("razor_claws");

    public static final TagKey<EntityType<?>> ROYAL_ALIENS = create("royal_aliens");

    public static final TagKey<EntityType<?>> ROYAL_XENOMORPHS = create("royal_xenomorphs");

    public static final TagKey<EntityType<?>> BURSTERS = create("bursters");

    public static final TagKey<EntityType<?>> RUNNER_HOSTS = create("runner_hosts");

    public static final TagKey<EntityType<?>> RUNNERS = create("runners");

    public static final TagKey<EntityType<?>> SCOURGE_ALIENS = create("scourge_aliens");

    public static final TagKey<EntityType<?>> SPAWNS_IN_HIVE_DRONE_LAYER = create("spawns_in_hive_drone_layer");

    public static final TagKey<EntityType<?>> SPAWNS_IN_HIVE_PRAETORIAN_LAYER = create("spawns_in_hive_praetorian_layer");

    public static final TagKey<EntityType<?>> SPAWNS_IN_HIVE_QUEEN_LAYER = create("spawns_in_hive_queen_layer");

    public static final TagKey<EntityType<?>> SPAWNS_IN_HIVE_WARRIOR_LAYER = create("spawns_in_hive_warrior_layer");

    public static final TagKey<EntityType<?>> SPITTERS = create("spitters");

    public static final TagKey<EntityType<?>> WARRIORS = create("warriors");

    public static final TagKey<EntityType<?>> XENOMORPHS = create("xenomorphs");

    private static TagKey<EntityType<?>> create(String name) {
        return TagKey.create(Registries.ENTITY_TYPE, AlienResources.location(name));
    }
}
