package com.alien.common.gameplay.hive.party;

import com.alien.common.gameplay.entity.living.alien.Alien;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * One drone per host.
 * <p>
 * Without this, every member of a host-hunt party independently runs the same "best target" scoring and converges on
 * the same villager - three drones walk past two other viable hosts to pile onto one, and only one of them can have it.
 * <p>
 * A claim is a soft reservation, not a lock. It is <b>refreshed every tick</b> by the pursuing drone and lapses after
 * {@link #CLAIM_TTL_TICKS} on its own, so nothing has to remember to release it: a drone that dies, unloads, replans,
 * or gets dragged into a fight simply stops refreshing, and its target frees up a few seconds later. There is no path
 * where a stale claim can make a host permanently un-huntable.
 */
public final class HostClaims {

    private HostClaims() {}

    /** A claim lapses this long after its last refresh. Short, because the pursuer refreshes it every tick. */
    public static final int CLAIM_TTL_TICKS = 60;

    private static final Map<LivingEntity, Claim> CLAIMS = new WeakHashMap<>();

    /** Stake or refresh this captor's claim on a host. */
    public static void claim(LivingEntity host, Alien captor) {
        CLAIMS.put(host, new Claim(captor.getUUID(), host.level().getGameTime() + CLAIM_TTL_TICKS));
    }

    /** True if some OTHER living drone is already on its way to this host. */
    public static boolean isClaimedByOther(LivingEntity host, Alien captor) {
        var claim = CLAIMS.get(host);
        if (claim == null) {
            return false;
        }
        if (claim.captorId().equals(captor.getUUID())) {
            return false; // our own claim
        }
        if (host.level().getGameTime() >= claim.expiresAt()) {
            CLAIMS.remove(host); // lapsed - the pursuer stopped refreshing it
            return false;
        }
        return true;
    }

    /** Drop the claim on a host (it has been grabbed, or is no longer worth taking). */
    public static void release(LivingEntity host) {
        CLAIMS.remove(host);
    }

    private record Claim(
        UUID captorId,
        long expiresAt
    ) {}
}
