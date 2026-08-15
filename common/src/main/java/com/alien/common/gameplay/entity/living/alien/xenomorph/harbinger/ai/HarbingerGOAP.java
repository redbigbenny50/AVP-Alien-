package com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger.ai;

import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.XenomorphGOAP;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.combat.TriggeredAttackGOAP;
import com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger.Harbinger;
import com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger.HarbingerFrontKickAttack;
import com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger.HarbingerGroundSlamAttack;
import com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger.HarbingerKickAttack;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;

public class HarbingerGOAP {

    public static final Graph<Harbinger> GRAPH = Graph.<Harbinger>builder()
        .apply(XenomorphGOAP::applyBaseGraph)
        .apply(graph -> TriggeredAttackGOAP.applyTriggeredAttack(graph, HarbingerGroundSlamAttack.ATTACK))
        .apply(graph -> TriggeredAttackGOAP.applyTriggeredAttack(graph, HarbingerKickAttack.ATTACK))
        // The front kick is aimed at the wall, not the target - it must survive having no line of sight.
        .apply(graph -> TriggeredAttackGOAP.applyBarrierAttack(graph, HarbingerFrontKickAttack.ATTACK))
        .build();

    public static Agent.Builder<Harbinger> applyAgentProperties(Agent.Builder<Harbinger> agentBuilder) {
        return XenomorphGOAP.applySpecialAttackAgentProperties(agentBuilder);
    }

    private HarbingerGOAP() {
        throw new UnsupportedOperationException();
    }
}
