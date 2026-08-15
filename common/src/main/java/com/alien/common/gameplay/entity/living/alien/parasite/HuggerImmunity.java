package com.alien.common.gameplay.entity.living.alien.parasite;

import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Post-escape state for the facehugger struggle: how long a host that has just torn a hugger off its face is left
 * alone, and how much harder the next one is.
 * <p>
 * <b>Immunity.</b> For {@link #IMMUNITY_TICKS} (30s) after a successful escape, huggers will not target, hatch for,
 * lunge at or attach to that host. This is checked from {@code AlienPredicates.isFreeHost}, which is the single choke
 * point for every route a hugger has onto a face - GOAP target selection, an ovomorph's hatch desire, and the
 * attach-on-touch and attach-on-hit paths in {@link Parasite}. Without it a hugger simply re-lunges the instant it
 * lands and the struggle is a treadmill.
 * <p>
 * <b>Escalation.</b> Each successful escape leaves the host more worn down, so the next struggle needs more mashes (see
 * {@code HuggerStruggle}). That counter is not permanent: like a warden's warning level, it <b>decays</b>, dropping one
 * step for every {@link #ESCALATION_DECAY_TICKS} that pass without another escape. Stay out of the eggs for a while and
 * you get your wind back.
 * <p>
 * Both maps are transient in-memory and keyed weakly by entity, so a respawned player is a fresh entity object and
 * therefore starts clean - death resets the escalation for free.
 */
public final class HuggerImmunity {

    private HuggerImmunity() {}

    /** How long a host that fought a hugger off is left alone. */
    public static final int IMMUNITY_TICKS = 30 * 20;

    /** One escalation step is shed for every five minutes without another escape. */
    public static final int ESCALATION_DECAY_TICKS = 5 * 60 * 20;

    private static final Map<Entity, Long> IMMUNE_UNTIL = new WeakHashMap<>();

    private static final Map<Entity, Escalation> ESCALATIONS = new WeakHashMap<>();

    /** True if huggers must currently leave this host alone. */
    public static boolean isImmune(Entity host) {
        var until = IMMUNE_UNTIL.get(host);
        if (until == null) {
            return false;
        }
        if (host.level().getGameTime() >= until) {
            IMMUNE_UNTIL.remove(host);
            return false;
        }
        return true;
    }

    /** A host tore a hugger off: grant the window and make the next one harder. */
    public static void onEscape(Entity host) {
        var now = host.level().getGameTime();
        IMMUNE_UNTIL.put(host, now + IMMUNITY_TICKS);

        var escalation = ESCALATIONS.computeIfAbsent(host, ignored -> new Escalation());
        decay(escalation, now);
        escalation.escapes++;
        escalation.lastEscapeTick = now;
    }

    /** How many escapes currently count against this host, after decay. */
    public static int escapeCount(Entity host) {
        var escalation = ESCALATIONS.get(host);
        if (escalation == null) {
            return 0;
        }

        decay(escalation, host.level().getGameTime());

        if (escalation.escapes <= 0) {
            ESCALATIONS.remove(host);
            return 0;
        }
        return escalation.escapes;
    }

    /** Wipe both the window and the escalation (used on death; a respawn is a clean slate anyway). */
    public static void reset(Entity host) {
        IMMUNE_UNTIL.remove(host);
        ESCALATIONS.remove(host);
    }

    /** Shed one escalation step per {@link #ESCALATION_DECAY_TICKS} elapsed, warden-warning-level style. */
    private static void decay(Escalation escalation, long now) {
        if (escalation.escapes <= 0) {
            return;
        }

        var elapsed = now - escalation.lastEscapeTick;
        if (elapsed < ESCALATION_DECAY_TICKS) {
            return;
        }

        var steps = (int) (elapsed / ESCALATION_DECAY_TICKS);
        escalation.escapes = Math.max(0, escalation.escapes - steps);
        escalation.lastEscapeTick = now;
    }

    private static final class Escalation {

        private int escapes;

        private long lastEscapeTick;
    }
}
