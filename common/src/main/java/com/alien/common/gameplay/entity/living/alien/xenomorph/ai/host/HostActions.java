package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host.action.CaptureHostAction;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host.action.DeliverHostAction;
import com.blib.api.common.goap.v1.GOAPSensors;
import com.blib.api.common.goap.v1.action.ActionMasks;
import com.blib.api.common.goap.v1.action.BLibAction;
import com.just.ai.goap.action.Action;
import com.just.ai.goap.condition.expression.Expressions;
import com.just.ai.goap.goal.Goal;

/** The host-hunt GOAP package: walk to a host, grab it, carry it to a vent, hand it into the chamber. */
public final class HostActions {

    private HostActions() {}

    public static final Action<Xenomorph> CAPTURE_HOST = BLibAction.<Xenomorph>builder("CaptureHostAction")
        .addMasks(ActionMasks.MOVE)
        .addPrecondition(GOAPSensors.HAS_ATTACK_TARGET.key(), Expressions.Boolean.isFalse())
        .addPrecondition(HostSensors.HAS_TARGET_HOST.key(), Expressions.Boolean.isTrue())
        .addPrecondition(HostSensors.IS_CARRYING_HOST.key(), Expressions.Boolean.isFalse())
        .addEffect(HostSensors.IS_CARRYING_HOST.key().asDerived(), true)
        .withPerformCallback(CaptureHostAction::perform)
        .withFinishCallback(CaptureHostAction::onFinish)
        .build();

    public static final Action<Xenomorph> DELIVER_HOST = BLibAction.<Xenomorph>builder("DeliverHostAction")
        .addMasks(ActionMasks.MOVE)
        .addPrecondition(HostSensors.IS_CARRYING_HOST.key(), Expressions.Boolean.isTrue())
        .addEffect(HostSensors.IS_CARRYING_HOST.key().asDerived(), false)
        .withPerformCallback(DeliverHostAction::perform)
        .withFinishCallback(DeliverHostAction::onFinish)
        .build();

    public static final Goal CAPTURE_HOST_GOAL = Goal.builder("CaptureHostGoal")
        .addPrecondition(GOAPSensors.HAS_ATTACK_TARGET.key(), Expressions.Boolean.isFalse())
        .addPrecondition(HostSensors.HAS_TARGET_HOST.key(), Expressions.Boolean.isTrue())
        .addPrecondition(HostSensors.IS_CARRYING_HOST.key(), Expressions.Boolean.isFalse())
        .addDesiredCondition(HostSensors.IS_CARRYING_HOST.key().asDerived(), Expressions.Boolean.isTrue())
        .build();

    /**
     * Delivering a captured host is NOT gated on having no attack target: a drone that gets jumped while carrying a
     * villager must still finish the delivery rather than drop everything to fight.
     */
    public static final Goal DELIVER_HOST_GOAL = Goal.builder("DeliverHostGoal")
        .addPrecondition(HostSensors.IS_CARRYING_HOST.key(), Expressions.Boolean.isTrue())
        .addDesiredCondition(HostSensors.IS_CARRYING_HOST.key().asDerived(), Expressions.Boolean.isFalse())
        .build();
}
