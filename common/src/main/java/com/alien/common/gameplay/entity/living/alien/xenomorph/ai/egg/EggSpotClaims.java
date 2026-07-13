package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which egg destinations are already SPOKEN FOR.
 * <p>
 * A destination is only "free" if no egg is sitting in it - but an egg still being carried across the hive is not
 * sitting anywhere yet. So every hauler looking for somewhere to put an egg saw the same empty cell and all of them
 * set off for it. Three drones queued up on ONE webbed host, all placed an egg, and all three hatched: one host,
 * three facehuggers.
 * <p>
 * A claim is a soft reservation: the first hauler to pick a spot owns it, and other haulers look elsewhere. It
 * EXPIRES ({@link #CLAIM_LIFETIME_TICKS}) so a carrier that dies, despawns, or gets stuck en route cannot lock a
 * nursery bed or a host out of the hive forever - the spot simply frees itself and someone else takes the job.
 * <p>
 * Deliberately transient (server-side, not persisted): on reload nothing is in flight, so nothing is claimed.
 */
public final class EggSpotClaims {

    private EggSpotClaims() {}

    /** A claim only outlives its carrier by this much. Refreshed every tick the carrier is still on its way. */
    private static final int CLAIM_LIFETIME_TICKS = 300;

    private record Claim(UUID carrier, long expiresAtGameTime) {}

    private static final Map<BlockPos, Claim> CLAIMS = new HashMap<>();

    /** Reserve {@code spot} for {@code carrier}, or refresh a claim it already holds. */
    public static void claim(ServerLevel level, BlockPos spot, UUID carrier) {
        CLAIMS.put(spot.immutable(), new Claim(carrier, level.getGameTime() + CLAIM_LIFETIME_TICKS));
    }

    /** True if somebody OTHER than {@code carrier} currently holds this spot. */
    public static boolean isClaimedByOther(ServerLevel level, BlockPos spot, UUID carrier) {
        var claim = CLAIMS.get(spot);
        if (claim == null) {
            return false;
        }
        if (level.getGameTime() > claim.expiresAtGameTime()) {
            CLAIMS.remove(spot); // stale - the carrier never made it
            return false;
        }
        return !claim.carrier().equals(carrier);
    }

    /** Drop the claim: the egg was placed, the target was abandoned, or the carrier gave up. */
    public static void release(BlockPos spot) {
        CLAIMS.remove(spot);
    }

    /** Drop every claim this carrier holds (it died, or lost its egg). */
    public static void releaseAll(UUID carrier) {
        CLAIMS.entrySet().removeIf(entry -> entry.getValue().carrier().equals(carrier));
    }
}