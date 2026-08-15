package com.alien.common.gameplay.hive.location;

import com.alien.Alien;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record HiveIdentityReserveEntry(
    EntityType<?> type,
    UUID uuid,
    CompoundTag entityTag
) {

    private static final String NBT_TYPE = "Type";

    private static final String NBT_UUID = "Uuid";

    private static final String NBT_ENTITY = "Entity";

    public static @Nullable HiveIdentityReserveEntry capture(Entity entity) {
        var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (typeId == null) {
            Alien.LOGGER.warn("Hive: could not identity-reserve {} because its entity type is not registered.", entity.getUUID());
            return null;
        }

        var entityTag = entity.saveWithoutId(new CompoundTag());
        entityTag.putString("id", typeId.toString());
        entityTag.putUUID(Entity.UUID_TAG, entity.getUUID());
        return new HiveIdentityReserveEntry(entity.getType(), entity.getUUID(), entityTag);
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString(NBT_TYPE, BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        tag.putUUID(NBT_UUID, uuid);
        tag.put(NBT_ENTITY, entityTag.copy());
        return tag;
    }

    public static @Nullable HiveIdentityReserveEntry load(CompoundTag tag) {
        if (!tag.contains(NBT_TYPE) || !tag.hasUUID(NBT_UUID) || !tag.contains(NBT_ENTITY)) {
            return null;
        }

        var typeId = ResourceLocation.tryParse(tag.getString(NBT_TYPE));
        if (typeId == null) {
            return null;
        }
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
        if (type == null) {
            return null;
        }

        var entityTag = tag.getCompound(NBT_ENTITY).copy();
        entityTag.putString("id", typeId.toString());
        entityTag.putUUID(Entity.UUID_TAG, tag.getUUID(NBT_UUID));
        return new HiveIdentityReserveEntry(type, tag.getUUID(NBT_UUID), entityTag);
    }

    public @Nullable Entity createEntity(ServerLevel level) {
        var tag = entityTag.copy();
        tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        tag.putUUID(Entity.UUID_TAG, uuid);
        return EntityType.loadEntityRecursive(tag, level, entity -> entity);
    }
}
