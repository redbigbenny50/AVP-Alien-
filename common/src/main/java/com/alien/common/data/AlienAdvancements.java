package com.alien.common.data;

import com.alien.Alien;
import com.blib.api.common.advancement.v1.BLibAdvancement;

public class AlienAdvancements {

    public static final BLibAdvancement BLOCK_SPITTER_SPIT_WITH_HEAD_SHIELD = create("block_spitter_spit_with_head_shield");

    public static final BLibAdvancement EAT_RAW_ROYAL_JELLY = create("eat_raw_royal_jelly");

    public static final BLibAdvancement EAT_RAW_SCOURGE_JELLY = create("eat_raw_scourge_jelly");

    public static final BLibAdvancement EAT_POISON_JELLY = create("eat_poison_jelly");

    public static final BLibAdvancement EAT_EVERY_JELLY = create("eat_every_jelly");

    public static final BLibAdvancement EAT_RAW_IRRADIATED_JELLY = create("eat_raw_irradiated_jelly");

    public static final BLibAdvancement KILL_A_HARBINGER = create("kill_a_harbinger");

    public static final BLibAdvancement KILL_A_HIVE = create("kill_a_hive");

    public static final BLibAdvancement WITHSTAND_ATTACK_PARTY = create("withstand_attack_party");

    public static final BLibAdvancement KILL_A_LINEAGE = create("kill_a_lineage");

    public static final BLibAdvancement KILL_A_ROYAL_ALIEN = create("kill_a_royal_alien");

    public static final BLibAdvancement KILL_ALL_ALIENS = create("kill_all_aliens");

    public static final BLibAdvancement KILL_ALL_ABERRANT_ALIENS = create("kill_all_aberrant_aliens");

    public static final BLibAdvancement KILL_ALL_IRRADIATED_ALIENS = create("kill_all_irradiated_aliens");

    public static final BLibAdvancement KILL_ALL_NETHER_ALIENS = create("kill_all_nether_aliens");

    public static final BLibAdvancement KILL_ALL_NORMAL_ALIENS = create("kill_all_normal_aliens");

    public static final BLibAdvancement KILL_AN_ALIEN = create("kill_an_alien");

    public static final BLibAdvancement KILL_AN_EMPRESS = create("kill_an_empress");

    /** Killing an EXILED empress on her remnant - see EmpressExileService. Granted from code, not a kill criterion. */
    public static final BLibAdvancement BROKEN_THRONE = create("broken_throne");

    public static final BLibAdvancement LEAD_RAID_TO_ENEMY_HIVE = create("lead_raid_to_enemy_hive");

    public static final BLibAdvancement DEFEAT_A_RAID = create("defeat_a_raid");

    public static final BLibAdvancement DUAL_VARIANT_RAIDS = create("dual_variant_raids");

    public static final BLibAdvancement REMOVE_EMBRYO_WITH_CHORUS_FRUIT = create("remove_embryo_with_chorus_fruit");

    public static final BLibAdvancement ROOT = create("root");

    public static final BLibAdvancement SHEAR_AN_OVOMORPH = create("shear_an_ovomorph");

    public static final BLibAdvancement WEAR_CHITIN_ARMOR = create("chitin_armor");

    public static final BLibAdvancement WEAR_PLATED_CHITIN_ARMOR = create("plated_chitin_armor");

    private static BLibAdvancement create(String path) {
        return new BLibAdvancement(Alien.MOD_ID, "aliens", path);
    }
}
