package com.alien.common.gameplay.block;

import com.alien.common.gameplay.block.entity.container.ResinContainerBlockEntity;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A resin-and-chitin strongbox with the capacity of a double chest.
 * <p>
 * [stated] "this is a chest that has the inventory space of a double chest. it is made from resin and chitin of its
 * strain type... the containers are acid proof and explosion proof. they can be places on the floor ceiling and walls
 * with the top of the chest facing you when you place it."
 * </p>
 * <h2>⚠⚠ SHULKER RULES: IT KEEPS ITS CONTENTS WHEN BROKEN</h2> [stated] "keep like shulker box". That is why this does
 * NOT extend a chest base class - every vanilla chest drops its inventory on break. The contents ride out on the
 * dropped item's {@code CONTAINER} component instead, which is what makes a full container a haul worth carrying rather
 * than a mess on the floor.
 * <h2>⚠ SIX-WAY FACING, NOT HORIZONTAL</h2> Floors, walls and ceilings all mount it, and
 * {@code getNearestLookingDirection().getOpposite()} is what turns "the top of the chest faces you when you place it"
 * into a blockstate. A HorizontalDirectionalBlock could not express the ceiling case at all.
 */
public class ResinContainerBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<ResinContainerBlock> CODEC = simpleCodec(ResinContainerBlock::new);

    /**
     * ⭐⭐ THE ANCHOR'S PLACEMENT MODEL, NOT A 6-WAY FACING. [stated] "the placement of this thing is exactly like the
     * anchor. cant you do the same settings for that when it comes to placement here?"
     * <p>
     * ⚠⚠ THIS IS BETTER THAN WHAT I HAD, AND NOT ONLY BECAUSE IT IS ALREADY PROVEN. A single 6-way {@code FACING}
     * cannot express ROTATION: a container on the floor could only ever face straight up, with no way to turn it to
     * suit the room. {@code AttachFace} (FLOOR/WALL/CEILING) plus a HORIZONTAL {@code FACING} gives the mount AND the
     * yaw, which is exactly how a grindstone - and the anchor - behave.
     * </p>
     */
    public ResinContainerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.defaultBlockState()
                .setValue(FACE, AttachFace.FLOOR)
                .setValue(FACING, Direction.NORTH)
        );
    }

    @Override
    protected @NotNull MapCodec<ResinContainerBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING);
    }

    /**
     * ⚠ THE ANCHOR'S EXACT PLACEMENT LOOP. Walks {@code getNearestLookingDirections()} and takes the FIRST candidate
     * that {@code canSurvive}, so a face the container cannot attach to is skipped rather than placed and left broken.
     * A vertical hit becomes FLOOR or CEILING with the player's horizontal facing for yaw; a horizontal hit becomes
     * WALL facing out of that wall.
     */
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
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new ResinContainerBlockEntity(pos, state);
    }

    /**
     * ⚠ MODEL, NOT INVISIBLE. The animated renderer draws the ribs opening and closing, but the block still needs a
     * baked model for the item form and for the static case - see the renderer note in ResinContainerBlockEntity.
     */
    /**
     * ⭐ 14 UNITS DEEP OUT FROM THE MOUNTING FACE. [stated] "it is not a full 16x16x16 cube its 14 tall."
     * <p>
     * ⚠ Same structure as the anchor's, which is why the wall case is a map keyed on FACING - on a wall the depth runs
     * along the horizontal facing, so each of the four walls needs its own box.
     * </p>
     */
    private static final VoxelShape FLOOR_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 14.0, 16.0);

    private static final VoxelShape CEILING_SHAPE = Block.box(0.0, 2.0, 0.0, 16.0, 16.0, 16.0);

    private static final Map<Direction, VoxelShape> WALL_SHAPES = new java.util.EnumMap<>(
        Map.of(
            Direction.NORTH,
            Block.box(0.0, 0.0, 2.0, 16.0, 16.0, 16.0),
            Direction.SOUTH,
            Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 14.0),
            Direction.EAST,
            Block.box(0.0, 0.0, 0.0, 14.0, 16.0, 16.0),
            Direction.WEST,
            Block.box(2.0, 0.0, 0.0, 16.0, 16.0, 16.0)
        )
    );

    @Override
    protected @NotNull VoxelShape getShape(
        BlockState state,
        @NotNull BlockGetter level,
        @NotNull BlockPos pos,
        @NotNull CollisionContext context
    ) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> FLOOR_SHAPE;
            case CEILING -> CEILING_SHAPE;
            case WALL -> WALL_SHAPES.get(state.getValue(FACING));
        };
    }

    @Override
    protected @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(
        @NotNull BlockState state,
        Level level,
        @NotNull BlockPos pos,
        @NotNull Player player,
        @NotNull BlockHitResult hitResult
    ) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // [stated] "player can open" - the hive itself never spends what is in here, so there is no ownership check.
        if (level.getBlockEntity(pos) instanceof ResinContainerBlockEntity container) {
            player.openMenu(container);
        }

        return InteractionResult.CONSUME;
    }

    /**
     * ⚠⚠ THE CONTENTS LEAVE WITH THE BLOCK, NOT ON THE FLOOR - and this is the half that makes it work.
     * <p>
     * Vanilla's container teardown empties a block entity into the world when the block goes. A shulker box avoids that
     * by writing its inventory onto the DROPPED ITEM here, and its loot table then copies the component across. Both
     * halves are required: without this the item is empty, without the loot table's copy_components the component never
     * reaches the stack.
     * </p>
     */
    /**
     * ⚠⚠ WITHOUT THIS THE LID NEVER MOVES FOR ANYONE ELSE. Vanilla routes block events to the BLOCK first and drops
     * them unless the block forwards them; the block entity never sees the packet on its own. Same reason ChestBlock
     * overrides it.
     */
    @Override
    protected boolean triggerEvent(@NotNull BlockState state, Level level, @NotNull BlockPos pos, int id, int param) {
        super.triggerEvent(state, level, pos, id, param);

        var blockEntity = level.getBlockEntity(pos);

        return blockEntity != null && blockEntity.triggerEvent(id, param);
    }

    @Override
    public @NotNull BlockState playerWillDestroy(
        @NotNull Level level,
        @NotNull BlockPos pos,
        @NotNull BlockState state,
        @NotNull Player player
    ) {
        if (level.getBlockEntity(pos) instanceof ResinContainerBlockEntity container) {
            // ⚠ setChanged is what commits the inventory into the block entity's saved components before the loot
            // table reads them. Skipping it drops a container that LOOKS full and comes back empty.
            container.setChanged();
        }

        return super.playerWillDestroy(level, pos, state, player);
    }
}
