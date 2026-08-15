package com.alien.common.gameplay.entity.living.alien.xenomorph;

import com.blib.api.common.nbt.v1.model.NBTSerializable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

public class XenomorphData implements NBTSerializable {

    public static final int MAX_IDLE_TIME_IN_TICKS = 12 * 20;

    public static final int MIN_IDLE_TIME_IN_TICKS = 7 * 20;

    private static final String NBT_TICKS_UNTIL_BORED = "ticksUntilBored";

    private static final String NBT_LAST_LUNGE_TICK = "lastLungeTick";

    private static final String NBT_ROYAL_LINE_CANDIDATE = "royalLineCandidate";

    private static final String NBT_ROYAL_CANDIDATE_KILLS = "royalCandidateKills";

    private final RandomSource random;

    private int ticksUntilBored;

    private long lastLungeTick;

    private boolean royalLineCandidate;

    private int royalCandidateKills;

    private int lastAlertedHurtTimestamp;

    private int parallelDigCount = 2;

    private int lastCrawlTick;

    private net.minecraft.core.BlockPos lastCrawlPos;

    public XenomorphData(RandomSource random) {
        this.random = random;
        this.ticksUntilBored = random.nextIntBetweenInclusive(MIN_IDLE_TIME_IN_TICKS, MAX_IDLE_TIME_IN_TICKS);
    }

    public void tick() {
        this.ticksUntilBored = Math.max(ticksUntilBored - 1, 0);
    }

    public int getTicksUntilBored() {
        return ticksUntilBored;
    }

    public void resetTicksUntilBored() {
        this.ticksUntilBored = random.nextIntBetweenInclusive(MIN_IDLE_TIME_IN_TICKS, MAX_IDLE_TIME_IN_TICKS);
    }

    public long getLastLungeTick() {
        return lastLungeTick;
    }

    public void setLastLungeTick(long tick) {
        this.lastLungeTick = tick;
    }

    public int getLastAlertedHurtTimestamp() {
        return lastAlertedHurtTimestamp;
    }

    public void setLastAlertedHurtTimestamp(int timestamp) {
        this.lastAlertedHurtTimestamp = timestamp;
    }

    public int getLastCrawlTick() {
        return lastCrawlTick;
    }

    public void setLastCrawlTick(int tick) {
        this.lastCrawlTick = tick;
    }

    public @org.jetbrains.annotations.Nullable net.minecraft.core.BlockPos getLastCrawlPos() {
        return lastCrawlPos;
    }

    public void setLastCrawlPos(net.minecraft.core.BlockPos pos) {
        this.lastCrawlPos = pos;
    }

    public int getParallelDigCount() {
        return parallelDigCount;
    }

    public void setParallelDigCount(int count) {
        this.parallelDigCount = count;
    }

    public boolean isRoyalLineCandidate() {
        return royalLineCandidate;
    }

    public void setRoyalLineCandidate(boolean royalLineCandidate) {
        this.royalLineCandidate = royalLineCandidate;
    }

    public int getRoyalCandidateKills() {
        return royalCandidateKills;
    }

    public void setRoyalCandidateKills(int royalCandidateKills) {
        this.royalCandidateKills = Math.max(royalCandidateKills, 0);
    }

    public int incrementRoyalCandidateKills() {
        royalCandidateKills++;
        return royalCandidateKills;
    }

    @Override
    public void load(CompoundTag compoundTag) {
        if (compoundTag.contains(NBT_TICKS_UNTIL_BORED)) {
            this.ticksUntilBored = compoundTag.getInt(NBT_TICKS_UNTIL_BORED);
        }

        if (compoundTag.contains(NBT_LAST_LUNGE_TICK)) {
            this.lastLungeTick = compoundTag.getLong(NBT_LAST_LUNGE_TICK);
        }

        if (compoundTag.contains(NBT_ROYAL_LINE_CANDIDATE)) {
            this.royalLineCandidate = compoundTag.getBoolean(NBT_ROYAL_LINE_CANDIDATE);
        }

        if (compoundTag.contains(NBT_ROYAL_CANDIDATE_KILLS)) {
            this.royalCandidateKills = compoundTag.getInt(NBT_ROYAL_CANDIDATE_KILLS);
        }
    }

    @Override
    public void save(CompoundTag compoundTag) {
        compoundTag.putInt(NBT_TICKS_UNTIL_BORED, ticksUntilBored);
        compoundTag.putLong(NBT_LAST_LUNGE_TICK, lastLungeTick);
        compoundTag.putBoolean(NBT_ROYAL_LINE_CANDIDATE, royalLineCandidate);
        compoundTag.putInt(NBT_ROYAL_CANDIDATE_KILLS, royalCandidateKills);
    }
}
