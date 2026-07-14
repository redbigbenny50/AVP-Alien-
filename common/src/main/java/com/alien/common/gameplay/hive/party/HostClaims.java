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

    /** How long a drone gives up on quarry it could not path to before it will consider it again. */
    public static final int WRITE_OFF_TICKS = 600;

    /** Quarry each captor has failed to reach, and when it may look at it again. */
    private static final Map<Alien, Map<LivingEntity, Long>> UNREACHABLE = new WeakHashMap<>();

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

    /**
     * This captor could not path to this host. Forget about it for a while and go and find another.
     * <p>
     * {@code CaptureHostAction} deliberately never ABORTS on a failed path - aborting made the planner drop the action,
     * re-plan, and abort again forever. But that means an unreachable quarry pins a drone in place for good. Writing
     * the target off instead keeps the action alive and simply makes the sensor stop offering that host, so the drone
     * picks the next one. Per-captor, and it lapses, so nothing is ever permanently un-huntable.
     */
    public static void writeOffUnreachable(LivingEntity host, Alien captor) {
        UNREACHABLE.computeIfAbsent(captor, ignored -> new WeakHashMap<>())
                .put(host, host.level().getGameTime() + WRITE_OFF_TICKS);
        release(host);
    }

    /** True if this captor recently gave up on reaching this host. */
    public static boolean isUnreachableFor(LivingEntity host, Alien captor) {
        var writtenOff = UNREACHABLE.get(captor);
        if (writtenOff == null) {
            return false;
        }
        var until = writtenOff.get(host);
        if (until == null) {
            return false;
        }
        if (host.level().getGameTime() >= until) {
            writtenOff.remove(host);
            return false;
        }
        return true;
    }

    private record Claim(
            UUID captorId,
            long expiresAt
    ) {}
}