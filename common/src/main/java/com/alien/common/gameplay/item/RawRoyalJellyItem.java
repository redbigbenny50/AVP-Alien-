package com.alien.common.gameplay.item;

import com.alien.common.data.AlienAdvancements;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * The queen's tonic. Eating it is a genuine boost - Speed II, Jump Boost II and Regeneration II for thirty seconds -
 * and in the fiction that is exactly the trap: it works, so people keep taking it. Living on the stuff is what makes
 * you sick, so the toll is the shared window in {@link JellyConsumption}, not the first dose.
 * <p>
 * Right-clicking an ovomorph with this still promotes the egg to its royal form. Entity interaction resolves before
 * item use, so the two behaviours cannot collide.
 * <p>
 * {@code alwaysEdible} is required rather than cosmetic: the jelly COSTS hunger instead of restoring it, and vanilla
 * refuses to let a player eat ordinary food on a full bar.
 */
public class RawRoyalJellyItem extends Item {

    public static final FoodProperties FOOD = new FoodProperties.Builder()
        .nutrition(0)
        .saturationModifier(0.0F)
        .alwaysEdible()
        .build();

    public RawRoyalJellyItem() {
        super(new Properties().food(FOOD));
    }

    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity eater) {
        var result = super.finishUsingItem(stack, level, eater);

        if (level.isClientSide) {
            return result;
        }

        eater.addEffect(effect(MobEffects.MOVEMENT_SPEED));
        eater.addEffect(effect(MobEffects.JUMP));
        eater.addEffect(effect(MobEffects.REGENERATION));

        JellyConsumption.consume(eater);

        var serverPlayer = JellyConsumption.serverPlayerOrNull(eater);
        if (serverPlayer != null) {
            AlienAdvancements.EAT_RAW_ROYAL_JELLY.grant(serverPlayer);
        }

        return result;
    }

    private static MobEffectInstance effect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> holder) {
        return new MobEffectInstance(holder, JellyConsumption.BUFF_DURATION_TICKS, JellyConsumption.BUFF_AMPLIFIER);
    }
}
