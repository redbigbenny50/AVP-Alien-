package com.alien.common.gameplay.hive.party;

import com.alien.Alien;
import com.alien.common.gameplay.hive.config.HiveConfig;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.spawning.ReserveSpawnUtil;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.entity.v1.EntityReserves;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Dispatch for {@link HiveParty.AttackParty} — retribution against a player who breached this hive location's claim and
 * lingered while fighting (the territorial-intrusion model in {@code AttackCampaign}). Vent-dependent like
 * {@link BiomassHuntingPartyDispatch}. Fires the campaign's scheduled waves: wave 1 one MC day after the intrusion,
 * wave 2 one cooldown period after wave 1. Wave sequencing lives on the per-player {@code AttackCampaign} in
 * {@code HiveLocation#attackCampaigns}.
 */
public final class AttackPartyDispatch {

    private AttackPartyDispatch() {}

    public static void tryRun(MinecraftServer server, HiveLocation location, HiveConfig config) {
        var serverLevel = server.getLevel(location.dimension());
        if (serverLevel == null) {
            return;
        }

        var currentTick = serverLevel.getGameTime();
        var target = pickTarget(location, config, currentTick);
        if (target == null) {
            return;
        }
        var campaign = location.attackCampaigns().get(target);

        // Attack parties are going out to hit something. Any door onto the world will do - surface or frontier.
        var surfaceVents = PartyVentUtil.findPartyVents(serverLevel, location);
        if (surfaceVents.isEmpty()) {
            return;
        }
        var spawnPos = surfaceVents.get(serverLevel.random.nextInt(surfaceVents.size()));

        var desiredSize = Math.max(
                1,
                Math.round(
                        config.attackPartyBaseSize() + config.attackPartySizePerClaimedChunk() * location.claimedChunks().size()
                )
        );

        var composition = drainComposition(location, (int) desiredSize);
        if (composition.getCount() <= 0) {
            return;
        }

        var party = new HiveParty.AttackParty(
                HivePartyId.fresh(),
                location.id(),
                location.dimension(),
                composition,
                currentTick,
                target
        );

        var spawnedCount = materialize(serverLevel, party, spawnPos);
        if (spawnedCount <= 0) {
            refund(location, composition);
            return;
        }

        // Advance the campaign's wave counter — wave 1 (a day after intrusion) or wave 2 (a cooldown after wave 1).
        campaign.recordWaveSent(currentTick);

        var targetPlayer = serverLevel.getPlayerByUUID(target);
        if (targetPlayer != null) {
            for (var memberId : party.materializedMembers().keySet()) {
                var entity = serverLevel.getEntity(memberId);
                if (entity instanceof com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph xenomorph) {
                    xenomorph.setHiveIntruderTarget(targetPlayer);
                }
            }
        }

        location.parties().add(party);
        Alien.LOGGER.info(
                "Hive: dispatched attack party (wave {}) for location {} — {} members targeting player {}",
                campaign.wavesSent(),
                location.id(),
                spawnedCount,
                target
        );
    }

    /**
     * First player whose retribution campaign has a wave due right now: campaign active (intruded, not yet cleared),
     * the scheduled delay for its next wave has elapsed (wave 1 = 1 MC day after intrusion; wave 2 = one cooldown
     * period after wave 1), fewer than 2 waves sent, and no AttackParty already out against them.
     */
    private static UUID pickTarget(HiveLocation location, HiveConfig config, long currentTick) {
        for (var entry : location.attackCampaigns().entrySet()) {
            var playerId = entry.getKey();
            var campaign = entry.getValue();

            if (!campaign.campaignActive() || campaign.wavesSent() >= 2) {
                continue;
            }

            var waveDue = campaign.wavesSent() == 0
                    ? currentTick - campaign.intrusionTick() >= config.attackPartyWave1DelayTicks()
                    : currentTick - campaign.lastWaveTick() >= config.attackPartyCooldownTicks();
            if (!waveDue) {
                continue;
            }

            var alreadyTargeted = false;
            for (var party : location.parties()) {
                if (party instanceof HiveParty.AttackParty attackParty && attackParty.targetPlayerId().equals(playerId)) {
                    alreadyTargeted = true;
                    break;
                }
            }
            if (alreadyTargeted) {
                continue;
            }

            return playerId;
        }
        return null;
    }

    private static EntityReserves drainComposition(HiveLocation location, int desiredCount) {
        var reserves = location.localReserves();
        var composition = new EntityReserves();

        var candidateTypes = new ArrayList<EntityType<?>>();
        for (var type : reserves.getAvailableEntityTypes()) {
            if (
                    type.is(AlienEntityTypeTags.WARRIORS)
                            || type.is(AlienEntityTypeTags.PROWLERS)
                            || type.is(AlienEntityTypeTags.CRUSHERS)
                            || type.is(AlienEntityTypeTags.PRAETORIANS)
            ) {
                candidateTypes.add(type);
            }
        }
        if (candidateTypes.isEmpty()) {
            return composition;
        }

        var drained = 0;
        while (drained < desiredCount) {
            var progressedThisPass = false;
            for (var type : candidateTypes) {
                if (drained >= desiredCount) {
                    break;
                }
                if (reserves.trySpawn(type)) {
                    composition.add(type, 1);
                    drained++;
                    progressedThisPass = true;
                }
            }
            if (!progressedThisPass) {
                break;
            }
        }

        return composition;
    }

    private static void refund(HiveLocation location, EntityReserves composition) {
        for (var type : new ArrayList<>(composition.getAvailableEntityTypes())) {
            var count = composition.getCount(type);
            if (count <= 0) {
                continue;
            }
            location.localReserves().addReturningMember(type, count);
            composition.add(type, -count);
        }
    }

    private static int materialize(ServerLevel level, HiveParty.AttackParty party, BlockPos spawnPos) {
        var spawnedCount = 0;
        for (var type : new ArrayList<>(party.composition().getAvailableEntityTypes())) {
            var count = party.composition().getCount(type);
            for (var i = 0; i < count; i++) {
                var entity = type.create(level);
                if (entity == null) {
                    continue;
                }
                var jitterX = spawnPos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 2.0;
                var jitterZ = spawnPos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 2.0;
                entity.moveTo(jitterX, spawnPos.getY(), jitterZ, level.random.nextFloat() * 360.0F, 0.0F);
                if (entity instanceof Mob mob) {
                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.MOB_SUMMONED, null);
                    mob.setPersistenceRequired();
                }
                level.addFreshEntityWithPassengers(entity);
                ReserveSpawnUtil.markSpawnedFromReserves(entity);
                if (entity instanceof com.alien.common.gameplay.entity.living.alien.Alien alien) {
                    alien.setPartyMembership(new PartyMembership(party.sourceLocationId(), party.id()));
                }
                party.trackMaterializedMember(entity.getUUID(), type);
                party.composition().add(type, -1);
                spawnedCount++;
            }
        }
        return spawnedCount;
    }
}