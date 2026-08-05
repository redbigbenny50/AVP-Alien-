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
        var head = prefix.equals("queen")
            ? template.fatalLimb(limb(prefix, "head"), LimbCategories.HEAD)
            // Firearm headshots are bonus damage only; Xenomorph heads are never detachable.
            : template.limb(limb(prefix, "head"), LimbCategories.HEAD);
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
