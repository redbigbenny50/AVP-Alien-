package com.alien.client.waypoint;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client-only registry of "pinned" tracked queens. The tracking PDA writes here when the player creates or removes a
 * waypoint; the in-world beacon renderer and external map-mod bridges (Xaero's / JourneyMap) read from here. Purely
 * client-side and non-persistent for now -- a per-session pin list keyed by the queen's UUID.
 */
public final class ClientWaypointStore {

    /** A single pinned waypoint. Carries enough to draw a beam and to hand off to a map mod. */
    public record Waypoint(
        UUID id,
        String name,
        ResourceKey<Level> dimension,
        BlockPos pos
    ) {}

    private static final Map<UUID, Waypoint> PINNED = new LinkedHashMap<>();

    private ClientWaypointStore() {}

    public static boolean isPinned(UUID id) {
        return id != null && PINNED.containsKey(id);
    }

    public static void pin(Waypoint waypoint) {
        if (waypoint != null && waypoint.id() != null) {
            PINNED.put(waypoint.id(), waypoint);
        }
    }

    public static void unpin(UUID id) {
        if (id != null) {
            PINNED.remove(id);
        }
    }

    /** Snapshot of all current pins, safe to iterate while the store mutates. */
    public static Collection<Waypoint> all() {
        return List.copyOf(PINNED.values());
    }

    public static void clear() {
        PINNED.clear();
    }
}
