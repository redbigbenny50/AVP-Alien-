package com.alien.common.gameplay.capture;

import com.alien.Alien;
import com.alien.common.network.payload.S2CCaptureHoldPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side registry of mob-to-mob capture chains: two mobs tied directly to each other rather than to an anchor. A
 * per-tick mutual tether keeps them within {@link #CHAIN_LENGTH} of one another and the chain breaks past a hard
 * distance or if either mob dies/unloads. Transient and server-authoritative (never persisted), mirroring
 * {@link CaptureHoldManager}; a restart or leaving the pair's chunks simply frees them.
 * <p>
 * Rendering reuses the held-chain path: each active pair is announced with {@link S2CCaptureHoldPayload} in a single
 * canonical direction (so exactly one ribbon is drawn between the two mobs), and the client draws it via the same
 * entity-to-entity render used for player holds.
 */
public final class MobChainManager {

    /** Soft length: past this the mutual pull kicks in. */
    private static final double CHAIN_LENGTH = 8.0;

    /** Hard ceiling: past this the chain snaps and both mobs are freed (no drop -- see the item for the drop rule). */
    private static final double BREAK_DISTANCE = 24.0;

    private static final double MAX_PULL = 0.35;

    private static final double SYNC_RANGE_SQR = 128.0 * 128.0;

    private static final int RELEASE = -1;

    private static final int RESYNC_INTERVAL = 20;

    /** Bidirectional: a -> b and b -> a, so partner/isLinked are O(1). */
    private static final Map<UUID, UUID> PARTNER = new HashMap<>();

    private static int resyncTick;

    private MobChainManager() {}

    public static boolean isLinked(Mob mob) {
        return PARTNER.containsKey(mob.getUUID());
    }

    /** Chain two mobs together. Keeps both loaded so the tether can run. */
    public static void link(Mob a, Mob b) {
        PARTNER.put(a.getUUID(), b.getUUID());
        PARTNER.put(b.getUUID(), a.getUUID());
        a.setPersistenceRequired();
        b.setPersistenceRequired();
        broadcastPair(a, b);
    }

    /**
     * Break the chain containing {@code mob}; returns the former partner's UUID, or {@code null} if it was not chained.
     */
    @Nullable
    public static UUID unlink(Mob mob) {
        UUID partnerId = PARTNER.remove(mob.getUUID());
        if (partnerId == null) {
            return null;
        }
        PARTNER.remove(partnerId);
        broadcast(mob, RELEASE);
        if (mob.level() instanceof ServerLevel level && level.getEntity(partnerId) instanceof Mob partner) {
            broadcast(partner, RELEASE);
        }
        return partnerId;
    }

    /** Drive the mutual tether for every chained pair; break pairs whose mobs are gone, dead, or too far apart. */
    public static void tick(MinecraftServer server) {
        if (PARTNER.isEmpty()) {
            return;
        }
        boolean resync = (++resyncTick % RESYNC_INTERVAL) == 0;
        Set<UUID> processed = new HashSet<>();
        Set<UUID> toRemove = new HashSet<>();

        for (Map.Entry<UUID, UUID> entry : new HashMap<>(PARTNER).entrySet()) {
            UUID aId = entry.getKey();
            UUID bId = entry.getValue();
            if (!processed.add(aId)) {
                continue;
            }
            processed.add(bId);

            Entity ea = resolve(server, aId);
            Entity eb = resolve(server, bId);
            Mob a = ea instanceof Mob m ? m : null;
            Mob b = eb instanceof Mob m ? m : null;

            if (a == null || b == null || !a.isAlive() || !b.isAlive() || a.level() != b.level()) {
                toRemove.add(aId);
                toRemove.add(bId);
                if (a != null) {
                    broadcast(a, RELEASE);
                }
                if (b != null) {
                    broadcast(b, RELEASE);
                }
                continue;
            }

            double dist = a.distanceTo(b);
            if (dist > BREAK_DISTANCE) {
                toRemove.add(aId);
                toRemove.add(bId);
                broadcast(a, RELEASE);
                broadcast(b, RELEASE);
                continue;
            }

            if (resync) {
                broadcastPair(a, b);
            }

            if (dist > CHAIN_LENGTH) {
                Vec3 dir = b.position().subtract(a.position());
                double len = dir.length();
                if (len > 1.0e-4) {
                    Vec3 n = dir.scale(1.0 / len);
                    double strength = Math.min((dist - CHAIN_LENGTH) * 0.08, MAX_PULL);
                    a.setDeltaMovement(a.getDeltaMovement().add(n.scale(strength)));
                    a.hasImpulse = true;
                    b.setDeltaMovement(b.getDeltaMovement().subtract(n.scale(strength)));
                    b.hasImpulse = true;
                }
            }
        }

        for (UUID id : toRemove) {
            PARTNER.remove(id);
        }
    }

    /** Announce a pair in one canonical direction (smaller entity id is the "mob", the other the "holder"). */
    private static void broadcastPair(Mob a, Mob b) {
        Mob mob = a.getId() <= b.getId() ? a : b;
        Mob holder = a.getId() <= b.getId() ? b : a;
        broadcast(mob, holder.getId());
    }

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
