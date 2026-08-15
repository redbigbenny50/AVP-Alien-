package com.alien.common.gameplay.item;

import net.minecraft.world.entity.LivingEntity;

public interface NoBlockingMovementPenaltyItem {

    static boolean isActive(LivingEntity entity) {
        return entity.isUsingItem() && entity.getUseItem().getItem() instanceof NoBlockingMovementPenaltyItem;
    }
}
