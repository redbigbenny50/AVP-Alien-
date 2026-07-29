package com.alien.common.gameplay.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * The irradiated strain's own trick, lent out: everything you strike is dosed with radiation.
 * <p>
 * This is exactly what an irradiated xenomorph does on every landed hit - see {@code Alien.doHurtTarget} - so a player
 * under it fights like one of them. It is a marker only; the work happens in {@code MixinLivingEntity_GlowingTalons},
 * because a {@link MobEffect} gets no callback when its holder hits something.
 * <p>
 * Dosing goes through the same bridge the aliens use, so it lands on AVP: Human's shared exposure counter when that mod
 * is present and on {@link RadiationSicknessStatusEffect} when it is not. Fellow aliens are never dosed - the species
 * is radiation-immune by decree - which means this cannot be used to poison a hive from the inside.
 */
public class GlowingTalonsStatusEffect extends MobEffect {

    /** The sickly glow of something that has been in the hot zone too long. */
    private static final int IRRADIATED_GREEN = 0x7FD41C;

    public GlowingTalonsStatusEffect() {
        super(MobEffectCategory.BENEFICIAL, IRRADIATED_GREEN);
    }
}
