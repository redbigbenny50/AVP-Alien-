package com.alien.common.gameplay.effect;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.key.AlienDamageTypeKeys;
import com.alien.common.util.AlienTransitionUtil;
import com.alien.compatibility.avp_human.RadiationCompat;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * Radiation cannot touch you while this is up - from either system.
 * <p>
 * AVP: Human grants radiation immunity by ENTITY TYPE TAG or a full radiation-resistant armour set, and neither of
 * those can be handed out by a potion: {@code HumanPredicates.canBeIrradiated} never asks what effects you are
 * carrying. So rather than trying to make it answer differently, this holds the exposure counter itself at zero every
 * tick. Their sickness reads its level from that counter, so a counter pinned at zero is immunity by another route -
 * doses still land, they just never accumulate into anything.
 * <p>
 * The fallback sickness is handled at the other end: {@link RadiationSicknessStatusEffect} checks for this effect and
 * stands down, which also stops an already-running sickness dead rather than letting it tick out underneath.
 * <h2>On an alien</h2> Every alien potion reshapes what it lands on - metamorphosis matures, scourge redirects a molt -
 * and this one TRANSMUTES. A normal or nether xenomorph doused in it becomes IRRADIATED, the deliberate version of what
 * the nuked biome does by accident at a 10% roll every three seconds. An ABERRANT one dies: that strain cannot survive
 * radiation at this level, and a splash potion is the same sentence a nuke will be.
 * <p>
 * It is hung on THIS effect rather than {@code GlowingTalonsStatusEffect} purely so it fires exactly once - the potion
 * grants both, and an alien caught by a splash would otherwise be transmuted twice in the same tick.
 */
public class RadiationResistanceStatusEffect extends MobEffect {

    /** The washed-out yellow of a hazard trefoil. */
    private static final int HAZARD_YELLOW = 0xD8C64A;

    public RadiationResistanceStatusEffect() {
        super(MobEffectCategory.BENEFICIAL, HAZARD_YELLOW);
    }

    @Override
    public void onEffectStarted(@NotNull LivingEntity livingEntity, int amplifier) {
        if (livingEntity.level().isClientSide || !(livingEntity instanceof Alien alien)) {
            return;
        }

        switch (alien.getVariant()) {
            case ABERRANT -> alien.hurt(
                alien.damageSources().source(AlienDamageTypeKeys.RADIATION_SICKNESS),
                Float.MAX_VALUE
            );
            case NORMAL, NETHER -> AlienTransitionUtil.transitionIntoVariant(alien, AlienVariant.IRRADIATED);
            // Already irradiated - it is bathing in its own element.
            case IRRADIATED -> {}
        }
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(@NotNull LivingEntity livingEntity, int amplifier) {
        if (!livingEntity.level().isClientSide) {
            RadiationCompat.clearExposure(livingEntity);
        }

        return true;
    }
}
