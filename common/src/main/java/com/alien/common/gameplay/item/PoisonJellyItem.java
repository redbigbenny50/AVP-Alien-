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
 * The jelly you should not eat.
 * <p>
 * Its real job is still brewing: Awkward + Poison Jelly makes the Growth Suppression potion, and the old
 * right-click-a-xeno interaction is deliberately gone - suppressing a xenomorph now requires the potion, mirroring how
 * royal jelly requires Metamorphosis.
 * <p>
 * But it IS edible, and eating it is purely a mistake. Where the other three raws pay you in buffs and charge a
 * sickness afterwards, this one has no upside at all: Harming I on the way down, then Slowness II and Nausea for the
 * same thirty seconds the others spend making you strong - and it still costs the hunger and still feeds the shared
 * jelly-sickness window. It exists so the jelly shelf has one bottle that is only ever the wrong choice.
 * <p>
 * Harming is instantaneous, so it lands once as the bite goes down rather than running alongside the other two. It is
 * applied at rank I - a plain vanilla Potion of Harming, 6 damage - so a healthy player survives being stupid.
 */
public class PoisonJellyItem extends Item {

    public static final FoodProperties FOOD = new FoodProperties.Builder()
        .nutrition(0)
        .saturationModifier(0.0F)
        .alwaysEdible()
        .build();

    /** Rank I - instantaneous, so it costs no HUD slot beside the two that linger. */
    private static final int HARMING_RANK = 0;

    /** Slowness II, matching the tier the other jellies buff at. */
    private static final int SLOWNESS_RANK = 1;

    /** Nausea I - the screen-warp needs no tier to be miserable. */
    private static final int NAUSEA_RANK = 0;

    public PoisonJellyItem() {
        super(new Properties().food(FOOD));
    }

    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity eater) {
        var result = super.finishUsingItem(stack, level, eater);

        if (level.isClientSide) {
            return result;
        }

        eater.addEffect(new MobEffectInstance(MobEffects.HARM, 1, HARMING_RANK));
        eater.addEffect(
            new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, JellyConsumption.BUFF_DURATION_TICKS, SLOWNESS_RANK)
        );
        eater.addEffect(
            new MobEffectInstance(MobEffects.CONFUSION, JellyConsumption.BUFF_DURATION_TICKS, NAUSEA_RANK)
        );

        JellyConsumption.consume(eater);

        var serverPlayer = JellyConsumption.serverPlayerOrNull(eater);
        if (serverPlayer != null) {
            JellyConsumption.grantJellyAdvancement(serverPlayer, AlienAdvancements.EAT_POISON_JELLY);
        }

        return result;
    }
}
