package com.alien.common.gameplay.hive.structure;

import com.alien.Alien;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The explicit set of hand-authored hive structure pieces, by resource id. The hive structure system uses a curated,
 * hand-authored piece set (not procedurally discovered), so listing the ids explicitly is deliberate: it keeps the
 * assembler's inputs predictable and prevents an unrelated .nbt from ever being pulled into hive generation.
 * <p>
 * Paths are relative to {@code data/avp_alien/structure/} (the loader prefixes the namespace). When you add or rename a
 * piece .nbt, update the matching list here.
 */
public final class HivePieceCatalog {

    private HivePieceCatalog() {}

    /** The founding seed: the queen's chamber (4 royal-door exits). */
    public static final ResourceLocation QUEEN_CHAMBER = hive("core/queen_chamber_3x3");

    /** Standard hallways (all {@code hive_door}). */
    public static final List<ResourceLocation> HALLWAYS = List.of(
        hive("hallway/hallway_straight_1x1"),
        hive("hallway/hallway_straight_2x1"),
        hive("hallway/hallway_corner_1x1"),
        hive("hallway/hallway_tee_1x1"),
        hive("hallway/hallway_cross_1x1")
    );

    /** Royal hallways a-e (queen-chamber connectors; a & b also carry a jelly door). */
    public static final List<ResourceLocation> ROYAL_HALLWAYS = List.of(
        hive("hallway_royal/hallway_royal_2x1_a"),
        hive("hallway_royal/hallway_royal_2x1_b"),
        hive("hallway_royal/hallway_royal_2x1_c"),
        hive("hallway_royal/hallway_royal_2x1_d"),
        hive("hallway_royal/hallway_royal_2x1_e")
    );

    /** Junction+chamber hubs (3-way / 4-way, all {@code hive_door}). */
    public static final List<ResourceLocation> HUBS = List.of(
        hive("hub/hub_2x2_3way"),
        hive("hub/hub_2x2_4way")
    );

    /** Functional chambers. */
    public static final List<ResourceLocation> CHAMBERS = List.of(
        hive("chamber/chamber_egg_1x1"),
        hive("chamber/chamber_harvest_2x2"),
        hive("chamber/chamber_host_2x2"),
        hive("chamber/chamber_jelly_vault_1x1"),
        hive("chamber/chamber_jelly_royal_1x1"),
        hive("chamber/chamber_raid_2x2"),
        hive("chamber/chamber_scourge_1x1")
    );

    /** Every hive piece id the loader should parse at startup. */
    public static List<ResourceLocation> all() {
        var all = new java.util.ArrayList<ResourceLocation>();
        all.add(QUEEN_CHAMBER);
        all.addAll(HALLWAYS);
        all.addAll(ROYAL_HALLWAYS);
        all.addAll(HUBS);
        all.addAll(CHAMBERS);
        return List.copyOf(all);
    }

    private static ResourceLocation hive(String path) {
        return ResourceLocation.fromNamespaceAndPath(Alien.MOD_ID, "hive/" + path);
    }
}
