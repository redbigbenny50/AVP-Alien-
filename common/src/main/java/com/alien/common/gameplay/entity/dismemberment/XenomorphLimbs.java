package com.alien.common.gameplay.entity.dismemberment;

import com.alien.AlienResources;
import com.blib.api.common.dismemberment.v1.SpawnFunctionRegistry;
import net.minecraft.world.phys.Vec3;

/** Server-side spawn offsets shared by xenomorph limb definitions. */
public final class XenomorphLimbs {

    private XenomorphLimbs() {}

    public static void registerSpawnOffsets(String idPrefix) {
        SpawnFunctionRegistry.register(AlienResources.location(idPrefix + "_head"), entity -> new Vec3(0.0, entity.getEyeHeight(), 0.0));
        SpawnFunctionRegistry.register(
            AlienResources.location(idPrefix + "_left_arm"),
            entity -> new Vec3(0.0, entity.getBbHeight() * 0.75, 0.0)
        );
        SpawnFunctionRegistry.register(
            AlienResources.location(idPrefix + "_right_arm"),
            entity -> new Vec3(0.0, entity.getBbHeight() * 0.75, 0.0)
        );
        SpawnFunctionRegistry.register(
            AlienResources.location(idPrefix + "_left_leg"),
            entity -> new Vec3(0.0, entity.getBbHeight() * 0.3, 0.0)
        );
        SpawnFunctionRegistry.register(
            AlienResources.location(idPrefix + "_right_leg"),
            entity -> new Vec3(0.0, entity.getBbHeight() * 0.3, 0.0)
        );
    }
}
