package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.anchor_break.action;

import com.alien.common.gameplay.block.capture.anchor.AnchorBlock;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.anchor_break.AnchorBreakSensors;
import com.blib.api.common.goap.v1.action.impl.NeoMoveToPosAction;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.action.Action;
import com.just.ai.goap.state.Blackboard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * A royal defender tears a capture anchor out of its hive.
 * <p>
 * The defender walks to the anchor and then works on it for a short while rather than deleting it on contact - a
 * capture setup the player invested in should take a visible moment to come apart, and the delay gives them a chance to
 * intervene. Breaking the block fires {@code AnchorBlockEntity.setRemoved}, which releases whatever it was holding, so
 * a chained queen is freed as a consequence of the demolition rather than by special-casing her here.
 */
public final class BreakAnchorAction {

    private BreakAnchorAction() {}

    /** The anchor this defender committed to, so it doesn't re-pick a different one mid-approach. */
    private static final StateKey<BlockPos> KEY_TARGET_ANCHOR = StateKey.sensed("anchor_break_target");

    /** Tick the defender started working on the anchor; used to time the teardown. */
    private static final StateKey<Integer> KEY_WORK_START_TICK = StateKey.sensed("anchor_break_started");

    /** How long a defender hammers on an anchor before it gives way. */
    private static final int BREAK_DURATION_TICKS = 40;

    /** A defender working a CHAINED anchor strikes the chain once per this many ticks. */
    private static final int CHAIN_STRIKE_INTERVAL_TICKS = 20;

    /** Close enough to be hitting it. */
    private static final double BREAK_RANGE_SQUARED = 9.0;

    public static Action.Signal perform(Action.Context<? extends Xenomorph> context) {
        var xenomorph = context.getActor();
        var blackboard = context.getBlackboard(Blackboard.Scope.ACTION);

        var target = blackboard.getOrDefault(KEY_TARGET_ANCHOR, (BlockPos) null);
        if (target == null) {
            target = AnchorBreakSensors.findAnchor(xenomorph);
            if (target == null) {
                return Action.Signal.ABORT;
            }
            blackboard.set(KEY_TARGET_ANCHOR, target);
        }

        // Someone else got it, or it was mined out from under us - stop and let the sensor pick a fresh one.
        if (!(xenomorph.level().getBlockState(target).getBlock() instanceof AnchorBlock)) {
            blackboard.set(KEY_TARGET_ANCHOR, null);
            return Action.Signal.CONTINUE;
        }

        if (xenomorph.blockPosition().distSqr(target) <= BREAK_RANGE_SQUARED) {
            // \u2b50 Oct 3 - AN ANCHOR WITH A CHAIN ON IT IS A RESCUE, NOT A DEMOLITION. [stated] "the rescueers would
            // be
            // trying to break the chain not the anchors". The defender strikes the chain about once a second (the same
            // roll a kin rescuer makes) and leaves the block standing. Once it is empty the ordinary teardown below
            // applies to it like any other anchor planted in the hive's territory.
            if (
                xenomorph.level()
                    .getBlockEntity(target) instanceof com.alien.common.gameplay.block.entity.capture.anchor.AnchorBlockEntity anchor
                    && anchor.hasChain()
            ) {
                blackboard.set(KEY_WORK_START_TICK, -1);
                xenomorph.getLookControl().setLookAt(Vec3.atCenterOf(target));
                if (xenomorph.tickCount % CHAIN_STRIKE_INTERVAL_TICKS == 0) {
                    com.alien.common.gameplay.hive.lifecycle.QueenCaptivity.strikeChain(
                        xenomorph,
                        target,
                        com.alien.common.gameplay.hive.lifecycle.QueenCaptivity.RESCUE_CHAIN_BREAK_CHANCE
                    );
                }
                return Action.Signal.CONTINUE;
            }
            var startedAt = blackboard.getOrDefault(KEY_WORK_START_TICK, -1);
            if (startedAt < 0) {
                blackboard.set(KEY_WORK_START_TICK, xenomorph.tickCount);
                return Action.Signal.CONTINUE;
            }
            if (xenomorph.tickCount - startedAt < BREAK_DURATION_TICKS) {
                xenomorph.getLookControl().setLookAt(Vec3.atCenterOf(target));
                return Action.Signal.CONTINUE;
            }
            // Down it comes. Dropping the item keeps the anchor recoverable rather than simply deleted.
            xenomorph.level().destroyBlock(target, true, xenomorph);
            blackboard.set(KEY_TARGET_ANCHOR, null);
            blackboard.set(KEY_WORK_START_TICK, -1);
            return Action.Signal.CONTINUE;
        }

        // ⚠ target is reassigned above, so it cannot be captured by a lambda directly.
        var anchorTarget = target;
        var result = com.alien.common.gameplay.hive.diag.DiagProfiler.timed(
            "path/BreakAnchorAction",
            () -> NeoMoveToPosAction.perform(
                context,
                // Oct 2 - an anchor across the hive is reached through the ducts when that is shorter.
                com.alien.common.gameplay.hive.vent.DuctRouting.stepToward(xenomorph, Vec3.atBottomCenterOf(anchorTarget)),
                0.5
            )
        );
        return switch (result) {
            case MOVING, FINISHED -> Action.Signal.CONTINUE;
            default -> {
                // Unreachable from here - forget it so the sensor can offer a different anchor next time.
                blackboard.set(KEY_TARGET_ANCHOR, null);
                blackboard.set(KEY_WORK_START_TICK, -1);
                yield Action.Signal.ABORT;
            }
        };
    }

    public static void onFinish(Action.Context<? extends Xenomorph> context) {
        NeoMoveToPosAction.onFinish(context);
    }
}
