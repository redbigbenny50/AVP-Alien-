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
        // END-STYLE: there is no structure - banking is VENT-ONLY across the whole claimed territory, and it is the
        // End's entire population model, so the scan box is the claimed footprint at full height (islands scatter
        // vertically). No vent standing means nothing banks - the End absorb below requires vent contact and never
        // uses the bank-in-place fallback, so a player who wants all their xenomorphs visible just keeps no vents.
        var endStyle = com.alien.common.gameplay.hive.dimension.EndStyleHiveRules.isEndStyle(level);
        var chunks = endStyle ? location.claimedChunks() : location.structurePieceByChunk().keySet();
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
        var box = endStyle
            ? new AABB(minX, level.getMinBuildHeight(), minZ, maxX + 1, level.getMaxBuildHeight(), maxZ + 1)
            : new AABB(minX, location.hiveFloorY(), minZ, maxX + 1, location.hiveCeilingY() + 1, maxZ + 1);

        for (var alien : level.getEntitiesOfClass(Alien.class, box, candidate -> isAbsorbable(location, candidate))) {
            var vent = nearestVent(location, alien.blockPosition());

            if (vent != null && alien.distanceToSqr(vent.getX() + 0.5, vent.getY() + 0.5, vent.getZ() + 0.5) <= VENT_ABSORB_RANGE_SQUARED) {
                absorb(level, location, alien, vent);
                continue;
            }

            // Past the grace window and still not at a vent: bank it where it stands. The fiction bends; the bank
            // never wedges. Marked returners (disbanded carve crews) time their grace from the MARK, not from age -
            // they are often minutes old and would otherwise blink out instantly, which is the exact mass-vanish
            // this path exists to prevent.
            if (endStyle) {
                continue; // END-STYLE: banking is VENT-ONLY - "if theres no vent they wont store". Never in place.
            }
            var graceExpired = alien.isMarkedForReserveReturn()
                ? level.getGameTime() - alien.reserveReturnMarkedAtTick() >= ABSORB_GRACE_TICKS
                : alien.tickCount >= ABSORB_AGE_TICKS + ABSORB_GRACE_TICKS;
            if (graceExpired) {
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
        // ROYALTY IS NEVER BANKED - either path, no exceptions. Caught live by QUEEN-DIAG (Aug 1 tester log): a
        // host-born praetorian promoted to daughter queen kept hostBorn=true across the molt (the flag survives
        // transitions BY DESIGN), stood idle in her mother's structure footprint during FOUNDING_HANDOFF, passed
        // every gate below, and was absorbed into the brood bank as an integer - discarded mid-founding. The
        // unload handler has guarded the QUEENS tag against exactly this since its own version of the bug; this
        // task needed the same guard and never had it. The tag covers queens AND empresses.
        if (alien.getType().is(AlienEntityTypeTags.QUEENS)) {
            return false;
        }
        // END-STYLE eligibility differences ([stated] spec): summoned and spawn-egged xenomorphs bank too - the
        // reserve stores PLAYER CHOICES, and host-born is only one of the supply routes - so the host-born gate is
        // waived; name-tagged xenomorphs NEVER bank (pets, not hive property); adults-only and the task-free gates
        // below apply unchanged. Handled by treating an End non-host-born adult exactly like a host-born one.
        // MARKED RETURNERS bypass every gate but life itself. A disbanded carve crew ([stated] tester report:
        // "when the builders finished a royal tunnel they all despawned") is sent here by CarveWorkers.disband
        // instead of being discarded on the spot - the walk to the vent is the whole point ("seen slipping into
        // the hive's duct network, not blinking out of the air", per the class comment above). They are reserve-
        // materialized (not host-born) and freshly spawned (under ABSORB_AGE), so the normal gates would reject
        // them; the mark IS the eligibility.
        if (alien.isMarkedForReserveReturn()) {
            // Life, combat, and a genuinely higher-priority job are the only things that defer a marked returner -
            // if it somehow joined a party or picked up cargo after being marked, that job wins until it is done.
            return alien.isAlive()
                && alien.getTarget() == null
                && alien.partyMembership() == null
                && !alien.isVehicle()
                && !alien.isPassenger();
        }
        var endStyle = alien.level() instanceof ServerLevel serverLevel
            && com.alien.common.gameplay.hive.dimension.EndStyleHiveRules.isEndStyle(serverLevel);
        if (alien.hasCustomName() && endStyle) {
            return false; // [stated] "name tagged xenos dont get absorbed into the reserves"
        }
        if ((!alien.isHostBorn() && !endStyle) || !alien.isAlive() || alien.tickCount < ABSORB_AGE_TICKS) {
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
        // END-STYLE: there is no structure interior; the claimed-territory scan box is the boundary.
        return endStyle || InteriorSweepDuty.isInsideHive(location, alien);
    }

    private static void absorb(ServerLevel level, HiveLocation location, Alien alien, BlockPos at) {
        // Marked returners fold back IDENTITY-INTACT - mirroring what CarveWorkers.disband did when it absorbed at
        // the site, so walking to the vent first never costs the worker its identity. Everything else banks as
        // fungible brood, unchanged.
        // Marked returners fall back to the brood bank when the identity return is refused - [stated] "if the
        // reserves are full it will join the host born bank as a bonus." (A variant mismatch fails both adds and
        // still leaves the entity be, unchanged.)
        // END-STYLE: everything banks IDENTITY-INTACT into the one universal capless store - the exact xenomorph
        // the player supplied comes back out of the vents, which is the whole point of "storing player choices".
        var endStyleAbsorb = level != null
            && com.alien.common.gameplay.hive.dimension.EndStyleHiveRules.isEndStyle(level);
        var returned = endStyleAbsorb
            ? location.localReserves().addReturningIdentityMember(alien)
            : alien.isMarkedForReserveReturn()
                ? location.localReserves().addReturningIdentityMember(alien)
                    || location.localReserves().addBrood(alien.getType(), 1)
                : location.localReserves().addBrood(alien.getType(), 1);
        if (!returned) {
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

    /** Public so CarveWorkers.disband can aim its disbanding crew at the same vents this task uses. */
    public static @Nullable BlockPos nearestVent(HiveLocation location, BlockPos from) {
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
