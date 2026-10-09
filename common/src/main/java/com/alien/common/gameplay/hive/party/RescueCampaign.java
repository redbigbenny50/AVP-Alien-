package com.alien.common.gameplay.hive.party;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Per-lost-queen recovery campaign state, stored on the queen's <b>original</b> {@code HiveLocation} (the one she
 * founded, now queenless-but-alive after inhibition severed her out). Drives the Part 4 recovery pipeline:
 * <ul>
 * <li><b>Pending</b> ({@code queenUuid} set, {@code active} false): recorded at inhibition time, before the campaign is
 * confirmed — she may be contained in-place (frenzy, not rescue). Promoted to active once she's detected contained AND
 * carried outside this location's claim.</li>
 * <li><b>Active</b>: a rescue raid is (or should be) tracking the player holding her. Each raid that ends without
 * freeing her, or a failure to even form a party, increments {@code attemptsFailed}.</li>
 * <li><b>Resolved</b>: she's freed (success — campaign removed), 3 attempts failed (firewall crowns a replacement), or
 * she dies mid-recovery (converts to a revenge raid carrying {@code attemptsFailed}, campaign removed).</li>
 * </ul>
 * While a campaign is unresolved, {@code QueenlessMaturationTask} holds off crowning a replacement — the hive holds out
 * hope until rescue gives up at 3 failures.
 */
public final class RescueCampaign {

    public static final int MAX_ATTEMPTS = 3;

    /**
     * \u2b50 Oct 3 - [stated] "if they cannot locate(load) the queen in that period of time she is counted as lost."
     * One Minecraft day of the hive being loaded without its queen being found anywhere in its dimension. Counted only
     * while the hive itself is loaded: a hive nobody is near is not looking for anyone, and must not lose its queen
     * just because the server sat empty overnight.
     */
    public static final long LOST_AFTER_UNSEEN_TICKS = 24_000L;

    private final UUID queenUuid;

    private UUID captorPlayerId;

    private boolean active;

    private int attemptsFailed;

    private UUID currentRaidId;

    /** Loaded-hive ticks since the queen was last found. Persisted, so a relog does not reset the search. */
    private long unseenTicks;

    /** Game time of the last search tick (transient: the first scan after a load just re-starts the clock). */
    private long lastSearchGameTime = -1L;

    public RescueCampaign(UUID queenUuid) {
        this.queenUuid = queenUuid;
        this.captorPlayerId = null;
        this.active = false;
        this.attemptsFailed = 0;
        this.currentRaidId = null;
    }

    private RescueCampaign(UUID queenUuid, UUID captorPlayerId, boolean active, int attemptsFailed, UUID currentRaidId) {
        this.queenUuid = queenUuid;
        this.captorPlayerId = captorPlayerId;
        this.active = active;
        this.attemptsFailed = attemptsFailed;
        this.currentRaidId = currentRaidId;
    }

    public UUID queenUuid() {
        return queenUuid;
    }

    public UUID captorPlayerId() {
        return captorPlayerId;
    }

    public void setCaptorPlayerId(UUID captorPlayerId) {
        this.captorPlayerId = captorPlayerId;
    }

    public boolean active() {
        return active;
    }

    public void activate(UUID captorPlayerId) {
        this.active = true;
        this.captorPlayerId = captorPlayerId;
    }

    public int attemptsFailed() {
        return attemptsFailed;
    }

    public void recordFailure() {
        this.attemptsFailed++;
        this.currentRaidId = null;
    }

    public boolean attemptsExhausted() {
        return attemptsFailed >= MAX_ATTEMPTS;
    }

    public UUID currentRaidId() {
        return currentRaidId;
    }

    public void setCurrentRaidId(UUID currentRaidId) {
        this.currentRaidId = currentRaidId;
    }

    public boolean hasActiveRaid() {
        return currentRaidId != null;
    }

    /**
     * The queen was found this scan.
     *
     * @param gameTime the current game time
     */
    public void markSeen(long gameTime) {
        unseenTicks = 0L;
        lastSearchGameTime = gameTime;
    }

    /**
     * The queen was NOT found this scan. {@code hiveLoaded} says whether the time counts.
     *
     * @param gameTime      the current game time
     * @param hiveLoaded    whether the hive itself is loaded (only then is anyone looking for her)
     * @param maxCountedGap the longest gap between two scans that is counted; anything longer means the server was not
     *                      simulating the hive (stopped, or the scan cadence was changed) and must not be charged to
     *                      the search
     * @return true once she has been unseen long enough to be counted as lost
     */
    public boolean markUnseen(long gameTime, boolean hiveLoaded, long maxCountedGap) {
        if (lastSearchGameTime >= 0L && hiveLoaded) {
            unseenTicks += Math.min(Math.max(0L, gameTime - lastSearchGameTime), maxCountedGap);
        }
        lastSearchGameTime = gameTime;
        return unseenTicks >= LOST_AFTER_UNSEEN_TICKS;
    }

    public long unseenTicks() {
        return unseenTicks;
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("QueenUuid", queenUuid);
        if (captorPlayerId != null) {
            tag.putUUID("CaptorPlayerId", captorPlayerId);
        }
        tag.putBoolean("Active", active);
        tag.putInt("AttemptsFailed", attemptsFailed);
        if (currentRaidId != null) {
            tag.putUUID("CurrentRaidId", currentRaidId);
        }
        tag.putLong("UnseenTicks", unseenTicks);
        return tag;
    }

    public static RescueCampaign load(CompoundTag tag) {
        var campaign = new RescueCampaign(
            tag.getUUID("QueenUuid"),
            tag.hasUUID("CaptorPlayerId") ? tag.getUUID("CaptorPlayerId") : null,
            tag.getBoolean("Active"),
            tag.getInt("AttemptsFailed"),
            tag.hasUUID("CurrentRaidId") ? tag.getUUID("CurrentRaidId") : null
        );
        campaign.unseenTicks = tag.getLong("UnseenTicks");
        return campaign;
    }
}
