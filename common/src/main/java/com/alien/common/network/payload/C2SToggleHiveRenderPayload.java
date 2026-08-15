package com.alien.common.network.payload;

import com.alien.Alien;
import com.just.codec.stream.RecordStreamCodec;
import com.just.codec.stream.StreamCodec;
import com.just.codec.stream.impl.StreamCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Client → server: toggle the hive render overlay for the sending player. When {@code enabled} is true the server
 * starts streaming {@link S2CHiveRenderDataPayload} for nearby hives to this player (and sends one immediately); when
 * false it stops. Handled by {@code HiveRenderToggleHandler}.
 * <p>
 * This is the network half of the {@code /avp_alien debug hive render} command: the command flips the client's desired
 * state and sends this payload so the server knows to start/stop dispatching.
 */
public record C2SToggleHiveRenderPayload(
    boolean enabled
) implements CustomPacketPayload {

    public static final ResourceLocation PAYLOAD_ID = Alien.MOD.resources().createLocation("toggle_hive_render");

    public static final Type<C2SToggleHiveRenderPayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<C2SToggleHiveRenderPayload> CODEC = RecordStreamCodec.of(
        // NOTE: if `StreamCodecs.BOOLEAN` does not resolve, the just-codec constant may be named differently
        // (e.g. StreamCodecs.BOOL). Fix this one token to match your just-codec version — it is the only
        // unverified reference in this payload.
        StreamCodecs.BOOLEAN,
        C2SToggleHiveRenderPayload::enabled,
        C2SToggleHiveRenderPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
