package com.alien.common.gameplay.entity.living.alien.xenomorph.spitter.ai;

import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.XenomorphGOAP;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.combat.CombatActions;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.combat.CombatGoals;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.combat.CombatSensors;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.combat.XenomorphTargetSensors;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.lunge.LungeConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.spitter.Spitter;
import com.alien.common.gameplay.entity.living.alien.xenomorph.spitter.ai.spit.SpitActions;
import com.alien.common.gameplay.entity.living.alien.xenomorph.spitter.ai.spit.SpitSensors;
import com.blib.api.common.goap.v1.GOAPSensors;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;

public class SpitterGOAP {

    private static final LungeConfig LUNGE_CONFIG = new LungeConfig(6, 12, 20 * 7);

    public static final Graph<Spitter> GRAPH = Graph.<Spitter>builder()
        .apply(XenomorphGOAP::addSensorsPackage)
        .apply(SpitterGOAP::addCombatPackage)
        // !! THIS CASTE HAD NO SWIM PACKAGE. addSwimPackage was added to applyBaseGraph, but the spitter,
        // the carrier and the chrysalis build their graphs by hand and were never given it - so those three
        // could not reach shore at all, whatever else was fixed.
        .apply(XenomorphGOAP::addSwimPackage)
        .apply(XenomorphGOAP::addIdlePackage)
        .apply(b -> XenomorphGOAP.addLungePackage(b, LUNGE_CONFIG))
        .apply(SpitterGOAP::addSpitPackage)
        .build();

    public static Agent.Builder<Spitter> applyAgentProperties(Agent.Builder<Spitter> agentBuilder) {
        return XenomorphGOAP.applyBaseAgentProperties(agentBuilder);
    }

    private static Graph.Builder<Spitter> addCombatPackage(Graph.Builder<Spitter> graphBuilder) {
        graphBuilder.addGoal(CombatGoals.KILL_TARGET);

        graphBuilder.addAction(SpitterCombatActions.MOVE_TO_TARGET);
        graphBuilder.addAction(CombatActions.MELEE_ATTACK);

        graphBuilder.addSensor(XenomorphTargetSensors.NEARBY_ATTACKABLE_TARGETS);
        graphBuilder.addSensor(GOAPSensors.NEAREST_ATTACKABLE_TARGETS);
        graphBuilder.addSensor(GOAPSensors.NEAREST_ATTACKABLE_TARGET);
        graphBuilder.addSensor(GOAPSensors.HAS_ATTACK_TARGET);
        graphBuilder.addSensor(CombatSensors.IS_TARGET_IN_MELEE_RANGE);

        return graphBuilder;
    }

    private static Graph.Builder<Spitter> addSpitPackage(Graph.Builder<Spitter> graphBuilder) {
        graphBuilder.addAction(SpitActions.SPIT_AT_TARGET);
        graphBuilder.addAction(SpitActions.WAIT_FOR_SPIT_COOLDOWN);

        graphBuilder.addSensor(SpitSensors.IS_TARGET_AT_SPIT_DISTANCE);
        graphBuilder.addSensor(SpitSensors.IS_SPIT_COOLDOWN_READY);

        return graphBuilder;
    }

    private SpitterGOAP() {
        throw new UnsupportedOperationException();
    }
}
