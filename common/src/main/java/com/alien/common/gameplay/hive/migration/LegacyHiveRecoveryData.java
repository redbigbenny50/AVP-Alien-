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

    private static final String NBT_LEGACY_PURGED = "LegacyPurged";

    private boolean legacyDetected;

    /** See {@link #legacyPurged()}. */
    private boolean legacyPurged;

    private boolean recoveryApplied;

    private boolean wakeAllLegacyQueens;

    private boolean killAllLegacyQueens;

    private final Map<UUID, HiveLocationId> memberLocations = new HashMap<>();

    private final Set<UUID> legacyQueens = new HashSet<>();

    private final Set<UUID> awakenedLegacyQueens = new HashSet<>();

    private final Set<UUID> messagedPlayers = new HashSet<>();

    private LegacyHiveRecoveryData() {}

    /**
     * ⭐⭐⭐ THE PLAYER HAS DELIBERATELY WIPED THIS WORLD'S HIVES. NEVER RECOVER LEGACY DATA AGAIN.
     * <p>
     * ⚠⚠ WITHOUT THIS, A WIPE CANNOT STICK. {@code detectAndRecover} calls {@code setLegacyDetected(true)}
     * UNCONDITIONALLY at the top of every world load whenever the old {@code hive_data.dat} is still on disk - before
     * the recoveryApplied gate is reached. So clearing the flag during a wipe was undone by the very next load, and
     * eighteen dormant queens were re-minted to reserve ground on a world the player had just emptied. He wiped,
     * restarted, and got "old echoes" again with nothing left to own it.
     * </p>
     * <p>
     * ⚠ PERSISTED, and deliberately one-way. The save file is not deleted - it stays untouched on disk - so this flag
     * is the only thing standing between an emptied world and a full re-import.
     * </p>
     */
    public boolean legacyPurged() {
        return legacyPurged;
    }

    public void setLegacyPurged(boolean legacyPurged) {
        this.legacyPurged = legacyPurged;
        setDirty();
    }

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

    /**
     * Drops every legacy record for an id, because the thing wearing it is no longer legacy.
     * <h2>⚠⚠ WHY THIS IS NEEDED AT ALL: A MOLT KEEPS THE UUID</h2> [stated] Sep 28, on a praetorian promoted to queen:
     * "the royal cocoon vanishes which should be signs she changed but she stands in place and doesnt move ... shes in
     * the molt pose loop again with no cocoon", and "something about her being classified as legacy even though shes a
     * new queen made after the legacy cutoff".
     * <p>
     * ⭐ {@code CocoonManager} deliberately carries the old body's UUID onto the new one - the forced-id transition
     * exists so the hive remembers who she was. But the legacy tables here are keyed by UUID, so a praetorian whose id
     * was ever recorded as legacy hands that record to the QUEEN she becomes: she matches {@link #isLegacyQueen}, gets
     * parked legacy-dormant, and legacy-dormant means STANDS STILL AND DOES NOTHING. The cocoon is gone because the
     * molt genuinely finished; nothing then advances her, so a relog drops her back into the molt pose.
     * </p>
     * <p>
     * ⚠ A queen that has just emerged from a cocoon was made under the current lifecycle by definition. Any legacy
     * record for her id is stale the moment she is created, whatever it meant for the body she grew out of.
     * </p>
     */
    public void forgetLegacyQueen(UUID queenId) {
        var changed = legacyQueens.remove(queenId);

        changed |= awakenedLegacyQueens.remove(queenId);

        if (changed) {
            setDirty();
        }
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
        tag.putBoolean(NBT_LEGACY_PURGED, legacyPurged);

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
        data.legacyPurged = tag.getBoolean(NBT_LEGACY_PURGED);

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
