package com.alien.common.gameplay.hive.war;

import com.alien.Alien;
import com.alien.common.gameplay.hive.economy.CasteResolver;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.spawning.HiveLoadedSpawner;
import com.alien.common.gameplay.hive.spawning.ReserveSpawnUtil;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The last two rooms. [stated] "queen guards will appear at the queen along with harbingers", and "harbingers are too
 * large to engage in hallway combat but will appear when the enemy enters the raid chamber or if they enter the queens
 * chamber... The raid party attacking an enemy hive wont spawn the harbinger like normal unless they enter the enemy
 * raid chamber and/or queen chamber."
 * <p>
 * TWO DIFFERENT ANSWERS TO TWO DIFFERENT THREATS. The guard is a STANDING watch: while the hive is at war a handful of
 * heavies hold the throne room whether or not anyone has arrived, because the whole point of a queen guard is that it
 * is already there when the attackers get in. The harbinger is a REACTION: it stays out of the war entirely - out of
 * the corridors, out of the mobilisation draw, out of the strike forces - until an enemy is physically standing in the
 * raid chamber or the throne room, and only then does it come out.
 * <p>
 * ENEMIES HERE MEANS MEMBERS OF A HIVE WE ARE AT WAR WITH. Players are deliberately not a trigger: raids are how a hive
 * answers a player, that machinery already exists, and making a wandering player pull a harbinger would rewrite it.
 */
public final class ThroneDefense {

    /** Cadence. Two seconds - fast enough that the reveal feels like a reaction to walking in. */
    private static final long INTERVAL_TICKS = 40L;

    /** Heavies held at the throne while the hive is at war. */
    private static final int QUEEN_GUARD_SIZE = 3;

    /** Slack above and below the hive slab when sweeping a chamber for intruders. */
    private static final int SANCTUM_BAND_MARGIN = 8;

    /** How far from the throne a guard counts as being ON the throne. */
    private static final double GUARD_STATION_RANGE = 12.0;

    /** Guards prefer the queen's own caste order - the biggest thing the hive can put in the doorway. */
    private static final List<net.minecraft.tags.TagKey<EntityType<?>>> GUARD_ORDER = List.of(
        AlienEntityTypeTags.PRAETORIANS,
        AlienEntityTypeTags.CRUSHERS,
        AlienEntityTypeTags.WARRIORS
    );

    /** Harbingers already revealed, per hive. They stay out until the war ends - never re-banked mid-fight. */
    private static final Map<HiveLocation, Set<UUID>> REVEALED = Collections.synchronizedMap(new WeakHashMap<>());

    private ThroneDefense() {}

    public static boolean shouldFire(long currentTick) {
        return currentTick % INTERVAL_TICKS == 0L;
    }

    public static void run(ServerLevel level, HiveLocation location) {
        if (!location.isAlive() || !location.isAtWar()) {
            REVEALED.remove(location);
            return;
        }

        holdTheThrone(level, location);
        revealHarbingers(level, location);
    }

    /**
     * Keeps a standing guard at the queen. Guards are drawn from the bank rather than the field so an offensive never
     * strips the throne room - the wave that leaves and the watch that stays are separate accounts.
     */
    private static void holdTheThrone(ServerLevel level, HiveLocation location) {
        var throne = thronePos(location);
        int standing = 0;
        for (var member : level.getEntitiesOfClass(Mob.class, box(throne, GUARD_STATION_RANGE))) {
            if (isOurs(location, member) && member.getType().is(AlienEntityTypeTags.XENOMORPHS)) {
                standing++;
            }
        }
        if (standing >= QUEEN_GUARD_SIZE) {
            return;
        }

        var variant = location.lineageVariantOrNull();
        if (variant == null) {
            return;
        }
        for (var caste : GUARD_ORDER) {
            if (standing >= QUEEN_GUARD_SIZE) {
                return;
            }
            var type = CasteResolver.entityTypeForCaste(variant, caste);
            if (type == null || location.localReserves().getReliableCount(type) <= 0) {
                continue;
            }
            var guard = HiveLoadedSpawner.trySpawnIdentityReserve(level, location, type, throne);
            if (guard instanceof Mob mob) {
                ReserveSpawnUtil.markSpawnedFromReserves(mob);
                if (location.isInLastStand()) {
                    WarMobilization.applyLastStand(mob);
                }
                standing++;
            }
        }
    }

    /**
     * The reveal. Walks the hive's own raid chambers and throne, and if an enemy member is standing in one of them,
     * puts a harbinger there - once per chamber, and only while that harbinger is alive.
     */
    private static void revealHarbingers(ServerLevel level, HiveLocation location) {
        var revealed = REVEALED.computeIfAbsent(location, key -> Collections.synchronizedSet(new HashSet<>()));
        revealed.removeIf(id -> !(level.getEntity(id) instanceof Mob harbinger) || !harbinger.isAlive());
        if (!revealed.isEmpty()) {
            return; // one is already out and fighting; the hive does not stack them
        }

        var variant = location.lineageVariantOrNull();
        var type = variant == null ? null : CasteResolver.entityTypeForCaste(variant, AlienEntityTypeTags.HARBINGERS);
        if (type == null || location.localReserves().getReliableCount(type) <= 0) {
            return; // nothing bred yet - the raid chamber has to have produced one
        }

        var enemyMembers = enemyMembers(location);
        if (enemyMembers.isEmpty()) {
            return; // nobody's members are loaded - there is nothing that could be standing in a chamber
        }
        for (var chunk : sanctumChunks(location)) {
            var intruder = intruderIn(level, location, chunk, enemyMembers);
            if (intruder == null) {
                continue;
            }
            var at = new BlockPos(chunk.getMiddleBlockX(), intruder.blockPosition().getY(), chunk.getMiddleBlockZ());
            var spawned = HiveLoadedSpawner.trySpawnIdentityReserve(level, location, type, at);
            if (!(spawned instanceof Mob harbinger)) {
                return;
            }
            ReserveSpawnUtil.markSpawnedFromReserves(harbinger);
            if (location.isInLastStand()) {
                WarMobilization.applyLastStand(harbinger);
            }
            harbinger.setTarget(intruder);
            revealed.add(harbinger.getUUID());
            Alien.LOGGER.info(
                "War: hive {} revealed a harbinger at [{}, {}] — an enemy reached its {}.",
                location.id(),
                chunk.x,
                chunk.z,
                isThroneChunk(location, chunk) ? "queen's chamber" : "raid chamber"
            );
            return;
        }
    }

    /** The only two rooms that pull a harbinger: the raid chambers and the queen's own. */
    private static List<ChunkPos> sanctumChunks(HiveLocation location) {
        var chunks = new ArrayList<ChunkPos>();
        for (var entry : location.structurePieceByChunk().entrySet()) {
            var id = entry.getValue();
            if (id.contains("chamber_raid") || id.contains("core")) {
                chunks.add(entry.getKey());
            }
        }
        return chunks;
    }

    private static boolean isThroneChunk(HiveLocation location, ChunkPos chunk) {
        var id = location.structurePieceByChunk().get(chunk);
        return id != null && id.contains("core");
    }

    /**
     * A living member of a hive this one is at war with, standing in the given chunk.
     * <p>
     * Y IS CLAMPED TO THE HIVE'S OWN SLAB. The chambers are carved inside it by construction, so a full-height scan of
     * every sanctum chunk - which is what this used to do, twice a second, per hive at war - was paying for the whole
     * world column to find intruders that can only ever be in one band of it.
     */
    @Nullable
    private static Mob intruderIn(ServerLevel level, HiveLocation location, ChunkPos chunk, Set<UUID> enemyMembers) {
        if (enemyMembers.isEmpty()) {
            return null;
        }
        var box = new AABB(
            chunk.getMinBlockX(),
            Math.max(level.getMinBuildHeight(), location.hiveFloorY() - SANCTUM_BAND_MARGIN),
            chunk.getMinBlockZ(),
            chunk.getMaxBlockX() + 1,
            Math.min(level.getMaxBuildHeight(), location.hiveCeilingY() + SANCTUM_BAND_MARGIN),
            chunk.getMaxBlockZ() + 1
        );
        for (var candidate : level.getEntitiesOfClass(Mob.class, box)) {
            if (candidate.isAlive() && enemyMembers.contains(candidate.getUUID())) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Every member of every hive we are at war with, collected ONCE per cycle. The old shape asked the registry for
     * each enemy for each candidate entity in each chamber - the same lookups repeated hundreds of times a second for
     * an answer that changes about once a minute.
     */
    private static Set<UUID> enemyMembers(HiveLocation location) {
        var members = new HashSet<UUID>();
        for (var enemyId : location.warEnemies()) {
            var enemy = HiveLocationRegistry.INSTANCE.get(HiveLocationId.of(enemyId));
            if (enemy == null) {
                continue;
            }
            for (var set : enemy.loadedMembersByType().values()) {
                members.addAll(set);
            }
        }
        return members;
    }

    private static boolean isOurs(HiveLocation location, Mob member) {
        return isMember(location, member);
    }

    private static boolean isMember(HiveLocation location, Mob member) {
        var members = location.loadedMembersByType().get(member.getType());
        return members != null && members.contains(member.getUUID());
    }

    /** The throne: the hive floor at its centre, which is the core piece by construction. */
    private static BlockPos thronePos(HiveLocation location) {
        var centre = location.centerPos();
        return new BlockPos(centre.getX(), location.hiveFloorY() + 1, centre.getZ());
    }

    private static AABB box(BlockPos centre, double range) {
        return new AABB(centre).inflate(range);
    }
}
