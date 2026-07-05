package com.alien.common.network.payload;

import com.alien.Alien;
import com.just.codec.stream.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Client → server: acknowledge and clear the "lost trackers" list after the player has reviewed it in the PDA's hazard
 * drawer. Fieldless. Handled by {@code AckLostTrackersHandler}.
 */
public record C2SAckLostTrackersPayload() implements CustomPacketPayload {

    public static final C2SAckLostTrackersPayload INSTANCE = new C2SAckLostTrackersPayload();

    public static final ResourceLocation PAYLOAD_ID = Alien.MOD.resources().createLocation("ack_lost_trackers");

    public static final Type<C2SAckLostTrackersPayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<C2SAckLostTrackersPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
