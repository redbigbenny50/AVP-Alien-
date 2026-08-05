package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.gameplay.hive.economy.CastePopulation;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

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
 * Runs every server tick from {@link HiveLocationRegistry#tick}, but only RULE 1 pays that cadence. Rules 2 and 3 are
 * staggered to once per {@link #EVAL_STRIDE_TICKS} per location, offset by location identity so the cost is spread
 * across ticks instead of spiking on one. Both of their thresholds are measured in elapsed GAME ticks rather than in
 * observations, so the stride can change without silently changing how long a hive gets.
 */
public final class LocationDormancyTask {

    /** Rule 2 kills only after the zero-population state has PERSISTED this many ticks (30s). */
    private static final int ZERO_POP_KILL_TICKS = 600;

    /**
     * How often any one location runs rules 2 and 3. Rule 1 stays per-tick because it is a single isEmpty() check.
     * <p>
     * Neither of the other two needs per-tick resolution - rule 2 has to see the same state persist for 600 ticks and
     * rule 3 for 336,000 - but both used to pay their full cost twenty times a second, on every location, forever. Rule
     * 3 is the expensive one: it walks every claimed chunk (up to maxChunksPerLocation, 256) asking the chunk source
     * whether it is loaded, and the UNLOADED case is the worst one because it never finds a loaded chunk to break on -
     * which is also the common case for a large empire.
     */
    private static final int EVAL_STRIDE_TICKS = 20;

    /**
     * Game tick at which each location was FIRST seen at zero reliable population, or absent if it is not currently in
     * that state. Transient.
     * <p>
     * Stores the tick rather than a count of observations deliberately: the threshold is then measured in elapsed game
     * time and stays correct no matter how often this task actually looks. A counter would have silently become a 30 x
     * longer grace period the moment the stride was introduced.
     */
    private static final java.util.Map<HiveLocation, Long> ZERO_POP_SINCE_TICK =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * Drops a dead location's zero-population entry. Without this, a location that was momentarily at zero population
     * and then died via a path OTHER than natural decay (player kill, contest loss, admin removal, End cull) left its
     * map entry behind for the server lifetime - the micro-leak flagged by the Aug-1 TPS scan. Called from
     * LocationDeathHandler.kill, the funnel every removal path runs through.
     */
    public static void forgetLocation(HiveLocation location) {
        ZERO_POP_SINCE_TICK.remove(location);
    }

    private LocationDormancyTask() {}

    /**
     * Reused snapshot buffer for the per-tick faction scan. The scan runs only on the single server thread, so one
     * static scratch list per scan is safe; clear+addAll keeps the same iterate-a-snapshot semantics (the loop body may
     * mutate the live faction registry) while allocating nothing once the backing array has grown - this scan used to
     * build a fresh ArrayList of every faction id EVERY TICK just to run its bucket filter.
     */
    private static final java.util.List<net.minecraft.resources.ResourceLocation> SCAN_SCRATCH =
        new java.util.ArrayList<>();

    public static void scanAll(MinecraftServer server) {
        var config = HiveLocationRegistry.INSTANCE.config();
        var maxNoContact = config.locationMaxNoContactTicks();
        var currentTick = server.overworld().getGameTime();

        // Snapshot ids before iteration — LocationDeathHandler.kill removes the per-location faction, which mutates
        // the underlying registry that getAllIds() returns a view of.
        SCAN_SCRATCH.clear();
        SCAN_SCRATCH.addAll(Alien.MOD.factions().getAllIds());
        for (var factionId : SCAN_SCRATCH) {
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

                if (evaluateLocation(serverLevel, location, lineage, maxNoContact, currentTick)) {
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
        long maxNoContact,
        long currentTick
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
        // Rules 2 and 3 are STAGGERED: each location evaluates them once per EVAL_STRIDE_TICKS, offset by its own
        // identity so the whole empire does not land on the same tick. Rule 1 above stays per-tick - it is free.
        if (Math.floorMod(currentTick + location.id().hashCode(), EVAL_STRIDE_TICKS) != 0) {
            return false;
        }

        boolean zeroNow = CastePopulation.totalReliableXenomorphPopulation(location) == 0
            && !hasBankedEggs(location)
            && location.ageInTicks() >= HiveLocationRegistry.INSTANCE.config().locationBootstrapGraceTicks();
        if (zeroNow) {
            var since = ZERO_POP_SINCE_TICK.putIfAbsent(location, currentTick);
            if (since != null && currentTick - since >= ZERO_POP_KILL_TICKS) {
                ZERO_POP_SINCE_TICK.remove(location);
                LocationDeathHandler.killNaturalDecay(level, location, lineage);
                return true;
            }
        } else {
            ZERO_POP_SINCE_TICK.remove(location);
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
                    if (location.claimedChunks().contains(entity.chunkPosition())) {
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

        // Accrues the whole interval this evaluation stands for, not 1 - the counter is elapsed GAME time, so it
        // must stay independent of how often the task looks.
        var nextAccrued = location.noContactTicksAccrued() + EVAL_STRIDE_TICKS;
        location.setNoContactTicksAccrued(nextAccrued);
        if (nextAccrued >= maxNoContact) {
            LocationDeathHandler.killNaturalDecay(level, location, lineage);
            return true;
        }

        return false;
    }
}
