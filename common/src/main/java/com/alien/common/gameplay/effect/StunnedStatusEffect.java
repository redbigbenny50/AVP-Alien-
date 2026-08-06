package com.alien.common.gameplay.effect;

import com.alien.AlienResources;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Prevents a living entity from moving or running AI while the effect is active. */
public class StunnedStatusEffect extends MobEffect {

    private static final int DARK_GRAY_PARTICLE_COLOR = 0x4A4A4A;

    public StunnedStatusEffect() {
        super(MobEffectCategory.HARMFUL, DARK_GRAY_PARTICLE_COLOR);
        addAttributeModifier(
            Attributes.MOVEMENT_SPEED,
            AlienResources.location("stunned_movement_speed"),
            -1.0D,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
    }
}
