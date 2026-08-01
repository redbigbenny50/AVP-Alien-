package com.alien.common.gameplay.entity.dismemberment;

/** Aggregates AVP-Alien server-side limb registration. */
public final class AlienLimbDefinitions {

    public static void initialize() {
        registerXenomorphSpawnOffsets();
    }

    private static void registerXenomorphSpawnOffsets() {
        XenomorphLimbs.registerSpawnOffsets("drone");
        XenomorphLimbs.registerSpawnOffsets("warrior");
        XenomorphLimbs.registerSpawnOffsets("runner");
        XenomorphLimbs.registerSpawnOffsets("spitter");
        XenomorphLimbs.registerSpawnOffsets("praetorian");
        XenomorphLimbs.registerSpawnOffsets("crusher");
        XenomorphLimbs.registerSpawnOffsets("boiler");
        XenomorphLimbs.registerSpawnOffsets("razor_claw");
        XenomorphLimbs.registerSpawnOffsets("ravager");
        XenomorphLimbs.registerSpawnOffsets("prowler");
        XenomorphLimbs.registerSpawnOffsets("carrier");
        XenomorphLimbs.registerSpawnOffsets("chrysalis");
        XenomorphLimbs.registerSpawnOffsets("predalien");
        XenomorphLimbs.registerSpawnOffsets("burster");
        XenomorphLimbs.registerSpawnOffsets("empress");
        XenomorphLimbs.registerSpawnOffsets("harbinger");
        XenomorphLimbs.registerSpawnOffsets("queen");
    }

    private AlienLimbDefinitions() {}
}
