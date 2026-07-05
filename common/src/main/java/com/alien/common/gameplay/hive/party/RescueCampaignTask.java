package com.alien.common.gameplay.hive.party;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.convoy.Convoy;
import com.alien.common.gameplay.hive.convoy.RaidDispatch;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;

/**
 * Drives the Part 4 recovery pipeline for captured queens. Runs on the slow lifecycle scan (see
 * {@code LineageInvariantTask}). For every hive location:
 * <ol>
 * <li><b>Promote</b> — a pending {@link RescueCampaign} (recorded at inhibition) becomes active once the lost queen is
 * detected {@link Queen#isContained() contained} AND physically outside this location's claimed chunks (per design:
 * capture-in-place is frenzy, not rescue).</li>
 * <li><b>Dispatch / track</b> — while active with attempts remaining and no raid in flight, dispatch a rescue raid at
 * the captor. A raid that ends (returns home / disappears) without freeing her counts as a failed attempt; a failure
 * to even form a party also counts.</li>
 * <li><b>Success</b> — if the queen is freed (no longer contained, or gone/reunited), the campaign clears; the raid,
 * if any, is left to return home on its own.</li>
 * <li><b>Exhaustion</b> — at {@link RescueCampaign#MAX_ATTEMPTS} failures, the campaign clears and stops blocking the
 * firewall, which then crowns a replacement.</li>
 * </ol>
 * The recovery→revenge conversion (queen dies mid-recovery) is handled in {@code Alien.die()}, which reads the active
 * campaign's {@code attemptsFailed} and fires a revenge raid carrying it.
 */
public final class RescueCampaignTask {

    private RescueCampaignTask() {}

    public static void scanAll(MinecraftServer server) {
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
                var campaign = location.rescueCampaign();
                if (campaign != null) {
                    process(server, serverLevel, lineage, factionId, location, campaign);
                }
            }
        }
    }

    private static void process(
        MinecraftServer server,
        ServerLevel serverLevel,
        LineageFactionData lineage,
        net.minecraft.resources.ResourceLocation factionId,
        HiveLocation location,
        RescueCampaign campaign
    ) {
        var queen = serverLevel.getEntity(campaign.queenUuid()) instanceof Queen q ? q : null;

        // Success: the queen is freed (no longer contained) or gone entirely (killed handled separately in die()).
        // A freed queen re-founds on her own; the campaign's job is done.
        if (queen != null && !queen.isContained()) {
            Alien.LOGGER.info("Hive: rescue succeeded — queen {} is free; clearing campaign at {}", campaign.queenUuid(), location.id());
            location.setRescueCampaign(null);
            lineage.markDirty();
            return;
        }

        if (!campaign.active()) {
            tryPromote(location, campaign, queen, lineage);
            return;
        }

        // Active campaign: check whether the in-flight raid (if any) has ended without success → count a failure.
        if (campaign.hasActiveRaid()) {
            if (!raidStillActive(lineage, campaign)) {
                campaign.recordFailure();
                lineage.markDirty();
                Alien.LOGGER.info(
                    "Hive: rescue attempt failed ({}/{}) for queen {} at {}",
                    campaign.attemptsFailed(),
                    RescueCampaign.MAX_ATTEMPTS,
                    campaign.queenUuid(),
                    location.id()
                );
            } else {
                return; // raid still hunting; nothing to do this scan
            }
        }

        if (campaign.attemptsExhausted()) {
            Alien.LOGGER.info(
                "Hive: rescue exhausted for queen {} at {} — firewall may now crown a replacement",
                campaign.queenUuid(),
                location.id()
            );
            location.setRescueCampaign(null);
            lineage.markDirty();
            return;
        }

        // Attempts remain and no raid in flight → dispatch the next one.
        var captor = campaign.captorPlayerId() == null ? null : server.getPlayerList().getPlayer(campaign.captorPlayerId());
        if (captor == null) {
            // Captor offline / unknown — can't form a party against nobody; count it as a failed attempt.
            campaign.recordFailure();
            lineage.markDirty();
            return;
        }

        var raidId = RaidDispatch.dispatchRescue(server, lineage, factionId, location, captor);
        if (raidId == null) {
            // Failure to even form a party counts as a failure (Part 4).
            campaign.recordFailure();
        } else {
            campaign.setCurrentRaidId(raidId.value());
        }
        lineage.markDirty();
    }

    /** Promotes a pending campaign to active once the queen is contained AND outside this location's claim. */
    private static void tryPromote(HiveLocation location, RescueCampaign campaign, Queen queen, LineageFactionData lineage) {
        if (queen == null || !queen.isContained()) {
            return; // not yet contained (or not loaded) — capture-in-place would be frenzy, handled elsewhere
        }
        var queenChunk = new ChunkPos(queen.blockPosition());
        if (location.claimedChunks().contains(queenChunk)) {
            return; // still inside her original claim — capture-in-place, not a rescue
        }

        // Contained + carried outside her claim = lost. Captor = the nearest player to her right now (the one holding
        // or escorting her out) — resolved here rather than threaded through chain-placement code. If nobody's nearby
        // this scan, stay pending and retry next scan.
        var captor = nearestPlayer(queen);
        if (captor == null) {
            return;
        }

        campaign.activate(captor.getUUID());
        lineage.markDirty();
        Alien.LOGGER.info(
            "Hive: rescue campaign armed for lost queen {} (original hive {}), captor {}",
            campaign.queenUuid(),
            location.id(),
            captor.getUUID()
        );
    }

    private static net.minecraft.server.level.ServerPlayer nearestPlayer(Queen queen) {
        if (!(queen.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        net.minecraft.server.level.ServerPlayer nearest = null;
        var nearestDistSqr = Double.MAX_VALUE;
        for (var player : serverLevel.players()) {
            if (player.isSpectator() || player.isCreative()) {
                continue;
            }
            var distSqr = player.distanceToSqr(queen);
            if (distSqr < nearestDistSqr) {
                nearestDistSqr = distSqr;
                nearest = player;
            }
        }
        return nearest;
    }

    private static boolean raidStillActive(LineageFactionData lineage, RescueCampaign campaign) {
        for (var convoy : lineage.convoys()) {
            if (
                convoy instanceof Convoy.Raid raid
                    && raid.id().value().equals(campaign.currentRaidId())
                    && !raid.returningHome()
            ) {
                return true;
            }
        }
        return false;
    }
}
