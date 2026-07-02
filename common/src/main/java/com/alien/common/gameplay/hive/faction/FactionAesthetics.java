package com.alien.common.gameplay.hive.faction;

import com.alien.AlienResources;
import com.alien.common.model.alien.variant.AlienVariant;
import com.blib.api.common.faction.v1.ClaimMapStyle;
import com.blib.api.common.faction.v1.ClaimVisibility;
import com.blib.api.common.faction.v1.Faction;
import com.blib.api.common.faction.v1.ProtectionMode;

/**
 * Standardized protection / flags / color defaults for hive BLib factions. Apply once at first-creation time — admin
 * overrides via the BLib faction UI must stick across reloads.
 * <p>
 * Color scheme: hue by variant (normal=green, aberrant=yellow, nether=red, irradiated=cyan), brightness by tier
 * (variant=darkest, lineage=mid, location=brightest).
 */
public final class FactionAesthetics {

    private FactionAesthetics() {}

    public enum Tier {
        VARIANT,
        LINEAGE,
        LOCATION
    }

    /** Sets visibility, protection modes, flags, and color on a newly-minted faction. */
    public static void applyDefaults(Faction<?> faction, AlienVariant variant, Tier tier) {
        faction.setClaimVisibility(ClaimVisibility.PUBLIC);
        faction.setBlockBreakProtection(ProtectionMode.PUBLIC);
        faction.setBlockInteractProtection(ProtectionMode.PUBLIC);
        faction.setEntityInteractProtection(ProtectionMode.PUBLIC);
        faction.setNonLivingEntityAttackProtection(ProtectionMode.PUBLIC);
        faction.setAllowPvp(true);
        faction.setAllowExplosions(true);
        faction.setAllowMobGriefing(true);
        faction.setColor(colorFor(variant, tier));
        faction.setClaimMapStyle(new ClaimMapStyle(resinVeinTextureFor(variant)));
    }

    /** Backfills the map overlay for old factions without replacing an existing/admin-set style. */
    public static void ensureClaimMapStyle(Faction<?> faction, AlienVariant variant) {
        var currentStyle = faction.claimMapStyle();

        if (currentStyle != null && currentStyle.hasOverlayTexture()) {
            return;
        }

        faction.setClaimMapStyle(new ClaimMapStyle(resinVeinTextureFor(variant)));
    }

    public static int colorFor(AlienVariant variant, Tier tier) {
        return switch (variant) {
            case NORMAL -> switch (tier) {
                case VARIANT -> 0x1F4F1F;
                case LINEAGE -> 0x3FAF3F;
                case LOCATION -> 0x55FF55;
            };
            case ABERRANT -> switch (tier) {
                case VARIANT -> 0x665C00;
                case LINEAGE -> 0xCCB400;
                case LOCATION -> 0xFFFF55;
            };
            case NETHER -> switch (tier) {
                case VARIANT -> 0x661A1A;
                case LINEAGE -> 0xB22222;
                case LOCATION -> 0xFF5555;
            };
            case IRRADIATED -> switch (tier) {
                case VARIANT -> 0x006666;
                case LINEAGE -> 0x00AAAA;
                case LOCATION -> 0x55FFFF;
            };
        };
    }

    private static net.minecraft.resources.ResourceLocation resinVeinTextureFor(AlienVariant variant) {
        return switch (variant) {
            case NORMAL -> AlienResources.blockTextureLocation("resin_vein");
            case ABERRANT -> AlienResources.blockTextureLocation("aberrant_resin_vein");
            case NETHER -> AlienResources.blockTextureLocation("nether_resin_vein");
            case IRRADIATED -> AlienResources.blockTextureLocation("irradiated_resin_vein");
        };
    }
}
