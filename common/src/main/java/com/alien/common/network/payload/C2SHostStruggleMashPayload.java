package com.alien.common.network.payload;

import com.alien.Alien;
import com.just.codec.stream.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Client -> server: the player mashed SPACE or LEFT-CLICK while being carried off by a xenomorph.
 * <p>
 * Carries nothing: the server already knows who sent it and whether they are actually on a carrier's back, and the fill
 * rate is rate-limited server-side ({@code HostStruggle.MASH_COOLDOWN_TICKS}), so a spammed packet buys nothing.
 */
public record C2SHostStruggleMashPayload() implements CustomPacketPayload {

    public static final C2SHostStruggleMashPayload INSTANCE = new C2SHostStruggleMashPayload();

    public static final ResourceLocation PAYLOAD_ID = Alien.MOD.resources().createLocation("host_struggle_mash");

    public static final Type<C2SHostStruggleMashPayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<C2SHostStruggleMashPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
