package com.alien.common.gameplay.entity.living.alien.xenomorph.queen.ai.founding_move;

import com.blib.api.common.goap.v1.GOAPSensors;
import com.just.ai.goap.condition.expression.Expressions;
import com.just.ai.goap.goal.Goal;

/**
 * Goal for the founding navigate-to-center behaviour (Option B).
 * <p>
 * {@link #BE_AT_CENTER} wants a founding queen to be standing at her hive center. It is only eligible while she is
 * founding and not already there, and — exactly like {@code IdleGoals.SATISFY_BOREDOM} — it stands down whenever she
 * has an attack target. That {@code HAS_ATTACK_TARGET == false} precondition is what lets her break off to defend or
 * chase an intruder; combat takes over, and once the target is gone this goal re-activates and pulls her home.
 * <p>
 * Patrolling/wandering within the claim is handled by the ordinary idle package; that behaviour stays active when she
 * is already at center (this goal's preconditions no longer hold), and the cheap cost on {@code GO_TO_CENTER}
 * re-asserts the pull the moment she drifts away. The net effect is "free to patrol and defend inside the claim, but
 * always gravitating back to center".
 */
public final class FoundingMoveGoals {

    public static final Goal BE_AT_CENTER = Goal.builder("BeAtFoundingCenterGoal")
        .addPrecondition(FoundingMoveSensors.IS_FOUNDING.key(), Expressions.Boolean.isTrue())
        .addPrecondition(FoundingMoveSensors.IS_AT_CENTER.key(), Expressions.Boolean.isFalse())
        .addPrecondition(GOAPSensors.HAS_ATTACK_TARGET.key(), Expressions.Boolean.isFalse())
        .addDesiredCondition(FoundingMoveSensors.IS_AT_CENTER.key().asDerived(), Expressions.Boolean.isTrue())
        .build();

    private FoundingMoveGoals() {
        throw new UnsupportedOperationException();
    }
}
