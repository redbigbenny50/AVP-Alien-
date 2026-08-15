package com.alien.mixin;

import com.alien.compatibility.AlienModGates;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tells a player holding a gated spawn egg why it does nothing. See {@link AlienModGates}.
 * <p>
 * The creative tabs already hide these eggs, so the only ways to be holding one are {@code /give} or a save made while
 * the sibling mod was installed. Cancelling at HEAD covers both use paths AND the branch of {@code useOn} that
 * retargets a spawner block, which would otherwise leave a spawner quietly trying - and failing - to produce a gated
 * mob forever.
 */
@Mixin(SpawnEggItem.class)
public abstract class MixinSpawnEggItem_GatedEntityDeny {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void avp_alien$denyEggOnBlock(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (avp_alien$refuse(context.getLevel(), context.getPlayer(), context.getItemInHand())) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void avp_alien$denyEggInAir(
        Level level,
        Player player,
        InteractionHand hand,
        CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir
    ) {
        var stack = player.getItemInHand(hand);
        if (avp_alien$refuse(level, player, stack)) {
            cir.setReturnValue(InteractionResultHolder.fail(stack));
        }
    }

    /** True when this egg is gated. Messages the player once, server side, so the text is not sent twice. */
    private boolean avp_alien$refuse(Level level, Player player, ItemStack stack) {
        var type = ((SpawnEggItem) (Object) this).getType(stack);
        if (type == null) {
            return false;
        }

        var missingMod = AlienModGates.missingModFor(type);
        if (missingMod == null) {
            return false;
        }

        if (!level.isClientSide && player != null) {
            player.displayClientMessage(AlienModGates.refusalMessage(type, missingMod), true);
        }
        return true;
    }
}
