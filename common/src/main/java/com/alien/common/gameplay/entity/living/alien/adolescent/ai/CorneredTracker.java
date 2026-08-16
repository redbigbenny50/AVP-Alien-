package com.alien.common.gameplay.entity.living.alien.adolescent.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * ⭐⭐ "CAN NO LONGER ESCAPE". [stated] the child runs "unless they can no longer escape inwhich they would turn to
 * fight" — and [stated] the test is the FLEE PATH BEING BLOCKED, chosen over the cheaper "has been hit N times" because
 * a fast pursuer landing hits across open ground is not cornering anything; it is just faster.
 * <p>
 * THE TEST: ask for a retreat position away from the threat, exactly the way {@code AvoidEntityGoal} does, and see
 * whether the navigator can produce a path to one. No position, or no path, means there is nowhere left to run.
 * </p>
 * <p>
 * ⚠ IT ONLY ASKS ONCE EVERY {@link #CHECK_INTERVAL_TICKS} TICKS, and only while something is actually hurting it. A
 * path query is not free and this would otherwise run every tick on every juvenile in every hive. The answer is then
 * LATCHED for {@link #CORNERED_MEMORY_TICKS} so a child that turns and fights does not flip back to fleeing the instant
 * it steps into a momentarily open tile mid-swing.
 * </p>
 */
public final class CorneredTracker {

    /** ⭐ How often the flee path is re-tested. Lower = more responsive, more path queries. */
    public static final int CHECK_INTERVAL_TICKS = 20;

    /** ⭐ How long a "cornered" answer sticks. Stops it oscillating between fleeing and fighting mid-fight. */
    public static final int CORNERED_MEMORY_TICKS = 60;

    /** How recently it must have been hurt for the question to be worth asking at all. */
    private static final int THREATENED_MEMORY_TICKS = 100;

    /** Matches AvoidEntityGoal's own retreat search so "can I flee" is asked the same way fleeing is done. */
    private static final int FLEE_HORIZONTAL_RANGE = 16;

    private static final int FLEE_VERTICAL_RANGE = 7;

    private int nextCheckTick;

    private int corneredUntilTick;

    /** Re-evaluates on the interval and returns whether the mob is currently boxed in. */
    public boolean tick(PathfinderMob mob) {
        var now = mob.tickCount;

        if (now < corneredUntilTick) {
            return true;
        }

        if (now < nextCheckTick) {
            return false;
        }

        nextCheckTick = now + CHECK_INTERVAL_TICKS;

        var threat = threatOf(mob);

        if (threat == null) {
            return false;
        }

        if (hasSomewhereToRun(mob, threat)) {
            return false;
        }

        corneredUntilTick = now + CORNERED_MEMORY_TICKS;
        return true;
    }

    public boolean isCornered(PathfinderMob mob) {
        return mob.tickCount < corneredUntilTick;
    }

    /**
     * Whatever is currently hurting it - and it has to be RECENT, or a wound taken minutes ago in another fight would
     * keep the child permanently "threatened" and permanently testing paths.
     */
    public static @Nullable LivingEntity threatOf(PathfinderMob mob) {
        var attacker = mob.getLastHurtByMob();

        if (attacker == null || !attacker.isAlive()) {
            return null;
        }

        return mob.tickCount - mob.getLastHurtByMobTimestamp() <= THREATENED_MEMORY_TICKS ? attacker : null;
    }

    private static boolean hasSomewhereToRun(PathfinderMob mob, LivingEntity threat) {
        var away = DefaultRandomPos.getPosAway(
            mob,
            FLEE_HORIZONTAL_RANGE,
            FLEE_VERTICAL_RANGE,
            new Vec3(threat.getX(), threat.getY(), threat.getZ())
        );

        if (away == null) {
            return false;
        }

        // ⚠ A position it can SEE is not a position it can REACH. Walled into a corridor end, getPosAway happily
        // returns a tile on the far side of the wall; only asking the navigator for an actual path settles it.
        var path = mob.getNavigation().createPath(away.x, away.y, away.z, 0);

        return path != null && path.canReach();
    }
}
