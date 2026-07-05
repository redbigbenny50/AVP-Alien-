package com.alien.common.gameplay.item;

import com.alien.Alien;
import com.alien.common.network.payload.C2SRequestTrackedQueensPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * The tracking PDA — right-click to pull the current tracked-queen list from the server. For now the readout prints to
 * chat; an on-screen list is the follow-up slice. Reusable (never consumed).
 */
public class TrackingPdaItem extends Item {

    public TrackingPdaItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(
        @NotNull Level level,
        @NotNull Player player,
        @NotNull InteractionHand hand
    ) {
        if (level.isClientSide) {
            com.alien.client.gui.ClientTrackerAlerts.requestOpen();
            Alien.MOD.networking().sendToServer(C2SRequestTrackedQueensPayload.INSTANCE);
        }

        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
}
