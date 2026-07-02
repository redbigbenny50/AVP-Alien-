package com.alien.common.network.handler;

import com.alien.Alien;
import com.alien.common.gameplay.level.saveddata.TrackedQueenRegistry;
import com.alien.common.gameplay.level.saveddata.TrackedQueenRow;
import com.alien.common.network.payload.C2SRequestTrackedQueensPayload;
import com.alien.common.network.payload.S2CTrackedQueensPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;

/**
 * Server-side handler for {@link C2SRequestTrackedQueensPayload}. Snapshots the tracked-queen registry into a row list
 * and replies to the requesting player with {@link S2CTrackedQueensPayload}. No permission gate — the PDA is a normal
 * survival item, and it only ever reveals queens the player has already tagged.
 */
public final class TrackedQueensRequestHandler {

    private TrackedQueensRequestHandler() {}

    public static void handle(C2SRequestTrackedQueensPayload payload, Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }

        var rows = new ArrayList<TrackedQueenRow>();

        TrackedQueenRegistry.getOrCreate(sp.server).ifSome(registry -> {
            for (var entry : registry.entries().entrySet()) {
                var e = entry.getValue();
                rows.add(new TrackedQueenRow(entry.getKey(), e.name(), e.dimension(), e.pos(), e.lastSeenGameTime()));
            }
        });

        Alien.MOD.networking().sendToClient(sp, new S2CTrackedQueensPayload(TrackedQueenRow.packList(rows)));
    }
}
