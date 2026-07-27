package com.alien.common.gameplay.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * A skin of hive chitin, grown over whoever drank scourge and had no scourge form to become. Anything that strikes the
 * wearer is likely to tear itself on the spines.
 * <p>
 * Modelled on Thorns V, which is two ranks above anything vanilla will enchant: the vanilla formula is a
 * {@code 0.15 * level} chance to retaliate for 1-4 damage, so level five means a <b>75%</b> chance per hit rather than
 * Thorns III's 45%. The damage range is unchanged - this is a much more reliable bite, not a harder one.
 * <p>
 * The retaliation itself lives in {@code MixinLivingEntity_ChitinousAura}, because a {@link MobEffect} has no hook for
 * "something hit me". This class carries the identity, the red particle colour that warns people it is up, and the
 * numbers the mixin reads.
 * <p>
 * Category is BENEFICIAL: it is armour, however unpleasantly it was acquired, and milk should not be the natural
 * reaction to seeing it.
 */
public class ChitinousAuraStatusEffect extends MobEffect {

    /** Arterial red - the warning that something is coated and will bite back. */
    private static final int CHITIN_RED_COLOR = 0x9B1B1B;

    /** Two ranks past vanilla's ceiling. */
    public static final int THORNS_LEVEL = 5;

    /** Vanilla's thorns roll: {@code 0.15 * level}. At level five that is 0.75. */
    public static final float RETALIATION_CHANCE = 0.15F * THORNS_LEVEL;

    /** Vanilla's thorns damage for any level at or below ten: {@code 1 + random.nextInt(4)}. */
    public static final int RETALIATION_DAMAGE_BOUND = 4;

    public ChitinousAuraStatusEffect() {
        super(MobEffectCategory.BENEFICIAL, CHITIN_RED_COLOR);
    }
}