package com.alien.mixin;

import com.alien.common.gameplay.hive.diag.WideDiagProfiler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Times every entity tick on the server for {@code /avp diag all}.
 * <p>
 * {@code Level.guardEntityTick} is the single point EVERY entity tick passes through, vanilla and modded alike, which
 * is what lets the report answer "which mod is eating the tick" rather than only "it is not avp_alien".
 * </p>
 * <p>
 * ⚠⚠ THE HOTTEST PATH IN THE GAME. Both handlers begin with {@link WideDiagProfiler#isCapturing()}, a single volatile
 * read, and do nothing else when no capture is running - an idle server pays one boolean per entity per tick. While a
 * capture IS running it costs two nanoTime calls per entity, which is a real TPS cost and is why the command is
 * start/stop rather than always-on.
 * </p>
 * <p>
 * ⚠ require = 0. If a future mapping change or another mod's mixin moves this target, the diagnostic quietly stops
 * working rather than crashing the game. A profiler must never be the thing that breaks a server.
 * </p>
 */
@Mixin(Level.class)
public abstract class MixinLevel_WideDiag {

    @Inject(method = "guardEntityTick", at = @At("HEAD"), require = 0)
    private <T extends Entity> void avp_alien$timeEntityTickStart(
        Consumer<T> consumer,
        T entity,
        CallbackInfo callbackInfo
    ) {
        if (!WideDiagProfiler.isCapturing()) {
            return;
        }

        WideDiagProfiler.markStart();
    }

    @Inject(method = "guardEntityTick", at = @At("TAIL"), require = 0)
    private <T extends Entity> void avp_alien$timeEntityTickEnd(
        Consumer<T> consumer,
        T entity,
        CallbackInfo callbackInfo
    ) {
        if (!WideDiagProfiler.isCapturing()) {
            return;
        }

        WideDiagProfiler.recordEntity(entity);
    }
}
