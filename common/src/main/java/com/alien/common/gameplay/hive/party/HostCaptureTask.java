package com.alien.common.gameplay.hive.party;

import com.alien.Alien;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.structure.HostChamberSlots;
import com.alien.common.gameplay.hive.structure.HostParking;
import com.alien.common.model.alien.FreeMob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

/**
 * The capture half of the host-hunt arc: a host-party drone grabs a viable host, walks it to the nearest SURFACE VENT,
 * and the pair is ducted straight into the host chamber.
 * <p>
 * Hosts are never carried home overland - the vent is the hand-off point into the hive, exactly as the party design
 * specifies. Once the carrier is within {@link #VENT_HANDOFF_DISTANCE} of a surface vent, both it and its cargo are
 * teleported: the host is embedded in a free host-chamber spot, and the drone returns to its duties.
 * <p>
 * Mobs do not resist - being grabbed immobilizes them. Players DO resist (the struggle bar), which is handled by the
 * struggle system, not here; this task only enforces the delivery.
 * <p>
 * [Flag for teammate review: capture/lifecycle interaction.]
 */
public final class HostCaptureTask {

    private HostCaptureTask() {}

    /** Reach for actually grabbing a host. */
    private static final double GRAB_RANGE = 2.0;

    /** How close the carrier must get to a surface vent before the hand-off fires. */
    private static final double VENT_HANDOFF_DISTANCE = 1.0;

    /** Checked on this cadence rather than every tick. */
    private static final int CHECK_INTERVAL_TICKS = 10;

    /** True if this alien is currently carrying a captured host. */
    public static boolean isCarryingHost(com.alien.common.gameplay.entity.living.alien.Alien alien) {
        return carriedHost(alien) != null;
    }

    /** The host this alien is carrying, or null. */
    public static @Nullable LivingEntity carriedHost(com.alien.common.gameplay.entity.living.alien.Alien alien) {
        for (var passenger : alien.getPassengers()) {
            if (passenger instanceof LivingEntity living && !(living instanceof com.alien.common.gameplay.entity.living.alien.Alien)) {
                return living;
            }
        }
        return null;
    }

    /** Grab a host: it rides the captor and (if a mob) stops fighting back. */
    public static void capture(com.alien.common.gameplay.entity.living.alien.Alien captor, LivingEntity host) {
        host.startRiding(captor, true);
        if (host instanceof Mob mob && mob instanceof FreeMob freeMob) {
            freeMob.removeFreedom();
        }
        captor.setPersistenceRequired();
        captor.level()
            .playSound(
                null,
                captor.getX(),
                captor.getY(),
                captor.getZ(),
                SoundEvents.SPIDER_AMBIENT,
                SoundSource.HOSTILE,
                1.0F,
                0.6F
            );
    }

    /**
     * Called from a host-party drone's tick. Handles both halves of the job: grab a viable host when one is in reach,
     * and hand a carried host off at the nearest surface vent.
     */
    public static void tick(
        com.alien.common.gameplay.entity.living.alien.Alien captor,
        ServerLevel level,
        HiveLocation location,
        int surfaceBandBlocks
    ) {
        if (captor.tickCount % CHECK_INTERVAL_TICKS != 0 || HostGrabImmunity.isStunned(captor)) {
            return;
        }

        var carried = carriedHost(captor);
        if (carried == null) {
            tryGrabNearbyHost(captor, level);
            return;
        }

        if (!carried.isAlive()) {
            carried.stopRiding();
            return;
        }

        tryVentHandoff(captor, carried, level, location, surfaceBandBlocks);
    }

    private static void tryGrabNearbyHost(com.alien.common.gameplay.entity.living.alien.Alien captor, ServerLevel level) {
        var box = captor.getBoundingBox().inflate(GRAB_RANGE);
        var candidates = level.getEntitiesOfClass(LivingEntity.class, box);
        var target = HostCaptureRules.pickTarget(captor, candidates);
        if (target != null) {
            capture(captor, target);
        }
    }

    private static void tryVentHandoff(
        com.alien.common.gameplay.entity.living.alien.Alien captor,
        LivingEntity carried,
        ServerLevel level,
        HiveLocation location,
        int surfaceBandBlocks
    ) {
        var vents = PartyVentUtil.findSurfaceVents(level, location, surfaceBandBlocks);
        if (vents.isEmpty()) {
            return;
        }
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (var vent : vents) {
            double distance = captor.distanceToSqr(vent.getX() + 0.5, vent.getY(), vent.getZ() + 0.5);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = vent;
            }
        }
        if (nearest == null || nearestDistance > VENT_HANDOFF_DISTANCE * VENT_HANDOFF_DISTANCE + 1.0) {
            return; // still walking it to the vent
        }
        deliverToHostChamber(captor, carried, level, location);
    }

    /** The hand-off itself: the host is ducted into the hive and webbed into a free host-chamber spot. */
    private static void deliverToHostChamber(
        com.alien.common.gameplay.entity.living.alien.Alien captor,
        LivingEntity carried,
        ServerLevel level,
        HiveLocation location
    ) {
        var spot = HostChamberSlots.firstFreeSpot(level, location);
        if (spot == null) {
            return; // no room - keep hold of it until a spot frees up
        }
        carried.stopRiding();
        HostParking.embed(level, carried, spot.pos(), spot.facing());
        level.playSound(
            null,
            spot.pos().getX() + 0.5,
            spot.pos().getY() + 0.5,
            spot.pos().getZ() + 0.5,
            SoundEvents.BEEHIVE_ENTER,
            SoundSource.HOSTILE,
            1.0F,
            0.7F
        );
        Alien.LOGGER.info(
            "Hive at {}: host {} delivered to a host chamber spot at {}.",
            location.centerPos(),
            carried.getName().getString(),
            spot.pos()
        );
    }
}
