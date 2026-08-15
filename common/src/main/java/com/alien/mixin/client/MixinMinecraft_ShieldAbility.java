package com.alien.mixin.client;

import com.alien.client.input.ShieldAbilityInputHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft_ShieldAbility {

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void avp_alien$handleShieldAbilityInput(CallbackInfo ci) {
        ShieldAbilityInputHandler.handle((Minecraft) (Object) this);
    }
}
