package com.alien.common.gameplay.hive.war;

import com.alien.Alien;
import com.alien.common.gameplay.hive.dimension.EndStyleHiveRules;
import com.alien.common.gameplay.hive.economy.CasteResolver;
import com.alien.common.gameplay.hive.economy.CastePopulation;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.List;

/**
 * The empress's answer to a rival empire declaring on hers.
 * <p>
 * [stated] "If the war is between two different empress hive territories then the empress will bolster the members of
 * their respective hives to 50% member cap for all castes if possible." A hive that spent the peace at a quarter
 * strength does not have to fight there - the crown pays the difference the moment the war opens, once, to BOTH
 * empires. That symmetry is deliberate: this is the empress tier showing up, not an advantage handed to whoever
 * contested first.
 * <p>
 * The bolster lands in the RESERVE BANK rather than on the ground - the war's own mobilisation decides when bodies
 * come out of the vents, and dumping fifty xenomorphs into a chamber at once is exactly the lag spike he ruled out.
 */
public final class EmpressWarBolster {

    /** [stated] "bolster the members of their respective hives to 50% member cap". */
    private static final double TARGET_CAP_FRACTION = 0.5D;

    /**
     * Grants are spread across the castes in this order, one at a time, so "for all castes" means a balanced army
     * rather than fifty drones. Elites sit at the front because an empire at war leads with its guard.
     */
    private static final List<TagKey<EntityType<?>>> SPREAD = List.of(
        AlienEntityTypeTags.PRAETORIANS,
        AlienEntityTypeTags.WARRIORS,
        AlienEntityTypeTags.CRUSHERS,
        AlienEntityTypeTags.PROWLERS,
        AlienEntityTypeTags.SPITTERS,
        AlienEntityTypeTags.RUNNERS,
        AlienEntityTypeTags.DRONES
    );

    /** Hard ceiling on one bolster, whatever the arithmetic says. A safety rail, not a design number. */
    private static final int MAX_GRANTED = 240;

    private EmpressWarBolster() {}

    /**
     * Tops {@code location} up to half its population cap if — and only if — both sides answer to an empress, and to
     * DIFFERENT ones. Two hives of the same empire never reach here; they are allied and never contest.
     */
    public static void tryBolster(ServerLevel level, HiveLocation location, HiveLocation enemy) {
        // END-STYLE: the End bank is the player's hand-fed property and the apex economy does not reach it. A crown
        // cannot post 240 members into a fortress the player stocked by hand.
        if (EndStyleHiveRules.forbidsApexEconomy(level)) {
            return;
        }
        var ourEmpress = empressOf(location);
        var theirEmpress = empressOf(enemy);
        if (ourEmpress == null || theirEmpress == null || ourEmpress.equals(theirEmpress)) {
            return; // not an empire war - the ordinary hive economy fights this one on its own numbers
        }

        var variant = location.lineageVariantOrNull();
        if (variant == null) {
            return;
        }

        int cap = HiveLocationRegistry.INSTANCE.config().populationPerChunk() * location.claimedChunks().size();
        int target = (int) Math.floor(cap * TARGET_CAP_FRACTION);
        int current = CastePopulation.totalTrackedPopulation(location);
        int shortfall = Math.min(target - current, MAX_GRANTED);
        if (shortfall <= 0) {
            return; // already at half strength or better - the empress spends nothing
        }

        int granted = 0;
        int index = 0;
        while (granted < shortfall) {
            var caste = SPREAD.get(index % SPREAD.size());
            index++;
            var type = CasteResolver.entityTypeForCaste(variant, caste);
            if (type != null && location.localReserves().addBrood(type, 1)) {
                granted++;
            } else if (index % SPREAD.size() == 0 && granted == 0) {
                break; // a full pass produced nothing this variant can field - stop rather than spin
            }
        }

        if (granted > 0) {
            // Registry-driven mutation: mark it, or an unloaded hive's grant is never written to disk.
            var faction = Alien.MOD.factions().get(location.lineageFactionId());
            if (faction != null && faction.data() instanceof com.alien.common.gameplay.hive.faction.LineageFactionData lineage) {
                lineage.markDirty();
            }
            Alien.LOGGER.info(
                "Empress war: bolstered hive {} with {} member(s) toward half its cap of {} ({} -> {}).",
                location.id(),
                granted,
                cap,
                current,
                current + granted
            );
        }
    }

    /** The empress this hive answers to, or null when its lineage has none. */
    private static java.util.UUID empressOf(HiveLocation location) {
        var faction = Alien.MOD.factions().get(location.lineageFactionId());
        if (
            faction == null
                || !(faction.data() instanceof com.alien.common.gameplay.hive.faction.LineageFactionData lineage)
        ) {
            return null;
        }
        return lineage.empressId();
    }
}
