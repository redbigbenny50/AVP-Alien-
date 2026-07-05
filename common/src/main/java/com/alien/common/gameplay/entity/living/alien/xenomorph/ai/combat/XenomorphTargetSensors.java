package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.combat;

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
