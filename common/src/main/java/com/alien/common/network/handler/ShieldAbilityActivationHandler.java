package com.alien.common.network.handler;

import com.alien.common.gameplay.item.ability.ShieldAbilityHandler;
import com.alien.common.network.payload.C2SActivateShieldAbilityPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class ShieldAbilityActivationHandler {

    private ShieldAbilityActivationHandler() {}

    public static void handle(C2SActivateShieldAbilityPayload payload, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            ShieldAbilityHandler.tryActivate(serverPlayer);
        }
    }
}
