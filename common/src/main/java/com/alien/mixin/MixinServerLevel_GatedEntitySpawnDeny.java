package com.alien.mixin;

import com.alien.Alien;
import com.alien.compatibility.AlienModGates;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The catch-all half of the sibling-mod gate: refuses to add an irradiated alien or a predalien to a server level while
 * the mod that family belongs to is missing. See {@link AlienModGates}.
 * <p>
 * {@code addFreshEntity} is the single door every NEW entity comes through - {@code /summon} (via
 * {@code tryAddFreshEntityWithPassengers}), spawn eggs, spawner blocks, the hive's own reserve spawns, and any other
 * mod calling the ordinary API. Blocking here means no spawn path has to be found and patched one at a time.
 * <p>
 * Chunk loading is deliberately NOT covered, and that is the point: entities already saved in a world arrive through
 * {@code addLegacyChunkEntities} / {@code addWorldGenChunkEntities} on the entity manager, and teleports arrive through
 * {@code addDuringTeleport}. A player who uninstalls AVP: Human keeps the irradiated xenomorphs already living in their
 * save - the hive simply cannot make any more. Deleting somebody's mobs out from under them is not what "hide this
 * content" should mean.
 * <p>
 * Silent by design. The player-facing explanations live where a person actually asked for the entity by name -
 * {@code MixinSummonCommand_GatedEntityDeny} and {@code MixinSpawnEggItem_GatedEntityDeny} - because a spawner block
 * ticking against a gate has no one to talk to.
 */
@Mixin(ServerLevel.class)
public abstract class MixinServerLevel_GatedEntitySpawnDeny {

    /** Game tick of the last refusal logged, so a spawner pointed at a gated type cannot fill the log. */
    private static long avp_alien$lastRefusalLogTick = Long.MIN_VALUE;

    private static final long avp_alien$REFUSAL_LOG_INTERVAL_TICKS = 200L;

    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void avp_alien$denySpawnsNeedingAMissingMod(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        var missingMod = AlienModGates.missingModFor(entity.getType());
        if (missingMod == null) {
            return;
        }

        var level = (ServerLevel) (Object) this;
        var now = level.getGameTime();
        if (now - avp_alien$lastRefusalLogTick >= avp_alien$REFUSAL_LOG_INTERVAL_TICKS) {
            avp_alien$lastRefusalLogTick = now;
            Alien.LOGGER.info(
                "Refused to spawn {} at {}: {} is not installed.",
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                entity.blockPosition(),
                missingMod
            );
        }

        cir.setReturnValue(false);
    }
}
