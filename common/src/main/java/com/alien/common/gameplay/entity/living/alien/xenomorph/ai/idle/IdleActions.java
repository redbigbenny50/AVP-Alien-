package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.idle;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.goap.v1.GOAPSensors;
import com.blib.api.common.goap.v1.action.ActionMasks;
import com.blib.api.common.goap.v1.action.BLibAction;
import com.blib.api.common.goap.v1.action.impl.NeoMoveToPosAction;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.action.Action;
import com.just.ai.goap.condition.expression.Expressions;
import com.just.ai.goap.state.Blackboard;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class IdleActions {

    private static final StateKey<Vec3> KEY_WANDER_TARGET = StateKey.sensed("wander_target");

    private static final int WANDER_HORIZONTAL_RANGE = 10;

    private static final int WANDER_VERTICAL_RANGE = 7;

    private static final double WANDER_SPEED = 0.5;

    private static final int WANDER_TARGET_ATTEMPTS = 12;

    public static final Action<Xenomorph> WANDER = BLibAction.<Xenomorph>builder("WanderAction")
        .addMasks(ActionMasks.MOVE)
        .addPrecondition(GOAPSensors.HAS_ATTACK_TARGET.key(), Expressions.Boolean.isFalse())
        .addPrecondition(IdleSensors.IS_BORED.key(), Expressions.Boolean.isTrue())
        .addEffect(IdleSensors.IS_BORED.key().asDerived(), false)
        .withPerformCallback(context -> {
            return performWander(context);
        })
        .withFinishCallback(context -> {
            NeoMoveToPosAction.onFinish(context);
            context.getBlackboard(Blackboard.Scope.ACTION).clear();
        })
        .build();

    private static Action.Signal performWander(Action.Context<? extends Xenomorph> context) {
        var actor = context.getActor();
        var location = resolveHiveBoundWanderLocation(actor);

        var blackboard = context.getBlackboard(Blackboard.Scope.ACTION);
        var target = blackboard.getOrDefault(KEY_WANDER_TARGET, (Vec3) null);
        if (target == null || !isValidWanderTarget(location, target)) {
            target = pickWanderTarget(actor, location);
            if (target == null) {
                actor.getXenomorphData().resetTicksUntilBored();
                return Action.Signal.CONTINUE;
            }
            blackboard.set(KEY_WANDER_TARGET, target);
        }

        var result = NeoMoveToPosAction.perform(context, target, WANDER_SPEED);
        return switch (result) {
            case FINISHED -> {
                actor.getXenomorphData().resetTicksUntilBored();
                blackboard.set(KEY_WANDER_TARGET, null);
                yield Action.Signal.CONTINUE;
            }
            case MOVING -> Action.Signal.CONTINUE;
            case NO_PATH -> {
                actor.getXenomorphData().resetTicksUntilBored();
                blackboard.set(KEY_WANDER_TARGET, null);
                yield Action.Signal.ABORT;
            }
            default -> {
                actor.getXenomorphData().resetTicksUntilBored();
                blackboard.set(KEY_WANDER_TARGET, null);
                yield Action.Signal.ABORT;
            }
        };
    }

    private static @Nullable Vec3 pickWanderTarget(Xenomorph actor, @Nullable HiveLocation location) {
        // Always try several times: LandRandomPos frequently returns null for large mobs (the queen especially),
        // so a single roll usually fails and she never wanders. Hive-bound queens also need the retries to land
        // a spot inside their claimed chunks.
        var attempts = WANDER_TARGET_ATTEMPTS;

        for (var attempt = 0; attempt < attempts; attempt++) {
            var candidate = LandRandomPos.getPos(actor, WANDER_HORIZONTAL_RANGE, WANDER_VERTICAL_RANGE);
            if (candidate != null && isValidWanderTarget(location, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isValidWanderTarget(@Nullable HiveLocation location, Vec3 pos) {
        return location == null || isInsideLocation(location, pos);
    }

    private static boolean isHiveBoundIdleWanderer(Xenomorph actor) {
        return actor.getType().is(AlienEntityTypeTags.QUEENS)
            || actor.getType().is(AlienEntityTypeTags.EMPRESSES)
            || actor.getType().is(AlienEntityTypeTags.HARBINGERS);
    }

    private static @Nullable HiveLocation resolveHiveBoundWanderLocation(Xenomorph actor) {
        if (!isHiveBoundIdleWanderer(actor)) {
            return null;
        }

        var location = HiveLocationRegistry.INSTANCE.getByChunk(actor.level().dimension(), actor.chunkPosition());
        return location != null && location.isAlive() ? location : null;
    }

    private static boolean isInsideLocation(HiveLocation location, Vec3 pos) {
        return location.claimedChunks().contains(new ChunkPos((int) Math.floor(pos.x) >> 4, (int) Math.floor(pos.z) >> 4));
    }

    private IdleActions() {
        throw new UnsupportedOperationException();
    }
}
