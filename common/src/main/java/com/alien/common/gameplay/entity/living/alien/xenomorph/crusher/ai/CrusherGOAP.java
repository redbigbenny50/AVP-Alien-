package com.alien.common.gameplay.entity.living.alien.xenomorph.crusher.ai;

import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.XenomorphGOAP;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.lunge.LungeConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.crusher.Crusher;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;

public class CrusherGOAP {

    private static final LungeConfig LUNGE_CONFIG = new LungeConfig(6, 12, 20 * 7);

    public static final Graph<Crusher> GRAPH = Graph.<Crusher>builder()
        .apply(XenomorphGOAP::applyBaseGraph)
        .apply(b -> XenomorphGOAP.addLungePackage(b, LUNGE_CONFIG))
        .apply(XenomorphGOAP::addAnchorBreakPackage)
        .build();

    public static Agent.Builder<Crusher> applyAgentProperties(Agent.Builder<Crusher> agentBuilder) {
        return XenomorphGOAP.applyBaseAgentProperties(agentBuilder);
    }

    private CrusherGOAP() {
        throw new UnsupportedOperationException();
    }
}
