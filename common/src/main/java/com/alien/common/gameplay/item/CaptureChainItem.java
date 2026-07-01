package com.alien.common.gameplay.item;

import com.alien.common.gameplay.block.capture.anchor.AnchorBlock;
import com.alien.common.gameplay.block.entity.capture.anchor.AnchorBlockEntity;
import com.alien.common.gameplay.capture.CaptureHoldManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * The capture chain — a heavy-duty restraint used to chain a mob to a capture {@link AnchorBlock}.
 * <p>
 * Right-click a mob to take hold of it: it is registered with {@link CaptureHoldManager}, which reels it toward you and
 * restricts its distance. Right-click the same mob again to let it go. This is deliberately <em>not</em> a vanilla leash — there is no rope to render through the
 * vanilla pipeline and nothing drops a {@code minecraft:lead} when the hold breaks. Right-click an anchor to bind the
 * mob you are holding to that anchor's chain; sneak-right-click an anchor to release its chain.
 */
public class CaptureChainItem extends Item {

    public CaptureChainItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult interactLivingEntity(
            @NotNull ItemStack stack,
            @NotNull Player player,
            @NotNull LivingEntity target,
            @NotNull InteractionHand hand
    ) {
        if (target instanceof Mob mob && target != player) {
            // Held by another player: don't interfere with their hold.
            if (CaptureHoldManager.isHeld(mob) && !CaptureHoldManager.isHeldBy(mob, player)) {
                return super.interactLivingEntity(stack, player, target, hand);
            }
            if (!player.level().isClientSide) {
                if (CaptureHoldManager.isHeldBy(mob, player)) {
                    CaptureHoldManager.release(mob); // toggle off — let the mob go
                } else {
                    CaptureHoldManager.hold(mob, player); // grab
                }
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        return super.interactLivingEntity(stack, player, target, hand);
    }

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (
                player == null
                        || !(level.getBlockState(pos).getBlock() instanceof AnchorBlock)
                        || !(level.getBlockEntity(pos) instanceof AnchorBlockEntity anchor)
        ) {
            return super.useOn(context);
        }
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        // Sneak: release any existing chain.
        if (player.isShiftKeyDown()) {
            if (anchor.hasChain()) {
                anchor.release();
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        }

        // Already chained: leave it.
        if (anchor.hasChain()) {
            return InteractionResult.PASS;
        }

        // Hand the mob the player is currently holding over to the anchor.
        Mob held = CaptureHoldManager.heldBy(serverLevel, player);
        if (held != null) {
            anchor.bind(held);
            CaptureHoldManager.release(held);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
}