package com.alien.common.network.payload;

import com.alien.Alien;
import com.alien.common.network.codec.CompoundTagStreamCodec;
import com.just.codec.stream.StreamCodec;
import com.just.codec.stream.schema.StreamCodecSchema;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Server → client: the complete set of facehugger head-attachment profiles, encoded by
 * {@link com.alien.common.registry.HeadAttachmentRegistry#encodeAll()}. Sent to each player as they load in and
 * re-broadcast to everyone after a datapack reload; consumed by
 * {@code AlienClientPacketListener#handleHeadAttachmentData}, which rebakes the client render cache wholesale. The set
 * is small (dozens of entries, a few doubles each), so full replacement beats delta tracking. Single-field payload, so
 * the codec delegates straight to {@link CompoundTagStreamCodec} rather than going through a record-style codec.
 */
public record S2CHeadAttachmentDataPayload(
    CompoundTag data
) implements CustomPacketPayload {

    public static final ResourceLocation PAYLOAD_ID = Alien.MOD.resources().createLocation("head_attachment_data");

    public static final Type<S2CHeadAttachmentDataPayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<S2CHeadAttachmentDataPayload> CODEC = new StreamCodec<>() {

        @Override
        public <T> void encode(
            @NotNull StreamCodecSchema<T> schema,
            @NotNull T input,
            @NotNull S2CHeadAttachmentDataPayload value
        ) {
            CompoundTagStreamCodec.INSTANCE.encode(schema, input, value.data());
        }

        @Override
        public @NotNull <T> S2CHeadAttachmentDataPayload decode(@NotNull StreamCodecSchema<T> schema, @NotNull T input) {
            return new S2CHeadAttachmentDataPayload(CompoundTagStreamCodec.INSTANCE.decode(schema, input));
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
