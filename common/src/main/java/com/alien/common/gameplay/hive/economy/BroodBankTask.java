package com.alien.common.gameplay.hive.economy;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host.InteriorSweepDuty;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.party.EggDutyGuard;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Absorbs idle HOST-BORN adults into the hive's brood bank.
 * <p>
 * A working host chamber with a steady supply of bodies mints real xenomorphs - each one cost a capture, an egg, and a
 * burst - and they persist, so an AFK'd farm ends up with dozens of adults milling about the chamber doing nothing but
 * burning ticks. This task banks them: a host-born adult that has been alive for {@link #ABSORB_AGE_TICKS} and is FREE
 * OF A TASK walks to the nearest vent and folds into the {@code brood} bank ({@code HiveLocationReserves.addBrood}),
 * where it no longer ticks, no longer counts against the member cap, and is drawn on FIRST - as itself, or as a 1:1
 * wildcard for any caste the hive needs - the next time anything spawns from reserves.
 * <p>
 * The walk matters for the fiction: chambers and hallways carry STRUCTURE vents throughout, so the alien is seen
 * slipping into the hive's duct network, not blinking out of the air. If it cannot get near a vent within a grace
 * window (wedged, or a chamber that somehow has none in range), it absorbs in place rather than standing around forever
 * - the bank must never depend on pathfinding succeeding.
 * <h2>Eligibility - every clause is a guard</h2>
 * <ul>
 * <li><b>Host-born only.</b> Reserve-materialized members are the simulation's stock and already governed by the
 * population rules; banking them here would double-count them into an uncapped pool.</li>
 * <li><b>Adults only.</b> Bursters, chrysalises, and adolescents are still growing; absorbing one would freeze it
 * mid-ladder (bank entries have no growth timers).</li>
 * <li><b>Free of a task.</b> The same test the construction economy uses: no egg duty, no party, no attack target,
 * carrying nothing, riding nothing. A busy worker is never yanked.</li>
 * <li><b>Inside the built structure.</b> A host-born runner off raiding the countryside is doing something; only the
 * ones literally milling around the hive are surplus.</li>
 * </ul>
 */
public final class BroodBankTask {

    private BroodBankTask() {}

    /** How old (in ticks) a host-born adult must be before it is banked: 5 minutes. */
    private static final int ABSORB_AGE_TICKS = 5 * 60 * 20;

    /** Past eligibility, how long it may spend failing to reach a vent before it absorbs in place: 2 minutes. */
    private static final int ABSORB_GRACE_TICKS = 2 * 60 * 20;

    /** Close enough to a vent to slip into it (checked on every axis, matching how vents are used elsewhere). */
    private static final double VENT_ABSORB_RANGE_SQUARED = 4.0 * 4.0;

    /** Runs on the hive's 200-tick loaded cadence - absorption is a chore, not a reaction. */
    public static void run(ServerLevel level, HiveLocation location) {
        var chunks = location.structurePieceByChunk().keySet();
        if (chunks.isEmpty()) {
            return;
        }

        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var chunk : chunks) {
            minX = Math.min(minX, chunk.getMinBlockX());
            minZ = Math.min(minZ, chunk.getMinBlockZ());
            maxX = Math.max(maxX, chunk.getMaxBlockX());
            maxZ = Math.max(maxZ, chunk.getMaxBlockZ());
        }
        var box = new AABB(minX, location.hiveFloorY(), minZ, maxX + 1, location.hiveCeilingY() + 1, maxZ + 1);

        for (var alien : level.getEntitiesOfClass(Alien.class, box, candidate -> isAbsorbable(location, candidate))) {
            var vent = nearestVent(location, alien.blockPosition());

            if (vent != null && alien.distanceToSqr(vent.getX() + 0.5, vent.getY() + 0.5, vent.getZ() + 0.5) <= VENT_ABSORB_RANGE_SQUARED) {
                absorb(level, location, alien, vent);
                continue;
            }

            // Past the grace window and still not at a vent: bank it where it stands. The fiction bends; the bank
            // never wedges.
            if (alien.tickCount >= ABSORB_AGE_TICKS + ABSORB_GRACE_TICKS) {
                absorb(level, location, alien, alien.blockPosition());
                continue;
            }

            // Nudge it toward the vent. The GOAP wander may fight this some ticks; between the nudge and its own
            // wandering it converges - chambers and halls carry vents throughout.
            if (vent != null && alien.getNavigation().isDone()) {
                alien.getNavigation().moveTo(vent.getX() + 0.5, vent.getY(), vent.getZ() + 0.5, 1.0);
            }
        }
    }

    private static boolean isAbsorbable(HiveLocation location, Alien alien) {
        if (!alien.isHostBorn() || !alien.isAlive() || alien.tickCount < ABSORB_AGE_TICKS) {
            return false;
        }
        var type = alien.getType();
        // Adults only: never freeze a growth ladder into the bank.
        if (
            !type.is(AlienEntityTypeTags.XENOMORPHS)
                || type.is(AlienEntityTypeTags.CHESTBURSTERS)
                || type.is(AlienEntityTypeTags.PREDALIEN_CHESTBURSTERS)
                || type.is(AlienEntityTypeTags.BURSTERS)
                || type.is(AlienEntityTypeTags.CHRYSALISES)
                || type.is(AlienEntityTypeTags.ADOLESCENTS)
                || type.is(AlienEntityTypeTags.PREDALIEN_ADOLESCENTS)
        ) {
            return false;
        }
        // Free of a task - a busy worker is never yanked.
        if (
            EggDutyGuard.isOnEggDuty(alien)
                || alien.partyMembership() != null
                || alien.getTarget() != null
                || alien.isVehicle()
                || alien.isPassenger()
        ) {
            return false;
        }
        return InteriorSweepDuty.isInsideHive(location, alien);
    }

    private static void absorb(ServerLevel level, HiveLocation location, Alien alien, BlockPos at) {
        if (!location.localReserves().addBrood(alien.getType(), 1)) {
            return; // variant mismatch (logged by the bank) - leave it be rather than delete it for nothing
        }
        level.playSound(
            null,
            at.getX() + 0.5,
            at.getY() + 0.5,
            at.getZ() + 0.5,
            SoundEvents.BEEHIVE_ENTER,
            SoundSource.HOSTILE,
            0.6F,
            0.9F
        );
        alien.discard();
    }

    private static @Nullable BlockPos nearestVent(HiveLocation location, BlockPos from) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (var vent : location.ventManager().allVents()) {
            double dist = vent.distSqr(from);
            if (dist < bestDist) {
                bestDist = dist;
                best = vent;
            }
        }
        return best;
    }
}
