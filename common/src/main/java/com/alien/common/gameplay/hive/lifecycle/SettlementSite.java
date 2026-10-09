package com.alien.common.gameplay.hive.lifecycle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Turns "where the queen happened to stop" into "where a hive can actually exist".
 * <h2>⚠⚠ THE SETTLE PATH NEVER CHECKED THE GROUND</h2> A dug hive walks to a committed anchor and
 * {@code QueenLifecyclePhaseManager.tickLocation} carves her a pocket on arrival. The SETTLE-WHERE-SHE-STANDS path —
 * {@code QueenSettlementDetector} banking out-of-combat ticks, then {@code HiveManager} founding — calls none of that.
 * Nothing looked at liquid, nothing looked at footing, and the one rule that would have caught a hive at y=69 (the
 * depth rule) is waived by {@code queenFoundsWhereStanding} and on flat worlds. That is how a hive ended up on an ocean
 * surface and a queen ended up sealed in terrain.
 * <h2>His ruling (Sep 16)</h2> "she shouldnt found on the waters surface but if she does found in an ocean have her go
 * down until she is under the surface if possible." So water does not refuse her outright: it pushes her DOWN to the
 * seabed, and only refuses if there is no seabed to reach.
 */
public final class SettlementSite {

    /** How far down to look for a seabed before giving up on an ocean site. */
    public static int MAX_DESCENT = 48;

    /** How far up to look for open air when she is buried, before the pocket carve is asked to rescue her. */
    public static int MAX_ASCENT = 8;

    private SettlementSite() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@return the position she should actually found at, or {@code null} if this place cannot hold a hive}
     * <p>
     * Three outcomes. Ordinary ground: returned unchanged. Water: walked down to the first solid floor under the
     * liquid, so the hive sits ON THE SEABED rather than floating — the arrival pocket then dams and drains it, which
     * it already knows how to do. No seabed within {@link #MAX_DESCENT}: null, and she keeps looking.
     */
    public static @Nullable BlockPos resolve(LivingEntity queen, BlockPos candidate) {
        if (!(queen.level() instanceof ServerLevel level)) {
            return candidate;
        }

        if (!isLiquidAt(level, candidate) && !isLiquidAt(level, candidate.below())) {
            return candidate;
        }

        // ⚠ In or over water: descend to the seabed. Starting at the candidate and walking DOWN means a shallow
        // river bed is found immediately, while an ocean walks the whole column.
        var probe = candidate.mutable();

        for (var step = 0; step < MAX_DESCENT; step++) {
            probe.move(0, -1, 0);

            if (level.isOutsideBuildHeight(probe.getY())) {
                return null;
            }

            var state = level.getBlockState(probe);

            if (!state.liquid() && state.isSolidRender(level, probe)) {
                // Stand her ON it, not IN it.
                return probe.above().immutable();
            }
        }

        return null;
    }

    /** {@return whether she is sealed inside blocks and needs the pocket carved before she can breathe} */
    public static boolean isBuried(LivingEntity queen, BlockPos position) {
        if (!(queen.level() instanceof ServerLevel level)) {
            return false;
        }

        return level.getBlockState(position).isSuffocating(level, position)
            || level.getBlockState(position.above()).isSuffocating(level, position.above());
    }

    private static boolean isLiquidAt(ServerLevel level, BlockPos position) {
        return level.getBlockState(position).liquid();
    }
}
