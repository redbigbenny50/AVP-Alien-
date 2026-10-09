package com.alien.common.gameplay.entity.living.alien;

import com.alien.common.util.AlienPredicates;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Makes a hive answer an attack it is not expecting.
 * <h2>The problem this solves</h2> ⭐ [stated] Sep 28, on another mod's xenomorphs mauling ours: "they getting hit and
 * not responding to it. We shouldn't need to add every single possible mob to the list in order for them to attack it."
 * <p>
 * Retaliation already worked, but only in the narrowest possible way. {@code isTargetThreatAllowed} lets an alien fight
 * back against whatever hurt it whatever the threat tiers say - and that override is correct - but it is PER INDIVIDUAL
 * and lasts 200 ticks. So one drone flails for ten seconds while five of its sisters stand next to it hauling eggs, and
 * the moment line of sight breaks the grudge lapses and nothing re-acquires the attacker, because a mod we have never
 * heard of is in no threat tier. From the outside that reads exactly as "they don't fight back".
 * </p>
 * <h2>⭐⭐ THE MECHANISM IS DELIBERATELY THE EXISTING ONE</h2> Both halves below work by calling {@code setLastHurtByMob}
 * on aliens. That is the very field the retaliation override already tests, so this adds NO new predicate, NO new tag
 * and NO new target path - every ally rule, captive rule and validity check keeps applying exactly as before. Anything
 * the hive already refuses to attack it still refuses to attack.
 */
public final class AlienRetaliation {

    /** How far a cry for help carries. Deliberately short: the hall you are standing in, not the whole hive. */
    private static final double SHARE_RADIUS = 16.0D;

    /**
     * ⚠ A CAP, BECAUSE THIS RUNS ON EVERY HIT. A xenomorph under sustained fire is hurt many times a second, and an
     * unbounded query in that path is how a fight turns into a tick spike.
     */
    private static final int MAX_SHARED = 8;

    /** Within this, an attacker still in reach keeps the grudge alive rather than letting it time out. */
    private static final double PERSIST_RADIUS = 24.0D;

    private AlienRetaliation() {}

    /**
     * A hive member was hurt: everyone nearby takes the same attacker personally.
     * <p>
     * ⚠ NOT A FREE-FOR-ALL. A neighbour only adopts the attacker if it would independently accept it as a target once
     * the grudge is set - {@code canContinueTargeting} is consulted first, so friendly fire, captives and anything the
     * hive protects are all still excluded. This widens WHO responds, never WHAT is attackable.
     * </p>
     */
    public static void shareGrudge(Alien victim, LivingEntity attacker) {
        if (victim.level().isClientSide || !attacker.isAlive() || AlienPredicates.isIgnoredByHive(attacker)) {
            return;
        }

        // ⚠ An alien hurt by its own side must never turn the hall on itself.
        if (attacker instanceof Alien attackingAlien && !AlienPredicates.areAliensEnemies(victim, attackingAlien)) {
            return;
        }

        var shared = 0;

        for (
            var neighbour : victim.level()
                .getEntitiesOfClass(
                    Alien.class,
                    AABB.ofSize(victim.position(), SHARE_RADIUS * 2, SHARE_RADIUS * 2, SHARE_RADIUS * 2)
                )
        ) {
            if (shared >= MAX_SHARED) {
                break;
            }

            if (neighbour == victim || neighbour == attacker || !neighbour.isAlive()) {
                continue;
            }

            // Only aliens of the same side answer the call - a rival hive's drone does not come running to help.
            if (AlienPredicates.areAliensEnemies(victim, neighbour)) {
                continue;
            }

            // Already angry at it, or would refuse it anyway: nothing to do.
            if (
                neighbour.getLastHurtByMob() == attacker
                    || !AlienPredicates.canContinueTargeting(neighbour, attacker)
            ) {
                continue;
            }

            neighbour.setLastHurtByMob(attacker);
            shared++;
        }
    }

    /**
     * Keeps a grudge alive while the attacker is still present.
     * <p>
     * ⭐ THIS IS WHY THE 200-TICK WINDOW CAN STAY AS IT IS. The window is the right length for "it hit me and ran" - the
     * alien gives up and goes back to work. It is the wrong length for "it is still standing in front of me", which is
     * the case that made the hive look passive. Re-stamping the timestamp while the attacker is alive and within
     * {@value #PERSIST_RADIUS} blocks turns the window into a LEASH rather than a countdown, and the moment the
     * attacker dies or leaves, the existing timeout does its job untouched.
     * </p>
     */
    public static void refreshGrudge(Alien alien) {
        if (alien.level().isClientSide) {
            return;
        }

        var attacker = alien.getLastHurtByMob();

        if (attacker == null || !attacker.isAlive()) {
            return;
        }

        if (alien.distanceToSqr(attacker) > PERSIST_RADIUS * PERSIST_RADIUS) {
            return;
        }

        // ⚠ Re-stamps through setLastHurtByMob rather than writing the timestamp directly, so any vanilla or mixin
        // bookkeeping attached to that call still happens.
        alien.setLastHurtByMob(attacker);
    }
}
