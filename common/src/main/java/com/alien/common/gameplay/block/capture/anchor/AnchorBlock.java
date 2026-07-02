package com.alien.common.gameplay.block.capture.anchor;

import com.alien.common.gameplay.block.entity.capture.anchor.AnchorBlockEntity;
import com.alien.common.registry.init.AlienBlockEntityTypes;
import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Capture-pipeline anchor: the fixed point that capture chains bind to. Mounts on floor, wall, or ceiling like a
 * grindstone (a {@code FACE} = FLOOR/WALL/CEILING attachment plus a horizontal {@code FACING}). It is a block entity so
 * the custom geo can be rendered (the angled arms are not expressible as a vanilla model) and so the bind point can be
 * tracked — {@link AnchorBlockEntity#chainAnchorPoint()} exposes the model's {@code gHandle} pivot in world space.
 */
public class AnchorBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<AnchorBlock> CODEC = simpleCodec(AnchorBlock::new);

    private static final VoxelShape FLOOR_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0);

    private static final VoxelShape CEILING_SHAPE = Block.box(0.0, 8.0, 0.0, 16.0, 16.0, 16.0);

    private static final Map<Direction, VoxelShape> WALL_SHAPES = Maps.newEnumMap(
            Map.of(
                    Direction.NORTH,
                    Block.box(0.0, 0.0, 8.0, 16.0, 16.0, 16.0),
                    Direction.SOUTH,
                    Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 8.0),
                    Direction.EAST,
                    Block.box(0.0, 0.0, 0.0, 8.0, 16.0, 16.0),
                    Direction.WEST,
                    Block.box(8.0, 0.0, 0.0, 16.0, 16.0, 16.0)
            )
    );

    public AnchorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
                this.defaultBlockState()
                        .setValue(FACE, AttachFace.FLOOR)
                        .setValue(FACING, Direction.NORTH)
        );
    }

    @Override
    protected @NotNull MapCodec<AnchorBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        for (Direction direction : context.getNearestLookingDirections()) {
            BlockState candidate;
            if (direction.getAxis() == Direction.Axis.Y) {
                candidate = this.defaultBlockState()
                        .setValue(FACE, direction == Direction.UP ? AttachFace.CEILING : AttachFace.FLOOR)
                        .setValue(FACING, context.getHorizontalDirection());
            } else {
                candidate = this.defaultBlockState()
                        .setValue(FACE, AttachFace.WALL)
                        .setValue(FACING, direction.getOpposite());
            }
            if (candidate.canSurvive(context.getLevel(), context.getClickedPos())) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    protected @NotNull VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> FLOOR_SHAPE;
            case CEILING -> CEILING_SHAPE;
            case WALL -> WALL_SHAPES.get(state.getValue(FACING));
        };
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        // Collision is kept, but the pathfinder treats the anchor as an obstacle so mobs route around it instead of
        // climbing onto the partial block and getting stuck oscillating on top (the way they do on stalagmites).
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING);
    }

    @Override
    protected @NotNull RenderShape getRenderShape(BlockState state) {
        // The geo is drawn by AnchorBlockEntityRenderer; the JSON model only provides break particles.
        return RenderShape.INVISIBLE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AnchorBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        if (level.isClientSide || type != AlienBlockEntityTypes.ANCHOR.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<AnchorBlockEntity>) (lvl, pos, st, be) -> be.serverTick();
    }

    /** Sneak-right-click an anchor to drop its chain. */
    @Override
    protected @NotNull InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (
                player.isShiftKeyDown()
                        && level.getBlockEntity(pos) instanceof AnchorBlockEntity anchor
                        && anchor.hasChain()
        ) {
            if (!level.isClientSide) {
                anchor.release();
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    /** Breaking the anchor frees whatever it was holding. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof AnchorBlockEntity anchor) {
            anchor.release();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}