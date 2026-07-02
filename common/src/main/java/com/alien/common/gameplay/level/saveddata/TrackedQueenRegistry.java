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
import java.util.Map;
import java.util.UUID;

/**
 * Server-wide registry of tracked queens for the tracking PDA. Keyed by queen UUID; each entry records her last-seen
 * position, dimension, and a display label, refreshed while she is loaded so the PDA can still point to her after she
 * unloads or wanders into other chunks. Stored on the overworld's data storage so a single table spans every dimension.
 * Server-side only.
 */
public class TrackedQueenRegistry extends SavedData {

    private static final String DATA_NAME = "avp_tracked_queens";

    private static final String NBT_ENTRIES = "entries";

    private static final String NBT_UUID = "uuid";

    private static final String NBT_X = "x";

    private static final String NBT_Y = "y";

    private static final String NBT_Z = "z";

    private static final String NBT_DIM = "dim";

    private static final String NBT_NAME = "name";

    private static final String NBT_SEEN = "lastSeen";

    /**
     * One tracked queen's last-known whereabouts. {@code lastSeenGameTime} is overworld game-time when last refreshed.
     */
    public record Entry(
        BlockPos pos,
        ResourceKey<Level> dimension,
        String name,
        long lastSeenGameTime
    ) {}

    private final Map<UUID, Entry> tracked;

    private TrackedQueenRegistry() {
        this(new LinkedHashMap<>());
    }

    private TrackedQueenRegistry(Map<UUID, Entry> tracked) {
        this.tracked = new LinkedHashMap<>(tracked);
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

        return new TrackedQueenRegistry(map);
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
