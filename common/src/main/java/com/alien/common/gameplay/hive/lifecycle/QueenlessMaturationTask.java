package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.economy.CastePopulation;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.model.lifecycle.growth.GrowthStage;
import com.alien.common.registry.GrowthStageRegistry;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

/**
 * Queenless lineage → matured queen pipeline. Runs on the slow-scan cadence (5 min) inside
 * {@link com.alien.common.gameplay.hive.faction.LineageInvariantTask#scanAllWithLifecycle}.
 * <p>
 * For each lineage with no empress and no pending empress emergence, walks every location and:
 * <ul>
 * <li>Reads the location's current leader via
 * {@link com.alien.common.gameplay.hive.location.HiveLocationLeadership}.</li>
 * <li>If the leader UUID has changed since the last advance — either first time or because a cocoon transition produced
 * a fresh entity — resets {@link HiveLocation#queenlessLeaderSnapshot} and
 * {@link HiveLocation#queenlessMaturationLastAdvanceTick}, then waits one full interval before advancing.</li>
 * <li>Otherwise, when {@code currentTick - lastAdvanceTick >= protoHiveStageInterval}, picks the queen-track growth
 * stage and calls {@link com.alien.common.gameplay.entity.living.alien.GrowthManager#forceGrow} on the leader.</li>
 * <li>Skips while the leader is currently cocooning (let the in-flight molt finish).</li>
 * <li>Skips when the leader is already a queen or empress (nothing more to mature; the
 * {@link com.alien.common.gameplay.hive.empress.EmpressEmergenceTask} handles empress promotion in multi-location
 * lineages).</li>
 * </ul>
 * <p>
 * Bypasses the metamorphosis-effect requirement that the JSON growth stages declare — the requirement here is the
 * hive's social state ("there is no queen and you are the leader of the cluster"), not a player-applied potion.
 */
public final class QueenlessMaturationTask {

    private QueenlessMaturationTask() {}

    public static void scanAll(MinecraftServer server) {
        var currentTick = server.overworld().getGameTime();
        var stageInterval = HiveLocationRegistry.INSTANCE.config().protoHiveStageInterval();

        for (var factionId : new java.util.ArrayList<>(Alien.MOD.factions().getAllIds())) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage) || !lineage.isAlive()) {
                continue;
            }
            if (lineage.empressId() != null || lineage.pendingEmpressEmergence()) {
                continue;
            }

            var serverLevel = server.getLevel(lineage.dimension());
            if (serverLevel == null) {
                continue;
            }

            for (var location : new java.util.ArrayList<>(lineage.locationsById().values())) {
                if (!location.isAlive()) {
                    continue;
                }
                advanceLocation(serverLevel, faction, lineage, factionId, location, currentTick, stageInterval);
            }
        }
    }

    /** Public entry point for the debug command. Forces an advance even if the interval hasn't elapsed. */
    public static int forceAdvance(MinecraftServer server, ResourceLocation lineageId) {
        var faction = Alien.MOD.factions().get(lineageId);
        if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
            return 0;
        }
        if (lineage.empressId() != null) {
            return 0;
        }
        var serverLevel = server.getLevel(lineage.dimension());
        if (serverLevel == null) {
            return 0;
        }
        var advanced = 0;
        for (var location : new java.util.ArrayList<>(lineage.locationsById().values())) {
            if (!location.isAlive()) {
                continue;
            }
            // Force-advance: pretend the interval already elapsed.
            location.setQueenlessMaturationLastAdvanceTick(0L);
            advanceLocation(serverLevel, faction, lineage, lineageId, location, server.overworld().getGameTime(), 0L);
            advanced++;
        }
        return advanced;
    }

    private static void advanceLocation(
            net.minecraft.server.level.ServerLevel serverLevel,
            com.blib.api.common.faction.v1.Faction<?> faction,
            LineageFactionData lineage,
            ResourceLocation lineageId,
            HiveLocation location,
            long currentTick,
            long stageInterval
    ) {
        var leaderId = location.leadership().getLeaderIdOrNull();
        if (leaderId == null) {
            // Dormant; clear any stale snapshot so a fresh leader restarts the timer cleanly.
            if (location.queenlessLeaderSnapshot() != null) {
                location.setQueenlessLeaderSnapshot(null);
                location.setQueenlessMaturationLastAdvanceTick(Long.MIN_VALUE);
            }
            return;
        }
        if (CastePopulation.countCaste(location, AlienEntityTypeTags.QUEENS) > 0) {
            return;
        }

        // Leader UUID changed since last observation — either first tag or post-transition. Reset and wait.
        if (!leaderId.equals(location.queenlessLeaderSnapshot())) {
            location.setQueenlessLeaderSnapshot(leaderId);
            location.setQueenlessMaturationLastAdvanceTick(currentTick);
            // After a transition, idempotently re-add the new entity to the lineage's BLib membership — the cocoon
            // produces a fresh UUID that's not yet a member.
            var entity = serverLevel.getEntity(leaderId);
            if (entity != null && !faction.membership().hasMember(com.blib.api.common.faction.v1.FactionMember.entity(entity))) {
                faction.membership().addEntity(entity);
                Alien.LOGGER.info(
                        "Hive: queenless leader {} re-joined lineage {} after cocoon transition",
                        leaderId,
                        lineageId
                );
            }
            return;
        }

        var entity = serverLevel.getEntity(leaderId);
        if (!(entity instanceof Xenomorph xenomorph) || !entity.isAlive()) {
            return;
        }
        // Already a queen / empress — nothing to mature. (EmpressEmergenceTask handles empress promotion if 2+
        // locations.)
        if (entity.getType().is(AlienEntityTypeTags.QUEENS) || entity.getType().is(AlienEntityTypeTags.EMPRESSES)) {
            return;
        }
        // Currently cocooning — let the in-flight molt finish.
        if (xenomorph.getCocoonManager().getState() != com.alien.common.gameplay.entity.living.alien.xenomorph.CocoonState.NONE) {
            return;
        }

        if (currentTick - location.queenlessMaturationLastAdvanceTick() < stageInterval) {
            return;
        }

        var stage = pickQueenTrackStage(entity.getType());
        if (stage == null) {
            // No reachable next form — e.g., a non-xenomorph or a dead-end caste. Stop maturing this leader.
            return;
        }

        if (stage.to().is(AlienEntityTypeTags.QUEENS)) {
            // This is the crowning molt itself — gated behind the firewall fund (per
            // AVP_Queen_Lifecycle_Design.md § 6). A fresh location's fund starts available, so a location's first-ever
            // queen loss always crowns normally; only a second loss before the fund refills gets denied here. Earlier,
            // non-queen growth stages toward queen-track (drone→warrior→praetorian, etc.) are never gated — only the
            // final step is.
            var config = HiveLocationRegistry.INSTANCE.config();

            // Rescue hold: while this location has an unresolved rescue campaign for its captured queen, the hive
            // holds out hope and won't crown a replacement. RescueCampaignTask clears the campaign on success, on
            // conversion (queen died), or on exhaustion (3 failed attempts) — only then does crowning proceed.
            if (location.rescueCampaign() != null) {
                Alien.LOGGER.info(
                        "Hive: crowning held for leader {} (lineage {}) — rescue campaign still active for the lost queen",
                        leaderId,
                        lineageId
                );
                return;
            }

            if (!location.firewallFundAvailable()) {
                Alien.LOGGER.info(
                        "Hive: crowning denied for leader {} (lineage {}) — firewall fund still spent, {}/{} stable ticks accrued",
                        leaderId,
                        lineageId,
                        location.firewallStableAccruedTicks(),
                        config.firewallCooldownTicks()
                );
                return;
            }

            var jellyCost = config.firewallCrowningJellyCost();
            if (location.royalJelly() < jellyCost) {
                Alien.LOGGER.info(
                        "Hive: crowning denied for leader {} (lineage {}) — insufficient royal jelly ({}/{})",
                        leaderId,
                        lineageId,
                        location.royalJelly(),
                        jellyCost
                );
                return;
            }

            location.setRoyalJelly(location.royalJelly() - jellyCost);
            location.setFirewallFundAvailable(false);
            location.setFirewallStableAccruedTicks(0L);
            location.setFirewallBiomassSampleTick(Long.MIN_VALUE);
            location.setFirewallBiomassSampleValue(0);
        }

        var result = xenomorph.getGrowthManager().forceGrow(stage);
        location.setQueenlessMaturationLastAdvanceTick(currentTick);
        Alien.LOGGER.info(
                "Hive: queenless maturation advanced leader {} ({}) → {} (lineage {}); growth result {}",
                leaderId,
                entity.getType().builtInRegistryHolder().key().location(),
                stage.to().builtInRegistryHolder().key().location(),
                lineageId,
                result.getClass().getSimpleName()
        );
    }

    /**
     * Picks the growth stage closest to queen for {@code from}. Queen-track preference: queens > praetorians/crushers >
     * warriors/prowlers. Other paths (drone → carrier, drone → razor_claw, warrior → ravager, praetorian → harbinger)
     * are dead-ends for queen maturation and only get picked if no queen-track candidate is available.
     */
    private static @Nullable GrowthStage pickQueenTrackStage(EntityType<?> from) {
        var candidates = GrowthStageRegistry.getCandidates(null, from);
        if (candidates.isEmpty()) {
            return null;
        }
        return pickByPriority(
                candidates,
                AlienEntityTypeTags.QUEENS,
                AlienEntityTypeTags.PRAETORIANS,
                AlienEntityTypeTags.CRUSHERS,
                AlienEntityTypeTags.WARRIORS,
                AlienEntityTypeTags.PROWLERS
        );
    }

    @SafeVarargs
    private static @Nullable GrowthStage pickByPriority(java.util.List<GrowthStage> candidates, TagKey<EntityType<?>>... priority) {
        for (var tag : priority) {
            for (var stage : candidates) {
                if (stage.to().is(tag)) {
                    return stage;
                }
            }
        }
        // Fallback: any candidate.
        return candidates.get(0);
    }

    /** Used by the inspect debug command. */
    public static @Nullable Entity peekLeader(MinecraftServer server, HiveLocation location) {
        var serverLevel = server.getLevel(location.dimension());
        if (serverLevel == null) {
            return null;
        }
        var leaderId = location.leadership().getLeaderIdOrNull();
        if (leaderId == null) {
            return null;
        }
        return serverLevel.getEntity(leaderId);
    }
}