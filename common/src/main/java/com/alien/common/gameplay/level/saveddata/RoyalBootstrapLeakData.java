package com.alien.common.gameplay.level.saveddata;

import com.alien.common.model.alien.variant.AlienVariant;
import com.just.core.functional.option.Option;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RoyalBootstrapLeakData extends SavedData {

    private static final String DATA_NAME = "royal_bootstrap_leak_data";

    private static final String NBT_PENDING_VARIANTS = "PendingVariants";

    private final Map<AlienVariant, Integer> pendingCounts = new EnumMap<>(AlienVariant.class);

    public void add(AlienVariant variant) {
        pendingCounts.merge(variant, 1, Integer::sum);
        setDirty();
    }

    public boolean consumeOne(AlienVariant variant) {
        var current = pendingCounts.getOrDefault(variant, 0);
        if (current <= 0) {
            return false;
        }

        if (current == 1) {
            pendingCounts.remove(variant);
        } else {
            pendingCounts.put(variant, current - 1);
        }

        setDirty();
        return true;
    }

    public int getCount(AlienVariant variant) {
        return pendingCounts.getOrDefault(variant, 0);
    }

    public Set<AlienVariant> getPendingVariants() {
        return pendingCounts.entrySet()
            .stream()
            .filter(entry -> entry.getValue() > 0)
            .map(Map.Entry::getKey)
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        var variantsTag = new CompoundTag();

        for (var entry : pendingCounts.entrySet()) {
            if (entry.getValue() > 0) {
                variantsTag.putInt(Integer.toString(entry.getKey().getId()), entry.getValue());
            }
        }

        tag.put(NBT_PENDING_VARIANTS, variantsTag);
        return tag;
    }

    private static RoyalBootstrapLeakData load(CompoundTag tag, HolderLookup.Provider provider) {
        var data = new RoyalBootstrapLeakData();

        if (tag.contains(NBT_PENDING_VARIANTS, Tag.TAG_COMPOUND)) {
            var variantsTag = tag.getCompound(NBT_PENDING_VARIANTS);

            for (var key : variantsTag.getAllKeys()) {
                try {
                    var variantId = Integer.parseInt(key);
                    var count = variantsTag.getInt(key);
                    AlienVariant.getById(variantId).ifSome(variant -> {
                        if (count > 0) {
                            data.pendingCounts.put(variant, count);
                        }
                    });
                } catch (NumberFormatException ignored) {
                    // Ignore malformed variant ids.
                }
            }
        }

        return data;
    }

    public static Option<RoyalBootstrapLeakData> getOrCreate(Level level) {
        return level.isClientSide
            ? Option.none()
            : Option.some(
                ((ServerLevel) level).getDataStorage()
                    .computeIfAbsent(
                        new Factory<>(RoyalBootstrapLeakData::new, RoyalBootstrapLeakData::load, null),
                        DATA_NAME
                    )
            );
    }
}
