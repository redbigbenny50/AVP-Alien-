package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg_laying;

import com.alien.common.gameplay.entity.living.alien.HiveManager;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.compatibility.avp_human.GeneManagerProxy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public interface EggLayer {

    // Do NOT declare vanilla LivingEntity/Entity methods (isAlive, level, getRandom, ...) on this
    // interface. They are satisfied only by inheritance from the Minecraft entity, whose names are
    // remapped in the production jar (e.g. isAlive -> method_5805), which leaves the interface method
    // abstract at runtime -> AbstractMethodError. Reach them through asEntity() instead
    // (e.g. asEntity().isAlive(), asEntity().level(), asEntity().getRandom()).
    Entity asEntity();

    AlienVariant getVariant();

    HiveManager getHiveManager();

    GeneManagerProxy getGeneManager();

    boolean isEggLayCooldownReady();

    void resetEggLayCooldown();

    boolean hasOvipositor();

    Vec3 getEggLayingPosition();

    /**
     * Whether this layer is a pacified CAPTIVE breeder (an inhibited queen on her chained eggsack). Default false; only
     * {@code Queen} overrides it. A captive breeder has no hauling drones, so her lay spot must be treated as a single
     * occupied slot - she waits for the egg to be moved instead of stacking, and banks only a tiny reserve. (Safe as an
     * interface method: isInhibited is NOT a remapped vanilla entity method, unlike isAlive/level/etc.)
     */
    default boolean isInhibited() {
        return false;
    }
}
