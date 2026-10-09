package com.alien.common.gameplay.entity.living.alien.xenomorph.carrier.ai;

import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.XenomorphGOAP;
import com.alien.common.gameplay.entity.living.alien.xenomorph.carrier.Carrier;
import com.alien.common.gameplay.entity.living.alien.xenomorph.carrier.ai.panic_release.PanicReleaseActions;
import com.alien.common.gameplay.entity.living.alien.xenomorph.carrier.ai.panic_release.PanicReleaseGoals;
import com.alien.common.gameplay.entity.living.alien.xenomorph.carrier.ai.panic_release.PanicReleaseSensors;
import com.alien.common.gameplay.entity.living.alien.xenomorph.carrier.ai.throw_attack.ThrowAttackActions;
import com.alien.common.gameplay.entity.living.alien.xenomorph.carrier.ai.throw_attack.ThrowAttackSensors;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;

public class CarrierGOAP {

    public static final Graph<Carrier> GRAPH = Graph.<Carrier>builder()
        .apply(XenomorphGOAP::addSensorsPackage)
        .apply(CarrierGOAP::addCombatPackage)
        // !! THIS CASTE HAD NO SWIM PACKAGE. addSwimPackage was added to applyBaseGraph, but the spitter,
        // the carrier and the chrysalis build their graphs by hand and were never given it - so those three
        // could not reach shore at all, whatever else was fixed.
        .apply(XenomorphGOAP::addSwimPackage)
        .apply(XenomorphGOAP::addIdlePackage)
        .apply(CarrierGOAP::addPanicReleasePackage)
        .apply(CarrierGOAP::addThrowPackage)
        .build();

    public static Agent.Builder<Carrier> applyAgentProperties(Agent.Builder<Carrier> agentBuilder) {
        return XenomorphGOAP.applyBaseAgentProperties(agentBuilder);
    }

    private static Graph.Builder<Carrier> addCombatPackage(Graph.Builder<Carrier> graphBuilder) {
        XenomorphGOAP.addCombatPackageWithoutMove(graphBuilder);
        graphBuilder.addAction(CarrierCombatActions.MOVE_TO_TARGET);
        return graphBuilder;
    }

    private static Graph.Builder<Carrier> addThrowPackage(Graph.Builder<Carrier> graphBuilder) {
        graphBuilder.addAction(ThrowAttackActions.THROW_FACEHUGGER);
        graphBuilder.addSensor(ThrowAttackSensors.HAS_RIDING_FACEHUGGER);
        graphBuilder.addSensor(ThrowAttackSensors.IS_THROW_COOLDOWN_READY);
        graphBuilder.addSensor(ThrowAttackSensors.CAN_THROW_FACEHUGGER);
        return graphBuilder;
    }

    private static Graph.Builder<Carrier> addPanicReleasePackage(Graph.Builder<Carrier> graphBuilder) {
        graphBuilder.addGoal(PanicReleaseGoals.PANIC_RELEASE_FACEHUGGERS);
        graphBuilder.addAction(PanicReleaseActions.PANIC_RELEASE_FACEHUGGERS);
        graphBuilder.addSensor(PanicReleaseSensors.IS_LOW_HEALTH);
        graphBuilder.addSensor(PanicReleaseSensors.IS_SURROUNDED_BY_HOSTS);
        graphBuilder.addSensor(PanicReleaseSensors.HAS_RELEASE_FACEHUGGER_COUNT);
        graphBuilder.addSensor(PanicReleaseSensors.SHOULD_PANIC_RELEASE);
        return graphBuilder;
    }

    private CarrierGOAP() {
        throw new UnsupportedOperationException();
    }
}
