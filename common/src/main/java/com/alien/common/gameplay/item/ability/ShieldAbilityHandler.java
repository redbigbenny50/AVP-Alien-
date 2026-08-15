package com.alien.common.gameplay.item.ability;

import net.minecraft.server.level.ServerPlayer;

public final class ShieldAbilityHandler {

    private ShieldAbilityHandler() {}

    public static void tryActivate(ServerPlayer player) {
        if (!player.isBlocking()) {
            return;
        }

        var stack = player.getUseItem();
        if (!(stack.getItem() instanceof ShieldAbilityItem abilityItem)) {
            return;
        }

        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return;
        }

        if (!abilityItem.canActivateShieldAbility(player, stack)) {
            return;
        }

        abilityItem.activateShieldAbility(player, stack);
    }
}
