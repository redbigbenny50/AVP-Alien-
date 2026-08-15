package com.alien.mixin.client;

import com.alien.common.gameplay.item.NoBlockingMovementPenaltyItem;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer_NoBlockingMovementPenalty {

    @ModifyExpressionValue(
        method = { "aiStep", "canStartSprinting" },
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z")
    )
    private boolean avp_alien$hasBlockingMovementPenalty(boolean original) {
        var player = (LocalPlayer) (Object) this;
        return original && !NoBlockingMovementPenaltyItem.isActive(player);
    }
}
