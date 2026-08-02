package com.alien.common.gameplay.hive.tick;

import com.alien.Alien;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.growth.AbstractSpreadAttempt;
import com.alien.common.gameplay.hive.growth.CatchUpEngine;
import com.alien.common.gameplay.hive.growth.LoadedBiomassTicker;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.party.AttackPartyDispatch;
import com.alien.common.gameplay.hive.party.AttackPartyLifecycleTask;
import com.alien.common.gameplay.hive.party.BiomassHuntingPartyDispatch;
import com.alien.common.gameplay.hive.party.BiomassHuntingPartyLifecycleTask;
import com.alien.common.gameplay.hive.party.SurfacePartyDispatch;
import com.alien.common.gameplay.hive.party.SurfacePartyLifecycleTask;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Fast-path per-server-tick work for a single {@link HiveLocation}. Resolves the owning lineage faction (skipping the
 * tick if the lineage has been removed mid-tick) and dispatches:
 * <ul>
 * <li>Phase 3 — leader pick, boss bar progress / visibility.</li>
 * <li>Phase 7 — every-tick {@link LoadedBiomassTicker} for player-nearby locations + per-tick {@link CatchUpEngine} for
 * claim attempts (the engine is idempotent on elapsed=0, so it's free to call when biomass hasn't moved).</li>
 * <li>Territory defense — loaded hive xenomorphs aggro players standing in claimed chunks.</li>
 * <li>Phase 8 — convoy manifestation interactions on this location's chunks.</li>
 * <li>Parties — {@link SurfacePartyDispatch} (nightly vent-seeding surface spawns), {@link SurfacePartyLifecycleTask}
 * (economy-bias recheck, opportunistic claims, dawn resolution), {@link BiomassHuntingPartyDispatch} (vent-gated
 * Prowler/Warrior/bonus-Spitter hunting party), {@link BiomassHuntingPartyLifecycleTask} (duration-timer resolution
 * with vent-teleport-home), and {@link AttackPartyDispatch}/{@link AttackPartyLifecycleTask} (per-target retribution
 * against players who've attacked the hive) on the same 20-tick cadence.</li>
 * </ul>
 * <p>
 * See {@code HIVE_REDESIGN_12_PERFORMANCE.md} § 1.
 */
public final class HiveLocationLoadedTickTask {

    private HiveLocationLoadedTickTask() {}

    public static void run(MinecraftServer server, HiveLocation location) {
        var faction = Alien.MOD.factions().get(location.lineageFactionId());

        if (faction == null) {
            return;
        }

        if (!(faction.data() instanceof LineageFactionData lineage)) {
            return;
        }

        if (!lineage.isAlive()) {
            return;
        }

        location.tick(server, lineage);

        // Persistence: hive locations mutate on practically every loaded tick (timers, biomass, claims, carve
        // progress, reserves, membership), but BLib only serializes faction data that is MARKED DIRTY - and
        // per-mutation marking has proven leaky (the "resumed at 86% after quitting at 100%" rollback from testing,
        // which also despawned every reloaded member the rolled-back registry no longer recognized). Marking here,
        // once per loaded tick, is a boolean set - effectively free - and guarantees every world save and the
        // shutdown save capture the live state. Unloaded mutation paths keep their explicit markDirty calls.
        lineage.markDirty();

        var serverLevel = server.getLevel(location.dimension());
        if (serverLevel == null) {
            return;
        }

        // An empress elected while this hive was unloaded gets her body the moment it comes back. The crown was
        // already hers - this is only the molt catching up with it.
        if (location.id().equals(lineage.pendingEmpressSeatId())) {
            com.alien.common.gameplay.hive.empress.EmpressEmergenceRitual.tryMaterialize(serverLevel, location, lineage);
        }

        var currentTick = serverLevel.getGameTime();
        if (!hasLoadedClaimedChunk(serverLevel, location)) {
            return;
        }

        if (HiveTerritoryAggroTask.shouldFire(currentTick)) {
            HiveTerritoryAggroTask.run(serverLevel, location);
        }
        if (com.alien.common.gameplay.hive.defense.VentDefenseTask.shouldFire(currentTick)) {
            com.alien.common.gameplay.hive.defense.VentDefenseTask.run(serverLevel, location);
        }

        // ---- END-STYLE HIVES branch off here and run NOTHING below this block. ---------------------------------
        // The End hive is a player-built fortress, not a self-growing empire: no construction, no expansion, no
        // parties, no economy, no simulated growth. What it keeps from above: defense (aggro + vent defense), and
        // what it adds lives in EndHiveTickTask (worker deployment, egg placement around the queen, the regent
        // check, the seven-day cull clock). Attack parties still run - they are the hive's teeth - and the brood
        // bank still runs because vent-only banking IS the End's population model. Everything else on this driver
        // is autonomy an End hive does not have. See EndStyleHiveRules for the full ruleset.
        if (com.alien.common.gameplay.hive.dimension.EndStyleHiveRules.isEndStyle(serverLevel)) {
            if (currentTick % 20L == 0L) {
                com.alien.common.gameplay.hive.empress.EmpressInfluenceSync.sync(location, lineage);
                var endConfig = HiveLocationRegistry.INSTANCE.config();
                AttackPartyDispatch.tryRun(server, location, endConfig);
                AttackPartyLifecycleTask.run(server, location, endConfig);
            }
            if (currentTick % 200L == 0L) {
                com.alien.common.gameplay.hive.economy.BroodBankTask.run(serverLevel, location);
            }
            com.alien.common.gameplay.hive.tick.EndHiveTickTask.run(server, serverLevel, location, lineage, currentTick);
            return;
        }

        // Inhibited (severed contained-breeder) locations run no autonomy below this line — no biomass income, no
        // claim expansion, no abstract spread. Defense (aggro, above) and her own combat / egg-laying are unaffected.
        if (location.isInhibited()) {
            return;
        }

        // Construction economy step 3: advance the active carve site - the ghost carve digs clumps and trails resin
        // on its own game-time timers until worker dispatch lands at step 5. Called every loaded tick; a hive with no
        // active site returns immediately, so this is free almost always. Sits below the inhibited gate on purpose:
        // building is autonomy, so an inhibited hive's build FREEZES (the site persists - never lost, only paused).
        com.alien.common.gameplay.hive.structure.carve.CarveSiteWork.tickActive(server, serverLevel, location);

        // Loaded biomass income — only for player-nearby locations (proxy: boss bar is showing). Cheap to call,
        // so we check every tick and let LoadedBiomassTicker decide whether this is its second.
        if (isPlayerNearby(serverLevel, location) && LoadedBiomassTicker.shouldFire(currentTick)) {
            LoadedBiomassTicker.run(location, lineage, currentTick);
        }

        // Per-tick claim attempts for loaded locations. CatchUpEngine is idempotent — it does its own
        // biomass-cost gating and skips if the location is angry. Calling every tick would be wasteful in the
        // limit; gate to a coarse cadence. Abstract spread follows this loaded-location cadence; unloaded locations
        // use HiveLocationSlowTickTask's bounded randomized fallback.
        if (currentTick % 20L == 0L) {
            // Reconcile on the coarse cadence rather than every tick: a hive that just loaded still starts building
            // at 23x23 within a second, and the periodic sweep is the real guarantee anyway.
            com.alien.common.gameplay.hive.empress.EmpressInfluenceSync.sync(location, lineage);
            CatchUpEngine.catchUpTo(serverLevel, location, lineage, currentTick);
            // A watched hive raises its own founding queen instead of teleporting the outcome. This stamps the
            // shared spread cooldown on success, so the abstract attempt below is already blocked for this hive.
            com.alien.common.gameplay.hive.growth.QueenPromotionService.tryPromote(
                serverLevel,
                location,
                lineage,
                HiveLocationRegistry.INSTANCE.config(),
                currentTick
            );
            AbstractSpreadAttempt.tryRun(server, location.lineageFactionId(), lineage, location, currentTick);

            var config = HiveLocationRegistry.INSTANCE.config();
            SurfacePartyDispatch.tryRun(server, location, config);
            SurfacePartyLifecycleTask.run(server, location, config);
            BiomassHuntingPartyDispatch.tryRun(server, location, config);
            BiomassHuntingPartyLifecycleTask.run(server, location, config);
            com.alien.common.gameplay.hive.party.HostHuntPartyDispatch.tryRun(server, location, config);
            com.alien.common.gameplay.hive.party.HostHuntPartyLifecycleTask.run(server, location, config);
            AttackPartyDispatch.tryRun(server, location, config);
            AttackPartyLifecycleTask.run(server, location, config);
        }

        // Structure growth: grow one hive piece off an open frontier socket on a coarse cadence (every 200 ticks / 10s)
        // so the hive expands gradually and visibly rather than all at once. Bounded and event-driven off the frontier
        // set; does nothing when there are no open sockets.
        if (currentTick % 200L == 0L) {
            if (com.alien.common.gameplay.hive.structure.HiveRouter.ENABLED) {
                com.alien.common.gameplay.hive.structure.HiveRouter.route(server, serverLevel, location);
            } else {
                com.alien.common.gameplay.hive.structure.HiveStructurePlanner.tryGrow(server, serverLevel, location);
            }
            // Pour the jelly bank into the placed vats for display (royal chambers first, then vaults).
            com.alien.common.gameplay.hive.economy.JellyVatDisplay.sync(serverLevel, location);
            // Banked reserve eggs restock free nursery beds (one per cycle, hauled by the drones).
            com.alien.common.gameplay.hive.spawning.EggRestockTask.run(serverLevel, location);
            // Captured terrain spawners move into harvest-chamber slots as chambers and slots free up.
            com.alien.common.gameplay.hive.structure.HarvestChamberTask.run(serverLevel, location);
            // A webbed host awaiting an egg releases one stored nursery egg, which a carrier then ferries to it.
            com.alien.common.gameplay.hive.structure.HostEggFerryTask.run(serverLevel, location);
            // Idle host-born adults walk to a vent and fold into the brood bank: uncapped, off the member cap, and
            // drawn on before the main reserves.
            com.alien.common.gameplay.hive.economy.BroodBankTask.run(serverLevel, location);
        }
    }

    public static boolean hasLoadedClaimedChunk(ServerLevel level, HiveLocation location) {
        for (var chunk : location.claimedChunks()) {
            if (level.getChunkSource().hasChunk(chunk.x, chunk.z)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPlayerNearby(ServerLevel level, HiveLocation location) {
        // Loaded biomass income accrues whenever a player is within the boss-bar radius of the hive - NOT only when
        // the hive is angry. (The old implementation checked bossBar.isAngry(), which meant a calm hive earned zero
        // biomass - fatal for a founding queen who must stay calm to settle/lay but needs biomass for her ovipositor.)
        var radius = HiveLocationRegistry.INSTANCE.config().bossBarDisplayRadiusBlocks();
        var radiusSqr = (double) radius * radius;
        var center = location.centerPos();
        for (var player : level.players()) {
            if (player.blockPosition().distSqr(center) <= radiusSqr) {
                return true;
            }
        }
        return false;
    }
}
