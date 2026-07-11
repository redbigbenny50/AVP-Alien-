package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.combat;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
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
    private static void applyHiveWorkerLeash(Xenomorph xenomorph, List<LivingEntity> targets) {
        if (xenomorph instanceof Queen) {
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
        targets.removeIf(target -> !structureChunks.containsKey(new ChunkPos(target.blockPosition())));
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
