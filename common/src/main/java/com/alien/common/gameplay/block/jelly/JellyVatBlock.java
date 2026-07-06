package com.alien.common.gameplay.block.jelly;

import com.alien.common.gameplay.block.entity.jelly.JellyVatBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * A floor-placed jelly vat that visibly fills with royal or scourge jelly. One block, one model; the
 * {@link #JELLY_TYPE} state fixes which jelly it holds (royal vs scourge — also how the structure parser tells them
 * apart in a piece's palette), while the fill level (0..9) lives in {@link JellyVatBlockEntity} and drives the
 * fill-stage bone shown by the block-entity renderer.
 * <p>
 * No facing: the vat is rotationally symmetric on the floor (the model's baked 45° gives the diagonal look regardless
 * of placement). Rendered via a block-entity renderer, so the blockstate JSON points at {@code builtin/entity} and this
 * block defines no cube model of its own.
 */
public class JellyVatBlock extends BaseEntityBlock {

    public static final EnumProperty<JellyType> JELLY_TYPE = EnumProperty.create("jelly_type", JellyType.class);

    // Sits within the block footprint, tall enough for the vat body; small inset so it reads as a vessel on the floor.
    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 14.0, 14.0);

    public JellyVatBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(JELLY_TYPE, JellyType.ROYAL));
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(JellyVatBlock::new);
    }

    @Override
    protected @NotNull VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected @NotNull VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(JELLY_TYPE);
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new JellyVatBlockEntity(pos, state);
    }
}
