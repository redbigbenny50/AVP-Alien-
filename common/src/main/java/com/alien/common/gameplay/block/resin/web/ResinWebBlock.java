package com.alien.common.gameplay.block.resin.web;

import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.party.AttackCampaign;
import com.alien.common.gameplay.hive.structure.HostParking;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

public class ResinWebBlock extends Block {

    private static final VoxelShape SHAPE = Block.box(0.8, 0.0, 0.8, 15.2, 16.0, 15.2);

    private static final Vec3 MOVEMENT_MODIFIER = new Vec3(0.05, 0.05F, 0.05);

    private static final Vec3 STUCK_MOVEMENT_MODIFIER = new Vec3(0.05, 0F, 0.05);

    public ResinWebBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NotNull VoxelShape getShape(
            @NotNull BlockState blockState,
            @NotNull BlockGetter blockGetter,
            @NotNull BlockPos blockPos,
            @NotNull CollisionContext collisionContext
    ) {
        return SHAPE;
    }

    /**
     * Cutting a captive loose is an act of WAR, and the hive notices.
     * <p>
     * The hive only accrues intrusion dwell against a player who is inside its claim AND has been recently hostile -
     * and until now the only thing that counted as hostile was hitting a member. So a player could walk into a host
     * chamber, cut the hive's larder loose, and walk out again, and the hive would never react. Stealing its food is
     * a blow, and is stamped as one: enough of it and a retribution campaign comes for you.
     * <p>
     * This fires BEFORE the block is removed, which is the only moment the captive is still detectably webbed. The
     * actual freeing happens in {@link #onRemove} a moment later - so an explosion or a piston still frees a captive,
     * it just does not pin the blame on anyone.
     */
    @Override
    public @NotNull BlockState playerWillDestroy(
            @NotNull Level level,
            @NotNull BlockPos blockPos,
            @NotNull BlockState blockState,
            @NotNull Player player
    ) {
        if (level instanceof ServerLevel serverLevel
                && player instanceof ServerPlayer serverPlayer
                && HostParking.holdsCaptive(serverLevel, blockPos)) {

            var location = HiveLocationRegistry.INSTANCE.getByChunk(
                    serverLevel.dimension(),
                    new ChunkPos(blockPos)
            );
            if (location == null) {
                // A chamber can sit in a chunk the hive has not claimed. Fall back to the nearest hive in the dim.
                location = HiveLocationRegistry.INSTANCE.findNearestInDim(serverLevel.dimension(), blockPos);
            }

            if (location != null) {
                AttackCampaign.recordHostileAct(serverLevel, location, serverPlayer);
            }
        }

        return super.playerWillDestroy(level, blockPos, blockState, player);
    }

    /**
     * Cutting the webbing frees the captive inside it.
     * <p>
     * A host embedded in a chamber is {@code setNoAi(true)} and nothing anywhere used to turn that back on, so a webbed
     * mob was frozen permanently and could not be rescued at all. Break the web and its AI comes back.
     */
    @Override
    protected void onRemove(
            @NotNull BlockState blockState,
            @NotNull Level level,
            @NotNull BlockPos blockPos,
            @NotNull BlockState newState,
            boolean movedByPiston
    ) {
        // Only when the web is actually GOING - not on a state swap of the same block.
        if (!blockState.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            HostParking.releaseAt(serverLevel, blockPos);
        }

        super.onRemove(blockState, level, blockPos, newState, movedByPiston);
    }

    @Override
    protected void entityInside(@NotNull BlockState blockState, @NotNull Level level, @NotNull BlockPos blockPos, Entity entity) {
        if (!entity.getType().is(AlienEntityTypeTags.ALIENS)) {
            var eyePos = entity.getEyePosition();
            var eyeBlockPos = BlockPos.containing(eyePos);
            var eyeBlockState = level.getBlockState(eyeBlockPos);
            var modifier = eyeBlockState.getBlock() instanceof ResinWebBlock
                    ? STUCK_MOVEMENT_MODIFIER
                    : MOVEMENT_MODIFIER;

            entity.makeStuckInBlock(blockState, modifier);
        }
    }
}