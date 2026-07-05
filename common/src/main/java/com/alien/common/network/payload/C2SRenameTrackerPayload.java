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
 * Client → server: rename a tracked queen's label from inside the PDA. The queen UUID and new name ride in the
 * {@code data} tag under {@code "id"} and {@code "name"}. Handled by {@code RenameTrackerHandler}.
 */
public record C2SRenameTrackerPayload(CompoundTag data) implements CustomPacketPayload {

    private static final String NBT_ID = "id";

    private static final String NBT_NAME = "name";

    public static final ResourceLocation PAYLOAD_ID = Alien.MOD.resources().createLocation("rename_tracker");

    public static final Type<C2SRenameTrackerPayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<C2SRenameTrackerPayload> CODEC = RecordStreamCodec.of(
        CompoundTagStreamCodec.INSTANCE,
        C2SRenameTrackerPayload::data,
        C2SRenameTrackerPayload::new
    );

    public static C2SRenameTrackerPayload of(UUID queenId, String name) {
        var tag = new CompoundTag();
        tag.putUUID(NBT_ID, queenId);
        tag.putString(NBT_NAME, name);
        return new C2SRenameTrackerPayload(tag);
    }

    public UUID queenId() {
        return data.getUUID(NBT_ID);
    }

    public String newName() {
        return data.getString(NBT_NAME);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
