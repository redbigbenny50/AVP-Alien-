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

    /**
     * The server connection the current pins belong to.
     * <p>
     * ⚠⚠ THE PINS USED TO OUTLIVE THE WORLD. This store is a static map on the client, and nothing ever called
     * {@link #clear()} - so a queen pinned in one world kept her beacon in every world opened afterwards in the same
     * game session. The PDA in the new world could not unpin her, because she was not in its list. Field report Sep 23:
     * "deleted the world and made a new one and every world they made had the trackers beacon."
     * </p>
     * <p>
     * ⭐ Keyed to the connection rather than a logout event so it works identically on both loaders with no event
     * wiring: joining any world or server builds a new {@code ClientPacketListener}, so the first read after a join
     * sees a different owner and drops the old pins. Changing dimension keeps the same connection, so pins survive
     * that, which is right - each pin already carries its own dimension.
     * </p>
     */
    private static Object owner;

    private ClientWaypointStore() {}

    /** Drops every pin (and the lost-queen alert list) the moment they belong to a connection that is gone. */
    private static void validateOwner() {
        var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
        if (connection != owner) {
            PINNED.clear();
            com.alien.client.gui.ClientTrackerAlerts.clear();
            owner = connection;
        }
    }

    public static boolean isPinned(UUID id) {
        validateOwner();
        return id != null && PINNED.containsKey(id);
    }

    public static void pin(Waypoint waypoint) {
        validateOwner();
        if (waypoint != null && waypoint.id() != null) {
            PINNED.put(waypoint.id(), waypoint);
        }
    }

    public static void unpin(UUID id) {
        validateOwner();
        if (id != null) {
            PINNED.remove(id);
        }
    }

    /** Snapshot of all current pins, safe to iterate while the store mutates. */
    public static Collection<Waypoint> all() {
        validateOwner();
        return List.copyOf(PINNED.values());
    }

    public static void clear() {
        PINNED.clear();
    }
}
