package com.alien.common.gameplay.hive.tick;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.alien.common.util.AlienPredicates;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Retargets loaded hive xenomorphs when a threat is present in the location's claimed chunks. Covers both trespassing
 * players and hated non-player enemies (marines, predators — anything tagged {@code HATED_BY_XENOMORPHS} or
 * {@code XENOMORPH_THREAT_3_HIGH_DANGER}), so a hive defends its territory against any high-priority intruder, not just
 * players.
 */
public final class HiveTerritoryAggroTask {

    private static final long INTERVAL_TICKS = 20L;

    /**
     * A player's dwell only accrues while they've damaged a member within this window — keeps "fighting" distinct from
     * "passing through".
     */
    private static final long HOSTILE_RECENCY_TICKS = 100L; // 5s

    private HiveTerritoryAggroTask() {}

    public static boolean shouldFire(long currentTick) {
        return currentTick % INTERVAL_TICKS == 0L;
    }

    public static void run(ServerLevel level, HiveLocation location) {
        var intruders = intrudersInTerritory(level, location);

        trackIntrusionDwell(level, location, intruders);

        if (intruders.isEmpty()) {
            return;
        }

        for (var entry : location.loadedMembersByType().entrySet()) {
            if (!entry.getKey().is(AlienEntityTypeTags.XENOMORPHS)) {
                continue;
            }

            aggroMembers(level, entry.getValue(), intruders);
        }
    }

    /**
     * Accrues per-player in-claim-while-recently-hostile dwell time and, once it crosses the intrusion threshold,
     * begins a two-wave retribution {@link com.alien.common.gameplay.hive.party.AttackCampaign}. A player must be both
     * physically inside a claimed chunk AND have damaged a member recently (within {@link #HOSTILE_RECENCY_TICKS}) for
     * dwell to accrue — merely passing through peacefully doesn't count. A player who has intruded, had their campaign
     * cleared, then re-enters and re-engages starts a fresh campaign (the prior cleared state is overwritten).
     */
    private static void trackIntrusionDwell(ServerLevel level, HiveLocation location, List<LivingEntity> intruders) {
        var currentTick = level.getGameTime();
        var config = com.alien.common.gameplay.hive.location.HiveLocationRegistry.INSTANCE.config();
        var dwellThreshold = config.attackIntrusionDwellTicks();

        for (var intruder : intruders) {
            if (!(intruder instanceof net.minecraft.server.level.ServerPlayer player)) {
                continue;
            }
            var campaign = location.attackCampaigns().get(player.getUUID());
            if (campaign == null) {
                // No hostile hit recorded yet (recordAttackByPlayer creates the entry on first hit) — a player merely
                // standing in-claim without having attacked accrues nothing.
                continue;
            }

            // Only accrue while the player has been hostile recently; a cleared campaign accrues toward a fresh one.
            var recentlyHostile = currentTick - campaign.lastHostileTick() <= HOSTILE_RECENCY_TICKS;
            if (!recentlyHostile) {
                continue;
            }

            if (campaign.campaignActive()) {
                // Active campaign already running — no re-accrual until it clears.
                continue;
            }

            if (campaign.cleared()) {
                // Re-intrusion after a prior campaign cleared: this hit already reset lastHostileTick; start dwell
                // over.
                campaign.resetDwell();
                // Un-clear by beginning fresh dwell accrual (campaign starts once threshold re-crossed below).
                // We leave `cleared` true until the threshold is actually re-crossed, so a single stray hit post-clear
                // doesn't immediately re-arm; beginCampaign() resets the flag.
            }

            campaign.addDwellTicks(INTERVAL_TICKS);

            if (campaign.dwellTicks() >= dwellThreshold) {
                campaign.beginCampaign(currentTick);
                com.alien.Alien.LOGGER.info(
                    "Hive: player {} intrusion threshold crossed at location {} — retribution campaign armed",
                    player.getUUID(),
                    location.id()
                );
            }
        }
    }

    private static List<LivingEntity> intrudersInTerritory(ServerLevel level, HiveLocation location) {
        var intruders = new ArrayList<LivingEntity>();

        // Players first (cheap — scan the level player list directly).
        for (var player : level.players()) {
            if (location.claimedChunks().contains(new ChunkPos(player.blockPosition()))) {
                intruders.add(player);
            }
        }

        // Hated non-player enemies (marines, predators, etc.) — scan each claimed chunk's column for tagged threats.
        for (var chunk : location.claimedChunks()) {
            var box = new AABB(
                chunk.getMinBlockX(),
                level.getMinBuildHeight(),
                chunk.getMinBlockZ(),
                chunk.getMaxBlockX() + 1,
                level.getMaxBuildHeight(),
                chunk.getMaxBlockZ() + 1
            );
            for (var entity : level.getEntitiesOfClass(LivingEntity.class, box, HiveTerritoryAggroTask::isHatedNonPlayer)) {
                intruders.add(entity);
            }
        }

        return intruders;
    }

    private static boolean isHatedNonPlayer(LivingEntity entity) {
        if (!entity.isAlive() || entity.isRemoved()) {
            return false;
        }
        return entity.getType().is(AlienEntityTypeTags.HATED_BY_XENOMORPHS)
            || entity.getType().is(AlienEntityTypeTags.XENOMORPH_THREAT_3_HIGH_DANGER);
    }

    private static void aggroMembers(ServerLevel level, Set<UUID> memberIds, List<LivingEntity> intruders) {
        for (var memberId : memberIds) {
            var entity = level.getEntity(memberId);
            if (!(entity instanceof Xenomorph xenomorph) || !xenomorph.isAlive() || xenomorph.isRemoved()) {
                continue;
            }

            var target = nearestTarget(xenomorph, intruders);
            if (target != null) {
                xenomorph.setHiveIntruderTarget(target);
            }
        }
    }

    private static @Nullable LivingEntity nearestTarget(Xenomorph xenomorph, List<LivingEntity> intruders) {
        LivingEntity nearest = null;
        var nearestDistanceSqr = Double.MAX_VALUE;

        for (var intruder : intruders) {
            if (!AlienPredicates.canAcquireTarget(xenomorph, intruder)) {
                continue;
            }

            var distanceSqr = xenomorph.distanceToSqr(intruder);
            if (distanceSqr < nearestDistanceSqr) {
                nearest = intruder;
                nearestDistanceSqr = distanceSqr;
            }
        }

        return nearest;
    }
}
