package com.alien.fabric.data.damage_type;

import com.alien.common.registry.key.AlienDamageTypeKeys;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.damagesource.DamageType;

public class DamageTypeBootstrapper {

    public static void bootstrap(BootstrapContext<DamageType> registry) {
        registry.register(AlienDamageTypeKeys.ACID, new DamageType("acid", 0.1F));
        registry.register(AlienDamageTypeKeys.ACID_SPIT, new DamageType("acid_spit", 0.1F));
        registry.register(AlienDamageTypeKeys.CHESTBURSTING, new DamageType("chestbursting", 0.1F));
        registry.register(AlienDamageTypeKeys.RADIATION_SICKNESS, new DamageType("radiation_sickness", 0.1F));
        registry.register(AlienDamageTypeKeys.RAVAGER_CLAW, new DamageType("ravager_claw", 0.1F));
        registry.register(AlienDamageTypeKeys.RAVAGER_SPECIAL, new DamageType("ravager_special", 0.1F));
        registry.register(AlienDamageTypeKeys.SMOTHERING, new DamageType("smothering", 0.1F));
    }
}
