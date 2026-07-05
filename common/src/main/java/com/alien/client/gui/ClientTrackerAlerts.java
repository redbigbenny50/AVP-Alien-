package com.alien.client.gui;

import com.alien.common.gameplay.level.saveddata.TrackedQueenRow;

import java.util.List;

/**
 * Client-side cache of trackers that have gone dark, refreshed each time the server replies to a PDA request. The PDA
 * reads this to decide whether to show the hazard button and to populate the "lost communications" drawer. Separate
 * from the screen so the reply handler can stash it without changing the screen's constructor signature.
 */
public final class ClientTrackerAlerts {

    private static List<TrackedQueenRow.Lost> lost = List.of();

    private static boolean pendingOpen;

    private ClientTrackerAlerts() {}

    public static void set(List<TrackedQueenRow.Lost> value) {
        lost = value == null ? List.of() : List.copyOf(value);
    }

    public static List<TrackedQueenRow.Lost> lost() {
        return lost;
    }

    public static boolean hasLost() {
        return !lost.isEmpty();
    }

    public static void clear() {
        lost = List.of();
    }

    /** Marked when the player uses a PDA item, so only that gesture opens the screen (not background refreshes). */
    public static void requestOpen() {
        pendingOpen = true;
    }

    /** Consume the open request; returns true once per {@link #requestOpen()}. */
    public static boolean consumeOpen() {
        boolean p = pendingOpen;
        pendingOpen = false;
        return p;
    }
}
