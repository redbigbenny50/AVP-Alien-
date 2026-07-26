package com.alien.common.gameplay.entity.dismemberment;

import com.alien.common.registry.init.AlienEntityTypes;

/**
 * Aggregates AVP-Alien limb definition registrations. Vanilla mob limb defs (zombie/skeleton/cow/wolf/etc.) are
 * provided by BLib's {@code BuiltInLimbDefinitions} and registered automatically during BLib's mod init — this class
 * only has to wire up the non-serializable spawn offsets for our custom xenomorph limb ids. Structural and visual limb
 * data is generated to JSON by the Fabric datagen providers.
 */
public final class AlienLimbDefinitions {

    public static void initialize() {
        registerXenomorphs();
    }

    private static void registerXenomorphs() {
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
        XenomorphLimbs.registerGunModelCollision(
            "drone",
            "drone",
            AlienEntityTypes.DRONE,
            AlienEntityTypes.ABERRANT_DRONE,
            AlienEntityTypes.IRRADIATED_DRONE,
            AlienEntityTypes.NETHER_DRONE
        );
        XenomorphLimbs.registerGunModelCollision(
            "warrior",
            "warrior",
            AlienEntityTypes.WARRIOR,
            AlienEntityTypes.ABERRANT_WARRIOR,
            AlienEntityTypes.IRRADIATED_WARRIOR,
            AlienEntityTypes.NETHER_WARRIOR
        );
        XenomorphLimbs.registerGunModelCollision(
            "runner",
            "runner",
            AlienEntityTypes.RUNNER,
            AlienEntityTypes.ABERRANT_RUNNER,
            AlienEntityTypes.IRRADIATED_RUNNER,
            AlienEntityTypes.NETHER_RUNNER
        );
        XenomorphLimbs.registerGunModelCollision(
            "spitter",
            "spitter",
            AlienEntityTypes.SPITTER,
            AlienEntityTypes.ABERRANT_SPITTER,
            AlienEntityTypes.NETHER_SPITTER
        );
        XenomorphLimbs.registerGunModelCollision(
            "prowler",
            "prowler",
            AlienEntityTypes.PROWLER,
            AlienEntityTypes.ABERRANT_PROWLER,
            AlienEntityTypes.IRRADIATED_PROWLER,
            AlienEntityTypes.NETHER_PROWLER
        );
        XenomorphLimbs.registerGunModelCollision(
            "crusher",
            "crusher",
            AlienEntityTypes.CRUSHER,
            AlienEntityTypes.ABERRANT_CRUSHER,
            AlienEntityTypes.IRRADIATED_CRUSHER,
            AlienEntityTypes.NETHER_CRUSHER
        );
        XenomorphLimbs.registerGunModelCollision(
            "praetorian",
            "praetorian",
            AlienEntityTypes.PRAETORIAN,
            AlienEntityTypes.ABERRANT_PRAETORIAN,
            AlienEntityTypes.IRRADIATED_PRAETORIAN,
            AlienEntityTypes.NETHER_PRAETORIAN
        );
        XenomorphLimbs.registerGunModelCollision(
            "predalien",
            "predalien",
            AlienEntityTypes.PREDALIEN,
            AlienEntityTypes.ABERRANT_PREDALIEN,
            AlienEntityTypes.IRRADIATED_PREDALIEN,
            AlienEntityTypes.NETHER_PREDALIEN
        );
        XenomorphLimbs.registerGunModelCollision(
            "queen",
            "queen",
            AlienEntityTypes.QUEEN,
            AlienEntityTypes.ABERRANT_QUEEN,
            AlienEntityTypes.IRRADIATED_QUEEN,
            AlienEntityTypes.NETHER_QUEEN
        );
    }

    private AlienLimbDefinitions() {}
}
