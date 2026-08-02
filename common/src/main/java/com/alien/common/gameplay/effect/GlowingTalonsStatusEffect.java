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
    /**
     * The irradiated jelly's own ice blue, sampled from raw_irradiated_jelly.png (body tone #2ADBFE). The potion line
     * must read as the jelly it is brewed from ([stated] "the irradiated potions are green they should be that ice blue
     * color the jelly is"); both irradiation-potion effects share the color, so the blended bottle color IS this color.
     * Radiation SICKNESS stays toxic green on purpose - it is the harm, not the potion.
     */
    private static final int JELLY_ICE_BLUE = 0x2ADBFE;

    public GlowingTalonsStatusEffect() {
        super(MobEffectCategory.BENEFICIAL, JELLY_ICE_BLUE);
    }
}
