package com.alien.compatibility.guns;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.registry.init.AlienGameRules;
import com.alien.compatibility.pointblank.PointBlankBulletResistance;
import com.alien.compatibility.tacz.TaczBulletResistance;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Brings third-party gun mods into line with this mod suite's own guns, for every one of them at once.
 * <h2>What it does</h2> Each supported gun mod supplies ONE number - a parity multiplier derived from its own gun data
 * - and this class applies it plus a shared damage budget. Supported today: TACZ ({@link TaczBulletResistance}) and
 * Pic's Point Blank ({@link PointBlankBulletResistance}).
 * <h2>⭐ The reference is avp_human's MEDIAN SUSTAINED DPS = 20</h2> Read from {@code GunData.java} (0.1.11b-fork) as a
 * player holding the trigger: {@code damage x 20 / cooldownInTicks}.
 *
 * <pre>
 *   Old Painless 160   smartgun 80   M4RA 40   F903WE 40   sniper 20   flamethrower 20
 *   M37 16   ZX-76 16   pulse rifle 12   pistol 12   rocket 5.3        (11 guns, median 20)
 * </pre>
 *
 * ⚠⚠ avp_human SHOTGUNS SPLIT THEIR DAMAGE ACROSS PELLETS - {@code HitScanGunAttackAction} gives each pellet
 * {@code 1 / pelletCount} of the shot, so an M37 blast is 16 total, not 16 per pellet. The previous derivation counted
 * it per pellet (128 dps) and got a median of 40, which is why TACZ used to run at 0.469 - double the intended damage.
 * <h2>⭐ ONE BUDGET PER XENOMORPH, SHARED BY EVERY GUN MOD</h2> A player running TACZ and Point Blank together cannot
 * double the cap by alternating weapons. The ceiling is avp_human's strongest gun, Old Painless, at 160 dps.
 * <h2>🚨🚨 THE FLOOR - WHY XENOMORPHS NO LONGER LOOK IMMUNE</h2> The old throttle returned exactly 0 once the budget
 * was spent. A 0-damage {@code hurt()} returns false before vanilla's hurt reaction, so there was no red flash, no
 * sound, no knockback - indistinguishable from immunity, and players reported it as a bug. [stated] "I would say having
 * it return the red each hit even if it does 0 to give them feedback." Past the budget every hit still deals
 * {@link #FLOOR} of its scaled damage, so the ordinary hurt path runs and every hit visibly lands. ⚠ Do NOT replace
 * this with a hand-forced flash on a 0-damage hit - that fights vanilla instead of using it.
 * <p>
 * ⭐ Both supported mods zero {@code invulnerableTime} before every hit (TACZ in
 * {@code EntityKineticBullet.tacAttackEntity}, Point Blank in {@code HurtingItem.hurtEntity}), so a small floored hit
 * is never swallowed by i-frames.
 * </p>
 * <h2>🚨🚨 THE OLD BUDGET NEVER REFRESHED</h2> Its window started at {@code Integer.MIN_VALUE} and the reset test
 * overflowed, so the per-second budget was really a per-LIFETIME budget - see {@link #sustainedFireThrottle}.
 * <h2>⚠⚠ THE VOLLEY CEILING IS GONE, DELIBERATELY</h2> It capped "shotgun volleys" at 60 dps and was the actual cause
 * of the reported immunity. Three separate problems, all verified against the jars:
 * <ul>
 * <li>Its "three hits within two ticks" window really spanned THREE ticks, so any 1200 rpm gun (TACZ minigun, Vector)
 * was classed as a shotgun on its third round.</li>
 * <li>TACZ calls {@code hurt()} TWICE per bullet (armour-piercing and non-piercing parts), so every single rifle round
 * counted as two hits. Any gun at 600 rpm or faster tripped it on its second shot - every automatic in TACZ.</li>
 * <li>Its premise was false. TACZ splits shotgun damage across pellets ({@code applyShotgunDamageSpread}, called
 * unconditionally), so the AA-12 is 175 dps, not 1750. After the parity multiplier no TACZ or Point Blank gun comes
 * near the 160 ceiling, shotguns included - there is nothing left for a second ceiling to catch.</li>
 * </ul>
 * <h2>⭐ Behind the {@code gunBalancing} game rule, ON by default</h2> With it off, every supported mod deals its own
 * unmodified damage to xenomorphs. See {@link AlienGameRules#GUN_BALANCING}.
 * <h2>⚠ Explosions are never scaled</h2> Launchers and grenades from either mod pass through untouched, as they always
 * did for TACZ.
 * <h2>⚠ Juveniles, facehuggers and eggs are never protected</h2> A bullet should still kill a facehugger outright, and
 * they were never part of the dps comparison.
 */
public final class GunDamageParity {

    /** No change: not third-party gunfire, not a xenomorph, or an explosion. */
    private static final float UNAFFECTED = 1.0F;

    /**
     * Share of a hit's scaled damage that always lands, however far past the budget the target is.
     * <p>
     * ⭐ Any non-zero amount is enough to run vanilla's hurt reaction; this value decides how much a gun still does once
     * it has spent the budget. 0.125 is the middle of the stated 10-15%. Raise it to let sustained fire keep more of
     * its bite; lower it toward 0.05 to make the ceiling harder.
     * </p>
     */
    public static final float FLOOR = 0.125F;

    /** Most third-party gun dps any one xenomorph can take - avp_human's best gun, Old Painless, sustains 160. */
    public static final float DPS_CEILING = 160F;

    /** Length of the rolling budget window. */
    private static final int WINDOW_TICKS = 20;

    /**
     * Per-target rolling windows, keyed on the entity itself and weakly held, so a dead or unloaded xenomorph takes its
     * entry with it. ⚠ Server thread only - see the client guard in {@link #damageMultiplier}.
     */
    private static final Map<Alien, DamageWindow> WINDOWS = new WeakHashMap<>();

    /**
     * How much of an incoming hit a xenomorph actually takes.
     *
     * @return a multiplier to apply to {@code incomingDamage}; 1 when the source is not third-party gunfire
     */
    public static float damageMultiplier(Alien target, DamageSource damageSource, float incomingDamage) {
        if (!(target instanceof Xenomorph)) {
            return UNAFFECTED;
        }

        if (damageSource.is(DamageTypeTags.IS_EXPLOSION)) {
            return UNAFFECTED;
        }

        // ⚠ The budget map is not thread-safe and damage is only authoritative on the server. The client's copy of
        // the game rules is not synced either, so a client-side hurt() call is simply left alone.
        if (target.level().isClientSide) {
            return UNAFFECTED;
        }

        // ⭐ THE OPT-OUT. [stated] players who "want to be op with these mods" turn this off; it is ON by default.
        // Checked per hit, so /gamerule gunBalancing false applies from the next shot.
        if (!target.level().getGameRules().getBoolean(AlienGameRules.GUN_BALANCING)) {
            return UNAFFECTED;
        }

        var parity = parityFor(target, damageSource);

        if (parity == UNAFFECTED) {
            return UNAFFECTED;
        }

        return parity * sustainedFireThrottle(target, incomingDamage * parity);
    }

    /**
     * Which gun mod fired this, and that mod's parity multiplier.
     * <p>
     * ⚠ TACZ is checked by damage TYPE (its own tag). Point Blank has no damage type of its own - it uses vanilla
     * {@code player_attack}, the same as a sword - so it is identified by the hit it is currently delivering, marked by
     * {@code MixinHurtingItem_PointBlankHit}. See {@link PointBlankBulletResistance}.
     * </p>
     */
    private static float parityFor(Alien target, DamageSource damageSource) {
        if (TaczBulletResistance.isTaczBullet(damageSource)) {
            return TaczBulletResistance.DPS_PARITY_MULTIPLIER;
        }

        if (PointBlankBulletResistance.isPointBlankHit(target)) {
            return PointBlankBulletResistance.DPS_PARITY_MULTIPLIER;
        }

        return UNAFFECTED;
    }

    /**
     * Keeps a xenomorph's third-party gun intake at or under {@link #DPS_CEILING}, with {@link #FLOOR} as the least any
     * hit ever deals.
     *
     * @return the share of {@code scaledDamage} that lands, in {@code FLOOR..1}
     */
    private static float sustainedFireThrottle(Alien target, float scaledDamage) {
        if (scaledDamage <= 0F) {
            // ⭐ TACZ's second hurt() per bullet carries 0 when the round has no armour-ignore share. Nothing to budget
            // and nothing to floor - 0 times anything is still 0.
            return 1F;
        }

        var now = target.tickCount;
        var window = WINDOWS.computeIfAbsent(target, $ -> new DamageWindow());

        // 🚨🚨 THIS RESET NEVER FIRED IN THE OLD FILE. The window started at Integer.MIN_VALUE, and
        // `now - Integer.MIN_VALUE` overflows to a NEGATIVE int for every real tick count, so the "one second" budget
        // never refreshed - each xenomorph got 160 scaled damage (60 once a "volley" was seen) for its WHOLE LIFE and
        // every TACZ hit after that did 0. That is the "after awhile they become immune" report, exactly.
        // ⭐ A plain started flag has nothing to overflow.
        if (!window.started || now - window.startTick >= WINDOW_TICKS || now < window.startTick) {
            window.started = true;
            window.startTick = now;
            window.damage = 0F;
        }

        var remaining = DPS_CEILING * (WINDOW_TICKS / 20F) - window.damage;

        if (remaining <= 0F) {
            return FLOOR; // Budget spent - the hit still registers, just weakly.
        }

        window.damage += Math.min(scaledDamage, remaining);

        if (scaledDamage <= remaining) {
            return 1F;
        }

        // ⚠ The hit that crosses the ceiling keeps whichever is larger: what was left of the budget, or the floor.
        return Math.max(remaining / scaledDamage, FLOOR);
    }

    /** Rolling third-party gun damage taken by one xenomorph. */
    private static final class DamageWindow {

        private boolean started;

        private int startTick;

        private float damage;
    }

    private GunDamageParity() {}
}
