package com.alien.common.network;

import com.alien.Alien;
import com.alien.common.network.handler.AckLostTrackersHandler;
import com.alien.common.network.handler.AlienClientPacketListener;
import com.alien.common.network.handler.DestroyTrackerHandler;
import com.alien.common.network.handler.HiveConfigUpdateHandler;
import com.alien.common.network.handler.HiveInspectionRequestHandler;
import com.alien.common.network.handler.HiveRenderToggleHandler;
import com.alien.common.network.handler.RenameTrackerHandler;
import com.alien.common.network.handler.ShieldAbilityActivationHandler;
import com.alien.common.network.handler.TrackedQueensRequestHandler;
import com.alien.common.network.payload.C2SAckLostTrackersPayload;
import com.alien.common.network.payload.C2SActivateShieldAbilityPayload;
import com.alien.common.network.payload.C2SDestroyTrackerPayload;
import com.alien.common.network.payload.C2SRenameTrackerPayload;
import com.alien.common.network.payload.C2SRequestHiveInspectionPayload;
import com.alien.common.network.payload.C2SRequestTrackedQueensPayload;
import com.alien.common.network.payload.C2SToggleHiveRenderPayload;
import com.alien.common.network.payload.C2SUpdateHiveConfigPayload;
import com.alien.common.network.payload.S2CCaptureHoldPayload;
import com.alien.common.network.payload.S2CHiveInspectionPayload;
import com.alien.common.network.payload.S2CHiveRenderDataPayload;
import com.alien.common.network.payload.S2CTrackedQueensPayload;
import com.blib.api.common.network.v1.NetworkHandler;
import com.blib.api.common.network.v1.PacketDirection;

/**
 * Single registration entry point for AVP-Alien's custom payloads. Called once from {@code Alien#runInitialization}.
 * Mirrors BLib's split of "directions" vs "handlers" — directions tell the loader where each packet may flow, handlers
 * wire the actual processing — but rolls both into one call site since AVP-Alien's surface is small.
 */
public final class AlienNetworking {

    private AlienNetworking() {}

    public static void initialize() {
        var registry = Alien.MOD.registries().createNetworkRegistry();

        registry.registerPacketDirection(
            new PacketDirection.C2S<>(C2SRequestHiveInspectionPayload.TYPE, C2SRequestHiveInspectionPayload.CODEC)
        );
        registry.registerPacketDirection(
            new PacketDirection.C2S<>(C2SUpdateHiveConfigPayload.TYPE, C2SUpdateHiveConfigPayload.CODEC)
        );
        registry.registerPacketDirection(
            new PacketDirection.C2S<>(C2SActivateShieldAbilityPayload.TYPE, C2SActivateShieldAbilityPayload.CODEC)
        );
        registry.registerPacketDirection(
            new PacketDirection.S2C<>(S2CHiveInspectionPayload.TYPE, S2CHiveInspectionPayload.CODEC)
        );
        registry.registerPacketDirection(
            new PacketDirection.C2S<>(C2SToggleHiveRenderPayload.TYPE, C2SToggleHiveRenderPayload.CODEC)
        );
        registry.registerPacketDirection(
            new PacketDirection.S2C<>(S2CHiveRenderDataPayload.TYPE, S2CHiveRenderDataPayload.CODEC)
        );
        registry.registerPacketDirection(
            new PacketDirection.S2C<>(S2CCaptureHoldPayload.TYPE, S2CCaptureHoldPayload.CODEC)
        );
        registry.registerPacketDirection(
            new PacketDirection.C2S<>(C2SRequestTrackedQueensPayload.TYPE, C2SRequestTrackedQueensPayload.CODEC)
        );
        registry.registerPacketDirection(
            new PacketDirection.S2C<>(S2CTrackedQueensPayload.TYPE, S2CTrackedQueensPayload.CODEC)
        );
        registry.registerPacketDirection(
            new PacketDirection.C2S<>(C2SDestroyTrackerPayload.TYPE, C2SDestroyTrackerPayload.CODEC)
        );
        registry.registerPacketDirection(
            new PacketDirection.C2S<>(C2SAckLostTrackersPayload.TYPE, C2SAckLostTrackersPayload.CODEC)
        );
        registry.registerPacketDirection(
            new PacketDirection.C2S<>(C2SRenameTrackerPayload.TYPE, C2SRenameTrackerPayload.CODEC)
        );

        registry.registerPacketHandler(
            new NetworkHandler.FromClient<>(
                C2SRequestHiveInspectionPayload.TYPE,
                C2SRequestHiveInspectionPayload.CODEC,
                HiveInspectionRequestHandler::handle
            )
        );
        registry.registerPacketHandler(
            new NetworkHandler.FromClient<>(
                C2SUpdateHiveConfigPayload.TYPE,
                C2SUpdateHiveConfigPayload.CODEC,
                HiveConfigUpdateHandler::handle
            )
        );
        registry.registerPacketHandler(
            new NetworkHandler.FromClient<>(
                C2SActivateShieldAbilityPayload.TYPE,
                C2SActivateShieldAbilityPayload.CODEC,
                ShieldAbilityActivationHandler::handle
            )
        );
        registry.registerPacketHandler(
            new NetworkHandler.FromServer<>(
                S2CHiveInspectionPayload.TYPE,
                S2CHiveInspectionPayload.CODEC,
                AlienClientPacketListener::handleHiveInspection
            )
        );
        registry.registerPacketHandler(
            new NetworkHandler.FromClient<>(
                C2SToggleHiveRenderPayload.TYPE,
                C2SToggleHiveRenderPayload.CODEC,
                HiveRenderToggleHandler::handle
            )
        );
        registry.registerPacketHandler(
            new NetworkHandler.FromServer<>(
                S2CHiveRenderDataPayload.TYPE,
                S2CHiveRenderDataPayload.CODEC,
                AlienClientPacketListener::handleHiveRenderData
            )
        );
        registry.registerPacketHandler(
            new NetworkHandler.FromServer<>(
                S2CCaptureHoldPayload.TYPE,
                S2CCaptureHoldPayload.CODEC,
                AlienClientPacketListener::handleCaptureHold
            )
        );
        registry.registerPacketHandler(
            new NetworkHandler.FromClient<>(
                C2SRequestTrackedQueensPayload.TYPE,
                C2SRequestTrackedQueensPayload.CODEC,
                TrackedQueensRequestHandler::handle
            )
        );
        registry.registerPacketHandler(
            new NetworkHandler.FromServer<>(
                S2CTrackedQueensPayload.TYPE,
                S2CTrackedQueensPayload.CODEC,
                AlienClientPacketListener::handleTrackedQueens
            )
        );
        registry.registerPacketHandler(
            new NetworkHandler.FromClient<>(
                C2SDestroyTrackerPayload.TYPE,
                C2SDestroyTrackerPayload.CODEC,
                DestroyTrackerHandler::handle
            )
        );
        registry.registerPacketHandler(
            new NetworkHandler.FromClient<>(
                C2SAckLostTrackersPayload.TYPE,
                C2SAckLostTrackersPayload.CODEC,
                AckLostTrackersHandler::handle
            )
        );
        registry.registerPacketHandler(
            new NetworkHandler.FromClient<>(
                C2SRenameTrackerPayload.TYPE,
                C2SRenameTrackerPayload.CODEC,
                RenameTrackerHandler::handle
            )
        );
    }
}
