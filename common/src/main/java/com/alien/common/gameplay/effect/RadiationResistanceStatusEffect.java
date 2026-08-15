package com.alien.common.gameplay.effect;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.util.AlienIrradiationUtil;
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
    /**
     * The irradiated jelly's own ice blue, sampled from raw_irradiated_jelly.png (body tone #2ADBFE). The potion line
     * must read as the jelly it is brewed from ([stated] "the irradiated potions are green they should be that ice blue
     * color the jelly is"); both irradiation-potion effects share the color, so the blended bottle color IS this color.
     * Radiation SICKNESS stays toxic green on purpose - it is the harm, not the potion.
     */
    private static final int JELLY_ICE_BLUE = 0x2ADBFE;

    public RadiationResistanceStatusEffect() {
        super(MobEffectCategory.BENEFICIAL, JELLY_ICE_BLUE);
    }

    @Override
    public void onEffectStarted(@NotNull LivingEntity livingEntity, int amplifier) {
        if (livingEntity.level().isClientSide || !(livingEntity instanceof Alien alien)) {
            return;
        }

        AlienIrradiationUtil.irradiate(alien);
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
