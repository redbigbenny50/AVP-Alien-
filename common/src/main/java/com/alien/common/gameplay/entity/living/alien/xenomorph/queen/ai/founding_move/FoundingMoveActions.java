package com.alien.common.gameplay.entity.living.alien.xenomorph.queen.ai.founding_move;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.blib.api.common.goap.v1.GOAPSensors;
import com.blib.api.common.goap.v1.action.ActionMasks;
import com.blib.api.common.goap.v1.action.BLibAction;
import com.blib.api.common.goap.v1.action.impl.NeoMoveToPosAction;
import com.just.ai.goap.action.Action;
import com.just.ai.goap.condition.expression.Expressions;
import com.just.ai.goap.state.Blackboard;
import net.minecraft.world.phys.Vec3;

/**
 * Action for the founding navigate-to-center behaviour (Option B).
 * <p>
 * {@link #GO_TO_CENTER} paths a founding queen toward her {@code centerPos} using the same BLib navigation primitive
 * the idle and combat moves use. Every xenomorph is a {@code PathNavigatorUser}, so {@link NeoMoveToPosAction} is used
 * unconditionally (as in {@code IdleActions}).
 * <p>
 * It is given a small negative cost so its plan is preferred over idle wandering when both are eligible — this is the
 * same idiom the triggered-attack and roll actions use to win priority. It does <em>not</em> outrank combat: the
 * {@code HAS_ATTACK_TARGET == false} precondition removes it from contention while she has a target, so self-defense
 * and in-claim pursuit (already leashed to her claimed chunks by {@code XenomorphTargetSensors}) take over instead.
 * <p>
 * The location is re-resolved every tick by founder UUID, so the action keeps working if the queen is displaced outside
 * her claim and aborts cleanly the instant she becomes reproductive or the location dies.
 */
public final class FoundingMoveActions {

    /** Purposeful but unhurried return pace (idle wander is 0.5; combat pursuit is 1.1). */
    private static final double MOVE_SPEED = 1.0;

    /**
     * Negative so the planner prefers heading home over idle wandering. Kept shallower than combat/triggered-attack
     * costs so anything combat-related still wins.
     */
    private static final float COST = -1.0F;

    public static final Action<Xenomorph> GO_TO_CENTER = BLibAction.<Xenomorph>builder("GoToFoundingCenterAction")
        .addMasks(ActionMasks.MOVE)
        .addPrecondition(GOAPSensors.HAS_ATTACK_TARGET.key(), Expressions.Boolean.isFalse())
        .addPrecondition(FoundingMoveSensors.IS_FOUNDING.key(), Expressions.Boolean.isTrue())
        .addPrecondition(FoundingMoveSensors.IS_AT_CENTER.key(), Expressions.Boolean.isFalse())
        .addEffect(FoundingMoveSensors.IS_AT_CENTER.key().asDerived(), true)
        .withCost(COST)
        .withPerformCallback(FoundingMoveActions::performGoToCenter)
        .withFinishCallback(context -> {
            NeoMoveToPosAction.onFinish(context);
            context.getBlackboard(Blackboard.Scope.ACTION).clear();
        })
        .build();

    private static Action.Signal performGoToCenter(Action.Context<? extends Xenomorph> context) {
        var actor = context.getActor();
        var location = FoundingMoveSensors.foundingLocationOrNull(actor);

        // No longer founding (became reproductive, location died, or behaviour disabled) — let GOAP replan.
        if (location == null) {
            return Action.Signal.ABORT;
        }

        var target = Vec3.atBottomCenterOf(location.centerPos());
        var result = NeoMoveToPosAction.perform(context, target, MOVE_SPEED);

        return switch (result) {
            // FINISHED: arrived. IS_AT_CENTER flips true next tick and the goal is satisfied, so we just yield.
            case FINISHED, MOVING -> Action.Signal.CONTINUE;
            // NO_PATH: can't currently reach center (mid-air after knockback, obstacle). Abort and let her replan;
            // the snap-to-center commit backstop still guarantees a correct founding position when the tank fills.
            case NO_PATH -> Action.Signal.ABORT;
            default -> Action.Signal.ABORT;
        };
    }

    private FoundingMoveActions() {
        throw new UnsupportedOperationException();
    }
}
