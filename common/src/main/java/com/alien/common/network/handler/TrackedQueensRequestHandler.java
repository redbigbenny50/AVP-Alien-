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
        var lost = new ArrayList<TrackedQueenRow.Lost>();

        TrackedQueenRegistry.getOrCreate(sp.server).ifSome(registry -> {
            for (var entry : registry.entries().entrySet()) {
                var id = entry.getKey();
                var e = entry.getValue();
                var pos = e.pos();
                var dim = e.dimension();
                // Use the queen's live position/dimension if she is loaded, so the PDA can update in real time; fall
                // back to her last-seen registry snapshot when she is unloaded (and therefore not moving).
                for (var lvl : sp.server.getAllLevels()) {
                    var ent = lvl.getEntity(id);
                    if (ent != null) {
                        pos = ent.blockPosition();
                        dim = lvl.dimension();
                        break;
                    }
                }
                rows.add(new TrackedQueenRow(id, e.name(), dim, pos, e.lastSeenGameTime()));
            }
            for (var entry : registry.lost().entrySet()) {
                var l = entry.getValue();
                lost.add(new TrackedQueenRow.Lost(entry.getKey(), l.name(), l.dimension(), l.pos(), l.reason(), l.lostGameTime()));
            }
        });

        var data = TrackedQueenRow.packList(rows);
        TrackedQueenRow.packLostInto(data, lost);
        Alien.MOD.networking().sendToClient(sp, new S2CTrackedQueensPayload(data));
    }
}
