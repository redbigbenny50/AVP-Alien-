package com.alien.common.gameplay.entity.living.alien.xenomorph.drone.ai;

import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.XenomorphGOAP;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.lunge.LungeConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.drone.Drone;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;

public class DroneGOAP {

    private static final LungeConfig LUNGE_CONFIG = new LungeConfig(6, 12, 20 * 7);

    public static final Graph<Drone> GRAPH = Graph.<Drone>builder()
        .apply(XenomorphGOAP::applyBaseGraph)
        .apply(b -> XenomorphGOAP.addLungePackage(b, LUNGE_CONFIG))
        .apply(XenomorphGOAP::addEggPackage)
        .apply(XenomorphGOAP::addHostCapturePackage)
        .apply(XenomorphGOAP::addVentPackage)
        .apply(XenomorphGOAP::addResinPackage)
        .build();

    public static Agent.Builder<Drone> applyAgentProperties(Agent.Builder<Drone> agentBuilder) {
        return XenomorphGOAP.applyBaseAgentProperties(agentBuilder);
    }

    private DroneGOAP() {
        throw new UnsupportedOperationException();
    }
}
