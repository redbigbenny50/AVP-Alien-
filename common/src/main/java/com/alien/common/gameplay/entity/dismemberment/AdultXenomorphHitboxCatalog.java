package com.alien.common.gameplay.entity.dismemberment;

import com.alien.AlienResources;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** Runtime roster for automatic adult-Xenomorph limb-region generation. */
public final class AdultXenomorphHitboxCatalog {

    /** Full detachable arm, leg, and tail regions. Eggs, huggers, chestbursters, and adolescents are excluded. */
    public static final List<String> STANDARD_MODELS = List.of(
        "carrier",
        "chrysalis",
        "crusher",
        "drone",
        "empress",
        "harbinger",
        "praetorian",
        "predalien",
        "prowler",
        "ravager",
        "razor_claw",
        "runner",
        "spitter",
        "warrior",
        "queen"
    );

    /** Headshot-only extensions: no arm, leg, tail, or detachable limb pools. */
    public static final List<String> HEAD_ONLY_MODELS = List.of("burster");

    public static final List<String> QUEEN_EXTRA_REGIONS = List.of("torso");

    public static final List<String> EMPRESS_EXTRA_REGIONS = List.of("torso");

    public static final List<String> HARBINGER_EXTRA_REGIONS = List.of("torso", "left_back_whip", "right_back_whip");

    /**
     * Registers every adult AVP Xenomorph entity id against its authored model. Variant ids use the same geometry as
     * their base form. A harmless registration for a currently unused variant is preferable to silently losing limb
     * targeting when that variant is enabled later.
     */
    static void registerRuntimeHitboxes() {
        for (var model : STANDARD_MODELS) {
            if (model.equals("drone") || model.equals("praetorian")) {
                continue;
            }
            for (var variantPrefix : List.of("", "aberrant_", "irradiated_", "nether_")) {
                StandardXenomorphLimbHitboxes.register(AlienResources.location(variantPrefix + model), model);
            }
        }
        for (var model : HEAD_ONLY_MODELS) {
            for (var variantPrefix : List.of("", "aberrant_", "irradiated_", "nether_")) {
                StandardXenomorphLimbHitboxes.register(AlienResources.location(variantPrefix + model), model);
            }
        }
    }

    static void registerSpawnOffsets() {
        for (var model : STANDARD_MODELS) {
            XenomorphLimbs.registerSpawnOffsets(model);
        }
    }

    public static ResourceLocation limbId(String model, String region) {
        return AlienResources.location(model + "_" + region);
    }

    public static Optional<String> modelForEntityPath(String entityPath) {
        var baseName = entityPath
            .replaceFirst("^aberrant_", "")
            .replaceFirst("^irradiated_", "")
            .replaceFirst("^nether_", "");
        return STANDARD_MODELS.contains(baseName) || HEAD_ONLY_MODELS.contains(baseName) ? Optional.of(baseName) : Optional.empty();
    }

    public static boolean isHeadOnlyModel(String model) {
        return HEAD_ONLY_MODELS.contains(model);
    }

    private AdultXenomorphHitboxCatalog() {}
}
