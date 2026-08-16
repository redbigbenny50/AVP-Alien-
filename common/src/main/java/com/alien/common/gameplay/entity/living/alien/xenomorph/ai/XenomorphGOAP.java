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
        return graphBuilder
            // ⚠⚠ THE SENSORS PACKAGE IS NOT OPTIONAL ON ANY GRAPH. `applyBaseAgentProperties` builds a replan policy
            // that READS `IS_ON_FIRE` and `HEALTH_RATIO` every 20 ticks, so a graph without these sensors makes the
            // agent's own replan logic log "Attempted to sense a value for key X, but no sensor exists" forever. The
            // egg-laying-only graph was the one graph that omitted it.
            .apply(XenomorphGOAP::addSensorsPackage)
            .apply(XenomorphGOAP::addEggLayingPackage)
            // ⭐ SENSORS ONLY, DELIBERATELY WITHOUT THEIR GOALS OR ACTIONS. A royal mounts her ovipositor and swaps
            // onto this narrow graph MID-PLAN; the plan she was already running belongs to the wide graph, and its
            // runtime preconditions still get evaluated against the new one. Those preconditions read
            // `has_attack_target`, `is_bored` and `has_target_anchor` - keys this graph never registered - which is
            // the burst of warnings seen whenever a queen settles onto her eggsack.
            // <p>
            // Registering the SENSORS makes those reads answerable; deliberately NOT registering the matching goals
            // and actions keeps the behaviour exactly as designed - a queen on her ovipositor still does not wander,
            // fight or go anchor-hunting, which is the entire point of the narrow graph. Sensing is demand-driven and
            // memoised, so an unread sensor costs nothing.
            // </p>
            // ⚠⚠⚠ DO NOT ADD `GOAPSensors.HAS_ATTACK_TARGET` HERE. I DID, AND IT CRASHED EVERY QUEEN THAT MOUNTED HER
            // EGGSACK: "NullPointerException: Cannot invoke Option.isSome() because attackTargetOption is null".
            // <p>
            // It is a COMPOSED sensor, not a plain one, with a three-link upstream chain:
            // XenomorphTargetSensors.NEARBY_ATTACKABLE_TARGETS -> GOAPSensors.NEAREST_ATTACKABLE_TARGETS ->
            // NEAREST_ATTACKABLE_TARGET -> HAS_ATTACK_TARGET. Register only the last link and the upstream read
            // returns null, and `attackTargetOption.isSome()` dereferences it on the very first plan evaluation.
            // </p>
            // <p>
            // ⚠ AND REGISTERING THE WHOLE CHAIN WOULD BE WORSE, not a fix: `NEAREST_ATTACKABLE_TARGET` has a SIDE
            // EFFECT - `targetOption.ifSome(mob::setTarget)` - so it would hand a queen bound to her ovipositor a live
            // attack target, which is exactly what the narrow graph exists to prevent.
            // </p>
            // <p>
            // The cost of leaving it out is a "no sensor exists for key 'sensed:has_attack_target'" WARNING when a
            // stale plan from the wide graph is evaluated against this one. A warning is not a crash. Leave it.
            // </p>
            .addSensor(IdleSensors.IS_BORED)
            .addSensor(com.alien.common.gameplay.entity.living.alien.xenomorph.ai.anchor_break.AnchorBreakSensors.HAS_TARGET_ANCHOR);
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
