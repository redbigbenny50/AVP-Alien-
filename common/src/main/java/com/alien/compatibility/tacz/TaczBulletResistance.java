package com.alien.compatibility.tacz;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.compatibility.guns.GunDamageParity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/**
 * Timeless and Classics Zero (TACZ) - identification and its parity multiplier. The budget and the floor live in
 * {@link GunDamageParity}, shared with every other supported gun mod.
 * <h2>⚠⚠ THE FACTOR IS DERIVED, NOT GUESSED</h2> Read from TACZ 1.1.8-hotfix-r6's default gun pack
 * ({@code tacz_default_gun/data/tacz/data/guns/*_data.json}) as {@code damage x rpm / 60}:
 *
 * <pre>
 *   avp_human   median 20.0 dps  (see GunDamageParity)
 *   TACZ        median 82.7 dps  52 guns - the m320 and rpg7 launchers excluded, their damage is the explosion
 *               top end: M107 367, SPAS-12 213, M95 189, AA-12 175, minigun 160, FN Evolys 150, Vector 140
 *   ratio       20.0 / 82.7 = 0.242
 * </pre>
 *
 * <h2>🚨 TACZ SPLITS SHOTGUN DAMAGE ACROSS PELLETS</h2> {@code ModernKineticGunScriptAPI} calls
 * {@code EntityKineticBullet.applyShotgunDamageSpread(bulletAmount)} for every bullet it spawns, unconditionally, which
 * sets each pellet to {@code 1 / bulletAmount} of the listed damage. The AA-12's "30 x 10" is 30 per trigger pull, not
 * 300. The earlier derivation counted every pellet at full damage (AA-12 1750 dps) and built a whole shotgun ceiling on
 * that misreading - see GunDamageParity for why that ceiling is gone.
 * <h2>⚠ Why a flat factor rather than per-weapon scaling</h2> Nothing in a {@link DamageSource} identifies which TACZ
 * gun fired it, so a flat factor is what can be applied. It puts the two medians on top of each other and keeps TACZ's
 * own internal balance between its guns.
 * <h2>⚠⚠ Why this cannot be done with armour attributes</h2> {@code tacz:bullet_ignore_armor} and
 * {@code tacz:bullet_void_ignore_armor} are both in {@code minecraft:bypasses_armor}, so armour points and toughness do
 * nothing against half of TACZ's ammunition. A damage multiplier is the only lever that reaches all four bullet types.
 * <h2>⭐ TACZ hurts TWICE per bullet</h2> {@code EntityKineticBullet.tacAttackEntity} calls {@code hurt()} once with the
 * non-piercing share and once with the armour-ignoring share ({@code armor_ignore}, 0 to 0.75 across the default pack),
 * zeroing {@code invulnerableTime} before each. Both carry a {@code tacz:bullets} type, so both are scaled; a share of
 * 0 stays 0.
 * <h2>No dependency on TACZ</h2> The tag is referenced by id, so this compiles and runs whether or not TACZ is
 * installed. With TACZ absent the tag has no members and every lookup below is false.
 */
public final class TaczBulletResistance {

    /** TACZ's own tag covering all four of its bullet damage types. Referenced by id - no dependency. */
    private static final TagKey<DamageType> TACZ_BULLETS =
        TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("tacz", "bullets"));

    /**
     * avp_human median dps divided by TACZ median dps - see the derivation above.
     * <p>
     * ⚠ Was 0.469, derived from an avp_human median that counted shotgun damage per pellet. Raise toward 1.0 to let
     * TACZ hit harder than this suite's own guns; 1.0 is unmodified TACZ.
     * </p>
     */
    public static final float DPS_PARITY_MULTIPLIER = 0.242F;

    /**
     * How much of a TACZ round's damage a xenomorph actually takes.
     * <p>
     * ⚠ Kept for any caller of the old API. {@code Alien.hurt} now calls {@link GunDamageParity} directly, which covers
     * TACZ and every other supported gun mod through one shared budget.
     * </p>
     */
    public static float damageMultiplier(Alien target, DamageSource damageSource, float incomingDamage) {
        if (!isTaczBullet(damageSource)) {
            return 1.0F;
        }

        return GunDamageParity.damageMultiplier(target, damageSource, incomingDamage);
    }

    /** Whether this damage came from a TACZ gun at all. */
    public static boolean isTaczBullet(DamageSource damageSource) {
        return damageSource.is(TACZ_BULLETS);
    }

    /**
     * Whether this TACZ round ignores armour.
     * <p>
     * ⚠ Informational only - the multiplier applies to these either way, which is the entire point of using a
     * multiplier rather than armour attributes.
     * </p>
     */
    public static boolean bypassesArmour(DamageSource damageSource) {
        return isTaczBullet(damageSource) && damageSource.is(DamageTypeTags.BYPASSES_ARMOR);
    }

    private TaczBulletResistance() {}
}
