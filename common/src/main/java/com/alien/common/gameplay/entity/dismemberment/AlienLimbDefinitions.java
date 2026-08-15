package com.alien.common.gameplay.entity.dismemberment;

import com.alien.common.registry.init.AlienEntityTypes;
import com.blib.api.common.dismemberment.v1.LimbCategories;
import com.blib.api.common.dismemberment.v1.LimbDefinition;

/** Aggregates AVP-Alien server-side limb registration. */
public final class AlienLimbDefinitions {

    public static void initialize() {
        registerXenomorphSpawnOffsets();
        registerExplosiveDeathLimbs();
        registerPraetorianLimbs();
        registerDroneHitboxes();
        AdultXenomorphHitboxCatalog.registerRuntimeHitboxes();
    }

    private static void registerXenomorphSpawnOffsets() {
        AdultXenomorphHitboxCatalog.registerSpawnOffsets();
    }

    /**
     * Boilers and Bursters are intentionally not firearm limb targets. Their acid-death burst still needs visual limb
     * definitions, however, because
     * {@link com.blib.api.common.dismemberment.v1.LimbDismemberer#getRemainingDefinitions} uses that registry to launch
     * each piece when they explode.
     */
    private static void registerExplosiveDeathLimbs() {
        registerExplosiveDeathLimbs(AlienEntityTypes.BOILER, "boiler");
        registerExplosiveDeathLimbs(AlienEntityTypes.ABERRANT_BOILER, "boiler");
        registerExplosiveDeathLimbs(AlienEntityTypes.NETHER_BOILER, "boiler");
        registerExplosiveDeathLimbs(AlienEntityTypes.BURSTER, "burster");
        registerExplosiveDeathLimbs(AlienEntityTypes.ABERRANT_BURSTER, "burster");
        registerExplosiveDeathLimbs(AlienEntityTypes.IRRADIATED_BURSTER, "burster");
        registerExplosiveDeathLimbs(AlienEntityTypes.NETHER_BURSTER, "burster");
    }

    private static void registerExplosiveDeathLimbs(
        com.blib.api.common.registry.v1.BLibHolder<? extends net.minecraft.world.entity.EntityType<?>> type,
        String prefix
    ) {
        LimbDefinition.builder(type, AdultXenomorphHitboxCatalog.limbId(prefix, "head"), "gHead", LimbCategories.HEAD).build();
        LimbDefinition.builder(type, AdultXenomorphHitboxCatalog.limbId(prefix, "left_arm"), "gLeftShoulder", LimbCategories.ARM).build();
        LimbDefinition.builder(type, AdultXenomorphHitboxCatalog.limbId(prefix, "right_arm"), "gRightShoulder", LimbCategories.ARM).build();
        LimbDefinition.builder(type, AdultXenomorphHitboxCatalog.limbId(prefix, "left_leg"), "gLeftLeg", LimbCategories.LEG).build();
        LimbDefinition.builder(type, AdultXenomorphHitboxCatalog.limbId(prefix, "right_leg"), "gRightLeg", LimbCategories.LEG).build();
        LimbDefinition.builder(type, AdultXenomorphHitboxCatalog.limbId(prefix, "tail"), "gTail1", LimbCategories.TAIL).build();
    }

    private static void registerPraetorianLimbs() {
        registerPraetorianLimbs(AlienEntityTypes.PRAETORIAN);
        registerPraetorianLimbs(AlienEntityTypes.ABERRANT_PRAETORIAN);
        registerPraetorianLimbs(AlienEntityTypes.IRRADIATED_PRAETORIAN);
        registerPraetorianLimbs(AlienEntityTypes.NETHER_PRAETORIAN);
        PraetorianLimbHitboxes.register(
            AlienEntityTypes.PRAETORIAN.getResourceLocation(),
            AlienEntityTypes.ABERRANT_PRAETORIAN.getResourceLocation(),
            AlienEntityTypes.IRRADIATED_PRAETORIAN.getResourceLocation(),
            AlienEntityTypes.NETHER_PRAETORIAN.getResourceLocation()
        );
    }

    private static void registerDroneHitboxes() {
        DroneLimbHitboxes.register(
            AlienEntityTypes.DRONE.getResourceLocation(),
            AlienEntityTypes.ABERRANT_DRONE.getResourceLocation(),
            AlienEntityTypes.IRRADIATED_DRONE.getResourceLocation(),
            AlienEntityTypes.NETHER_DRONE.getResourceLocation()
        );
    }

    private static void registerPraetorianLimbs(
        com.blib.api.common.registry.v1.BLibHolder<? extends net.minecraft.world.entity.EntityType<?>> type
    ) {
        LimbDefinition.builder(type, PraetorianLimbHitboxes.LEFT_ARM, "gLeftShoulder", LimbCategories.ARM).build();
        LimbDefinition.builder(type, PraetorianLimbHitboxes.RIGHT_ARM, "gRightShoulder", LimbCategories.ARM).build();
        LimbDefinition.builder(type, PraetorianLimbHitboxes.LEFT_LEG, "gLeftLeg", LimbCategories.LEG).build();
        LimbDefinition.builder(type, PraetorianLimbHitboxes.RIGHT_LEG, "gRightLeg", LimbCategories.LEG).build();
        LimbDefinition.builder(type, PraetorianLimbHitboxes.TAIL, "gTail1", LimbCategories.TAIL).build();
    }

    private AlienLimbDefinitions() {}
}
