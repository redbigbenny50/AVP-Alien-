package com.alien.compatibility.blib_engine.client.inspector;

import com.alien.common.network.payload.S2CHiveInspectionPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side cache for the most-recent {@link S2CHiveInspectionPayload}. The hive inspector sections request a
 * snapshot when the selection swaps to an AVP faction and render against whatever is in this cache; "(loading…)" shows
 * while the reply is in flight.
 * <p>
 * Holds at most one snapshot — selection swaps clear-and-replace. Stale-reply guard: callers compare
 * {@link #current()}'s {@code factionId} against the active selection's faction id before rendering, so a delayed reply
 * for the previous selection doesn't paint over a fresh one.
 */
public final class ClientHiveInspectionCache {

    public record HiveInspection(
        String kind,
        ResourceLocation factionId,
        CompoundTag data
    ) {}

    private static @Nullable HiveInspection current;

    private ClientHiveInspectionCache() {}

    public static @Nullable HiveInspection current() {
        return current;
    }

    public static void apply(S2CHiveInspectionPayload payload) {
        current = new HiveInspection(payload.kind(), payload.factionId(), payload.data());
    }

    public static void clear() {
        current = null;
    }
}
