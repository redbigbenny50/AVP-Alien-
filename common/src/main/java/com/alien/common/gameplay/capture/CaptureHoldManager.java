package com.alien.common.gameplay.capture;

import com.alien.Alien;
import com.alien.common.network.payload.S2CCaptureHoldPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side registry of mobs currently held by a player via the capture chain.
 * <p>
 * This is the clean replacement for vanilla leashing during the player-held window. Instead of {@code
 * setLeashedTo} — which draws a vanilla rope and hard-spawns a {@code minecraft:lead} when it snaps — the chain
 * registers the hold here. A per-tick tether gently pulls the held mob toward its holder and breaks the hold past a
 * hard distance. Binding the mob to an anchor hands ownership to the anchor and clears the hold.
 * <p>
 * State is transient and server-authoritative: it is never persisted, so a server restart (or the holder/mob leaving)
 * simply frees the mob. Every grab and release is mirrored to nearby clients via {@link S2CCaptureHoldPayload} so the
 * chain can be drawn client-side; the manager itself never touches render state.
 */
public final class CaptureHoldManager {

    /** Beyond this distance from the holder the mob starts being reeled in. */
    private static final double FOLLOW_DISTANCE = 6.0;

    /** Hard ceiling: past this the hold snaps and the mob is freed. A little longer than a vanilla lead. */
    private static final double BREAK_DISTANCE = 16.0;

    /** Per-tick pull acceleration cap, in blocks/tick added to the mob's velocity. */
    private static final double MAX_PULL = 0.35;

    /** Players within this distance of a held mob receive its grab/release packets. */
    private static final double SYNC_RANGE_SQR = 128.0 * 128.0;

    /** Sentinel holder id meaning "the hold ended". */
    private static final int RELEASE = -1;

    /** How often (in ticks) each active hold is re-announced, so players who came into range mid-hold see the chain. */
    private static final int RESYNC_INTERVAL = 20;

    /** mob UUID -> holding player UUID. */
    private static final Map<UUID, UUID> HELD = new HashMap<>();

    /** Rolling tick counter that gates the periodic re-announce. */
    private static int resyncTick;

    private CaptureHoldManager() {}

    /** Register {@code mob} as held by {@code player}, replacing any prior holder. */
    public static void hold(Mob mob, Player player) {
        HELD.put(mob.getUUID(), player.getUUID());
        broadcast(mob, player.getId());
    }

    /** Release {@code mob} from whoever holds it. No-op if it was not held. */
    public static void release(Mob mob) {
        if (HELD.remove(mob.getUUID()) != null) {
            broadcast(mob, RELEASE);
        }
    }

    /** Whether {@code mob} is currently held by any player. */
    public static boolean isHeld(Mob mob) {
        return HELD.containsKey(mob.getUUID());
    }

    /** Whether {@code mob} is currently held specifically by {@code player}. */
    public static boolean isHeldBy(Mob mob, Player player) {
        return player.getUUID().equals(HELD.get(mob.getUUID()));
    }

    /**
     * The mob held by {@code player} in {@code level} that is nearest to them, or {@code null} if the player is not
     * holding anything resolvable in that level. Used when handing a held mob off to an anchor.
     */
    @Nullable
    public static Mob heldBy(ServerLevel level, Player player) {
        UUID holder = player.getUUID();
        Mob nearest = null;
        double best = Double.MAX_VALUE;
        for (Map.Entry<UUID, UUID> entry : HELD.entrySet()) {
            if (!holder.equals(entry.getValue())) {
                continue;
            }
            if (level.getEntity(entry.getKey()) instanceof Mob mob && mob.isAlive()) {
                double d = mob.distanceToSqr(player);
                if (d < best) {
                    best = d;
                    nearest = mob;
                }
            }
        }
        return nearest;
    }

    /** Drive the tether for every held mob; prune stale, dead, cross-dimension, or out-of-range entries. */
    public static void tick(MinecraftServer server) {
        if (HELD.isEmpty()) {
            return;
        }
        boolean resync = (++resyncTick % RESYNC_INTERVAL) == 0;
        Iterator<Map.Entry<UUID, UUID>> it = HELD.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> entry = it.next();
            ServerPlayer holder = server.getPlayerList().getPlayer(entry.getValue());
            Entity mobEntity = resolve(server, entry.getKey());
            Mob mob = mobEntity instanceof Mob m ? m : null;

            if (
                    holder == null
                            || !holder.isAlive()
                            || mob == null
                            || !mob.isAlive()
                            || mob.level() != holder.level()
            ) {
                it.remove();
                if (mob != null && mob.isAlive()) {
                    broadcast(mob, RELEASE);
                }
                continue;
            }

            double dist = mob.distanceTo(holder);
            if (dist > BREAK_DISTANCE) {
                it.remove();
                broadcast(mob, RELEASE);
                continue;
            }
            // Periodic re-announce so a player who entered range mid-hold picks up the chain. Idempotent for clients
            // that already have it (the client map just overwrites the same entry).
            if (resync) {
                broadcast(mob, holder.getId());
            }
            if (dist > FOLLOW_DISTANCE) {
                Vec3 pull = holder.position().subtract(mob.position());
                double len = pull.length();
                if (len > 1.0e-4) {
                    double strength = Math.min((dist - FOLLOW_DISTANCE) * 0.08, MAX_PULL);
                    mob.setDeltaMovement(mob.getDeltaMovement().add(pull.scale(strength / len)));
                    mob.hasImpulse = true;
                }
            }
        }
    }

    /** Tell every player near {@code mob} about a grab ({@code holderId} = the holder) or release ({@code -1}). */
    private static void broadcast(Mob mob, int holderId) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        S2CCaptureHoldPayload payload = new S2CCaptureHoldPayload(mob.getId(), holderId);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(mob) <= SYNC_RANGE_SQR) {
                Alien.MOD.networking().sendToClient(player, payload);
            }
        }
    }

    @Nullable
    private static Entity resolve(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e != null) {
                return e;
            }
        }
        return null;
    }
}