package com.alien.common.gameplay.hive.war;

import com.alien.Alien;
import com.alien.common.gameplay.hive.economy.CasteResolver;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.spawning.HiveLoadedSpawner;
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
 * !!! REVERSED Aug 24 - PLAYERS ARE NOW A TRIGGER, AND ALWAYS WERE MEANT TO BE. [stated] "if the player is standing in
 * the throne room or the raid room while the hive is attacking them ie defense then the harbinger can spawn. This is
 * intended to be a way for the player to sabotage the hives ability to do raids and make scourge xenos. it was always
 * intented to be how they can trigger it."
 * <p>
 * ⚠⚠ THE OLD RULE WAS THE OPPOSITE AND IT MADE THIS ENTIRE FILE INERT FOR PLAYERS. run() returned immediately unless
 * isAtWar() - the hive-vs-hive war flag, which a player attack never sets - and the reveal only ever scanned members of
 * hives we were at war with. So the harbinger fired correctly in a hive war and had never once fired for a player,
 * which is exactly the "mixed reports" pattern.
 * <p>
 * ⭐ THE FEATURE IS SABOTAGE, NOT AMBUSH. The harbinger is the hive's raid key and its only scourge-jelly factory, so
 * baiting it out of the raid chamber and killing it is how a player shuts down raids and the scourge tier. That only
 * works if the player can make it come out.
 * <p>
 * ⚠ NOT MERE PRESENCE. [stated] "while the hive is attacking them ie defense" - so the trigger reuses
 * {@code HiveTerritoryAggroTask.intrudersInTerritory}, the same set the hive already fields vent defenders against,
 * which excludes creative and spectator players by construction. Sneaking in does not pull a harbinger; being fought
 * over does.
 */
public final class ThroneDefense {

    /** Cadence. Two seconds - fast enough that the reveal feels like a reaction to walking in. */
    private static final long INTERVAL_TICKS = 40L;

    /** Heavies held at the throne while the hive is at war. */
    private static final int QUEEN_GUARD_SIZE = 3;

    /**
     * How far the challenge line carries. Deliberately tighter than the roar itself.
     * <p>
     * The roar is played at volume 8, which vanilla makes audible for a long way - that is the point of a roar. The
     * TEXT is addressed to the people it is about, so it goes to players close enough to be in the fight rather than to
     * everyone who can hear it. This is the dial if it should reach further.
     * </p>
     */
    private static final double CHALLENGE_MESSAGE_RANGE = 48.0;

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

    /**
     * How long the sanctum must be free of anything hostile before a revealed harbinger stands down.
     * <p>
     * Long enough that stepping out to heal or fetch ammo does not hand the hive its raid key back.
     * </p>
     */
    private static final long REBANK_QUIET_TICKS = 20L * 60L;

    /** When the sanctum last became free of hostiles, per hive. Absent while anything hostile is present. */
    private static final Map<HiveLocation, Long> SANCTUM_CLEAR_SINCE = Collections.synchronizedMap(new WeakHashMap<>());

    /** Harbingers already revealed, per hive. They stay out until the fight is over - never re-banked mid-fight. */
    private static final Map<HiveLocation, Set<UUID>> REVEALED = Collections.synchronizedMap(new WeakHashMap<>());

    private ThroneDefense() {}

    public static boolean shouldFire(long currentTick) {
        return currentTick % INTERVAL_TICKS == 0L;
    }

    public static void run(ServerLevel level, HiveLocation location) {
        if (!location.isAlive()) {
            REVEALED.remove(location);
            SANCTUM_CLEAR_SINCE.remove(location);
            return;
        }

        // ⚠ THE STANDING GUARD IS STILL WAR-ONLY. It is a garrison held in anticipation of an enemy hive's strike
        // force; a player raid is answered by the vents and the raid machinery, and posting three heavies at the
        // throne whenever anyone walks past would be a different feature.
        if (location.isAtWar()) {
            holdTheThrone(level, location);
        }

        revealHarbingers(level, location);
        rebankIdleHarbingers(level, location);
    }

    /**
     * Keeps a standing guard at the queen. Guards are drawn from the bank rather than the field so an offensive never
     * strips the throne room - the wave that leaves and the watch that stays are separate accounts.
     */
    /** Throne guards each hive has drawn from its bank, until they die or leave the world. */
    private static final java.util.Map<HiveLocation, java.util.Set<java.util.UUID>> GUARDS_DRAWN =
        Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static void holdTheThrone(ServerLevel level, HiveLocation location) {
        var throne = thronePos(level, location);
        int standing = 0;
        var counted = new HashSet<java.util.UUID>();
        for (var member : level.getEntitiesOfClass(Mob.class, box(throne, GUARD_STATION_RANGE))) {
            if (isOurs(location, member) && member.getType().is(AlienEntityTypeTags.XENOMORPHS)) {
                standing++;
                counted.add(member.getUUID());
            }
        }
        // 🚨 Oct 2 - GUARDS WHO LEFT THE THRONE STILL COUNT. Only xenos within 12 blocks of the throne were counted, so
        // a guard that chased an attacker out of the room (or could not reach one and wandered) was replaced from the
        // bank every two seconds for the whole war - the same drain shape as the vent defenders. A guard drawn from
        // the bank counts against the watch until it dies or leaves the world.
        var drawn = GUARDS_DRAWN.computeIfAbsent(location, key -> Collections.synchronizedSet(new HashSet<>()));
        drawn.removeIf(id -> !(level.getEntity(id) instanceof Mob guard) || !guard.isAlive());
        for (var id : drawn) {
            if (!counted.contains(id)) {
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
            var guard = HiveLoadedSpawner.trySpawnFromReserves(level, location, type, throne);
            if (guard instanceof Mob mob) {
                drawn.add(mob.getUUID());
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

        var hostiles = hostilesFor(level, location);
        if (hostiles.isEmpty()) {
            return; // nothing hostile is loaded - there is nothing that could be standing in a chamber
        }
        for (var chunk : sanctumChunks(location)) {
            var intruder = intruderIn(level, location, chunk, hostiles);
            if (intruder == null) {
                continue;
            }
            var at = new BlockPos(chunk.getMiddleBlockX(), intruder.blockPosition().getY(), chunk.getMiddleBlockZ());
            var spawned = HiveLoadedSpawner.trySpawnFromReserves(level, location, type, at);
            if (!(spawned instanceof Mob harbinger)) {
                return;
            }
            if (location.isInLastStand()) {
                WarMobilization.applyLastStand(harbinger);
            }
            harbinger.setTarget(intruder);
            revealed.add(harbinger.getUUID());
            announceChallenge(level, harbinger);
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

        // \u2b50\u2b50 BUILD-FREE: THE SANCTUM IS THE GROUND AROUND THE QUEEN, because there is no throne chamber to
        // stand in. [stated] "harbinger would appear around the queen during an attack yes if they get within range
        // of her maybe 16 block radius."
        //
        // \u26a0 THE BEHAVIOUR IS UNCHANGED AND WAS ALREADY WHAT HE WANTED - his own earlier ruling is in this
        // class's header: the harbinger "is a REACTION: it stays out of the war entirely" and only appears when
        // attackers reach the chamber. Only the ANCHOR breaks in this mode. Keying it to her position keeps it a
        // reaction to REACHING HER rather than a hive-wide alarm - a fight forty chunks away in the same territory
        // must not produce one.
        if (com.alien.common.gameplay.hive.config.BuildFreeMode.isEnabled()) {
            var radius = com.alien.common.gameplay.hive.location.HiveLocationRegistry.INSTANCE.config()
                .buildFreeHarbingerRevealRadius();
            var center = new ChunkPos(location.centerPos());
            var chunkRadius = Math.max(0, radius >> 4);
            for (var dx = -chunkRadius; dx <= chunkRadius; dx++) {
                for (var dz = -chunkRadius; dz <= chunkRadius; dz++) {
                    chunks.add(new ChunkPos(center.x + dx, center.z + dz));
                }
            }
            return chunks;
        }

        for (var entry : location.structurePieceByChunk().entrySet()) {
            var id = entry.getValue();
            if (id.contains("chamber_raid") || id.contains("core")) {
                chunks.add(entry.getKey());
            }
        }
        return chunks;
    }

    private static boolean isThroneChunk(HiveLocation location, ChunkPos chunk) {
        // \u26a0 BUILD-FREE: the queen's own chunk IS the throne - there is no "core" piece to name one.
        if (com.alien.common.gameplay.hive.config.BuildFreeMode.isEnabled()) {
            return chunk.equals(new ChunkPos(location.centerPos()));
        }
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
    private static net.minecraft.world.entity.LivingEntity intruderIn(
        ServerLevel level,
        HiveLocation location,
        ChunkPos chunk,
        Set<UUID> enemyMembers
    ) {
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
        // ⚠ LivingEntity, NOT Mob. A Player is not a Mob, so the old Mob-typed sweep could not have found one even
        // if the hostile set had contained players.
        for (var candidate : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box)) {
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
    /**
     * The roar and the line, at the moment it comes out.
     * <p>
     * [stated] "there was supposed to be a chat message too when it happened "The harbinger accepts youre challenge"
     * and then it plays the harbinger roar file."
     * </p>
     * <p>
     * ⚠ SURVIVAL AND ADVENTURE ONLY. [stated] "only survival players count for this not creative or spectator." The
     * TRIGGER already honours that - intrudersInTerritory skips creative and spectator - but the MESSAGE is a separate
     * sweep of nearby players and would otherwise have told a spectator that a harbinger had accepted a challenge they
     * never made.
     * </p>
     */
    private static void announceChallenge(ServerLevel level, Mob harbinger) {
        level.playSound(
            null,
            harbinger.getX(),
            harbinger.getY(),
            harbinger.getZ(),
            com.alien.common.registry.init.AlienSoundEvents.ENTITY_HARBINGER_ROAR_3.get(),
            net.minecraft.sounds.SoundSource.HOSTILE,
            8.0F,
            1.0F
        );

        var line = net.minecraft.network.chat.Component
            .literal("The harbinger accepts your challenge.")
            .withStyle(net.minecraft.ChatFormatting.DARK_RED);

        var rangeSqr = CHALLENGE_MESSAGE_RANGE * CHALLENGE_MESSAGE_RANGE;

        for (var player : level.players()) {
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }
            if (player.distanceToSqr(harbinger) > rangeSqr) {
                continue;
            }
            player.displayClientMessage(line, false);
        }
    }

    /**
     * Everything this hive would presently fight inside its own walls: members of hives it is at war with, plus the
     * intruders it is already fielding vent defenders against.
     * <p>
     * ⭐ REUSES {@code HiveTerritoryAggroTask.intrudersInTerritory} RATHER THAN ROLLING ITS OWN PLAYER TEST. That is the
     * single definition of "the hive is defending against you" in the mod - it is what the vent response reads - so the
     * harbinger cannot disagree with the rest of the hive about who is an enemy. It already excludes creative and
     * spectator players.
     * </p>
     */
    private static Set<UUID> hostilesFor(ServerLevel level, HiveLocation location) {
        var hostiles = new HashSet<>(enemyMembers(location));

        for (
            var intruder : com.alien.common.gameplay.hive.tick.HiveTerritoryAggroTask
                .intrudersInTerritory(level, location)
        ) {
            hostiles.add(intruder.getUUID());
        }

        return hostiles;
    }

    /**
     * A revealed harbinger that nothing is fighting any more goes back in the bank.
     * <p>
     * [stated] "if the player runs from it and its left alive after the attack ends then it goes back into the
     * reserves."
     * </p>
     * <p>
     * ⚠⚠ THIS DID NOT EXIST. At war's end the revealed set was simply dropped, so a surviving harbinger was left
     * wandering as a loose member and only ever re-banked if its chunk happened to unload. A player who fled and stayed
     * in the area kept it out indefinitely.
     * </p>
     * <p>
     * ⚠ BANKED AS A COUNT, NOT TELEPORTED - the same shape {@code StrandedMemberRecovery.bankAndRemove} uses, with the
     * same brood fallback so a full reserve cannot destroy the unit. The hive re-spawns it properly next time it needs
     * one.
     * </p>
     */
    private static void rebankIdleHarbingers(ServerLevel level, HiveLocation location) {
        var revealed = REVEALED.get(location);
        if (revealed == null || revealed.isEmpty()) {
            SANCTUM_CLEAR_SINCE.remove(location);
            return;
        }

        if (!hostilesFor(level, location).isEmpty()) {
            SANCTUM_CLEAR_SINCE.remove(location);
            return;
        }

        var since = SANCTUM_CLEAR_SINCE.putIfAbsent(location, level.getGameTime());
        if (since == null || level.getGameTime() - since < REBANK_QUIET_TICKS) {
            return;
        }

        SANCTUM_CLEAR_SINCE.remove(location);

        for (var id : Set.copyOf(revealed)) {
            revealed.remove(id);

            if (
                !(level.getEntity(id) instanceof com.alien.common.gameplay.entity.living.alien.Alien harbinger)
                    || !harbinger.isAlive()
            ) {
                continue;
            }

            // !!! NEVER BANK A HARBINGER SOMEONE OWNS. Banking stores a TYPE and a COUNT and then discards the body,
            // so a name tag, a leash and anything riding it are all destroyed - the entity does not come back, an
            // anonymous one is spawned in its place later. StrandedMemberRecovery has refused these three cases from
            // the start; the stand-down was written without that guard and would have deleted a named harbinger the
            // first time a player walked away from one.
            //
            // * It is dropped from tracking rather than retried, so it simply carries on as an ordinary loose member
            // instead of being reconsidered every minute forever.
            if (harbinger.hasCustomName() || harbinger.isPassenger() || harbinger.isLeashed()) {
                continue;
            }

            if (!location.localReserves().addReturningMember(harbinger.getType(), 1)) {
                location.localReserves().addBrood(harbinger.getType(), 1);
            }

            harbinger.discard();
            Alien.LOGGER.info(
                "War: hive {} stood its harbinger down - the attack is over, banked back into reserves.",
                location.id()
            );
        }
    }

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
    private static BlockPos thronePos(net.minecraft.world.level.Level level, HiveLocation location) {
        var centre = location.centerPos();
        // Oct 1: throneFloorY, not hiveFloorY - in build-free the latter is 24 blocks under the queen, inside rock.
        return new BlockPos(centre.getX(), location.throneFloorY(level) + 1, centre.getZ());
    }

    private static AABB box(BlockPos centre, double range) {
        return new AABB(centre).inflate(range);
    }
}
