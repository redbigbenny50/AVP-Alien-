package com.alien.common.gameplay.entity.living.alien.xenomorph.praetorian.ai;

import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.XenomorphGOAP;
import com.alien.common.gameplay.entity.living.alien.xenomorph.praetorian.Praetorian;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;

public class PraetorianGOAP {

    public static final Graph<Praetorian> GRAPH = Graph.<Praetorian>builder()
        .apply(XenomorphGOAP::applyBaseGraph)
        .apply(XenomorphGOAP::addAnchorBreakPackage)
        .build();

    public static Agent.Builder<Praetorian> applyAgentProperties(Agent.Builder<Praetorian> agentBuilder) {
        return XenomorphGOAP.applyBaseAgentProperties(agentBuilder);
    }

    private PraetorianGOAP() {
        throw new UnsupportedOperationException();
    }
}
