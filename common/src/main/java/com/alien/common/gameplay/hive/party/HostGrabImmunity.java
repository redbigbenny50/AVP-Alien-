package com.alien.common.gameplay.hive.party;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.model.alien.FreeMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Post-escape state for the host capture arc: a freed host cannot be immediately re-grabbed, and the drone that lost it
 * is stunned.
 * <p>
 * When a carried host gets free - a player fills the struggle bar, or someone kills/attacks the carrier - two things
 * happen for {@link #ESCAPE_DURATION_TICKS} (60s):
 * <ul>
 * <li>the host is <b>grab-immune</b>: no drone will try to carry it off again. Other xenomorphs still ATTACK it
 * normally - escaping a capture is not a truce, it just means the hive stops trying to take you alive.</li>
 * <li>the carrier is <b>stunned</b>: it stands idle and helpless. Hitting it wakes it early.</li>
 * </ul>
 * Both are transient (in-memory): after a restart the world has moved on and neither should persist.
 */
public final class HostGrabImmunity {

    private HostGrabImmunity() {}

    /** Grab-immunity and carrier stun both last one minute. */
    public static final int ESCAPE_DURATION_TICKS = 60 * 20;

    /** Game time at which each host's grab-immunity expires. */
    private static final Map<Entity, Long> IMMUNE_UNTIL = new WeakHashMap<>();

    /** Game time at which each stunned carrier recovers. */
    private static final Map<Alien, Long> STUNNED_UNTIL = new WeakHashMap<>();

    /** Grant a freed host immunity from being grabbed again for a minute. */
    public static void grantImmunity(Entity host) {
        IMMUNE_UNTIL.put(host, host.level().getGameTime() + ESCAPE_DURATION_TICKS);
    }

    public static boolean isImmune(Entity host) {
        var until = IMMUNE_UNTIL.get(host);
        if (until == null) {
            return false;
        }
        if (host.level().getGameTime() >= until) {
            IMMUNE_UNTIL.remove(host);
            return false;
        }
        return true;
    }

    /** Stun the drone that just lost its catch: it stands idle until the timer runs out or something hits it. */
    public static void stun(Alien carrier) {
        STUNNED_UNTIL.put(carrier, carrier.level().getGameTime() + ESCAPE_DURATION_TICKS);
        if (carrier instanceof FreeMob freeMob) {
            freeMob.removeFreedom();
        }
    }

    public static boolean isStunned(Alien carrier) {
        return STUNNED_UNTIL.containsKey(carrier);
    }

    /** Called from the carrier's tick: ends the stun when the timer expires. Hitting it wakes it early. */
    public static void tickStun(Alien carrier) {
        var until = STUNNED_UNTIL.get(carrier);
        if (until == null) {
            return;
        }
        boolean recovered = carrier.level().getGameTime() >= until;
        boolean wokenByPain = carrier.getLastHurtByMob() != null
            && carrier.tickCount - carrier.getLastHurtByMobTimestamp() < 5;
        if (recovered || wokenByPain) {
            wake(carrier);
        }
    }

    /** End a stun immediately (recovered, or hit). */
    public static void wake(Alien carrier) {
        if (STUNNED_UNTIL.remove(carrier) != null && carrier instanceof FreeMob freeMob) {
            freeMob.restoreFreedom();
        }
    }

    /**
     * Release a carried host: it dismounts, regains its freedom and its grab-immunity, and the carrier is stunned. Used
     * both when a player wins the struggle and when someone rescues a captive by hurting the carrier.
     */
    public static void breakCapture(Alien carrier, LivingEntity host) {
        host.stopRiding();
        if (host instanceof Mob mob && mob instanceof FreeMob freeMob) {
            freeMob.restoreFreedom();
        }
        grantImmunity(host);
        stun(carrier);
    }
}
