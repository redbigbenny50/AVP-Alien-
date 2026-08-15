package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.combat;

import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
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

public class TriggeredAttackGOAP {

    public static <T extends Xenomorph> Graph.Builder<T> applyTriggeredAttack(Graph.Builder<T> graphBuilder, AttackType attack) {
        return applyTriggeredAttack(graphBuilder, attack, true);
    }

    /**
     * Variant for a triggered attack whose whole purpose is to act when the target is NOT reachable - the harbinger's
     * barrier-breaking front kick being the case it was written for. Such an attack is aimed at the world rather than
     * at the target, so the two gates that assume a clean approach are dropped: line-of-sight (there is a wall in the
     * way - that IS the trigger) and melee range (she is kicking the wall, not the thing behind it). Its own
     * {@code activationCondition} carries the real precondition.
     */
    public static <T extends Xenomorph> Graph.Builder<T> applyBarrierAttack(Graph.Builder<T> graphBuilder, AttackType attack) {
        return applyTriggeredAttack(graphBuilder, attack, false);
    }

    private static <T extends Xenomorph> Graph.Builder<T> applyTriggeredAttack(
        Graph.Builder<T> graphBuilder,
        AttackType attack,
        boolean requireDirectApproach
    ) {
        var canAttackKey = StateKey.<Boolean>sensed("can_attack_" + attack.id());
        var startedKey = StateKey.<Boolean>sensed("triggered_attack_started_" + attack.id());

        var canAttackSensor = Sensors.compose(
            GOAPSensors.NEAREST_ATTACKABLE_TARGET.key(),
            canAttackKey,
            (Xenomorph xenomorph, Option<LivingEntity> attackTargetOption) -> {
                if (attackTargetOption.isNone()) {
                    return false;
                }

                if (!xenomorph.getCooldownTracker().isReady(attack)) {
                    return false;
                }

                if (requireDirectApproach && !xenomorph.getSensing().hasLineOfSight(attackTargetOption.unwrap())) {
                    return false;
                }

                return xenomorph.canUseAttack(attack);
            }
        );

        var goal = Goal.builder("TriggeredAttackGoal_" + attack.id())
            .addPrecondition(canAttackKey, Expressions.Boolean.isTrue())
            .addDesiredCondition(canAttackKey.asDerived(), Expressions.Boolean.isFalse())
            .build();

        // Two full chains rather than one conditionally-extended builder: the melee-range precondition is the
        // only difference, and building each variant outright avoids relying on the builder mutating in place.
        var goapAction = requireDirectApproach
            ? BLibAction.<T>builder("TriggeredAttackAction_" + attack.id())
                .addMasks(ActionMasks.MOVE, ActionMasks.LOOK)
                .addPrecondition(GOAPSensors.HAS_ATTACK_TARGET.key(), Expressions.Boolean.isTrue())
                .addPrecondition(CombatSensors.IS_TARGET_IN_MELEE_RANGE.key(), Expressions.Boolean.isTrue())
                .addPrecondition(canAttackKey, Expressions.Boolean.isTrue())
                .addEffect(canAttackKey.asDerived(), false)
                .withCost(-2.0F)
                .withPerformCallback(context -> perform(context, attack, startedKey))
                .build()
            : BLibAction.<T>builder("TriggeredAttackAction_" + attack.id())
                .addMasks(ActionMasks.MOVE, ActionMasks.LOOK)
                .addPrecondition(GOAPSensors.HAS_ATTACK_TARGET.key(), Expressions.Boolean.isTrue())
                .addPrecondition(canAttackKey, Expressions.Boolean.isTrue())
                .addEffect(canAttackKey.asDerived(), false)
                .withCost(-2.0F)
                .withPerformCallback(context -> perform(context, attack, startedKey))
                .build();

        graphBuilder.addGoal(goal);
        graphBuilder.addAction(goapAction);
        graphBuilder.addSensor(canAttackSensor);

        return graphBuilder;
    }

    private static <T extends Xenomorph> Action.Signal perform(
        Action.Context<? extends T> context,
        AttackType attack,
        StateKey<Boolean> startedKey
    ) {
        var xenomorph = context.getActor();
        var worldState = context.getWorldState();
        var blackboard = context.getBlackboard(Blackboard.Scope.ACTION);
        var attackTargetOption = worldState.getOrDefault(GOAPSensors.NEAREST_ATTACKABLE_TARGET.key(), Option.<LivingEntity>none());
        var attackStarted = blackboard.getOrDefault(startedKey, false);

        if (attackStarted) {
            if (xenomorph.isAttacking() && xenomorph.attackType.get() == attack) {
                return Action.Signal.CONTINUE;
            }

            blackboard.set(startedKey, false);
            return Action.Signal.ABORT;
        }

        if (attackTargetOption.isNone() || !xenomorph.getCooldownTracker().isReady(attack) || !xenomorph.canUseAttack(attack)) {
            return Action.Signal.ABORT;
        }

        var target = attackTargetOption.unwrap();

        xenomorph.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        xenomorph.getLookControl().setLookAt(target);
        xenomorph.getNavigation().stop();

        xenomorph.startAttack(attack, target);
        blackboard.set(startedKey, true);
        return Action.Signal.CONTINUE;
    }

    private TriggeredAttackGOAP() {
        throw new UnsupportedOperationException();
    }
}
