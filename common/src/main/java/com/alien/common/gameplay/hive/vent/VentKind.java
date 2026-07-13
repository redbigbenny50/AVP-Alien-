package com.alien.common.gameplay.hive.vent;

import org.jetbrains.annotations.Nullable;

/**
 * What a vent is FOR. Every vent used to be an anonymous {@code BlockPos}, and "is this a surface vent?" was answered
 * by comparing its Y against the heightmap of its own column - which is how a carrier ended up trying to walk from y=3
 * to a vent at y=-4 that was "near the surface" of the hill it was buried under. A vent's role is now recorded when it
 * is placed, not guessed afterwards.
 */
public enum VentKind {

    /**
     * Comes with the hive structure itself - stamped by the templates, inside the slab. These are the hive's internal
     * duct network: egg haulers shortcut through them, and defenders emerge from them. Parties never use them as a door
     * to the outside world, because they don't lead there.
     */
    STRUCTURE,

    /**
     * Placed by a xenomorph out beyond the structure slab, at cave mouths and openings next to the hive. The hive's
     * outposts onto the cave network - biomass hunters and attack parties launch from these.
     * <p>
     * Defenders deliberately do NOT emerge from frontier vents: they would pop out in a cave, far from the intruder
     * they were summoned to deal with, and strand themselves.
     */
    FRONTIER,

    /**
     * Dropped by a surface party out in the open air. The hive's front door, and the ONLY vent a host-hunt party will
     * use - both to launch from and to carry a captive back to.
     */
    SURFACE;

    /** Parse a persisted name, tolerating anything unrecognised (returns null so the vent is re-classified). */
    public static @Nullable VentKind byName(String name) {
        for (var kind : values()) {
            if (kind.name().equals(name)) {
                return kind;
            }
        }
        return null;
    }

    /** Vents a party may launch from or return to, other than the surface door. */
    public boolean isPartyDoor() {
        return this == SURFACE || this == FRONTIER;
    }

    /** Vents a defender may safely emerge from - inside the hive, or at its front door. */
    public boolean isDefenderEmergence() {
        return this == STRUCTURE || this == SURFACE;
    }
}
