package com.alien.common.gameplay.hive.convoy;

import com.alien.Alien;
import com.alien.common.gameplay.hive.config.HiveConfig;
import com.alien.common.gameplay.hive.empress.EmpressExileService;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.lifecycle.LocationRemovalHelper;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationBootstrapProtection;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.location.HiveLocationRemovalReason;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Migration dispatch and execution. Two trigger paths in Phase 8b:
 * <ul>
 * <li><b>Auto</b>: any location whose {@code claimedChunks.size() <= migrationTerritoryFloorChunks} (default 2) is
 * evacuated, provided the lineage has an empress alive and ≥ 1 sister, and the source is past its bootstrap protection
 * window.</li>
 * <li><b>Admin</b>: {@link #forceMigration(MinecraftServer, HiveLocation, LineageFactionData)} — trigger any evacuation
 * regardless of conditions, useful for testing.</li>
 * </ul>
 * <p>
 * Phase 8b ships an instant-rally version: at trigger time, the source's reserves + biomass payload are drained
 * straight into the convoy and the source location is removed. The 30-second Evacuating boss-bar window from
 * {@code HIVE_REDESIGN_06_CONVOYS.md} § 5 is parked for a future polish pass — the boss bar visual is wired (via the
 * bar's existing Evacuating state in Phase 3) but Phase 8b doesn't gate departure on the rally timer.
 * <p>
 * See {@code HIVE_REDESIGN_06_CONVOYS.md} § 5.
 */
public final class MigrationDispatch {

    private MigrationDispatch() {}

    public static void scanAndDispatch(MinecraftServer server) {
        var currentTick = server.overworld().getGameTime();
        var config = HiveLocationRegistry.INSTANCE.config();

        // Snapshot faction ids because dispatch removes the source location's location-faction.
        for (var factionId : new java.util.ArrayList<>(Alien.MOD.factions().getAllIds())) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage) || !lineage.isAlive()) {
                continue;
            }
            if (lineage.empressId() == null) {
                continue;
            }
            if (lineage.activeLocationCount() < 2) {
                continue;
            }

            // Snapshot the location set since we'll mutate via removeLocation.
            var locations = new java.util.ArrayList<>(lineage.locationsById().values());
            for (var location : locations) {
                if (com.alien.common.gameplay.hive.dimension.EndStyleHiveRules.isEndStyle(server, location)) {
                    continue; // END-STYLE: no migration convoys (and so no migration-triggered empress exile)
                }
                if (!location.isAlive() || location.isExiled()) {
                    // A remnant has nothing left to evacuate and must never re-trigger the migration that made it.
                    continue;
                }
                if (HiveLocationBootstrapProtection.isProtected(location, config)) {
                    continue;
                }
                if (location.claimedChunks().size() > config.migrationTerritoryFloorChunks()) {
                    continue;
                }
                tryDispatch(server, location, factionId, lineage, currentTick, config);
            }
        }
    }

    /** Admin-trigger entry. Forces a migration regardless of trigger conditions, as long as a destination exists. */
    public static boolean forceMigration(
        MinecraftServer server,
        HiveLocation source,
        LineageFactionData lineage
    ) {
        var currentTick = server.overworld().getGameTime();
        var config = HiveLocationRegistry.INSTANCE.config();
        return tryDispatch(server, source, source.lineageFactionId(), lineage, currentTick, config);
    }

    private static boolean tryDispatch(
        MinecraftServer server,
        HiveLocation source,
        ResourceLocation lineageFactionId,
        LineageFactionData lineage,
        long currentTick,
        HiveConfig config
    ) {
        // AN IRRADIATED HIVE NEVER MIGRATES. [stated] "they dont make new hives, they maintain their current slab
        // only. once more an island among a sea of hives." It is also structurally impossible: each converted hive is
        // minted its OWN lineage of one, so pickClosestSister could never find a sister anyway. This says why, rather
        // than leaving it as an accident of the lineage split that a later change could undo.
        if (com.alien.common.gameplay.hive.economy.IrradiatedHiveRules.isIrradiated(source)) {
            return false;
        }

        var destination = pickClosestSister(source, lineage);
        if (destination == null) {
            Alien.LOGGER.info(
                "Hive: migration declined for {} — no sister destination in lineage {}",
                source.id(),
                lineageFactionId
            );
            return false;
        }

        var serverLevel = server.getLevel(source.dimension());
        if (serverLevel == null) {
            Alien.LOGGER.warn(
                "Hive: migration declined for {} — dimension {} not loaded",
                source.id(),
                source.dimension().location()
            );
            return false;
        }

        // If this is the empress's own seat, she does NOT evacuate with it - she is written off and exiled here.
        // Her royal guard refuses the order and stays: praetorians, crushers and predaliens are held back from the
        // drain below so the remnant keeps its heavies. Everything else leaves, exactly as it would from any hive.
        var exilingEmpress = EmpressExileService.isEmpressSeat(source, lineage);

        // Drain the source's reserves into the convoy composition.
        var composition = new com.blib.api.common.entity.v1.EntityReserves();
        for (var type : new java.util.ArrayList<>(source.localReserves().getAvailableEntityTypes())) {
            if (exilingEmpress && EmpressExileService.isRoyalGuard(type)) {
                continue;
            }
            var count = source.localReserves().getCount(type);
            composition.add(type, count);
            source.localReserves().underlying().add(type, -count);
        }

        // Capture biomass payload (capped per HIVE_REDESIGN_10_BIOMASS § 6).
        var biomassPayload = Math.min(source.biomass(), config.migrationBiomassPayloadCap());

        // Capture empress-carry: Phase 8b records the flag; Phase 10 will handle the actual entity move.
        var carriesEmpress = lineage.empressId() != null && wasEmpressAtSource(lineage, source);

        var convoy = new Convoy.Migration(
            ConvoyId.fresh(),
            lineageFactionId,
            source.dimension(),
            source.id(),
            destination.id(),
            new Vec3(source.centerPos().getX() + 0.5, source.centerPos().getY() + 0.5, source.centerPos().getZ() + 0.5),
            destination.centerPos(),
            composition,
            biomassPayload,
            carriesEmpress,
            currentTick
        );

        // Push the boss bar into Evacuating state for visible feedback (held for migrationRallyTicks).
        var bossBar = source.bossBar();
        if (bossBar != null) {
            bossBar.enterEvacuating(config.migrationRallyTicks());
        }

        lineage.convoys().add(convoy);
        lineage.markDirty();

        Alien.LOGGER.info(
            "Hive: migration dispatched: {} → {} carrying {} members + {} biomass{}",
            source.id(),
            destination.id(),
            composition.getCount(),
            biomassPayload,
            carriesEmpress ? " (empress)" : ""
        );

        // The empress's seat is NOT deleted - it survives as her exile, stripped of everything the convoy took and
        // holding only her and her guard. Every other source dies on dispatch as before
        // (per HIVE_REDESIGN_03_LOCATIONS § 10 + § 06 CONVOYS § 5).
        if (!EmpressExileService.exile(serverLevel, source, lineage, true)) {
            LocationRemovalHelper.remove(serverLevel, source, lineage, new HiveLocationRemovalReason.Migrated(destination.id().value()));
        }

        return true;
    }

    private static @Nullable HiveLocation pickClosestSister(HiveLocation source, LineageFactionData lineage) {
        HiveLocation best = null;
        var bestDistSqr = Double.MAX_VALUE;

        for (var sister : lineage.locationsById().values()) {
            if (sister.id().equals(source.id()) || !sister.isAlive() || sister.isExiled()) {
                // Never evacuate INTO a remnant - it has no economy to receive anything.
                continue;
            }
            var distSqr = sister.centerPos().distSqr(source.centerPos());
            if (distSqr < bestDistSqr) {
                bestDistSqr = distSqr;
                best = sister;
            }
        }

        return best;
    }

    /** True if the empress is currently in the source location's loaded membership. */
    private static boolean wasEmpressAtSource(LineageFactionData lineage, HiveLocation source) {
        var empressId = lineage.empressId();
        if (empressId == null) {
            return false;
        }
        for (var entry : source.loadedMembersByType().values()) {
            if (entry.contains(empressId)) {
                return true;
            }
        }
        return false;
    }

}
