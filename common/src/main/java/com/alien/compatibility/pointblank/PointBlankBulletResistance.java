package com.alien.compatibility.pointblank;

import com.alien.compatibility.guns.GunDamageParity;
import net.minecraft.world.entity.Entity;

/**
 * Pic's Point Blank - identification and its parity multiplier. The budget and the floor live in
 * {@link GunDamageParity}, shared with every other supported gun mod.
 * <h2>⚠⚠ THE FACTOR IS DERIVED, NOT GUESSED</h2> Read from Point Blank 2.2.0's 61 gun definitions
 * ({@code data/pointblank/items/*.json}, identical in the NeoForge and Fabric jars) as
 * {@code damage x pellets x rpm / 60}, using each gun's strongest unconditional fire mode:
 *
 * <pre>
 *   avp_human     median 20.0 dps  (see GunDamageParity)
 *   Point Blank   median 66.7 dps  57 guns - AT4, Javelin, SMAW and M32 MGL excluded, their damage is the explosion
 *                 top end: SPAS-12/M1014/HS12/Citori 275-300, AA-12 200, M134 167, AN-94 burst 150, UAR-10 125
 *   ratio         20.0 / 66.7 = 0.300
 * </pre>
 *
 * ⭐ The median does not move if semi-automatic guns are capped at a human click rate (their listed rpm of 500-800 is a
 * ceiling nobody reaches by hand) - it is 66.7 either way, so the ratio does not rest on that assumption.
 * <h2>⚠ Point Blank pellets each carry the full listed damage</h2> Unlike TACZ and avp_human it does not split: its
 * shotguns fire 30 pellets of 1.0-2.2 each, and {@code HurtingItem.hurtEntity} deals {@code FireModeFeature.getDamage}
 * per pellet. The per-pellet numbers are authored that small for exactly that reason. After the multiplier its shotguns
 * land at 31-90 dps, well under the shared ceiling.
 * <h2>🚨🚨 POINT BLANK HAS NO DAMAGE TYPE OF ITS OWN</h2> Every bullet is {@code damageSources().playerAttack(player)}
 * (or {@code generic()} when a non-player fires) - the same damage type as a sword swing. A damage-type tag, the way
 * TACZ is identified, cannot tell the two apart. Point Blank's own knockback mixin gets around this by checking the
 * attacker's main hand, which misfires on a gun swapped away mid-flight and on melee with a gun held.
 * <p>
 * ⭐ Instead: every non-explosive Point Blank hit - hitscan, slow projectile and thrown - goes through ONE method,
 * {@code HurtingItem.hurtEntity} (verified: the only declaration of it in either jar, no subclass overrides).
 * {@code MixinHurtingItem_PointBlankHit} marks the entity being hit for the duration of that call, and
 * {@link GunDamageParity} asks {@link #isPointBlankHit} while the xenomorph's {@code hurt()} runs inside it. That is
 * exact: a melee swing never passes through that method.
 * </p>
 * <h2>⚠ Explosions</h2> A Point Blank explosive round can detonate from inside {@code hurtEntity}, while the mark is
 * set. Its damage is an explosion type, and {@link GunDamageParity} leaves every explosion unscaled.
 * <h2>No dependency on Point Blank</h2> Nothing here names a Point Blank class. Without the mod the mixin never applies
 * (it is {@code @Pseudo}), the mark is never set, and {@link #isPointBlankHit} is always false.
 */
public final class PointBlankBulletResistance {

    /**
     * avp_human median dps divided by Point Blank median dps - see the derivation above.
     * <p>
     * ⚠ Point Blank's own config has a {@code hitscanDamageModifier} (default 1.0, range 0.1-10) and a
     * {@code headshotDamageModifier} (default 3.0). This multiplier is derived at defaults and applies on top of
     * whatever a server has set.
     * </p>
     */
    public static final float DPS_PARITY_MULTIPLIER = 0.300F;

    /**
     * The entity a Point Blank round is landing on right now, on this thread; null outside {@code hurtEntity}.
     * <p>
     * ⚠ Keyed to the TARGET rather than a bare boolean, so if Point Blank ever threw mid-hit and skipped the RETURN
     * that clears it, the stale mark could only ever match that one entity - and the next Point Blank hit overwrites it
     * anyway.
     * </p>
     */
    private static final ThreadLocal<Entity> CURRENT_TARGET = new ThreadLocal<>();

    /** Called at the HEAD of Point Blank's {@code HurtingItem.hurtEntity}. */
    public static void beginHit(Entity target) {
        CURRENT_TARGET.set(target);
    }

    /** Called at the RETURN of Point Blank's {@code HurtingItem.hurtEntity}. */
    public static void endHit() {
        CURRENT_TARGET.remove();
    }

    /** Whether the damage {@code target} is taking right now is a Point Blank round. */
    public static boolean isPointBlankHit(Entity target) {
        return target != null && CURRENT_TARGET.get() == target;
    }

    private PointBlankBulletResistance() {}
}
