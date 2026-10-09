package com.alien.common.gameplay.entity.living.alien.xenomorph.boiler.ai.action;

import com.alien.common.gameplay.entity.living.alien.xenomorph.boiler.Boiler;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.util.AlienPredicates;
import com.blib.api.common.goap.v1.action.impl.NeoMoveToPosAction;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.action.Action;
import com.just.ai.goap.state.Blackboard;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class InvestigateVibrationAction {

    private static final StateKey<Vec3> KEY_LAST_VIBRATION_POS = StateKey.sensed("vibration_last_pos");

    public static Action.Signal perform(Action.Context<? extends Boiler> context) {
        var boiler = context.getActor();
        var blackboard = context.getBlackboard(Blackboard.Scope.ACTION);

        if (boiler.getTarget() != null) {
            return Action.Signal.ABORT;
        }

        var vibration = boiler.getVibrationSystemManager()
            .getVibrationData()
            .getCurrentVibration();

        if (vibration == null) {
            return Action.Signal.ABORT;
        }

        var vibrationPos = vibration.pos();
        var lastPos = blackboard.getOrDefault(KEY_LAST_VIBRATION_POS, (Vec3) null);

        if (lastPos == null || !lastPos.equals(vibrationPos)) {
            blackboard.set(KEY_LAST_VIBRATION_POS, vibrationPos);

            var boilerData = boiler.getBoilerData();
            boilerData.increasePissedMeter();

            if (boilerData.tryHiss()) {
                boiler.level()
                    .playSound(
                        null,
                        boiler.getX(),
                        boiler.getY(),
                        boiler.getZ(),
                        AlienSoundEvents.ENTITY_XENOMORPH_HISS.get(),
                        boiler.getSoundSource(),
                        1F,
                        (boiler.getRandom().nextFloat() - boiler.getRandom().nextFloat()) * 0.2F + 1.0F
                    );
            }

            if (
                boilerData.isMaxPissed()
                    && vibration.entity() instanceof LivingEntity livingEntity
                    && AlienPredicates.canTarget(boiler, livingEntity)
            ) {
                boiler.setTarget(livingEntity);
                boilerData.resetPissedMeter();

                boiler.level()
                    .playSound(
                        null,
                        boiler.getX(),
                        boiler.getY(),
                        boiler.getZ(),
                        AlienSoundEvents.ENTITY_XENOMORPH_LUNGE.get(),
                        boiler.getSoundSource(),
                        1F,
                        (boiler.getRandom().nextFloat() - boiler.getRandom().nextFloat()) * 0.2F + 1.0F
                    );

                return Action.Signal.ABORT;
            }
        }

        // ⚠ vibrationPos is reassigned above, so it cannot be captured by a lambda directly.
        var vibrationPosFinal = vibrationPos;
        var result = com.alien.common.gameplay.hive.diag.DiagProfiler.timed(
            "path/InvestigateVibrationAction",
            () -> NeoMoveToPosAction.perform(context, vibrationPosFinal, 0.5)
        );

        return switch (result) {
            case FINISHED, MOVING -> Action.Signal.CONTINUE;
            case NO_PATH -> Action.Signal.ABORT;
            default -> Action.Signal.ABORT;
        };
    }

    public static void onFinish(Action.Context<? extends Boiler> context) {
        NeoMoveToPosAction.onFinish(context);
    }

    private InvestigateVibrationAction() {
        throw new UnsupportedOperationException();
    }
}
