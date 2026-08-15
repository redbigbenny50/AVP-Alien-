package com.alien.common.gameplay.entity.living.alien.xenomorph.predalien.ai;

import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.XenomorphGOAP;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.lunge.LungeConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.predalien.Predalien;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;

public class PredalienGOAP {

    /** Pounce reach and cadence, matching the runner and prowler this move is shared with. */
    private static final LungeConfig LUNGE_CONFIG = new LungeConfig(6, 12, 20 * 7);

    public static final Graph<Predalien> GRAPH = Graph.<Predalien>builder()
        .apply(XenomorphGOAP::applyBaseGraph)
        .apply(b -> XenomorphGOAP.addLungePackage(b, LUNGE_CONFIG))
        .build();

    public static Agent.Builder<Predalien> applyAgentProperties(Agent.Builder<Predalien> agentBuilder) {
        return XenomorphGOAP.applyBaseAgentProperties(agentBuilder);
    }

    private PredalienGOAP() {
        throw new UnsupportedOperationException();
    }
}
