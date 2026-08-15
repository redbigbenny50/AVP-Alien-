package com.alien.common.gameplay.item;

import com.alien.common.data.AlienAdvancements;
import com.alien.common.gameplay.effect.RadiationSicknessStatusEffect;
import com.alien.common.registry.init.AlienMobEffects;
import com.alien.compatibility.avp_human.AVPHuman;
import com.alien.compatibility.avp_human.RadiationCompat;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * The third jelly, and the one that bites hardest both ways.
 * <p>
 * Royal jelly makes you quick and scourge jelly makes you immovable; this makes you DANGEROUS - Strength IV and Speed
 * IV, two ranks above what either of the others hands out, night vision to see what you are about to hit, and
 * regeneration to keep you standing while you use them.
 * <p>
 * The price is proportional. On top of the usual jelly sickness it dumps a dose of AVPHuman radiation sickness into the
 * eater, which is the worst backlash of any raw jelly by a distance: the other two cost hunger and a short illness,
 * this one poisons you. Anyone eating it is trading their next several minutes for the current fight.
 * <p>
 * The radiation goes through {@link RadiationCompat}, so with AVPHuman absent the buffs land and the backlash simply
 * does not - the item stays usable rather than throwing.
 */
public class RawIrradiatedJellyItem extends Item {

    public static final FoodProperties FOOD = new FoodProperties.Builder()
        .nutrition(0)
        .saturationModifier(0.0F)
        .alwaysEdible()
        .build();

    /** Strength and Speed IV - amplifier 3 reads as rank IV. */
    private static final int BUFF_AMPLIFIER = 3;

    /** Night vision is untiered, so it rides at 0 while its companions sit at IV. */
    private static final int NIGHT_VISION_AMPLIFIER = 0;

    /**
     * Regeneration I - deliberately rank I, not the II royal jelly gives.
     * <p>
     * It very nearly cancels the sickness for as long as the buffs last (eleven damage against twelve healing over the
     * thirty seconds) and then stops dead while the radiation is entering its tightest phase. The jelly hands you a
     * window and presents the bill afterwards, rather than taxing you throughout: six hits land in the last ten seconds
     * with nothing left to offset them, right as Strength and Speed expire too.
     */
    private static final int REGENERATION_AMPLIFIER = 0;

    /**
     * Rank I, and it does NOT climb with repeat doses the way jelly sickness does - eating a second jelly restarts the
     * sickness rather than deepening it.
     */
    private static final int RADIATION_SICKNESS_RANK = 0;

    public RawIrradiatedJellyItem() {
        super(new Properties().food(FOOD));
    }

    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity eater) {
        var result = super.finishUsingItem(stack, level, eater);

        if (level.isClientSide) {
            return result;
        }

        eater.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, JellyConsumption.BUFF_DURATION_TICKS, BUFF_AMPLIFIER));
        eater.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, JellyConsumption.BUFF_DURATION_TICKS, BUFF_AMPLIFIER));
        eater.addEffect(
            new MobEffectInstance(MobEffects.NIGHT_VISION, JellyConsumption.BUFF_DURATION_TICKS, NIGHT_VISION_AMPLIFIER)
        );
        eater.addEffect(
            new MobEffectInstance(MobEffects.REGENERATION, JellyConsumption.BUFF_DURATION_TICKS, REGENERATION_AMPLIFIER)
        );

        // AVP: Human's counter when it is there, our own copy of it when it is not - see
        // RadiationSicknessStatusEffect for why the fallback has to exist at all.
        if (AVPHuman.MOD.isLoaded()) {
            RadiationCompat.addExposure(eater, RadiationCompat.EXPOSURE_PER_SICKNESS_LEVEL);
        } else {
            eater.addEffect(
                new MobEffectInstance(
                    AlienMobEffects.getRadiationSicknessHolder(),
                    RadiationSicknessStatusEffect.JELLY_DOSE_DURATION_TICKS,
                    RADIATION_SICKNESS_RANK
                )
            );
        }

        JellyConsumption.consume(eater);

        var serverPlayer = JellyConsumption.serverPlayerOrNull(eater);
        if (serverPlayer != null) {
            JellyConsumption.grantJellyAdvancement(serverPlayer, AlienAdvancements.EAT_RAW_IRRADIATED_JELLY);
        }

        return result;
    }
}
