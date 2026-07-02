package com.alien.common.gameplay.hive.faction;

import com.alien.Alien;
import com.alien.common.gameplay.hive.id.VariantIds;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienFactionDataTypes;
import com.blib.api.common.faction.v1.Faction;

/**
 * Idempotent accessor for the per-variant faction. Each call returns (or creates on first request) the unique BLib
 * faction identified by {@code avp_alien:variant/<variant_name>}.
 * <p>
 * Variant factions never die, so there is no removal counterpart.
 */
public final class VariantFactionRegistry {

    public static Faction<VariantFactionData> getOrCreate(AlienVariant variant) {
        var id = VariantIds.of(variant);
        var preExisting = Alien.MOD.factions().get(id) != null;
        var faction = Alien.MOD.factions().getOrCreate(id, AlienFactionDataTypes.VARIANT);

        var data = faction.data();
        if (data != null && data.variant() != variant) {
            data.setVariant(variant);
        }

        if (!preExisting) {
            FactionAesthetics.applyDefaults(faction, variant, FactionAesthetics.Tier.VARIANT);
            faction.setName(FactionNaming.forVariant(variant));
        } else {
            FactionAesthetics.ensureClaimMapStyle(faction, variant);
        }

        return faction;
    }

    private VariantFactionRegistry() {}
}
