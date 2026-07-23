package com.alien.client.render.entity.head;

import com.alien.common.model.parasite.HeadAttachmentProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side view of the synced head-attachment profiles, pre-baked from the JSON's model-pixel units into blocks so
 * the renderer never converts per frame. Replaced wholesale by {@code AlienClientPacketListener} whenever an
 * {@code S2CHeadAttachmentDataPayload} arrives (player load and datapack reload). Lookup is by entity type registry id,
 * so third-party host types resolve exactly like vanilla ones.
 */
public final class HeadAttachmentClientCache {

    private static final double PIXELS_TO_BLOCKS = 1 / 16.0;

    private static volatile Map<ResourceLocation, BakedHeadAttachment> BAKED = Map.of();

    private HeadAttachmentClientCache() {}

    /**
     * All values in blocks. Null offsets mean "use the renderer's default formula"; {@code referenceHeight} was already
     * authored in blocks (it describes the host's hitbox, not the model) so it is carried through unconverted — null
     * means "no per-individual scaling".
     */
    public record BakedHeadAttachment(
        Vec3 size,
        Vec3 pivot,
        @Nullable Double verticalOffset,
        @Nullable Double faceOffset,
        @Nullable Double referenceHeight
    ) {}

    public static @Nullable BakedHeadAttachment get(EntityType<?> entityType) {
        return BAKED.get(BuiltInRegistries.ENTITY_TYPE.getKey(entityType));
    }

    public static void replaceAll(Map<ResourceLocation, HeadAttachmentProfile> profiles) {
        var baked = new HashMap<ResourceLocation, BakedHeadAttachment>();

        for (var entry : profiles.entrySet()) {
            var profile = entry.getValue();

            baked.put(
                entry.getKey(),
                new BakedHeadAttachment(
                    profile.size().scale(PIXELS_TO_BLOCKS),
                    profile.pivot().scale(PIXELS_TO_BLOCKS),
                    profile.verticalOffset().map(value -> value * PIXELS_TO_BLOCKS).orElse(null),
                    profile.faceOffset().map(value -> value * PIXELS_TO_BLOCKS).orElse(null),
                    profile.referenceHeight().orElse(null)
                )
            );
        }

        BAKED = Map.copyOf(baked);
    }
}
