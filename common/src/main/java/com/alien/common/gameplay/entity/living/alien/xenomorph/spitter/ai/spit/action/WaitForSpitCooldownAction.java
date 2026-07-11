package com.alien.common.gameplay.entity.living.alien.xenomorph.spitter.ai.spit.action;

import com.alien.common.gameplay.entity.living.alien.xenomorph.spitter.Spitter;
import com.blib.api.common.goap.v1.GOAPSensors;
import com.just.ai.goap.action.Action;
import com.just.core.functional.option.Option;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WaitForSpitCooldownAction {

    private static final Logger LOGGER = LoggerFactory.getLogger(WaitForSpitCooldownAction.class);

    public static Action.Signal perform(Action.Context<? extends Spitter> context) {
        var spitter = context.getActor();
        var worldState = context.getWorldState();
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

        var currentTick = spitter.tickCount;
        var cooldownReady = spitter.getSpitterData().isCooldownReady(currentTick);

        if (cooldownReady) {
            return Action.Signal.ABORT;
        }

        return Action.Signal.CONTINUE;
    }

    private WaitForSpitCooldownAction() {
        throw new UnsupportedOperationException();
    }
}
