package com.alien.fabric.data.tag;

import com.alien.common.registry.key.AlienDamageTypeKeys;
import com.alien.common.registry.tag.AlienDamageTypesTags;
import com.blib.api.common.tag.v1.BLibDamageTypeTags;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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

        // ⚠ FREEZE IS NO LONGER HERE. It was blanket-refused for every alien, which made the nether strain immune to
        // cold too. Sep 22 ruling: "irradiated, normal, and aberrant should be immune to cold and the nether would
        // take the damage." Freeze now goes through EXTREME_COLD and StrainHazardImmunity, per strain.
        getOrCreateTagBuilder(AlienDamageTypesTags.DOES_NOT_HURT_ALIENS)
            .addTag(AlienDamageTypesTags.ACID)
            .add(
                DamageTypes.DROWN,
                DamageTypes.IN_WALL
            );

        addSpaceHazardDamageTypes();
    }

    /**
     * The space mods' hazards as damage-type tags, so StrainHazardImmunity can answer them per strain. Names read from
     * the 1.16.26 Ad Astra and 1.4.25 Stellaris jars; optional, because neither mod is on the datagen classpath and a
     * missing damage type must not break the tag. Ad Astra's heat is vanilla fire and its cold is vanilla freeze, which
     * is why those two are vanilla keys here.
     */
    private void addSpaceHazardDamageTypes() {
        getOrCreateTagBuilder(AlienDamageTypesTags.SUFFOCATION)
            .addOptional(ResourceLocation.fromNamespaceAndPath("ad_astra", "oxygen"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("stellaris", "oxygen"));

        getOrCreateTagBuilder(AlienDamageTypesTags.EXTREME_COLD)
            .add(DamageTypes.FREEZE);

        getOrCreateTagBuilder(AlienDamageTypesTags.ACID_RAIN)
            .addOptional(ResourceLocation.fromNamespaceAndPath("ad_astra", "acid_rain"));

        getOrCreateTagBuilder(AlienDamageTypesTags.CRYO_FUEL)
            .addOptional(ResourceLocation.fromNamespaceAndPath("ad_astra", "cryo_fuel"));

        getOrCreateTagBuilder(AlienDamageTypesTags.ROCKET_FLAMES)
            .addOptional(ResourceLocation.fromNamespaceAndPath("ad_astra", "rocket_flames"));
    }
}
