package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.anchor_break;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.anchor_break.action.BreakAnchorAction;
import com.blib.api.common.goap.v1.GOAPSensors;
import com.blib.api.common.goap.v1.action.ActionMasks;
import com.blib.api.common.goap.v1.action.BLibAction;
import com.just.ai.goap.action.Action;
import com.just.ai.goap.condition.expression.Expressions;
import com.just.ai.goap.goal.Goal;

/** The anchor-demolition GOAP package: royal defenders tear capture anchors out of their own hive. */
public final class AnchorBreakActions {

    private AnchorBreakActions() {}

    /**
     * Gated on having no attack target: a live intruder always outranks masonry. A defender finishes the fight, then
     * goes back to the anchor.
     */
    public static final Action<Xenomorph> BREAK_ANCHOR = BLibAction.<Xenomorph>builder("BreakAnchorAction")
        .addMasks(ActionMasks.MOVE)
        .addPrecondition(GOAPSensors.HAS_ATTACK_TARGET.key(), Expressions.Boolean.isFalse())
        .addPrecondition(AnchorBreakSensors.HAS_TARGET_ANCHOR.key(), Expressions.Boolean.isTrue())
        .addEffect(AnchorBreakSensors.HAS_TARGET_ANCHOR.key().asDerived(), false)
        .withPerformCallback(BreakAnchorAction::perform)
        .withFinishCallback(BreakAnchorAction::onFinish)
        .build();

    public static final Goal BREAK_ANCHOR_GOAL = Goal.builder("BreakAnchorGoal")
        .addPrecondition(GOAPSensors.HAS_ATTACK_TARGET.key(), Expressions.Boolean.isFalse())
        .addPrecondition(AnchorBreakSensors.HAS_TARGET_ANCHOR.key(), Expressions.Boolean.isTrue())
        .addDesiredCondition(AnchorBreakSensors.HAS_TARGET_ANCHOR.key().asDerived(), Expressions.Boolean.isFalse())
        .build();
}
