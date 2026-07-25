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

    /**
     * The strain mirror folders under {@code hive/} that hold full copies of the piece set. The NORMAL set lives at
     * the root ({@code hive/<type>/...}); each strain here has an identical tree at {@code hive/<strain>/<type>/...}.
     * IRRADIATED has no set yet and falls back to the normal pieces until its folder exists (then add it here).
     */
    private static final List<String> STRAIN_FOLDER_PREFIXES = List.of("aberrant/", "nether/");

    /**
     * The strain folder prefix for a lineage variant: {@code ""} (normal set) for normal, irradiated (no set yet),
     * and unknown/null variants; {@code "aberrant/"} / {@code "nether/"} for their mirror sets.
     */
    public static String strainFolderPrefix(
        @org.jetbrains.annotations.Nullable com.alien.common.model.alien.variant.AlienVariant variant
    ) {
        if (variant == null) {
            return "";
        }
        var type = com.alien.common.data.AlienVariantTypes.getFor(variant);
        if (type == com.alien.common.data.AlienVariantTypes.ABERRANT) {
            return "aberrant/";
        }
        if (type == com.alien.common.data.AlienVariantTypes.NETHER) {
            return "nether/";
        }
        return "";
    }

    /** The founding queen chamber for a lineage variant (the strain's own core piece). */
    public static ResourceLocation queenChamber(
        @org.jetbrains.annotations.Nullable com.alien.common.model.alien.variant.AlienVariant variant
    ) {
        var prefix = strainFolderPrefix(variant);
        return prefix.isEmpty() ? QUEEN_CHAMBER : withStrainFolder(QUEEN_CHAMBER, prefix);
    }

    /** True for ANY strain's queen chamber - the founding seed must never be selected as a growth piece. */
    public static boolean isQueenChamber(ResourceLocation id) {
        return id.getPath().endsWith("core/queen_chamber_3x3");
    }

    /** True when the piece id belongs to the given lineage variant's strain set (normal set for null/no-set strains). */
    public static boolean belongsToStrain(
        ResourceLocation id,
        @org.jetbrains.annotations.Nullable com.alien.common.model.alien.variant.AlienVariant variant
    ) {
        var rest = id.getPath().substring("hive/".length());
        var owner = "";
        for (var prefix : STRAIN_FOLDER_PREFIXES) {
            if (rest.startsWith(prefix)) {
                owner = prefix;
                break;
            }
        }
        return owner.equals(strainFolderPrefix(variant));
    }

    private static ResourceLocation withStrainFolder(ResourceLocation normalId, String prefix) {
        var rest = normalId.getPath().substring("hive/".length());
        return ResourceLocation.fromNamespaceAndPath(Alien.MOD_ID, "hive/" + prefix + rest);
    }

    /** Every hive piece id the loader should parse at startup: the normal set plus every strain mirror set. */
    public static List<ResourceLocation> all() {
        var normal = new java.util.ArrayList<ResourceLocation>();
        normal.add(QUEEN_CHAMBER);
        normal.addAll(HALLWAYS);
        normal.addAll(ROYAL_HALLWAYS);
        normal.addAll(HUBS);
        normal.addAll(CHAMBERS);

        var all = new java.util.ArrayList<>(normal);
        for (var prefix : STRAIN_FOLDER_PREFIXES) {
            for (var id : normal) {
                all.add(withStrainFolder(id, prefix));
            }
        }
        return List.copyOf(all);
    }

    private static ResourceLocation hive(String path) {
        return ResourceLocation.fromNamespaceAndPath(Alien.MOD_ID, "hive/" + path);
    }
}
