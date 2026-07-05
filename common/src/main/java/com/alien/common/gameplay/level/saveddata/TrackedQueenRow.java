package com.alien.common.gameplay.level.saveddata;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A single tracked-queen row shared between the server (built from {@link TrackedQueenRegistry}) and the client PDA.
 * The {@link #packList} / {@link #unpackList} helpers move a whole list through the {@link CompoundTag} carried by
 * {@code S2CTrackedQueensPayload}.
 */
public record TrackedQueenRow(
    UUID id,
    String name,
    ResourceKey<Level> dimension,
    BlockPos pos,
    long lastSeenGameTime
) {

    private static final String NBT_ROWS = "rows";

    private static final String NBT_UUID = "uuid";

    private static final String NBT_NAME = "name";

    private static final String NBT_DIM = "dim";

    private static final String NBT_X = "x";

    private static final String NBT_Y = "y";

    private static final String NBT_Z = "z";

    private static final String NBT_SEEN = "lastSeen";

    private static final String NBT_LOST = "lost";

    private static final String NBT_REASON = "reason";

    public static CompoundTag packList(List<TrackedQueenRow> rows) {
        var list = new ListTag();

        for (var row : rows) {
            var t = new CompoundTag();
            t.putUUID(NBT_UUID, row.id());
            t.putString(NBT_NAME, row.name());
            t.putString(NBT_DIM, row.dimension().location().toString());
            t.putInt(NBT_X, row.pos().getX());
            t.putInt(NBT_Y, row.pos().getY());
            t.putInt(NBT_Z, row.pos().getZ());
            t.putLong(NBT_SEEN, row.lastSeenGameTime());
            list.add(t);
        }

        var tag = new CompoundTag();
        tag.put(NBT_ROWS, list);
        return tag;
    }

    public static List<TrackedQueenRow> unpackList(CompoundTag tag) {
        var rows = new ArrayList<TrackedQueenRow>();
        var list = tag.getList(NBT_ROWS, Tag.TAG_COMPOUND);

        for (var i = 0; i < list.size(); i++) {
            var t = list.getCompound(i);
            var id = t.getUUID(NBT_UUID);
            var name = t.getString(NBT_NAME);
            var dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString(NBT_DIM)));
            var pos = new BlockPos(t.getInt(NBT_X), t.getInt(NBT_Y), t.getInt(NBT_Z));
            var seen = t.getLong(NBT_SEEN);

            rows.add(new TrackedQueenRow(id, name, dimension, pos, seen));
        }

        return rows;
    }

    /** A tracker that has gone dark, with the reason it was lost. */
    public record Lost(
        UUID id,
        String name,
        ResourceKey<Level> dimension,
        BlockPos pos,
        String reason,
        long lostGameTime
    ) {}

    /** Add a "lost" list to an existing packed tag (alongside the active {@code rows}). */
    public static void packLostInto(CompoundTag tag, List<Lost> lost) {
        var list = new ListTag();
        for (var l : lost) {
            var t = new CompoundTag();
            t.putUUID(NBT_UUID, l.id());
            t.putString(NBT_NAME, l.name());
            t.putString(NBT_DIM, l.dimension().location().toString());
            t.putInt(NBT_X, l.pos().getX());
            t.putInt(NBT_Y, l.pos().getY());
            t.putInt(NBT_Z, l.pos().getZ());
            t.putString(NBT_REASON, l.reason());
            t.putLong(NBT_SEEN, l.lostGameTime());
            list.add(t);
        }
        tag.put(NBT_LOST, list);
    }

    public static List<Lost> unpackLost(CompoundTag tag) {
        var out = new ArrayList<Lost>();
        var list = tag.getList(NBT_LOST, Tag.TAG_COMPOUND);
        for (var i = 0; i < list.size(); i++) {
            var t = list.getCompound(i);
            var id = t.getUUID(NBT_UUID);
            var name = t.getString(NBT_NAME);
            var dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString(NBT_DIM)));
            var pos = new BlockPos(t.getInt(NBT_X), t.getInt(NBT_Y), t.getInt(NBT_Z));
            var reason = t.getString(NBT_REASON);
            var seen = t.getLong(NBT_SEEN);
            out.add(new Lost(id, name, dimension, pos, reason, seen));
        }
        return out;
    }
}
