package com.alien.common.network.handler;

import com.alien.Alien;
import com.alien.common.gameplay.hive.render.HiveRenderDataBuilder;
import com.alien.common.network.payload.C2SToggleHiveRenderPayload;
import com.alien.common.network.payload.S2CHiveRenderDataPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side coordinator for the hive render overlay ({@code /avp_alien debug hive render}).
 * <ul>
 * <li>{@link #handle} processes the client's {@link C2SToggleHiveRenderPayload}: op-gated, records the player as
 * wanting (or not wanting) the overlay, and sends an immediate data push on enable.</li>
 * <li>{@link #tick} re-pushes render data to every opted-in player on a slow cadence so the overlay stays current as
 * hives grow / the player moves. Call once per server tick from the hive registry tick.</li>
 * </ul>
 * <p>
 * Opt-in state is in-memory only (debug overlay, not persisted). A player who logs out is pruned on their next absence
 * from the player list.
 */
public final class HiveRenderToggleHandler {

    /** How often (ticks) to re-push render data to opted-in players. Slow on purpose — hive shape changes slowly. */
    private static final int PUSH_INTERVAL_TICKS = 20;

    private static final Set<UUID> ENABLED_PLAYERS = ConcurrentHashMap.newKeySet();

    private static int tickCounter;

    private HiveRenderToggleHandler() {}

    public static void handle(C2SToggleHiveRenderPayload payload, Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        if (!sp.hasPermissions(2)) {
            return;
        }

        if (payload.enabled()) {
            ENABLED_PLAYERS.add(sp.getUUID());
            push(sp);
        } else {
            ENABLED_PLAYERS.remove(sp.getUUID());
            clear(sp);
        }
    }

    public static void tick(MinecraftServer server) {
        if (ENABLED_PLAYERS.isEmpty()) {
            return;
        }
        if (++tickCounter < PUSH_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        for (var sp : server.getPlayerList().getPlayers()) {
            if (ENABLED_PLAYERS.contains(sp.getUUID())) {
                push(sp);
            }
        }
    }

    /** Whether the given player currently has the overlay enabled (server-authoritative). */
    public static boolean isEnabled(UUID playerId) {
        return ENABLED_PLAYERS.contains(playerId);
    }

    /**
     * Enable/disable the overlay for a player directly (used by the {@code render} command, which already runs
     * server-side so it needs no C2S packet). On enable, pushes data immediately.
     */
    public static void setEnabled(ServerPlayer player, boolean enabled) {
        if (enabled) {
            ENABLED_PLAYERS.add(player.getUUID());
            push(player);
        } else {
            ENABLED_PLAYERS.remove(player.getUUID());
            clear(player);
        }
    }

    private static void push(ServerPlayer player) {
        var data = HiveRenderDataBuilder.build(player);
        Alien.MOD.networking().sendToClient(player, new S2CHiveRenderDataPayload(data));
    }

    private static void clear(ServerPlayer player) {
        Alien.MOD.networking().sendToClient(player, new S2CHiveRenderDataPayload(HiveRenderDataBuilder.empty()));
    }
}
