package com.alien.common.registry.tag;

import com.alien.Alien;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;

public class AlienDamageTypesTags {

    public static final TagKey<DamageType> ACID = create("acid");

    public static final TagKey<DamageType> DOES_NOT_HURT_ALIENS = create("does_not_hurt_aliens");

    /** Radiation-class damage. Ignored by every alien except the aberrant strain - see AlienMobEffectTags.RADIATION. */
    public static final TagKey<DamageType> RADIATION = create("radiation");

    /** The space mods' "no air" damage. Every strain and prop ignores it - see StrainHazardImmunity. */
    public static final TagKey<DamageType> SUFFOCATION = create("suffocation");

    /** Vanilla freeze, which is what Ad Astra's extreme cold deals. Every strain but nether ignores it. */
    public static final TagKey<DamageType> EXTREME_COLD = create("extreme_cold");

    /** Ad Astra acid rain. Every strain ignores it. */
    public static final TagKey<DamageType> ACID_RAIN = create("acid_rain");

    /** Ad Astra cryo fuel contact. Only the irradiated strain ignores it. */
    public static final TagKey<DamageType> CRYO_FUEL = create("cryo_fuel");

    /** Ad Astra rocket exhaust. Only the nether strain ignores it. */
    public static final TagKey<DamageType> ROCKET_FLAMES = create("rocket_flames");

    private static TagKey<DamageType> create(String path) {
        return Alien.MOD.resources().createTagKey(Registries.DAMAGE_TYPE, path);
    }
}
