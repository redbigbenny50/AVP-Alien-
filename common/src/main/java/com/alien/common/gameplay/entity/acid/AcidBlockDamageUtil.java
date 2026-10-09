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

    /**
     * [stated] Oct 3: blocks that stand up to acid THREE TIMES longer - avp_human's composite (steel and plastic).
     * Named by id, not imported, so avp_alien needs nothing from avp_human: without it the tag is simply empty.
     */
    private static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> ACID_RESISTANT_3X =
        net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.BLOCK,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("avp_human", "acid_resistant_3x")
        );

    /** How much slower acid eats an {@link #ACID_RESISTANT_3X} block. */
    private static final float ACID_RESISTANT_3X_FACTOR = 3.0F;

    /**
     * Oct 6 - performance. Acid eats blocks every {@value} ticks on the server, with each bite {@value} times as large
     * and aging the acid {@value} times as much, so a block melts at exactly the same speed and the acid lasts exactly
     * as long. Each bite also sends the crack-progress update to nearby players, so this cuts that traffic by the same
     * factor. 160 acid pools in a fight were costing 0.3 ms of every tick. The client keeps its every-tick smoke.
     */
    static final int SERVER_BITE_INTERVAL_TICKS = 4;

    public static void damageBlocks(Acid acid) {
        var level = acid.level();

        // Offset by id so a splash of pools does not all bite on the same tick.
        if (!level.isClientSide && (acid.tickCount + acid.getId()) % SERVER_BITE_INTERVAL_TICKS != 0) {
            return;
        }

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
        var amount = 0.2F * acid.getMultiplier() * SERVER_BITE_INTERVAL_TICKS;

        // Acid-resistant blocks take a third of the damage per tick, so they last exactly three times as long.
        if (blockStateBeforeDamage.is(ACID_RESISTANT_3X)) {
            amount /= ACID_RESISTANT_3X_FACTOR;
        }

        var result = BlockBreakProgressManager.damage(level, blockPos, amount);

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
            // Was "tickCount % (10..109) == 0" checked every tick - about a 1-in-42 chance per tick. A bite now covers
            // four ticks, so the same rate is about 1 in 10 per bite.
            if (acid.getRandom().nextInt(10) == 0) {
                level.playSound(null, acid, AlienSoundEvents.BLOCK_ACID_BURN.get(), SoundSource.NEUTRAL, 1F, 1F);
            }

            for (var i = 0; i < SERVER_BITE_INTERVAL_TICKS; i++) {
                acid.age();
            }
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
