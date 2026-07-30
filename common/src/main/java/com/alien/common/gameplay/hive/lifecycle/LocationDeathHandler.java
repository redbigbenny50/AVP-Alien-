package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.data.AlienAdvancements;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.location.HiveLocationRemovalReason;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.UUID;

/**
 * Full {@link HiveLocation} death handling. Per {@code HIVE_REDESIGN_03_LOCATIONS.md} § 10:
 * <ul>
 * <li>Fires the {@link AlienAdvancements#KILL_A_HIVE} advancement to nearby players, but only when the death was
 * player-caused.</li>
 * <li>Releases every claimed chunk through BLib's territory manager.</li>
 * <li>Removes the location from its lineage's {@code locationsById} and from the {@link HiveLocationRegistry}.</li>
 * <li>Removes the per-location BLib faction. Members of the location-tier faction lose location membership but stay in
 * their parent lineage (no "convert to forager" eviction — loading state should not affect lineage membership).</li>
 * </ul>
 * <p>
 * Doesn't trigger lineage death directly — the per-tick {@link LineageDeathHandler#scanAndKill} picks up empty or
 * 0-location lineages on the next tick.
 */
public final class LocationDeathHandler {

    private LocationDeathHandler() {}

    /** Player-caused death. Fires the advancement to nearby players. */
    public static void killByPlayer(
        ServerLevel level,
        HiveLocation location,
        LineageFactionData lineage,
        UUID killerId
    ) {
        if (lineage.locationsById().size() == 1 && lineage.locationsById().containsKey(location.id())) {
            lineage.recordLineageKillCredit(killerId);
        }
        kill(level, location, lineage, new HiveLocationRemovalReason.KilledByPlayer(killerId), true);
    }

    /**
     * Natural decay (dormancy/0-chunk shrink). Does NOT fire the advancement.
     * <p>
     * If this was a living empress's seat she is EXILED rather than removed with it - she failed to hold the hive, so
     * the lineage writes her off and leaves her the ruin. Nukes are deliberately not routed here: they kill every
     * spawned hive member, and she dies with them.
     */
    public static void killNaturalDecay(ServerLevel level, HiveLocation location, LineageFactionData lineage) {
        if (com.alien.common.gameplay.hive.empress.EmpressExileService.exile(level, location, lineage, false)) {
            return;
        }
        kill(level, location, lineage, new HiveLocationRemovalReason.NaturalDecay(), false);
    }

    /** Contest loss (lost a same-variant cross-lineage chunk fight to the point of zero territory). */
    public static void killContestLost(ServerLevel level, HiveLocation location, LineageFactionData lineage) {
        kill(level, location, lineage, new HiveLocationRemovalReason.ContestLost(), false);
    }

    /** Admin-triggered. */
    public static void killAdmin(ServerLevel level, HiveLocation location, LineageFactionData lineage, String reason) {
        kill(level, location, lineage, new HiveLocationRemovalReason.AdminRemoved(reason), false);
    }

    private static void kill(
        ServerLevel level,
        HiveLocation location,
        LineageFactionData lineage,
        HiveLocationRemovalReason reason,
        boolean fireAdvancement
    ) {
        // 1. Fire advancement BEFORE we release chunks so the territory check still works.
        if (fireAdvancement) {
            fireKillAHiveAdvancement(level, location);
        }

        // 2. Release every claimed chunk via BLib + reset local indexes (LocationRemovalHelper handles BLib release).
        LocationRemovalHelper.remove(level, location, lineage, reason);

        // 3. Drop the per-location BLib faction. Members of this faction lose location-tier membership but stay in
        // the parent lineage (no eviction-on-load — loading state should not affect lineage membership).
        Alien.MOD.factions().remove(location.id().value());

        Alien.LOGGER.info(
            "Hive: location {} death complete (reason={}); lineage {} now has {} location(s)",
            location.id(),
            reason.typeKind(),
            location.lineageFactionId(),
            lineage.locationsById().size()
        );
    }

    private static void fireKillAHiveAdvancement(ServerLevel level, HiveLocation location) {
        var radius = HiveLocationRegistry.INSTANCE.config().bossBarDisplayRadiusBlocks();
        var radiusSqr = (double) radius * radius;
        var centerChunks = location.claimedChunks();

        for (var player : level.players()) {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                continue;
            }
            // Eligible if the player is either inside the location's claimed chunks OR within the boss bar radius
            // from center — both indicate "the player was here when it fell."
            var inChunk = centerChunks.contains(new ChunkPos(serverPlayer.blockPosition()));
            var inRadius = serverPlayer.blockPosition().distSqr(location.centerPos()) <= radiusSqr;
            if (inChunk || inRadius) {
                AlienAdvancements.KILL_A_HIVE.grant(serverPlayer);
            }
        }
    }
}
