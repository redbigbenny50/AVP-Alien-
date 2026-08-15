package com.alien.common.gameplay.hive.economy;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.hive.location.HiveLocation;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Brings home a hive member that has fallen somewhere it can never walk back from.
 * <p>
 * [stated] "this hive was also above a large drop into lava these are nether xenos so they dont die in the lava but its
 * possible a large amount of xenos are under the hive and cant get back in maybe we should have it where if a drone
 * isnt enclosed in not natural blocks and cant path to a hive or vent it warps back into reserves."
 * </p>
 * <h2>⚠⚠ THIS IS A LEAK, NOT A DEATH — WHICH IS WHY IT NEEDED ITS OWN RULE</h2> A nether xenomorph is fire-immune, so a
 * fall into a lava lake under the hive costs it nothing and it simply stands there forever. It still COUNTS as a
 * member, so the census keeps reporting workers the hive cannot use: his log says "no free drones, nothing in reserve.
 * Build paused." on every single carve while drones plainly exist. Anything that killed them would have self-corrected;
 * surviving is what makes it permanent.
 * <h2>⚠ NO PATHFINDING TEST — DISTANCE PROGRESS INSTEAD</h2> Running a full path to a vent for every candidate would be
 * a real per-tick cost, and it would still miss cases a path cannot express. Tracking the best distance to the hive
 * centre and giving up when it stops improving costs one comparison per interval, and it catches lava, walls, drops and
 * simple confusion alike. It is the same technique {@code DeliverHostAction} already uses for a stranded host carrier.
 */
public final class StrandedMemberRecovery {

    /** How long without getting any closer to home before a member is considered stranded. 90 seconds. */
    private static final long NO_PROGRESS_TICKS = 1800L;

    /** Only bother with members that are genuinely far out - anything closer is just walking around. */
    private static final double MIN_STRANDED_DISTANCE_SQUARED = 24.0 * 24.0;

    /** Progress has to be real to count, not navigation jitter. */
    private static final double PROGRESS_EPSILON_SQUARED = 4.0;

    private static final int CHECK_INTERVAL_TICKS = 40;

    private static final Map<Alien, Progress> PROGRESS = new WeakHashMap<>();

    private StrandedMemberRecovery() {}

    private record Progress(
        double bestDistanceSquared,
        long lastImprovedTick
    ) {}

    /**
     * Sweep every member of this hive that is currently loaded.
     * <p>
     * ⚠ ONE QUERY OVER THE CLAIM, then a membership test - the same shape the aggro sweep uses. NOT one query per
     * chunk.
     * </p>
     */
    public static void sweep(ServerLevel level, HiveLocation location) {
        var claimed = location.claimedChunks();

        if (claimed.isEmpty()) {
            return;
        }

        var minX = Integer.MAX_VALUE;
        var minZ = Integer.MAX_VALUE;
        var maxX = Integer.MIN_VALUE;
        var maxZ = Integer.MIN_VALUE;

        for (var chunk : claimed) {
            minX = Math.min(minX, chunk.getMinBlockX());
            minZ = Math.min(minZ, chunk.getMinBlockZ());
            maxX = Math.max(maxX, chunk.getMaxBlockX() + 1);
            maxZ = Math.max(maxZ, chunk.getMaxBlockZ() + 1);
        }

        // ⚠ FULL HEIGHT IS THE POINT HERE - the stranded ones are BELOW the hive, in the lava.
        var box = new net.minecraft.world.phys.AABB(
            minX,
            level.getMinBuildHeight(),
            minZ,
            maxX,
            level.getMaxBuildHeight(),
            maxZ
        );

        // ⚠ NEAREST-LOCATION TEST AS THE MEMBERSHIP PROXY. A stranded alien is OUTSIDE the structure, so the
        // interior test BroodBankTask uses cannot serve here - it is false for exactly the population we want.
        // Asking whether THIS hive is the closest one to it is the honest available substitute, and it keeps a
        // neighbouring hive from banking someone else's stray.
        for (var alien : level.getEntitiesOfClass(Alien.class, box, a -> isOurs(level, location, a))) {
            tick(alien, location);
        }
    }

    private static boolean isOurs(ServerLevel level, HiveLocation location, Alien alien) {
        var nearest = com.alien.common.gameplay.hive.location.HiveLocationRegistry.INSTANCE.findNearestInDim(
            level.dimension(),
            alien.blockPosition()
        );

        return nearest != null && nearest.id().equals(location.id());
    }

    /**
     * @return true if the alien was banked and removed
     */
    public static boolean tick(Alien alien, HiveLocation location) {
        if (!(alien.level() instanceof ServerLevel level) || alien.tickCount % CHECK_INTERVAL_TICKS != 0) {
            return false;
        }

        if (!isRecoverable(alien, level)) {
            PROGRESS.remove(alien);
            return false;
        }

        var centre = location.centerPos();
        var distanceSquared = alien.distanceToSqr(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5);

        if (distanceSquared < MIN_STRANDED_DISTANCE_SQUARED) {
            PROGRESS.remove(alien);
            return false;
        }

        var now = level.getGameTime();
        var progress = PROGRESS.get(alien);

        if (progress == null || distanceSquared <= progress.bestDistanceSquared() - PROGRESS_EPSILON_SQUARED) {
            PROGRESS.put(alien, new Progress(distanceSquared, now));
            return false;
        }

        if (now - progress.lastImprovedTick() < NO_PROGRESS_TICKS) {
            return false;
        }

        return bankAndRemove(alien, location, level);
    }

    /**
     * ⚠⚠ THE CAGE TEST — [stated] "just want to make sure we dont take someones captured xeno which is why i said not
     * enclosed by unnatural blocks."
     * <p>
     * ⚠ I TEST **ENCLOSURE**, NOT "PLAYER-PLACED". There is no reliable way to ask whether a block was placed by a
     * player - worldgen and player builds are the same blocks - so the honest signal is whether the alien is boxed in
     * at all. A captive in a cell has no open neighbour; one stranded on a lava lake has open sky and open sides. That
     * also means a cage built out of NATURAL blocks still protects its occupant, which a "player-placed" test would
     * have missed.
     * </p>
     */
    private static boolean isRecoverable(Alien alien, ServerLevel level) {
        if (!alien.isAlive() || alien.isPassenger() || alien.hasCustomName() || alien.isLeashed()) {
            return false;
        }

        // ⚠ Anything with a JOB is legitimately out there - a hunting party, a convoy, a carrier mid-haul. Banking one
        // of those would delete a working unit and look exactly like a despawn.
        if (alien.partyMembership() != null || alien.isMarkedForReserveReturn()) {
            return false;
        }

        return !isEnclosed(alien, level);
    }

    /** Boxed in on every side means somebody is keeping it. Any open neighbour means it merely cannot get home. */
    private static boolean isEnclosed(Alien alien, ServerLevel level) {
        var pos = alien.blockPosition();

        for (var direction : Direction.values()) {
            var neighbour = pos.relative(direction);

            if (!level.getBlockState(neighbour).isCollisionShapeFullBlock(level, neighbour)) {
                return false;
            }
        }

        return true;
    }

    /**
     * ⚠ BANKED AS A COUNT, NOT TELEPORTED. Warping a live entity into the hive drops it wherever the centre happens to
     * be - possibly inside rock - and re-triggers the adoption churn. Returning it to the reserve pool lets the hive
     * spawn it again properly when it needs the worker.
     */
    private static boolean bankAndRemove(Alien alien, HiveLocation location, ServerLevel level) {
        var returned = location.localReserves().addReturningMember(alien.getType(), 1);

        if (!returned) {
            // ⚠ A FULL RESERVE MUST NOT LOSE THE UNIT - same fallback the brood bank uses everywhere else.
            location.localReserves().addBrood(alien.getType(), 1);
        }

        PROGRESS.remove(alien);
        alien.discard();

        com.alien.Alien.LOGGER.info(
            "Hive: recovered a stranded {} at {} - no progress toward home for {}s, banked into reserves",
            alien.getType().builtInRegistryHolder().key().location(),
            alien.blockPosition(),
            NO_PROGRESS_TICKS / 20L
        );

        return true;
    }
}
