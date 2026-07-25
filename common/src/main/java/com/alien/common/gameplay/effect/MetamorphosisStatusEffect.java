package com.alien.common.gameplay.effect;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.model.alien.Host;
import com.alien.common.util.AlienEmbryoUtil;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * The on/off growth accelerant (deliberately untiered - see {@code AlienPotions}). Beyond its long-standing role as a
 * growth-stage requirement, applying it now does two things at the moment it lands:
 * <ul>
 * <li>On a xenomorph: clears the growth-suppression flag set by the Growth Suppression potion, resuming molting and
 * growth.</li>
 * <li>On a host carrying a chestburster: slams the gestation clock to the burst threshold - the chest-bursting phase
 * begins immediately. The accelerant accelerates; be careful what you drink.</li>
 * </ul>
 */
public class MetamorphosisStatusEffect extends MobEffect {

    private static final int GREEN_PARTICLE_COLOR = 0x8BF200;

    public MetamorphosisStatusEffect() {
        super(MobEffectCategory.NEUTRAL, GREEN_PARTICLE_COLOR);
    }

    @Override
    public void onEffectStarted(@NotNull LivingEntity livingEntity, int amplifier) {
        if (livingEntity.level().isClientSide) {
            return;
        }

        if (livingEntity instanceof Alien alien) {
            alien.setPoisoned(false);
            return;
        }

        if (livingEntity instanceof Host host && host.getEmbryoType().isSome()) {
            host.setEmbryoGrowthTimeInTicks(AlienEmbryoUtil.BURST_TIME_IN_TICKS);
        }
    }
}
