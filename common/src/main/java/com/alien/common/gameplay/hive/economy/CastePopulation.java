package com.alien.common.gameplay.hive.economy;

import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Snapshot of a location's reliable per-caste population: currently loaded location members + local reserves, summed by
 * entity-type tag. Persisted known members are intentionally excluded because unloaded UUIDs can become stale and
 * should not keep a location alive or block new reserve materialization.
 */
public final class CastePopulation {

    /** All castes the buy task considers when computing deficits. Iterate in this order for stable tiebreaks. */
    public static final TagKey<EntityType<?>>[] TRACKED_CASTES = new TagKey[] {
        AlienEntityTypeTags.QUEENS,
        AlienEntityTypeTags.DRONES,
        AlienEntityTypeTags.RUNNERS,
        AlienEntityTypeTags.WARRIORS,
        AlienEntityTypeTags.PROWLERS,
        AlienEntityTypeTags.PRAETORIANS,
        AlienEntityTypeTags.CRUSHERS,
        AlienEntityTypeTags.RAVAGERS,
        AlienEntityTypeTags.RAZOR_CLAWS,
        AlienEntityTypeTags.BURSTERS,
        AlienEntityTypeTags.CARRIERS,
        AlienEntityTypeTags.CHRYSALISES,
        AlienEntityTypeTags.SPITTERS,
        AlienEntityTypeTags.PREDALIENS,
        AlienEntityTypeTags.HARBINGERS
    };

    private CastePopulation() {}

    /** Per-caste population (loaded location members + reserves). Insertion-ordered for stable iteration. */
    public static Map<TagKey<EntityType<?>>, Integer> popByCaste(HiveLocation location) {
        var counts = new LinkedHashMap<TagKey<EntityType<?>>, Integer>();
        for (var caste : TRACKED_CASTES) {
            counts.put(caste, countCaste(location, caste));
        }
        return counts;
    }

    /** Sum of all tracked-caste counts in the location. Used as the gate against the population cap. */
    public static int totalTrackedPopulation(HiveLocation location) {
        var total = 0;
        for (var caste : TRACKED_CASTES) {
            total += countCaste(location, caste);
        }
        return total;
    }

    /** Count of one caste (loaded location members + reserves) in this location. */
    public static int countCaste(HiveLocation location, TagKey<EntityType<?>> caste) {
        return countLoadedCaste(location, caste) + location.localReserves().getReliableCountMatching(type -> type.is(caste));
    }

    /** Count of one caste from loaded location members only; reserve entries are intentionally excluded. */
    public static int countLoadedCaste(HiveLocation location, TagKey<EntityType<?>> caste) {
        var count = 0;
        for (var entry : location.loadedMembersByType().entrySet()) {
            if (entry.getKey().is(caste)) {
                count += entry.getValue().size();
            }
        }
        return count;
    }

    /** Count of one concrete entity type (loaded location members + reserves) in this location. */
    public static int countEntity(HiveLocation location, EntityType<?> entityType) {
        var loaded = location.loadedMembersByType()
            .getOrDefault(entityType, java.util.Set.of())
            .size();
        return loaded + location.localReserves().getReliableCount(entityType);
    }

    /** Count loaded xenomorphs plus reserve xenomorphs, matching the hive boss-bar source of truth. */
    public static int totalReliableXenomorphPopulation(HiveLocation location) {
        var count = 0;
        for (var entry : location.loadedMembersByType().entrySet()) {
            if (entry.getKey().is(AlienEntityTypeTags.XENOMORPHS)) {
                count += entry.getValue().size();
            }
        }
        return count + location.localReserves().getReliableCountMatching(type -> type.is(AlienEntityTypeTags.XENOMORPHS));
    }
}
