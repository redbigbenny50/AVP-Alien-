package com.alien.common.registry.key;

import com.alien.AlienResources;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

public class AlienDamageTypeKeys {

    public static final ResourceKey<DamageType> ACID = create("acid");

    public static final ResourceKey<DamageType> ACID_SPIT = create("acid_spit");

    public static final ResourceKey<DamageType> CHESTBURSTING = create("chestbursting");

    /**
     * Only ever dealt by our FALLBACK radiation sickness, which is inert whenever AVP: Human is installed. Their
     * radiation is a separate type in their own namespace, so the two can never collide.
     */
    public static final ResourceKey<DamageType> RADIATION_SICKNESS = create("radiation_sickness");

    public static final ResourceKey<DamageType> HARBINGER_BACKHAND = create("harbinger_backhand");

    public static final ResourceKey<DamageType> HARBINGER_KICK = create("harbinger_kick");

    public static final ResourceKey<DamageType> HARBINGER_SLAM = create("harbinger_slam");

    public static final ResourceKey<DamageType> RAVAGER_CLAW = create("ravager_claw");

    public static final ResourceKey<DamageType> RAVAGER_SPECIAL = create("ravager_special");

    public static final ResourceKey<DamageType> SMOTHERING = create("smothering");

    private static ResourceKey<DamageType> create(String id) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, AlienResources.location(id));
    }
}
