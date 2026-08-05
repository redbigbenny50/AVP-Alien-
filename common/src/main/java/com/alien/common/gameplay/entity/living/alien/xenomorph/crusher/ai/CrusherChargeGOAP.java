package com.alien.common.gameplay.entity.living.alien.xenomorph.crusher.ai;

import com.alien.common.gameplay.entity.living.alien.xenomorph.crusher.Crusher;
import com.alien.common.gameplay.entity.living.alien.xenomorph.crusher.CrusherChargeAttack;
import com.blib.api.common.goap.v1.GOAPSensors;
import com.blib.api.common.goap.v1.action.ActionMasks;
import com.blib.api.common.goap.v1.action.BLibAction;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.action.Action;
import com.just.ai.goap.condition.expression.Expressions;
import com.just.ai.goap.goal.Goal;
import com.just.ai.goap.graph.Graph;
import com.just.ai.goap.sensor.Sensors;
import com.just.ai.goap.state.Blackboard;
import com.just.core.functional.option.Option;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** GOAP integration for CrusherChargeAttack, including targets hidden behind a wall. */
public final class CrusherChargeGOAP {

    private static final double ESCAPE_TRIGGER_RANGE_IN_BLOCKS = 6.0D;

    private static final double MAX_ESCAPE_CHARGE_RANGE_IN_BLOCKS = 25.0D;

    private static final double MINIMUM_AWAY_SPEED = 0.01D;

    public static Graph.Builder<Crusher> apply(Graph.Builder<Crusher> graphBuilder) {
        var canChargeKey = StateKey.<Boolean>sensed("can_crusher_charge");
        var startedKey = StateKey.<Boolean>sensed("crusher_charge_started");
        var canChargeSensor = Sensors.compose(
            GOAPSensors.NEAREST_ATTACKABLE_TARGET.key(),
            canChargeKey,
            (Crusher crusher, Option<LivingEntity> targetOption) -> {
                var target = chooseTarget(crusher, targetOption);
                return target != null
                    && crusher.getCooldownTracker().isReady(CrusherChargeAttack.ATTACK)
                    && crusher.canUseAttack(CrusherChargeAttack.ATTACK)
                    && isValidChargeTarget(crusher, target);
            }
        );
        var goal = Goal.builder("CrusherChargeGoal")
            .addPrecondition(canChargeKey, Expressions.Boolean.isTrue())
            .addDesiredCondition(canChargeKey.asDerived(), Expressions.Boolean.isFalse())
            .build();
        var action = BLibAction.<Crusher>builder("CrusherChargeAction")
            .addMasks(ActionMasks.MOVE, ActionMasks.LOOK)
            .addPrecondition(GOAPSensors.HAS_ATTACK_TARGET.key(), Expressions.Boolean.isTrue())
            .addPrecondition(canChargeKey, Expressions.Boolean.isTrue())
            .addEffect(canChargeKey.asDerived(), false)
            .withCost(-4.0F)
            .withPerformCallback(context -> perform(context, startedKey))
            .build();

        return graphBuilder.addGoal(goal).addAction(action).addSensor(canChargeSensor);
    }

    private static Action.Signal perform(Action.Context<? extends Crusher> context, StateKey<Boolean> startedKey) {
        var crusher = context.getActor();
        var blackboard = context.getBlackboard(Blackboard.Scope.ACTION);
        var targetOption = context.getWorldState()
            .getOrDefault(
                GOAPSensors.NEAREST_ATTACKABLE_TARGET.key(),
                Option.<LivingEntity>none()
            );

        if (blackboard.getOrDefault(startedKey, false)) {
            if (crusher.isExecutingTriggeredAttack()) {
                return Action.Signal.CONTINUE;
            }

            blackboard.set(startedKey, false);
            return Action.Signal.ABORT;
        }

        var target = chooseTarget(crusher, targetOption);

        if (
            target == null
                || !crusher.getCooldownTracker().isReady(CrusherChargeAttack.ATTACK)
                || !crusher.canUseAttack(CrusherChargeAttack.ATTACK)
                || !isValidChargeTarget(crusher, target)
        ) {
            return Action.Signal.ABORT;
        }

        crusher.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        crusher.getLookControl().setLookAt(target);
        crusher.getNavigation().stop();
        crusher.startAttack(CrusherChargeAttack.ATTACK, target);
        blackboard.set(startedKey, true);
        return Action.Signal.CONTINUE;
    }

    private static @Nullable LivingEntity chooseTarget(Crusher crusher, Option<LivingEntity> sensedTargetOption) {
        var currentTarget = crusher.getTarget();

        if (currentTarget != null && currentTarget.isAlive()) {
            return currentTarget;
        }

        return sensedTargetOption.isSome() ? sensedTargetOption.unwrap() : null;
    }

    private static boolean isValidChargeTarget(Crusher crusher, LivingEntity target) {
        if (!crusher.onGround()) {
            return false;
        }

        var dx = crusher.getX() - target.getX();
        var dz = crusher.getZ() - target.getZ();
        var horizontalDistanceSqr = dx * dx + dz * dz;
        var minRangeSqr = CrusherChargeAttack.MIN_VISIBLE_RANGE_IN_BLOCKS
            * CrusherChargeAttack.MIN_VISIBLE_RANGE_IN_BLOCKS;
        var targetIsEscaping = isEscapingFromCrusher(crusher, target, horizontalDistanceSqr);
        var maximumRange = targetIsEscaping
            ? MAX_ESCAPE_CHARGE_RANGE_IN_BLOCKS
            : CrusherChargeAttack.MAX_RANGE_IN_BLOCKS;
        var maxRangeSqr = maximumRange * maximumRange;

        if (horizontalDistanceSqr > maxRangeSqr) {
            return false;
        }

        if (Math.abs(crusher.getY() - target.getY()) > maximumRange) {
            return false;
        }

        var hit = crusher.level()
            .clip(
                new ClipContext(
                    crusher.getEyePosition(),
                    target.getEyePosition(),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    crusher
                )
            );
        var targetIsObscured = hit.getType() == HitResult.Type.BLOCK;
        return targetIsObscured || horizontalDistanceSqr >= minRangeSqr;
    }

    private static boolean isEscapingFromCrusher(Crusher crusher, LivingEntity target, double horizontalDistanceSqr) {
        if (horizontalDistanceSqr < ESCAPE_TRIGGER_RANGE_IN_BLOCKS * ESCAPE_TRIGGER_RANGE_IN_BLOCKS) {
            return false;
        }

        var awayFromCrusher = new Vec3(target.getX() - crusher.getX(), 0.0D, target.getZ() - crusher.getZ());
        var targetMovement = target.getDeltaMovement();
        var horizontalMovement = new Vec3(targetMovement.x, 0.0D, targetMovement.z);

        return awayFromCrusher.lengthSqr() > 0.001D
            && horizontalMovement.dot(awayFromCrusher.normalize()) >= MINIMUM_AWAY_SPEED;
    }

    private CrusherChargeGOAP() {
        throw new UnsupportedOperationException();
    }
}
