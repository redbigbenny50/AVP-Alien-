package com.alien.common.gameplay.hive.party;

import com.alien.common.gameplay.hive.location.HiveLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Per-player retribution campaign state for {@link HiveParty.AttackParty}, driven by the territorial-intrusion model: a
 * player who breaches a hive location's claim, fights its members, and lingers past a dwell threshold earns a fixed
 * two-wave offense response — wave 1 a full MC day after the intrusion, wave 2 one cooldown period after wave 1.
 * <p>
 * Lifecycle:
 * <ul>
 * <li>Created / reset when {@code dwellTicks} crosses the intrusion threshold in {@code HiveTerritoryAggroTask} — a
 * re-intrusion after a prior campaign cleared wipes the old state and starts fresh.</li>
 * <li>{@code wavesSent} advances 0 → 1 → 2 as {@code AttackPartyDispatch} fires each wave on schedule.</li>
 * <li>{@code cleared} is set when either wave's party kills the player, or the player survives wave 2's full duration
 * ({@code AttackPartyLifecycleTask}). A cleared campaign dispatches nothing further; the hive leaves the player alone
 * until they intrude again.</li>
 * </ul>
 * <p>
 * {@code dwellTicks} is the accrued in-claim-while-recently-hostile time used only <em>before</em> a campaign begins;
 * once {@code intrusionTick >= 0} the campaign is active and dwell is no longer accumulated (it resets on clear).
 */
public final class AttackCampaign {

    private long dwellTicks;

    private long lastHostileTick;

    private long intrusionTick;

    private int wavesSent;

    private long lastWaveTick;

    private boolean cleared;

    public AttackCampaign() {
        this.dwellTicks = 0L;
        this.lastHostileTick = Long.MIN_VALUE;
        this.intrusionTick = -1L;
        this.wavesSent = 0;
        this.lastWaveTick = Long.MIN_VALUE;
        this.cleared = false;
    }

    private AttackCampaign(long dwellTicks, long lastHostileTick, long intrusionTick, int wavesSent, long lastWaveTick, boolean cleared) {
        this.dwellTicks = dwellTicks;
        this.lastHostileTick = lastHostileTick;
        this.intrusionTick = intrusionTick;
        this.wavesSent = wavesSent;
        this.lastWaveTick = lastWaveTick;
        this.cleared = cleared;
    }

    public boolean campaignActive() {
        return intrusionTick >= 0L && !cleared;
    }

    public long dwellTicks() {
        return dwellTicks;
    }

    public void addDwellTicks(long delta) {
        this.dwellTicks += delta;
    }

    public void resetDwell() {
        this.dwellTicks = 0L;
    }

    /**
     * Stamp a hostile act against a hive. Dwell only accrues for a player who is BOTH inside the claim and recently
     * hostile ({@code HiveTerritoryAggroTask.HOSTILE_RECENCY_TICKS}) - that is what tells "fighting the hive" apart
     * from "walking through it". Until now the only thing that counted as hostile was hitting a member, so a player
     * could stroll into a host chamber, cut the hive's larder loose, and stroll out again without the hive ever
     * noticing. Freeing a captive is theft, and the hive treats it as a blow.
     */
    public static void recordHostileAct(ServerLevel level, HiveLocation location, ServerPlayer player) {
        if (!location.isAlive()) {
            return;
        }
        location.attackCampaigns()
                .computeIfAbsent(player.getUUID(), ignored -> new AttackCampaign())
                .setLastHostileTick(level.getGameTime());
    }

    public long lastHostileTick() {
        return lastHostileTick;
    }

    public void setLastHostileTick(long tick) {
        this.lastHostileTick = tick;
    }

    public long intrusionTick() {
        return intrusionTick;
    }

    public void beginCampaign(long tick) {
        this.intrusionTick = tick;
        this.wavesSent = 0;
        this.lastWaveTick = Long.MIN_VALUE;
        this.cleared = false;
        this.dwellTicks = 0L;
    }

    public int wavesSent() {
        return wavesSent;
    }

    public long lastWaveTick() {
        return lastWaveTick;
    }

    public void recordWaveSent(long tick) {
        this.wavesSent++;
        this.lastWaveTick = tick;
    }

    public boolean cleared() {
        return cleared;
    }

    public void markCleared() {
        this.cleared = true;
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putLong("DwellTicks", dwellTicks);
        tag.putLong("LastHostileTick", lastHostileTick);
        tag.putLong("IntrusionTick", intrusionTick);
        tag.putInt("WavesSent", wavesSent);
        tag.putLong("LastWaveTick", lastWaveTick);
        tag.putBoolean("Cleared", cleared);
        return tag;
    }

    public static AttackCampaign load(CompoundTag tag) {
        return new AttackCampaign(
                tag.getLong("DwellTicks"),
                tag.contains("LastHostileTick") ? tag.getLong("LastHostileTick") : Long.MIN_VALUE,
                tag.contains("IntrusionTick") ? tag.getLong("IntrusionTick") : -1L,
                tag.getInt("WavesSent"),
                tag.contains("LastWaveTick") ? tag.getLong("LastWaveTick") : Long.MIN_VALUE,
                tag.getBoolean("Cleared")
        );
    }
}