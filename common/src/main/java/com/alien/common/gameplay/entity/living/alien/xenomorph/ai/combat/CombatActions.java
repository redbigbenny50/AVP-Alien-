package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.combat;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.combat.action.MeleeAttackAction;
import com.blib.api.common.goap.v1.GOAPSensors;
import com.blib.api.common.goap.v1.action.ActionMasks;
import com.blib.api.common.goap.v1.action.BLibAction;
import com.blib.api.common.goap.v1.action.impl.MoveToPosAction;
import com.blib.api.common.goap.v1.action.impl.NeoMoveToPosAction;
import com.blib.api.common.pathfinding.v1.navigator.PathNavigatorUser;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.action.Action;
import com.just.ai.goap.condition.expression.Expressions;
import com.just.ai.goap.state.Blackboard;
import com.just.core.functional.option.Option;

public class CombatActions {

    private static final StateKey<Double> KEY_LAST_DISTANCE_TO_TARGET = StateKey.sensed("move_last_distance");

    private static final StateKey<Integer> KEY_LAST_PROGRESS_TICK = StateKey.sensed("move_last_progress_tick");

    private static final int STUCK_THRESHOLD_IN_TICKS = 20;

    private static final double PROGRESS_THRESHOLD = 0.5;

    public static final Action<Xenomorph> MOVE_TO_TARGET = BLibAction.<Xenomorph>builder("MoveToTargetAction")
        .addMasks(ActionMasks.MOVE)
        .addPrecondition(GOAPSensors.HAS_ATTACK_TARGET.key(), Expressions.Boolean.isTrue())
        .addPrecondition(CombatSensors.IS_TARGET_IN_MELEE_RANGE.key(), Expressions.Boolean.isFalse())
        .addEffect(CombatSensors.IS_TARGET_IN_MELEE_RANGE.key().asDerived(), true)
        .withPerformCallback(CombatActions::performMoveToTarget)
        .withFinishCallback(CombatActions::finishMoveToTarget)
        .build();

    public static final Action<Xenomorph> MELEE_ATTACK = BLibAction.<Xenomorph>builder("MeleeAttackAction")
        .addMasks(ActionMasks.MOVE, ActionMasks.LOOK)
        .addPrecondition(GOAPSensors.HAS_ATTACK_TARGET.key(), Expressions.Boolean.isTrue())
        .addPrecondition(CombatSensors.IS_TARGET_IN_MELEE_RANGE.key(), Expressions.Boolean.isTrue())
        .addEffect(GOAPSensors.HAS_ATTACK_TARGET.key().asDerived(), false)
        .withPerformCallback(MeleeAttackAction::perform)
        .build();

    public static Action.Signal performMoveToTarget(Action.Context<? extends Xenomorph> context) {
        var xenomorph = context.getActor();
        var worldState = context.getWorldState();
        var attackTargetOption = worldState.getOrDefault(GOAPSensors.NEAREST_ATTACKABLE_TARGET.key(), Option.none());

        if (attackTargetOption.isNone()) {
            return Action.Signal.ABORT;
        }

        var attackTarget = attackTargetOption.unwrap();

        xenomorph.getLookControl().setLookAt(attackTarget);

        if (xenomorph instanceof PathNavigatorUser) {
            return performWithBLibNav(context, attackTarget);
        }

        return performWithVanillaNav(context, attackTarget);
    }

    public static void finishMoveToTarget(Action.Context<? extends Xenomorph> context) {
        if (context.getActor() instanceof PathNavigatorUser) {
            NeoMoveToPosAction.onFinish(context);
        } else {
            MoveToPosAction.onFinish(context);
        }
    }

    private static final double MAX_PREDICTION_DISTANCE_SQUARED = 32.0 * 32.0;

    private static Action.Signal performWithBLibNav(
        Action.Context<? extends Xenomorph> context,
        net.minecraft.world.entity.LivingEntity attackTarget
    ) {
        var xenomorph = context.getActor();

        var interceptPos = computeInterceptPoint(xenomorph, attackTarget);
        var result = NeoMoveToPosAction.perform(context, interceptPos, 1.1, true);

        return switch (result) {
            case FINISHED, MOVING -> Action.Signal.CONTINUE;
            case NO_PATH -> Action.Signal.ABORT;
            default -> Action.Signal.ABORT;
        };
    }

    private static net.minecraft.world.phys.Vec3 computeInterceptPoint(
        Xenomorph xenomorph,
        net.minecraft.world.entity.LivingEntity target
    ) {
        var targetPos = target.position();
        var targetVelocity = target.getDeltaMovement();

        var horizontalVelocity = new net.minecraft.world.phys.Vec3(targetVelocity.x, 0, targetVelocity.z);
        var horizontalSpeed = horizontalVelocity.length();

        if (horizontalSpeed < 0.01) {
            return targetPos;
        }

        var distance = xenomorph.distanceTo(target);
        var xenomorphSpeed = xenomorph.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);

        if (xenomorphSpeed < 0.01) {
            return targetPos;
        }

        var estimatedTicksToArrive = distance / xenomorphSpeed;
        var predictedPos = targetPos.add(horizontalVelocity.scale(estimatedTicksToArrive));

        if (predictedPos.distanceToSqr(targetPos) > MAX_PREDICTION_DISTANCE_SQUARED) {
            var direction = predictedPos.subtract(targetPos).normalize();

            predictedPos = targetPos.add(direction.scale(32.0));
        }

        return predictedPos;
    }

    private static Action.Signal performWithVanillaNav(
        Action.Context<? extends Xenomorph> context,
        net.minecraft.world.entity.LivingEntity attackTarget
    ) {
        var xenomorph = context.getActor();
        var blackboard = context.getBlackboard(Blackboard.Scope.ACTION);

        var currentDistance = xenomorph.distanceToSqr(attackTarget);
        var lastDistance = blackboard.getOrDefault(KEY_LAST_DISTANCE_TO_TARGET, Double.MAX_VALUE);
        var lastProgressTick = blackboard.getOrDefault(KEY_LAST_PROGRESS_TICK, xenomorph.tickCount);

        if (currentDistance < lastDistance - PROGRESS_THRESHOLD) {
            blackboard.set(KEY_LAST_DISTANCE_TO_TARGET, currentDistance);
            blackboard.set(KEY_LAST_PROGRESS_TICK, xenomorph.tickCount);
        } else if (xenomorph.tickCount - lastProgressTick >= STUCK_THRESHOLD_IN_TICKS) {
            return Action.Signal.ABORT;
        }

        return switch (MoveToPosAction.perform(context, attackTarget.position(), 1.1)) {
            case FINISHED, MOVING -> Action.Signal.CONTINUE;
            case NO_PATH -> Action.Signal.ABORT;
        };
    }

    private CombatActions() {
        throw new UnsupportedOperationException();
    }
}
