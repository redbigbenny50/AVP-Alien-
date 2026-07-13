package com.alien.mixin.client;

import com.alien.client.input.HostStruggleInputHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drives the mash input for BOTH struggles - being carried off by a drone, and having a facehugger on your face.
 * {@link HostStruggleInputHandler} works out which (if either) applies.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft_HostStruggle {

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void avp_alien$handleHostStruggleInput(CallbackInfo ci) {
        HostStruggleInputHandler.handle((Minecraft) (Object) this);
    }
}
