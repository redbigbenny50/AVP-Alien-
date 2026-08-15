package com.alien.common.gameplay.item;

import com.alien.common.gameplay.block.capture.anchor.AnchorBlock;
import com.alien.common.gameplay.block.entity.capture.anchor.AnchorBlockEntity;
import com.alien.common.gameplay.capture.CaptureChainInteraction;
import com.alien.common.gameplay.capture.CaptureHoldManager;
import com.alien.common.registry.init.item.AlienItems;
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
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

/**
 * The capture chain — a heavy-duty restraint used to chain a mob to a capture {@link AnchorBlock}.
 * <p>
 * Right-click a mob to take hold of it: it is registered with {@link CaptureHoldManager}, which reels it toward you and
 * restricts its distance. Right-click the same mob again to let it go. This is deliberately <em>not</em> a vanilla
 * leash — there is no rope to render through the vanilla pipeline. Binding a held mob to an anchor consumes one capture
 * chain; sneak-right-clicking the anchor releases the mob and drops the capture chain back as an item (lead-style).
 * Merely grabbing/letting go of a mob does not consume anything.
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
        // Fallback path for mobs whose own right-click does not consume the interaction. Mobs that would otherwise
        // swallow it first (e.g. villager trading) are handled earlier by the loader interaction events, which call
        // the same CaptureChainInteraction logic before the mob's interact runs.
        InteractionResult result = CaptureChainInteraction.tryHold(player, target, hand);
        if (result != InteractionResult.PASS) {
            return result;
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

        // Sneak: release any existing chain. The physical chain frees and drops back as an item (lead-style).
        if (player.isShiftKeyDown()) {
            if (anchor.hasChain()) {
                anchor.release();
                Block.popResource(serverLevel, pos, new ItemStack(AlienItems.CAPTURE_CHAIN.get()));
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
            if (!player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
}
