package com.alien.mixin;

import com.alien.common.registry.tag.AlienBlockTags;
import com.blib.api.common.spatial.v1.block.BlockPosUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Strain-aware resin conversion, applied at the {@link BlockBehaviour} level so it covers the entire resin family
 * (bricks, smooth, ribbed, decoratives, slabs, stairs, walls, nodes, vents, veins, webs) without per-class subclasses.
 * <ul>
 * <li>Normal, aberrant, and irradiated resin turns to basalt when consumed while fire is adjacent — the behavior
 * {@code ResinBlock} used to implement for the four plain resin blocks only.</li>
 * <li>Nether resin is exempt from the basalt path entirely (it is fire-native) and instead turns to netherrack when
 * exposed to a freezing source (powder snow contact; irradiated acid is handled in {@code AcidBlockDamageUtil}).</li>
 * </ul>
 */
@Mixin(BlockBehaviour.class)
public class MixinBlockBehaviour_ResinConversion {

    @Inject(method = "onRemove", at = @At("TAIL"))
    private void avp_alien$convertBurnedResinToBasalt(
        BlockState blockState,
        Level level,
        BlockPos blockPos,
        BlockState newBlockState,
        boolean movedByPiston,
        CallbackInfo callbackInfo
    ) {
        if (level.isClientSide || !blockState.is(AlienBlockTags.RESIN) || blockState.is(AlienBlockTags.NETHER_RESIN)) {
            return;
        }

        // Veins and webs are wisps, not masses: fire consumes them and leaves nothing behind.
        if (blockState.is(AlienBlockTags.RESIN_VEINS) || blockState.is(AlienBlockTags.RESIN_WEBS)) {
            return;
        }

        if (BlockPosUtil.isFireAdjacent(level, blockPos)) {
            level.setBlock(blockPos, Blocks.BASALT.defaultBlockState(), 3);
        }
    }

    @Inject(method = "onPlace", at = @At("TAIL"))
    private void avp_alien$freezeNetherResinOnPlace(
        BlockState blockState,
        Level level,
        BlockPos blockPos,
        BlockState oldBlockState,
        boolean movedByPiston,
        CallbackInfo callbackInfo
    ) {
        avp_alien$tryFreezeNetherResin(blockState, level, blockPos);
    }

    @Inject(method = "neighborChanged", at = @At("TAIL"))
    private void avp_alien$freezeNetherResinOnNeighborChanged(
        BlockState blockState,
        Level level,
        BlockPos blockPos,
        Block neighborBlock,
        BlockPos neighborBlockPos,
        boolean movedByPiston,
        CallbackInfo callbackInfo
    ) {
        avp_alien$tryFreezeNetherResin(blockState, level, blockPos);
    }

    @Unique
    private void avp_alien$tryFreezeNetherResin(BlockState blockState, Level level, BlockPos blockPos) {
        if (level.isClientSide || !blockState.is(AlienBlockTags.NETHER_RESIN)) {
            return;
        }

        for (var direction : Direction.values()) {
            if (level.getBlockState(blockPos.relative(direction)).is(Blocks.POWDER_SNOW)) {
                if (blockState.is(AlienBlockTags.RESIN_VEINS) || blockState.is(AlienBlockTags.RESIN_WEBS)) {
                    // Frozen veins and webs simply perish rather than petrifying into a full block.
                    level.destroyBlock(blockPos, false);
                } else {
                    level.setBlock(blockPos, Blocks.NETHERRACK.defaultBlockState(), 3);
                }

                return;
            }
        }
    }
}
