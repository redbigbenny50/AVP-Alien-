package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.faction.HiveMemberLocationResolver;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.party.EggDutyGuard;
import com.alien.common.gameplay.hive.structure.HostChamberSlots;
import com.alien.common.gameplay.hive.structure.HostParking;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * A host loose INSIDE the hive is a chore, not an expedition.
 * <p>
 * Livestock wanders in through the vents; a player cuts a captive out of the webbing and it walks off down a corridor.
 * Nothing used to deal with either - {@link HostSensors} only ever let a dispatched {@code HostHunt} party member
 * target a host, so the hive's own workers strolled straight past a cow standing in the nursery.
 * <p>
 * This is deliberately NOT a seventh party type. There is no dispatch, no reserve drain, no member cap: any drone that
 * happens to be near a loose host simply picks it up, the way workers already react to a loose egg. Nobody needs to be
 * <i>sent</i> to deal with a sheep that wandered into the hive.
 * <h2>The guardrails, and why each one is there</h2>
 * <ul>
 * <li><b>Built structure only, not the whole claim.</b> The load-bearing one. On a flat world a claim is enormous and
 * full of surface animals; if the sweep could see the whole claim, every drone in the hive would abandon its chores to
 * fetch sheep out of a field. This is the same failure the worker leash was added to fix.</li>
 * <li><b>Only if a chamber spot is free.</b> Otherwise a drone grabs a cow and stands holding it forever. Same rule the
 * host-hunt dispatch already applies. With the larder full, loose livestock is simply left to mill about.</li>
 * <li><b>Drones only.</b> {@code canEntityRideAlien} permits HOSTS on {@code Drone} and nowhere else - a runner
 * physically cannot carry one, so letting it target one would strand it.</li>
 * <li><b>Never while hauling an egg.</b> A carrier mid-run would drop its egg for a cow.</li>
 * </ul>
 */
public final class InteriorSweepDuty {

    private InteriorSweepDuty() {}

    /**
     * Per-hive cache of "does a free host-chamber spot exist", refreshed at most every {@link #FREE_SPOT_TTL_TICKS}.
     * <p>
     * TPS guard. {@code isSweeper} backs a GOAP sensor, so it runs for EVERY drone, continuously - and
     * {@code HostChamberSlots.firstFreeSpot} costs a 32x32 block sweep per chamber group plus an entity query per spot.
     * Thirty idle drones asking that every sensor pass is tens of thousands of block reads per second for a question
     * whose answer changes rarely. A two-second-stale answer is harmless for a chore: worst case a drone starts toward
     * a cow two seconds after the larder filled, and the delivery path re-checks the real spot anyway.
     */
    private static final Map<com.alien.common.gameplay.hive.id.HiveLocationId, long[]> FREE_SPOT_CACHE = new HashMap<>();

    private static final long FREE_SPOT_TTL_TICKS = 40L; // 2 seconds

    /**
     * Per-hive cache of "is any loose host actually inside the built structure", same TTL discipline as
     * {@link #FREE_SPOT_CACHE}.
     * <p>
     * The other half of the TPS guard. Even with the free-spot answer cached, a healthy hive keeps {@code isSweeper}
     * true for every idle drone inside it - and each of those drones then runs the 32-radius entity scan in
     * {@code HostSensors.findCaptureTarget} EVERY sensor pass, hunting for a cow that almost never exists. One AABB
     * query per hive per 2 seconds answers "is there anything to sweep at all"; the per-drone scans only wake up when
     * there genuinely is. Parked (webbed) and carried hosts are excluded, or a full larder would hold the flag true
     * forever and defeat the gate.
     */
    private static final Map<com.alien.common.gameplay.hive.id.HiveLocationId, long[]> LOOSE_HOST_CACHE = new HashMap<>();

    /** Can this xenomorph take a host it finds lying around inside the hive? */
    public static boolean isSweeper(Xenomorph xenomorph) {
        if (!(xenomorph.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        // Only a drone can physically carry a host home.
        if (!xenomorph.getType().is(AlienEntityTypeTags.DRONES)) {
            return false;
        }

        // Egg first. A hauler mid-run does not drop its cargo for a cow.
        if (EggDutyGuard.isOnEggDuty(xenomorph)) {
            return false;
        }

        var location = HiveMemberLocationResolver.reserveReturnLocation(xenomorph);
        if (location == null || !location.isAlive()) {
            return false;
        }

        // We must be inside the hive proper ourselves - this is a chore, not a patrol. Checked BEFORE the chamber
        // scan: this is a map lookup, the spot scan is a 1000-block sweep.
        if (!isInsideHive(location, xenomorph)) {
            return false;
        }

        // Nothing loose to sweep = nothing to do. Checked before the chamber scan; both are cached per hive.
        if (!hasLooseHostCached(serverLevel, location)) {
            return false;
        }

        // Nowhere to put it = do not pick it up. Cached per hive with a short TTL - see FREE_SPOT_CACHE.
        return hasFreeSpotCached(serverLevel, location);
    }

    /** One entity query over the hive's built footprint: any live, un-parked, un-carried non-alien inside? */
    private static boolean hasLooseHostCached(ServerLevel serverLevel, HiveLocation location) {
        long now = serverLevel.getGameTime();
        var entry = LOOSE_HOST_CACHE.get(location.id());
        if (entry != null && now - entry[0] < FREE_SPOT_TTL_TICKS) {
            return entry[1] != 0L;
        }
        boolean found = false;
        var chunks = location.structurePieceByChunk().keySet();
        if (!chunks.isEmpty()) {
            int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (var chunk : chunks) {
                minX = Math.min(minX, chunk.getMinBlockX());
                minZ = Math.min(minZ, chunk.getMinBlockZ());
                maxX = Math.max(maxX, chunk.getMaxBlockX());
                maxZ = Math.max(maxZ, chunk.getMaxBlockZ());
            }
            var box = new net.minecraft.world.phys.AABB(
                minX,
                location.hiveFloorY(),
                minZ,
                maxX + 1,
                location.hiveCeilingY() + 1,
                maxZ + 1
            );
            found = !serverLevel.getEntitiesOfClass(
                LivingEntity.class,
                box,
                candidate -> !(candidate instanceof com.alien.common.gameplay.entity.living.alien.Alien)
                    && candidate.isAlive()
                    && !candidate.isPassenger()
                    && !HostParking.isParked(candidate)
                    && isInsideHive(location, candidate)
            ).isEmpty();
        }
        LOOSE_HOST_CACHE.put(location.id(), new long[] { now, found ? 1L : 0L });
        LOOSE_HOST_CACHE.entrySet().removeIf(e -> now - e.getValue()[0] >= FREE_SPOT_TTL_TICKS * 10);
        return found;
    }

    /** The cached free-spot answer for this hive, recomputed at most once per TTL window. */
    private static boolean hasFreeSpotCached(ServerLevel serverLevel, HiveLocation location) {
        long now = serverLevel.getGameTime();
        var entry = FREE_SPOT_CACHE.get(location.id());
        if (entry != null && now - entry[0] < FREE_SPOT_TTL_TICKS) {
            return entry[1] != 0L;
        }
        boolean hasFree = HostChamberSlots.firstFreeSpot(serverLevel, location) != null;
        FREE_SPOT_CACHE.put(location.id(), new long[] { now, hasFree ? 1L : 0L });
        // Prune dead hives so the cache cannot grow unbounded across a long server session.
        FREE_SPOT_CACHE.entrySet().removeIf(e -> now - e.getValue()[0] >= FREE_SPOT_TTL_TICKS * 10);
        return hasFree;
    }

    /** The hive this xenomorph belongs to, or null. */
    public static @Nullable HiveLocation hiveOf(Xenomorph xenomorph) {
        var location = HiveMemberLocationResolver.reserveReturnLocation(xenomorph);
        return location != null && location.isAlive() ? location : null;
    }

    /**
     * Is this entity inside the hive's built interior?
     * <p>
     * Deliberately the BUILT STRUCTURE ({@code structurePieceByChunk} + {@code withinSlab}), not the claim. The claim
     * is territory; the structure is the building. A sheep in a field the hive happens to own is a host-hunt's problem,
     * not a chore.
     * <p>
     * Asked of two different things: of a HOST, to decide whether it is loose in the corridors and should be swept up;
     * and of a CARRIER, to decide whether it can walk its captive straight to the chamber instead of hauling it out to
     * a surface vent.
     */
    public static boolean isInsideHive(HiveLocation location, LivingEntity entity) {
        var pos = entity.blockPosition();
        return location.structurePieceByChunk().containsKey(new ChunkPos(pos))
            && location.withinSlab(pos.getY());
    }
}
