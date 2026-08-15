package com.alien.common.gameplay.entity.living.alien.xenomorph;

import com.blib.api.common.dismemberment.v1.Dismemberable;
import com.blib.api.common.dismemberment.v1.DismembermentManager;
import com.blib.api.common.dismemberment.v1.LimbCategories;
import com.blib.api.common.dismemberment.v1.LimbCategory;
import com.blib.api.common.dismemberment.v1.LimbDefinition;
import com.blib.api.common.dismemberment.v1.LimbDefinitionRegistry;

public enum XenomorphAttackLimbRequirement {

    HEAD {

        @Override
        public boolean isSatisfiedBy(Xenomorph xenomorph) {
            return hasUsableLimb(xenomorph, LimbCategories.HEAD);
        }
    },
    TAIL {

        @Override
        public boolean isSatisfiedBy(Xenomorph xenomorph) {
            return hasUsableLimb(xenomorph, LimbCategories.TAIL);
        }
    },
    /**
     * A SPECIFIC arm, by side - for attacks whose clip is authored for one limb (the harbinger's left/right whipstabs).
     * Rule 2's logic half at per-limb granularity: lose the left whip and the left stab alone goes dark, the right
     * keeps working. Sides are matched on the limb definition id path ("left_arm"/"right_arm", the names
     * AlienLimbDefinitionDataProvider registers for every caste); a caste with no sided arm definition passes,
     * mirroring how the category checks treat an empty definition list.
     */
    LEFT_ARM {

        @Override
        public boolean isSatisfiedBy(Xenomorph xenomorph) {
            return hasUsableSidedArm(xenomorph, "left_arm");
        }
    },
    RIGHT_ARM {

        @Override
        public boolean isSatisfiedBy(Xenomorph xenomorph) {
            return hasUsableSidedArm(xenomorph, "right_arm");
        }
    },
    ANY_ARM {

        @Override
        public boolean isSatisfiedBy(Xenomorph xenomorph) {
            return hasUsableLimb(xenomorph, LimbCategories.ARM);
        }
    },
    BOTH_ARMS {

        @Override
        public boolean isSatisfiedBy(Xenomorph xenomorph) {
            return allLimbsAttached(xenomorph, LimbCategories.ARM);
        }
    },
    ALL_LEGS {

        @Override
        public boolean isSatisfiedBy(Xenomorph xenomorph) {
            return allLimbsAttached(xenomorph, LimbCategories.LEG);
        }
    };

    public abstract boolean isSatisfiedBy(Xenomorph xenomorph);

    private static boolean hasUsableSidedArm(Xenomorph xenomorph, String sideSuffix) {
        var definitions = LimbDefinitionRegistry.getDefinitionsByCategory(xenomorph.getType(), LimbCategories.ARM);

        LimbDefinition sided = null;
        for (var definition : definitions) {
            if (definition.id().getPath().endsWith(sideSuffix)) {
                sided = definition;
                break;
            }
        }
        if (sided == null) {
            return true;
        }

        var manager = getDismembermentManagerOrNull(xenomorph);
        if (manager == null || !manager.hasAnyDetached()) {
            return true;
        }
        return !manager.isDetached(sided);
    }

    private static boolean hasUsableLimb(Xenomorph xenomorph, LimbCategory category) {
        var definitions = LimbDefinitionRegistry.getDefinitionsByCategory(xenomorph.getType(), category);

        if (definitions.isEmpty()) {
            return true;
        }

        var manager = getDismembermentManagerOrNull(xenomorph);

        if (manager == null || !manager.hasAnyDetached()) {
            return true;
        }

        for (var definition : definitions) {
            if (!manager.isDetached(definition)) {
                return true;
            }
        }

        return false;
    }

    private static boolean allLimbsAttached(Xenomorph xenomorph, LimbCategory category) {
        var definitions = LimbDefinitionRegistry.getDefinitionsByCategory(xenomorph.getType(), category);

        if (definitions.isEmpty()) {
            return true;
        }

        var manager = getDismembermentManagerOrNull(xenomorph);

        if (manager == null || !manager.hasAnyDetached()) {
            return true;
        }

        for (var definition : definitions) {
            if (manager.isDetached(definition)) {
                return false;
            }
        }

        return true;
    }

    private static DismembermentManager getDismembermentManagerOrNull(Xenomorph xenomorph) {
        if (!(xenomorph instanceof Dismemberable dismemberable)) {
            return null;
        }

        return dismemberable.getDismembermentManager();
    }
}
