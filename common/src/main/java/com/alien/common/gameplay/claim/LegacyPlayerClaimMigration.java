package com.alien.common.gameplay.claim;

import com.alien.Alien;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LegacyPlayerClaimMigration {

    private static final String DATA_NAME = "avp_alien_player_claims";

    private LegacyPlayerClaimMigration() {}

    public static void migrateToBLib(MinecraftServer server) {
        var legacy = server.overworld()
            .getDataStorage()
            .computeIfAbsent(new SavedData.Factory<>(LegacyData::new, LegacyData::load, null), DATA_NAME);

        for (var entry : legacy.extraSlotsByOwner.entrySet()) {
            var owner = entry.getKey();
            var targetSlots = Math.max(0, entry.getValue());
            var currentSlots = Alien.MOD.territory().getPurchasedPlayerClaimSlots(server, owner);

            for (var i = currentSlots; i < targetSlots; i++) {
                Alien.MOD.territory().addPurchasedPlayerClaimSlot(server, owner);
            }
        }

        for (var entry : legacy.ownersByClaim.entrySet()) {
            var key = entry.getKey();
            var dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(key.dimension()));
            var level = server.getLevel(dimension);

            if (level == null) {
                continue;
            }

            var chunk = new ChunkPos(key.x(), key.z());
            if (!Alien.MOD.territory().isPlayerClaimed(level, chunk)) {
                Alien.MOD.territory().claimPlayerChunk(level, chunk, entry.getValue());
            }
        }
    }

    private static final class LegacyData extends SavedData {

        private static final String NBT_CLAIMS = "Claims";

        private static final String NBT_DIMENSION = "Dimension";

        private static final String NBT_CHUNK_X = "ChunkX";

        private static final String NBT_CHUNK_Z = "ChunkZ";

        private static final String NBT_OWNER = "Owner";

        private static final String NBT_EXTRA_SLOTS = "ExtraSlots";

        private final Map<ClaimKey, UUID> ownersByClaim = new HashMap<>();

        private final Map<UUID, Integer> extraSlotsByOwner = new HashMap<>();

        @Override
        public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
            return tag;
        }

        private static LegacyData load(CompoundTag tag, HolderLookup.Provider provider) {
            var data = new LegacyData();

            if (tag.contains(NBT_CLAIMS, Tag.TAG_LIST)) {
                var claimTags = tag.getList(NBT_CLAIMS, Tag.TAG_COMPOUND);
                for (var i = 0; i < claimTags.size(); i++) {
                    var claimTag = claimTags.getCompound(i);
                    if (claimTag.hasUUID(NBT_OWNER)) {
                        data.ownersByClaim.put(
                            new ClaimKey(
                                claimTag.getString(NBT_DIMENSION),
                                claimTag.getInt(NBT_CHUNK_X),
                                claimTag.getInt(NBT_CHUNK_Z)
                            ),
                            claimTag.getUUID(NBT_OWNER)
                        );
                    }
                }
            }

            if (tag.contains(NBT_EXTRA_SLOTS, Tag.TAG_COMPOUND)) {
                var extraSlotsTag = tag.getCompound(NBT_EXTRA_SLOTS);
                for (var key : extraSlotsTag.getAllKeys()) {
                    try {
                        data.extraSlotsByOwner.put(UUID.fromString(key), Math.max(0, extraSlotsTag.getInt(key)));
                    } catch (IllegalArgumentException ignored) {
                        // Ignore malformed legacy rows.
                    }
                }
            }

            return data;
        }
    }

    private record ClaimKey(
        String dimension,
        int x,
        int z
    ) {}
}
