package com.alien.common.gameplay.hive.tick;

import com.alien.Alien;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.growth.AbstractSpreadAttempt;
import com.alien.common.gameplay.hive.growth.CatchUpEngine;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;

/**
 * Bounded slow-path scheduler for hive locations. Instead of sweeping every location in one large scan, this keeps a
 * shuffled round-robin queue of living locations across all lineages and samples a configured number each server tick.
 */
public final class HiveLocationSlowTickTask {

    private static final Deque<HiveLocationId> PENDING = new ArrayDeque<>();

    private HiveLocationSlowTickTask() {}

    public static void reset() {
        PENDING.clear();
    }

    public static void run(MinecraftServer server) {
        var config = HiveLocationRegistry.INSTANCE.config();
        var budget = Math.max(0, config.slowPathLocationUpdatesPerTick());
        if (budget <= 0) {
            return;
        }

        if (PENDING.isEmpty()) {
            refill();
        }
        if (PENDING.isEmpty()) {
            return;
        }

        var candidates = Math.min(budget, PENDING.size());
        for (var i = 0; i < candidates; i++) {
            tickCandidate(server, PENDING.removeFirst());
        }
    }

    private static void refill() {
        var ids = new ArrayList<HiveLocationId>();
        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (location.isAlive()) {
                ids.add(location.id());
            }
        }
        Collections.shuffle(ids);
        PENDING.addAll(ids);
    }

    private static void tickCandidate(MinecraftServer server, HiveLocationId locationId) {
        var location = HiveLocationRegistry.INSTANCE.get(locationId);
        if (location == null || !location.isAlive()) {
            return;
        }

        var faction = Alien.MOD.factions().get(location.lineageFactionId());
        if (faction == null || !(faction.data() instanceof LineageFactionData lineage) || !lineage.isAlive()) {
            return;
        }

        var level = server.getLevel(location.dimension());
        var isLoaded = level != null && HiveLocationLoadedTickTask.hasLoadedClaimedChunk(level, location);
        if (isLoaded) {
            return;
        }

        // Inhibited (severed contained-breeder) locations expand and accrue nothing while unloaded either.
        if (location.isInhibited()) {
            return;
        }

        var currentTick = level != null ? level.getGameTime() : server.overworld().getGameTime();
        if (level != null) {
            CatchUpEngine.catchUpUnloadedTo(level, location, lineage, currentTick);
        }

        AbstractSpreadAttempt.tryRun(server, location.lineageFactionId(), lineage, location, currentTick);
    }
}
