package com.alien.mixin;

import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.tag.AlienBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells a hive the moment one of its blocks is broken, so the damaged piece is scanned within about a second instead of
 * whenever the upkeep cursor next comes round to it.
 * <p>
 * WHY THIS EXISTS. {@code HiveLocation.tickStructureUpkeep} scans exactly ONE built piece per upkeep interval, cycling
 * with a cursor, so time-to-notice scales with hive size: roughly a minute on a small hive and over three on a finished
 * one. A player could stand at a hole for a minute and conclude repair was broken, when it simply had not been asked
 * yet. Reporting the break turns detection into an event and stops it scaling at all; the cursor sweep stays as the
 * safety net for damage nobody reports.
 * </p>
 * <p>
 * ⚠⚠ THIS IS ONE OF THE HOTTEST PATHS IN THE GAME. {@code onRemove} fires for EVERY block change in the world - every
 * block of every explosion, every piston, every worldgen edit. The filter order below is therefore load-bearing and
 * must not be rearranged:
 * </p>
 * <ol>
 * <li>SERVER ONLY - halves the calls for free.</li>
 * <li>Is the block that vanished HIVE RESIN? A tag lookup, and it rejects essentially every block change that happens
 * anywhere in the world. This has to come FIRST.</li>
 * <li>Did it become AIR or fluid? A resin-for-resin swap is the hive building itself, not a wound - this is what stops
 * a stamping hive flagging its own construction every tick.</li>
 * <li>ONLY NOW the registry map lookup, which is the expensive step, and by here it runs a handful of times per broken
 * wall rather than millions of times per crater.</li>
 * </ol>
 * <p>
 * The queue on the other end is bounded, so even a nuke inside a hive cannot turn this into a leak.
 * </p>
 */
@Mixin(BlockBehaviour.class)
public class MixinBlockBehaviour_HiveBreachFlag {

    @Inject(method = "onRemove", at = @At("TAIL"))
    private void avp_alien$flagHiveBreach(
        BlockState blockState,
        Level level,
        BlockPos blockPos,
        BlockState newBlockState,
        boolean movedByPiston,
        CallbackInfo callbackInfo
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!blockState.is(AlienBlockTags.RESIN)) {
            return;
        }

        // A hole, not a re-stamp: only air and fluids count as a wound.
        if (!newBlockState.isAir() && newBlockState.getFluidState().isEmpty()) {
            return;
        }

        var location = HiveLocationRegistry.INSTANCE.getByChunk(serverLevel.dimension(), new ChunkPos(blockPos));

        if (location != null && location.isAlive()) {
            location.flagBreachAt(blockPos);
        }
    }
}
