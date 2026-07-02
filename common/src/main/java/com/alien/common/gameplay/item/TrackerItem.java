package com.alien.common.gameplay.item;

import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.level.saveddata.TrackedQueenRegistry;
import com.alien.common.model.alien.variant.AlienVariant;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * The tracker tag — right-click a queen to clamp a tracking device to her, revealing the {@code gTracker} bone and
 * flagging her as tracked. A handheld tracking PDA can then list every tagged queen and point you to her (the PDA and
 * its readout are their own slice). Unlike the inhibitor, tracking has no helpless-state gate — any queen can be
 * tagged, though tagging an active queen up close is its own risk.
 */
public class TrackerItem extends Item {

    public TrackerItem(Properties properties) {
        super(properties);
    }

    /** Readable label stored with the tracked queen, e.g. "Aberrant Queen"; plain "Queen" for the normal strain. */
    private static String queenLabel(Queen queen) {
        var variant = queen.getVariant();
        if (variant == AlienVariant.NORMAL) {
            return "Queen";
        }
        var name = variant.name();
        return name.charAt(0) + name.substring(1).toLowerCase() + " Queen";
    }

    @Override
    public @NotNull InteractionResult interactLivingEntity(
        @NotNull ItemStack stack,
        @NotNull Player player,
        @NotNull LivingEntity target,
        @NotNull InteractionHand hand
    ) {
        if (target instanceof Queen queen && !queen.isTracked()) {
            if (!player.level().isClientSide) {
                queen.setTracked(true);
                TrackedQueenRegistry.getOrCreate(queen.level())
                    .ifSome(
                        registry -> registry.track(
                            queen.getUUID(),
                            queen.blockPosition(),
                            queen.level().dimension(),
                            queenLabel(queen),
                            queen.level().getGameTime()
                        )
                    );
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        return super.interactLivingEntity(stack, player, target, hand);
    }
}
