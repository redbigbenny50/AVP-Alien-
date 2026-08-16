package com.alien.fabric.data.dismemberment;

import com.alien.AlienResources;
import com.blib.api.common.dismemberment.v1.LimbCategories;
import com.blib.api.common.dismemberment.v1.datagen.LimbDefinitionDataProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.resources.ResourceLocation;

public final class AlienLimbDefinitionDataProvider extends LimbDefinitionDataProvider {

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
        // Every caste's head is fatal, not just the queen's. The old rule rested on the assumption in the comment it
        // replaced -- that xenomorph heads are never detachable and a headshot is only bonus damage -- which the
        // ravager's dismemberment attack has since made untrue. Taking a xenomorph's head off left it fighting on.
        var head = template.fatalLimb(limb(prefix, "head"), LimbCategories.HEAD);

        head
            .limb(limb(prefix, "left_arm"), LimbCategories.ARM)
            .limb(limb(prefix, "right_arm"), LimbCategories.ARM)
            .limb(limb(prefix, "left_leg"), LimbCategories.LEG)
            .limb(limb(prefix, "right_leg"), LimbCategories.LEG)
            .limb(limb(prefix, "tail"), LimbCategories.TAIL);

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
