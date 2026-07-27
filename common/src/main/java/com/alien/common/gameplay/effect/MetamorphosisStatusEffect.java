package com.alien.common.gameplay.effect;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.model.alien.Host;
import com.alien.common.util.AlienEmbryoUtil;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * The on/off growth accelerant (deliberately untiered - see {@code AlienPotions}). Beyond its long-standing role as a
 * growth-stage requirement, applying it now does two things at the moment it lands:
 * <ul>
 * <li>On a xenomorph: clears the growth-suppression flag set by the Growth Suppression potion, resuming molting and
 * growth - and if the alien is still growing into its full size (the phased molt cycle with the dark molt-skin overlay:
 * a fresh adult normally works through its phases one by one), every remaining phase collapses at once via
 * {@code MoltingManager#skipToFullMaturity()}. One solid phase, not three broken ones: potion + undersized queen =
 * full-size queen. A fully grown alien is untouched here - the effect instead satisfies the growth-stage requirement as
 * it always has, sending a full-grown drone into its molt toward warrior, and so on up the ladder.</li>
 * <li>On a host carrying a chestburster: slams the gestation clock to the burst threshold - the chest-bursting phase
 * begins immediately. The accelerant accelerates; be careful what you drink.</li>
 * <li>On a baby left permanently young by the Growth Suppression potion: releases it, and it grows up on the spot.
 * Exactly what the effect already does for a suppressed xenomorph, one rung down the ladder - the accelerant undoes the
 * suppressant, whatever it was holding back.</li>
 * </ul>
 * <h2>Xeno vision</h2> On a PLAYER the effect also grants borrowed hive senses: every living thing within 24 blocks is
 * outlined through walls for as long as the effect lasts. That lives entirely on the client, in
 * {@code MixinEntity_XenoVision} - nothing is applied to the mobs and nothing is synced, so the sense belongs to the
 * drinker alone. See that class for why.
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

            // Accelerate any in-progress size growth: all remaining molt phases complete in one step. No-ops for
            // aliens without a molting profile or already at full size, so the data (molting_profiles) decides scope.
            if (!alien.getMoltingManager().hasReachedTargetScale()) {
                alien.getMoltingManager().skipToFullMaturity();
            }

            return;
        }

        if (livingEntity instanceof Host host && host.getEmbryoType().isSome()) {
            host.setEmbryoGrowthTimeInTicks(AlienEmbryoUtil.BURST_TIME_IN_TICKS);
            return;
        }

        // An arrested baby, recognised by the same threshold that pinned it. setAge(0) crosses the age boundary, so
        // vanilla clears the baby flag and fires ageBoundaryReached for us: it grows up, it does not merely resume
        // ageing. An ordinary baby is untouched - there is nothing being held back to release.
        if (
            livingEntity instanceof AgeableMob ageable
                && ageable.getAge() <= GrowthSuppressionStatusEffect.ARRESTED_BABY_THRESHOLD
        ) {
            ageable.setAge(0);
        }
    }
}
