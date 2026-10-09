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
 * ⭐⭐ Server → client: the whole live hive config, so the client can draw a screen for it.
 * <p>
 * ⚠⚠ THE CONFIG LIVES ON THE SERVER AND ONLY ON THE SERVER. On a dedicated server the client has never seen a single
 * one of these values — it does not read the JSON file and has no registry of its own. A config screen that rendered
 * from client-side state would show defaults to every player on every server and let them "change" nothing.
 * </p>
 * <p>
 * ⭐ The payload is just {@code HiveConfigSchema.toTag(config)}. The schema already knows how to serialise every field
 * for the SavedData path, so the screen costs no new serialisation code and picks up new fields automatically — the
 * same reason the JSON file needed none.
 * </p>
 */
public record S2CHiveConfigSnapshotPayload(CompoundTag data) implements CustomPacketPayload {

    public static final ResourceLocation PAYLOAD_ID = Alien.MOD.resources().createLocation("hive_config_snapshot");

    public static final Type<S2CHiveConfigSnapshotPayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<S2CHiveConfigSnapshotPayload> CODEC = RecordStreamCodec.of(
        CompoundTagStreamCodec.INSTANCE,
        S2CHiveConfigSnapshotPayload::data,
        S2CHiveConfigSnapshotPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
