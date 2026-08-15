package com.alien.common.gameplay.entity.living.alien.xenomorph.queen.ai.hibernation;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.blib.api.common.goap.v1.action.ActionMasks;
import com.blib.api.common.goap.v1.action.BLibAction;
import com.just.ai.goap.action.Action;
import com.just.ai.goap.condition.expression.Expressions;
import net.minecraft.world.phys.Vec3;

/**
 * Actions for hibernation (Stages 3a + 3b).
 * <p>
 * {@link #HIBERNATE_HOLD} pins her asleep at the anchor while {@link HibernationSensors#IS_ASLEEP}. The hibernate pose
 * is driven client-side by {@code QueenAnimator} off the synced {@code isHibernating} flag (set by the phase manager),
 * not here — this perform runs server-side and AzCommand dispatch is client-only. Negative cost so the planner prefers
 * the hibernation behaviours over idle wander.
 * <p>
 * {@link #HIBERNATE_RETURN} (Stage 3b) walks her back to the anchor after a disturbance clears, while
 * {@link HibernationSensors#IS_RETURNING}. With no walkable route home it asks the phase manager to re-anchor where she
 * stands. Threats are handled by the phase manager flipping her back to the defend sub-state (which drops IS_RETURNING
 * and this action with it), so no combat-yield precondition is needed here.
 */
public final class HibernationActions {

    /** Negative so the planner prefers the hibernation behaviours over idle wandering. */
    private static final float COST = -1.0F;

    /** Normal walking pace back to the anchor. */
    private static final double RETURN_WALK_SPEED = 1.0;

    public static final Action<Xenomorph> HIBERNATE_HOLD = BLibAction.<Xenomorph>builder("HibernateHoldAction")
        .addMasks(ActionMasks.MOVE, ActionMasks.LOOK)
        .addPrecondition(HibernationSensors.IS_ASLEEP.key(), Expressions.Boolean.isTrue())
        .addEffect(HibernationSensors.IS_ASLEEP.key().asDerived(), false)
        .withCost(COST)
        .withPerformCallback(context -> {
            var actor = context.getActor();
            actor.setDeltaMovement(Vec3.ZERO);
            actor.getNavigation().stop();
            actor.setTarget(null);
            // Animation is driven client-side by QueenAnimator off the synced isHibernating flag — never dispatched
            // here, since this perform runs on the server and AzCommand dispatch is client-only.
            return Action.Signal.CONTINUE;
        })
        .build();

    public static final Action<Xenomorph> HIBERNATE_RETURN = BLibAction.<Xenomorph>builder("HibernateReturnAction")
        .addMasks(ActionMasks.MOVE, ActionMasks.LOOK)
        .addPrecondition(HibernationSensors.IS_RETURNING.key(), Expressions.Boolean.isTrue())
        .addEffect(HibernationSensors.IS_RETURNING.key().asDerived(), false)
        .withCost(COST)
        .withPerformCallback(context -> {
            var actor = context.getActor();
            if (!(actor instanceof Queen queen)) {
                return Action.Signal.ABORT;
            }
            var anchor = queen.getLifecyclePhaseManager().getAnchor();
            if (anchor == null) {
                return Action.Signal.ABORT;
            }
            var navigation = actor.getNavigation();
            if (navigation.isDone()) {
                var path = navigation.createPath(anchor.getX(), anchor.getY(), anchor.getZ(), 0);
                if (path == null) {
                    // No route home — re-anchor her where she stands and resume sleep there.
                    queen.getLifecyclePhaseManager().onReturnPathBlocked();
                    return Action.Signal.CONTINUE;
                }
                navigation.moveTo(path, RETURN_WALK_SPEED);
            }
            return Action.Signal.CONTINUE;
        })
        .build();

    private HibernationActions() {
        throw new UnsupportedOperationException();
    }
}
