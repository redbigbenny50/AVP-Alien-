package com.alien.common.gameplay.entity.living.alien.xenomorph.crusher.ai;

import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.XenomorphGOAP;
import com.alien.common.gameplay.entity.living.alien.xenomorph.crusher.Crusher;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;

public class CrusherGOAP {

    public static final Graph<Crusher> GRAPH = Graph.<Crusher>builder()
        .apply(XenomorphGOAP::applyBaseGraph)
        .apply(CrusherChargeGOAP::apply)
        .build();

    public static Agent.Builder<Crusher> applyAgentProperties(Agent.Builder<Crusher> agentBuilder) {
        return XenomorphGOAP.applySpecialAttackAgentProperties(agentBuilder);
    }

    private CrusherGOAP() {
        throw new UnsupportedOperationException();
    }
}
