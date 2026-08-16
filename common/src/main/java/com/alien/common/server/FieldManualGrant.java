package com.alien.common.server;

import com.alien.common.registry.init.item.AlienItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Hands every player a field manual once, on their first join.
 * <p>
 * Ported from the dropship_transport module. The player tag is what makes it once-per-player rather than once-per-join,
 * and it persists with the player, so a relog does not produce a second copy. Dropping the manual when the inventory is
 * full is deliberate: silently swallowing it would leave a player who never receives the guide with no way to tell that
 * anything was meant to happen.
 */
public final class FieldManualGrant {

    private static final String GRANT_TAG = "avp_alien.field_manual_received";

    private FieldManualGrant() {
        throw new UnsupportedOperationException();
    }

    public static void grantIfNeeded(ServerPlayer player) {
        if (player == null || player.getTags().contains(GRANT_TAG)) {
            return;
        }

        var manual = new ItemStack(AlienItems.FIELD_MANUAL.get());

        if (!player.getInventory().add(manual)) {
            player.drop(manual, false);
        }

        player.addTag(GRANT_TAG);
    }
}
