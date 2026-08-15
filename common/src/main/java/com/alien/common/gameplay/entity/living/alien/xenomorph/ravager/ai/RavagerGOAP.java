package com.alien.common.gameplay.entity.living.alien.xenomorph.ravager.ai;

import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.XenomorphGOAP;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.combat.TriggeredAttackGOAP;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ravager.Ravager;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ravager.RavagerSpecialCleaveAttack;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;

public class RavagerGOAP {

    public static final Graph<Ravager> GRAPH = Graph.<Ravager>builder()
        .apply(XenomorphGOAP::applyBaseGraph)
        .apply(graph -> TriggeredAttackGOAP.applyTriggeredAttack(graph, RavagerSpecialCleaveAttack.ATTACK))
        .build();

    public static Agent.Builder<Ravager> applyAgentProperties(Agent.Builder<Ravager> agentBuilder) {
        return XenomorphGOAP.applySpecialAttackAgentProperties(agentBuilder);
    }

    private RavagerGOAP() {
        throw new UnsupportedOperationException();
    }
}
