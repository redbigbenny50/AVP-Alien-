package com.alien.common.gameplay.entity.living.alien.xenomorph.chrysalis.ai;

import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.XenomorphGOAP;
import com.alien.common.gameplay.entity.living.alien.xenomorph.chrysalis.Chrysalis;
import com.alien.common.gameplay.entity.living.alien.xenomorph.chrysalis.ai.roll.RollActions;
import com.alien.common.gameplay.entity.living.alien.xenomorph.chrysalis.ai.roll.RollConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.chrysalis.ai.roll.RollSensors;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;

public class ChrysalisGOAP {

    public static final Graph<Chrysalis> GRAPH = Graph.<Chrysalis>builder()
        .apply(XenomorphGOAP::addSensorsPackage)
        .apply(XenomorphGOAP::addCombatPackageWithoutMove)
        // [stated] "chrystalys should swim if its in deep water enough to submerge. when its in a defense ball it
        // shouldnt be affected by a bucket or 1 block deep of water but it should absolutely try to swim if in a
        // possible water feature."
        //
        // SAFE ALONGSIDE THE ROLL AND ITS BESPOKE MOVE, because both of those require an attack TARGET and the idle
        // crossing requires the absence of one - the same exact-complement trick that keeps SWIM_TO_SHORE and
        // SWIM_TO_LAND from ever competing. And the depth gate in SwimSensors.isSubmerged is what keeps a balled
        // chrysalis in a bucket or a one-deep puddle from deciding it needs to swim.
        .apply(XenomorphGOAP::addSwimPackage)
        .apply(XenomorphGOAP::addIdlePackage)
        .apply(ChrysalisGOAP::addRollPackage)
        .apply(ChrysalisGOAP::addChrysalisMovePackage)
        .build();

    public static Agent.Builder<Chrysalis> applyAgentProperties(Agent.Builder<Chrysalis> agentBuilder) {
        return XenomorphGOAP.applyBaseAgentProperties(agentBuilder);
    }

    private static Graph.Builder<Chrysalis> addRollPackage(Graph.Builder<Chrysalis> graphBuilder) {
        var rollSensor = RollSensors.createRollRangeSensor(RollConfig.DEFAULT);

        graphBuilder.addAction(RollActions.createRollAtTarget(rollSensor.key()));
        graphBuilder.addSensor(rollSensor);

        return graphBuilder;
    }

    private static Graph.Builder<Chrysalis> addChrysalisMovePackage(Graph.Builder<Chrysalis> graphBuilder) {
        graphBuilder.addAction(ChrysalisCombatActions.MOVE_TO_TARGET);
        return graphBuilder;
    }

    private ChrysalisGOAP() {
        throw new UnsupportedOperationException();
    }
}
