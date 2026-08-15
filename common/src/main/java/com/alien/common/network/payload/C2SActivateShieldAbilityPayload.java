package com.alien.common.network.payload;

import com.alien.Alien;
import com.just.codec.stream.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record C2SActivateShieldAbilityPayload() implements CustomPacketPayload {

    public static final C2SActivateShieldAbilityPayload INSTANCE = new C2SActivateShieldAbilityPayload();

    public static final ResourceLocation PAYLOAD_ID = Alien.MOD.resources().createLocation("activate_shield_ability");

    public static final Type<C2SActivateShieldAbilityPayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<C2SActivateShieldAbilityPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
