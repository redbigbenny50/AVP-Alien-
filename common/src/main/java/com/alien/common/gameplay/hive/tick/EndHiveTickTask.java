package com.alien.common.gameplay.hive.tick;

import com.alien.Alien;
import com.alien.common.gameplay.hive.dimension.EndStyleHiveRules;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.lifecycle.LocationDeathHandler;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.spawning.HiveLoadedSpawner;
import com.alien.common.gameplay.hive.spawning.ReserveSpawnUtil;
import com.alien.common.gameplay.hive.vent.VentKind;
import com.alien.common.gameplay.hive.vent.VentPlacement;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * The end-style hive's own tick: everything a fortress hive does that a normal hive does not. Runs from
 * {@code HiveLocationLoadedTickTask}'s End branch, AFTER defense (aggro + vent defense + attack parties) and vent
 * banking, INSTEAD OF every autonomy system below the branch.
 * <ul>
 * <li><b>PERSISTENCE SWEEP</b> - [stated] "we want to make sure the xenos dont just despawn": every loaded member of an
 * End hive is made persistence-required, so a queenless fortress never bleeds members to vanilla despawn.</li>
 * <li><b>WORKER DEPLOYMENT</b> - up to {@link EndStyleHiveRules#ACTIVE_WORKER_CAP} workers stand active in the world,
 * drawn from the identity bank (the exact xenomorphs the player supplied). They are the hive's entire visible labor
 * force.</li>
 * <li><b>WORKER VENT PLACEMENT</b> - each claimed chunk gets ONE worker-placed vent, once ever: a broken worker vent is
 * never replaced by the hive ([stated] "if its broken thats it"); only a player-placed vent of the strain restores that
 * chunk (and those bind themselves through the vent block entity as normal).</li>
 * <li><b>THE SEVEN-DAY CULL CLOCK</b> - a hive reduced to PURE BANK (zero vents, zero living surface members) survives
 * {@link EndStyleHiveRules#CULL_GRACE_MILLIS} of real wall-clock time; reconnecting it clears the clock, abandonment
 * culls the hive and drops the territory.</li>
 * </ul>
 */
public final class EndHiveTickTask {

    private EndHiveTickTask() {}

    /** Fortress chores run on the hive's coarse cadence - none of this is a reaction. */
    private static final long CHORE_INTERVAL_TICKS = 200L;

    /** How far around a worker the vent-spot search looks for an open cell against a solid face. */
    private static final int VENT_SPOT_SEARCH_RADIUS = 3;

    public static void run(
        MinecraftServer server,
        ServerLevel level,
        HiveLocation location,
        LineageFactionData lineage,
        long currentTick
    ) {
        if (currentTick % CHORE_INTERVAL_TICKS != 0L) {
            return;
        }
        var members = loadedMembers(level, location);
        sweepPersistence(members);
        deployWorkers(level, location, members);
        placeWorkerVents(level, location, lineage, members);
        enforceEndermanResinRule(level, location, lineage, members);
        tickCullClock(level, location, lineage, members);
    }

    /** Living loaded members of this location inside the claimed footprint, full height. */
    private static List<com.alien.common.gameplay.entity.living.alien.Alien> loadedMembers(
        ServerLevel level,
        HiveLocation location
    ) {
        var chunks = location.claimedChunks();
        if (chunks.isEmpty()) {
            return List.of();
        }
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var chunk : chunks) {
            minX = Math.min(minX, chunk.getMinBlockX());
            minZ = Math.min(minZ, chunk.getMinBlockZ());
            maxX = Math.max(maxX, chunk.getMaxBlockX());
            maxZ = Math.max(maxZ, chunk.getMaxBlockZ());
        }
        var box = new AABB(minX, level.getMinBuildHeight(), minZ, maxX + 1, level.getMaxBuildHeight(), maxZ + 1);
        var locationFactionId = location.id().value();
        return level.getEntitiesOfClass(
            com.alien.common.gameplay.entity.living.alien.Alien.class,
            box,
            alien -> {
                if (!alien.isAlive()) {
                    return false;
                }
                for (var factionId : Alien.MOD.factions().getFactionIds(alien.getUUID())) {
                    if (factionId.equals(locationFactionId)) {
                        return true;
                    }
                }
                return false;
            }
        );
    }

    /** [stated] "we want to make sure the xenos dont just despawn" - self-healing, covers every join route. */
    private static void sweepPersistence(List<com.alien.common.gameplay.entity.living.alien.Alien> members) {
        for (var member : members) {
            if (!member.isPersistenceRequired()) {
                member.setPersistenceRequired();
            }
        }
    }

    /**
     * Keeps up to {@link EndStyleHiveRules#ACTIVE_WORKER_CAP} workers standing in the world, drawn from the identity
     * bank. Emergence prefers a vent mouth (the fiction: they climb out of the ducts); a ventless young hive falls back
     * to the hive center so the FIRST workers can exist to place the first vents.
     */
    private static void deployWorkers(
        ServerLevel level,
        HiveLocation location,
        List<com.alien.common.gameplay.entity.living.alien.Alien> members
    ) {
        var active = 0;
        for (var member : members) {
            if (member.getType().is(AlienEntityTypeTags.DRONES)) {
                active++;
            }
        }
        if (active >= EndStyleHiveRules.ACTIVE_WORKER_CAP) {
            return;
        }

        var workerType = workerTypeFor(location);
        if (workerType == null) {
            return;
        }

        var emergence = emergencePos(level, location);
        if (emergence == null) {
            return;
        }

        // One per chore pass - the crew assembles over half a minute rather than materializing as a crowd.
        var worker = HiveLoadedSpawner.trySpawnIdentityReserve(level, location, workerType, emergence);
        if (worker != null) {
            ReserveSpawnUtil.markSpawnedFromReserves(worker);
            if (worker instanceof com.alien.common.gameplay.entity.living.alien.Alien alienWorker) {
                alienWorker.setPersistenceRequired();
            }
        }
    }

    /**
     * ONE worker vent per claimed chunk, ONCE EVER ([stated] "only the workers can place a vent in a chunk and if its
     * broken thats it"). A worker standing in an unvented chunk it has never vented "places" one at the nearest valid
     * open cell beside it. Player-placed strain vents restore broken chunks instead - those bind themselves.
     */
    private static void placeWorkerVents(
        ServerLevel level,
        HiveLocation location,
        LineageFactionData lineage,
        List<com.alien.common.gameplay.entity.living.alien.Alien> members
    ) {
        for (var member : members) {
            if (!member.getType().is(AlienEntityTypeTags.DRONES)) {
                continue;
            }
            var chunk = member.chunkPosition();
            if (!location.claimedChunks().contains(chunk)) {
                continue;
            }
            var packed = chunk.toLong();
            if (location.endWorkerVentedChunks().contains(packed)) {
                continue; // this chunk had its one worker vent - broken means broken.
            }
            if (!chunkHasVent(location, chunk) == false) {
                continue; // already vented (by an earlier pass or by the player) - record nothing, spend nothing.
            }
            var spot = findVentSpot(level, member.blockPosition());
            if (spot == null) {
                continue;
            }
            VentPlacement.place(
                level,
                spot,
                com.alien.common.data.AlienVariantTypes.getFor(lineage.variant()),
                VentKind.SURFACE,
                location
            );
            location.endWorkerVentedChunks().add(packed);
            Alien.LOGGER.info(
                "End hive at {}: worker placed the vent for chunk {} at {} ({} of {} chunks vented).",
                location.centerPos(),
                chunk,
                spot,
                location.endWorkerVentedChunks().size(),
                location.claimedChunks().size()
            );
            return; // one vent per chore pass - visible, gradual work.
        }
    }

    /** Whether any registered vent of this hive sits inside the given chunk. */
    private static boolean chunkHasVent(HiveLocation location, ChunkPos chunk) {
        for (var vent : location.ventManager().allVents()) {
            if (vent.getX() >> 4 == chunk.x && vent.getZ() >> 4 == chunk.z) {
                return true;
            }
        }
        return false;
    }

    /** An open cell resting against a solid face, near the worker - the same validity VentPlacement demands. */
    private static BlockPos findVentSpot(ServerLevel level, BlockPos near) {
        for (int r = 0; r <= VENT_SPOT_SEARCH_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        var pos = near.offset(dx, dy, dz);
                        if (VentPlacement.isOpen(level, pos) && VentPlacement.restsOnSolidFace(level, pos)) {
                            return pos;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Emergence at the nearest vent mouth when one exists; the hive center for a ventless young fortress. */
    private static BlockPos emergencePos(ServerLevel level, HiveLocation location) {
        var vent = com.alien.common.gameplay.hive.economy.BroodBankTask.nearestVent(location, location.centerPos());
        if (vent != null) {
            var mouth = com.alien.common.gameplay.hive.vent.HiveVents.emergencePosNear(level, vent);
            if (mouth != null) {
                return mouth;
            }
        }
        var center = location.centerPos();
        // Snap to the top solid surface at the center column so a fresh worker never spawns inside the island.
        var surface = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, center);
        return surface.getY() > level.getMinBuildHeight() ? surface : center;
    }

    /** The strain's drone type - the End's worker caste. */
    private static EntityType<?> workerTypeFor(HiveLocation location) {
        var variant = location.lineageVariantOrNull();
        if (variant == null) {
            return null;
        }
        return switch (variant) {
            case NORMAL -> com.alien.common.registry.init.AlienEntityTypes.DRONE.get();
            case NETHER -> com.alien.common.registry.init.AlienEntityTypes.NETHER_DRONE.get();
            case ABERRANT -> com.alien.common.registry.init.AlienEntityTypes.ABERRANT_DRONE.get();
            case IRRADIATED -> com.alien.common.registry.init.AlienEntityTypes.IRRADIATED_DRONE.get();
        };
    }

    /**
     * [stated] "if you remove all vents, and kill all surface members, youre left with a bank of hive members keeping
     * the hive alive. there should be a time of 7 real world days where if new active members arent added back or a
     * vent installed for members to arrive the hive is culled and the territory drops again."
     */
    private static void tickCullClock(
        ServerLevel level,
        HiveLocation location,
        LineageFactionData lineage,
        List<com.alien.common.gameplay.entity.living.alien.Alien> members
    ) {
        var pureBank = location.ventManager().ventCount() == 0 && members.isEmpty();

        if (!pureBank) {
            if (location.endPureBankSinceMillis() != 0L) {
                location.setEndPureBankSinceMillis(0L);
                Alien.LOGGER.info(
                    "End hive at {}: reconnected (vent or active members restored) - the cull clock is cleared.",
                    location.centerPos()
                );
            }
            return;
        }

        var now = System.currentTimeMillis();
        if (location.endPureBankSinceMillis() == 0L) {
            location.setEndPureBankSinceMillis(now);
            Alien.LOGGER.info(
                "End hive at {}: reduced to pure bank (no vents, no surface members). Culled in 7 real days unless "
                    + "a vent is placed or active members return.",
                location.centerPos()
            );
            return;
        }

        if (now - location.endPureBankSinceMillis() >= EndStyleHiveRules.CULL_GRACE_MILLIS) {
            Alien.LOGGER.info(
                "End hive at {}: culled - seven real days as a pure bank with no reconnection. The territory drops.",
                location.centerPos()
            );
            LocationDeathHandler.killAdmin(
                level,
                location,
                lineage,
                "end-style cull: 7 real days as pure bank (no vents, no surface members)"
            );
        }
    }

    /**
     * [stated] "have them ignore enderman unless they trespass into the hives territory onto any resin ... its a good
     * rule for them not other mobs." ENDERMAN-ONLY, by design: an enderman standing ON the hive's own resin inside a
     * claimed chunk is a valid target for the nearest few members; the aggro is issued once per chore pass, so the
     * moment it ports off the resin nothing re-issues and the grudge lapses on its own - the boundary the hive enforces
     * is exactly the boundary the player can SEE.
     */
    private static void enforceEndermanResinRule(
        ServerLevel level,
        HiveLocation location,
        LineageFactionData lineage,
        List<com.alien.common.gameplay.entity.living.alien.Alien> members
    ) {
        if (members.isEmpty()) {
            return;
        }
        var resinTag = switch (lineage.variant()) {
            case NORMAL -> com.alien.common.registry.tag.AlienBlockTags.NORMAL_RESIN;
            case NETHER -> com.alien.common.registry.tag.AlienBlockTags.NETHER_RESIN;
            case ABERRANT -> com.alien.common.registry.tag.AlienBlockTags.ABERRANT_RESIN;
            case IRRADIATED -> com.alien.common.registry.tag.AlienBlockTags.IRRADIATED_RESIN;
        };
        var chunks = location.claimedChunks();
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var chunk : chunks) {
            minX = Math.min(minX, chunk.getMinBlockX());
            minZ = Math.min(minZ, chunk.getMinBlockZ());
            maxX = Math.max(maxX, chunk.getMaxBlockX());
            maxZ = Math.max(maxZ, chunk.getMaxBlockZ());
        }
        var box = new AABB(minX, level.getMinBuildHeight(), minZ, maxX + 1, level.getMaxBuildHeight(), maxZ + 1);
        for (
            var enderman : level.getEntitiesOfClass(
                net.minecraft.world.entity.monster.EnderMan.class,
                box,
                e -> e.isAlive()
                    && chunks.contains(e.chunkPosition())
                    && level.getBlockState(e.blockPosition().below()).is(resinTag)
            )
        ) {
            var set = 0;
            for (var member : members) {
                if (member.getTarget() == null && member.distanceToSqr(enderman) <= 48.0 * 48.0) {
                    member.setTarget(enderman);
                    if (++set >= 3) {
                        break; // a few answer - the island does not empty for one trespasser.
                    }
                }
            }
        }
    }
}
