package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.data.AlienAdvancements;
import com.alien.common.gameplay.hive.economy.CastePopulation;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.faction.LineageRemovalReason;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;

/**
 * Per-tick lineage death check. No grace periods — runs every server tick from
 * {@link com.alien.common.gameplay.hive.location.HiveLocationRegistry#tick}.
 * <ul>
 * <li><b>Members empty AND locationsById non-empty</b> → kill owned locations that also have no reliable population.
 * Reliable population matches the boss bar: loaded xenomorphs + local reserves.</li>
 * <li><b>Members empty AND locationsById empty</b> → kill the lineage immediately. Sets
 * {@link LineageRemovalReason.NoLocationsRemain} and calls {@code Alien.MOD.factions().remove(lineageId)}.</li>
 * </ul>
 * <p>
 * A lineage may temporarily have no BLib members while still owning reserve population; those locations remain alive
 * because reserves are reliable and spawnable.
 */
public final class LineageDeathHandler {

    private LineageDeathHandler() {}

    /** Per-tick scan: kill locations of empty lineages, then kill empty+locationless lineages. */
    /**
     * Lineages are processed in buckets: each one is evaluated once per this many ticks, chosen by its own id hash so
     * the empire spreads evenly across ticks instead of spiking on one. Nothing here needs per-tick resolution - it
     * only needs to happen about once a second.
     */
    private static final int LINEAGE_BUCKET_TICKS = 20;

    /**
     * Offset within the bucket window, distinct per scan, so one lineage's economy tasks land on DIFFERENT ticks.
     * Without it every scan would pick the same lineage on the same tick and the saving would be a smaller spike rather
     * than no spike.
     */
    private static final int BUCKET_PHASE = 13;

    /**
    * Reused snapshot buffer for the per-tick faction scan. The scan runs only on the single server thread, so one
    * static scratch list per scan is safe; clear+addAll keeps the same iterate-a-snapshot semantics (the loop body
    * may mutate the live faction registry) while allocating nothing once the backing array has grown - this scan
    * used to build a fresh ArrayList of every faction id EVERY TICK just to run its bucket filter.
    */
    private static final java.util.List<net.minecraft.resources.ResourceLocation> SCAN_SCRATCH =
        new java.util.ArrayList<>();

    public static void scanAndKill(MinecraftServer server) {
        var deathQueue = new ArrayList<ResourceLocation>();
        var currentTick = server.overworld().getGameTime();

        SCAN_SCRATCH.clear();
        SCAN_SCRATCH.addAll(Alien.MOD.factions().getAllIds());
        for (var factionId : SCAN_SCRATCH) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }
            // An empty lineage stays dead; noticing it a second later changes nothing. The ordering note in
            // HiveLocationRegistry still holds - dormancy runs first, this just picks the result up on its own
            // bucket rather than the very next tick.
            if (Math.floorMod(currentTick - factionId.hashCode(), LINEAGE_BUCKET_TICKS) != BUCKET_PHASE) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage) || !lineage.isAlive()) {
                continue;
            }

            if (!faction.membership().getMembers().isEmpty()) {
                continue;
            }

            if (!lineage.locationsById().isEmpty()) {
                // Kill all owned locations. The lineage then becomes 0-locations and will be killed on the next branch
                // (same tick if rescanned, or next tick).
                killOwnedLocations(server, factionId, lineage);
            }

            // After location cleanup (or if locations were already empty), the lineage is dead.
            if (lineage.locationsById().isEmpty()) {
                deathQueue.add(factionId);
            }
        }

        for (var lineageId : deathQueue) {
            kill(server, lineageId);
        }
    }

    private static void killOwnedLocations(
        MinecraftServer server,
        ResourceLocation lineageId,
        LineageFactionData lineage
    ) {
        var serverLevel = server.getLevel(lineage.dimension());
        if (serverLevel == null) {
            Alien.LOGGER.warn(
                "Hive: lineage {} has 0 members but its dimension {} is not loaded — skipping owned-location cleanup",
                lineageId,
                lineage.dimension().location()
            );
            return;
        }

        var killable = new ArrayList<HiveLocation>();
        for (var location : new ArrayList<>(lineage.locationsById().values())) {
            if (location.isAlive() && CastePopulation.totalReliableXenomorphPopulation(location) == 0) {
                killable.add(location);
            }
        }

        if (killable.isEmpty()) {
            return;
        }

        Alien.LOGGER.info(
            "Hive: lineage {} has 0 members; killing {} owned location(s) with no reliable population",
            lineageId,
            killable.size()
        );

        for (var location : killable) {
            LocationDeathHandler.killNaturalDecay(serverLevel, location, lineage);
        }
    }

    /**
     * Forces lineage death now. Sets removal reason and removes from BLib. Idempotent — a lineage already dead returns
     * false.
     */
    public static boolean kill(MinecraftServer server, ResourceLocation lineageId) {
        var faction = Alien.MOD.factions().get(lineageId);
        if (faction == null || !(faction.data() instanceof LineageFactionData lineage) || !lineage.isAlive()) {
            return false;
        }

        grantKillLineageAdvancement(server, lineage);
        lineage.setRemovalReason(new LineageRemovalReason.NoLocationsRemain());
        Alien.MOD.factions().remove(lineageId);

        Alien.LOGGER.info(
            "Hive: lineage {} dead (no locations remain); faction removed",
            lineageId
        );
        return true;
    }

    private static void grantKillLineageAdvancement(MinecraftServer server, LineageFactionData lineage) {
        var playerId = lineage.lineageKillCreditPlayerId();
        if (playerId == null) {
            return;
        }

        var player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            AlienAdvancements.KILL_A_LINEAGE.grant(player);
        }
    }

}
