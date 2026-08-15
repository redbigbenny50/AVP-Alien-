package com.alien.common.network;

import com.alien.Alien;
import com.alien.common.network.payload.S2CHeadAttachmentDataPayload;
import com.alien.common.registry.HeadAttachmentRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps every client's head-attachment cache in step with the server's datapack state. Two triggers:
 * <ul>
 * <li><b>Player load</b> — the shared {@code onEntityLoad} hook fires for {@link ServerPlayer}s when they enter a level
 * (initial join and every dimension change; the resend on dimension change is a harmless full replace).</li>
 * <li><b>Datapack reload</b> — {@code HeadAttachmentReloadListener} calls {@link #broadcastToAll()} after reparsing, so
 * {@code /reload} propagates without anyone relogging. The server reference for that broadcast is captured from the
 * started/stopped lifecycle events; during initial boot it is still null, which is fine — nobody is connected yet, and
 * each player syncs on load anyway.</li>
 * </ul>
 */
public final class HeadAttachmentSync {

    private static volatile @Nullable MinecraftServer currentServer;

    private HeadAttachmentSync() {}

    public static void initialize() {
        Alien.MOD.events().onServerStarted().register(server -> {
            currentServer = server;
        });
        Alien.MOD.events().onServerStopped().register(server -> {
            currentServer = null;
        });
        Alien.MOD.events().onEntityLoad().register(HeadAttachmentSync::onEntityLoaded);
    }

    private static void onEntityLoaded(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            sendTo(player);
        }
    }

    public static void sendTo(ServerPlayer player) {
        Alien.MOD.networking().sendToClient(player, new S2CHeadAttachmentDataPayload(HeadAttachmentRegistry.encodeAll()));
    }

    public static void broadcastToAll() {
        var server = currentServer;

        if (server == null) {
            return;
        }

        var payload = new S2CHeadAttachmentDataPayload(HeadAttachmentRegistry.encodeAll());

        for (var player : server.getPlayerList().getPlayers()) {
            Alien.MOD.networking().sendToClient(player, payload);
        }
    }
}
