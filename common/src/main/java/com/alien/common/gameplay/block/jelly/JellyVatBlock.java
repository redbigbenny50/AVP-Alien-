package com.alien.common.gameplay.block.jelly;

import com.alien.common.gameplay.block.entity.jelly.JellyVatBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    /**
     * Player-placed vats are visible to the hive as jelly storage (groundwork; the economy will consume this later).
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (
            !level.isClientSide
                && placer instanceof Player
                && level.getBlockEntity(pos) instanceof JellyVatBlockEntity vat
        ) {
            vat.setHiveVisible(true);
        }
    }

    /**
     * Right-click with an item: - raw jelly -> add one level (commits the type on the first fill; rejected if it
     * mismatches a committed type) - a jelly block -> fill to full (same type rules) Empty hand is handled by
     * {@link #useWithoutItem} (withdraw everything).
     */
    @Override
    protected @NotNull ItemInteractionResult useItemOn(
        ItemStack stack,
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        BlockHitResult hit
    ) {
        if (!(level.getBlockEntity(pos) instanceof JellyVatBlockEntity vat)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        JellyType rawType = JellyItems.typeOfRawItem(stack);
        JellyType blockType = JellyItems.typeOfBlockItem(stack);

        // Not a jelly item -> let empty-hand / default handling run.
        if (rawType == null && blockType == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        JellyType incoming = rawType != null ? rawType : blockType;

        // Type lock: a committed vat only accepts its own type.
        if (!vat.canAccept(incoming)) {
            return ItemInteractionResult.FAIL;
        }
        // Already full: nothing to add.
        if (vat.isFull()) {
            return ItemInteractionResult.FAIL;
        }
        // A jelly block does a clean empty->full fill, so it's only accepted when the vat is empty. If any layers are
        // already present, reject it - otherwise the block would overwrite (and destroy) those layers, returning only
        // one block for what was several levels of jelly. Raw jelly still tops up one level at a time.
        if (blockType != null && vat.getFillLevel() > 0) {
            return ItemInteractionResult.FAIL;
        }

        if (!level.isClientSide) {
            vat.commitType(incoming);
            if (rawType != null) {
                vat.addOneLevel();
            } else {
                vat.setFillLevel(JellyVatBlockEntity.MAX_FILL);
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Empty-hand right-click withdraws the vat's entire contents and leaves it empty and re-typeable. */
    @Override
    protected @NotNull InteractionResult useWithoutItem(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        BlockHitResult hit
    ) {
        if (!(level.getBlockEntity(pos) instanceof JellyVatBlockEntity vat) || vat.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            JellyType type = vat.getJellyType();
            int fill = vat.getFillLevel();
            for (ItemStack drop : contentsFor(type, fill)) {
                player.getInventory().placeItemBackInInventory(drop);
            }
            vat.emptyAndUncommit();
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Custom drops. Silk touch -> the vat block AND its contents (you keep the whole filled vat). Normal break -> only
     * the jelly contents by level (one raw jelly per level; a full vat, level 9, drops one jelly block instead of nine
     * loose items), and the vat block itself is destroyed (not returned).
     */
    @Override
    public void playerDestroy(
        Level level,
        Player player,
        BlockPos pos,
        BlockState state,
        @Nullable BlockEntity blockEntity,
        ItemStack tool
    ) {
        if (!level.isClientSide && blockEntity instanceof JellyVatBlockEntity vat) {
            if (hasSilkTouch(level, tool)) {
                popResource(level, pos, new ItemStack(this));
            }
            // Contents drop either way; silk touch additionally returns the vat block above.
            for (ItemStack drop : contentsFor(vat.getJellyType(), vat.getFillLevel())) {
                popResource(level, pos, drop);
            }
        }
        // Award tool damage / stats without the default block drop (we handled drops above).
        player.awardStat(net.minecraft.stats.Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F);
    }

    /** The item stacks representing {@code fill} levels of {@code type}: a full block at MAX_FILL, else raw items. */
    private static java.util.List<ItemStack> contentsFor(JellyType type, int fill) {
        if (fill <= 0) {
            return java.util.List.of();
        }
        if (fill >= JellyVatBlockEntity.MAX_FILL) {
            return java.util.List.of(new ItemStack(JellyItems.fullBlock(type)));
        }
        return java.util.List.of(new ItemStack(JellyItems.rawItem(type), fill));
    }

    private static boolean hasSilkTouch(Level level, ItemStack tool) {
        var silk = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH);
        return EnchantmentHelper.getItemEnchantmentLevel(silk, tool) > 0;
    }
}
