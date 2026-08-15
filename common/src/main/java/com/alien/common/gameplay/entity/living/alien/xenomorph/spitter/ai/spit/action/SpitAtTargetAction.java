package com.alien.common.gameplay.entity.living.alien.xenomorph.spitter.ai.spit.action;

import com.alien.common.gameplay.entity.living.alien.xenomorph.spitter.Spitter;
import com.alien.common.gameplay.entity.living.alien.xenomorph.spitter.SpitterSpitAttack;
import com.blib.api.common.goap.v1.GOAPSensors;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.action.Action;
import com.just.ai.goap.state.Blackboard;
import com.just.core.functional.option.Option;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.LivingEntity;

public class SpitAtTargetAction {

    private static final int WIND_UP_TICKS = 10;

    private static final StateKey<Integer> KEY_WIND_UP_REMAINING = StateKey.sensed("spit_wind_up_remaining");

    private static final StateKey<Boolean> KEY_HAS_FIRED = StateKey.sensed("spit_has_fired");

    private static final StateKey<Boolean> KEY_ANIMATION_STARTED = StateKey.sensed("spit_animation_started");

    public static Action.Signal perform(Action.Context<? extends Spitter> context) {
        var spitter = context.getActor();
        var worldState = context.getWorldState();
        var blackboard = context.getBlackboard(Blackboard.Scope.ACTION);
        var attackTargetOption = worldState.getOrDefault(
            GOAPSensors.NEAREST_ATTACKABLE_TARGET.key(),
            Option.<LivingEntity>none()
        );

        if (attackTargetOption.isNone()) {
            return Action.Signal.ABORT;
        }

        var target = attackTargetOption.unwrap();

        spitter.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        spitter.getLookControl().setLookAt(target);
        spitter.getNavigation().stop();

        if (blackboard.getOrDefault(KEY_HAS_FIRED, false)) {
            return Action.Signal.ABORT;
        }

        var isStationary = spitter.getDeltaMovement().horizontalDistanceSqr() < 0.001;

        if (!isStationary) {
            return Action.Signal.CONTINUE;
        }

        var windUpRemaining = blackboard.getOrDefault(KEY_WIND_UP_REMAINING, WIND_UP_TICKS);

        if (!blackboard.getOrDefault(KEY_ANIMATION_STARTED, false)) {
            spitter.startAttack(Spitter.SPIT, target);
            blackboard.set(KEY_ANIMATION_STARTED, true);
        }

        if (windUpRemaining > 0) {
            blackboard.set(KEY_WIND_UP_REMAINING, windUpRemaining - 1);
            return Action.Signal.CONTINUE;
        }

        SpitterSpitAttack.shootAtTarget(spitter, target);
        blackboard.set(KEY_HAS_FIRED, true);

        return Action.Signal.ABORT;
    }

    private SpitAtTargetAction() {
        throw new UnsupportedOperationException();
    }
}
