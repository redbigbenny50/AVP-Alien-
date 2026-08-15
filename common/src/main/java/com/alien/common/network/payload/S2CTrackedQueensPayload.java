package com.alien.common.network.payload;

import com.alien.Alien;
import com.alien.common.network.codec.CompoundTagStreamCodec;
import com.just.codec.stream.RecordStreamCodec;
import com.just.codec.stream.StreamCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Server → client: the tracked-queen list for the PDA, sent in reply to {@link C2SRequestTrackedQueensPayload}. The
 * {@code data} tag holds the row list packed by
 * {@link com.alien.common.gameplay.level.saveddata.TrackedQueenRow#packList}. Consumed by
 * {@code AlienClientPacketListener#handleTrackedQueens}.
 */
public record S2CTrackedQueensPayload(CompoundTag data) implements CustomPacketPayload {

    public static final ResourceLocation PAYLOAD_ID = Alien.MOD.resources().createLocation("tracked_queens");

    public static final Type<S2CTrackedQueensPayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<S2CTrackedQueensPayload> CODEC = RecordStreamCodec.of(
        CompoundTagStreamCodec.INSTANCE,
        S2CTrackedQueensPayload::data,
        S2CTrackedQueensPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
