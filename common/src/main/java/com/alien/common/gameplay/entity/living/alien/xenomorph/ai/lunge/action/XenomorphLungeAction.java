package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.lunge.action;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.lunge.LungeSensors;
import com.alien.common.registry.init.AlienSoundEvents;
import com.blib.api.common.goap.v1.GOAPSensors;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.action.Action;
import com.just.ai.goap.state.Blackboard;
import com.just.core.functional.option.Option;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.LivingEntity;

public class XenomorphLungeAction {

    private static final StateKey<Boolean> KEY_HAS_LUNGED = StateKey.sensed("xeno_has_lunged");

    private static final StateKey<Integer> KEY_WIND_UP_TICKS_REMAINING = StateKey.sensed("xeno_wind_up_ticks_remaining");

    private static final int LUNGE_WIND_UP_TICKS = 10;

    public static Action.Signal perform(Action.Context<? extends Xenomorph> context) {
        var xenomorph = context.getActor();
        var worldState = context.getWorldState();
        var blackboard = context.getBlackboard(Blackboard.Scope.ACTION);
        var attackTargetOption = worldState.getOrDefault(GOAPSensors.NEAREST_ATTACKABLE_TARGET.key(), Option.<LivingEntity>none());

        if (attackTargetOption.isNone()) {
            return Action.Signal.ABORT;
        }

        var attackTarget = attackTargetOption.unwrap();

        if (!xenomorph.onGround() || !LungeSensors.hasRequiredLegs(xenomorph)) {
            return Action.Signal.ABORT;
        }

        xenomorph.lookAt(EntityAnchorArgument.Anchor.EYES, attackTarget.getEyePosition());
        xenomorph.getLookControl().setLookAt(attackTarget);

        var windUpTicksRemaining = blackboard.getOrDefault(KEY_WIND_UP_TICKS_REMAINING, LUNGE_WIND_UP_TICKS);

        if (xenomorph.getLastHurtByMobTimestamp() > 0 && xenomorph.tickCount - xenomorph.getLastHurtByMobTimestamp() < 20) {
            windUpTicksRemaining = 0;
        }

        if (windUpTicksRemaining > 0) {
            windUpTicksRemaining--;
            blackboard.set(KEY_WIND_UP_TICKS_REMAINING, windUpTicksRemaining);
            xenomorph.getNavigation().stop();
            return Action.Signal.CONTINUE;
        }

        var hasLunged = blackboard.getOrDefault(KEY_HAS_LUNGED, false);

        if (!hasLunged) {
            var distanceToTarget = xenomorph.distanceTo(attackTarget);
            var deltaMovement = xenomorph.getDeltaMovement().scale(0.2);
            var vectorDifference = attackTarget.getEyePosition().subtract(xenomorph.getEyePosition());

            vectorDifference = vectorDifference.normalize()
                .scale(0.2 * distanceToTarget)
                .add(deltaMovement.x, 0, deltaMovement.z);

            xenomorph.setDeltaMovement(vectorDifference.x, Math.max(0.6, vectorDifference.y), vectorDifference.z);

            xenomorph.playSound(
                AlienSoundEvents.ENTITY_XENOMORPH_LUNGE.get(),
                1.0F,
                (xenomorph.getRandom().nextFloat() - xenomorph.getRandom().nextFloat()) * 0.2F + 1.0F
            );
            xenomorph.isLunging.set(true);
            xenomorph.getXenomorphData().setLastLungeTick(xenomorph.tickCount);
            blackboard.set(KEY_HAS_LUNGED, true);
        }

        return Action.Signal.CONTINUE;
    }

    private XenomorphLungeAction() {
        throw new UnsupportedOperationException();
    }
}
