package com.alien.common.gameplay.hive.economy;

import com.alien.Alien;
import com.alien.common.gameplay.hive.growth.BiomassIncome;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.alien.common.gameplay.hive.id.HiveLocationIds;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Hive economy event hooks. The mixin into {@link net.minecraft.world.entity.LivingEntity#die} routes every entity
 * death here; we filter to xenomorph→non-xenomorph kills and credit the killer's home location.
 */
public final class HiveBiomassEvents {

    private HiveBiomassEvents() {}

    /**
     * Awards {@code config.loadedBiomassPerNonAlienKill()} biomass to the killer's home location when a xenomorph kills
     * a non-xenomorph entity. No-op for any other kill scenario.
     */
    public static void onLivingEntityDeath(LivingEntity victim, Entity rawKiller) {
        if (victim.getType().is(AlienEntityTypeTags.XENOMORPHS)) {
            return;
        }
        if (!(rawKiller instanceof LivingEntity killer)) {
            return;
        }
        if (!killer.getType().is(AlienEntityTypeTags.XENOMORPHS)) {
            return;
        }

        var homeLocation = findHomeLocation(killer);
        if (homeLocation == null) {
            return;
        }

        var config = HiveLocationRegistry.INSTANCE.config();
        var amount = config.loadedBiomassPerNonAlienKill();
        if (amount <= 0) {
            return;
        }

        // Oct 8: never lowers a pool banked while unloaded.
        BiomassIncome.addUpToCap(homeLocation, amount, BiomassIncome.biomassCap(homeLocation, config));
    }

    private static com.alien.common.gameplay.hive.location.HiveLocation findHomeLocation(LivingEntity killer) {
        for (var factionId : Alien.MOD.factions().getFactionIds(killer.getUUID())) {
            if (!HiveLocationIds.isHiveLocationId(factionId)) {
                continue;
            }
            var location = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(factionId));
            if (location != null && location.isAlive()) {
                return location;
            }
        }
        return null;
    }
}
