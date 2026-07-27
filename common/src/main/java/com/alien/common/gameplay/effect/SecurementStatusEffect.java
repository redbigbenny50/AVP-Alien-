package com.alien.common.gameplay.effect;

import com.alien.AlienResources;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Planted. Nothing shoves you: while this is up, knockback of every kind is negated - melee, explosions, a crusher's
 * charge, a spitter's gob.
 * <p>
 * There is no vanilla effect for this, which is why it exists. It works by pushing
 * {@link Attributes#KNOCKBACK_RESISTANCE} to 1.0, the same 0-1 scale vanilla armour trims uses, so it stacks with
 * nothing and needs no tick logic - the attribute system does the work and cleans itself up when the effect ends.
 * <p>
 * Deliberately untiered: knockback immunity is binary, so an amplifier would have nothing to say.
 */
public class SecurementStatusEffect extends MobEffect {

    private static final int ANCHOR_GREY_COLOR = 0x6E7B8B;

    public SecurementStatusEffect() {
        super(MobEffectCategory.BENEFICIAL, ANCHOR_GREY_COLOR);
        addAttributeModifier(
            Attributes.KNOCKBACK_RESISTANCE,
            AlienResources.location("securement_knockback_resistance"),
            1.0,
            AttributeModifier.Operation.ADD_VALUE
        );
    }
}
