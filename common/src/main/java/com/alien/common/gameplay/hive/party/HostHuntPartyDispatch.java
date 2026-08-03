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
 * Dispatch for {@link HiveParty.HostHunt}. Unlike {@link SurfacePartyDispatch}, this party needs an existing
 * near-surface vent to spawn from at all (see {@code AVP_Party_System_Design.md} § 3) — if the hive has none yet
 * (general surface spawn hasn't seeded any), dispatch simply fails and retries next cycle. Runs from the same 20-tick
 * cadence as the other party dispatchers.
 */
public final class HostHuntPartyDispatch {

    private HostHuntPartyDispatch() {}

    public static void tryRun(MinecraftServer server, HiveLocation location, HiveConfig config) {
        // Three-day cooldown: a party of this kind is an EVENT, not a conveyor belt. Back-to-back dispatches
        // drained the reserves as fast as the hive could breed them, so the population never settled and
        // never got promoted into warriors or prowlers.
        var world = server.getLevel(location.dimension());
        if (world == null) {
            return;
        }
        if (HiveLocation.onPartyCooldown(location.lastHostHuntPartyTick(), world.getGameTime())) {
            return;
        }

        var serverLevel = server.getLevel(location.dimension());
        if (serverLevel == null) {
            return;
        }

        for (var party : location.parties()) {
            if (party instanceof HiveParty.HostHunt) {
                // One at a time per hive.
                return;
            }
        }

        // No point hunting hosts we have nowhere to put: require a free host-chamber spot.
        if (com.alien.common.gameplay.hive.structure.HostChamberSlots.firstFreeSpot(serverLevel, location) == null) {
            return;
        }

        // Host hunts go out the FRONT DOOR and come back through it. Surface vents only - a captive is carried back
        // here, and a frontier vent in a cave is not somewhere a drone can drag a struggling villager to.
        var surfaceVents = PartyVentUtil.findSurfaceVents(serverLevel, location);
        if (surfaceVents.isEmpty()) {
            return;
        }
        // HOST-SEEKING TIER PICK ([stated]): instead of a random front door, spawn from the vent nearest the
        // loaded hosts - and when every vent is a TIER away from them (the nether's stacked shelves; the tester
        // case was piglins one level below the whole vent network), the party digs its own door first: a fresh
        // SURFACE vent planted on the hosts' shelf, guaranteed, the same idiom as the bastion structure vent.
        var spawnPos = surfaceVents.get(serverLevel.random.nextInt(surfaceVents.size()));
        var hostAnchor = nearestLoadedHost(serverLevel, location);
        if (hostAnchor != null) {
            BlockPos best = null;
            var bestDist = Double.MAX_VALUE;
            for (var vent : surfaceVents) {
                var dist = vent.distSqr(hostAnchor);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = vent;
                }
            }
            if (best != null) {
                spawnPos = best;
            }
            if (best == null || Math.abs(best.getY() - hostAnchor.getY()) > TIER_GAP_BLOCKS) {
                var planted = plantVentOnHostTier(serverLevel, location, hostAnchor);
                if (planted != null) {
                    spawnPos = planted;
                }
            }
        }

        // Size scales with claims but is CAPPED (bonus spitters ride on top of this budget).
        var hostCap = com.alien.common.gameplay.hive.empress.EmpressCaps.scale(location, config.hostHuntPartyMaxSize());
        var desiredSize = Math.min(
            hostCap,
            Math.max(
                1,
                Math.round(
                    config.hostHuntPartyBaseSize()
                        + config.hostHuntPartySizePerClaimedChunk() * location.claimedChunks().size()
                )
            )
        );

        var composition = drainComposition(location, (int) desiredSize, 0);
        if (composition.getCount() <= 0) {
            return;
        }

        var currentTick = serverLevel.getGameTime();
        var party = new HiveParty.HostHunt(
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
        location.setLastHostHuntPartyTick(world.getGameTime());

        Alien.LOGGER.info(
            "Hive: dispatched host hunt party for location {} — {} members from vent at {}",
            location.id(),
            spawnedCount,
            spawnPos
        );
    }

    /** Host parties are DRONES: they carry hosts, they do not fight for sport. */
    private static EntityReserves drainComposition(HiveLocation location, int desiredCount, int bonusSpitterCount) {
        var reserves = location.localReserves();
        var composition = new EntityReserves();

        var droneTypes = new ArrayList<EntityType<?>>();
        for (var type : reserves.getAvailableEntityTypes()) {
            if (type.is(AlienEntityTypeTags.DRONES)) {
                droneTypes.add(type);
            }
        }
        drainUpTo(reserves, composition, droneTypes, desiredCount);

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

    private static int materialize(ServerLevel level, HiveLocation location, HiveParty.HostHunt party, BlockPos spawnPos) {
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

    /** Vents this much above/below the prey are a different shelf - the party can see dinner but not reach it. */
    private static final int TIER_GAP_BLOCKS = 10;

    /** How far around the hive centre the dispatcher looks for loaded prey (full column height - tiers matter). */
    private static final double HOST_SCAN_RADIUS = 96.0D;

    /** The loaded, living host nearest the hive centre, any tier - or null when none are loaded. */
    private static net.minecraft.core.BlockPos nearestLoadedHost(
        net.minecraft.server.level.ServerLevel serverLevel,
        HiveLocation location
    ) {
        var center = location.centerPos();
        var box = new net.minecraft.world.phys.AABB(
            center.getX() - HOST_SCAN_RADIUS,
            serverLevel.getMinBuildHeight(),
            center.getZ() - HOST_SCAN_RADIUS,
            center.getX() + HOST_SCAN_RADIUS,
            serverLevel.getMaxBuildHeight(),
            center.getZ() + HOST_SCAN_RADIUS
        );
        net.minecraft.core.BlockPos best = null;
        var bestDist = Double.MAX_VALUE;
        for (var candidate : serverLevel.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box)) {
            if (!candidate.isAlive() || candidate.isPassenger() || candidate.isBaby()) {
                continue; // dead, already on someone's back, or a baby (never capturable - see HostCaptureRules)
            }
            var type = candidate.getType();
            if (
                !type.is(com.alien.common.registry.tag.AlienEntityTypeTags.HOSTS)
                    && !type.is(com.alien.common.registry.tag.AlienEntityTypeTags.RUNNER_HOSTS)
            ) {
                continue;
            }
            var dist = candidate.distanceToSqr(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate.blockPosition();
            }
        }
        return best;
    }

    /** Digs the party's own front door on the prey's shelf. Null when no valid vent spot exists there. */
    private static net.minecraft.core.BlockPos plantVentOnHostTier(
        net.minecraft.server.level.ServerLevel serverLevel,
        HiveLocation location,
        net.minecraft.core.BlockPos hostAnchor
    ) {
        var variant = location.lineageVariantOrNull();
        if (variant == null) {
            return null;
        }
        var ventPos = SurfacePartyLifecycleTask.findSurfaceVentSpot(
            serverLevel,
            new net.minecraft.world.level.ChunkPos(hostAnchor),
            hostAnchor,
            variant
        );
        if (ventPos == null) {
            return null;
        }
        com.alien.common.gameplay.hive.vent.VentPlacement.place(
            serverLevel,
            ventPos,
            com.alien.common.data.AlienVariantTypes.getFor(variant),
            com.alien.common.gameplay.hive.vent.VentKind.SURFACE,
            location
        );
        Alien.LOGGER.info(
            "Hive: host hunt party dug its own vent at {} on the prey's tier (hosts near {}) for {}",
            ventPos,
            hostAnchor,
            location.id()
        );
        return ventPos;
    }
}
