package com.alien.common.gameplay.entity.living.alien.xenomorph.queen.ai.location_move;

import com.blib.api.common.goap.v1.GOAPSensors;
import com.just.ai.goap.condition.expression.Expressions;
import com.just.ai.goap.goal.Goal;

/**
 * Goal for the location-phase dig-to-anchor behaviour (Stage 2b).
 * <p>
 * {@link #BE_AT_ANCHOR} wants a locating queen to reach her committed anchor. It is eligible only while she is locating
 * and not already there, and — exactly like {@code FoundingMoveGoals.BE_AT_CENTER} — it stands down whenever she has an
 * attack target, so combat/self-defence takes over and the dig resumes once the target is gone. (Her clip/lava/wall
 * protection is held by the phase manager for the whole LOCATION window, so a combat interruption pauses the dig
 * without exposing her to suffocation mid-stone.)
 */
public final class LocationMoveGoals {

    public static final Goal BE_AT_ANCHOR = Goal.builder("BeAtLocationAnchorGoal")
        .addPrecondition(LocationMoveSensors.IS_LOCATING.key(), Expressions.Boolean.isTrue())
        .addPrecondition(LocationMoveSensors.IS_AT_ANCHOR.key(), Expressions.Boolean.isFalse())
        .addPrecondition(GOAPSensors.HAS_ATTACK_TARGET.key(), Expressions.Boolean.isFalse())
        .addDesiredCondition(LocationMoveSensors.IS_AT_ANCHOR.key().asDerived(), Expressions.Boolean.isTrue())
        .build();

    private LocationMoveGoals() {
        throw new UnsupportedOperationException();
    }
}
