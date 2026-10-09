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
    SURFACE,

    /**
     * \u2b50\u2b50 A BEACHHEAD. Placed by an INVADING hive on ground it bought inside a rival's territory, so its
     * attack parties have a door to come through.
     * <p>
     * [stated] "the vents that prexisted the claim are owned by the native hive. and vents placed by the invader are
     * tied to the other hive. So there should be a clear distinction, maybe a 4th register."
     * </p>
     * <p>
     * \u26a0\u26a0 IT IS A PARTY DOOR BUT NOT A DEFENDER EMERGENCE. A defender stepping out of one arrives deep inside
     * hostile ground, alone, far from the hive it was summoned to protect - the same reason FRONTIER is excluded. Get
     * this wrong and an invader's assault vent starts disgorging its own DEFENDERS every time an intruder wanders near
     * the hive it was built to attack.
     * </p>
     * <p>
     * \u26a0 NEVER INFERRED. This is a placement-time fact - "an invader put this here" - and cannot be read off a
     * position afterwards, so {@code classifyUntagged} must never return it: a vent that loses its tag falls back to
     * the positional rules instead.
     * </p>
     */
    INVADER;

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
        return this == SURFACE || this == FRONTIER || this == INVADER;
    }

    /** Vents a defender may safely emerge from - inside the hive, or at its front door. */
    public boolean isDefenderEmergence() {
        return this == STRUCTURE || this == SURFACE;
    }
}
