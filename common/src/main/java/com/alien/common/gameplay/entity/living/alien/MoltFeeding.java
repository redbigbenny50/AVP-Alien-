package com.alien.common.gameplay.entity.living.alien;

import com.alien.common.gameplay.entity.living.alien.ovomorph.Ovomorph;
import com.alien.common.gameplay.entity.living.alien.parasite.facehugger.Facehugger;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Molt feeding + spent-remains cleanup.
 * <p>
 * An implantation leaves litter behind forever: the opened ovomorph and the spent facehugger both persist in place, so
 * a host chamber silently fills with corpses. Rather than just deleting them, the young alien EATS them: a chestburster
 * or adolescent that is next to a spent egg or a spent facehugger consumes it and instantly completes one molt phase.
 * Both have three phases, so eating the egg AND the hugger left by its own birth cuts its molting from three phases to
 * one.
 * <p>
 * A spent remain that nothing eats despawns after {@link #REMAINS_LIFETIME_TICKS} (one Minecraft day), so a chamber can
 * never pile up even with no young aliens around.
 * <p>
 * "Spent" means finished, never live: an ovomorph that has already HATCHED (its hugger is out), and a facehugger that
 * has already implanted its embryo ({@code isFertile == false}). Live eggs and fertile huggers are never eaten.
 * <p>
 * [Flag for teammate review: growth/lifecycle system.]
 */
public final class MoltFeeding {

    private MoltFeeding() {}

    /** Spent remains left uneaten disappear after one Minecraft day. */
    public static final int REMAINS_LIFETIME_TICKS = 24000;

    /** How close a young alien must be to eat a spent remain. */
    private static final double FEED_RADIUS = 2.0;

    /** Feeding is checked on this cadence, not every tick. */
    private static final int FEED_CHECK_INTERVAL_TICKS = 20;

    /** Game time each remain was first seen spent. Transient - a reload just restarts the cleanup clock. */
    private static final Map<Entity, Long> SPENT_SINCE = new WeakHashMap<>();

    /**
     * Called from a chestburster's / adolescent's tick. If it is still molting and a spent egg or hugger is within
     * reach, it eats one and advances a molt phase.
     */
    public static void tickFeeding(Alien alien) {
        if (alien.level().isClientSide || alien.tickCount % FEED_CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        if (alien.getMoltingManager().hasReachedTargetScale()) {
            return; // fully grown - nothing left to gain
        }
        var meal = findSpentRemain(alien);
        if (meal == null) {
            return;
        }
        if (!alien.getMoltingManager().advancePhase()) {
            return;
        }

        // [stated] "if the adol eats anything it jumps ahead its growth time." A spent remain is food like any
        // other, so it now buys growth time ON TOP of the molt phase it already bought. ⚠ THE TWO ARE DIFFERENT
        // AXES and both are wanted: advancePhase is MOLTING (body scale - this form finishing growing INTO
        // itself), feedOnMeal is GROWTH (the clock toward becoming the NEXT form). Eating an egg used to move
        // only the first.
        // ⚠ getGrowthManager lives on Xenomorph, NOT Alien - a chestburster is an Alien and has its own private
        // one, so it keeps the molt phase and does not get the growth jump. Only the adolescents were specified.
        if (alien instanceof com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph xenomorph) {
            xenomorph.getGrowthManager().feedOnMeal();
        }

        consume(alien, meal);
    }

    /**
     * Called from a spent egg's / spent hugger's tick: once it has lain around for a full Minecraft day, remove it, so
     * uneaten remains can never accumulate. Age is tracked in memory only - after a reload the clock simply restarts,
     * which is fine for a cleanup backstop.
     */
    public static void tickRemainsLifetime(Entity remain) {
        if (remain.level().isClientSide || remain.tickCount % FEED_CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        if (!isSpentEgg(remain) && !isSpentHugger(remain)) {
            SPENT_SINCE.remove(remain); // still live - not litter yet
            return;
        }
        long now = remain.level().getGameTime();
        long since = SPENT_SINCE.computeIfAbsent(remain, key -> now);
        if (now - since >= REMAINS_LIFETIME_TICKS) {
            SPENT_SINCE.remove(remain);
            remain.discard();
        }
    }

    /** True if this ovomorph is a spent shell: already hatched, its facehugger long gone. */
    public static boolean isSpentEgg(Entity entity) {
        return entity instanceof Ovomorph ovomorph
            && ovomorph.isAlive()
            && ovomorph.getHatchManager().isHatched();
    }

    /** True if this facehugger has already implanted its embryo and has nothing left to give. */
    public static boolean isSpentHugger(Entity entity) {
        return entity instanceof Facehugger facehugger
            && facehugger.isAlive()
            && !facehugger.isFertile.get()
            && !facehugger.isPassenger();
    }

    /**
     * The nearest spent remain it can actually reach.
     * <p>
     * The box is inflated in every direction, so on its own it reaches straight THROUGH walls - a burster was eating
     * the egg in the next chamber without moving. {@code hasLineOfSight} clips an eye-to-eye ray against block
     * colliders, so a resin wall between them now blocks the meal exactly as it should.
     * <p>
     * NEAREST rather than first-found, too: the box iterates in no meaningful order, so with several remains around it
     * would snap at an arbitrary one while ignoring the corpse at its feet.
     */
    private static Entity findSpentRemain(Alien alien) {
        var box = alien.getBoundingBox().inflate(FEED_RADIUS);
        Entity closest = null;
        var closestDistance = Double.MAX_VALUE;

        for (var candidate : alien.level().getEntities(alien, box)) {
            if (!isSpentEgg(candidate) && !isSpentHugger(candidate)) {
                continue;
            }
            if (!alien.hasLineOfSight(candidate)) {
                continue;
            }

            var distance = alien.distanceToSqr(candidate);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = candidate;
            }
        }

        return closest;
    }

    private static void consume(Alien alien, Entity meal) {
        var level = alien.level();

        // Snap at the meal: reuse the bite attack animation so eating actually reads as eating.
        //
        // ⚠⚠ THIS RUNS ON THE SERVER, SO IT MUST NOT TOUCH A DISPATCHER. It used to call
        // getAnimationDispatcher().biteAttack() directly, which produced the log line "AzCommand.dispatch() was called
        // on the server for Entity 'Adolescent'" and played NOTHING - animation commands are client-side only. The
        // correct server-side lever is startAttack, which sets the synced attackType / attackId /
        // attackStartedAtGameTime accessors; each client's animator reads those and plays the clip itself. Same
        // synced-flag rule as the carve dig gait.
        alien.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, meal.position());
        if (alien instanceof com.alien.common.gameplay.entity.living.alien.adolescent.Adolescent adolescent) {
            adolescent.startAttack(
                com.alien.common.gameplay.entity.living.alien.adolescent.Adolescent.BITE,
                null
            );
        }
        level.playSound(
            null,
            alien.getX(),
            alien.getY(),
            alien.getZ(),
            SoundEvents.GENERIC_EAT,
            SoundSource.HOSTILE,
            1.0F,
            0.7F
        );
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                ParticleTypes.ITEM_SLIME,
                meal.getX(),
                meal.getY() + 0.4,
                meal.getZ(),
                12,
                0.2,
                0.2,
                0.2,
                0.01
            );
        }
        meal.discard();
    }
}
