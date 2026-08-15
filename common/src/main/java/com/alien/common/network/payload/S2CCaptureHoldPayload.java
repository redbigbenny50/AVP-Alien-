package com.alien.common.network.payload;

import com.alien.Alien;
import com.just.codec.stream.RecordStreamCodec;
import com.just.codec.stream.StreamCodec;
import com.just.codec.stream.impl.StreamCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Server -> client: a mob has been grabbed or released by the capture chain.
 * <p>
 * Carries the mob's network entity id and the holder's network entity id; a {@code holderId} of {@code -1} means the
 * hold ended (release). Sent to every player near the mob on grab and on release by {@code CaptureHoldManager}, and
 * consumed by {@code CaptureHoldClientState}, which the draw-only render inject reads to draw the chain. This replaces
 * vanilla leashing entirely, so no rope is ever drawn through the vanilla pipeline and nothing drops a lead item.
 */
public record S2CCaptureHoldPayload(
    int mobId,
    int holderId
) implements CustomPacketPayload {

    public static final ResourceLocation PAYLOAD_ID = Alien.MOD.resources().createLocation("capture_hold");

    public static final Type<S2CCaptureHoldPayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<S2CCaptureHoldPayload> CODEC = RecordStreamCodec.of(
        StreamCodecs.INT,
        S2CCaptureHoldPayload::mobId,
        StreamCodecs.INT,
        S2CCaptureHoldPayload::holderId,
        S2CCaptureHoldPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
