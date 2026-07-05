package com.alien.common.network.payload;

import com.alien.Alien;
import com.alien.common.network.codec.CompoundTagStreamCodec;
import com.just.codec.stream.RecordStreamCodec;
import com.just.codec.stream.StreamCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Client → server: destroy the tracker attached to a specific queen. Clears her tracker tag (if she is loaded, and on
 * her next tick otherwise) and removes her from the tracked-queen registry. This is a clean removal — unlike death or
 * empress interference, it does <em>not</em> produce a "tracker lost" warning. Handled by
 * {@code DestroyTrackerHandler}. The queen UUID rides in the {@code data} tag under {@code "id"}.
 */
public record C2SDestroyTrackerPayload(CompoundTag data) implements CustomPacketPayload {

    private static final String NBT_ID = "id";

    public static final ResourceLocation PAYLOAD_ID = Alien.MOD.resources().createLocation("destroy_tracker");

    public static final Type<C2SDestroyTrackerPayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<C2SDestroyTrackerPayload> CODEC = RecordStreamCodec.of(
        CompoundTagStreamCodec.INSTANCE,
        C2SDestroyTrackerPayload::data,
        C2SDestroyTrackerPayload::new
    );

    /** Build a payload targeting a single queen by UUID. */
    public static C2SDestroyTrackerPayload of(UUID queenId) {
        var tag = new CompoundTag();
        tag.putUUID(NBT_ID, queenId);
        return new C2SDestroyTrackerPayload(tag);
    }

    /** The queen whose tracker should be destroyed. */
    public UUID queenId() {
        return data.getUUID(NBT_ID);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
