package com.alien.common.gameplay.hive.faction;

import com.alien.Alien;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.registry.init.AlienFactionDataTypes;

/**
 * Creates and backfills the BLib faction that represents a single hive location.
 */
public final class HiveLocationFactionProvisioner {

    private HiveLocationFactionProvisioner() {}

    public static void ensure(HiveLocation location, LineageFactionData lineage) {
        var locationFactionId = location.id().value();
        var locationFaction = Alien.MOD.factions().get(locationFactionId);

        if (locationFaction == null) {
            locationFaction = Alien.MOD.factions().getOrCreate(locationFactionId, AlienFactionDataTypes.LOCATION);
            FactionAesthetics.applyDefaults(locationFaction, lineage.variant(), FactionAesthetics.Tier.LOCATION);
        } else {
            FactionAesthetics.ensureClaimMapStyle(locationFaction, lineage.variant());
        }

        if (location.locationNumber() < 0) {
            location.setLocationNumber(lineage.allocateLocationNumber());
        }

        if (locationFaction.data() instanceof LocationFactionData locationData && locationData.locationId() == null) {
            locationData.setLocationId(location.id());
        }

        locationFaction.setName(
            FactionNaming.forLocation(
                lineage.variant(),
                lineage.lineageNumber(),
                location.locationNumber()
            )
        );
    }
}
