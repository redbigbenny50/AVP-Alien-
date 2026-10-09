package com.alien.common.util;

/**
 * A damage amount that kills anything, without breaking the entity that receives it.
 * <h2>🚨🚨 Why Float.MAX_VALUE must never be passed to hurt()</h2> Vanilla's damage pipeline MULTIPLIES and SUBTRACTS
 * the amount on its way through - armour absorption, magic absorption, then absorption hearts. Starting from
 * {@code Float.MAX_VALUE}, those steps reach {@code Infinity}, and {@code Infinity - Infinity} is {@code NaN}.
 * <h2>⚠⚠ NaN health does not die</h2> {@code LivingEntity.setHealth} clamps with {@code Mth.clamp}, which compares -
 * and EVERY comparison against NaN is false, so the clamp passes NaN through untouched. {@code isDeadOrDying()} then
 * tests {@code health <= 0}, which is also false for NaN. The result is an entity showing empty hearts that is alive
 * and cannot be killed.
 * <p>
 * Reported on a player hit by the ravager's execute: all health gone, no death. The same pattern was present in the
 * queen and empress finishing blows, the nuke kill sweeps and the generic self-kill path.
 * </p>
 * <h2>⭐ Why this value</h2> Armour caps at 80% reduction, so 10,000 still lands over 2,000 damage on a fully armoured,
 * fully enchanted target - lethal many times over - while every intermediate step stays a real, finite number.
 * <p>
 * ⚠ Use this for DAMAGE only. {@code Float.MAX_VALUE} is still correct for healing calls such as
 * {@code healLimbDamage}, which have no such pipeline.
 * </p>
 */
public final class LethalDamage {

    /** Lethal against anything, finite at every step of the damage pipeline. */
    public static final float AMOUNT = 10_000F;

    private LethalDamage() {
        throw new UnsupportedOperationException();
    }
}
