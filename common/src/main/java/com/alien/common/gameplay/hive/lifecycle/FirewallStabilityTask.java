package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.economy.CastePopulation;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;

/**
 * Royal-replacement "firewall" fund tracker. Runs on the slow-scan cadence (5 min, same call site as
 * {@link QueenlessMaturationTask}) from {@link com.alien.common.gameplay.hive.faction.LineageInvariantTask}.
 * <p>
 * Only evaluates locations whose fund is currently spent ({@code !location.firewallFundAvailable()}) — a location
 * that has never lost a queen through {@link QueenlessMaturationTask}, or whose fund already refilled, needs no
 * tracking. For each such location, checks all four stability conditions per {@code AVP_Queen_Lifecycle_Design.md} §
 * 6:
 * <ol>
 * <li>Has eggs — a live, loaded queen with an active ovipositor.</li>
 * <li>Jelly reserves — {@link HiveLocation#royalJelly()} at or above {@code config.firewallJellyFloor()}.</li>
 * <li>Positive biomass income rate — sampled each scan against the previous sample
 * ({@link HiveLocation#firewallBiomassSampleTick()} / {@link HiveLocation#firewallBiomassSampleValue()}), not just a
 * positive balance. A hive coasting on a fat reserve while actively being drained reads as unstable here, correctly.</li>
 * <li>Room to grow — current tracked population below {@code populationPerChunk * claimedChunks().size()}.</li>
 * </ol>
 * <p>
 * When all four hold, {@link HiveLocation#firewallStableAccruedTicks()} accrues by the elapsed time since the last
 * sample. When any fail, accrual <b>pauses</b> (holds its progress, does not reset) — sustained pressure denies
 * refill without erasing prior progress the moment stability briefly returns. Reaching
 * {@code config.firewallCooldownTicks()} refills the fund and clears the sample state for the next spend cycle.
 * <p>
 * A location whose fund stays spent simply remains unable to crown a new queen (see
 * {@link QueenlessMaturationTask#advanceLocation}) — no separate "permanently dead" state is introduced here; a
 * denied location eventually dies through the existing {@link LocationDormancyTask} population/no-contact rules as
 * its economy withers on its own.
 */
public final class FirewallStabilityTask {

    private FirewallStabilityTask() {}

    public static void scanAll(MinecraftServer server) {
        var config = HiveLocationRegistry.INSTANCE.config();

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

            for (var location : new ArrayList<>(lineage.locationsById().values())) {
                if (!location.isAlive() || location.firewallFundAvailable()) {
                    continue;
                }
                evaluate(serverLevel, location, config);
            }
        }
    }

    private static void evaluate(ServerLevel serverLevel, HiveLocation location, com.alien.common.gameplay.hive.config.HiveConfig config) {
        var currentTick = serverLevel.getGameTime();

        if (location.firewallBiomassSampleTick() == Long.MIN_VALUE) {
            // First observation since the fund was spent (or since this location loaded) — establish the baseline
            // only; there's no prior sample to diff against yet, so no accrual this cycle.
            location.setFirewallBiomassSampleTick(currentTick);
            location.setFirewallBiomassSampleValue(location.biomass());
            return;
        }

        var elapsed = currentTick - location.firewallBiomassSampleTick();
        var biomassDelta = location.biomass() - location.firewallBiomassSampleValue();
        var biomassIncomeOk = biomassDelta > 0;

        // Resample for the next interval regardless of outcome.
        location.setFirewallBiomassSampleTick(currentTick);
        location.setFirewallBiomassSampleValue(location.biomass());

        var hasEggs = hasActiveOvipositor(serverLevel, location);
        var jellyOk = location.royalJelly() >= config.firewallJellyFloor();
        var populationRoomOk = CastePopulation.totalTrackedPopulation(location)
                < config.populationPerChunk() * location.claimedChunks().size();

        if (!(hasEggs && jellyOk && biomassIncomeOk && populationRoomOk)) {
            // Unstable — pause. Progress already accrued is preserved, we just don't add to it this cycle.
            return;
        }

        var accrued = location.firewallStableAccruedTicks() + elapsed;

        if (accrued >= config.firewallCooldownTicks()) {
            location.setFirewallFundAvailable(true);
            location.setFirewallStableAccruedTicks(0L);
            location.setFirewallBiomassSampleTick(Long.MIN_VALUE);
            location.setFirewallBiomassSampleValue(0);
            Alien.LOGGER.info(
                    "Hive: firewall fund refilled for location {} after {} stable ticks",
                    location.id(),
                    accrued
            );
            return;
        }

        location.setFirewallStableAccruedTicks(accrued);
    }

    /** True if a live, loaded queen belonging to this location currently has an active ovipositor. */
    private static boolean hasActiveOvipositor(ServerLevel serverLevel, HiveLocation location) {
        for (var entry : location.loadedMembersByType().entrySet()) {
            if (!entry.getKey().is(AlienEntityTypeTags.QUEENS)) {
                continue;
            }
            for (var uuid : entry.getValue()) {
                var entity = serverLevel.getEntity(uuid);
                if (entity instanceof Queen queen && queen.isAlive() && queen.hasOvipositor()) {
                    return true;
                }
            }
        }
        return false;
    }
}