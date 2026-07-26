package com.alien.common.gameplay.hive.party;

import com.alien.Alien;
import com.alien.common.gameplay.hive.config.HiveConfig;
import com.alien.common.gameplay.hive.location.HiveLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;

/**
 * Per-tick (piggybacking {@code HiveLocationLoadedTickTask}'s 20-tick cadence) resolution for
 * {@link HiveParty.AttackParty}. Resolves when either the fixed {@code config.attackPartyDurationTicks()} active
 * duration elapses, or the target player is confirmed dead/offline-and-removed — whichever comes first. On resolution,
 * survivors instant-teleport to the nearest hive vent (the fast-travel network) before refunding to reserves.
 * <p>
 * Mirrors {@link BiomassHuntingPartyLifecycleTask}; the only behavioral addition is the early target-death exit. The
 * per-target dispatch cooldown is NOT touched here — it was set at dispatch time and must persist past resolution.
 */
public final class AttackPartyLifecycleTask {

    private AttackPartyLifecycleTask() {}

    public static void run(MinecraftServer server, HiveLocation location, HiveConfig config) {
        if (location.parties().isEmpty()) {
            return;
        }

        var serverLevel = server.getLevel(location.dimension());
        if (serverLevel == null) {
            return;
        }

        var currentTick = serverLevel.getGameTime();

        var iterator = location.parties().iterator();
        while (iterator.hasNext()) {
            var party = iterator.next();
            if (!(party instanceof HiveParty.AttackParty attackParty)) {
                continue;
            }

            var durationElapsed = currentTick - attackParty.dispatchedTick() >= config.attackPartyDurationTicks();
            var targetGone = isTargetGone(serverLevel, attackParty);

            if (!durationElapsed && !targetGone) {
                continue;
            }

            resolve(serverLevel, location, attackParty, config);
            iterator.remove();
        }
    }

    /** True if the target player is dead or no longer present in this dimension (so there's nothing left to hunt). */
    private static boolean isTargetGone(ServerLevel serverLevel, HiveParty.AttackParty party) {
        var target = serverLevel.getPlayerByUUID(party.targetPlayerId());
        return target == null || !target.isAlive();
    }

    private static void resolve(
        ServerLevel serverLevel,
        HiveLocation location,
        HiveParty.AttackParty party,
        HiveConfig config
    ) {
        // Campaign clearing (territorial-intrusion model): the hive stops hunting a player when EITHER a wave kills
        // them (targetGone via death), OR they survive the second wave's full duration. A player who dies to wave 1 is
        // done immediately; a player who outlasts wave 2 is done. Surviving wave 1 alone does NOT clear — wave 2 still
        // comes.
        var campaign = location.attackCampaigns().get(party.targetPlayerId());
        if (campaign != null) {
            var target = serverLevel.getPlayerByUUID(party.targetPlayerId());
            var targetDead = target == null || !target.isAlive();
            if (targetDead || campaign.wavesSent() >= 2) {
                campaign.markCleared();
                Alien.LOGGER.info(
                    "Hive: retribution campaign against player {} cleared at location {} ({})",
                    party.targetPlayerId(),
                    location.id(),
                    targetDead ? "target defeated" : "survived final wave"
                );
            }
        }

        var homeVent = nearestVent(serverLevel, location, config);

        for (var entry : new ArrayList<>(party.materializedMembers().entrySet())) {
            var entity = serverLevel.getEntity(entry.getKey());
            party.untrackMaterializedMember(entry.getKey());
            if (entity == null) {
                // Not loaded - NOT dead (real deaths are untracked in PartyMemberDeath). Refund it: writing
                // off every out-of-range member is what quietly drained the hive on each dispatch.
                location.localReserves().addReturningMember(entry.getValue(), 1);
                continue;
            }
            if (!entity.isAlive()) {
                continue; // died this tick, before its death hook untracked it
            }

            if (homeVent != null) {
                entity.teleportTo(homeVent.getX() + 0.5, homeVent.getY(), homeVent.getZ() + 0.5);
            }

            location.localReserves().addReturningMember(entry.getValue(), 1);
            if (entity instanceof com.alien.common.gameplay.entity.living.alien.Alien alien) {
                alien.clearPartyMembership();
            }
            // EGG DUTY: a carrier is refunded but NEVER discarded - discarding it mid-haul vanished the
            // worker and dropped its egg. It stays alive to finish the delivery.
            if (EggDutyGuard.isOnEggDuty(entity)) {
                continue;
            }
            entity.discard();
        }

        for (var type : new ArrayList<>(party.composition().getAvailableEntityTypes())) {
            var count = party.composition().getCount(type);
            if (count <= 0) {
                continue;
            }
            location.localReserves().addReturningMember(type, count);
            party.composition().add(type, -count);
        }

        Alien.LOGGER.info("Hive: attack party resolved for location {} (target {})", location.id(), party.targetPlayerId());
    }

    private static BlockPos nearestVent(ServerLevel serverLevel, HiveLocation location, HiveConfig config) {
        // Home is any door onto the world - surface or frontier. No fallback to interior ducts: survivors cannot
        // walk home to a vent buried in the rock.
        var surfaceVents = PartyVentUtil.findPartyVents(serverLevel, location);
        if (!surfaceVents.isEmpty()) {
            return closestTo(surfaceVents, location.centerPos());
        }
        return null;
    }

    private static BlockPos closestTo(java.util.List<BlockPos> candidates, BlockPos reference) {
        BlockPos closest = null;
        var closestDistSqr = Double.MAX_VALUE;
        for (var candidate : candidates) {
            var distSqr = candidate.distSqr(reference);
            if (distSqr < closestDistSqr) {
                closestDistSqr = distSqr;
                closest = candidate;
            }
        }
        return closest;
    }
}
