package com.alien.common.gameplay.hive.convoy;

import com.alien.Alien;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.blib.api.common.faction.v1.FactionMember;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;

public final class ConvoyMemberTracker {

    private ConvoyMemberTracker() {}

    public static void markSpawned(Convoy convoy, Entity entity) {
        convoy.trackMaterializedMember(entity.getUUID(), entity.getType());

        var faction = Alien.MOD.factions().get(convoy.lineageFactionId());
        if (faction != null && !faction.membership().hasMember(FactionMember.entity(entity))) {
            faction.membership().addEntity(entity);
        }

        if (entity instanceof com.alien.common.gameplay.entity.living.alien.Alien alien) {
            alien.setConvoyMembership(new ConvoyMembership(convoy.lineageFactionId(), convoy.id()));
        }
        markDirty(new ConvoyMembership(convoy.lineageFactionId(), convoy.id()));
    }

    public static boolean returnDespawned(com.alien.common.gameplay.entity.living.alien.Alien alien) {
        return returnToReserves(alien, "despawned");
    }

    public static boolean returnUnloaded(com.alien.common.gameplay.entity.living.alien.Alien alien) {
        return returnToReserves(alien, "unloaded");
    }

    public static boolean isRaidMember(com.alien.common.gameplay.entity.living.alien.Alien alien) {
        var membership = alien.convoyMembership();
        return membership != null && findConvoy(membership) instanceof Convoy.Raid;
    }

    public static void markJoinedRaid(Convoy.Raid raid, com.alien.common.gameplay.entity.living.alien.Alien alien) {
        markSpawned(raid, alien);
        raid.incrementFrenziedJoinCount();
    }

    public static boolean isNearActiveRaidContext(Entity entity, double radiusBlocks) {
        if (radiusBlocks <= 0.0 || entity.level().isClientSide()) {
            return false;
        }

        var radiusSqr = radiusBlocks * radiusBlocks;
        for (var factionId : new ArrayList<>(Alien.MOD.factions().getAllIds())) {
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage) || !lineage.isAlive()) {
                continue;
            }

            for (var convoy : lineage.convoys()) {
                if (!(convoy instanceof Convoy.Raid raid) || raid.returningHome()) {
                    continue;
                }
                if (!raid.dimension().equals(entity.level().dimension())) {
                    continue;
                }
                if (entity.position().distanceToSqr(raid.currentPos()) <= radiusSqr) {
                    return true;
                }
                if (entity.position().distanceToSqr(raid.lastKnownTargetPos().getCenter()) <= radiusSqr) {
                    return true;
                }
                if (isNearMaterializedRaidMember(entity, raid, radiusSqr)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isNearMaterializedRaidMember(Entity entity, Convoy.Raid raid, double radiusSqr) {
        var level = entity.getServer() == null ? null : entity.getServer().getLevel(raid.dimension());
        if (level == null) {
            return false;
        }

        for (var memberId : raid.materializedMembers().keySet()) {
            var member = level.getEntity(memberId);
            if (member != null && member != entity && member.position().distanceToSqr(entity.position()) <= radiusSqr) {
                return true;
            }
        }
        return false;
    }

    private static boolean returnToReserves(com.alien.common.gameplay.entity.living.alien.Alien alien, String reason) {
        var membership = alien.convoyMembership();
        if (membership == null) {
            return false;
        }

        var convoy = findConvoy(membership);
        if (convoy == null) {
            alien.clearConvoyMembership();
            return false;
        }

        var trackedType = convoy.materializedMembers().get(alien.getUUID());
        if (trackedType == null) {
            alien.clearConvoyMembership();
            return false;
        }

        convoy.composition().add(trackedType, 1);
        convoy.untrackMaterializedMember(alien.getUUID());
        markDirty(membership);
        alien.clearConvoyMembership();

        Alien.LOGGER.info(
            "Hive: convoy member {} {} - returned {} to convoy {} reserves",
            alien.getUUID(),
            reason,
            trackedType,
            convoy.id()
        );
        return true;
    }

    public static void unregisterKilled(com.alien.common.gameplay.entity.living.alien.Alien alien) {
        var membership = alien.convoyMembership();
        if (membership == null) {
            return;
        }

        var convoy = findConvoy(membership);
        if (convoy != null) {
            convoy.untrackMaterializedMember(alien.getUUID());
            markDirty(membership);
        }
        alien.clearConvoyMembership();
    }

    public static boolean discardStaleLoadedMember(com.alien.common.gameplay.entity.living.alien.Alien alien) {
        var membership = alien.convoyMembership();
        if (membership == null) {
            return false;
        }

        var convoy = findConvoy(membership);
        if (convoy != null && convoy.materializedMembers().containsKey(alien.getUUID())) {
            return false;
        }

        alien.clearConvoyMembership();
        Alien.LOGGER.info(
            "Hive: discarding stale loaded convoy member {} for convoy {}",
            alien.getUUID(),
            membership.convoyId()
        );
        return true;
    }

    public static int returnMissingMaterializedMembers(MinecraftServer server, Convoy convoy) {
        if (convoy.materializedMembers().isEmpty()) {
            return 0;
        }

        var level = server.getLevel(convoy.dimension());
        var returned = 0;
        for (var entry : new ArrayList<>(convoy.materializedMembers().entrySet())) {
            if (level != null && level.getEntity(entry.getKey()) != null) {
                continue;
            }
            convoy.composition().add(entry.getValue(), 1);
            convoy.untrackMaterializedMember(entry.getKey());
            returned++;
        }

        if (returned > 0) {
            markDirty(new ConvoyMembership(convoy.lineageFactionId(), convoy.id()));
            Alien.LOGGER.info(
                "Hive: returned {} missing materialized member(s) to convoy {} reserves",
                returned,
                convoy.id()
            );
        }
        return returned;
    }

    /**
     * How long a hunting raider may go without moving, hurting anything or being hurt before it is written off. 90s.
     */
    private static final long STALLED_TICKS = 1800L;

    /** Movement below this is navigation jitter, not progress. */
    private static final double STALL_MOVE_EPSILON_SQUARED = 4.0;

    private static final int STALL_CHECK_INTERVAL_TICKS = 40;

    /**
     * Transient - a restart forgets the watch, so the next stall is measured from scratch rather than punished early.
     */
    private static final java.util.Map<java.util.UUID, StallWatch> STALL_WATCH = new java.util.HashMap<>();

    private record StallWatch(
        net.minecraft.world.phys.Vec3 position,
        float health,
        int lastHurtByStamp,
        int lastHurtMobStamp,
        long sinceTick
    ) {}

    /**
     * ⭐ THE STUCK-ATTACKER GATE (fix 2 of [[raid-stall]]). {@code ConvoyArrival.arriveRaid} will not throw the next
     * wave until {@code materializedMembers()} is EMPTY, so one raider wedged in terrain - alive, in range, hunting
     * nobody - paused the whole campaign until RAID_SPENT or TARGET_ABSENT eventually retired it. Mirrors
     * {@code StrandedMemberRecovery} (same 90s, same "did anything change" test) but keyed on stalled-while-HUNTING: a
     * raider that has neither moved, dealt damage nor taken damage for 90 seconds is banked back into the convoy
     * composition and removed, and the next wave fires. A raider that is fighting, chasing or being shot at never
     * qualifies, because every one of those changes a value we watch.
     *
     * @return how many raiders were banked
     */
    public static int returnStalledRaiders(MinecraftServer server, Convoy.Raid raid, long now) {
        if (raid.materializedMembers().isEmpty() || now % STALL_CHECK_INTERVAL_TICKS != 0) {
            return 0;
        }

        var level = server.getLevel(raid.dimension());
        if (level == null) {
            return 0;
        }

        var returned = 0;
        for (var entry : new ArrayList<>(raid.materializedMembers().entrySet())) {
            var id = entry.getKey();
            if (!(level.getEntity(id) instanceof net.minecraft.world.entity.LivingEntity living) || !living.isAlive()) {
                STALL_WATCH.remove(id);
                continue; // missing/dead members are the missing-member sweep's business, not ours
            }

            var watch = STALL_WATCH.get(id);
            var moved = watch == null || living.position().distanceToSqr(watch.position()) > STALL_MOVE_EPSILON_SQUARED;
            var changed = watch == null
                || moved
                || living.getHealth() != watch.health()
                || living.getLastHurtByMobTimestamp() != watch.lastHurtByStamp()
                || living.getLastHurtMobTimestamp() != watch.lastHurtMobStamp();

            if (changed) {
                STALL_WATCH.put(
                    id,
                    new StallWatch(
                        living.position(),
                        living.getHealth(),
                        living.getLastHurtByMobTimestamp(),
                        living.getLastHurtMobTimestamp(),
                        now
                    )
                );
                continue;
            }

            if (now - watch.sinceTick() < STALLED_TICKS) {
                continue;
            }

            if (living instanceof com.alien.common.gameplay.entity.living.alien.xenomorph.carrier.Carrier carrier) {
                carrier.releaseAllFacehuggers();
            }
            if (living instanceof com.alien.common.gameplay.entity.living.alien.Alien alien) {
                alien.clearConvoyMembership();
            }
            living.discard();
            raid.composition().add(entry.getValue(), 1);
            raid.untrackMaterializedMember(id);
            STALL_WATCH.remove(id);
            returned++;
            Alien.LOGGER.info(
                "Hive: raid {} raider {} stalled for {}s while hunting (no movement, no damage dealt or taken) - banked back into the raid so the next wave can go",
                raid.id(),
                entry.getValue(),
                STALLED_TICKS / 20L
            );
        }

        if (returned > 0) {
            markDirty(new ConvoyMembership(raid.lineageFactionId(), raid.id()));
        }
        return returned;
    }

    public static int returnDistantMaterializedMembers(MinecraftServer server, Convoy convoy, double maxDistanceSqr) {
        if (convoy.materializedMembers().isEmpty()) {
            return 0;
        }

        var level = server.getLevel(convoy.dimension());
        var returned = 0;
        for (var entry : new ArrayList<>(convoy.materializedMembers().entrySet())) {
            var entity = level == null ? null : level.getEntity(entry.getKey());
            if (entity != null && entity.position().distanceToSqr(convoy.currentPos()) <= maxDistanceSqr) {
                continue;
            }
            if (entity instanceof com.alien.common.gameplay.entity.living.alien.Alien alien) {
                alien.clearConvoyMembership();
            }
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
            convoy.composition().add(entry.getValue(), 1);
            convoy.untrackMaterializedMember(entry.getKey());
            returned++;
        }

        if (returned > 0) {
            markDirty(new ConvoyMembership(convoy.lineageFactionId(), convoy.id()));
            Alien.LOGGER.info(
                "Hive: returned {} distant materialized member(s) to convoy {} reserves",
                returned,
                convoy.id()
            );
        }
        return returned;
    }

    public static int recallMaterializedMembers(MinecraftServer server, Convoy convoy) {
        var level = server.getLevel(convoy.dimension());
        var hadTrackedMembers = !convoy.materializedMembers().isEmpty();
        var recalled = 0;

        for (var entry : new ArrayList<>(convoy.materializedMembers().entrySet())) {
            var entity = level == null ? null : level.getEntity(entry.getKey());
            if (entity != null && entity.isAlive() && !entity.isRemoved()) {
                if (entity instanceof com.alien.common.gameplay.entity.living.alien.xenomorph.carrier.Carrier carrier) {
                    carrier.releaseAllFacehuggers();
                }
                if (entity instanceof com.alien.common.gameplay.entity.living.alien.Alien alien) {
                    alien.clearConvoyMembership();
                }
                entity.discard();
            }
            convoy.composition().add(entry.getValue(), 1);
            recalled++;
            convoy.untrackMaterializedMember(entry.getKey());
        }

        if (hadTrackedMembers) {
            markDirty(new ConvoyMembership(convoy.lineageFactionId(), convoy.id()));
        }
        return recalled;
    }

    private static Convoy findConvoy(ConvoyMembership membership) {
        var faction = Alien.MOD.factions().get(membership.lineageFactionId());
        if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
            return null;
        }

        for (var convoy : lineage.convoys()) {
            if (convoy.id().equals(membership.convoyId())) {
                return convoy;
            }
        }
        return null;
    }

    private static void markDirty(ConvoyMembership membership) {
        var faction = Alien.MOD.factions().get(membership.lineageFactionId());
        if (faction != null && faction.data() instanceof LineageFactionData lineage) {
            lineage.markDirty();
        }
    }
}
