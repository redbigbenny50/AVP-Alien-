package com.alien.common.gameplay.entity.acid;

import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.tag.AlienBlockTags;
import com.blib.api.common.block.v1.BlockBreakProgressManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

public class AcidBlockDamageUtil {

    public static void damageBlocks(Acid acid) {
        var level = acid.level();

        BlockPos.betweenClosedStream(acid.getBoundingBox().inflate(0, 0.1, 0))
            .filter(blockPos -> canAcidDestroyBlock(acid, blockPos, level))
            .forEach(blockPos -> tryDestroyBlock(acid, blockPos, level));
    }

    private static void tryDestroyBlock(Acid acid, BlockPos blockPos, Level level) {
        if (!level.isClientSide) {
            if (acid.isInWater() || !acid.onGround()) {
                return;
            }

            damageBlock(acid, blockPos, level);
        } else {
            spawnClientSideParticles(acid, level);
        }
    }

    private static void damageBlock(Acid acid, BlockPos blockPos, Level level) {
        var blockStateBeforeDamage = level.getBlockState(blockPos);
        var result = BlockBreakProgressManager.damage(level, blockPos, 0.2F * acid.getMultiplier());

        switch (result) {
            case DAMAGED -> {

                if (acid.isNetherAfflicted()) {
                    var above = blockPos.above();

                    if (level.getBlockState(above).isAir()) {
                        level.setBlockAndUpdate(above, Blocks.FIRE.defaultBlockState());
                    }
                }
            }
            case DESTROYED -> {
                if (acid.isIrradiated()) {
                    // Freezing blood: veins and webs it destroys simply perish; solid nether resin becomes
                    // netherrack; anything else it eats through freezes over as blue ice.
                    if (
                        blockStateBeforeDamage.is(AlienBlockTags.RESIN_VEINS)
                            || blockStateBeforeDamage.is(AlienBlockTags.RESIN_WEBS)
                    ) {
                        break;
                    }

                    var frozenBlock = blockStateBeforeDamage.is(AlienBlockTags.NETHER_RESIN)
                        ? Blocks.NETHERRACK
                        : Blocks.BLUE_ICE;

                    level.setBlockAndUpdate(blockPos, frozenBlock.defaultBlockState());
                }
            }
        }

        if (result != BlockBreakProgressManager.Result.NOT_DAMAGED) {
            if (acid.tickCount % (acid.getRandom().nextInt(100) + 10) == 0) {
                level.playSound(null, acid, AlienSoundEvents.BLOCK_ACID_BURN.get(), SoundSource.NEUTRAL, 1F, 1F);
            }

            acid.age();
        }
    }

    private static void spawnClientSideParticles(Acid acid, Level level) {
        if (acid.isIrradiated()) {
            return;
        }

        level.addAlwaysVisibleParticle(
            ParticleTypes.SMOKE,
            acid.getRandomX(0.5),
            acid.getRandomY(),
            acid.getRandomZ(0.5),
            0,
            0,
            0
        );
    }

    private static boolean canAcidDestroyBlock(Acid acid, BlockPos blockPos, Level level) {
        var blockState = level.getBlockState(blockPos);

        if (blockState.isAir()) {
            return false;
        }

        if (acid.isNetherAfflicted()) {
            return !blockState.is(AlienBlockTags.NETHER_ACID_IMMUNE);
        }

        if (acid.isIrradiated()) {
            // Nether resin is carved out of the immunity umbrella for irradiated blood specifically: the freezing
            // blood converts it to netherrack (see damageBlock) instead of leaving it untouched.
            return blockState.is(AlienBlockTags.NETHER_RESIN) || !blockState.is(AlienBlockTags.IRRADIATED_ACID_IMMUNE);
        }

        return !blockState.is(AlienBlockTags.ACID_IMMUNE);
    }
}
