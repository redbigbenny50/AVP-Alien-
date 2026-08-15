package com.alien.common.registry;

import com.alien.common.model.lifecycle.growth.MoltingProfile;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class MoltingProfileRegistry {

    private static final Map<EntityType<?>, MoltingProfile> REGISTRY = new HashMap<>();

    public static @Nullable MoltingProfile get(EntityType<?> entityType) {
        return REGISTRY.get(entityType);
    }

    public static boolean has(EntityType<?> entityType) {
        return REGISTRY.containsKey(entityType);
    }

    public static void clear() {
        REGISTRY.clear();
    }

    public static void register(MoltingProfile moltingProfile) {
        REGISTRY.put(moltingProfile.entityType(), moltingProfile);
    }
}
