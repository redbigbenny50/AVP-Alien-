package com.alien.common.gameplay.item;

import net.minecraft.world.item.Item;

/**
 * Poison jelly is a brewing ingredient only: Awkward + Poison Jelly brews the Growth Suppression potion (see
 * {@code AlienPotions}). The old direct right-click-a-xeno interaction is deliberately gone - suppressing a
 * xenomorph's growth now requires the potion, mirroring how royal jelly requires the Metamorphosis potion.
 */
public class PoisonJellyItem extends Item {

    public PoisonJellyItem() {
        super(new Properties());
    }
}
