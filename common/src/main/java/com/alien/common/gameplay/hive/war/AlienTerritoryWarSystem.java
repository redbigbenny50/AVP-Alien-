package com.alien.common.gameplay.hive.war;

import com.alien.Alien;
import com.alien.AlienResources;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.growth.HiveLocationClaims;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.alien.common.gameplay.hive.id.HiveLocationIds;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.faction.v1.RelationshipState;
import com.blib.api.common.territory.v1.TerritoryContest;
import com.blib.api.common.territory.v1.TerritoryContestListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

/**
 * Alien-side territory war behavior layered on top of BLib's generic contest system. Hives are territorial rivals
 * unless their lineages have submitted to the same empress.
 */
public final class AlienTerritoryWarSystem implements TerritoryContestListener {

    private static final AlienTerritoryWarSystem LISTENER = new AlienTerritoryWarSystem();

    private static final ResourceLocation REASON = AlienResources.location("territory_war");

    private static final int[][] CARDINAL_OFFSETS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    private AlienTerritoryWarSystem() {}

    public static void initialize() {
        Alien.MOD.territory().contests().registerPowerProvider(AlienTerritoryWarSystem::contestPower);
        Alien.MOD.territory().contests().registerListener(LISTENER);
    }

    public static void scanAndApply(MinecraftServer server) {
        var config = HiveLocationRegistry.INSTANCE.config();
        var maxStarts = Math.max(1, Math.min(8, config.maxClaimsPerScan()));
        var started = 0;

        for (var location : new ArrayList<>(HiveLocationRegistry.INSTANCE.all())) {
            if (started >= maxStarts) {
                return;
            }
            if (!canProjectWar(location)) {
                continue;
            }

            var level = server.getLevel(location.dimension());
            if (level == null) {
                continue;
            }

            if (tryStartBorderContest(level, location)) {
                started++;
            }
        }
    }

    public static boolean areAlienLineagesEnemies(
        com.alien.common.gameplay.entity.living.alien.Alien first,
        com.alien.common.gameplay.entity.living.alien.Alien second
    ) {
        var firstLineage = lineageFor(first.getUUID());
        var secondLineage = lineageFor(second.getUUID());

        return firstLineage != null
            && secondLineage != null
            && !firstLineage.equals(secondLineage)
            && !shareEmpressAuthority(firstLineage, secondLineage);
    }

    public static boolean areRivalLineages(ResourceLocation firstLineage, ResourceLocation secondLineage) {
        return !firstLineage.equals(secondLineage) && !shareEmpressAuthority(firstLineage, secondLineage);
    }

    private static boolean tryStartBorderContest(ServerLevel level, HiveLocation attacker) {
        for (var ownedChunk : attacker.claimedChunks()) {
            for (var offset : CARDINAL_OFFSETS) {
                var targetChunk = new ChunkPos(ownedChunk.x + offset[0], ownedChunk.z + offset[1]);

                if (attacker.claimedChunks().contains(targetChunk)) {
                    continue;
                }

                var defender = findDefender(level, attacker, targetChunk);
                if (defender == null) {
                    continue;
                }

                var attackerId = attacker.id().value();
                if (Alien.MOD.territory().contests().getContest(level, targetChunk, attackerId, defender).isPresent()) {
                    return false;
                }

                return Alien.MOD.territory().contests().startContest(level, targetChunk, attackerId, defender, REASON);
            }
        }

        return false;
    }

    private static ResourceLocation findDefender(ServerLevel level, HiveLocation attacker, ChunkPos targetChunk) {
        var playerOwner = Alien.MOD.territory().getPlayerClaimOwner(level, targetChunk);
        if (playerOwner != null) {
            var playerClaim = Alien.MOD.territory().getPlayerClaimFactionId(playerOwner);
            makeHostile(attacker.id().value(), playerClaim);
            makeHostile(attacker.lineageFactionId(), playerClaim);
            return playerClaim;
        }

        for (var claimantId : Alien.MOD.territory().getClaimants(level, targetChunk)) {
            if (!HiveLocationIds.isHiveLocationId(claimantId) || claimantId.equals(attacker.id().value())) {
                continue;
            }

            var defender = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(claimantId));
            if (defender == null || !defender.isAlive() || defender.isInhibited()) {
                continue;
            }

            var relationship = areRivalLineages(attacker.lineageFactionId(), defender.lineageFactionId())
                ? RelationshipState.HOSTILE
                : RelationshipState.ALLIED;
            Alien.MOD.factions().setRelationship(attacker.lineageFactionId(), defender.lineageFactionId(), relationship);
            Alien.MOD.factions().setRelationship(attacker.id().value(), defender.id().value(), relationship);

            if (relationship == RelationshipState.HOSTILE) {
                return defender.id().value();
            }
        }

        return null;
    }

    private static boolean canProjectWar(HiveLocation location) {
        if (!location.isAlive() || location.isInhibited() || location.claimedChunks().isEmpty()) {
            return false;
        }

        var faction = Alien.MOD.factions().get(location.lineageFactionId());
        if (faction == null || !(faction.data() instanceof LineageFactionData lineage) || !lineage.isAlive()) {
            return false;
        }

        return lineage.empressId() != null
            || countTaggedMembers(location, AlienEntityTypeTags.QUEENS) > 0
            || location.localReserves().getReliableCount() >= HiveLocationRegistry.INSTANCE.config().hiveSpawnerMinimumLoadedXenomorphs();
    }

    private static int contestPower(ServerLevel level, ChunkPos chunk, ResourceLocation factionId) {
        if (HiveLocationIds.isHiveLocationId(factionId)) {
            return hivePower(level, chunk, factionId);
        }

        var owner = Alien.MOD.territory().getPlayerClaimOwner(level, chunk);
        if (owner != null && Alien.MOD.territory().getPlayerClaimFactionId(owner).equals(factionId)) {
            return playerClaimPower(level, chunk, owner);
        }

        return 0;
    }

    private static int hivePower(ServerLevel level, ChunkPos chunk, ResourceLocation locationFactionId) {
        var location = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(locationFactionId));
        if (location == null || !location.isAlive() || location.isInhibited() || !location.dimension().equals(level.dimension())) {
            return 0;
        }

        var loadedPresence = countXenomorphsIn(level, chunk, location.lineageFactionId()) * 6;
        var canProject = location.claimedChunks().contains(chunk) || hasCardinalNeighborClaim(location, chunk);
        if (!canProject) {
            return loadedPresence;
        }

        var reservePressure = Math.max(1, location.localReserves().getReliableCount() / 8);
        var territoryPressure = Math.max(1, location.claimedChunks().size() / 16);
        var queenPressure = countTaggedMembers(location, AlienEntityTypeTags.QUEENS) > 0 ? 4 : 0;

        return loadedPresence + reservePressure + territoryPressure + queenPressure;
    }

    private static int playerClaimPower(ServerLevel level, ChunkPos chunk, UUID owner) {
        var power = 2;

        for (var player : level.players()) {
            if (!owner.equals(player.getUUID())) {
                continue;
            }

            var playerChunk = player.chunkPosition();
            var distance = Math.max(Math.abs(playerChunk.x - chunk.x), Math.abs(playerChunk.z - chunk.z));
            if (distance == 0) {
                power += 14;
            } else if (distance <= 1) {
                power += 6;
            }
        }

        return power;
    }

    private static int countXenomorphsIn(ServerLevel level, ChunkPos chunk, ResourceLocation lineageId) {
        var minX = chunk.getMinBlockX();
        var minZ = chunk.getMinBlockZ();
        var maxX = chunk.getMaxBlockX();
        var maxZ = chunk.getMaxBlockZ();
        var box = new AABB(minX, level.getMinBuildHeight(), minZ, maxX + 1, level.getMaxBuildHeight(), maxZ + 1);
        var count = 0;

        for (var entity : level.getEntitiesOfClass(com.alien.common.gameplay.entity.living.alien.Alien.class, box)) {
            if (!entity.getType().is(AlienEntityTypeTags.XENOMORPHS)) {
                continue;
            }
            if (Alien.MOD.factions().getFactionIds(entity.getUUID()).contains(lineageId)) {
                count++;
            }
        }

        return count;
    }

    private static int countTaggedMembers(HiveLocation location, net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> tag) {
        var count = 0;

        for (var entry : location.loadedMembersByType().entrySet()) {
            if (entry.getKey().is(tag)) {
                count += entry.getValue().size();
            }
        }

        count += location.localReserves().getReliableCountMatching(type -> type.is(tag));
        return count;
    }

    private static boolean hasCardinalNeighborClaim(HiveLocation location, ChunkPos chunk) {
        for (var offset : CARDINAL_OFFSETS) {
            if (location.claimedChunks().contains(new ChunkPos(chunk.x + offset[0], chunk.z + offset[1]))) {
                return true;
            }
        }
        return false;
    }

    private static void makeHostile(ResourceLocation first, ResourceLocation second) {
        Alien.MOD.factions().setRelationship(first, second, RelationshipState.HOSTILE);
    }

    private static boolean shareEmpressAuthority(ResourceLocation firstLineage, ResourceLocation secondLineage) {
        var first = lineageData(firstLineage);
        var second = lineageData(secondLineage);

        return first != null
            && second != null
            && first.empressId() != null
            && Objects.equals(first.empressId(), second.empressId());
    }

    private static LineageFactionData lineageData(ResourceLocation lineageId) {
        var faction = Alien.MOD.factions().get(lineageId);
        if (faction == null || !(faction.data() instanceof LineageFactionData lineage) || !lineage.isAlive()) {
            return null;
        }
        return lineage;
    }

    private static ResourceLocation lineageFor(UUID entityId) {
        // Prefer the lineage the entity's LOCATION membership names. A membership set is unordered, so an entity
        // that ended up in two lineages (a founder who didn't shed her old one, a worker mid-migration) would
        // otherwise resolve by iteration order and could read as an enemy of its own hive. A location membership
        // is unambiguous - it points at exactly one lineage - so it wins over a bare lineage-faction membership.
        ResourceLocation lineageFallback = null;
        for (var factionId : Alien.MOD.factions().getFactionIds(entityId)) {
            if (HiveLocationIds.isHiveLocationId(factionId)) {
                var location = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(factionId));
                if (location != null) {
                    return location.lineageFactionId();
                }
            } else if (lineageFallback == null && LineageIds.isLineageId(factionId)) {
                lineageFallback = factionId; // remember, but keep looking for a location membership
            }
        }

        return lineageFallback;
    }

    @Override
    public void onContestStarted(ServerLevel level, TerritoryContest contest) {
        if (!REASON.equals(contest.reason())) {
            return;
        }

        Alien.LOGGER.info(
            "Alien territory war started: attacker={}, defender={}, chunk=[{}, {}], dimension={}, reason={}",
            contest.attacker(),
            contest.defender(),
            contest.chunkX(),
            contest.chunkZ(),
            contest.dimension(),
            contest.reason()
        );
    }

    @Override
    public void onContestResolved(ServerLevel level, TerritoryContest contest, ResourceLocation winner, ResourceLocation loser) {
        if (REASON.equals(contest.reason())) {
            Alien.LOGGER.info(
                "Alien territory war resolved: winner={}, loser={}, chunk=[{}, {}], dimension={}",
                winner,
                loser,
                contest.chunkX(),
                contest.chunkZ(),
                contest.dimension()
            );
        }

        var chunk = contest.chunkPos();

        if (HiveLocationIds.isHiveLocationId(loser)) {
            var losingLocation = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(loser));
            if (losingLocation != null && losingLocation.claimedChunks().contains(chunk)) {
                HiveLocationClaims.release(level, losingLocation, chunk);
            }
        }

        if (HiveLocationIds.isHiveLocationId(winner)) {
            var winningLocation = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(winner));
            if (winningLocation != null && winningLocation.isAlive() && !winningLocation.claimedChunks().contains(chunk)) {
                HiveLocationClaims.claim(level, winningLocation, chunk, level.getGameTime());
            }
        }
    }
}
