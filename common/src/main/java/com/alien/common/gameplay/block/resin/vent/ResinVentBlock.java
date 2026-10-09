package com.alien.common.gameplay.block.resin.vent;

import com.alien.common.gameplay.block.entity.resin.vent.ResinVentBlockEntity;
import com.alien.common.registry.init.AlienBlockEntityTypes;
import com.blib.api.common.spatial.v1.block.BlockPosUtil;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ResinVentBlock extends BaseEntityBlock {

    public static final MapCodec<ResinVentBlock> CODEC = simpleCodec(ResinVentBlock::new);

    public ResinVentBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(DORMANT, false));
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void onRemove(
        @NotNull BlockState blockState,
        @NotNull Level level,
        @NotNull BlockPos blockPos,
        @NotNull BlockState blockState2,
        boolean bl
    ) {
        // 🚨🚨🚨 DEREGISTER BEFORE super, NOT AFTER. EVERY BROKEN VENT WAS LEAVING A GHOST.
        //
        // ⚠⚠ VANILLA'S BlockBehaviour.onRemove CALLS removeBlockEntity - verified in the 1.21.1 bytecode. So
        // reading getBlockEntity AFTER super returned NULL every time, the instanceof failed, and removeVent was
        // NEVER CALLED. The manager kept the entry forever.
        //
        // ⚠⚠ AND NOTHING ANYWHERE VALIDATES THAT A REGISTERED VENT'S BLOCK STILL EXISTS - the manager never
        // looks at the world. So a destroyed vent stayed a live entry: counted by the defence scan, offered to
        // emergencePosNear, and able to pass its clearance checks on the empty air where it used to be. A hive
        // could report vents it did not have and try to push defenders through them.
        //
        // ⭐ Reading the block entity FIRST is the whole fix - the state is still intact at that point.
        var blockEntity = level.getBlockEntity(blockPos);

        if (blockEntity instanceof ResinVentBlockEntity resinVentBlockEntity) {
            var location = resinVentBlockEntity.getBoundLocation();
            if (location != null) {
                location.ventManager().removeVent(blockPos);
            }
        }

        super.onRemove(blockState, level, blockPos, blockState2, bl);

        if (BlockPosUtil.isFireAdjacent(level, blockPos)) {
            level.setBlock(blockPos, Blocks.BASALT.defaultBlockState(), 3);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos blockPos, @NotNull BlockState blockState) {
        return new ResinVentBlockEntity(blockPos, blockState);
    }

    /**
     * True once this vent has accepted that its hive is gone.
     * <p>
     * 🚨🚨 A DORMANT VENT IS NOT TICKED AT ALL - see {@link #getTicker}. Nothing removes hive blocks when a location
     * dies, and ruins are wanted, but a ruin should be SCENERY: without this every vent of every hive that ever died
     * kept running a 64-ring nearest-hive search forever, and that load only accumulated over a world's life.
     * </p>
     * <p>
     * ⚠ A dormant vent cannot wake itself, because it is not ticking. It is revived by the hive that adopts its chunk -
     * see {@code HiveLocationClaims.claim} - which is event-driven and free, rather than polled.
     * </p>
     * <p>
     * ⚠ NO MODEL OR DATAGEN CHANGE: the vents use createTrivialCube, whose blockstate is a single {@code ""} variant
     * that matches every state.
     * </p>
     */
    public static final BooleanProperty DORMANT = BooleanProperty.create("dormant");

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DORMANT);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        Level level,
        @NotNull BlockState blockState,
        @NotNull BlockEntityType<T> blockEntityType
    ) {
        // ⭐ THE WHOLE POINT: a dead vent returns no ticker, so Minecraft never adds it to the ticking list.
        if (level.isClientSide || blockState.getValue(DORMANT)) {
            return null;
        }

        return createTickerHelper(blockEntityType, AlienBlockEntityTypes.RESIN_VENT.get(), ResinVentBlockEntity::serverTick);
    }

    @Override
    protected @NotNull RenderShape getRenderShape(@NotNull BlockState blockState) {
        return RenderShape.MODEL;
    }
}
