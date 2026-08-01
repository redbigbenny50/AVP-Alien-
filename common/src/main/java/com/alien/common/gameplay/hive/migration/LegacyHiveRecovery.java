package com.alien.common.gameplay.hive.migration;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.faction.FactionAesthetics;
import com.alien.common.gameplay.hive.faction.FactionNaming;
import com.alien.common.gameplay.hive.faction.HiveLocationFactionProvisioner;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.faction.LocationMembership;
import com.alien.common.gameplay.hive.faction.VariantFactionRegistry;
import com.alien.common.gameplay.hive.growth.HiveLocationClaims;
import com.alien.common.gameplay.hive.id.HiveLocationIds;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienFactionDataTypes;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.codec.v1.BLibCodecs;
import com.blib.api.common.entity.v1.EntityReserves;
import com.blib.api.common.faction.v1.FactionMember;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LegacyHiveRecovery {

    private static final int FOUNDING_RADIUS_CHUNKS = 1;

    private static final int NEARBY_MEMBER_JOIN_RADIUS_BLOCKS = 64;

    private static final String AWAKEN_ALL_COMMAND = "/avp_alien debug hive awaken_legacy_queens";

    private static final String AWAKEN_NEAREST_COMMAND = "/avp_alien debug hive awaken_nearest_legacy_queen";

    private static final String KILL_ALL_COMMAND = "/avp_alien debug hive kill_legacy_queens";

    private static final String KILL_NEAREST_COMMAND = "/avp_alien debug hive kill_nearest_legacy_queen";

    private static final List<String> CENTER_POS_KEYS = List.of("CenterPos", "centerPos", "center", "Center");

    private LegacyHiveRecovery() {}

    /**
     * @return true when the pass actually repaired something - the caller uses this to decide whether the registry
     *     rebuild needs a second run. A clean world (no legacy data, or already recovered) returns false, which is
     *     what stops the byte-identical double rebuild that used to run on every load.
     */
    public static boolean detectAndRecover(MinecraftServer server) {
        var repairedAnything = new boolean[1];
        LegacyHiveRecoveryData.getOrCreate(server)
            .ifSome(data -> {
                var snapshots = readLegacyHiveSnapshots(server);
                if (!snapshots.isEmpty() || hasLegacyHiveDataFiles(server)) {
                    data.setLegacyDetected(true);
                }
                if (!data.legacyDetected()) {
                    return;
                }
                if (data.recoveryApplied() && snapshots.stream().noneMatch(snapshot -> snapshotNeedsRepair(data, snapshot))) {
                    return;
                }

                var repaired = 0;
                for (var snapshot : snapshots) {
                    if (repairSnapshot(server, data, snapshot)) {
                        repaired++;
                    }
                }
                repairedAnything[0] = repaired > 0;

                data.setRecoveryApplied(true);
                Alien.LOGGER.info(
                    "Legacy hive recovery: detected old hive data and repaired {}/{} hive snapshot(s)",
                    repaired,
                    snapshots.size()
                );
            });
        return repairedAnything[0];
    }

    private static boolean snapshotNeedsRepair(LegacyHiveRecoveryData data, LegacyHiveSnapshot snapshot) {
        if (data.memberLocationCount() == 0) {
            return true;
        }
        if (snapshot.leaderId() != null && data.memberLocation(snapshot.leaderId()) == null) {
            return true;
        }
        for (var member : snapshot.members()) {
            if (data.memberLocation(member.uuid()) == null) {
                return true;
            }
        }
        return findMatchingLocation(snapshot) == null;
    }

    public static void recoverLoadedAlien(Entity entity) {
        if (!(entity instanceof com.alien.common.gameplay.entity.living.alien.Alien alien)) {
            return;
        }
        if (entity.level().isClientSide || entity.level().getServer() == null) {
            return;
        }

        var server = entity.level().getServer();
        LegacyHiveRecoveryData.getOrCreate(server)
            .ifSome(data -> {
                if (!data.legacyDetected()) {
                    return;
                }

                alien.getHiveManager().ensureVariantFactionMembership();
                var isLegacyRecoveryQueen = alien instanceof Queen queen && shouldTreatAsLegacyQueen(data, queen);

                var mappedLocationId = data.memberLocation(entity.getUUID());
                if (mappedLocationId != null) {
                    var location = HiveLocationRegistry.INSTANCE.get(mappedLocationId);
                    if (location != null && location.isAlive()) {
                        joinRecoveredMember(data, location, alien);
                    }
                } else {
                    LocationMembership.autoJoinAtPosition(entity, (ServerLevel) entity.level());
                }

                if (isLegacyRecoveryQueen && alien instanceof Queen queen) {
                    data.rememberLegacyQueen(queen.getUUID());
                    rememberQueenLocationIfMissing(data, queen, false);

                    if (data.killAllLegacyQueens()) {
                        queen.discard();
                        return;
                    }
                    if (data.wakeAllLegacyQueens() || data.isAwakenedLegacyQueen(queen.getUUID())) {
                        data.rememberAwakenedLegacyQueen(queen.getUUID());
                        queen.wakeFromLegacyDormantRecovery();
                    } else {
                        queen.setLegacyDormant(true);
                    }
                }
            });
    }

    /**
     * Whether a queen came out of a pre-lifecycle save and should be handed to the legacy recovery path.
     * <p>
     * <b>An ovipositor with no live hive behind it used to be enough on its own, and that was wrong.</b> A queen this
     * world created can be in exactly that state for entirely healthy reasons - most importantly WHILE SHE IS FOUNDING,
     * when her location exists but she has not joined its faction yet, and again if her hive later dies and leaves her
     * orphaned. Once {@code killAllLegacyQueens} has been armed by an admin sweep (it persists in NBT so unloaded
     * legacy queens are caught as they load), matching that test means she is DISCARDED - a founding queen would simply
     * vanish mid-dig and never come back. Without the flag armed she was instead frozen dormant, which stops the carve
     * just as dead.
     * <p>
     * So identification now needs the one signal only a genuinely old save can produce: arriving with NO lifecycle
     * state at all. A queen already recorded as legacy stays recorded, so she is still recognised on later loads once
     * that state has been written for her.
     */
    private static boolean shouldTreatAsLegacyQueen(LegacyHiveRecoveryData data, Queen queen) {
        return data.isAwakenedLegacyQueen(queen.getUUID())
            || data.isLegacyQueen(queen.getUUID())
            || queen.wasLoadedWithoutLifecycleState();
    }

    private static void rememberQueenLocationIfMissing(LegacyHiveRecoveryData data, Queen queen, boolean createIfMissing) {
        if (data.memberLocation(queen.getUUID()) != null) {
            return;
        }
        var nearest = HiveLocationRegistry.INSTANCE.findNearestInDim(queen.level().dimension(), queen.blockPosition());
        if (nearest == null || !nearest.isAlive()) {
            if (createIfMissing) {
                var created = createLocationForQueen(queen);
                if (created != null) {
                    data.rememberMemberLocation(queen.getUUID(), created.id());
                }
            }
            return;
        }
        data.rememberMemberLocation(queen.getUUID(), nearest.id());
    }

    public static void tickPlayerMessages(MinecraftServer server) {
        LegacyHiveRecoveryData.getOrCreate(server)
            .ifSome(data -> {
                if (!data.legacyDetected()) {
                    return;
                }
                for (var player : server.getPlayerList().getPlayers()) {
                    if (!data.markPlayerMessaged(player.getUUID())) {
                        continue;
                    }
                    sendLegacyMessage(player);
                }
            });
    }

    public static int awakenLegacyQueens(MinecraftServer server) {
        final int[] awakened = { 0 };
        LegacyHiveRecoveryData.getOrCreate(server)
            .ifSome(data -> data.setWakeAllLegacyQueens(true));

        for (var level : server.getAllLevels()) {
            for (var queen : level.getEntitiesOfClass(Queen.class, level.getWorldBorder().getCollisionShape().bounds())) {
                if (queen.isLegacyDormant()) {
                    queen.wakeFromLegacyDormantRecovery();
                    awakened[0]++;
                } else {
                    onLegacyQueenAwakened(queen);
                }
                recoverLoadedAlien(queen);
            }
        }
        return awakened[0];
    }

    public static boolean awakenLegacyQueen(Queen queen) {
        if (queen.level().isClientSide || queen.level().getServer() == null) {
            return false;
        }

        final boolean[] awakened = { false };
        LegacyHiveRecoveryData.getOrCreate(queen.level().getServer())
            .ifSome(data -> {
                if (!canWakeAsLegacyQueen(data, queen)) {
                    return;
                }
                data.rememberAwakenedLegacyQueen(queen.getUUID());
                queen.wakeFromLegacyDormantRecovery();
                recoverLoadedAlien(queen);
                awakened[0] = true;
            });
        return awakened[0];
    }

    public static int killLegacyQueens(MinecraftServer server) {
        final int[] killed = { 0 };
        LegacyHiveRecoveryData.getOrCreate(server)
            .ifSome(data -> data.setKillAllLegacyQueens(true));

        for (var level : server.getAllLevels()) {
            for (var queen : level.getEntitiesOfClass(Queen.class, level.getWorldBorder().getCollisionShape().bounds())) {
                if (killLegacyQueenIfOld(queen)) {
                    killed[0]++;
                }
            }
        }
        return killed[0];
    }

    public static boolean killLegacyQueen(Queen queen) {
        if (queen.level().isClientSide || queen.level().getServer() == null) {
            return false;
        }
        return killLegacyQueenIfOld(queen);
    }

    private static boolean killLegacyQueenIfOld(Queen queen) {
        var server = queen.level().getServer();
        if (server == null) {
            return false;
        }

        final boolean[] killed = { false };
        LegacyHiveRecoveryData.getOrCreate(server)
            .ifSome(data -> {
                if (!shouldTreatAsLegacyQueen(data, queen)) {
                    return;
                }
                data.rememberLegacyQueen(queen.getUUID());
                queen.discard();
                killed[0] = true;
            });
        return killed[0];
    }

    public static void onLegacyQueenAwakened(Queen queen) {
        if (queen.level().isClientSide || queen.level().getServer() == null) {
            return;
        }

        var server = queen.level().getServer();
        LegacyHiveRecoveryData.getOrCreate(server)
            .ifSome(data -> {
                if (!canWakeAsLegacyQueen(data, queen)) {
                    return;
                }
                data.rememberAwakenedLegacyQueen(queen.getUUID());
                var location = resolveWakeLocation(data, queen);
                if (location == null || !location.isAlive()) {
                    return;
                }
                if (!(queen.level() instanceof ServerLevel level)) {
                    return;
                }

                claimFoundingArea(level, location);
                var joinedMembers = joinNearbyMembers(data, level, location, queen);
                clearUnifiedAlienTargets(joinedMembers);
                markLocationDirty(location);
            });
    }

    private static boolean canWakeAsLegacyQueen(LegacyHiveRecoveryData data, Queen queen) {
        return data.isLegacyQueen(queen.getUUID())
            || queen.wasLoadedWithoutLifecycleState()
            || queen.hasOvipositor()
            || data.memberLocation(queen.getUUID()) != null;
    }

    private static @Nullable HiveLocation resolveWakeLocation(LegacyHiveRecoveryData data, Queen queen) {
        var mapped = locationForQueen(data, queen);
        if (mapped != null && mapped.isAlive()) {
            return mapped;
        }

        var founderMatch = findLocationFoundedBy(queen);
        if (founderMatch != null) {
            data.rememberMemberLocation(queen.getUUID(), founderMatch.id());
            return founderMatch;
        }

        if (queen.level() instanceof ServerLevel level) {
            var currentChunkLocation = HiveLocationRegistry.INSTANCE.getByChunk(
                level.dimension(),
                new ChunkPos(queen.blockPosition())
            );
            if (currentChunkLocation != null && currentChunkLocation.isAlive() && locationAcceptsQueen(currentChunkLocation, queen)) {
                data.rememberMemberLocation(queen.getUUID(), currentChunkLocation.id());
                return currentChunkLocation;
            }
        }

        var created = createLocationForQueen(queen);
        if (created != null) {
            data.rememberMemberLocation(queen.getUUID(), created.id());
        }
        return created;
    }

    private static @Nullable HiveLocation findLocationFoundedBy(Queen queen) {
        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (!location.dimension().equals(queen.level().dimension()) || !location.isAlive()) {
                continue;
            }
            if (queen.getUUID().equals(location.founderId())) {
                return location;
            }
        }
        return null;
    }

    private static boolean locationAcceptsQueen(HiveLocation location, Queen queen) {
        var variant = location.lineageVariantOrNull();
        return variant != null && variant == queen.getVariant();
    }

    private static @Nullable HiveLocation locationForQueen(LegacyHiveRecoveryData data, Queen queen) {
        var locationId = data.memberLocation(queen.getUUID());
        return locationId == null ? null : HiveLocationRegistry.INSTANCE.get(locationId);
    }

    private static void sendLegacyMessage(ServerPlayer player) {
        player.sendSystemMessage(
            Component.literal(
                "[AVP] Legacy hive data detected.\n"
                    + "Old xenomorphs are being repaired so they can rejoin the new hive system.\n"
                    + "Old queens are dormant. They can wake from damage, interaction, or a command.\n"
                    + "Legacy queen commands:\n"
                    + "Wake all legacy queens: "
                    + AWAKEN_ALL_COMMAND
                    + "\n"
                    + "Wake only the nearest loaded legacy queen: "
                    + AWAKEN_NEAREST_COMMAND
                    + "\n"
                    + "Kill all legacy queens: "
                    + KILL_ALL_COMMAND
                    + "\n"
                    + "Kill only the nearest loaded legacy queen: "
                    + KILL_NEAREST_COMMAND
                    + "\n"
                    + "When a legacy queen wakes, her hive claims its 3x3 founding area. That means her center chunk plus the 8 surrounding chunks, which becomes the hive's starting territory."
            )
        );
    }

    private static boolean repairSnapshot(MinecraftServer server, LegacyHiveRecoveryData recoveryData, LegacyHiveSnapshot snapshot) {
        var level = server.getLevel(snapshot.dimension());
        if (level == null) {
            Alien.LOGGER.warn(
                "Legacy hive recovery: dimension {} is not loaded; skipping {}",
                snapshot.dimension().location(),
                snapshot.id()
            );
            return false;
        }

        var location = findMatchingLocation(snapshot);
        LineageFactionData lineageData;
        if (location != null) {
            var lineageFaction = Alien.MOD.factions().get(location.lineageFactionId());
            if (lineageFaction == null || !(lineageFaction.data() instanceof LineageFactionData lineage)) {
                return false;
            }
            lineageData = lineage;
        } else {
            var created = createLocation(snapshot);
            if (created == null) {
                return false;
            }
            location = created.location();
            lineageData = created.lineage();
            HiveLocationRegistry.INSTANCE.register(location);
        }

        if (location.founderId() == null && snapshot.leaderId() != null) {
            location.setFounderId(snapshot.leaderId());
        }
        if (lineageData.founderId() == null && snapshot.leaderId() != null) {
            lineageData.setFounderId(snapshot.leaderId());
        }
        location.setBiomass(Math.max(location.biomass(), snapshot.biomass()));
        copyLegacyReserves(location, snapshot.reserves());

        var lineageFaction = Alien.MOD.factions().get(location.lineageFactionId());
        var locationFaction = Alien.MOD.factions().getOrCreate(location.id().value(), AlienFactionDataTypes.LOCATION);
        if (snapshot.leaderId() != null) {
            var leaderMember = FactionMember.entity(snapshot.leaderId());
            recoveryData.rememberMemberLocation(snapshot.leaderId(), location.id());
            if (lineageFaction != null && !lineageFaction.membership().hasMember(leaderMember)) {
                lineageFaction.membership().addMember(leaderMember);
            }
            if (!locationFaction.membership().hasMember(leaderMember)) {
                locationFaction.membership().addMember(leaderMember);
            }
        }
        for (var member : snapshot.members()) {
            recoveryData.rememberMemberLocation(member.uuid(), location.id());
            if (member.entityType() != null) {
                location
                    .knownMembersByType()
                    .computeIfAbsent(member.entityType(), $ -> new HashSet<>())
                    .add(member.uuid());
                location.localReserves().tryAdd(member.entityType(), 0);
            }
            var factionMember = FactionMember.entity(member.uuid());
            if (lineageFaction != null && !lineageFaction.membership().hasMember(factionMember)) {
                lineageFaction.membership().addMember(factionMember);
            }
            if (!locationFaction.membership().hasMember(factionMember)) {
                locationFaction.membership().addMember(factionMember);
            }
            if (member.isQueen()) {
                recoveryData.rememberLegacyQueen(member.uuid());
            }
        }

        lineageData.addLocation(location);
        return true;
    }

    private static void markLocationDirty(HiveLocation location) {
        var lineageFaction = Alien.MOD.factions().get(location.lineageFactionId());
        if (lineageFaction != null && lineageFaction.data() instanceof LineageFactionData lineageData) {
            lineageData.addLocation(location);
        }
    }

    private static void copyLegacyReserves(HiveLocation location, Map<EntityType<?>, Integer> reserves) {
        for (var entry : reserves.entrySet()) {
            var count = Math.max(0, entry.getValue());
            if (count > 0) {
                location.localReserves().tryAdd(entry.getKey(), count);
            }
        }
    }

    private static @Nullable HiveLocation findMatchingLocation(LegacyHiveSnapshot snapshot) {
        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (!location.dimension().equals(snapshot.dimension())) {
                continue;
            }
            if (location.centerPos().distSqr(snapshot.centerPos()) <= 16.0D * 16.0D) {
                return location;
            }
            if (snapshot.leaderId() != null && snapshot.leaderId().equals(location.founderId())) {
                return location;
            }
        }
        return null;
    }

    private static @Nullable CreatedLocation createLocation(LegacyHiveSnapshot snapshot) {
        var variantFaction = VariantFactionRegistry.getOrCreate(snapshot.variant());
        var lineageId = LineageIds.create();
        var lineageFaction = Alien.MOD.factions().getOrCreate(lineageId, AlienFactionDataTypes.LINEAGE);
        if (!(lineageFaction.data() instanceof LineageFactionData lineageData)) {
            return null;
        }

        FactionAesthetics.applyDefaults(lineageFaction, snapshot.variant(), FactionAesthetics.Tier.LINEAGE);
        lineageData.setFactionId(lineageId);
        var variantData = variantFaction.data();
        var lineageNumber = variantData != null ? variantData.allocateLineageNumber() : 0L;
        lineageData.setLineageNumber(lineageNumber);
        lineageFaction.setName(FactionNaming.forLineage(snapshot.variant(), lineageNumber));
        lineageData.setVariant(snapshot.variant());
        lineageData.setParentVariantFactionId(variantFaction.id());
        lineageData.setDimension(snapshot.dimension());
        lineageData.setFounderId(snapshot.leaderId());

        var location = new HiveLocation(
            HiveLocationIds.create(),
            lineageId,
            snapshot.dimension(),
            snapshot.centerPos(),
            snapshot.leaderId()
        );
        location.setLocationNumber(lineageData.allocateLocationNumber());
        HiveLocationFactionProvisioner.ensure(location, lineageData);
        lineageData.addLocation(location);
        return new CreatedLocation(lineageData, location);
    }

    private static @Nullable HiveLocation createLocationForQueen(Queen queen) {
        if (!(queen.level() instanceof ServerLevel)) {
            return null;
        }

        var snapshot = new LegacyHiveSnapshot(
            "legacy_queen/" + queen.getUUID(),
            queen.level().dimension(),
            queen.blockPosition(),
            queen.getUUID(),
            queen.getVariant(),
            0,
            List.of(new LegacyHiveMember(queen.getUUID(), queen.getType())),
            Map.of()
        );
        var created = createLocation(snapshot);
        if (created == null) {
            return null;
        }
        HiveLocationRegistry.INSTANCE.register(created.location());
        return created.location();
    }

    private static void claimFoundingArea(ServerLevel level, HiveLocation location) {
        var center = new ChunkPos(location.centerPos());
        var currentTick = level.getGameTime();
        for (var dx = -FOUNDING_RADIUS_CHUNKS; dx <= FOUNDING_RADIUS_CHUNKS; dx++) {
            for (var dz = -FOUNDING_RADIUS_CHUNKS; dz <= FOUNDING_RADIUS_CHUNKS; dz++) {
                var chunk = new ChunkPos(center.x + dx, center.z + dz);
                var existing = HiveLocationRegistry.INSTANCE.getByChunk(level.dimension(), chunk);
                if (existing != null && !existing.id().equals(location.id())) {
                    continue;
                }
                HiveLocationClaims.claim(level, location, chunk, currentTick);
            }
        }
    }

    private static List<com.alien.common.gameplay.entity.living.alien.Alien> joinNearbyMembers(
        LegacyHiveRecoveryData data,
        ServerLevel level,
        HiveLocation location,
        Queen queen
    ) {
        var joinedMembers = new ArrayList<com.alien.common.gameplay.entity.living.alien.Alien>();
        joinRecoveredMember(data, location, queen);
        joinedMembers.add(queen);

        var area = queen.getBoundingBox().inflate(NEARBY_MEMBER_JOIN_RADIUS_BLOCKS);
        for (
            var alien : level.getEntitiesOfClass(
                com.alien.common.gameplay.entity.living.alien.Alien.class,
                area
            )
        ) {
            if (alien == queen) {
                continue;
            }
            joinRecoveredMember(data, location, alien);
            joinedMembers.add(alien);
        }
        return joinedMembers;
    }

    private static void joinRecoveredMember(
        LegacyHiveRecoveryData data,
        HiveLocation location,
        com.alien.common.gameplay.entity.living.alien.Alien alien
    ) {
        data.rememberMemberLocation(alien.getUUID(), location.id());
        removeConflictingHiveMemberships(location, alien);
        LocationMembership.join(location, alien);
    }

    private static void removeConflictingHiveMemberships(HiveLocation location, Entity entity) {
        var member = FactionMember.entity(entity);
        for (var factionId : new ArrayList<>(Alien.MOD.factions().getFactionIds(entity.getUUID()))) {
            if (LineageIds.isLineageId(factionId) && !factionId.equals(location.lineageFactionId())) {
                var faction = Alien.MOD.factions().get(factionId);
                if (faction != null) {
                    faction.membership().removeMember(member);
                }
                continue;
            }

            if (HiveLocationIds.isHiveLocationId(factionId) && !factionId.equals(location.id().value())) {
                var faction = Alien.MOD.factions().get(factionId);
                if (faction != null) {
                    faction.membership().removeMember(member);
                }
            }
        }
    }

    private static void clearUnifiedAlienTargets(List<com.alien.common.gameplay.entity.living.alien.Alien> aliens) {
        for (var alien : aliens) {
            if (!(alien.getTarget() instanceof com.alien.common.gameplay.entity.living.alien.Alien targetAlien)) {
                continue;
            }
            if (com.alien.common.util.AlienPredicates.areAliensSameHive(alien, targetAlien)) {
                alien.setTarget(null);
            }
        }

        for (var alien : aliens) {
            for (var other : aliens) {
                if (other == alien) {
                    continue;
                }
                LivingEntity target = other.getTarget();
                if (target == alien && com.alien.common.util.AlienPredicates.areAliensSameHive(other, alien)) {
                    other.setTarget(null);
                }
            }
        }
    }

    private static List<LegacyHiveSnapshot> readLegacyHiveSnapshots(MinecraftServer server) {
        var worldPath = server.getWorldPath(LevelResource.ROOT);
        var snapshots = new ArrayList<LegacyHiveSnapshot>();
        readLegacyHiveFile(worldPath.resolve("data").resolve("hive_data.dat"), Level.OVERWORLD, snapshots);
        readLegacyHiveFile(worldPath.resolve("DIM-1").resolve("data").resolve("hive_data.dat"), Level.NETHER, snapshots);
        readLegacyHiveFile(worldPath.resolve("DIM1").resolve("data").resolve("hive_data.dat"), Level.END, snapshots);
        return snapshots;
    }

    private static boolean hasLegacyHiveDataFiles(MinecraftServer server) {
        var worldPath = server.getWorldPath(LevelResource.ROOT);
        return Files.isRegularFile(worldPath.resolve("data").resolve("hive_data.dat"))
            || Files.isRegularFile(worldPath.resolve("DIM-1").resolve("data").resolve("hive_data.dat"))
            || Files.isRegularFile(worldPath.resolve("DIM1").resolve("data").resolve("hive_data.dat"));
    }

    private static void readLegacyHiveFile(Path path, ResourceKey<Level> dimension, List<LegacyHiveSnapshot> snapshots) {
        if (!Files.isRegularFile(path)) {
            return;
        }

        try {
            var root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            var container = root.contains("data", Tag.TAG_COMPOUND) ? root.getCompound("data") : root;
            var hives = findHiveSnapshots(container, dimension);
            if (hives.isEmpty()) {
                Alien.LOGGER.warn("Legacy hive recovery: found {} but could not find any hive snapshots", path);
                return;
            }
            snapshots.addAll(hives);
        } catch (IOException | RuntimeException e) {
            Alien.LOGGER.warn("Legacy hive recovery: failed to read {}", path, e);
        }
    }

    private static List<LegacyHiveSnapshot> findHiveSnapshots(CompoundTag tag, ResourceKey<Level> dimension) {
        var snapshots = new ArrayList<LegacyHiveSnapshot>();

        if (tag.contains("hives", Tag.TAG_LIST)) {
            collectHiveList(tag.getList("hives", Tag.TAG_COMPOUND), dimension, snapshots);
        }
        if (tag.contains("Hives", Tag.TAG_LIST)) {
            collectHiveList(tag.getList("Hives", Tag.TAG_COMPOUND), dimension, snapshots);
        }
        if (tag.contains("hives", Tag.TAG_COMPOUND)) {
            collectHiveMap(tag.getCompound("hives"), dimension, snapshots);
        }
        if (tag.contains("Hives", Tag.TAG_COMPOUND)) {
            collectHiveMap(tag.getCompound("Hives"), dimension, snapshots);
        }

        return snapshots;
    }

    private static void collectHiveList(
        ListTag hives,
        ResourceKey<Level> dimension,
        List<LegacyHiveSnapshot> snapshots
    ) {
        for (var i = 0; i < hives.size(); i++) {
            var snapshot = parseHiveSnapshot(null, hives.getCompound(i), dimension);
            if (snapshot != null) {
                snapshots.add(snapshot);
            }
        }
    }

    private static void collectHiveMap(
        CompoundTag hives,
        ResourceKey<Level> dimension,
        List<LegacyHiveSnapshot> snapshots
    ) {
        for (var hiveId : hives.getAllKeys()) {
            if (!hives.contains(hiveId, Tag.TAG_COMPOUND)) {
                continue;
            }
            var snapshot = parseHiveSnapshot(hiveId, hives.getCompound(hiveId), dimension);
            if (snapshot != null) {
                snapshots.add(snapshot);
            }
        }
    }

    private static @Nullable LegacyHiveSnapshot parseHiveSnapshot(
        @Nullable String fallbackId,
        CompoundTag tag,
        ResourceKey<Level> dimension
    ) {
        var center = readFirstBlockPos(tag, CENTER_POS_KEYS);
        if (center == null) {
            return null;
        }

        var members = new ArrayList<LegacyHiveMember>();
        collectMembers(tag, members);
        var leader = readUuid(tag, "HiveLeaderId");
        var variant = readVariant(tag);
        var biomass = readBiomass(tag);
        var reserves = readLegacyReserves(tag);
        var id = tag.contains("Id")
            ? tag.getString("Id")
            : tag.contains("id") ? tag.getString("id") : fallbackId != null ? fallbackId : center.toShortString();

        return new LegacyHiveSnapshot(id, dimension, center, leader, variant, biomass, members, reserves);
    }

    private static Map<EntityType<?>, Integer> readLegacyReserves(CompoundTag tag) {
        var result = new LinkedHashMap<EntityType<?>, Integer>();
        var foundSimpleReserveMap = false;
        for (var key : List.of("hiveMemberReserves", "HiveMemberReserves", "reserves", "Reserves")) {
            foundSimpleReserveMap |= readLegacyReserveCompound(tag, key, result);
        }

        if (!foundSimpleReserveMap) {
            var reserves = new EntityReserves();
            readLegacyReserveTag(tag, "hiveMemberReserves", reserves);
            readLegacyReserveTag(tag, "HiveMemberReserves", reserves);
            readLegacyReserveTag(tag, "reserves", reserves);
            readLegacyReserveTag(tag, "Reserves", reserves);

            for (var entry : reserves.getBackingMap().entrySet()) {
                var count = Math.max(0, entry.getValue());
                if (count > 0) {
                    result.put(entry.getKey(), count);
                }
            }
        }
        return result;
    }

    private static void readLegacyReserveTag(CompoundTag tag, String key, EntityReserves target) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            return;
        }
        EntityReserves.CODEC.decode(BLibCodecs.Schema.NBT, tag.getCompound(key))
            .inspectErr(failure -> Alien.LOGGER.warn("Legacy hive recovery: failed to read {} reserves: {}", key, failure))
            .ifOk(loaded -> target.putAll(loaded.getBackingMap()));
    }

    private static boolean readLegacyReserveCompound(
        CompoundTag tag,
        String key,
        Map<EntityType<?>, Integer> target
    ) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            return false;
        }

        var reserves = tag.getCompound(key);
        var foundAny = false;
        for (var entityTypeId : reserves.getAllKeys()) {
            var id = ResourceLocation.tryParse(entityTypeId);
            if (id == null) {
                continue;
            }
            var type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            var countTag = reserves.get(entityTypeId);
            if (type == null || !(countTag instanceof NumericTag numeric)) {
                continue;
            }
            var count = Math.max(0, numeric.getAsInt());
            if (count > 0) {
                target.merge(type, count, Math::max);
                foundAny = true;
            }
        }
        return foundAny;
    }

    private static void collectMembers(CompoundTag tag, List<LegacyHiveMember> members) {
        collectHiveMemberData(tag, members);

        var type = readEntityType(tag);
        var uuid = readAnyUuid(tag);
        if (type != null && uuid != null) {
            members.add(new LegacyHiveMember(uuid, type));
        }

        for (var key : tag.getAllKeys()) {
            var child = tag.get(key);
            if (child instanceof CompoundTag compound) {
                collectMembers(compound, members);
            } else if (child instanceof ListTag list) {
                for (var i = 0; i < list.size(); i++) {
                    if (list.get(i) instanceof CompoundTag compound) {
                        collectMembers(compound, members);
                    }
                }
            }
        }
    }

    private static void collectHiveMemberData(CompoundTag tag, List<LegacyHiveMember> members) {
        for (var key : List.of("HiveMemberData", "hiveMemberData", "members", "Members")) {
            if (!tag.contains(key, Tag.TAG_COMPOUND)) {
                continue;
            }
            var memberData = tag.getCompound(key);
            for (var memberId : memberData.getAllKeys()) {
                if (!memberData.contains(memberId, Tag.TAG_COMPOUND)) {
                    continue;
                }
                var uuid = readUuidString(memberId);
                var type = readEntityType(memberData.getCompound(memberId));
                if (uuid != null && type != null) {
                    members.add(new LegacyHiveMember(uuid, type));
                }
            }
        }
    }

    private static @Nullable EntityType<?> readEntityType(CompoundTag tag) {
        for (var key : List.of("entityType", "EntityType", "type", "Type")) {
            if (!tag.contains(key, Tag.TAG_STRING)) {
                continue;
            }
            var id = ResourceLocation.tryParse(tag.getString(key));
            if (id == null) {
                continue;
            }
            var type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            if (type != null) {
                return type;
            }
        }
        return null;
    }

    private static @Nullable UUID readAnyUuid(CompoundTag tag) {
        for (var key : List.of("uuid", "Uuid", "UUID", "entityId", "EntityId", "id", "Id")) {
            var uuid = readUuid(tag, key);
            if (uuid != null) {
                return uuid;
            }
        }
        return null;
    }

    private static @Nullable UUID readUuid(CompoundTag tag, String key) {
        try {
            if (tag.hasUUID(key)) {
                return tag.getUUID(key);
            }
            if (tag.contains(key, Tag.TAG_STRING)) {
                return readUuidString(tag.getString(key));
            }
            if (tag.contains(key, Tag.TAG_INT_ARRAY)) {
                var array = tag.getIntArray(key);
                if (array.length == 4) {
                    var most = ((long) array[0] << 32) | (array[1] & 0xffffffffL);
                    var least = ((long) array[2] << 32) | (array[3] & 0xffffffffL);
                    return new UUID(most, least);
                }
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }

    private static @Nullable UUID readUuidString(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static @Nullable BlockPos readBlockPos(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return null;
        }
        var value = tag.get(key);
        if (value instanceof net.minecraft.nbt.LongTag) {
            return BlockPos.of(tag.getLong(key));
        }
        if (value instanceof CompoundTag compound) {
            return new BlockPos(compound.getInt("X"), compound.getInt("Y"), compound.getInt("Z"));
        }
        if (value instanceof net.minecraft.nbt.IntArrayTag) {
            var array = tag.getIntArray(key);
            if (array.length >= 3) {
                return new BlockPos(array[0], array[1], array[2]);
            }
        }
        return null;
    }

    private static @Nullable BlockPos readFirstBlockPos(CompoundTag tag, List<String> keys) {
        for (var key : keys) {
            var pos = readBlockPos(tag, key);
            if (pos != null) {
                return pos;
            }
        }
        return null;
    }

    private static AlienVariant readVariant(CompoundTag tag) {
        if (tag.contains("VariantId", Tag.TAG_BYTE) || tag.contains("VariantId", Tag.TAG_INT)) {
            return AlienVariant.getById(tag.getInt("VariantId")).unwrapOr(AlienVariant.NORMAL);
        }
        if (tag.contains("Variant", Tag.TAG_STRING)) {
            try {
                return AlienVariant.valueOf(tag.getString("Variant").toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return AlienVariant.NORMAL;
            }
        }
        return AlienVariant.NORMAL;
    }

    private static int readBiomass(CompoundTag tag) {
        if (tag.contains("Biomass", Tag.TAG_INT)) {
            return tag.getInt("Biomass");
        }
        if (tag.contains("biomass", Tag.TAG_INT)) {
            return tag.getInt("biomass");
        }
        return 0;
    }

    private record CreatedLocation(
        LineageFactionData lineage,
        HiveLocation location
    ) {}

    private record LegacyHiveSnapshot(
        String id,
        ResourceKey<Level> dimension,
        BlockPos centerPos,
        @Nullable UUID leaderId,
        AlienVariant variant,
        int biomass,
        List<LegacyHiveMember> members,
        Map<EntityType<?>, Integer> reserves
    ) {}

    private record LegacyHiveMember(
        UUID uuid,
        @Nullable EntityType<?> entityType
    ) {

        boolean isQueen() {
            return entityType != null && entityType.is(AlienEntityTypeTags.QUEENS);
        }
    }
}
