package com.alien.common.gameplay.item;

import com.alien.common.gameplay.entity.living.alien.xenomorph.empress.Empress;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.lifecycle.ForcedQueenSettlement;
import com.alien.common.registry.init.block.AlienBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Poison jelly, compressed. Storage like the other jelly blocks, and the survival way to place a hive.
 * <p>
 * [stated] "using this block on a new queen forces her to make a hive where standing" - and [stated] "people were
 * asking for a poison jelly block for storage and this gives it a use."
 * </p>
 * <p>
 * ⚠⚠ THE TERRITORY GUARD IS THE REASON THIS IS SAFE TO GIVE PLAYERS. [stated] "it still shouldnt cause hives to overlap
 * ... otherwise this would let people wreck the entire gap system." {@link ForcedQueenSettlement} waives only the
 * surface rule; spacing, existing claims and rival lineages all still refuse, and the block is NOT consumed on a
 * refusal.
 * </p>
 */
public class PoisonJellyBlockItem extends BlockItem {

    public PoisonJellyBlockItem() {
        super(AlienBlocks.POISON_JELLY_BLOCK.get(), new Properties().stacksTo(64));
    }

    @Override
    public @NotNull InteractionResult interactLivingEntity(
        @NotNull ItemStack stack,
        @NotNull Player player,
        @NotNull LivingEntity target,
        @NotNull InteractionHand hand
    ) {
        // WARNING: QUEENS **AND** EMPRESSES. Empress does not extend Queen, so a Queen-typed check silently did
        // nothing on her - and she is the caste that needs this most, having no lifecycle of her own to fall back on.
        if (!(target instanceof Queen) && !(target instanceof Empress)) {
            return InteractionResult.PASS;
        }

        var queen = (com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph) target;

        // ⭐ THE CLIENT ANSWERS FIRST AND PREDICTS THE SWING. Whether she accepts needs server-side hive state, so a
        // client-side check is impossible - and the same mistake on the royal jelly block made feeding a queen look
        // like a dead click for weeks. A predicted swing plus a stated reason a tick later reads far better.
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        var refusal = ForcedQueenSettlement.force(serverLevel, queen, player);
        if (refusal != null) {
            // !! CHAT, NOT THE ACTION BAR. [stated] "the warning ... needs to linger longer i can barely see it and
            // have to click on the queen multiple times to read it." The action bar fades in about two seconds, and
            // these refusals are the ONE thing a player has to read carefully - they name why she was refused and
            // what to do about it. Chat keeps them, and keeps them scrollable if several arrive.
            player.displayClientMessage(Component.literal(refusal).withStyle(ChatFormatting.RED), false);
            // ⚠ NOT CONSUMED ON A REFUSAL. Eating an expensive block and doing nothing is what gets reported as
            // "the item is broken" when the real answer is "you were standing in a claim".
            return InteractionResult.CONSUME;
        }

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(
        @NotNull ItemStack stack,
        Item.@NotNull TooltipContext context,
        @NotNull List<Component> tooltip,
        @NotNull TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);

        // ⭐ WEYLAND-YUTANI HOUSE VOICE, matching the field manual: dry, corporate, quietly disclaiming liability.
        // [stated] "the same comedic cold company narration we have it" - the manual's own register is lines like
        // "The Company does not replace structures lost to preventable enthusiasm."
        tooltip.add(
            Component.literal("Field trials confirm that a queen sickened quickly enough will, in her panic,")
                .withStyle(ChatFormatting.GRAY)
        );
        tooltip.add(
            Component.literal("found a hive precisely where she stands.")
                .withStyle(ChatFormatting.GRAY)
        );
        tooltip.add(
            Component.literal("This bypasses every safeguard she would otherwise observe.")
                .withStyle(ChatFormatting.GRAY)
        );
        tooltip.add(
            Component.literal("The Company considers the resulting hive the operator's to enjoy.")
                .withStyle(ChatFormatting.DARK_GRAY)
        );
    }
}
