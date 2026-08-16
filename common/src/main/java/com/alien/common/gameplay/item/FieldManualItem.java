package com.alien.common.gameplay.item;

import com.alien.client.AlienClientHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Opens the field manual. Ported from the dropship_transport module's equivalent.
 * <p>
 * The screen is reached through {@link AlienClientHooks} rather than imported, so this class stays safe to load on a
 * dedicated server.
 */
public class FieldManualItem extends Item {

    public FieldManualItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(
        @NotNull Level level,
        @NotNull Player player,
        @NotNull InteractionHand hand
    ) {
        var stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            AlienClientHooks.openFieldManualScreen();
            return InteractionResultHolder.success(stack);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
