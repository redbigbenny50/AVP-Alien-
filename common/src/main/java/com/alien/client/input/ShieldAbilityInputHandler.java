package com.alien.client.input;

import com.alien.Alien;
import com.alien.common.gameplay.item.ability.ShieldAbilityItem;
import com.alien.common.network.payload.C2SActivateShieldAbilityPayload;
import net.minecraft.client.Minecraft;

public final class ShieldAbilityInputHandler {

    private static boolean attackWasDown;

    private ShieldAbilityInputHandler() {}

    public static void handle(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            attackWasDown = false;
            return;
        }

        var attackDown = minecraft.options.keyAttack.isDown();
        if (!attackDown) {
            attackWasDown = false;
            return;
        }

        if (attackWasDown) {
            return;
        }

        attackWasDown = true;

        var player = minecraft.player;
        if (!player.isBlocking()) {
            return;
        }

        var stack = player.getUseItem();
        if (!(stack.getItem() instanceof ShieldAbilityItem)) {
            return;
        }

        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return;
        }

        Alien.MOD.networking().sendToServer(C2SActivateShieldAbilityPayload.INSTANCE);
        while (minecraft.options.keyAttack.consumeClick()) {
            // The click was consumed by the shield ability, so don't also let vanilla process it as an attack.
        }
    }
}
