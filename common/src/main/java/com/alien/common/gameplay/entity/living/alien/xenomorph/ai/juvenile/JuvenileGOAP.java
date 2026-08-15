package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.juvenile;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.XenomorphGOAP;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;

/**
 * ⭐⭐ THE GRAPH THAT MAKES A JUVENILE A REAL GOAP AGENT — and the reason its molt ever finishes.
 * <p>
 * The Aug 11 reparent moved the adolescent from {@code Alien} to {@link Xenomorph}, which is what routed its growth
 * through {@code CocoonManager} and finally made the authored molt clips reachable. But the cocoon state machine is
 * advanced by exactly ONE thing in this codebase: {@code CocoonActions.COCOON}, a GOAP ACTION, selected through
 * {@code Xenomorph.getActiveGOAPGraph} when {@code shouldRunCocoonAction()} is true. Every other xenomorph subclass -
 * Warrior, Runner, Prowler, RazorClaw, Crusher, Boiler, Chrysalis, Predalien, Queen - implements {@code GOAPUser} and
 * therefore has an agent to run that action.
 * </p>
 * <p>
 * ⚠⚠ THE ADOLESCENT WAS THE ONLY XENOMORPH SUBCLASS WITHOUT ONE. So {@code GrowthManager.grow()} called
 * {@code prepare()}, the cocoon entered PENDING, {@code maintainLockedState()} froze the entity via
 * {@code stopMovementAndTargeting()} - and nothing ever ticked it out again. That is the "sitting still and not
 * targeting or running" report: not a juvenile that failed to start its molt, a juvenile that started and could never
 * finish. Sealing them in separate boxes ruled out every {@code canStartMolting} clause and left only this.
 * </p>
 * <p>
 * ⚠ THE BASE GRAPH IS SENSORS ONLY, ON PURPOSE. The juvenile's ordinary behaviour still lives in its vanilla goals
 * ({@code JuvenileMeleeGoal}, {@code AvoidEntityGoal}, {@code HuntPreyGoal}, the stroll, {@code SettleToMoltGoal}), so
 * this graph deliberately declares no goals and no actions - with nothing to plan, the agent stays out of the way and
 * the vanilla goals keep running exactly as before. What it DOES provide is a live agent plus the base sensor package,
 * so that the moment {@code getActiveGOAPGraph} swaps in {@code CocoonGOAP.GRAPH} there is something to execute it.
 * </p>
 * <p>
 * ⏭ PORTING THE JUVENILE'S OWN BEHAVIOURS TO GOAP (hunt / avoid / settle) is the natural next step and would let the
 * planner arbitrate them by {@code ActionMask} instead of by vanilla goal priority - which is what the settle-vs-avoid
 * priority clash needed. Deliberately NOT done in this pass: it is a behaviour rewrite, not a fix, and it should be
 * playtested on its own.
 * </p>
 */
public class JuvenileGOAP {

    /**
     * Built per-caste rather than shared, because {@code GOAPUser<T>} wants a {@code Graph<T>} for the concrete class.
     * The contents are identical; only the type parameter differs.
     */
    public static <T extends Xenomorph> Graph<T> buildGraph() {
        return Graph.<T>builder()
            .apply(XenomorphGOAP::addSensorsPackage)
            .build();
    }

    /**
     * The same replan policy every adult caste uses. It reads {@code IS_ON_FIRE} and {@code HEALTH_RATIO}, both of
     * which {@code addSensorsPackage} registers - so a juvenile no longer logs "no sensor exists for key" on replan,
     * the way a cocooning praetorian used to before {@code CocoonGOAP} adopted the same package.
     */
    public static <T extends Xenomorph> Agent.Builder<T> applyAgentProperties(Agent.Builder<T> agentBuilder) {
        return XenomorphGOAP.applyBaseAgentProperties(agentBuilder);
    }

    private JuvenileGOAP() {
        throw new UnsupportedOperationException();
    }
}
