package com.alien.common.gameplay.hive.tick;

import com.alien.Alien;
import com.alien.common.data.AlienAdvancements;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.config.HiveConfig;
import com.alien.common.gameplay.hive.convoy.Convoy;
import com.alien.common.gameplay.hive.convoy.Convoy.Raid.ReturnHomeReason;
import com.alien.common.gameplay.hive.convoy.ConvoyArrival;
import com.alien.common.gameplay.hive.convoy.ConvoyBossBars;
import com.alien.common.gameplay.hive.convoy.ConvoyId;
import com.alien.common.gameplay.hive.convoy.ConvoyInterception;
import com.alien.common.gameplay.hive.convoy.ConvoyMemberTracker;
import com.alien.common.gameplay.hive.convoy.RaidDispatch;
import com.alien.common.gameplay.hive.convoy.ConvoyTravel;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienMobEffects;
import com.alien.common.registry.init.AlienSoundEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

/**
 * Per-server-tick driver for in-flight convoys. Walks every loaded lineage's convoy list, advances each convoy's
 * position via {@link ConvoyTravel#tick}, and removes any that have arrived (firing their type-specific arrival effect
 * via {@link ConvoyArrival#checkArrival}).
 * <p>
 * Convoys are sparse — a busy lineage might have a handful active at any moment — so this is cheap. Travel is a single
 * Vec3 lerp per convoy.
 * <p>
 * See {@code HIVE_REDESIGN_12_PERFORMANCE.md} § 1.
 */
public final class LineageConvoyTickTask {

    private static final long RAID_WARNING_LEAD_TICKS = 20L * 60L;

    private static final int MARKED_FOR_DEATH_ACTIVE_RAID_TICKS = 20 * 10;

    private static final int MARKED_FOR_DEATH_DURATION_DRIFT_TICKS = 20;

    private static final double RAID_FRENZY_JOIN_CONTEXT_RADIUS_BLOCKS = 32.0D;

    private LineageConvoyTickTask() {}

    public static void run(MinecraftServer server) {
        var config = HiveLocationRegistry.INSTANCE.config();
        var currentTick = server.overworld().getGameTime();
        var activeConvoyIds = new java.util.HashSet<ConvoyId>();

        for (var factionId : new java.util.ArrayList<>(Alien.MOD.factions().getAllIds())) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage) || !lineage.isAlive()) {
                continue;
            }
            if (lineage.convoys().isEmpty()) {
                continue;
            }

            var iterator = lineage.convoys().listIterator();
            var anyChanged = false;

            while (iterator.hasNext()) {
                var convoy = iterator.next();
                activeConvoyIds.add(convoy.id());

                if (convoy instanceof Convoy.Raid raid) {
                    if (raid.returningHome()) {
                        if (shouldResumeRaidHunt(raid, server)) {
                            resumeRaidHunt(raid, server);
                            anyChanged = true;
                        }
                    } else {
                        if (updateRaidLossState(raid, server, lineage, config, currentTick)) {
                            anyChanged = true;
                        }
                        var returnHomeReason = returnHomeReason(raid, server, config, currentTick);
                        if (
                            returnHomeReason != ReturnHomeReason.NONE
                                && beginRaidReturnHome(raid, server, lineage, returnHomeReason)
                        ) {
                            ConvoyBossBars.remove(convoy);
                            activeConvoyIds.remove(convoy.id());
                            iterator.remove();
                            anyChanged = true;
                            continue;
                        }
                        if (returnHomeReason != ReturnHomeReason.NONE) {
                            anyChanged = true;
                        }
                    }
                }

                if (convoy instanceof Convoy.Reinforcement reinforcement) {
                    var redirected = turnReinforcementHomeIfDestinationGone(server, reinforcement);
                    if (redirected == null) {
                        ConvoyBossBars.remove(convoy);
                        activeConvoyIds.remove(convoy.id());
                        iterator.remove();
                        anyChanged = true;
                        continue;
                    }
                    if (redirected != reinforcement) {
                        convoy = redirected;
                        iterator.set(convoy);
                        anyChanged = true;
                    }
                }

                if (ConvoyMemberTracker.returnMissingMaterializedMembers(server, convoy) > 0) {
                    anyChanged = true;
                }

                if (convoy instanceof Convoy.Raid raid) {
                    if (raid.composition().getCount() <= 0 && raid.materializedMembers().isEmpty()) {
                        grantDefeatRaidAdvancement(raid, server);
                        ConvoyBossBars.remove(convoy);
                        activeConvoyIds.remove(convoy.id());
                        iterator.remove();
                        anyChanged = true;
                        continue;
                    }

                    if (!raid.returningHome() && !raid.lossConfirmed()) {
                        updateRaidTargetPos(raid, server);
                        refreshMarkedForDeath(raid, server, config);
                        maybeWarnRaidTarget(raid, server, lineage, config);
                        if (joinNearbyFrenziedRaidMembers(raid, server, lineage, config) > 0) {
                            anyChanged = true;
                        }
                        grantLeadRaidToEnemyHiveAdvancement(raid, server, lineage);
                        if (raid.shouldStartWaveBreak()) {
                            raid.startWaveBreak(currentTick);
                            anyChanged = true;
                        }
                    }
                }

                ConvoyTravel.tick(convoy, config);

                if (
                    ConvoyMemberTracker.returnDistantMaterializedMembers(
                        server,
                        convoy,
                        materializedMemberLeashDistanceSqr(config)
                    ) > 0
                ) {
                    anyChanged = true;
                }

                if (ConvoyArrival.checkArrival(server, convoy, lineage, config)) {
                    ConvoyBossBars.remove(convoy);
                    activeConvoyIds.remove(convoy.id());
                    iterator.remove();
                    anyChanged = true;
                    continue;
                }

                ConvoyBossBars.tick(server, convoy, lineage, config);

                if (ConvoyInterception.tryIntercept(server, convoy, lineage, config)) {
                    ConvoyBossBars.remove(convoy);
                    activeConvoyIds.remove(convoy.id());
                    iterator.remove();
                    anyChanged = true;
                }
            }

            if (anyChanged) {
                lineage.markDirty();
            }
        }

        ConvoyBossBars.retain(activeConvoyIds);
        grantDualVariantRaidAdvancements(server);
    }

    private static int joinNearbyFrenziedRaidMembers(
        Convoy.Raid raid,
        MinecraftServer server,
        LineageFactionData lineage,
        HiveConfig config
    ) {
        if (raid.frenziedJoinCount() >= config.raidFrenzyExtraMemberCap()) {
            return 0;
        }

        var level = server.getLevel(raid.dimension());
        if (level == null) {
            return 0;
        }

        var scanBounds = raidContextBounds(raid, level).inflate(RAID_FRENZY_JOIN_CONTEXT_RADIUS_BLOCKS);
        var candidates = level.getEntitiesOfClass(Xenomorph.class, scanBounds, candidate -> {
            if (!candidate.isAlive() || candidate.isRemoved()) {
                return false;
            }
            if (!candidate.hasEffect(AlienMobEffects.getFrenzyHolder())) {
                return false;
            }
            if (candidate.convoyMembership() != null) {
                return false;
            }
            return matchesRaidLineageOrVariant(candidate, raid, lineage)
                && isNearRaidContext(candidate, raid, level, RAID_FRENZY_JOIN_CONTEXT_RADIUS_BLOCKS);
        });

        var target = server.getPlayerList().getPlayer(raid.targetPlayerId());
        var joined = 0;
        for (var candidate : candidates) {
            if (raid.frenziedJoinCount() >= config.raidFrenzyExtraMemberCap()) {
                break;
            }

            ConvoyMemberTracker.markJoinedRaid(raid, candidate);
            if (target != null && target.isAlive() && target.level().dimension().equals(raid.dimension())) {
                candidate.setTarget(target);
            }
            joined++;
        }
        return joined;
    }

    private static AABB raidContextBounds(Convoy.Raid raid, ServerLevel level) {
        var targetCenter = raid.lastKnownTargetPos().getCenter();
        var minX = Math.min(raid.currentPos().x, targetCenter.x);
        var minY = Math.min(raid.currentPos().y, targetCenter.y);
        var minZ = Math.min(raid.currentPos().z, targetCenter.z);
        var maxX = Math.max(raid.currentPos().x, targetCenter.x);
        var maxY = Math.max(raid.currentPos().y, targetCenter.y);
        var maxZ = Math.max(raid.currentPos().z, targetCenter.z);

        for (var memberId : raid.materializedMembers().keySet()) {
            var member = level.getEntity(memberId);
            if (member == null) {
                continue;
            }
            var pos = member.position();
            minX = Math.min(minX, pos.x);
            minY = Math.min(minY, pos.y);
            minZ = Math.min(minZ, pos.z);
            maxX = Math.max(maxX, pos.x);
            maxY = Math.max(maxY, pos.y);
            maxZ = Math.max(maxZ, pos.z);
        }

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean matchesRaidLineageOrVariant(
        Xenomorph candidate,
        Convoy.Raid raid,
        LineageFactionData lineage
    ) {
        var hasCandidateLineage = false;
        for (var factionId : Alien.MOD.factions().getFactionIds(candidate.getUUID())) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }
            hasCandidateLineage = true;
            if (factionId.equals(raid.lineageFactionId())) {
                return true;
            }
        }

        return !hasCandidateLineage && candidate.getVariant() == lineage.variant();
    }

    private static boolean isNearRaidContext(
        LivingEntity candidate,
        Convoy.Raid raid,
        ServerLevel level,
        double radiusBlocks
    ) {
        var radiusSqr = radiusBlocks * radiusBlocks;
        if (candidate.position().distanceToSqr(raid.currentPos()) <= radiusSqr) {
            return true;
        }
        if (candidate.position().distanceToSqr(raid.lastKnownTargetPos().getCenter()) <= radiusSqr) {
            return true;
        }
        return isNearMaterializedRaidMember(candidate, raid, level, radiusSqr);
    }

    private static boolean isNearMaterializedRaidMember(
        LivingEntity candidate,
        Convoy.Raid raid,
        ServerLevel level,
        double radiusSqr
    ) {
        for (var memberId : raid.materializedMembers().keySet()) {
            var member = level.getEntity(memberId);
            if (member != null && member != candidate && member.position().distanceToSqr(candidate.position()) <= radiusSqr) {
                return true;
            }
        }
        return false;
    }

    private static Convoy.Reinforcement turnReinforcementHomeIfDestinationGone(
        MinecraftServer server,
        Convoy.Reinforcement reinforcement
    ) {
        var destination = HiveLocationRegistry.INSTANCE.get(reinforcement.destinationLocationId());
        if (destination != null && destination.isAlive()) {
            return reinforcement;
        }

        ConvoyMemberTracker.recallMaterializedMembers(server, reinforcement);

        var source = HiveLocationRegistry.INSTANCE.get(reinforcement.sourceLocationId());
        if (source == null || !source.isAlive()) {
            Alien.LOGGER.info(
                "Hive: reinforcement {} target location {} is gone, but source location {} is not alive; disbanding {} member(s)",
                reinforcement.id(),
                reinforcement.destinationLocationId(),
                reinforcement.sourceLocationId(),
                reinforcement.composition().getCount()
            );
            return null;
        }

        Alien.LOGGER.info(
            "Hive: reinforcement {} target location {} is gone; returning {} member(s) to source location {}",
            reinforcement.id(),
            reinforcement.destinationLocationId(),
            reinforcement.composition().getCount(),
            source.id()
        );

        return new Convoy.Reinforcement(
            reinforcement.id(),
            reinforcement.lineageFactionId(),
            reinforcement.dimension(),
            reinforcement.sourceLocationId(),
            source.id(),
            reinforcement.currentPos(),
            source.centerPos(),
            reinforcement.composition(),
            reinforcement.materializedMembers(),
            reinforcement.dispatchedTick()
        );
    }

    private static void grantDualVariantRaidAdvancements(MinecraftServer server) {
        var variantsByPlayer = new HashMap<UUID, HashSet<AlienVariant>>();

        for (var factionId : new java.util.ArrayList<>(Alien.MOD.factions().getAllIds())) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage) || !lineage.isAlive()) {
                continue;
            }

            for (var convoy : lineage.convoys()) {
                if (!(convoy instanceof Convoy.Raid raid) || raid.returningHome() || !isRaidTargetValid(raid, server)) {
                    continue;
                }
                variantsByPlayer
                    .computeIfAbsent(raid.targetPlayerId(), $ -> new HashSet<>())
                    .add(lineage.variant());
            }
        }

        for (var entry : variantsByPlayer.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            var player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                AlienAdvancements.DUAL_VARIANT_RAIDS.grant(player);
            }
        }
    }

    private static void grantLeadRaidToEnemyHiveAdvancement(
        Convoy.Raid raid,
        MinecraftServer server,
        LineageFactionData raidLineage
    ) {
        if (!isRaidTargetValid(raid, server)) {
            return;
        }

        var player = server.getPlayerList().getPlayer(raid.targetPlayerId());
        if (player == null) {
            return;
        }

        var location = HiveLocationRegistry.INSTANCE.getByChunk(
            player.level().dimension(),
            new ChunkPos(player.blockPosition())
        );
        if (location == null || location.lineageFactionId().equals(raidLineage.factionId())) {
            return;
        }

        var hiveVariant = location.lineageVariantOrNull();
        if (hiveVariant != null && hiveVariant != raidLineage.variant()) {
            AlienAdvancements.LEAD_RAID_TO_ENEMY_HIVE.grant(player);
        }
    }

    private static void refreshMarkedForDeath(Convoy.Raid raid, MinecraftServer server, HiveConfig config) {
        var player = server.getPlayerList().getPlayer(raid.targetPlayerId());
        if (player == null || !player.isAlive()) {
            return;
        }

        var duration = markedForDeathDurationTicks(raid, config);
        var currentEffect = player.getEffect(AlienMobEffects.getMarkedForDeathHolder());
        if (
            currentEffect != null
                && Math.abs(currentEffect.getDuration() - duration) <= MARKED_FOR_DEATH_DURATION_DRIFT_TICKS
        ) {
            return;
        }

        player.forceAddEffect(
            new MobEffectInstance(
                AlienMobEffects.getMarkedForDeathHolder(),
                duration,
                0,
                false,
                false,
                true
            ),
            null
        );
    }

    private static void grantDefeatRaidAdvancement(Convoy.Raid raid, MinecraftServer server) {
        var player = server.getPlayerList().getPlayer(raid.targetPlayerId());
        if (player != null) {
            AlienAdvancements.DEFEAT_A_RAID.grant(player);
        }
    }

    private static int markedForDeathDurationTicks(Convoy.Raid raid, HiveConfig config) {
        if (!raid.materializedMembers().isEmpty()) {
            return MARKED_FOR_DEATH_ACTIVE_RAID_TICKS;
        }

        var ticksToArrival = ConvoyTravel.ticksToArrival(raid, config);
        if (ticksToArrival <= 0L || ticksToArrival == Long.MAX_VALUE) {
            return MARKED_FOR_DEATH_ACTIVE_RAID_TICKS;
        }
        if (ticksToArrival >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) ticksToArrival;
    }

    /** Refreshes a raid's last-known target position when its target player is online + same dim + alive. */
    private static void updateRaidTargetPos(Convoy.Raid raid, MinecraftServer server) {
        var player = server.getPlayerList().getPlayer(raid.targetPlayerId());
        if (player == null || !player.isAlive()) {
            return;
        }
        if (!player.level().dimension().equals(raid.dimension())) {
            return;
        }
        raid.setLastKnownTargetPos(player.blockPosition());
    }

    private static boolean updateRaidLossState(
        Convoy.Raid raid,
        MinecraftServer server,
        LineageFactionData lineage,
        HiveConfig config,
        long currentTick
    ) {
        if (raid.lossConfirmed()) {
            return false;
        }

        var changed = false;
        var player = server.getPlayerList().getPlayer(raid.targetPlayerId());
        if (
            player != null
                && player.isAlive()
                && !player.isCreative()
                && !player.isSpectator()
                && player.level().dimension().equals(raid.dimension())
        ) {
            changed = raid.targetDownSinceTick() >= 0L || !raid.targetWasAlive();
            raid.noteTargetAlive();
            return changed;
        }

        if (player != null && !player.isAlive()) {
            if (raid.targetWasAlive()) {
                raid.recordTargetDeath(currentTick, config.raidLossDeathWindowTicks());
                changed = true;
            } else {
                changed = raid.targetDownSinceTick() < 0L;
                raid.noteTargetDown(currentTick);
            }
        }

        var repeatedDeaths = raid.targetDeathCount() >= config.raidLossDeathThreshold();
        var stayedDown = raid.targetDownSinceTick() >= 0L
            && currentTick - raid.targetDownSinceTick() >= config.raidLossDownGraceTicks();
        if (!repeatedDeaths && !stayedDown) {
            return changed;
        }

        raid.confirmLoss(currentTick);
        lineage.clearKillAttributionForPlayer(raid.targetPlayerId());
        RaidDispatch.markRaidPressureSpent(raid.sourceLocationId(), currentTick);
        Alien.LOGGER.info(
            "Hive: raid {} confirmed target {} defeated after {} death(s); aftermath running for {} ticks",
            raid.id(),
            raid.targetPlayerId(),
            raid.targetDeathCount(),
            config.raidLossAftermathTicks()
        );
        return true;
    }

    private static ReturnHomeReason returnHomeReason(
        Convoy.Raid raid,
        MinecraftServer server,
        HiveConfig config,
        long currentTick
    ) {
        if (raid.lossConfirmed()) {
            return currentTick - raid.lossConfirmedTick() >= config.raidLossAftermathTicks()
                ? ReturnHomeReason.TARGET_DEFEATED
                : ReturnHomeReason.NONE;
        }

        var player = server.getPlayerList().getPlayer(raid.targetPlayerId());
        if (player == null) {
            return ReturnHomeReason.NONE;
        }
        if (player.isCreative() || player.isSpectator()) {
            return ReturnHomeReason.TARGET_UNAVAILABLE;
        }
        return ReturnHomeReason.NONE;
    }

    private static boolean shouldResumeRaidHunt(Convoy.Raid raid, MinecraftServer server) {
        return raid.returnHomeReason() == ReturnHomeReason.TARGET_UNAVAILABLE && isRaidTargetValid(raid, server);
    }

    private static boolean isRaidTargetValid(Convoy.Raid raid, MinecraftServer server) {
        var player = server.getPlayerList().getPlayer(raid.targetPlayerId());
        return player != null
            && player.isAlive()
            && !player.isCreative()
            && !player.isSpectator()
            && player.level().dimension().equals(raid.dimension());
    }

    private static void resumeRaidHunt(Convoy.Raid raid, MinecraftServer server) {
        raid.resumeHunt();
        updateRaidTargetPos(raid, server);
        Alien.LOGGER.info("Hive: raid {} resumed hunting player {}", raid.id(), raid.targetPlayerId());
    }

    private static double materializedMemberLeashDistanceSqr(HiveConfig config) {
        var distance = config.manifestDistanceBlocks();
        return (double) distance * distance;
    }

    private static boolean beginRaidReturnHome(
        Convoy.Raid raid,
        MinecraftServer server,
        LineageFactionData lineage,
        ReturnHomeReason reason
    ) {
        var recalled = ConvoyMemberTracker.recallMaterializedMembers(server, raid);
        if (reason == ReturnHomeReason.TARGET_UNAVAILABLE && recalled > 0) {
            raid.rewindActiveWave();
        }

        if (raid.composition().getCount() <= 0) {
            Alien.LOGGER.info(
                "Hive: raid {} stopped hunting player {} because {} with no surviving members to return",
                raid.id(),
                raid.targetPlayerId(),
                returnHomeReasonDescription(reason)
            );
            return true;
        }

        var destination = pickReturnLocation(raid, lineage);
        if (destination == null) {
            if (reason == ReturnHomeReason.TARGET_UNAVAILABLE) {
                raid.beginReturnHome(null, null, reason);
                lineage.markDirty();
                Alien.LOGGER.info(
                    "Hive: raid {} stopped hunting player {} because {} but no live return location exists; "
                        + "waiting to resume",
                    raid.id(),
                    raid.targetPlayerId(),
                    returnHomeReasonDescription(reason)
                );
                return false;
            }
            Alien.LOGGER.info(
                "Hive: raid {} stopped hunting player {} because {} but no live return location exists; "
                    + "disbanding {} member(s)",
                raid.id(),
                raid.targetPlayerId(),
                returnHomeReasonDescription(reason),
                raid.composition().getCount()
            );
            return true;
        }

        raid.beginReturnHome(destination.id(), destination.centerPos(), reason);
        lineage.markDirty();
        Alien.LOGGER.info(
            "Hive: raid {} stopped hunting player {} because {}; returning {} member(s) to location {} (recalled {})",
            raid.id(),
            raid.targetPlayerId(),
            returnHomeReasonDescription(reason),
            raid.composition().getCount(),
            destination.id(),
            recalled
        );
        return false;
    }

    private static String returnHomeReasonDescription(ReturnHomeReason reason) {
        return switch (reason) {
            case TARGET_UNAVAILABLE -> "the target became unavailable";
            case TARGET_DEFEATED -> "the target was defeated";
            case NONE -> "no return reason was recorded";
        };
    }

    private static HiveLocation pickReturnLocation(Convoy.Raid raid, LineageFactionData lineage) {
        var source = HiveLocationRegistry.INSTANCE.get(raid.sourceLocationId());
        if (source != null && source.isAlive()) {
            return source;
        }

        HiveLocation best = null;
        var bestDistance = Double.MAX_VALUE;
        for (var location : lineage.locationsById().values()) {
            if (!location.isAlive() || !location.dimension().equals(raid.dimension())) {
                continue;
            }
            var dx = location.centerPos().getX() - raid.currentPos().x;
            var dz = location.centerPos().getZ() - raid.currentPos().z;
            var distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = location;
            }
        }
        return best;
    }

    private static void maybeWarnRaidTarget(
        Convoy.Raid raid,
        MinecraftServer server,
        LineageFactionData lineage,
        HiveConfig config
    ) {
        if (raid.warningIssued()) {
            return;
        }

        var player = server.getPlayerList().getPlayer(raid.targetPlayerId());
        if (player == null || !player.isAlive()) {
            return;
        }
        if (!player.level().dimension().equals(raid.dimension())) {
            return;
        }

        var ticksToArrival = ConvoyTravel.ticksToArrival(raid, config);
        if (ticksToArrival > RAID_WARNING_LEAD_TICKS) {
            return;
        }

        player.playNotifySound(AlienSoundEvents.ENTITY_QUEEN_SCREAM.get(), SoundSource.MASTER, 1.0F, 1.0F);
        player.sendSystemMessage(
            Component.literal("A distant screech answers your violence...")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC)
        );
        raid.setWarningIssued(true);
        lineage.markDirty();
    }

}
