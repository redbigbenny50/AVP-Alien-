package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.cocoon;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.XenomorphGOAP;
import com.just.ai.goap.graph.Graph;

public class CocoonGOAP {

    public static final Graph<Xenomorph> GRAPH = Graph.<Xenomorph>builder()
        .addGoal(CocoonGoals.COCOON)
        .addAction(CocoonActions.COCOON)
        .addSensor(CocoonSensors.SHOULD_COCOON)
        // The shared agent replan policy reads is_on_fire / health_ratio every replan; register the base sensors here
        // too so cocooning xenomorphs (including a praetorian/crusher mid-metamorphosis) don't spam "no sensor exists
        // for key" each replan. Sensors only — no goals/actions, so cocoon behaviour is unchanged.
        .apply(XenomorphGOAP::addSensorsPackage)
        .build();

    private CocoonGOAP() {
        throw new UnsupportedOperationException();
    }
}
