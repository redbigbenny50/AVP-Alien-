package com.alien.client.render;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of {@code CaptureHoldManager}: which mobs are currently held, and by whom. Populated from
 * {@code S2CCaptureHoldPayload} (grab/release) and read by the draw-only render inject to draw the capture chain.
 * <p>
 * Keyed by network entity id rather than UUID because that is what the payload ships and what the client level can
 * resolve cheaply. Stale entries are self-healing: a hold that ends sends a release, and {@link #holderOf} only returns
 * a holder that still resolves to a {@link Player}, so a recycled id can never draw a chain to the wrong entity.
 */
public final class CaptureHoldClientState {

    /** mob network id -> holder network id. */
    private static final Map<Integer, Integer> HELD = new ConcurrentHashMap<>();

    private CaptureHoldClientState() {}

    public static void put(int mobId, int holderId) {
        HELD.put(mobId, holderId);
    }

    public static void remove(int mobId) {
        HELD.remove(mobId);
    }

    public static void clear() {
        HELD.clear();
    }

    /** The player holding {@code mob} right now, resolved in the mob's (client) level, or {@code null} if none. */
    @Nullable
    public static Entity holderOf(Entity mob) {
        Integer holderId = HELD.get(mob.getId());
        if (holderId == null) {
            return null;
        }
        Entity holder = mob.level().getEntity(holderId);
        return holder instanceof LivingEntity ? holder : null;
    }
}
