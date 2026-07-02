package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.gameplay.hive.convoy.Convoy;
import com.alien.common.gameplay.hive.faction.HiveLocationFactionProvisioner;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.growth.HiveLocationClaims;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.location.HiveLocationRemovalReason;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Consolidates same-lineage hive locations when their territory overlaps. This is not a location death: the absorbed
 * location's territory and owned population become part of the survivor.
 */
public final class HiveLocationMergeHandler {

    private HiveLocationMergeHandler() {}

    public static @Nullable HiveLocation mergeSameLineage(
        ServerLevel level,
        ResourceLocation lineageFactionId,
        Collection<HiveLocation> candidates
    ) {
        var lineageFaction = Alien.MOD.factions().get(lineageFactionId);
        if (lineageFaction == null || !(lineageFaction.data() instanceof LineageFactionData lineage)) {
            return null;
        }

        var locationsById = new LinkedHashMap<HiveLocationId, HiveLocation>();
        for (var location : candidates) {
            if (
                location == null
                    || !location.isAlive()
                    || !location.dimension().equals(level.dimension())
                    || !location.lineageFactionId().equals(lineageFactionId)
            ) {
                continue;
            }
            locationsById.put(location.id(), location);
        }

        if (locationsById.size() < 2) {
            return null;
        }

        var locations = new ArrayList<>(locationsById.values());
        var survivor = pickSurvivor(locations);
        HiveLocationFactionProvisioner.ensure(survivor, lineage);

        var absorbedCount = 0;
        var transferredChunks = 0;
        for (var absorbed : locations) {
            if (absorbed.id().equals(survivor.id())) {
                continue;
            }
            transferredChunks += absorb(level, lineage, survivor, absorbed);
            absorbedCount++;
        }

        Alien.LOGGER.info(
            "Hive: merged {} same-lineage hive location(s) into {} for lineage {} ({} chunks transferred)",
            absorbedCount,
            survivor.id(),
            lineageFactionId,
            transferredChunks
        );

        return survivor;
    }

    private static HiveLocation pickSurvivor(ArrayList<HiveLocation> locations) {
        return locations
            .stream()
            .max(
                Comparator
                    .comparingInt((HiveLocation location) -> location.claimedChunks().size())
                    .thenComparingLong(HiveLocation::ageInTicks)
                    .thenComparing(location -> location.id().value(), Comparator.reverseOrder())
            )
            .orElseThrow();
    }

    private static int absorb(
        ServerLevel level,
        LineageFactionData lineage,
        HiveLocation survivor,
        HiveLocation absorbed
    ) {
        HiveLocationFactionProvisioner.ensure(survivor, lineage);

        var transferredChunks = transferClaims(level, survivor, absorbed);
        survivor.decoratedChunks().addAll(absorbed.decoratedChunks());
        transferResources(survivor, absorbed);
        transferReserves(survivor, absorbed);
        transferMemberIndexes(survivor, absorbed);
        transferLocationFactionMembers(survivor, absorbed);
        remapConvoys(lineage, absorbed.id(), survivor);

        absorbed.setRemovalReason(new HiveLocationRemovalReason.Migrated(survivor.id().value()));
        HiveLocationRegistry.INSTANCE.unregister(absorbed.id());
        lineage.removeLocation(absorbed.id());

        var absorbedFactionId = absorbed.id().value();
        if (Alien.MOD.factions().exists(absorbedFactionId)) {
            Alien.MOD.factions().remove(absorbedFactionId);
        }

        return transferredChunks;
    }

    private static int transferClaims(ServerLevel level, HiveLocation survivor, HiveLocation absorbed) {
        var chunks = new ArrayList<>(absorbed.claimedChunks());
        var transferred = 0;

        for (var chunk : chunks) {
            var alreadyClaimedBySurvivor = survivor.claimedChunks().contains(chunk);
            var absorbedClaimTick = absorbed.chunkClaimTicks().getOrDefault(chunk, level.getGameTime());
            var survivorClaimTick = survivor.chunkClaimTicks().get(chunk);

            HiveLocationClaims.release(level, absorbed, chunk, true);

            if (alreadyClaimedBySurvivor) {
                if (survivorClaimTick == null) {
                    survivor.chunkClaimTicks().put(chunk, absorbedClaimTick);
                } else {
                    survivor.chunkClaimTicks().put(chunk, Math.min(survivorClaimTick, absorbedClaimTick));
                }
                HiveLocationRegistry.INSTANCE.onChunkClaimed(survivor, chunk);
                HiveLocationClaims.syncTerritoryClaim(level, survivor, chunk);
                continue;
            }

            if (HiveLocationClaims.claim(level, survivor, chunk, absorbedClaimTick)) {
                transferred++;
            }
        }

        return transferred;
    }

    private static void transferResources(HiveLocation survivor, HiveLocation absorbed) {
        survivor.setBiomass(saturatedAdd(survivor.biomass(), absorbed.biomass()));
        survivor.setRoyalJelly(saturatedAdd(survivor.royalJelly(), absorbed.royalJelly()));
        survivor.setScourgeJelly(saturatedAdd(survivor.scourgeJelly(), absorbed.scourgeJelly()));
        survivor.setRoyalJellyAccumulator(
            saturatedAdd(survivor.royalJellyAccumulator(), absorbed.royalJellyAccumulator())
        );
        survivor.setQueenScourgeAccumulator(
            saturatedAdd(survivor.queenScourgeAccumulator(), absorbed.queenScourgeAccumulator())
        );
        survivor.setHarbingerScourgeAccumulator(
            saturatedAdd(survivor.harbingerScourgeAccumulator(), absorbed.harbingerScourgeAccumulator())
        );
        survivor.setPeakXenomorphCount(saturatedAdd(survivor.peakXenomorphCount(), absorbed.peakXenomorphCount()));
        survivor.setPeakDecayElapsedTicks(Math.min(survivor.peakDecayElapsedTicks(), absorbed.peakDecayElapsedTicks()));
        survivor.setEvacuatingRemainingTicks(
            Math.max(survivor.evacuatingRemainingTicks(), absorbed.evacuatingRemainingTicks())
        );
        survivor.setNoContactTicksAccrued(Math.min(survivor.noContactTicksAccrued(), absorbed.noContactTicksAccrued()));
        if (survivor.founderId() == null) {
            survivor.setFounderId(absorbed.founderId());
        }
    }

    private static int saturatedAdd(int a, int b) {
        var sum = (long) a + b;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    private static long saturatedAdd(long a, long b) {
        var sum = a + b;
        return sum < 0L ? Long.MAX_VALUE : sum;
    }

    private static void transferReserves(HiveLocation survivor, HiveLocation absorbed) {
        for (var type : new ArrayList<>(absorbed.localReserves().getAvailableEntityTypes())) {
            var count = absorbed.localReserves().getCount(type);
            if (count <= 0) {
                continue;
            }
            if (survivor.localReserves().addReturningMember(type, count)) {
                absorbed.localReserves().underlying().add(type, -count);
            }
        }
    }

    private static void transferMemberIndexes(HiveLocation survivor, HiveLocation absorbed) {
        mergeMemberMap(survivor.knownMembersByType(), absorbed.knownMembersByType());
        mergeMemberMap(survivor.loadedMembersByType(), absorbed.loadedMembersByType());
        absorbed.knownMembersByType().clear();
        absorbed.loadedMembersByType().clear();
    }

    private static void mergeMemberMap(
        Map<EntityType<?>, Set<UUID>> target,
        Map<EntityType<?>, Set<UUID>> source
    ) {
        for (var entry : source.entrySet()) {
            target.computeIfAbsent(entry.getKey(), $ -> new HashSet<>()).addAll(entry.getValue());
        }
    }

    private static void transferLocationFactionMembers(HiveLocation survivor, HiveLocation absorbed) {
        var survivorFaction = Alien.MOD.factions().get(survivor.id().value());
        var absorbedFaction = Alien.MOD.factions().get(absorbed.id().value());
        if (survivorFaction == null || absorbedFaction == null) {
            return;
        }

        var members = new ArrayList<>(absorbedFaction.membership().getMembers());
        for (var member : members) {
            absorbedFaction.membership().removeMember(member);
            survivorFaction.membership().addMember(member);
        }
    }

    private static void remapConvoys(LineageFactionData lineage, HiveLocationId absorbedId, HiveLocation survivor) {
        var updated = new ArrayList<Convoy>(lineage.convoys().size());
        for (var convoy : lineage.convoys()) {
            updated.add(remapConvoy(convoy, absorbedId, survivor));
        }
        lineage.convoys().clear();
        lineage.convoys().addAll(updated);
    }

    private static Convoy remapConvoy(Convoy convoy, HiveLocationId absorbedId, HiveLocation survivor) {
        if (convoy instanceof Convoy.Reinforcement reinforcement) {
            var destinationWasAbsorbed = reinforcement.destinationLocationId().equals(absorbedId);
            return new Convoy.Reinforcement(
                reinforcement.id(),
                reinforcement.lineageFactionId(),
                reinforcement.dimension(),
                remapLocationId(reinforcement.sourceLocationId(), absorbedId, survivor.id()),
                remapLocationId(reinforcement.destinationLocationId(), absorbedId, survivor.id()),
                reinforcement.currentPos(),
                destinationWasAbsorbed ? survivor.centerPos() : reinforcement.destinationPos(),
                reinforcement.composition(),
                reinforcement.materializedMembers(),
                reinforcement.dispatchedTick()
            );
        }

        if (convoy instanceof Convoy.Migration migration) {
            var destinationWasAbsorbed = migration.destinationLocationId().equals(absorbedId);
            return new Convoy.Migration(
                migration.id(),
                migration.lineageFactionId(),
                migration.dimension(),
                remapLocationId(migration.sourceLocationId(), absorbedId, survivor.id()),
                remapLocationId(migration.destinationLocationId(), absorbedId, survivor.id()),
                migration.currentPos(),
                destinationWasAbsorbed ? survivor.centerPos() : migration.destinationPos(),
                migration.composition(),
                migration.materializedMembers(),
                migration.biomassPayload(),
                migration.carriesEmpress(),
                migration.dispatchedTick()
            );
        }

        if (convoy instanceof Convoy.Raid raid) {
            var returnLocationWasAbsorbed = absorbedId.equals(raid.returnLocationId());
            return new Convoy.Raid(
                raid.id(),
                raid.lineageFactionId(),
                raid.dimension(),
                remapLocationId(raid.sourceLocationId(), absorbedId, survivor.id()),
                raid.targetPlayerId(),
                raid.currentPos(),
                raid.lastKnownTargetPos(),
                raid.composition(),
                raid.materializedMembers(),
                raid.warningIssued(),
                raid.nextWaveIndex(),
                raid.activeWaveIndex(),
                raid.activeWaveInitialCount(),
                raid.waveBreakStartedTick(),
                raid.frenziedJoinCount(),
                raid.targetDeathCount(),
                raid.deathWindowStartedTick(),
                raid.lastTargetDeathTick(),
                raid.targetDownSinceTick(),
                raid.lossConfirmedTick(),
                raid.targetWasAlive(),
                raid.returningHome(),
                raid.returnHomeReason(),
                returnLocationWasAbsorbed ? survivor.id() : raid.returnLocationId(),
                returnLocationWasAbsorbed ? survivor.centerPos() : raid.returnPos(),
                raid.dispatchedTick(),
                raid.expiresAtTick()
            );
        }

        return convoy;
    }

    private static HiveLocationId remapLocationId(
        HiveLocationId id,
        HiveLocationId absorbedId,
        HiveLocationId survivorId
    ) {
        return id.equals(absorbedId) ? survivorId : id;
    }
}
