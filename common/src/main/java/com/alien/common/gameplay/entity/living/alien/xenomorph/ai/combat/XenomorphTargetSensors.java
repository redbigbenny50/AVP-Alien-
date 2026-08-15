package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.combat;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.alien.common.util.AlienPredicates;
import com.blib.api.common.goap.v1.GOAPSensors;
import com.just.ai.goap.sensor.Sensor;
import com.just.ai.goap.sensor.Sensors;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class XenomorphTargetSensors {

    public static final Sensor.Mono<Xenomorph, List<LivingEntity>> NEARBY_ATTACKABLE_TARGETS = Sensors.map(
        GOAPSensors.NEARBY_ATTACKABLE_TARGETS_KEY,
        xenomorph -> {
            var targets = new ArrayList<LivingEntity>();
            var currentTarget = xenomorph.getTarget();

            for (var livingEntity : xenomorph.getEntitySenseCache().getByClass(LivingEntity.class)) {
                if (
                    canKeepCurrentTarget(xenomorph, currentTarget, livingEntity)
                        || AlienPredicates.canAcquireTarget(xenomorph, livingEntity)
                ) {
                    targets.add(livingEntity);
                }
            }

            if (
                currentTarget != null
                    && !targets.contains(currentTarget)
                    && AlienPredicates.canContinueTargeting(xenomorph, currentTarget)
            ) {
                targets.add(currentTarget);
            }

            var hiveIntruderTarget = xenomorph.getHiveIntruderTargetOrNull();

            if (
                hiveIntruderTarget != null
                    && !targets.contains(hiveIntruderTarget)
                    && (hiveIntruderTarget == currentTarget || AlienPredicates.canAcquireTarget(xenomorph, hiveIntruderTarget))
            ) {
                targets.add(hiveIntruderTarget);
            }

            // Founding leash (Option B): a queen who is still founding her hive (location exists but not yet
            // reproductive) must not pursue targets OUTSIDE her claimed chunks. This keeps her from chasing prey out of
            // her territory and abandoning the founding ritual (or walking into hazards en route). She can still defend
            // against intruders standing INSIDE her claim. No-op for everything except a founding queen.
            applyFoundingLeash(xenomorph, targets);
            applyHiveWorkerLeash(xenomorph, targets);
            applyTargetGiveUp(xenomorph, targets);
            applyEggDutyDisarm(xenomorph, targets);
            applyHostHuntSwitch(xenomorph, targets);

            return targets;
        }
    );

    /**
     * Removes any target outside the founding queen's claimed chunks. Only acts when {@code xenomorph} is a queen whose
     * current location is founded-but-not-yet-reproductive; otherwise does nothing.
     */
    private static void applyFoundingLeash(Xenomorph xenomorph, List<LivingEntity> targets) {
        if (!(xenomorph instanceof Queen)) {
            return;
        }
        var location = foundingLocationOrNull(xenomorph);
        if (location == null) {
            return;
        }
        targets.removeIf(target -> !location.claimedChunks().contains(new ChunkPos(target.blockPosition())));
    }

    /**
     * Territory leash for hive WORKERS (Option 1, structure-scoped): a non-queen xenomorph in a live hive defends the
     * BUILT structure but must NOT chase prey outside it. Scoped to chunks that contain a placed structure piece (not
     * the whole claim) - on a flat world the claim is huge and full of surface mobs. Dispatched party members are
     * EXEMPT; a xeno not in any live territory is left to wild behaviour.
     * <p>
     * [Flag for teammate review: aggression/threat targeting flow.]
     */
    /** Matches AlienPredicates' own RETALIATION_GRUDGE_TICKS - one definition of "just hurt me", two enforcers. */
    private static final int LEASH_RETALIATION_TICKS = 200;

    private static void applyHiveWorkerLeash(Xenomorph xenomorph, List<LivingEntity> targets) {
        // ROYALS ARE NOT WORKERS. Matched on the QUEENS tag rather than `instanceof Queen`, because the EMPRESS
        // extends Xenomorph and NOT Queen - so she fell through this guard and got leashed like a drone.
        //
        // The symptom was baffling in the field ([stated] "the empress just ignores them and they ignore her",
        // "they are walking around and have ai", and two empresses of DIFFERENT strains standing peacefully
        // together): she wandered into a hive a queen had dug, and from that moment every target outside the
        // location's STRUCTURE chunks was stripped from her list. A claim is far larger than its structure - the
        // comment below says as much - so standing on her own hive's outer territory pacified her completely
        // while leaving idle wander untouched. A second empress spawned away from the claim behaved perfectly,
        // which is what proved it was per-entity position and not a caste-wide flaw.
        //
        // The tag ALREADY contains #avp_alien:empresses, so this needs no data change and picks up any future
        // royal automatically. applyFoundingLeash above stays `instanceof Queen` on purpose - founding is hers.
        if (xenomorph.getType().is(AlienEntityTypeTags.QUEENS)) {
            return;
        }
        if (xenomorph.partyMembership() != null) {
            return; // out on a surface/attack party - allowed to engage outside the hive
        }
        var location = HiveLocationRegistry.INSTANCE.getByChunk(
            xenomorph.level().dimension(),
            new ChunkPos(xenomorph.blockPosition())
        );
        if (location == null || !location.isAlive()) {
            return;
        }
        var structureChunks = location.structurePieceByChunk();

        // THE LEASH IS ABOUT PREY, NOT ABOUT DEFENCE.
        //
        // Two things must survive it, or a worker standing on his own hive's ground is pacified in a fight he
        // did not start:
        //
        // 1. A RIVAL ALIEN. Surface parties claim chunks as they go ("surface party opportunistic claim" fires
        // all over the log), so a battle between two strains happens ON claimed territory - far from either
        // hive STRUCTURE. Every rival was being stripped from the defender's list the moment he stood there.
        // [stated] "the normal xeno reinforcments wouldnt fight the nether xenos".
        // 2. ANYTHING THAT JUST HURT HIM. Retaliation already overrides every rule in AlienPredicates; it must
        // override this one too, or the override is a lie for any leashed caste.
        //
        // This also explains why the fight PETERED OUT rather than never starting: the original combatants were
        // party members and exempt, and the log shows "surface spawn party resolved at dawn". The instant a
        // party resolved, its members lost partyMembership, fell into this leash and went quiet mid-battle.
        // [stated] "after a while the pure xenos stopped fighting back".
        targets.removeIf(target -> {
            if (target instanceof Alien rival && AlienPredicates.areAliensEnemies(xenomorph, rival)) {
                return false;
            }
            if (
                xenomorph.getLastHurtByMob() == target
                    && xenomorph.tickCount - xenomorph.getLastHurtByMobTimestamp() < LEASH_RETALIATION_TICKS
            ) {
                return false;
            }
            return !structureChunks.containsKey(new ChunkPos(target.blockPosition()));
        });
    }

    // ===== Target give-up (anti wall-shove / anti kite) ==============================================================
    // Without this, canContinueTargeting keeps a target forever with no line-of-sight or reachability check, so a xeno
    // that spotted a mob through a vent/doorway shoves the wall between them indefinitely (and can be kited/abused).
    // A xeno drops a target only when it is FAR and has made no progress toward it for a while - i.e. it genuinely
    // can't reach it. A target that is close AND visible counts as actively engaged and is NEVER dropped, so this can
    // never make a xeno quit mid-fight. Distance + the already-computed LOS only; no pathfinding.
    // [Flag for teammate review: complements AlienPredicates.canContinueTargeting.]

    /** Close AND visible = actively fighting; never give up inside this range. */
    private static final double ENGAGE_RANGE_SQUARED = 4.0 * 4.0;

    /** Must close by ~1 block to count as progress (ignore jitter). */
    private static final double PROGRESS_EPSILON_SQUARED = 1.0;

    /** ~6s of no progress toward an unreachable far target before dropping it. Tunable. */
    private static final int GIVE_UP_TICKS = 120;

    /** Transient per-xeno progress tracking for the current target. Not saved - targets don't persist across reload. */
    private static final Map<Alien, TargetProgress> TARGET_PROGRESS = new WeakHashMap<>();

    private static final class TargetProgress {

        private int targetId = Integer.MIN_VALUE;

        private int lastProgressTick;

        private double bestDistanceSquared = Double.MAX_VALUE;
    }

    /**
     * A xenomorph CARRYING AN EGG does not pick fights.
     * <p>
     * This is not just flavour: the DROP_OFF_EGG action is gated on {@code HAS_ATTACK_TARGET == false}. A hauler that
     * acquired any target - through a wall, a passing mob, anything - could therefore never run the drop-off action at
     * all, and simply stood in place holding its egg forever, with no error and no log (perform() was never even
     * reached). Dropping the target lets the delivery proceed.
     * <p>
     * It keeps defending itself in the sense that anything that hurts it will still be handled once the egg is
     * delivered - the egg run just takes priority over the fight.
     */
    /**
     * A host-hunt drone is HUNTING, not brawling.
     * <p>
     * Two things happen here, and both are needed because CAPTURE_HOST is gated on {@code HAS_ATTACK_TARGET ==
     * false}:
     * <ol>
     * <li><b>A capturable target is never attacked.</b> Players may only be grabbed once worn down to 30% health, so a
     * drone has to fight them first - but the moment they turn capturable the attack target is dropped, so the drone
     * stops trying to KILL them and starts trying to TAKE them.</li>
     * <li><b>Non-hosts are ignored while a host is in reach.</b> The surface is full of mobs; a hunting drone that
     * locked onto a zombie kept HAS_ATTACK_TARGET true forever and could never capture ANYTHING. It ignores them and
     * goes for its quarry - unless something actually hurt it, in which case it defends itself.</li>
     * </ol>
     */
    private static void applyHostHuntSwitch(Xenomorph xenomorph, List<LivingEntity> targets) {
        // (0) NEVER target our own passengers - above all, the host we are already carrying. A carried host is a
        // passenger, so HostCaptureRules.isCapturable returns false for it (rule below), which means filter (1)
        // does NOT strip it; and findCaptureTarget returns null while carrying, so filter (2) is skipped too. The
        // captive therefore stayed a valid ATTACK target, the drone set its own rider as its target, and tried to
        // path to a thing moving with it - the "tug of war" where a carrier chases the host on its own back. This
        // runs for ALL host-hunters (not gated on isOnHostHunt) since a carrier could leave hunt state mid-haul.
        if (!xenomorph.getPassengers().isEmpty()) {
            targets.removeIf(candidate -> candidate.getVehicle() == xenomorph);
            var riding = xenomorph.getTarget();
            if (riding != null && riding.getVehicle() == xenomorph) {
                xenomorph.setTarget(null);
            }
        }

        if (!com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host.HostHuntDuty.isOnHostHunt(xenomorph)) {
            return;
        }

        // (1) never attack something we mean to carry off
        targets.removeIf(
            candidate -> com.alien.common.gameplay.hive.party.HostCaptureRules.isCapturable(xenomorph, candidate)
        );

        // (2) with a host in reach, ignore everything that is not actively hurting us
        var quarry = com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host.HostSensors
            .findCaptureTarget(xenomorph);
        if (quarry != null) {
            var aggressor = xenomorph.getLastHurtByMob();
            targets.removeIf(candidate -> candidate != aggressor);
        }

        var current = xenomorph.getTarget();
        if (current != null && !targets.contains(current)) {
            xenomorph.setTarget(null);
        }
    }

    private static void applyEggDutyDisarm(Xenomorph xenomorph, List<LivingEntity> targets) {
        if (!com.alien.common.gameplay.hive.party.EggDutyGuard.isOnEggDuty(xenomorph)) {
            return;
        }
        targets.clear();
        if (xenomorph.getTarget() != null) {
            xenomorph.setTarget(null);
        }
        // The [eggdebug] line that used to sit here is gone - it was armed for the frozen-hauler investigation, that
        // bug is long fixed, and firing per carrier every 5 seconds it was the loudest thing in every log.
    }

    private static void applyTargetGiveUp(Xenomorph xenomorph, List<LivingEntity> targets) {
        var current = xenomorph.getTarget();
        if (current != null && shouldGiveUpOnTarget(xenomorph, current)) {
            targets.remove(current);
        }
    }

    private static boolean shouldGiveUpOnTarget(Xenomorph xenomorph, LivingEntity target) {
        int now = xenomorph.tickCount;
        var progress = TARGET_PROGRESS.computeIfAbsent(xenomorph, key -> new TargetProgress());
        double distanceSquared = xenomorph.distanceToSqr(target);

        // New target: start fresh, keep it.
        if (progress.targetId != target.getId()) {
            progress.targetId = target.getId();
            progress.lastProgressTick = now;
            progress.bestDistanceSquared = distanceSquared;
            return false;
        }

        // Close AND visible = actively engaged. Never give up; hold the timer fresh.
        if (distanceSquared <= ENGAGE_RANGE_SQUARED && xenomorph.getSensing().hasLineOfSight(target)) {
            progress.lastProgressTick = now;
            progress.bestDistanceSquared = Math.min(progress.bestDistanceSquared, distanceSquared);
            return false;
        }

        // Got meaningfully closer than ever before -> real progress, reset the give-up timer.
        if (distanceSquared < progress.bestDistanceSquared - PROGRESS_EPSILON_SQUARED) {
            progress.bestDistanceSquared = distanceSquared;
            progress.lastProgressTick = now;
        }

        // Far, and no progress for too long: it can't be reached (wall between / being kited) -> drop it.
        return now - progress.lastProgressTick > GIVE_UP_TICKS;
    }

    /**
     * The xenomorph's current location IF it is in founding mode (founder set, not yet reproductive), else null.
     */
    private static HiveLocation foundingLocationOrNull(Xenomorph xenomorph) {
        var location = HiveLocationRegistry.INSTANCE.getByChunk(
            xenomorph.level().dimension(),
            new ChunkPos(xenomorph.blockPosition())
        );
        if (location != null && location.founderId() != null && !location.reproductiveEstablished()) {
            return location;
        }
        return null;
    }

    private static boolean canKeepCurrentTarget(
        Xenomorph xenomorph,
        LivingEntity currentTarget,
        LivingEntity potentialTarget
    ) {
        return potentialTarget == currentTarget && AlienPredicates.canContinueTargeting(xenomorph, potentialTarget);
    }

    private XenomorphTargetSensors() {
        throw new UnsupportedOperationException();
    }
}
