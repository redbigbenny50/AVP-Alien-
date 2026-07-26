package com.alien.common.gameplay.block.resin;

import net.minecraft.world.level.block.Block;

/**
 * Marker class for the plain resin blocks. Fire-to-basalt conversion for the whole resin family (and the nether
 * freeze-to-netherrack rule) lives in {@code MixinBlockBehaviour_ResinConversion}; flammability is registered in
 * {@code FlammableBlockRegistry} on Fabric and via the block mixins on NeoForge.
 */
public class ResinBlock extends Block {

    public ResinBlock(Properties properties) {
        super(properties);
    }
}
