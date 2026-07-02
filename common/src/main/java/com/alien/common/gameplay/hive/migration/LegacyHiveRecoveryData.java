package com.alien.common.gameplay.hive.migration;

import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.just.core.functional.option.Option;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LegacyHiveRecoveryData extends SavedData {

    private static final String DATA_NAME = "avp_alien_legacy_hive_recovery";

    private static final String NBT_LEGACY_DETECTED = "LegacyDetected";

    private static final String NBT_RECOVERY_APPLIED = "RecoveryApplied";

    private static final String NBT_MEMBER_LOCATIONS = "MemberLocations";

    private static final String NBT_LEGACY_QUEENS = "LegacyQueens";

    private static final String NBT_AWAKENED_LEGACY_QUEENS = "AwakenedLegacyQueens";

    private static final String NBT_MESSAGED_PLAYERS = "MessagedPlayers";

    private static final String NBT_WAKE_ALL_LEGACY_QUEENS = "WakeAllLegacyQueens";

    private static final String NBT_KILL_ALL_LEGACY_QUEENS = "KillAllLegacyQueens";

    private boolean legacyDetected;

    private boolean recoveryApplied;

    private boolean wakeAllLegacyQueens;

    private boolean killAllLegacyQueens;

    private final Map<UUID, HiveLocationId> memberLocations = new HashMap<>();

    private final Set<UUID> legacyQueens = new HashSet<>();

    private final Set<UUID> awakenedLegacyQueens = new HashSet<>();

    private final Set<UUID> messagedPlayers = new HashSet<>();

    private LegacyHiveRecoveryData() {}

    public boolean legacyDetected() {
        return legacyDetected;
    }

    public void setLegacyDetected(boolean legacyDetected) {
        if (this.legacyDetected == legacyDetected) {
            return;
        }
        this.legacyDetected = legacyDetected;
        setDirty();
    }

    public boolean recoveryApplied() {
        return recoveryApplied;
    }

    public void setRecoveryApplied(boolean recoveryApplied) {
        if (this.recoveryApplied == recoveryApplied) {
            return;
        }
        this.recoveryApplied = recoveryApplied;
        setDirty();
    }

    public void rememberMemberLocation(UUID memberId, HiveLocationId locationId) {
        memberLocations.put(memberId, locationId);
        setDirty();
    }

    public @Nullable HiveLocationId memberLocation(UUID memberId) {
        return memberLocations.get(memberId);
    }

    public int memberLocationCount() {
        return memberLocations.size();
    }

    public void rememberLegacyQueen(UUID queenId) {
        if (legacyQueens.add(queenId)) {
            setDirty();
        }
    }

    public boolean isLegacyQueen(UUID queenId) {
        return legacyQueens.contains(queenId);
    }

    public void rememberAwakenedLegacyQueen(UUID queenId) {
        rememberLegacyQueen(queenId);
        if (awakenedLegacyQueens.add(queenId)) {
            setDirty();
        }
    }

    public boolean isAwakenedLegacyQueen(UUID queenId) {
        return awakenedLegacyQueens.contains(queenId);
    }

    public boolean markPlayerMessaged(UUID playerId) {
        var added = messagedPlayers.add(playerId);
        if (added) {
            setDirty();
        }
        return added;
    }

    public boolean wakeAllLegacyQueens() {
        return wakeAllLegacyQueens;
    }

    public void setWakeAllLegacyQueens(boolean wakeAllLegacyQueens) {
        if (this.wakeAllLegacyQueens == wakeAllLegacyQueens) {
            return;
        }
        this.wakeAllLegacyQueens = wakeAllLegacyQueens;
        setDirty();
    }

    public boolean killAllLegacyQueens() {
        return killAllLegacyQueens;
    }

    public void setKillAllLegacyQueens(boolean killAllLegacyQueens) {
        if (this.killAllLegacyQueens == killAllLegacyQueens) {
            return;
        }
        this.killAllLegacyQueens = killAllLegacyQueens;
        setDirty();
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        tag.putBoolean(NBT_LEGACY_DETECTED, legacyDetected);
        tag.putBoolean(NBT_RECOVERY_APPLIED, recoveryApplied);
        tag.putBoolean(NBT_WAKE_ALL_LEGACY_QUEENS, wakeAllLegacyQueens);
        tag.putBoolean(NBT_KILL_ALL_LEGACY_QUEENS, killAllLegacyQueens);

        var memberList = new ListTag();
        for (var entry : memberLocations.entrySet()) {
            var row = new CompoundTag();
            row.putUUID("MemberId", entry.getKey());
            row.putString("LocationId", entry.getValue().value().toString());
            memberList.add(row);
        }
        tag.put(NBT_MEMBER_LOCATIONS, memberList);

        var queenList = new ListTag();
        for (var queenId : legacyQueens) {
            var row = new CompoundTag();
            row.putUUID("QueenId", queenId);
            queenList.add(row);
        }
        tag.put(NBT_LEGACY_QUEENS, queenList);

        var awakenedQueenList = new ListTag();
        for (var queenId : awakenedLegacyQueens) {
            var row = new CompoundTag();
            row.putUUID("QueenId", queenId);
            awakenedQueenList.add(row);
        }
        tag.put(NBT_AWAKENED_LEGACY_QUEENS, awakenedQueenList);

        var playerList = new ListTag();
        for (var playerId : messagedPlayers) {
            var row = new CompoundTag();
            row.putUUID("PlayerId", playerId);
            playerList.add(row);
        }
        tag.put(NBT_MESSAGED_PLAYERS, playerList);

        return tag;
    }

    public static LegacyHiveRecoveryData load(CompoundTag tag, HolderLookup.Provider provider) {
        var data = new LegacyHiveRecoveryData();
        data.legacyDetected = tag.getBoolean(NBT_LEGACY_DETECTED);
        data.recoveryApplied = tag.getBoolean(NBT_RECOVERY_APPLIED);
        data.wakeAllLegacyQueens = tag.getBoolean(NBT_WAKE_ALL_LEGACY_QUEENS);
        data.killAllLegacyQueens = tag.getBoolean(NBT_KILL_ALL_LEGACY_QUEENS);

        if (tag.contains(NBT_MEMBER_LOCATIONS, Tag.TAG_LIST)) {
            var list = tag.getList(NBT_MEMBER_LOCATIONS, Tag.TAG_COMPOUND);
            for (var i = 0; i < list.size(); i++) {
                var row = list.getCompound(i);
                if (!row.hasUUID("MemberId") || !row.contains("LocationId")) {
                    continue;
                }
                data.memberLocations.put(
                    row.getUUID("MemberId"),
                    HiveLocationId.of(ResourceLocation.parse(row.getString("LocationId")))
                );
            }
        }

        if (tag.contains(NBT_LEGACY_QUEENS, Tag.TAG_LIST)) {
            var list = tag.getList(NBT_LEGACY_QUEENS, Tag.TAG_COMPOUND);
            for (var i = 0; i < list.size(); i++) {
                var row = list.getCompound(i);
                if (row.hasUUID("QueenId")) {
                    data.legacyQueens.add(row.getUUID("QueenId"));
                }
            }
        }

        if (tag.contains(NBT_AWAKENED_LEGACY_QUEENS, Tag.TAG_LIST)) {
            var list = tag.getList(NBT_AWAKENED_LEGACY_QUEENS, Tag.TAG_COMPOUND);
            for (var i = 0; i < list.size(); i++) {
                var row = list.getCompound(i);
                if (row.hasUUID("QueenId")) {
                    var queenId = row.getUUID("QueenId");
                    data.legacyQueens.add(queenId);
                    data.awakenedLegacyQueens.add(queenId);
                }
            }
        }

        if (tag.contains(NBT_MESSAGED_PLAYERS, Tag.TAG_LIST)) {
            var list = tag.getList(NBT_MESSAGED_PLAYERS, Tag.TAG_COMPOUND);
            for (var i = 0; i < list.size(); i++) {
                var row = list.getCompound(i);
                if (row.hasUUID("PlayerId")) {
                    data.messagedPlayers.add(row.getUUID("PlayerId"));
                }
            }
        }

        return data;
    }

    public static Option<LegacyHiveRecoveryData> getOrCreate(MinecraftServer server) {
        var level = server.overworld();
        return Option.some(
            level.getDataStorage()
                .computeIfAbsent(
                    new Factory<>(LegacyHiveRecoveryData::new, LegacyHiveRecoveryData::load, null),
                    DATA_NAME
                )
        );
    }
}
