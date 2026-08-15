package com.alien.common.network.payload;

import com.alien.Alien;
import com.just.codec.stream.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Client → server: request the current tracked-queen list for the tracking PDA. Fieldless — the server replies to the
 * requesting player with {@link S2CTrackedQueensPayload}.
 */
public record C2SRequestTrackedQueensPayload() implements CustomPacketPayload {

    public static final C2SRequestTrackedQueensPayload INSTANCE = new C2SRequestTrackedQueensPayload();

    public static final ResourceLocation PAYLOAD_ID = Alien.MOD.resources().createLocation("request_tracked_queens");

    public static final Type<C2SRequestTrackedQueensPayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<C2SRequestTrackedQueensPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
