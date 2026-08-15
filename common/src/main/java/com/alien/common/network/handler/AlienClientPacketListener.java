package com.alien.common.network.handler;

import com.alien.client.gui.ClientTrackerAlerts;
import com.alien.client.gui.TrackingPdaScreen;
import com.alien.client.render.CaptureHoldClientState;
import com.alien.client.render.entity.head.HeadAttachmentClientCache;
import com.alien.client.render.hive.ClientHiveRenderCache;
import com.alien.common.gameplay.level.saveddata.TrackedQueenRow;
import com.alien.common.network.payload.S2CCaptureHoldPayload;
import com.alien.common.network.payload.S2CHeadAttachmentDataPayload;
import com.alien.common.network.payload.S2CHiveInspectionPayload;
import com.alien.common.network.payload.S2CHiveRenderDataPayload;
import com.alien.common.network.payload.S2CTrackedQueensPayload;
import com.alien.common.registry.HeadAttachmentRegistry;
import com.alien.compatibility.blib_engine.client.inspector.ClientHiveInspectionCache;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Client-side packet handlers for AVP-Alien. Mirrors the {@code BLibClientListener} pattern: handler methods are
 * invoked off the network thread (BLib's network registry takes care of scheduling onto the main client thread before
 * calling these), so handlers can touch client-only state directly.
 */
public final class AlienClientPacketListener {

    private AlienClientPacketListener() {}

    /** Server-pushed hive inspection snapshot for the currently-selected AVP faction. */
    public static void handleHiveInspection(S2CHiveInspectionPayload payload, Player player) {
        ClientHiveInspectionCache.apply(payload);
    }

    /** Server-pushed capture-chain grab/release: mirror it into the client hold map for rendering. */
    public static void handleCaptureHold(S2CCaptureHoldPayload payload, Player player) {
        if (payload.holderId() < 0) {
            CaptureHoldClientState.remove(payload.mobId());
        } else {
            CaptureHoldClientState.put(payload.mobId(), payload.holderId());
        }
    }

    /** Server-pushed head-attachment profiles (join or datapack reload): rebake the client render cache. */
    public static void handleHeadAttachmentData(S2CHeadAttachmentDataPayload payload, Player player) {
        HeadAttachmentClientCache.replaceAll(HeadAttachmentRegistry.decodeAll(payload.data()));
    }

    /** Server-pushed hive render data for the debug wireframe overlay. */
    public static void handleHiveRenderData(S2CHiveRenderDataPayload payload, Player player) {
        ClientHiveRenderCache.apply(payload);
    }

    /** Server-pushed tracked-queen list for the PDA: open the readout screen. */
    public static void handleTrackedQueens(S2CTrackedQueensPayload payload, Player player) {
        var rows = TrackedQueenRow.unpackList(payload.data());
        ClientTrackerAlerts.set(TrackedQueenRow.unpackLost(payload.data()));
        if (Minecraft.getInstance().screen instanceof TrackingPdaScreen pda) {
            pda.update(rows);
        } else if (ClientTrackerAlerts.consumeOpen()) {
            TrackingPdaScreen.open(rows);
        }
        // Otherwise this is a background refresh reply that arrived while no PDA is open (e.g. just after ESC); ignore
        // it.
    }
}
