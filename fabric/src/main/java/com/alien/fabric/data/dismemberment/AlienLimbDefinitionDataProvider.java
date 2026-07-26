package com.alien.fabric.data.dismemberment;

import com.alien.AlienResources;
import com.blib.api.common.dismemberment.v1.LimbCategories;
import com.blib.api.common.dismemberment.v1.datagen.LimbDefinitionDataProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public final class AlienLimbDefinitionDataProvider extends LimbDefinitionDataProvider {

    private static final Set<String> GUN_DISMEMBERMENT_GROUPS = Set.of(
        "drone",
        "warrior",
        "runner",
        "spitter",
        "prowler",
        "crusher",
        "praetorian",
        "predalien",
        "queen"
    );

    public AlienLimbDefinitionDataProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    protected void generate() {
        for (var group : AlienXenomorphLimbGroups.ALL) {
            xenomorph(group);
        }
    }

    private void xenomorph(AlienXenomorphLimbGroups.Group group) {
        var prefix = group.prefix();
        var parent = templateId(prefix);
        var template = template(parent);
        if (GUN_DISMEMBERMENT_GROUPS.contains(prefix)) {
            template
                .limb(
                    limb(prefix, "head"),
                    LimbCategories.HEAD,
                    builder -> builder.fatal().hitVolume(-0.38, 0.74, -0.38, 0.38, 1.08, 0.42)
                )
                .limb(
                    limb(prefix, "left_arm"),
                    LimbCategories.ARM,
                    builder -> builder.hitVolume(-0.82, 0.40, -0.34, -0.23, 0.82, 0.36)
                )
                .limb(
                    limb(prefix, "right_arm"),
                    LimbCategories.ARM,
                    builder -> builder.hitVolume(0.23, 0.40, -0.34, 0.82, 0.82, 0.36)
                )
                .limb(
                    limb(prefix, "left_leg"),
                    LimbCategories.LEG,
                    builder -> builder.hitVolume(-0.46, 0.0, -0.32, -0.015, 0.47, 0.34)
                )
                .limb(
                    limb(prefix, "right_leg"),
                    LimbCategories.LEG,
                    builder -> builder.hitVolume(0.015, 0.0, -0.32, 0.46, 0.47, 0.34)
                )
                .limb(
                    limb(prefix, "tail"),
                    LimbCategories.TAIL,
                    builder -> builder
                        .hitVolume(-0.30, 0.28, -0.95, 0.30, 0.62, -0.18)
                        .hitVolume(-0.22, 0.18, -1.55, 0.22, 0.52, -0.88)
                );
        } else {
            template
                .fatalLimb(limb(prefix, "head"), LimbCategories.HEAD)
                .limb(limb(prefix, "left_arm"), LimbCategories.ARM)
                .limb(limb(prefix, "right_arm"), LimbCategories.ARM)
                .limb(limb(prefix, "left_leg"), LimbCategories.LEG)
                .limb(limb(prefix, "right_leg"), LimbCategories.LEG)
                .limb(limb(prefix, "tail"), LimbCategories.TAIL);
        }

        for (var entityType : group.entityTypes()) {
            entity(entityType).parent(parent);
        }
    }

    private static ResourceLocation templateId(String prefix) {
        return AlienResources.location(prefix + "_template");
    }

    private static ResourceLocation limb(String prefix, String suffix) {
        return AlienResources.location(prefix + "_" + suffix);
    }
}
