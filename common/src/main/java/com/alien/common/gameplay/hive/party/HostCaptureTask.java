package com.alien.common.gameplay.hive.party;

import com.alien.Alien;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.structure.HostChamberSlots;
import com.alien.common.gameplay.hive.structure.HostParking;
import com.alien.common.model.alien.FreeMob;
import com.alien.common.registry.init.AlienSoundEvents;
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
    /**
     * The host an alien is physically carrying, or null.
     * <p>
     * ⚠ THE OVIPOSITOR EXCLUSION IS LOAD-BEARING - WITHOUT IT A QUEEN DROPS HER EGGSACK TO ANY HIT. The old test was "a
     * passenger that is a LivingEntity and not an Alien", which reads as "a captured victim" but is not:
     * {@code Ovipositor extends Mob}, so it IS a LivingEntity and is NOT an Alien, and a queen wearing her eggsack
     * therefore reported the EGGSACK as her captive. {@code Alien.hurt} drops a carried host on ANY damage from anyone
     * other than the captive - that is the rescue rule - so a 0.01 syringe, a stray splash, anything at all, called
     * {@code breakCapture} and put her sack on the floor. It never touched the disturbance threshold, so nothing logged
     * and no amount of tuning the rouse bar could have fixed it.
     * </p>
     * <p>
     * This is recurring mistake #4 in the notes, verbatim: non-Alien entities like Ovipositor and RoyalCocoon extend
     * Mob and inherit NO alien behaviour, so every "all aliens do X" rule has to list them by hand. Excluded by ENTITY
     * TYPE rather than class so a future prop that also extends Mob is a one-line addition here.
     * </p>
     */
    public static @Nullable LivingEntity carriedHost(com.alien.common.gameplay.entity.living.alien.Alien alien) {
        for (var passenger : alien.getPassengers()) {
            if (
                passenger instanceof LivingEntity living
                    && !(living instanceof com.alien.common.gameplay.entity.living.alien.Alien)
                    && !isCarriedProp(living)
            ) {
                return living;
            }
        }
        return null;
    }

    /** Mod-owned things that RIDE an alien without ever being its captive. */
    private static boolean isCarriedProp(LivingEntity passenger) {
        // BOTH royals. The Empress extends Xenomorph and so is an Alien too, and EmpressOvipositor is likewise a Mob
        // that is not an Alien - she had exactly the same exposure.
        return passenger.getType() == com.alien.common.registry.init.AlienEntityTypes.OVIPOSITOR.get()
            || passenger.getType() == com.alien.common.registry.init.AlienEntityTypes.EMPRESS_OVIPOSITOR.get();
    }

    /**
     * Is this entity currently the recorded cargo of an alien captor - i.e. mid-haul between grab and handoff? The ONE
     * definition of "being carried home": vehicle is an Alien AND that captor's carry bookkeeping names this exact
     * entity. Both host protections key on it (no suffocation, no fighting back), so merely riding an alien never
     * grants either.
     */
    public static boolean isBeingCarriedHome(LivingEntity host) {
        return host.getVehicle() instanceof com.alien.common.gameplay.entity.living.alien.Alien captor
            && carriedHost(captor) == host;
    }

    /**
     * Entity types that have refused to be mounted, so the warning below fires once per type rather than once per grab
     * attempt.
     */
    private static final java.util.Set<net.minecraft.world.entity.EntityType<?>> REFUSED_MOUNT_TYPES =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Grab a host: it rides the captor and (if a mob) stops fighting back. Returns whether the grab actually TOOK.
     * <p>
     * ⚠⚠ {@code startRiding(captor, true)} CAN STILL REFUSE, AND USED TO BE IGNORED. Force bypasses vanilla's
     * {@code canRide}/{@code canAddPassenger} pair, but not the two gates ahead of it: {@code couldAcceptPassenger} and
     * the loader's mount event ({@code EntityMountEvent} on NeoForge, which any mod may cancel), nor a host that
     * overrides {@code startRiding} itself. The old code played the grab sound and returned regardless, so a host that
     * could not be mounted produced exactly the field report of Sep 22 (MineColonies citizens): the grab sound every
     * tick, the drone standing over its quarry, and nothing ever carried. The caller now writes such a host off, and
     * the refusal is logged once per entity type so the next report names the culprit.
     */
    public static boolean capture(com.alien.common.gameplay.entity.living.alien.Alien captor, LivingEntity host) {
        if ((!host.startRiding(captor, true) || host.getVehicle() != captor) && !forceMount(captor, host)) {
            if (REFUSED_MOUNT_TYPES.add(host.getType())) {
                Alien.LOGGER.warn(
                    "[hostdbg] {} could not be carried even by force (a mount event or the vehicle refused it). It "
                        + "cannot be captured as a host; drones will write it off and hunt something else.",
                    host.getType().getDescriptionId()
                );
            }

            return false;
        }

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

        return true;
    }

    /**
     * The mount vanilla's {@code startRiding(captor, true)} would have performed, done directly, for a host whose own
     * {@code startRiding} override refuses every vehicle it does not recognise. MineColonies citizens are the known
     * case (their override ignores {@code force} entirely - see {@code MixinEntity_ForceMountAccessor}), and this is
     * exactly the Sep 22 report: grab sound on loop, drone standing over the citizen, nothing ever carried.
     * <p>
     * Same sequence as {@code Entity.startRiding} after its gates: dismount anything current, stand, set the vehicle,
     * register on it. The vehicle's own {@code canAddPassenger} is still honoured - {@code Alien.tick} evicts any
     * passenger that fails {@code canEntityRideAlien} a tick later, so this cannot put a host on an alien that does not
     * carry hosts. Only used after the normal path has been refused.
     */
    private static boolean forceMount(com.alien.common.gameplay.entity.living.alien.Alien captor, LivingEntity host) {
        var vehicle = (com.alien.mixin.MixinEntity_ForceMountAccessor) captor;

        if (!vehicle.avp_alien$couldAcceptPassenger() || !vehicle.avp_alien$canAddPassenger(host)) {
            return false;
        }

        if (host.isPassenger()) {
            host.stopRiding();
        }

        host.setPose(net.minecraft.world.entity.Pose.STANDING);
        ((com.alien.mixin.MixinEntity_ForceMountAccessor) host).avp_alien$setVehicle(captor);
        ((com.alien.mixin.MixinEntity_ForceMountAccessor) captor).avp_alien$addPassenger(host);

        return host.getVehicle() == captor;
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

        // ⭐ Oct 1 - BUILD-FREE WEBS THE HOST IN PLACE. [stated] "webbing hosts inplace instead of sticking them to
        // already existing walls". A chamber spot is already a web cell; a build-free spot is open ground, so the web
        // is laid around the host's feet here, in the hive's own strain. Only into open air - never over a block.
        if (com.alien.common.gameplay.hive.config.BuildFreeMode.isEnabled() && !location.isEndStyleHive()) {
            var web = com.alien.common.gameplay.hive.config.BuildFreeClusters.strainWeb(location).defaultBlockState();
            for (var cell : new net.minecraft.core.BlockPos[] { spot.pos(), spot.pos().above() }) {
                if (level.getBlockState(cell).isAir()) {
                    level.setBlock(cell, web, 3);
                }
            }
        }
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
