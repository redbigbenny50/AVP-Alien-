package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.swim;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.combat.CombatSensors;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.swim.action.SwimToLandAction;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.swim.action.SwimToShoreAction;
import com.blib.api.common.goap.v1.GOAPSensors;
import com.blib.api.common.goap.v1.action.ActionMasks;
import com.blib.api.common.goap.v1.action.BLibAction;
import com.just.ai.goap.action.Action;
import com.just.ai.goap.condition.expression.Expressions;
import com.just.ai.goap.goal.Goal;

public class SwimActions {

    public static final Action<Xenomorph> SWIM_TO_LAND = BLibAction.<Xenomorph>builder("SwimToLandAction")
        .addMasks(ActionMasks.MOVE, ActionMasks.LOOK)
        .addPrecondition(GOAPSensors.HAS_ATTACK_TARGET.key(), Expressions.Boolean.isTrue())
        .addPrecondition(SwimSensors.NEEDS_WATER_TO_LAND_TRANSITION.key(), Expressions.Boolean.isTrue())
        .addPrecondition(CombatSensors.IS_TARGET_IN_MELEE_RANGE.key(), Expressions.Boolean.isFalse())
        .addEffect(CombatSensors.IS_TARGET_IN_MELEE_RANGE.key().asDerived(), true)
        .withPerformCallback(SwimToLandAction::perform)
        .build();

    /**
     * ⭐⭐ THE IDLE CROSSING. Gated on {@code IS_STRANDED_IN_WATER} alone, so it needs no target and no hive — a
     * xenomorph dropped in a lake now has somewhere to be.
     * </p>
     */
    public static final Action<Xenomorph> SWIM_TO_SHORE = BLibAction.<Xenomorph>builder("SwimToShoreAction")
        .addMasks(ActionMasks.MOVE, ActionMasks.LOOK)
        .addPrecondition(SwimSensors.IS_STRANDED_IN_WATER.key(), Expressions.Boolean.isTrue())
        .addEffect(SwimSensors.IS_STRANDED_IN_WATER.key().asDerived(), false)
        .withPerformCallback(SwimToShoreAction::perform)
        .build();

    /**
     * ⚠ RANKED BELOW COMBAT BY ITS PRECONDITION, NOT BY A PRIORITY NUMBER. The goal requires NO attack target, so the
     * instant a xenomorph acquires one this goal stops being satisfiable and the combat plan takes over mid-swim. That
     * is also why it cannot fight {@code SWIM_TO_LAND}, which requires the opposite.
     * </p>
     */
    public static final Goal REACH_DRY_LAND = Goal.builder("ReachDryLandGoal")
        .addPrecondition(SwimSensors.IS_STRANDED_IN_WATER.key(), Expressions.Boolean.isTrue())
        .addPrecondition(GOAPSensors.HAS_ATTACK_TARGET.key(), Expressions.Boolean.isFalse())
        .addDesiredCondition(SwimSensors.IS_STRANDED_IN_WATER.key().asDerived(), Expressions.Boolean.isFalse())
        .build();

    private SwimActions() {
        throw new UnsupportedOperationException();
    }
}
