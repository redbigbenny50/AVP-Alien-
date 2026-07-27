package com.alien.common.gameplay.effect;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.registry.init.AlienMobEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * Turns an alien toward the scourge tier. Beyond its long-standing role as a growth-stage requirement, a SECOND dose
 * now steers a molt that is already underway.
 * <h2>Redosing mid-molt</h2> A caste can have two scourge futures - a drone becomes a carrier by default, or a razor
 * claw. The first dose starts the molt toward the default. Dose it again while it is still in the molt-ENTER phase and
 * the cocoon is pointed at the alternate instead, so {@code drone -> potion -> molting -> carrier} and
 * {@code drone -> potion -> molting -> potion -> razor claw} are the same ladder walked differently.
 * <p>
 * This replaced an amplifier gate. Razor claw used to require Scourge II, which meant the tier existed purely to split
 * the drone's two outcomes and made a stronger potion mandatory forever. Steering the molt is both simpler and open-
 * ended: a new scourge form only needs an {@code alternate} on its stage, never a new potion strength.
 * <p>
 * The window closes when the molt-enter phase ends, because that is where the xenomorph is actually replaced by its new
 * caste - see {@code CocoonManager.canRedirectTarget}. A dose after that lands on an alien that has already become
 * something, and does nothing.
 * <h2>On anything that is not an alien</h2> There is no scourge form for a body that was never going to have one, so
 * the change stops at the skin: the victim grows a {@link ChitinousAuraStatusEffect} instead - Thorns V for
 * {@value #CHITINOUS_AURA_DURATION_TICKS} ticks. It lands on players and ordinary mobs alike, so a splash potion arms
 * everything it touches.
 */
public class ScourgeStatusEffect extends MobEffect {

    private static final int RED_PARTICLE_COLOR = 0xB53536;

    /** A full minute, matching the potion that granted it. */
    private static final int CHITINOUS_AURA_DURATION_TICKS = 20 * 60;

    public ScourgeStatusEffect() {
        super(MobEffectCategory.NEUTRAL, RED_PARTICLE_COLOR);
    }

    @Override
    public void onEffectStarted(@NotNull LivingEntity livingEntity, int amplifier) {
        if (livingEntity.level().isClientSide) {
            return;
        }

        if (livingEntity instanceof Xenomorph xenomorph) {
            xenomorph.getCocoonManager().redirectTarget();
            return;
        }

        livingEntity.addEffect(
            new MobEffectInstance(AlienMobEffects.getChitinousAuraHolder(), CHITINOUS_AURA_DURATION_TICKS, 0)
        );
    }
}
