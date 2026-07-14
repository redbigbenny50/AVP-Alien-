package com.alien.common.gameplay.hive.convoy;

import com.alien.Alien;
import com.alien.common.gameplay.hive.config.HiveConfig;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.location.HiveLocationReserves;
import com.alien.common.registry.RaidWaveProfileRegistry;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.entity.v1.EntityReserves;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Raid dispatch — counter-attacks against players who've racked up too many lineage-member kills inside the aggro
 * window.
 * <p>
 * Per {@code HIVE_REDESIGN_06_CONVOYS.md} § 6:
 * <ul>
 * <li>Empress-gated.</li>
 * <li>Triggered when a player has at least {@code raidThresholdKills} kills in the aggro window.</li>
 * <li>Source = the largest qualifying location ({@code claimedChunks ≥ raidMinLocationSizeChunks}) that can satisfy the
 * lineage variant's raid wave profile.</li>
 * <li>Per-source cooldown so the same source doesn't spam raids.</li>
 * <li>Composition drained from source reserves using the active datapack raid wave profile.</li>
 * <li>Persists until the target player dies, then returns to a live lineage location when possible.</li>
 * </ul>
 * <p>
 * Phase 8b ships an "admin-trigger only or auto-trigger via kill threshold" path. The full design's complexities
 * (cross-dimension blocking, target-offline camping behaviors mid-flight) are handled by
 * {@link com.alien.common.gameplay.hive.tick.LineageConvoyTickTask}'s tick-time updater.
 */
public final class RaidDispatch {

    private static final Map<HiveLocationId, Long> lastDispatchTickByLocation = new HashMap<>();

    private RaidDispatch() {}

    public static void clear() {
        lastDispatchTickByLocation.clear();
    }

    public static void markRaidPressureSpent(HiveLocationId sourceLocationId, long currentTick) {
        lastDispatchTickByLocation.put(sourceLocationId, currentTick);
    }

    public static void scanAndDispatch(MinecraftServer server) {
        var currentTick = server.overworld().getGameTime();
        var config = HiveLocationRegistry.INSTANCE.config();

        for (var factionId : new ArrayList<>(Alien.MOD.factions().getAllIds())) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage) || !lineage.isAlive()) {
                continue;
            }

            // Post-replacement grudge runs UNGATED (a lone hive whose queen was killed still gets one grudge raid once
            // it has a queen again) — checked before, and independent of, the empress-gated kill-threshold auto-raid.
            scanGrudge(server, factionId, lineage, currentTick, config);

            if (lineage.empressId() == null) {
                continue;
            }
            scanLineage(server, factionId, lineage, currentTick, config);
        }
    }

    /**
     * Post-replacement grudge: for each location holding a {@code grudgePlayerId} (its founder queen was killed by that
     * player), once the location has a living queen again (the firewall crowned a replacement) and the player is online
     * in-dimension, dispatch a single grudge raid against them and clear the grudge — one raid only, not a permanent
     * vendetta.
     */
    private static void scanGrudge(
            MinecraftServer server,
            ResourceLocation factionId,
            LineageFactionData lineage,
            long currentTick,
            HiveConfig config
    ) {
        for (var location : lineage.locationsById().values()) {
            var grudgePlayerId = location.grudgePlayerId();
            if (grudgePlayerId == null) {
                continue;
            }
            // Wait until a replacement queen actually exists (grudge belongs to the successor's first raid).
            if (!locationHasLivingQueen(server, location)) {
                continue;
            }
            var targetPlayer = server.getPlayerList().getPlayer(grudgePlayerId);
            if (targetPlayer == null || targetPlayer.level().dimension() != location.dimension()) {
                continue;
            }

            var dispatched = tryDispatchAgainstPlayer(
                    server,
                    lineage,
                    factionId,
                    grudgePlayerId,
                    targetPlayer,
                    currentTick,
                    config,
                    false,
                    true
            );
            // Clear the grudge whether or not a party formed — it's a one-shot intent, not a retry loop.
            location.setGrudgePlayerId(null);
            lineage.markDirty();
            if (dispatched) {
                Alien.LOGGER.info("Hive: post-replacement grudge raid dispatched from {} at player {}", location.id(), grudgePlayerId);
            }
        }
    }

    private static boolean locationHasLivingQueen(MinecraftServer server, HiveLocation location) {
        var serverLevel = server.getLevel(location.dimension());
        if (serverLevel == null) {
            return false;
        }
        for (var entry : location.loadedMembersByType().entrySet()) {
            if (!entry.getKey().is(AlienEntityTypeTags.QUEENS)) {
                continue;
            }
            for (var uuid : entry.getValue()) {
                var entity = serverLevel.getEntity(uuid);
                if (entity != null && entity.isAlive()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Admin trigger entry. Forces a raid against {@code targetPlayer} from the lineage's largest location. */
    public static boolean forceRaid(
            MinecraftServer server,
            LineageFactionData lineage,
            ResourceLocation lineageFactionId,
            ServerPlayer targetPlayer
    ) {
        var currentTick = server.overworld().getGameTime();
        var config = HiveLocationRegistry.INSTANCE.config();
        return tryDispatchAgainstPlayer(
                server,
                lineage,
                lineageFactionId,
                targetPlayer.getUUID(),
                targetPlayer,
                currentTick,
                config,
                false,
                false
        );
    }

    /**
     * Revenge trigger: a founder queen of {@code lineage} was killed by {@code killer}. Dispatches an ungated (no
     * empress required) 3-wave revenge raid against the killer, using the revenge wave profile. Distinct from the
     * kill-threshold auto-raid — this fires on the single event of a queen's death, not accumulated kills.
     */
    public static boolean onQueenKilled(
            MinecraftServer server,
            LineageFactionData lineage,
            ResourceLocation lineageFactionId,
            ServerPlayer killer
    ) {
        var currentTick = server.overworld().getGameTime();
        var config = HiveLocationRegistry.INSTANCE.config();
        return tryDispatchAgainstPlayer(
                server,
                lineage,
                lineageFactionId,
                killer.getUUID(),
                killer,
                currentTick,
                config,
                false,
                true,
                true
        );
    }

    /**
     * Rescue trigger: {@code source}'s founder queen was captured and carried off. Dispatches an ungated rescue raid
     * from {@code source} against the player holding her ({@code captor}). Returns the dispatched raid's id so the
     * {@code RescueCampaign} can track this attempt, or {@code null} if no party could form (which the caller counts as
     * a failed attempt).
     */
    public static @Nullable ConvoyId dispatchRescue(
            MinecraftServer server,
            LineageFactionData lineage,
            ResourceLocation lineageFactionId,
            HiveLocation source,
            ServerPlayer captor
    ) {
        var currentTick = server.overworld().getGameTime();
        if (hasActiveOutboundRaidAgainst(lineage, captor.getUUID())) {
            return null;
        }

        var waveProfile = RaidWaveProfileRegistry.rescue();
        var composition = drainComposition(source.localReserves(), waveProfile, server.overworld().random);
        if (composition.getCount() < waveProfile.totalSize()) {
            refundComposition(source.localReserves(), composition);
            return null;
        }

        var sourceCenter = new Vec3(
                source.centerPos().getX() + 0.5,
                source.centerPos().getY() + 0.5,
                source.centerPos().getZ() + 0.5
        );

        var raidId = ConvoyId.fresh();
        var raid = new Convoy.Raid(
                raidId,
                lineageFactionId,
                source.dimension(),
                source.id(),
                captor.getUUID(),
                sourceCenter,
                captor.blockPosition(),
                composition,
                currentTick,
                Long.MAX_VALUE
        );
        raid.markRescue();
        raid.setWaveCount(waveProfile.waves().size());

        lineage.convoys().add(raid);
        lineage.markDirty();

        Alien.LOGGER.info(
                "Hive: rescue raid {} dispatched from {} at captor {} ({} members)",
                raidId,
                source.id(),
                captor.getUUID(),
                composition.getCount()
        );
        return raidId;
    }

    private static void scanLineage(
            MinecraftServer server,
            ResourceLocation lineageFactionId,
            LineageFactionData lineage,
            long currentTick,
            HiveConfig config
    ) {
        // Snapshot since recordKillByPlayer mutates lists during iteration via prune.
        var attribution = new HashMap<>(lineage.killAttributionByPlayer());

        for (var entry : attribution.entrySet()) {
            var playerId = entry.getKey();
            var kills = lineage.countRecentKills(playerId, currentTick, config.raidAggroWindowTicks());

            if (kills < config.raidThresholdKills()) {
                continue;
            }

            var targetPlayer = server.getPlayerList().getPlayer(playerId);
            if (targetPlayer == null) {
                continue;
            }
            if (!targetPlayer.level().dimension().equals(lineage.dimension())) {
                continue;
            }

            tryDispatchAgainstPlayer(
                    server,
                    lineage,
                    lineageFactionId,
                    playerId,
                    targetPlayer,
                    currentTick,
                    config,
                    true,
                    true
            );
        }
    }

    private static boolean tryDispatchAgainstPlayer(
            MinecraftServer server,
            LineageFactionData lineage,
            ResourceLocation lineageFactionId,
            UUID playerId,
            ServerPlayer targetPlayer,
            long currentTick,
            HiveConfig config,
            boolean consumeKillAttribution,
            boolean blockExistingTargetRaid
    ) {
        return tryDispatchAgainstPlayer(
                server,
                lineage,
                lineageFactionId,
                playerId,
                targetPlayer,
                currentTick,
                config,
                consumeKillAttribution,
                blockExistingTargetRaid,
                false
        );
    }

    private static boolean tryDispatchAgainstPlayer(
            MinecraftServer server,
            LineageFactionData lineage,
            ResourceLocation lineageFactionId,
            UUID playerId,
            ServerPlayer targetPlayer,
            long currentTick,
            HiveConfig config,
            boolean consumeKillAttribution,
            boolean blockExistingTargetRaid,
            boolean revenge
    ) {
        if (blockExistingTargetRaid && hasActiveOutboundRaidAgainst(lineage, playerId)) {
            return false;
        }

        var waveProfile = revenge
                ? RaidWaveProfileRegistry.revenge()
                : RaidWaveProfileRegistry.forVariant(lineage.variant());
        HiveLocation source = null;
        EntityReserves composition = null;

        for (var candidate : eligibleSources(lineage, currentTick, config, waveProfile)) {
            var candidateComposition = drainComposition(
                    candidate.localReserves(),
                    waveProfile,
                    server.overworld().random
            );
            if (candidateComposition.getCount() >= waveProfile.totalSize()) {
                source = candidate;
                composition = candidateComposition;
                break;
            }
            refundComposition(candidate.localReserves(), candidateComposition);
        }

        if (source == null || composition == null) {
            return false;
        }

        var sourceCenter = new Vec3(
                source.centerPos().getX() + 0.5,
                source.centerPos().getY() + 0.5,
                source.centerPos().getZ() + 0.5
        );

        var raid = new Convoy.Raid(
                ConvoyId.fresh(),
                lineageFactionId,
                source.dimension(),
                source.id(),
                playerId,
                sourceCenter,
                targetPlayer.blockPosition(),
                composition,
                currentTick,
                Long.MAX_VALUE
        );
        if (revenge) {
            raid.markRevenge();
            raid.setWaveCount(waveProfile.waves().size());
        }

        lineage.convoys().add(raid);
        if (consumeKillAttribution) {
            lineage.clearKillAttributionForPlayer(playerId);
        }
        lineage.markDirty();
        lastDispatchTickByLocation.put(source.id(), currentTick);

        Alien.LOGGER.info(
                "Hive: raid dispatched: {} → player {} from source {} ({} members, no expiry)",
                raid.id(),
                playerId,
                source.id(),
                composition.getCount()
        );

        return true;
    }

    private static boolean hasActiveOutboundRaidAgainst(LineageFactionData lineage, UUID playerId) {
        for (var convoy : lineage.convoys()) {
            if (
                    convoy instanceof Convoy.Raid raid
                            && !raid.returningHome()
                            && raid.targetPlayerId().equals(playerId)
            ) {
                return true;
            }
        }
        return false;
    }

    private static List<HiveLocation> eligibleSources(
            LineageFactionData lineage,
            long currentTick,
            HiveConfig config,
            RaidWaveProfile waveProfile
    ) {
        var candidates = new ArrayList<HiveLocation>();

        for (var location : lineage.locationsById().values()) {
            if (!location.isAlive()) {
                continue;
            }
            if (location.claimedChunks().size() < config.raidMinLocationSizeChunks()) {
                continue;
            }
            // NO HARBINGER, NO RAID.
            //
            // The harbinger is the raid key: it is the hive's only scourge-jelly factory, so without one the
            // entire scourge tier stops being produced - and it marches in wave 5, so every raid stakes it.
            // Kill it and the hive cannot raid again until it has grown a praetorian, fed it 200 biomass and a
            // scourge jelly, and waited out the cooldown. Killing the harbinger is a DISABLE, not just a kill.
            if (com.alien.common.gameplay.hive.economy.CastePopulation.countCaste(
                    location,
                    com.alien.common.registry.tag.AlienEntityTypeTags.HARBINGERS
            ) <= 0) {
                continue;
            }
            if (!hasRaidCapacity(location.localReserves(), waveProfile)) {
                continue;
            }

            var lastDispatch = lastDispatchTickByLocation.get(location.id());
            if (lastDispatch != null && currentTick - lastDispatch < config.perSourceRaidCooldownTicks()) {
                continue;
            }

            candidates.add(location);
        }

        candidates.sort(Comparator.comparingInt((HiveLocation location) -> location.claimedChunks().size()).reversed());
        return candidates;
    }

    private static boolean hasRaidCapacity(HiveLocationReserves reserves, RaidWaveProfile waveProfile) {
        return reserves.getCountMatching(waveProfile::isRaidEligible) >= waveProfile.totalSize();
    }

    private static EntityReserves drainComposition(
            HiveLocationReserves donorReserves,
            RaidWaveProfile waveProfile,
            RandomSource random
    ) {
        var composition = new EntityReserves();

        for (var i = 0; i < waveProfile.waves().size(); i++) {
            if (!drainWave(donorReserves, composition, waveProfile.wave(i), random)) {
                return composition;
            }
        }

        return composition;
    }

    private static boolean drainWave(
            HiveLocationReserves donorReserves,
            EntityReserves composition,
            RaidWaveProfile.Wave wave,
            RandomSource random
    ) {
        var inventory = new RaidWaveSelection.Inventory() {

            @Override
            public Iterable<EntityType<?>> availableTypes() {
                return donorReserves.getAvailableEntityTypes();
            }

            @Override
            public int count(EntityType<?> type) {
                return donorReserves.getCount(type);
            }
        };

        for (var guarantee : wave.guaranteed()) {
            if (!drainFromPools(donorReserves, composition, inventory, guarantee.pools(), guarantee.count(), random)) {
                return false;
            }
        }

        return drainFromPools(
                donorReserves,
                composition,
                inventory,
                wave.pools(),
                wave.size() - wave.guaranteedSize(),
                random
        );
    }

    private static boolean drainFromPools(
            HiveLocationReserves donorReserves,
            EntityReserves composition,
            RaidWaveSelection.Inventory inventory,
            List<RaidWaveProfile.PoolEntry> pools,
            int count,
            RandomSource random
    ) {
        var selectedByPool = new HashMap<Integer, Integer>();
        for (var i = 0; i < count; i++) {
            var selectedType = RaidWaveSelection.chooseType(pools, inventory, selectedByPool, random);
            if (selectedType == null || !donorReserves.trySpawn(selectedType)) {
                return false;
            }
            composition.add(selectedType, 1);
        }
        return true;
    }

    private static void refundComposition(HiveLocationReserves reserves, EntityReserves composition) {
        for (var type : new ArrayList<>(composition.getAvailableEntityTypes())) {
            var count = composition.getCount(type);
            if (count <= 0) {
                continue;
            }
            reserves.addReturningMember(type, count);
            composition.add(type, -count);
        }
    }
}