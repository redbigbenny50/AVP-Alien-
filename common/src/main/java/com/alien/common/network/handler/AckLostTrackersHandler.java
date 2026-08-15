package com.alien.common.network.handler;

import com.alien.common.gameplay.level.saveddata.TrackedQueenRegistry;
import com.alien.common.network.payload.C2SAckLostTrackersPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Server-side handler for {@link C2SAckLostTrackersPayload}. Clears the shared "lost trackers" list once the player has
 * reviewed the hazard drawer, so the warning does not keep re-appearing.
 */
public final class AckLostTrackersHandler {

    private AckLostTrackersHandler() {}

    public static void handle(C2SAckLostTrackersPayload payload, Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        TrackedQueenRegistry.getOrCreate(sp.server).ifSome(TrackedQueenRegistry::clearLost);
    }
}
