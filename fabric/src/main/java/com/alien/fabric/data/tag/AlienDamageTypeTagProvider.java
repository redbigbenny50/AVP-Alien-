package com.alien.fabric.data.tag;

import com.alien.common.registry.key.AlienDamageTypeKeys;
import com.alien.common.registry.tag.AlienDamageTypesTags;
import com.blib.api.common.tag.v1.BLibDamageTypeTags;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;

import java.util.concurrent.CompletableFuture;

public class AlienDamageTypeTagProvider extends FabricTagProvider<DamageType> {

    public AlienDamageTypeTagProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, Registries.DAMAGE_TYPE, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider wrapperLookup) {
        getOrCreateTagBuilder(DamageTypeTags.BYPASSES_ARMOR)
            .add(AlienDamageTypeKeys.CHESTBURSTING);

        // Ravager attacks rip through armor but should still be blockable by a raised shield. Vanilla's
        // bypasses_armor implies bypasses_shield (it's listed as a sub-tag value in the vanilla
        // bypasses_shield JSON), so we use BLib's bypasses_armor_only tag — which has the armor-skipping
        // behavior wired through MixinLivingEntity_BypassesArmorOnly without the shield-bypass inheritance.
        getOrCreateTagBuilder(BLibDamageTypeTags.BYPASSES_ARMOR_ONLY)
            .add(
                AlienDamageTypeKeys.RAVAGER_CLAW,
                AlienDamageTypeKeys.RAVAGER_SPECIAL
            );

        getOrCreateTagBuilder(DamageTypeTags.BYPASSES_ENCHANTMENTS)
            .add(
                AlienDamageTypeKeys.CHESTBURSTING
            );

        getOrCreateTagBuilder(DamageTypeTags.BYPASSES_RESISTANCE)
            .add(
                AlienDamageTypeKeys.CHESTBURSTING
            );

        getOrCreateTagBuilder(DamageTypeTags.BYPASSES_SHIELD)
            .add(
                AlienDamageTypeKeys.CHESTBURSTING
            );

        getOrCreateTagBuilder(DamageTypeTags.BYPASSES_WOLF_ARMOR)
            .add(
                AlienDamageTypeKeys.CHESTBURSTING
            );

        getOrCreateTagBuilder(AlienDamageTypesTags.ACID)
            .add(
                AlienDamageTypeKeys.ACID,
                AlienDamageTypeKeys.ACID_SPIT
            );

        getOrCreateTagBuilder(DamageTypeTags.NO_KNOCKBACK)
            .addTag(AlienDamageTypesTags.ACID)
            .add(
                AlienDamageTypeKeys.CHESTBURSTING,
                AlienDamageTypeKeys.SMOTHERING
            );

        getOrCreateTagBuilder(DamageTypeTags.IS_PROJECTILE)
            .add(
                AlienDamageTypeKeys.ACID_SPIT
            );

        getOrCreateTagBuilder(AlienDamageTypesTags.DOES_NOT_HURT_ALIENS)
            .addTag(AlienDamageTypesTags.ACID)
            .add(
                DamageTypes.DROWN,
                DamageTypes.FREEZE,
                DamageTypes.IN_WALL
            );
    }
}
