package com.alien.common.gameplay.entity.living.alien.xenomorph.ai;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.combat.CombatActions;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.combat.CombatGoals;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.combat.CombatSensors;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.combat.XenomorphTargetSensors;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg.EggActions;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg.EggGoals;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg.EggSensors;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg_laying.EggLayer;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg_laying.EggLayingActions;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg_laying.EggLayingGoals;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg_laying.EggLayingSensors;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.idle.IdleActions;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.idle.IdleGoals;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.idle.IdleSensors;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.lunge.LungeActions;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.lunge.LungeConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.lunge.LungeSensors;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.resin.ResinActions;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.resin.ResinGoals;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.resin.ResinSensors;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.vent.VentActions;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.vent.VentGoals;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.vent.VentSensors;
import com.blib.api.common.goap.v1.GOAPSensors;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;
import com.just.ai.goap.plan.ReplanPolicies;

public class XenomorphGOAP {

    public static <T extends Xenomorph> Graph.Builder<T> applyBaseGraph(Graph.Builder<T> graphBuilder) {
        return graphBuilder
            .apply(XenomorphGOAP::addSensorsPackage)
            .apply(XenomorphGOAP::addCombatPackage)
            .apply(XenomorphGOAP::addIdlePackage);
    }

    public static <T extends Xenomorph & EggLayer> Graph.Builder<T> applyEggLayingOnlyGraph(Graph.Builder<T> graphBuilder) {
        return graphBuilder.apply(XenomorphGOAP::addEggLayingPackage);
    }

    public static <T extends Xenomorph> Agent.Builder<T> applyBaseAgentProperties(Agent.Builder<T> agentBuilder) {
        return agentBuilder.withReplanPolicy(
            ReplanPolicies.anyOf(
                ReplanPolicies.ifNoActivePlans(),
                ReplanPolicies.custom(context -> {
                    var actor = context.agent().getActor();
                    return actor.tickCount % 20 == 0;
                }),
                ReplanPolicies.custom(context -> {
                    var isOnFire = context.worldState().getOrDefault(GOAPSensors.IS_ON_FIRE.key(), false);
                    var wasOnFire = context.previousWorldState().getOrDefault(GOAPSensors.IS_ON_FIRE.key(), false);
                    return !wasOnFire && isOnFire;
                }),
                ReplanPolicies.custom(context -> {
                    var currentHealthRatio = context.worldState().getOrDefault(GOAPSensors.HEALTH_RATIO.key(), 0F);
                    var previousHealthRatio = context.previousWorldState().getOrDefault(GOAPSensors.HEALTH_RATIO.key(), 0F);
                    return currentHealthRatio < previousHealthRatio;
                })
            )
        );
    }

    public static <T extends Xenomorph> Agent.Builder<T> applySpecialAttackAgentProperties(
        Agent.Builder<T> agentBuilder
    ) {
        return agentBuilder.withReplanPolicy(
            ReplanPolicies.anyOf(
                ReplanPolicies.custom(
                    context -> !context.agent().getActor().isExecutingTriggeredAttack() && !context.agent().hasPlan()
                ),
                ReplanPolicies.custom(context -> {
                    var actor = context.agent().getActor();

                    if (actor.isExecutingTriggeredAttack()) {
                        return false;
                    }

                    return actor.tickCount % 20 == 0;
                }),
                ReplanPolicies.custom(context -> {
                    if (context.agent().getActor().isExecutingTriggeredAttack()) {
                        return false;
                    }

                    var isOnFire = context.worldState().getOrDefault(GOAPSensors.IS_ON_FIRE.key(), false);
                    var wasOnFire = context.previousWorldState().getOrDefault(GOAPSensors.IS_ON_FIRE.key(), false);
                    return !wasOnFire && isOnFire;
                }),
                ReplanPolicies.custom(context -> {
                    if (context.agent().getActor().isExecutingTriggeredAttack()) {
                        return false;
                    }

                    var currentHealthRatio = context.worldState().getOrDefault(GOAPSensors.HEALTH_RATIO.key(), 0F);
                    var previousHealthRatio = context.previousWorldState().getOrDefault(GOAPSensors.HEALTH_RATIO.key(), 0F);
                    return currentHealthRatio < previousHealthRatio;
                })
            )
        );
    }

    public static <T extends Xenomorph> Graph.Builder<T> addSensorsPackage(Graph.Builder<T> graphBuilder) {
        graphBuilder.addSensor(GOAPSensors.IS_ON_GROUND);
        graphBuilder.addSensor(GOAPSensors.IS_ON_FIRE);
        graphBuilder.addSensor(GOAPSensors.HEALTH_RATIO);

        return graphBuilder;
    }

    public static <T extends Xenomorph> Graph.Builder<T> addCombatPackage(Graph.Builder<T> graphBuilder) {
        addCombatPackageWithoutMove(graphBuilder);
        graphBuilder.addAction(CombatActions.MOVE_TO_TARGET);
        return graphBuilder;
    }

    public static <T extends Xenomorph> Graph.Builder<T> addCombatPackageWithoutMove(Graph.Builder<T> graphBuilder) {
        graphBuilder.addGoal(CombatGoals.KILL_TARGET);

        graphBuilder.addAction(CombatActions.MELEE_ATTACK);

        graphBuilder.addSensor(XenomorphTargetSensors.NEARBY_ATTACKABLE_TARGETS);
        graphBuilder.addSensor(GOAPSensors.NEAREST_ATTACKABLE_TARGETS);
        graphBuilder.addSensor(GOAPSensors.NEAREST_ATTACKABLE_TARGET);
        graphBuilder.addSensor(GOAPSensors.HAS_ATTACK_TARGET);
        graphBuilder.addSensor(CombatSensors.IS_TARGET_IN_MELEE_RANGE);

        return graphBuilder;
    }

    public static <T extends Xenomorph> Graph.Builder<T> addIdlePackage(Graph.Builder<T> graphBuilder) {
        graphBuilder.addGoal(IdleGoals.SATISFY_BOREDOM);

        graphBuilder.addAction(IdleActions.WANDER);

        graphBuilder.addSensor(IdleSensors.IS_BORED);

        return graphBuilder;
    }

    public static <T extends Xenomorph> Graph.Builder<T> addLungePackage(Graph.Builder<T> graphBuilder, LungeConfig config) {
        var lungeSensor = LungeSensors.<T>createLungeRangeSensor(config);

        graphBuilder.addAction(LungeActions.createLungeAtTarget(lungeSensor.key()));
        graphBuilder.addSensor(lungeSensor);

        return graphBuilder;
    }

    /**
     * Anchor demolition: a royal defender tears capture anchors out of its own hive claim.
     * <p>
     * Deliberately NOT part of the base graph. Only praetorians and crushers get it - they are the royal guard, and the
     * queen must never have it: she is what the anchors are for, and a queen who could free herself would make capture
     * pointless.
     */
    public static <T extends Xenomorph> Graph.Builder<T> addAnchorBreakPackage(Graph.Builder<T> graphBuilder) {
        graphBuilder.addGoal(com.alien.common.gameplay.entity.living.alien.xenomorph.ai.anchor_break.AnchorBreakActions.BREAK_ANCHOR_GOAL);
        graphBuilder.addAction(com.alien.common.gameplay.entity.living.alien.xenomorph.ai.anchor_break.AnchorBreakActions.BREAK_ANCHOR);
        graphBuilder.addSensor(
            com.alien.common.gameplay.entity.living.alien.xenomorph.ai.anchor_break.AnchorBreakSensors.HAS_TARGET_ANCHOR
        );
        return graphBuilder;
    }

    /** Host hunt: walk to a host, grab it, carry it to a vent, hand it into the host chamber. */
    public static <T extends Xenomorph> Graph.Builder<T> addHostCapturePackage(Graph.Builder<T> graphBuilder) {
        graphBuilder.addGoal(com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host.HostActions.CAPTURE_HOST_GOAL);
        graphBuilder.addGoal(com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host.HostActions.DELIVER_HOST_GOAL);
        graphBuilder.addAction(com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host.HostActions.CAPTURE_HOST);
        graphBuilder.addAction(com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host.HostActions.DELIVER_HOST);
        graphBuilder.addSensor(com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host.HostSensors.HAS_TARGET_HOST);
        graphBuilder.addSensor(com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host.HostSensors.IS_CARRYING_HOST);
        return graphBuilder;
    }

    public static <T extends Xenomorph> Graph.Builder<T> addEggPackage(Graph.Builder<T> graphBuilder) {
        graphBuilder.addGoal(EggGoals.FETCH_EGG);
        graphBuilder.addGoal(EggGoals.DELIVER_EGG);

        graphBuilder.addAction(EggActions.PICK_UP_EGG);
        graphBuilder.addAction(EggActions.DROP_OFF_EGG);

        graphBuilder.addSensor(EggSensors.HAS_TARGET_OVOMORPH);
        graphBuilder.addSensor(EggSensors.IS_CARRYING_OVOMORPH);

        return graphBuilder;
    }

    public static <T extends Xenomorph> Graph.Builder<T> addVentPackage(Graph.Builder<T> graphBuilder) {
        graphBuilder.addGoal(VentGoals.CREATE_VENT);

        graphBuilder.addAction(VentActions.CREATE_VENT);

        graphBuilder.addSensor(VentSensors.CAN_CREATE_VENT);
        graphBuilder.addSensor(VentSensors.HAS_VENT_TARGET);

        return graphBuilder;
    }

    public static <T extends Xenomorph> Graph.Builder<T> addResinPackage(Graph.Builder<T> graphBuilder) {
        graphBuilder.addGoal(ResinGoals.SPREAD_RESIN);

        graphBuilder.addAction(ResinActions.SPREAD_RESIN);

        graphBuilder.addSensor(ResinSensors.CAN_SPREAD_RESIN);

        return graphBuilder;
    }

    public static <T extends Xenomorph & EggLayer> Graph.Builder<T> addEggLayingPackage(Graph.Builder<T> graphBuilder) {
        graphBuilder.addGoal(EggLayingGoals.LAY_EGG);

        graphBuilder.addAction(EggLayingActions.layEgg());

        graphBuilder.addSensor(EggLayingSensors.canLayEgg());

        return graphBuilder;
    }

    private XenomorphGOAP() {
        throw new UnsupportedOperationException();
    }
}
