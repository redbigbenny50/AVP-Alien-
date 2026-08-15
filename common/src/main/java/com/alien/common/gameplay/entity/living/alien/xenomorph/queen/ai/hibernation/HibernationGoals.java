package com.alien.common.gameplay.entity.living.alien.xenomorph.queen.ai.hibernation;

import com.just.ai.goap.condition.expression.Expressions;
import com.just.ai.goap.goal.Goal;

/**
 * Goals for hibernation (Stages 3a + 3b). {@link #HIBERNATE} keeps the hold selected for the whole sleep;
 * {@link #RETURN_TO_ANCHOR} keeps the walk-back selected after a disturbance clears. Each is a continuous "be done"
 * want the world never actually satisfies (the actions' effects are planner-only) — they end when the phase manager
 * flips the matching sub-state, which drops the precondition.
 */
public final class HibernationGoals {

    public static final Goal HIBERNATE = Goal.builder("HibernateGoal")
        .addPrecondition(HibernationSensors.IS_ASLEEP.key(), Expressions.Boolean.isTrue())
        .addDesiredCondition(HibernationSensors.IS_ASLEEP.key().asDerived(), Expressions.Boolean.isFalse())
        .build();

    public static final Goal RETURN_TO_ANCHOR = Goal.builder("HibernateReturnGoal")
        .addPrecondition(HibernationSensors.IS_RETURNING.key(), Expressions.Boolean.isTrue())
        .addDesiredCondition(HibernationSensors.IS_RETURNING.key().asDerived(), Expressions.Boolean.isFalse())
        .build();

    private HibernationGoals() {
        throw new UnsupportedOperationException();
    }
}
