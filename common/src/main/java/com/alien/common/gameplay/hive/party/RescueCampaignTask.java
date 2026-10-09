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
 * <li><b>Promote</b> — a pending {@link RescueCampaign} (recorded when she was captured - first chain or inhibitor)
 * becomes active once the lost queen is CAPTIVE and outside this hive's slab. Oct 3, [stated]: "the hive will try to
 * free her if shes still in the slab otherwise it would be like a kidnapped queen." Inside the slab her kin claw at her
 * chains directly ({@code QueenRescueManager}).</li>
 * <li><b>Dispatch / track</b> — while active with attempts remaining and no raid in flight, dispatch a rescue raid at
 * the captor. A raid that ends (returns home / disappears) without freeing her counts as a failed attempt; a failure to
 * even form a party also counts.</li>
 * <li><b>Success</b> — once the queen is free (no chain, no inhibitor) and her release grace is over: if she is
 * standing in this hive's slab and her seat is still empty she is put back on the throne; either way the campaign
 * clears. The raid, if any, is left to return home on its own.</li>
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
                if (com.alien.common.gameplay.hive.dimension.EndStyleHiveRules.isEndStyle(server, location)) {
                    continue; // END-STYLE: no rescue campaigns - convoys of every type are off
                }
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

        // \u2b50 Oct 3 - THE LOST-QUEEN CLOCK. Without it a campaign whose queen was carried somewhere unloaded (a far
        // base,
        // another dimension) never armed and never failed, and every succession path waits on an open campaign - so
        // the hive would never get a new queen. See RescueCampaign.LOST_AFTER_UNSEEN_TICKS.
        var now = serverLevel.getGameTime();
        if (queen != null) {
            campaign.markSeen(now);
        } else if (
            campaign.markUnseen(
                now,
                serverLevel.isLoaded(location.centerPos()),
                // \u26a0 This task runs once per lineage scan (default every 5 minutes). The cap on one counted gap
                // MUST follow that interval - a fixed 1200 here charged 1 minute per 5-minute scan and made the
                // one-day limit take five days.
                2L * Math.max(
                    1L,
                    com.alien.common.gameplay.hive.location.HiveLocationRegistry.INSTANCE.config()
                        .lineageScanIntervalTicks()
                )
            )
        ) {
            declareLost(server, lineage, factionId, location, campaign);
            return;
        }

        // Success: the queen is free - no chain AND no inhibitor (Oct 3: one chain used to read as "free" because only
        // four counted as contained). Killed is handled separately in die().
        if (queen != null && !com.alien.common.gameplay.hive.lifecycle.QueenCaptivity.isCaptive(queen)) {
            // \u2b50 Oct 3 - WAIT OUT HER GRACE, THEN BRING HER HOME IF SHE IS HOME. A queen freed inside her own slab
            // (by a player, or by kin the rescue manager did not recruit) gets her seat back once she may join again;
            // the campaign is what remembers which seat. Freed anywhere else she is her own queen and it simply clears.
            // Only a grace that is actually BLOCKING her: the 30s kin grace (anywhere), or the 5-minute one while she
            // is
            // outside every slab. Released inside her own slab, the 5-minute grace does not apply ([stated] "any new
            // queens ... in a hive slab follow the same rules currently") and she goes straight back on her throne.
            if (com.alien.common.gameplay.hive.lifecycle.QueenCaptivity.isGraceBlocking(queen)) {
                return;
            }
            var restored = location.withinSlab(queen.blockPosition().getY())
                && location.claimedChunks().contains(new ChunkPos(queen.blockPosition()))
                && com.alien.common.gameplay.hive.lifecycle.QueenCaptivity.tryRestoreToOriginalHive(queen, location);
            if (!restored) {
                Alien.LOGGER.info(
                    "Hive: rescue succeeded — queen {} is free; clearing campaign at {}",
                    campaign.queenUuid(),
                    location.id()
                );
                location.setRescueCampaign(null);
            }
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

    /**
     * [stated] "she is counted as lost. revenge party would then take over and a new queen is crowned either with the
     * royal bank backup an available praetorian if the bank isnt available or other means built into the mod."
     * <p>
     * The same conversion her death runs: the captor (if one was ever named) becomes the hive's grudge, a revenge raid
     * goes after them if they are online, and the campaign closes - which is all the succession paths were waiting for.
     * They then crown in their usual order (the royal bank, then a maturing praetorian, then the rest).
     * </p>
     */
    private static void declareLost(
        MinecraftServer server,
        LineageFactionData lineage,
        net.minecraft.resources.ResourceLocation factionId,
        HiveLocation location,
        RescueCampaign campaign
    ) {
        var captorId = campaign.captorPlayerId();
        if (captorId != null) {
            location.setGrudgePlayerId(captorId);
        }
        location.setRescueCampaign(null);
        lineage.markDirty();
        var captor = captorId == null ? null : server.getPlayerList().getPlayer(captorId);
        if (captor != null) {
            RaidDispatch.onQueenKilled(server, lineage, factionId, captor);
        }
        Alien.LOGGER.info(
            "Hive: queen {} counted as LOST by {} - unseen for {} ticks; {} - succession may now crown a replacement",
            campaign.queenUuid(),
            location.id(),
            campaign.unseenTicks(),
            captor != null ? "revenge raid sent at captor " + captorId : "no captor online, grudge stamped only"
        );
    }

    /** Promotes a pending campaign to active once the queen is captive AND outside this location's slab. */
    private static void tryPromote(HiveLocation location, RescueCampaign campaign, Queen queen, LineageFactionData lineage) {
        if (queen == null || !com.alien.common.gameplay.hive.lifecycle.QueenCaptivity.isCaptive(queen)) {
            return; // not captive (or not loaded)
        }
        var queenChunk = new ChunkPos(queen.blockPosition());
        if (location.claimedChunks().contains(queenChunk) && location.withinSlab(queen.blockPosition().getY())) {
            return; // still in her hive's slab - her kin are working on her chains there, this is not a kidnapping
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
