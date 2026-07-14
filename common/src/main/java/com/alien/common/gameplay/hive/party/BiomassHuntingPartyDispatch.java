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
import java.util.List;

/**
 * Dispatch for {@link HiveParty.BiomassHunting}. Unlike {@link SurfacePartyDispatch}, this party needs an existing
 * near-surface vent to spawn from at all (see {@code AVP_Party_System_Design.md} § 3) — if the hive has none yet
 * (general surface spawn hasn't seeded any), dispatch simply fails and retries next cycle. Runs from the same 20-tick
 * cadence as the other party dispatchers.
 */
public final class BiomassHuntingPartyDispatch {

    private BiomassHuntingPartyDispatch() {}

    public static void tryRun(MinecraftServer server, HiveLocation location, HiveConfig config) {
        // Three-day cooldown: a party of this kind is an EVENT, not a conveyor belt. Back-to-back dispatches
        // drained the reserves as fast as the hive could breed them, so the population never settled and
        // never got promoted into warriors or prowlers.
        var world = server.getLevel(location.dimension());
        if (world == null) {
            return;
        }
        if (HiveLocation.onPartyCooldown(location.lastBiomassPartyTick(), world.getGameTime())) {
            return;
        }

        var serverLevel = server.getLevel(location.dimension());
        if (serverLevel == null) {
            return;
        }

        for (var party : location.parties()) {
            if (party instanceof HiveParty.BiomassHunting) {
                // One at a time per hive.
                return;
            }
        }

        // Biomass hunters go wherever the hive opens onto the world: the surface doors, or a frontier vent at a cave
        // mouth.
        var surfaceVents = PartyVentUtil.findPartyVents(serverLevel, location);
        if (surfaceVents.isEmpty()) {
            return;
        }
        var spawnPos = surfaceVents.get(serverLevel.random.nextInt(surfaceVents.size()));

        // Size scales with claims but is CAPPED (bonus spitters ride on top of this budget).
        var biomassCap = com.alien.common.gameplay.hive.structure.HiveRouter.isEmpressInfluenced(location)
            ? config.biomassHuntingPartyMaxSizeEmpress()
            : config.biomassHuntingPartyMaxSize();
        var desiredSize = Math.min(
            biomassCap,
            Math.max(
                1,
                Math.round(
                    config.biomassHuntingPartyBaseSize()
                        + config.biomassHuntingPartySizePerClaimedChunk() * location.claimedChunks().size()
                )
            )
        );

        var composition = drainComposition(location, (int) desiredSize, config.biomassHuntingPartyBonusSpitterCount());
        if (composition.getCount() <= 0) {
            return;
        }

        var currentTick = serverLevel.getGameTime();
        var party = new HiveParty.BiomassHunting(
            HivePartyId.fresh(),
            location.id(),
            location.dimension(),
            composition,
            currentTick
        );

        var spawnedCount = materialize(serverLevel, location, party, spawnPos);
        if (spawnedCount <= 0) {
            refund(location, composition);
            return;
        }

        location.parties().add(party);
        location.setLastBiomassPartyTick(world.getGameTime());

        Alien.LOGGER.info(
            "Hive: dispatched biomass hunting party for location {} — {} members from vent at {}",
            location.id(),
            spawnedCount,
            spawnPos
        );
    }

    /**
     * Guaranteed Prowlers + Warriors up to {@code desiredCount}, plus up to {@code bonusSpitterCount} Spitters on top.
     */
    private static EntityReserves drainComposition(HiveLocation location, int desiredCount, int bonusSpitterCount) {
        var reserves = location.localReserves();
        var composition = new EntityReserves();

        var coreTypes = new ArrayList<EntityType<?>>();
        for (var type : reserves.getAvailableEntityTypes()) {
            if (type.is(AlienEntityTypeTags.PROWLERS) || type.is(AlienEntityTypeTags.WARRIORS)) {
                coreTypes.add(type);
            }
        }
        drainUpTo(reserves, composition, coreTypes, desiredCount);

        if (bonusSpitterCount > 0) {
            var spitterTypes = new ArrayList<EntityType<?>>();
            for (var type : reserves.getAvailableEntityTypes()) {
                if (type.is(AlienEntityTypeTags.SPITTERS)) {
                    spitterTypes.add(type);
                }
            }
            drainUpTo(reserves, composition, spitterTypes, bonusSpitterCount);
        }

        return composition;
    }

    private static void drainUpTo(
        com.alien.common.gameplay.hive.location.HiveLocationReserves reserves,
        EntityReserves composition,
        List<EntityType<?>> candidateTypes,
        int count
    ) {
        if (candidateTypes.isEmpty()) {
            return;
        }
        var drained = 0;
        while (drained < count) {
            var progressedThisPass = false;
            for (var type : candidateTypes) {
                if (drained >= count) {
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

    private static int materialize(ServerLevel level, HiveLocation location, HiveParty.BiomassHunting party, BlockPos spawnPos) {
        var spawnedCount = 0;
        for (var type : new ArrayList<>(party.composition().getAvailableEntityTypes())) {
            var count = party.composition().getCount(type);
            for (var i = 0; i < count; i++) {
                var entity = type.create(level);
                if (entity == null) {
                    continue;
                }
                // The vent is a BEACON for its chunk, not a doorway. Surface anywhere standable in that chunk so it
                // no longer matters that the vent itself is buried: members used to materialise INSIDE SOLID
                // GROUND at the vent's own Y and could never path a single step.
                var emergePos = PartyVentUtil.surfaceEmergeSpot(level, spawnPos);
                if (emergePos == null) {
                    // Nowhere dry to surface (an ocean vent). Do NOT fall back to the vent block - that buries
                    // them again. Skip: the member stays in the composition and is refunded at resolution.
                    entity.discard();
                    continue;
                }
                var jitterX = emergePos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 2.0;
                var jitterZ = emergePos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 2.0;
                entity.moveTo(jitterX, emergePos.getY(), jitterZ, level.random.nextFloat() * 360.0F, 0.0F);
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
