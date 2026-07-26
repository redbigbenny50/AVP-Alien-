package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.gameplay.hive.economy.CastePopulation;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;

/**
 * Per-tick location death check. Replaces the legacy 24h dormancy timer with three accuracy-first rules:
 * <ol>
 * <li>Zero claimed chunks → kill (preserved from the legacy behavior).</li>
 * <li>No reliable xenomorph population → kill. Reliable population matches the boss bar: actively loaded xenomorphs in
 * this location + local reserves.</li>
 * <li>No-contact safety net: {@link HiveLocation#noContactTicksAccrued()} accumulates while ≥1 claimed chunk is loaded
 * AND no loaded location member is currently in any claimed chunk. Pauses when nothing is loaded; resets when contact
 * is observed; triggers a kill at {@link com.alien.common.gameplay.hive.config.HiveConfig#locationMaxNoContactTicks()}
 * (default 7 game-days).</li>
 * </ol>
 * <p>
 * Runs every server tick from {@link HiveLocationRegistry#tick}; no scan-cadence throttling. If this becomes a perf
 * hotspot, the per-location body is cheap to gate (early-return on non-loaded territory).
 */
public final class LocationDormancyTask {

    /** Rule 2 kills only after the zero-population state has PERSISTED this many consecutive ticks (30s). */
    private static final int ZERO_POP_KILL_TICKS = 600;

    /** Consecutive ticks each location has been observed at zero reliable population. Transient. */
    private static final java.util.Map<HiveLocation, Integer> ZERO_POP_TICKS =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private LocationDormancyTask() {}

    public static void scanAll(MinecraftServer server) {
        var config = HiveLocationRegistry.INSTANCE.config();
        var maxNoContact = config.locationMaxNoContactTicks();

        // Snapshot ids before iteration — LocationDeathHandler.kill removes the per-location faction, which mutates
        // the underlying registry that getAllIds() returns a view of.
        for (var factionId : new ArrayList<>(Alien.MOD.factions().getAllIds())) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage) || !lineage.isAlive()) {
                continue;
            }

            var serverLevel = server.getLevel(lineage.dimension());
            if (serverLevel == null) {
                continue;
            }

            // Snapshot since LocationDeathHandler.killNaturalDecay can mutate locationsById.
            var locations = new ArrayList<>(lineage.locationsById().values());
            for (var location : locations) {
                if (!location.isAlive()) {
                    continue;
                }

                if (evaluateLocation(serverLevel, location, lineage, maxNoContact)) {
                    // Killed — skip further checks on this location.
                    continue;
                }
            }
        }
    }

    /** Whether the location's reserves still bank any of its lineage's eggs - banked eggs are future adults. */
    private static boolean hasBankedEggs(HiveLocation location) {
        var variant = location.lineageVariantOrNull();
        if (variant == null) {
            return false;
        }
        var eggType = com.alien.common.gameplay.entity.living.alien.ovomorph.Ovomorph.getType(variant, false);
        return eggType != null && location.localReserves().getCount(eggType) > 0;
    }

    /** Returns true if the location was killed this tick. */
    private static boolean evaluateLocation(
        ServerLevel level,
        HiveLocation location,
        LineageFactionData lineage,
        long maxNoContact
    ) {
        // Rule 1: zero claimed chunks → die.
        if (location.claimedChunks().isEmpty()) {
            LocationDeathHandler.killNaturalDecay(level, location, lineage);
            return true;
        }

        // Rule 2: boss-bar source of truth. Persisted unloaded members do not keep a location alive.
        // EXCEPTION: newborn locations are shielded during the bootstrap grace window. A freshly-founded single-queen
        // hive has no reserve buffer, and its founding queen can momentarily read as zero reliable population during
        // the chunk-unload / return-to-reserves transition (or on player logout). Reaping it in that window would
        // delete a legitimate new hive. The same grace window already protects newborn locations from shrink/evacuate.
        // HARDENED: the zero state must PERSIST for ZERO_POP_KILL_TICKS before the kill. Member registration
        // lags world load by ticks (a seated queen read as zero pop 2s after login and her hive was executed
        // under her), and logout/unload transitions produce the same momentary zero the bootstrap comment
        // already warns about. Additionally, a hive whose reserves still bank OVOMORPHS is not dead - the
        // purchase economy rebuilds adults from banked eggs, so eggs count as life.
        boolean zeroNow = CastePopulation.totalReliableXenomorphPopulation(location) == 0
            && !hasBankedEggs(location)
            && location.ageInTicks() >= HiveLocationRegistry.INSTANCE.config().locationBootstrapGraceTicks();
        if (zeroNow) {
            int observed = ZERO_POP_TICKS.merge(location, 1, Integer::sum);
            if (observed >= ZERO_POP_KILL_TICKS) {
                ZERO_POP_TICKS.remove(location);
                LocationDeathHandler.killNaturalDecay(level, location, lineage);
                return true;
            }
        } else {
            ZERO_POP_TICKS.remove(location);
        }

        // Rule 3: no-contact safety net.
        var anyChunkLoaded = false;
        var memberInTerritory = false;

        for (var chunk : location.claimedChunks()) {
            if (level.getChunkSource().hasChunk(chunk.x, chunk.z)) {
                anyChunkLoaded = true;
                break;
            }
        }

        if (anyChunkLoaded) {
            for (var memberIds : location.loadedMembersByType().values()) {
                for (var memberId : memberIds) {
                    var entity = level.getEntity(memberId);
                    if (entity == null) {
                        continue;
                    }
                    if (location.claimedChunks().contains(new ChunkPos(entity.blockPosition()))) {
                        memberInTerritory = true;
                        break;
                    }
                }
                if (memberInTerritory) {
                    break;
                }
            }
        }

        if (!anyChunkLoaded) {
            // Paused — neither advance nor reset.
            return false;
        }

        if (memberInTerritory) {
            if (location.noContactTicksAccrued() != 0L) {
                location.setNoContactTicksAccrued(0L);
            }
            return false;
        }

        var nextAccrued = location.noContactTicksAccrued() + 1L;
        location.setNoContactTicksAccrued(nextAccrued);
        if (nextAccrued >= maxNoContact) {
            LocationDeathHandler.killNaturalDecay(level, location, lineage);
            return true;
        }

        return false;
    }
}
