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
import com.blib.api.common.entity.v1.EntityReserves;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

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
            if (lineage.empressId() == null) {
                continue;
            }
            scanLineage(server, factionId, lineage, currentTick, config);
        }
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
        if (blockExistingTargetRaid && hasActiveOutboundRaidAgainst(lineage, playerId)) {
            return false;
        }

        var waveProfile = RaidWaveProfileRegistry.forVariant(lineage.variant());
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
