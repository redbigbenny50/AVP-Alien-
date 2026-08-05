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

    private static TagKey<DamageType> create(String path) {
        return Alien.MOD.resources().createTagKey(Registries.DAMAGE_TYPE, path);
    }
}
