package com.alien.common.gameplay.item;

import com.alien.common.data.AlienAdvancements;
import com.alien.common.registry.init.AlienMobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * What the scourge tier runs on. Where royal jelly makes you quick, this makes you immovable: Strength II, Resistance
 * II and Immovable - total knockback immunity - for thirty seconds. It is the rarer of the two by some distance, since
 * the only source is the harbinger, and a hive is allowed exactly one of those.
 * <p>
 * Same shared sickness window as royal jelly. See {@link JellyConsumption} for why they deliberately share it.
 */
public class RawScourgeJellyItem extends Item {

    public static final FoodProperties FOOD = new FoodProperties.Builder()
        .nutrition(0)
        .saturationModifier(0.0F)
        .alwaysEdible()
        .build();

    public RawScourgeJellyItem() {
        super(new Properties().food(FOOD));
    }

    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity eater) {
        var result = super.finishUsingItem(stack, level, eater);

        if (level.isClientSide) {
            return result;
        }

        eater.addEffect(
            new MobEffectInstance(
                MobEffects.DAMAGE_BOOST,
                JellyConsumption.BUFF_DURATION_TICKS,
                JellyConsumption.BUFF_AMPLIFIER
            )
        );
        eater.addEffect(
            new MobEffectInstance(
                MobEffects.DAMAGE_RESISTANCE,
                JellyConsumption.BUFF_DURATION_TICKS,
                JellyConsumption.BUFF_AMPLIFIER
            )
        );
        // Immovable is untiered - knockback immunity is on or off - so it rides at amplifier 0 while its companions
        // sit at II.
        eater.addEffect(
            new MobEffectInstance(AlienMobEffects.getImmovableHolder(), JellyConsumption.BUFF_DURATION_TICKS, 0)
        );

        JellyConsumption.consume(eater);

        var serverPlayer = JellyConsumption.serverPlayerOrNull(eater);
        if (serverPlayer != null) {
            JellyConsumption.grantJellyAdvancement(serverPlayer, AlienAdvancements.EAT_RAW_SCOURGE_JELLY);
        }

        return result;
    }
}
