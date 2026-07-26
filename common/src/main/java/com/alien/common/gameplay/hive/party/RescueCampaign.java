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

    private final UUID queenUuid;

    private UUID captorPlayerId;

    private boolean active;

    private int attemptsFailed;

    private UUID currentRaidId;

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
        return tag;
    }

    public static RescueCampaign load(CompoundTag tag) {
        return new RescueCampaign(
            tag.getUUID("QueenUuid"),
            tag.hasUUID("CaptorPlayerId") ? tag.getUUID("CaptorPlayerId") : null,
            tag.getBoolean("Active"),
            tag.getInt("AttemptsFailed"),
            tag.hasUUID("CurrentRaidId") ? tag.getUUID("CurrentRaidId") : null
        );
    }
}
