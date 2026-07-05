package com.alien.common.gameplay.level.saveddata;

import com.just.core.functional.option.Option;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-wide registry of tracked queens for the tracking PDA. Keyed by queen UUID; each entry records her last-seen
 * position, dimension, and a display label, refreshed while she is loaded so the PDA can still point to her after she
 * unloads or wanders into other chunks. Stored on the overworld's data storage so a single table spans every dimension.
 * Server-side only.
 */
public class TrackedQueenRegistry extends SavedData {

    private static final String DATA_NAME = "avp_tracked_queens";

    public static final String REASON_DECEASED = "DECEASED";

    public static final String REASON_EMPRESS = "EMPRESS";

    private static final String NBT_ENTRIES = "entries";

    private static final String NBT_UUID = "uuid";

    private static final String NBT_X = "x";

    private static final String NBT_Y = "y";

    private static final String NBT_Z = "z";

    private static final String NBT_DIM = "dim";

    private static final String NBT_NAME = "name";

    private static final String NBT_SEEN = "lastSeen";

    private static final String NBT_PENDING = "pendingDestroy";

    private static final String NBT_LOST = "lost";

    private static final String NBT_REASON = "reason";

    /**
     * One tracked queen's last-known whereabouts. {@code lastSeenGameTime} is overworld game-time when last refreshed.
     */
    public record Entry(
        BlockPos pos,
        ResourceKey<Level> dimension,
        String name,
        long lastSeenGameTime
    ) {}

    /** A tracker that went dark (queen death or empress interference), retained until the player acknowledges it. */
    public record LostEntry(
        BlockPos pos,
        ResourceKey<Level> dimension,
        String name,
        String reason,
        long lostGameTime
    ) {}

    private final Map<UUID, Entry> tracked;

    private final Set<UUID> pendingDestroy;

    private final Map<UUID, LostEntry> lost;

    private TrackedQueenRegistry() {
        this(new LinkedHashMap<>(), new LinkedHashSet<>(), new LinkedHashMap<>());
    }

    private TrackedQueenRegistry(Map<UUID, Entry> tracked, Set<UUID> pendingDestroy, Map<UUID, LostEntry> lost) {
        this.tracked = new LinkedHashMap<>(tracked);
        this.pendingDestroy = new LinkedHashSet<>(pendingDestroy);
        this.lost = new LinkedHashMap<>(lost);
    }

    /** Add or replace a tracked queen (called when the tracker tag is applied). */
    public void track(UUID id, BlockPos pos, ResourceKey<Level> dimension, String name, long gameTime) {
        tracked.put(id, new Entry(pos, dimension, name, gameTime));
        setDirty();
    }

    /** Refresh a tracked queen's position while she's loaded, preserving her stored label. */
    public void updatePosition(UUID id, BlockPos pos, ResourceKey<Level> dimension, long gameTime) {
        var existing = tracked.get(id);
        var name = existing != null ? existing.name() : "Queen";
        tracked.put(id, new Entry(pos, dimension, name, gameTime));
        setDirty();
    }

    /** Remove a tracked queen (death, or the tag being cleared). */
    public void untrack(UUID id) {
        if (tracked.remove(id) != null) {
            setDirty();
        }
    }

    public boolean isTracked(UUID id) {
        return tracked.containsKey(id);
    }

    /** Change the readable label of a tracked queen (used by the PDA's on-the-fly rename). */
    public void rename(UUID id, String name) {
        var entry = tracked.get(id);
        if (entry != null) {
            tracked.put(id, new Entry(entry.pos(), entry.dimension(), name, entry.lastSeenGameTime()));
            setDirty();
        }
    }

    /**
     * Queue a queen's tag to be cleared on her next tick. Used by "destroy tracker" so an unloaded queen still has her
     * tag removed on reload instead of re-registering herself.
     */
    public void markPendingDestroy(UUID id) {
        if (pendingDestroy.add(id)) {
            setDirty();
        }
    }

    /** If a tag-clear is queued for this queen, consume it and return {@code true}. */
    public boolean consumePendingDestroy(UUID id) {
        if (pendingDestroy.remove(id)) {
            setDirty();
            return true;
        }
        return false;
    }

    /**
     * Move a tracked queen into the "lost" list with a reason (death, empress interference). Returns {@code true} if
     * she was actually being tracked; callers use that to decide whether to raise a "tracker gone dark" alert.
     */
    public boolean markLost(UUID id, String reason, long gameTime) {
        var entry = tracked.remove(id);
        if (entry == null) {
            return false;
        }
        lost.put(id, new LostEntry(entry.pos(), entry.dimension(), entry.name(), reason, gameTime));
        setDirty();
        return true;
    }

    /** Live view of trackers that have gone dark, insertion-ordered. */
    public Map<UUID, LostEntry> lost() {
        return lost;
    }

    /** Acknowledge and drop a single lost tracker. */
    public void acknowledgeLost(UUID id) {
        if (lost.remove(id) != null) {
            setDirty();
        }
    }

    /** Acknowledge and clear every lost tracker. */
    public void clearLost() {
        if (!lost.isEmpty()) {
            lost.clear();
            setDirty();
        }
    }

    /**
     * Mark a queen's tracker as lost with the given reason (see the {@code REASON_*} constants) and, when she was
     * actually tracked, announce it. Use wherever a tracked queen leaves play by means other than death -- evolving
     * into an empress, or being absorbed by one -- so her tracker surfaces in the PDA's lost-communications list.
     */
    public static void markLostAndAnnounce(ServerLevel level, UUID id, String reason) {
        getOrCreate(level).ifSome(registry -> {
            if (registry.markLost(id, reason, level.getGameTime())) {
                level.getServer()
                    .getPlayerList()
                    .broadcastSystemMessage(
                        net.minecraft.network.chat.Component.literal("One of your trackers has gone dark"),
                        false
                    );
            }
        });
    }

    /** Live view of all tracked queens, insertion-ordered. */
    public Map<UUID, Entry> entries() {
        return tracked;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag compoundTag, @NotNull HolderLookup.Provider provider) {
        var list = new ListTag();

        for (var e : tracked.entrySet()) {
            var entry = e.getValue();
            var t = new CompoundTag();
            t.putUUID(NBT_UUID, e.getKey());
            t.putInt(NBT_X, entry.pos().getX());
            t.putInt(NBT_Y, entry.pos().getY());
            t.putInt(NBT_Z, entry.pos().getZ());
            t.putString(NBT_DIM, entry.dimension().location().toString());
            t.putString(NBT_NAME, entry.name());
            t.putLong(NBT_SEEN, entry.lastSeenGameTime());
            list.add(t);
        }

        compoundTag.put(NBT_ENTRIES, list);

        var pending = new ListTag();
        for (var id : pendingDestroy) {
            var t = new CompoundTag();
            t.putUUID(NBT_UUID, id);
            pending.add(t);
        }
        compoundTag.put(NBT_PENDING, pending);

        var lostList = new ListTag();
        for (var e : lost.entrySet()) {
            var entry = e.getValue();
            var t = new CompoundTag();
            t.putUUID(NBT_UUID, e.getKey());
            t.putInt(NBT_X, entry.pos().getX());
            t.putInt(NBT_Y, entry.pos().getY());
            t.putInt(NBT_Z, entry.pos().getZ());
            t.putString(NBT_DIM, entry.dimension().location().toString());
            t.putString(NBT_NAME, entry.name());
            t.putString(NBT_REASON, entry.reason());
            t.putLong(NBT_SEEN, entry.lostGameTime());
            lostList.add(t);
        }
        compoundTag.put(NBT_LOST, lostList);

        return compoundTag;
    }

    public static TrackedQueenRegistry load(CompoundTag compoundTag, HolderLookup.Provider provider) {
        var map = new LinkedHashMap<UUID, Entry>();

        var list = compoundTag.getList(NBT_ENTRIES, Tag.TAG_COMPOUND);

        for (var i = 0; i < list.size(); i++) {
            var t = list.getCompound(i);
            var id = t.getUUID(NBT_UUID);
            var pos = new BlockPos(t.getInt(NBT_X), t.getInt(NBT_Y), t.getInt(NBT_Z));
            var dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString(NBT_DIM)));
            var name = t.getString(NBT_NAME);
            var seen = t.getLong(NBT_SEEN);

            map.put(id, new Entry(pos, dimension, name, seen));
        }

        var pending = new LinkedHashSet<UUID>();
        var pendingList = compoundTag.getList(NBT_PENDING, Tag.TAG_COMPOUND);
        for (var i = 0; i < pendingList.size(); i++) {
            pending.add(pendingList.getCompound(i).getUUID(NBT_UUID));
        }

        var lostMap = new LinkedHashMap<UUID, LostEntry>();
        var lostList = compoundTag.getList(NBT_LOST, Tag.TAG_COMPOUND);
        for (var i = 0; i < lostList.size(); i++) {
            var t = lostList.getCompound(i);
            var id = t.getUUID(NBT_UUID);
            var pos = new BlockPos(t.getInt(NBT_X), t.getInt(NBT_Y), t.getInt(NBT_Z));
            var dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString(NBT_DIM)));
            var name = t.getString(NBT_NAME);
            var reason = t.getString(NBT_REASON);
            var seen = t.getLong(NBT_SEEN);
            lostMap.put(id, new LostEntry(pos, dimension, name, reason, seen));
        }

        return new TrackedQueenRegistry(map, pending, lostMap);
    }

    /** The single server-wide registry, stored on the overworld so it spans dimensions. Empty on the client. */
    public static Option<TrackedQueenRegistry> getOrCreate(MinecraftServer server) {
        if (server == null) {
            return Option.none();
        }

        return Option.some(
            server.overworld()
                .getDataStorage()
                .computeIfAbsent(new Factory<>(TrackedQueenRegistry::new, TrackedQueenRegistry::load, null), DATA_NAME)
        );
    }

    /** Convenience: resolve the registry from any server level. Empty on the client. */
    public static Option<TrackedQueenRegistry> getOrCreate(Level level) {
        return level.isClientSide ? Option.none() : getOrCreate(((ServerLevel) level).getServer());
    }
}
