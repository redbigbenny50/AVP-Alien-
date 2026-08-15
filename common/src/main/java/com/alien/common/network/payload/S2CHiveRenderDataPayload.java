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
 * Server → client: hive render data for the {@code /avp_alien debug hive render} wireframe overlay.
 * <p>
 * Carries, for every hive location near the receiving player, the data the client needs to draw the three-color
 * wireframe: the slab floor/ceiling Y, and the set of claimed chunk positions. Packed into a single {@link CompoundTag}
 * (see {@code HiveRenderData}) to avoid bespoke stream codecs for nested lists — mirrors how
 * {@link S2CHiveInspectionPayload} ships its snapshot as an opaque tag.
 * <p>
 * Sent on a slow timer (and once on toggle) by {@code HiveRenderDataDispatcher} while the player has the overlay
 * enabled; consumed by {@code ClientHiveRenderCache}.
 */
public record S2CHiveRenderDataPayload(
    CompoundTag data
) implements CustomPacketPayload {

    public static final ResourceLocation PAYLOAD_ID = Alien.MOD.resources().createLocation("hive_render_data");

    public static final Type<S2CHiveRenderDataPayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<S2CHiveRenderDataPayload> CODEC = RecordStreamCodec.of(
        CompoundTagStreamCodec.INSTANCE,
        S2CHiveRenderDataPayload::data,
        S2CHiveRenderDataPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
