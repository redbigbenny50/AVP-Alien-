package com.alien.neoforge.mixin;

import com.alien.common.registry.tag.AlienBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockExtension;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Makes the entire resin family of the normal, aberrant, and irradiated strains flammable on NeoForge (bricks, smooth,
 * ribbed, decoratives, slabs, stairs, walls, nodes, vents, veins, webs), mirroring the Fabric-side
 * {@code FlammableBlockRegistry}. Nether resin is exempt and takes no fire damage.
 */
@Mixin(Block.class)
public class MixinBlock_FlammableResinFamily implements IBlockExtension {

    @Override
    public int getFireSpreadSpeed(
        @NotNull BlockState state,
        @NotNull BlockGetter level,
        @NotNull BlockPos pos,
        @NotNull Direction direction
    ) {
        return state.is(AlienBlockTags.RESIN) && !state.is(AlienBlockTags.NETHER_RESIN)
            ? 1
            : IBlockExtension.super.getFireSpreadSpeed(state, level, pos, direction);
    }

    @Override
    public int getFlammability(
        @NotNull BlockState state,
        @NotNull BlockGetter level,
        @NotNull BlockPos pos,
        @NotNull Direction direction
    ) {
        return state.is(AlienBlockTags.RESIN) && !state.is(AlienBlockTags.NETHER_RESIN)
            ? 20
            : IBlockExtension.super.getFlammability(state, level, pos, direction);
    }
}
