package com.alien.common.network.handler;

import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.level.saveddata.TrackedQueenRegistry;
import com.alien.common.network.payload.C2SDestroyTrackerPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Server-side handler for {@link C2SDestroyTrackerPayload}. Clears the target queen's tracker tag and removes her from
 * the tracked-queen registry. If she is currently loaded, her tag is cleared immediately; if not, the removal is queued
 * on the registry so her tag is cleared the next time she ticks (otherwise she would re-register herself on reload).
 * This is a deliberate, clean removal and does not raise a "tracker lost" warning.
 */
public final class DestroyTrackerHandler {

    private DestroyTrackerHandler() {}

    public static void handle(C2SDestroyTrackerPayload payload, Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }

        UUID queenId = payload.queenId();

        // Clear the tag on the live entity if she's loaded anywhere on the server.
        for (var level : sp.server.getAllLevels()) {
            Entity entity = level.getEntity(queenId);
            if (entity instanceof Queen queen) {
                queen.setTracked(false);
                break;
            }
        }

        // Drop her from the registry, and queue the tag-clear in case she's unloaded (cleared on her next tick).
        TrackedQueenRegistry.getOrCreate(sp.server).ifSome(registry -> {
            registry.untrack(queenId);
            registry.markPendingDestroy(queenId);
        });
    }
}
