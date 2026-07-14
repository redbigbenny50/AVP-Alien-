package com.alien.common.gameplay.hive.party;

import com.alien.Alien;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.structure.HostChamberSlots;
import com.alien.common.gameplay.hive.structure.HostParking;
import com.alien.common.model.alien.FreeMob;
import net.minecraft.server.level.ServerLevel;
import com.alien.common.registry.init.AlienSoundEvents;
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
 * specifies. Once the carrier reaches a surface vent, both it and its cargo are teleported: the host is embedded in a
 * free host-chamber spot, and the drone returns to its duties.
 * <p>
 * This class holds only the PRIMITIVES - grab, carry, hand off. The AI that walks a drone to a host and then to a vent
 * lives in {@code CaptureHostAction} / {@code DeliverHostAction}, because movement must be a GOAP action holding the
 * MOVE mask; driven from an entity tick, the planner's idle goals simply walk the drone away again.
 * <p>
 * Mobs do not resist - being grabbed immobilizes them. Players DO resist (the struggle bar), which is handled by the
 * struggle system, not here; this task only enforces the delivery.
 * <p>
 * [Flag for teammate review: capture/lifecycle interaction.]
 */
public final class HostCaptureTask {

    private HostCaptureTask() {}

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
                        AlienSoundEvents.ENTITY_XENOMORPH_GRAB_HOST.get(),
                        SoundSource.HOSTILE,
                        1.0F,
                        1.0F
                );
    }

    /**
     * NOTE: the old tick-driven capture loop lived here (tick / tryGrabNearbyHost / tryVentHandoff). It is gone.
     * <p>
     * A drone cannot be MOVED from an entity tick - movement has to be a GOAP action holding the MOVE mask, or the
     * planner's idle goals just walk the drone somewhere else. That is what {@code CaptureHostAction} and
     * {@code DeliverHostAction} are for; they call {@link #capture} and {@link #deliverToHostChamber} below. This class
     * is now only the primitives - grab, carry, hand off - with the AI layer on top of it.
     */

    /** The hand-off itself: the host is ducted into the hive and webbed into a free host-chamber spot. */
    public static void deliverToHostChamber(
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