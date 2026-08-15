package com.alien.common.gameplay.entity.living.alien.xenomorph.queen.ai;

import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.XenomorphGOAP;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.ai.founding_move.FoundingMoveActions;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.ai.founding_move.FoundingMoveGoals;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.ai.founding_move.FoundingMoveSensors;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.ai.hibernation.HibernationActions;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.ai.hibernation.HibernationGoals;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.ai.hibernation.HibernationSensors;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.ai.location_move.LocationMoveActions;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.ai.location_move.LocationMoveGoals;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.ai.location_move.LocationMoveSensors;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;

public class QueenGOAP {

    public static final Graph<Queen> GRAPH = Graph.<Queen>builder()
        .apply(XenomorphGOAP::applyBaseGraph)
        .apply(XenomorphGOAP::addResinPackage)
        .apply(XenomorphGOAP::addEggLayingPackage)
        .apply(QueenGOAP::addLocationMovePackage)
        .apply(QueenGOAP::addHibernationPackage)
        .apply(QueenGOAP::addFoundingMovePackage)
        .build();

    public static final Graph<Queen> OVIPOSITOR_GRAPH = Graph.<Queen>builder()
        // The shared queen agent's replan policy reads is_on_fire / health_ratio every replan, so this graph must
        // register the base sensors too — otherwise BLib warns "no sensor exists for key" each replan while she lays,
        // and the on-fire / health-drop replan triggers silently never fire. Sensors only; no goals/actions, so the
        // working laying flow is unaffected.
        .apply(XenomorphGOAP::addSensorsPackage)
        .apply(XenomorphGOAP::applyEggLayingOnlyGraph)
        .build();

    /**
     * Location-phase dig-to-anchor (Stage 2b). Queen-specific and attached only to the normal {@link #GRAPH} (never the
     * egg-laying-only {@link #OVIPOSITOR_GRAPH}), so it can never touch a reproductive queen. Active only while she is
     * a never-founded queen in the LOCATION phase with a committed anchor; gated as a whole by
     * {@link LocationMoveSensors#locationAnchorOrNull} returning null when the front-end is off.
     */
    public static Graph.Builder<Queen> addLocationMovePackage(Graph.Builder<Queen> graphBuilder) {
        graphBuilder.addGoal(LocationMoveGoals.BE_AT_ANCHOR);

        graphBuilder.addAction(LocationMoveActions.DIG_TO_ANCHOR);

        graphBuilder.addSensor(LocationMoveSensors.IS_LOCATING);
        graphBuilder.addSensor(LocationMoveSensors.IS_AT_ANCHOR);

        return graphBuilder;
    }

    /**
     * Hibernation behaviours (Stages 3a + 3b). Queen-specific and attached only to the normal {@link #GRAPH} (never the
     * egg-laying-only {@link #OVIPOSITOR_GRAPH}). Active only while she is a never-founded queen in the HIBERNATION
     * phase: {@link HibernationActions#HIBERNATE_HOLD} keeps her asleep at the anchor, and
     * {@link HibernationActions#HIBERNATE_RETURN} walks her back after a disturbance clears. The sub-state sensors are
     * false when the front-end is off, so the package drops out with it.
     */
    public static Graph.Builder<Queen> addHibernationPackage(Graph.Builder<Queen> graphBuilder) {
        graphBuilder.addGoal(HibernationGoals.HIBERNATE);
        graphBuilder.addGoal(HibernationGoals.RETURN_TO_ANCHOR);

        graphBuilder.addAction(HibernationActions.HIBERNATE_HOLD);
        graphBuilder.addAction(HibernationActions.HIBERNATE_RETURN);

        graphBuilder.addSensor(HibernationSensors.IS_ASLEEP);
        graphBuilder.addSensor(HibernationSensors.IS_RETURNING);

        return graphBuilder;
    }

    /**
     * Founding navigate-to-center (Option B). Queen-specific so drones and the empress are unaffected, and attached
     * only to the normal {@link #GRAPH} — never the egg-laying-only {@link #OVIPOSITOR_GRAPH} — so it cannot interfere
     * with the working laying flow once she is reproductive. The behaviour as a whole is gated by
     * {@link FoundingMoveSensors#isEnabled()}; when disabled, these nodes simply never get selected.
     */
    public static Graph.Builder<Queen> addFoundingMovePackage(Graph.Builder<Queen> graphBuilder) {
        graphBuilder.addGoal(FoundingMoveGoals.BE_AT_CENTER);

        graphBuilder.addAction(FoundingMoveActions.GO_TO_CENTER);

        graphBuilder.addSensor(FoundingMoveSensors.IS_FOUNDING);
        graphBuilder.addSensor(FoundingMoveSensors.IS_AT_CENTER);

        return graphBuilder;
    }

    public static Agent.Builder<Queen> applyAgentProperties(Agent.Builder<Queen> agentBuilder) {
        return XenomorphGOAP.applyBaseAgentProperties(agentBuilder);
    }

    private QueenGOAP() {
        throw new UnsupportedOperationException();
    }
}
