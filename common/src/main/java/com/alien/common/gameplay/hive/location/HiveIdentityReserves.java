package com.alien.common.gameplay.hive.location;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public final class HiveIdentityReserves {

    private final List<HiveIdentityReserveEntry> entries = new ArrayList<>();

    public boolean add(HiveIdentityReserveEntry entry) {
        entries.removeIf(existing -> existing.uuid().equals(entry.uuid()));
        return entries.add(entry);
    }

    public int getCount(EntityType<?> type) {
        return getCountMatching(type::equals);
    }

    public int getCountMatching(Predicate<EntityType<?>> predicate) {
        var count = 0;
        for (var entry : entries) {
            if (predicate.test(entry.type())) {
                count++;
            }
        }
        return count;
    }

    public int getCount() {
        return entries.size();
    }

    public List<EntityType<?>> getAvailableEntityTypes() {
        var types = new LinkedHashSet<EntityType<?>>();
        for (var entry : entries) {
            types.add(entry.type());
        }
        return List.copyOf(types);
    }

    public @Nullable HiveIdentityReserveEntry removeFirst(EntityType<?> type) {
        for (var i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (entry.type().equals(type)) {
                entries.remove(i);
                return entry;
            }
        }
        return null;
    }

    public boolean remove(UUID uuid) {
        return entries.removeIf(entry -> entry.uuid().equals(uuid));
    }

    public ListTag save() {
        var list = new ListTag();
        for (var entry : entries) {
            list.add(entry.save());
        }
        return list;
    }

    public int load(ListTag list, Predicate<EntityType<?>> accepts) {
        entries.clear();
        var rejected = 0;
        for (var i = 0; i < list.size(); i++) {
            var entry = HiveIdentityReserveEntry.load(list.getCompound(i));
            if (entry == null || !accepts.test(entry.type())) {
                rejected++;
                continue;
            }
            add(entry);
        }
        return rejected;
    }

    public int removeIf(Predicate<EntityType<?>> predicate) {
        var removed = 0;
        var iterator = entries.iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (predicate.test(entry.type())) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    public static ListTag listTag(CompoundTag tag, String key) {
        return tag.getList(key, Tag.TAG_COMPOUND);
    }
}
