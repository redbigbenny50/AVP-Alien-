package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.economy.CastePopulation;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.spawning.HiveLoadedSpawner;
import com.alien.common.gameplay.hive.tick.HiveTerritoryAggroTask;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * THE HIVE REPLACES A KILLED QUEEN OUT OF ITS OWN BANK - once the killer has gone.
 * <p>
 * [stated] "player killing a queen should trigger a replacement if ones available as an option but also once the player
 * leaves."
 * </p>
 * <p>
 * TWO HALVES, AND THE SECOND ONE IS THE WHOLE DESIGN. A hive that can field a queen from the bank should not be
 * finished just because someone killed the one it had - but a replacement that walks out while the killer is still
 * standing in the throne room is a free second kill and a farm. So the succession waits: no intruders anywhere in the
 * claim, and nobody within {@link #WATCH_RADIUS_BLOCKS} of the throne, held for {@link #QUIET_TICKS}. Walk away from a
 * hive you have beheaded and it is not beheaded for long.
 * </p>
 * <p>
 * \u26a0 THIS IS THE FAST PATH, NOT THE ONLY PATH. It only fires when the bank actually HOLDS a queen of this hive's
 * strain. A hive with no banked queen is unaffected and still succeeds the slow way, through
 * {@link QueenlessMaturationTask} walking its leader up the queen track over the proto-hive stage interval. Nothing
 * here replaces that ladder; this just spends a queen the hive already owns instead of growing a new one from a drone.
 * </p>
 * <p>
 * \u26a0 END-STYLE HIVES ARE EXCLUDED. Their succession is the regent praetorian ({@link EndRegent}), fired from the
 * royal's own death, and the End's bank is the player's deliberate stock rather than a breeding economy.
 * </p>
 */
public final class QueenSuccessionTask {

    /** Cadence. Five seconds - this only ever does real work in the seconds after a hive loses its queen. */
    private static final long INTERVAL_TICKS = 100L;

    /** How close a player has to be to the throne to count as still standing over the body. */
    private static final double WATCH_RADIUS_BLOCKS = 64.0;

    /** How long the hive must be left completely alone before the successor comes up. */
    private static final long QUIET_TICKS = 200L;

    /** Floor between attempts, so a hive that cannot place her does not retry every cadence forever. */
    private static final long ATTEMPT_COOLDOWN_TICKS = 600L;

    /** Last game tick a player was seen watching this hive. Transient: a restart simply re-arms the quiet timer. */
    private static final Map<HiveLocation, Long> LAST_WATCHED = Collections.synchronizedMap(new WeakHashMap<>());

    private static final Map<HiveLocation, Long> LAST_ATTEMPT = Collections.synchronizedMap(new WeakHashMap<>());

    private QueenSuccessionTask() {}

    public static boolean shouldFire(long currentTick) {
        return currentTick % INTERVAL_TICKS == 0L;
    }

    public static void run(ServerLevel level, HiveLocation location) {
        if (!location.isAlive() || location.isInhibited() || location.isExiled()) {
            return;
        }
        // \u2b50 Oct 3 - HER SEAT IS HELD WHILE SHE IS A CAPTIVE. Chaining now severs a queen from her own hive, so a
        // queen chained in her own throne room leaves it founderless - and this task would hand the throne to a banked
        // queen while her kin were still clawing at her chains. QueenlessMaturationTask and BuildFreeVacantThrone
        // already wait on the rescue campaign; this was the one succession path that did not.
        if (location.rescueCampaign() != null) {
            return;
        }
        if (com.alien.common.gameplay.hive.dimension.EndStyleHiveRules.isEndStyle(level)) {
            return; // the End's answer to a dead royal is the regent praetorian, not a banked queen
        }
        // Only an ESTABLISHED hive succeeds. A hive still founding has a queen out digging (or has never had one at
        // all), and handing it a second queen out of the bank would give it two founders racing each other.
        if (!location.reproductiveEstablished()) {
            return;
        }
        if (CastePopulation.countCaste(location, AlienEntityTypeTags.QUEENS) > 0) {
            LAST_WATCHED.remove(location);
            return;
        }
        // [stated] no ordinary queen replacement while two empires are at war - the hive that loses its queen has
        // lost. Same gate QueenlessMaturationTask honours before its crowning molt, read here so the two cannot
        // disagree about whether a hive is allowed a new queen.
        if (com.alien.common.gameplay.hive.war.AlienTerritoryWarSystem.isExcludedFromQueenReplacement(location)) {
            return;
        }

        var variant = location.lineageVariantOrNull();
        if (variant == null) {
            return;
        }
        var queenType = Queen.getType(variant);
        if (queenType == null) {
            return; // this strain has no queen form (irradiated) - it repopulates its own way
        }
        if (location.localReserves().getReliableCount(queenType) <= 0) {
            return; // nothing banked to promote; the slow maturation ladder still owns this hive's succession
        }

        var now = level.getGameTime();
        if (isBeingWatched(level, location)) {
            LAST_WATCHED.put(location, now);
            return;
        }
        var lastWatched = LAST_WATCHED.get(location);
        if (lastWatched != null && now - lastWatched < QUIET_TICKS) {
            return; // they only just left - let the dust settle before she comes up
        }
        var lastAttempt = LAST_ATTEMPT.get(location);
        if (lastAttempt != null && now - lastAttempt < ATTEMPT_COOLDOWN_TICKS) {
            return;
        }
        LAST_ATTEMPT.put(location, now);

        var throne = thronePos(level, location);
        var successor = HiveLoadedSpawner.trySpawnFromReserves(level, location, queenType, throne);
        if (!(successor instanceof Queen queen)) {
            return;
        }

        queen.setPersistenceRequired();
        // She inherits a STANDING hive. Without this she would walk out of it, pick her own anchor somewhere else and
        // dig a second one - the ordinary lifecycle every never-founded queen runs.
        queen.getLifecyclePhaseManager().assumeVacantThrone(location);
        if (location.founderId() == null) {
            location.setFounderId(queen.getUUID());
        }
        LAST_WATCHED.remove(location);

        Alien.LOGGER.info(
            "Hive at {}: the throne was empty and the bank was not - a successor queen has taken it.",
            location.centerPos()
        );
    }

    /**
     * Is anyone still standing over the hive? Both questions are asked: the territory scan catches a raider anywhere in
     * the claim (and already filters creative and spectator players out at its own choke point), and the throne radius
     * catches someone camping the chamber from a chunk the hive has not claimed.
     */
    private static boolean isBeingWatched(ServerLevel level, HiveLocation location) {
        if (!HiveTerritoryAggroTask.intrudersInTerritory(level, location).isEmpty()) {
            return true;
        }
        var throne = thronePos(level, location);
        var radiusSq = WATCH_RADIUS_BLOCKS * WATCH_RADIUS_BLOCKS;
        for (var player : level.players()) {
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }
            if (player.blockPosition().distSqr(throne) <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    /** Same throne the war's guard holds: hive centre, one block off the floor. */
    private static BlockPos thronePos(net.minecraft.world.level.Level level, HiveLocation location) {
        var centre = location.centerPos();
        // Oct 1: throneFloorY, not hiveFloorY - in build-free the latter is 24 blocks under the queen, inside rock.
        return new BlockPos(centre.getX(), location.throneFloorY(level) + 1, centre.getZ());
    }
}
