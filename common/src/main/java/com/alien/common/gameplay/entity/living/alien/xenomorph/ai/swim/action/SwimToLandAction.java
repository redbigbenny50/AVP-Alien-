package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.swim.action;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.just.ai.goap.action.Action;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class SwimToLandAction {

    private static final double NAVIGATION_SPEED = 1.0;

    public static Action.Signal perform(Action.Context<? extends Xenomorph> context) {
        var xenomorph = context.getActor();
        var target = xenomorph.getTarget();

        if (target == null) {
            return Action.Signal.ABORT;
        }

        if (xenomorph.onGround() && !xenomorph.isInWater()) {
            return Action.Signal.CONTINUE;
        }

        var landPos = findAdjacentLandPosition(xenomorph);

        if (landPos != null) {
            xenomorph.setPos(landPos.getX() + 0.5, landPos.getY(), landPos.getZ() + 0.5);
            return Action.Signal.CONTINUE;
        }

        // ⭐⭐⭐ THIS IS THE SPIN. A xenomorph a few blocks off shore has no ADJACENT land, so the step-out above
        // fails - and the fallback then asked a GROUND navigator to path, from inside water, to a target standing on
        // dry land. It cannot, so it produced nothing, and the whole decision re-ran next tick. She paddled on the
        // spot forever, in full view of the player watching her try.
        //
        // ⭐ Head for the nearest shore instead, using the SAME search SWIM_TO_SHORE uses. Getting out of the water
        // is progress toward the target in every case that matters - once she is on land the ordinary combat pathing
        // takes over and can actually reach him.
        var shore = SwimToShoreAction.findNearestDrySurface(
            xenomorph,
            SwimToShoreAction.SHORE_SEARCH_RADIUS,
            SwimToShoreAction.SHORE_SEARCH_STEP
        );

        if (shore != null) {
            xenomorph.getLookControl().setLookAt(shore.getX() + 0.5, shore.getY(), shore.getZ() + 0.5);
            xenomorph.getNavigation().moveTo(shore.getX() + 0.5, shore.getY(), shore.getZ() + 0.5, NAVIGATION_SPEED);
            return Action.Signal.CONTINUE;
        }

        // No shore anywhere in range - open ocean. Swimming at the target is genuinely the best move available.
        xenomorph.getLookControl().setLookAt(target);
        xenomorph.getNavigation().moveTo(target, NAVIGATION_SPEED);

        return Action.Signal.CONTINUE;
    }

    private static @Nullable BlockPos findAdjacentLandPosition(Xenomorph xenomorph) {
        var pos = xenomorph.blockPosition();
        var level = xenomorph.level();
        var mobHeight = (int) Math.ceil(xenomorph.getBbHeight());

        for (var direction : Direction.Plane.HORIZONTAL) {
            var candidate = pos.relative(direction);

            if (isWalkable(level, candidate, mobHeight)) {
                return candidate;
            }

            var candidateAbove = candidate.above();

            if (isWalkable(level, candidateAbove, mobHeight)) {
                return candidateAbove;
            }
        }

        return null;
    }

    private static boolean isWalkable(Level level, BlockPos feetPos, int entityHeight) {
        var groundState = level.getBlockState(feetPos.below());

        if (!groundState.isSolid() || groundState.liquid()) {
            return false;
        }

        for (int i = 0; i < entityHeight; i++) {
            var blockState = level.getBlockState(feetPos.above(i));

            if (blockState.isSolid() || blockState.liquid()) {
                return false;
            }
        }

        return true;
    }

    private SwimToLandAction() {
        throw new UnsupportedOperationException();
    }
}
