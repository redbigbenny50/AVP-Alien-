package com.alien.common.gameplay.item.ability;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public interface ShieldAbilityItem {

    default boolean canActivateShieldAbility(ServerPlayer player, ItemStack stack) {
        return true;
    }

    void activateShieldAbility(ServerPlayer player, ItemStack stack);
}
