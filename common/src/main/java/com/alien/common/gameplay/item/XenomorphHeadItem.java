package com.alien.common.gameplay.item;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

/**
 * Wearable and placeable xenomorph head trophy.
 */
public class XenomorphHeadItem extends StandingAndWallBlockItem implements Equipable {

    public XenomorphHeadItem(Block standingBlock, Block wallBlock, Properties properties) {
        super(standingBlock, wallBlock, properties, Direction.DOWN);
    }

    @Override
    public @NotNull EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }
}
