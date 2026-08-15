package com.alien.common.gameplay.entity.living.alien.xenomorph.queen;

/**
 * The front-end life-cycle phases an adult, never-founded queen passes through before the existing (working) founding
 * system takes over. See {@code AVP_Queen_Lifecycle_Design.pdf}, Part 1.
 * <p>
 * Ordering is {@link #DEVELOPING} → {@link #LOCATION} → {@link #HIBERNATION} → {@link #FOUNDING_HANDOFF}. The first
 * three are driven by {@link QueenLifecyclePhaseManager}; {@link #FOUNDING_HANDOFF} is a terminal, inert state meaning
 * "the front-end is done, the founding/established systems own her now". The enum intentionally stops there rather than
 * modelling founding/reproductive state, which already lives on {@code HiveLocation} — duplicating it here would invite
 * drift.
 * <p>
 * Capture states (incapacitated / inhibited, Part 2) are deliberately <em>not</em> phases. They are independent layers
 * that can apply during any phase, so they get their own flags rather than enum members.
 */
public enum QueenLifecyclePhase {

    /** First ~5 minutes after adulting. No hive behaviour; she can fight, be downed, be captured. Timer only. */
    DEVELOPING,

    /** Picks a target Y from weighted bands and "digs" to a committed anchor. (Stage 2.) */
    LOCATION,

    /** Clears a 6×6×6 pocket at the anchor and sleeps for 3 days, waking to found if undisturbed. (Stage 3.) */
    HIBERNATION,

    /** Terminal, inert: the existing founding/established systems own the queen from here on. */
    FOUNDING_HANDOFF;

    public static QueenLifecyclePhase byNameOrDefault(String name, QueenLifecyclePhase fallback) {
        for (var phase : values()) {
            if (phase.name().equals(name)) {
                return phase;
            }
        }
        return fallback;
    }
}
