package com.alien.common.registry;

import com.alien.Alien;
import com.alien.common.model.parasite.HeadAttachmentProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-side store of every {@link HeadAttachmentProfile} loaded from datapacks, keyed by the host entity type id.
 * Cleared and repopulated by {@code HeadAttachmentReloadListener} on every datapack (re)load; serialized whole into a
 * single {@link CompoundTag} for the join/reload sync payload. The client never reads this class' map directly — it
 * decodes the payload into its own render-side cache.
 */
public class HeadAttachmentRegistry {

    private static final Map<ResourceLocation, HeadAttachmentProfile> PROFILES = new HashMap<>();

    public static void clear() {
        PROFILES.clear();
    }

    public static void register(HeadAttachmentProfile profile) {
        PROFILES.put(profile.entity(), profile);
    }

    public static int size() {
        return PROFILES.size();
    }

    /** Encode every registered profile into one tag: {@code { "<entityId>": <encoded profile>, ... } }. */
    public static CompoundTag encodeAll() {
        var root = new CompoundTag();

        for (var entry : PROFILES.entrySet()) {
            HeadAttachmentProfile.CODEC.encodeStart(NbtOps.INSTANCE, entry.getValue())
                .resultOrPartial(
                    err -> Alien.LOGGER.error("Failed to encode head attachment profile {}: {}", entry.getKey(), err)
                )
                .ifPresent(tag -> root.put(entry.getKey().toString(), tag));
        }

        return root;
    }

    /** Inverse of {@link #encodeAll()}; used by the client payload handler. */
    public static Map<ResourceLocation, HeadAttachmentProfile> decodeAll(CompoundTag root) {
        var map = new HashMap<ResourceLocation, HeadAttachmentProfile>();

        for (var key : root.getAllKeys()) {
            HeadAttachmentProfile.CODEC.parse(NbtOps.INSTANCE, root.get(key))
                .resultOrPartial(err -> Alien.LOGGER.error("Failed to decode head attachment profile {}: {}", key, err))
                .ifPresent(profile -> map.put(profile.entity(), profile));
        }

        return map;
    }
}
