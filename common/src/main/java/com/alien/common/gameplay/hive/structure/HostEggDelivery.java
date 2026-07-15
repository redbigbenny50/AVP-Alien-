package com.alien.common.gameplay.hive.structure;

import com.alien.common.gameplay.entity.living.alien.ovomorph.Ovomorph;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.model.alien.Host;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.alien.common.util.AlienPredicates;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Egg -> host delivery target finder (host-capture arc, half b). Answers a single question for the egg haul: "which
 * host-chamber cell should the next egg go to?" It returns the {@code eggDropFor} cell in front of an embedded host
 * that is ready to receive an egg. The egg then opens on its own (an ovomorph raises its hatch desire next to a free
 * host) and the emerged facehugger attaches - both handled by existing ovomorph/facehugger systems, not here.
 * <p>
 * The settle delay is measured from the host's persisted embed game-time ({@link Host#getEmbedGameTime()}, stamped by
 * {@link HostParking#embed}), so the countdown starts at the actual embed and survives reloads.
 * <p>
 * [Flag for teammate review: reads the host/parasite lifecycle.]
 */
public final class HostEggDelivery {

    private HostEggDelivery() {}

    /** Wait this long after a host is embedded before an egg is routed to it (host may still be arriving). */
    private static final long SETTLE_TICKS = 20L * 20L;

    private static final double HOST_SCAN_RADIUS = 0.5;

    private static final double EGG_DROP_SCAN_RADIUS = 0.5;

    /**
     * How far from a host's egg-drop cell a loose or carried egg counts as "one is already on the way".
     * <p>
     * MUST match the ferry's inbound radius. An egg the ferry un-roots starts life across the hive and takes seconds to
     * arrive; if this gate only looked AT the drop cell (as it once did) it would report the host still waiting and
     * trigger another release every cadence, draining the whole nursery.
     */
    private static final double INBOUND_EGG_RADIUS = 48.0;

    /**
     * FERRY query - "does any host need a NEW egg released?" A free host (alive, un-implanted, no parasite) settled at
     * least {@link #SETTLE_TICKS} ago whose drop cell has no ovomorph AND none inbound. The inbound test is the
     * anti-drain guard: if an egg is already loose or being carried toward the host, no new one is released.
     * <p>
     * Do NOT use this to route a carrier that is ALREADY holding an egg - the carrier's own egg counts as "inbound" and
     * hides the very destination it is looking for. Use {@link #findHostDropForCarrier} for that.
     */
    public static Optional<BlockPos> findAwaitingHostEggDrop(ServerLevel level, HiveLocation location) {
        return findHostDrop(level, location, null);
    }

    /**
     * ROUTING query - "where do I, this carrier, take the egg I am holding?" Identical to the ferry query EXCEPT the
     * carrier's own egg is ignored when testing for an inbound egg. Without this, a hauler that picks up a host egg
     * asks where to deliver it, its own egg trips the inbound guard, the method returns empty, and the hauler loses its
     * destination and mills around unable to reach the chamber - the "too confused to even vent toward the exits"
     * regression the anti-drain guard introduced.
     */
    public static Optional<BlockPos> findHostDropForCarrier(ServerLevel level, HiveLocation location, Ovomorph carriedEgg) {
        return findHostDrop(level, location, carriedEgg);
    }

    /**
     * Shared core. {@code ignoreEgg}, when non-null, is excluded from the inbound-egg test - so a carrier can find the
     * host its OWN egg is bound for, while the ferry (passing null) still refuses to release a duplicate.
     */
    private static Optional<BlockPos> findHostDrop(ServerLevel level, HiveLocation location, @Nullable Ovomorph ignoreEgg) {
        long now = level.getGameTime();
        for (var origin : HostChamberSlots.hostChamberGroups(location)) {
            for (var spot : HostChamberSlots.hostSpots(level, location, origin)) {
                var host = freeHostAt(level, spot.pos());
                if (host == null) {
                    continue;
                }
                var eggDrop = HostChamberSlots.eggDropFor(spot);
                if (hasOvomorphAt(level, eggDrop) || hasInboundEgg(level, eggDrop, ignoreEgg)) {
                    continue; // already has its egg, or a DIFFERENT egg is loose and on the way
                }
                long embedTime = ((Host) host).getEmbedGameTime();
                if (embedTime != Long.MIN_VALUE && now - embedTime >= SETTLE_TICKS) {
                    return Optional.of(eggDrop);
                }
            }
        }
        return Optional.empty();
    }

    private static @Nullable LivingEntity freeHostAt(ServerLevel level, BlockPos spot) {
        var box = new AABB(spot).inflate(HOST_SCAN_RADIUS);
        for (var entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity.getType().is(AlienEntityTypeTags.ALIENS)) {
                continue;
            }
            if (
                AlienPredicates.isHost(entity)
                    && entity.isAlive()
                    && !AlienPredicates.hasEmbryo(entity)
                    && !hasParasitePassenger(entity)
            ) {
                return entity;
            }
        }
        return null;
    }

    private static boolean hasParasitePassenger(LivingEntity host) {
        for (var passenger : host.getPassengers()) {
            if (passenger.getType().is(AlienEntityTypeTags.PARASITES)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasOvomorphAt(ServerLevel level, BlockPos pos) {
        return !level.getEntitiesOfClass(Ovomorph.class, new AABB(pos).inflate(EGG_DROP_SCAN_RADIUS)).isEmpty();
    }

    /**
     * Is an egg (other than {@code ignoreEgg}) already loose or being carried toward this drop cell? Then no NEW egg is
     * needed. {@code ignoreEgg} lets a carrier discount the very egg it is holding, so it does not hide its own
     * destination from itself.
     */
    private static boolean hasInboundEgg(ServerLevel level, BlockPos eggDrop, @Nullable Ovomorph ignoreEgg) {
        var box = new AABB(eggDrop).inflate(INBOUND_EGG_RADIUS);
        return !level.getEntitiesOfClass(
            Ovomorph.class,
            box,
            egg -> egg != ignoreEgg && egg.isAlive() && (!egg.isRooted.get() || egg.isPassenger())
        ).isEmpty();
    }
}
