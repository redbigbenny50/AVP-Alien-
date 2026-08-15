package com.alien.common.gameplay.capture;

import com.alien.common.gameplay.item.CaptureChainItem;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Shared "grab a mob with the capture chain" logic, invoked from the loader interaction events (Fabric
 * {@code UseEntityCallback}, NeoForge {@code PlayerInteractEvent.EntityInteract}). These fire <em>before</em> the mob's
 * own right-click, so a held capture chain grabs the mob before something like a villager's trade window can consume
 * the interaction. Returns a consuming result when the chain handled the click (so the caller cancels the vanilla
 * interaction), or {@link InteractionResult#PASS} to let the normal behaviour proceed.
 * <p>
 * Behaviour mirrors {@link CaptureChainItem}: grab an un-held mob, or let go of one this player is already holding.
 */
public final class CaptureChainInteraction {

    private CaptureChainInteraction() {}

    public static InteractionResult tryHold(Player player, Entity target, InteractionHand hand) {
        if (!(player.getItemInHand(hand).getItem() instanceof CaptureChainItem)) {
            return InteractionResult.PASS;
        }
        if (!(target instanceof Mob mob) || target == player) {
            return InteractionResult.PASS;
        }
        if (mob.getType().is(AlienEntityTypeTags.CAPTURE_CHAIN_BLACKLIST)) {
            // Bosses and other blacklisted mobs cannot be grabbed or chained.
            return InteractionResult.PASS;
        }
        // Held by another player: don't interfere with their hold.
        if (CaptureHoldManager.isHeld(mob) && !CaptureHoldManager.isHeldBy(mob, player)) {
            return InteractionResult.PASS;
        }
        if (!player.level().isClientSide && player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            // Sneak on a chained mob: break the chain and drop the capture chain back (lead-style release).
            if (player.isShiftKeyDown() && MobChainManager.isLinked(mob)) {
                MobChainManager.unlink(mob);
                net.minecraft.world.level.block.Block.popResource(
                    serverLevel,
                    mob.blockPosition(),
                    new net.minecraft.world.item.ItemStack(com.alien.common.registry.init.item.AlienItems.CAPTURE_CHAIN.get())
                );
                return InteractionResult.SUCCESS;
            }
            // Already holding a different mob: chain the two together, consuming one capture chain.
            Mob heldOther = CaptureHoldManager.heldBy(serverLevel, player);
            if (
                heldOther != null
                    && !heldOther.getUUID().equals(mob.getUUID())
                    && !MobChainManager.isLinked(mob)
                    && !MobChainManager.isLinked(heldOther)
            ) {
                MobChainManager.link(heldOther, mob);
                CaptureHoldManager.release(heldOther);
                if (!player.getAbilities().instabuild) {
                    player.getItemInHand(hand).shrink(1);
                }
                return InteractionResult.SUCCESS;
            }
            // Otherwise toggle the simple hold.
            if (CaptureHoldManager.isHeldBy(mob, player)) {
                CaptureHoldManager.release(mob); // toggle off — let the mob go
            } else {
                CaptureHoldManager.hold(mob, player); // grab
            }
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }
}
