package com.alien.mixin;

import com.alien.common.gameplay.hive.diag.WideDiagProfiler;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Times every block-entity tick on the server for {@code /avp diag all}.
 * <p>
 * ⚠ TARGETS THE VANILLA WRAPPER, NOT BlockEntity ITSELF. {@code LevelChunk$BoundTickingBlockEntity} is what the chunk
 * actually calls, so this catches every modded block entity without needing any of them to cooperate. It is
 * package-private, hence the string target.
 * </p>
 * <p>
 * ⚠⚠ HOT PATH. Both handlers return immediately on a single volatile read unless a capture is running - see
 * MixinLevel_WideDiag for the full reasoning, which applies here identically.
 * </p>
 * <p>
 * ⚠ require = 0: a mapping change disables the diagnostic rather than crashing the server.
 * </p>
 */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class MixinBlockEntity_WideDiag {

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void avp_alien$timeBlockEntityStart(CallbackInfo callbackInfo) {
        WideDiagProfiler.markStart();
    }

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void avp_alien$timeBlockEntityEnd(CallbackInfo callbackInfo) {
        if (!WideDiagProfiler.isCapturing()) {
            return;
        }

        if (((Object) this) instanceof BlockEntity blockEntity) {
            WideDiagProfiler.recordBlockEntity(blockEntity);
        }
    }
}
