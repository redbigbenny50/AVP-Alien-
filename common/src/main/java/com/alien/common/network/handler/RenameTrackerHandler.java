package com.alien.common.network.handler;

import com.alien.common.gameplay.level.saveddata.TrackedQueenRegistry;
import com.alien.common.network.payload.C2SRenameTrackerPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Server-side handler for {@link C2SRenameTrackerPayload}. Updates the tracked queen's label in the registry. Empty or
 * over-long names are ignored; the change shows up on the next PDA refresh.
 */
public final class RenameTrackerHandler {

    private static final int MAX_NAME_LENGTH = 32;

    private RenameTrackerHandler() {}

    public static void handle(C2SRenameTrackerPayload payload, Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        String name = payload.newName().trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            return;
        }
        TrackedQueenRegistry.getOrCreate(sp.server).ifSome(registry -> registry.rename(payload.queenId(), name));
    }
}
