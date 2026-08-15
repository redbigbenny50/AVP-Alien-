package com.alien.common.gameplay.block.xenomorph.head;

import com.alien.common.gameplay.block.entity.xenomorph.head.XenomorphHeadBlockEntity;
import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class XenomorphWallHeadBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private static final Map<Direction, VoxelShape> AABBS = Maps.newEnumMap(
        Map.of(
            Direction.NORTH,
            Block.box(4.0, 4.0, 8.0, 12.0, 12.0, 16.0),
            Direction.SOUTH,
            Block.box(4.0, 4.0, 0.0, 12.0, 12.0, 8.0),
            Direction.EAST,
            Block.box(0.0, 4.0, 4.0, 8.0, 12.0, 12.0),
            Direction.WEST,
            Block.box(8.0, 4.0, 4.0, 16.0, 12.0, 12.0)
        )
    );

    private final String itemPath;

    public XenomorphWallHeadBlock(String itemPath, BlockBehaviour.Properties properties) {
        super(properties);
        this.itemPath = itemPath;
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    public String itemPath() {
        return itemPath;
    }

    @Override
    public @NotNull String getDescriptionId() {
        return this.asItem().getDescriptionId();
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(properties -> new XenomorphWallHeadBlock(itemPath, properties));
    }

    @Override
    protected @NotNull VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AABBS.get(state.getValue(FACING));
    }

    @Override
    protected @NotNull VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var clickedFace = context.getClickedFace();

        if (clickedFace.getAxis().isVertical()) {
            return null;
        }

        return this.defaultBlockState().setValue(FACING, clickedFace);
    }

    @Override
    protected @NotNull BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected @NotNull BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new XenomorphHeadBlockEntity(pos, state);
    }
}
