package com.alien.fabric.data.tag;

import com.alien.common.registry.init.AlienMobEffects;
import com.alien.common.registry.tag.AlienMobEffectTags;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

import java.util.concurrent.CompletableFuture;

public class AlienMobEffectTagProvider extends FabricTagProvider<MobEffect> {

    public AlienMobEffectTagProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, Registries.MOB_EFFECT, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider wrapperLookup) {
        getOrCreateTagBuilder(AlienMobEffectTags.DOES_NOT_AFFECT_ALIENS)
            .add(
                MobEffects.BLINDNESS.value(),
                MobEffects.CONFUSION.value(),
                MobEffects.DARKNESS.value(),
                MobEffects.DIG_SLOWDOWN.value(),
                MobEffects.HARM.value(),
                MobEffects.INFESTED.value(),
                MobEffects.MOVEMENT_SLOWDOWN.value(),
                MobEffects.POISON.value(),
                MobEffects.WEAKNESS.value(),
                MobEffects.WITHER.value()
            );

        // The host's own pregnancy state, which must not become a permanent trait of what crawls out of it.
        getOrCreateTagBuilder(AlienMobEffectTags.NOT_INHERITED_BY_EMBRYO)
            .add(
                // Rung IV deals wither-TYPE damage on a loop with no health floor. Inherited forever, it left the
                // burster in a permanent damage stall - never dying, bleeding acid the whole time.
                AlienMobEffects.getJellySicknessHolder().value(),
                // The potion that triggered the birth. Inherited forever, it sat on the newborn as a permanent effect.
                AlienMobEffects.getMetamorphosisHolder().value(),
                AlienMobEffects.getGrowthSuppressionHolder().value(),
                // Would send the newborn straight up the scourge ladder on a parent's dose.
                AlienMobEffects.getScourgeHolder().value(),
                // Unbounded poison, and blood loss shaves max health with no end to restore it.
                AlienMobEffects.getHivesBaneHolder().value(),
                AlienMobEffects.getBloodLossHolder().value()
            );
    }
}
