package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.combat;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.util.AlienPredicates;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/**
 * TARGETING DIAGNOSTIC — answers "why is this thing not fighting, and why is nothing fighting it".
 * <p>
 * Written because the empress/marine mutual-ignore could not be found by reading the code: every structural suspect
 * (tags, attackable/isAlliedTo overrides, GOAP packages, attack config, follow range) checked out clean, so the missing
 * information is runtime state rather than logic. This walks the ACTUAL predicate chain on a live entity and reports
 * which gate says no.
 * <p>
 * It reports BOTH DIRECTIONS per candidate — can the subject target it, and can it target the subject — because a
 * mutual ignore is two separate failures until proven otherwise, and knowing which half fails halves the search.
 * <p>
 * Read-only: it calls the same public predicates target selection uses and changes nothing.
 */
public final class XenomorphTargetingDiagnostics {

    /** How far out to consider candidates, independent of the sense cache, so we can see what the cache MISSED. */
    private static final double CANDIDATE_RADIUS = 40.0;

    private XenomorphTargetingDiagnostics() {
        throw new UnsupportedOperationException();
    }

    public static List<String> report(Xenomorph subject) {
        var lines = new ArrayList<String>();

        lines.add("=== TARGETING DIAG: " + subject.getType().getDescriptionId() + " " + subject.getUUID() + " ===");
        lines.add(describeSubject(subject));

        // The sense cache is what target selection actually iterates. If it is empty while candidates are clearly
        // nearby, the fault is upstream of every predicate below and nothing else in this report matters.
        var sensed = subject.getEntitySenseCache().getByClass(LivingEntity.class);
        lines.add("senseCache: " + sensed.size() + " living entities");

        var candidates = subject.level()
            .getEntitiesOfClass(
                LivingEntity.class,
                subject.getBoundingBox().inflate(CANDIDATE_RADIUS),
                candidate -> candidate != subject && candidate.isAlive()
            );

        lines.add("within " + (int) CANDIDATE_RADIUS + " blocks: " + candidates.size() + " living entities");

        if (candidates.isEmpty()) {
            lines.add("  (nothing to test against)");
            return lines;
        }

        for (var candidate : candidates) {
            lines.add(describeCandidate(subject, candidate, sensed.contains(candidate)));
        }

        return lines;
    }

    private static String describeSubject(Xenomorph subject) {
        var target = subject.getTarget();
        var location = HiveLocationRegistry.INSTANCE.getByChunk(
            subject.level().dimension(),
            new ChunkPos(subject.blockPosition())
        );

        return "  variant=" + subject.getVariant()
            + " scale=" + String.format("%.2f", subject.getScale())
            + " invulnerable=" + subject.isInvulnerable()
            + " noAi=" + subject.isNoAi()
            + " attackable=" + subject.attackable()
            + " canBeSeenAsEnemy=" + subject.canBeSeenAsEnemy()
            + " deadOrDying=" + subject.isDeadOrDying()
            + "\n  target=" + (target == null ? "none" : target.getType().getDescriptionId())
            + " party=" + (subject.partyMembership() == null ? "none" : "yes")
            + " crawling=" + subject.getCrawlingManager().isCrawling()
            + "\n  passengers=" + describePassengers(subject)
            + " (an EMPRESS_OVIPOSITOR passenger swaps her onto the egg-laying-ONLY graph, which has no combat"
            + " package at all - she would walk to resin spots and never fight anything)"
            // ⭐⭐⭐ WHAT IS IT ACTUALLY DOING? The report answered "what may it FIGHT" and nothing else, so a
            // xenomorph busy with an errand looked identical to one idling at nothing.
            //
            // ⚠⚠ THIS COST A WHOLE DIAGNOSIS. Runners were reported "clearly attacking something on those
            // stalagmites"; the report proved target=none and party=none with only same-lineage allies in range, so
            // I inferred they were egg haulers from a separate log line - a guess I could not confirm, and the
            // player quite reasonably asked where the egg was. A xenomorph fetching an egg forty blocks away looks
            // exactly like one charging at nothing, and NONE of the state that would say so was printed.
            // ⚠ IN WATER IS ON THIS LINE BECAUSE THE SWIM ACTIONS ARE NOT INSTRUMENTED. A xenomorph spinning in a
            // lake reported target=none, party=none and NO_PATH 0% - because the diagnostic measures the pathing
            // actions and the swim package uses the navigation directly. Printing the state at least says WHY the
            // report looks empty.
            + "\n  inWater=" + subject.isInWater()
            + " navigationDone=" + subject.getNavigation().isDone()
            + "\n  errand: haulingEggAt=" + describeHaulTarget(subject)
            + " carryingHost=" + com.alien.common.gameplay.hive.party.HostCaptureTask.isCarryingHost(subject)
            + " onHostHunt=" + com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host.HostHuntDuty
                .isOnHostHunt(subject)
            + " quarry=" + describeQuarry(subject)
            // !!! "LEAVING" IS A TASK AND IT WAS INVISIBLE HERE. A worker marked for reserve return counts as BUSY to
            // every carve site, so a hive can report "unstaffed - no free drones" while drones stand around plainly
            // doing nothing. Nine readouts of that exact situation all printed target=none and no errand, and said
            // nothing about why - because this line was missing.
            + " leaving=" + describeReserveReturn(subject)
            + "\n  standingInLocation=" + (location == null ? "none" : location.id() + " alive=" + location.isAlive())
            + " (a LIVE location here arms the hive-worker leash, which strips every target outside its structure chunks)";
    }

    /**
     * Whether this worker has been marked to fold back into the reserve, and how long ago.
     * <p>
     * The age is the useful half: a mark minutes old means the walk to a vent failed, which is the difference between
     * "it is on its way out" and "it is stuck being neither working nor banked".
     * </p>
     */
    private static String describeReserveReturn(Xenomorph subject) {
        if (!subject.isMarkedForReserveReturn()) {
            return "false";
        }

        return "true (marked " + (subject.level().getGameTime() - subject.reserveReturnMarkedAtTick()) + "t ago)";
    }

    /**
     * Riders matter because the royals swap GOAP graphs on them: a queen or empress carrying her ovipositor runs the
     * egg-laying-only graph, which registers no combat goals whatsoever. A rider that fails to render would look
     * exactly like "she has AI but never fights".
     */
    /**
     * The egg this xenomorph has been sent to fetch, if any.
     * <p>
     * ⚠ Prints the POSITION, not just "yes". "haulingEggAt=[1042,37,-2380]" tells you instantly whether the thing it is
     * charging at is off-screen - which is the whole question when a player says it is attacking nothing.
     * </p>
     */
    private static String describeHaulTarget(Xenomorph subject) {
        if (
            !(subject instanceof com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg_laying.EggLayer)
                && !(subject instanceof com.alien.common.gameplay.entity.living.alien.EggCarrier)
        ) {
            return "n/a";
        }
        if (subject instanceof com.alien.common.gameplay.entity.living.alien.EggCarrier carrier) {
            var egg = carrier.getEggPickupManager().getTargetOvomorphOrNull();
            return egg == null ? "none" : egg.blockPosition().toShortString();
        }
        return "none";
    }

    /** The host it is stalking, if any - the other errand that looks like unprovoked charging. */
    private static String describeQuarry(Xenomorph subject) {
        var quarry = com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host.HostSensors
            .findCaptureTarget(subject);
        return quarry == null
            ? "none"
            : quarry.getType().getDescription().getString()
                + " @" + quarry.blockPosition().toShortString();
    }

    private static String describePassengers(Xenomorph subject) {
        if (subject.getPassengers().isEmpty()) {
            return "none";
        }

        var names = new ArrayList<String>();

        for (var passenger : subject.getPassengers()) {
            names.add(passenger.getType().getDescriptionId());
        }

        return String.join(",", names);
    }

    /**
     * One candidate, both directions. The outbound chain is reported gate by gate in the order target selection
     * evaluates it, so the FIRST false is the answer.
     */
    private static String describeCandidate(Xenomorph subject, LivingEntity candidate, boolean sensed) {
        var line = new StringBuilder();

        line.append("  - ")
            .append(candidate.getType().getDescriptionId())
            .append(" @ ")
            .append(String.format("%.1f", subject.distanceTo(candidate)))
            .append("m")
            .append(" sensed=")
            .append(sensed);

        // OUTBOUND: can the subject target this?
        var valid = AlienPredicates.isValidTarget(subject.getVariant(), candidate);
        var canContinue = AlienPredicates.canContinueTargeting(subject, candidate);
        var canTarget = AlienPredicates.canTarget(subject, candidate);
        var canAcquire = AlienPredicates.canAcquireTarget(subject, candidate);
        var los = subject.getSensing().hasLineOfSight(candidate);

        line.append("\n      OUT: isValidTarget=")
            .append(valid)
            .append(" canContinueTargeting=")
            .append(canContinue)
            .append(" canTarget=")
            .append(canTarget)
            .append(" canAcquireTarget=")
            .append(canAcquire)
            .append(" lineOfSight=")
            .append(los);

        // canTarget is canContinueTargeting AND the threat-tier gate, so this isolates the tier check without
        // needing to make the private predicate public.
        if (canContinue && !canTarget) {
            line.append("  <-- THREAT-TIER GATE rejected it");
        } else if (!canContinue) {
            line.append("  <-- canContinueTargeting rejected it");
        } else if (canTarget && !canAcquire) {
            line.append("  <-- acquisition rejected it (line of sight)");
        }

        if (candidate instanceof Alien alienCandidate) {
            // ⭐⭐⭐ PRINT THE LINEAGE IDS. THIS IS THE FIELD THAT WOULD HAVE ANSWERED IT IMMEDIATELY.
            //
            // ⚠⚠ TWO ALIENS ARE ENEMIES WHENEVER THEIR LINEAGE IDS DIFFER - no war declaration is needed, only a
            // different id, no shared empress and no remembrance window. So a hive whose QUEEN carries a different
            // lineage id from its own members is not a bug in targeting at all: every drone is correctly attacking
            // a foreign queen standing in its hive, and she is correctly hitting back.
            //
            // ⚠ A live log showed exactly the conditions for it - "enemies=true" against the queen while every other
            // member read "enemies=false", all at variant=NORMAL, and SIXTY lineage factions in that world holding
            // ZERO locations. Without the ids printed, that could only be inferred.
            var subjectLineage = com.alien.common.gameplay.hive.war.AlienTerritoryWarSystem
                .lineageOf(subject.getUUID());
            var candidateLineage = com.alien.common.gameplay.hive.war.AlienTerritoryWarSystem
                .lineageOf(alienCandidate.getUUID());
            line.append("\n      alien-vs-alien: enemies=")
                .append(AlienPredicates.areAliensEnemies(subject, alienCandidate))
                .append(" theirVariant=")
                .append(alienCandidate.getVariant())
                .append(" (false here means they read as ALLIES and will never fight)")
                .append("\n      lineage: mine=")
                .append(subjectLineage == null ? "NONE" : subjectLineage.getPath())
                .append(" theirs=")
                .append(candidateLineage == null ? "NONE" : candidateLineage.getPath())
                .append(
                    java.util.Objects.equals(subjectLineage, candidateLineage)
                        ? " (SAME)"
                        : "  <-- DIFFERENT LINEAGES: this alone makes them enemies"
                );
        }

        // INBOUND: can this target the subject? Only meaningful for our own castes - another mod's mobs run their
        // own selection and this says nothing about them.
        if (candidate instanceof Xenomorph xenoCandidate) {
            line.append("\n      IN:  theirCanAcquire=")
                .append(AlienPredicates.canAcquireTarget(xenoCandidate, subject))
                .append(" theirCanTarget=")
                .append(AlienPredicates.canTarget(xenoCandidate, subject))
                .append(" theirTarget=")
                .append(xenoCandidate.getTarget() == null ? "none" : xenoCandidate.getTarget().getType().getDescriptionId());
        }

        return line.toString();
    }
}
