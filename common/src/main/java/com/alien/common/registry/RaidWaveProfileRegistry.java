package com.alien.common.registry;

import com.alien.AlienResources;
import com.alien.common.gameplay.hive.convoy.RaidWaveProfile;
import com.alien.common.model.alien.variant.AlienVariant;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class RaidWaveProfileRegistry {

    public static final ResourceLocation DEFAULT_ID = AlienResources.location("default");

    public static final ResourceLocation REVENGE_ID = AlienResources.location("revenge");

    public static final ResourceLocation REVENGE_EMPRESS_ID = AlienResources.location("revenge_empress");

    public static final ResourceLocation RESCUE_ID = AlienResources.location("rescue");

    private static final Map<ResourceLocation, RaidWaveProfile> PROFILES = new LinkedHashMap<>();

    private RaidWaveProfileRegistry() {}

    public static void clear() {
        PROFILES.clear();
    }

    public static void register(ResourceLocation id, RaidWaveProfile profile) {
        PROFILES.put(id, profile);
    }

    public static RaidWaveProfile active() {
        var profile = PROFILES.get(DEFAULT_ID);
        if (profile != null) {
            return profile;
        }
        return RaidWaveProfile.fallback();
    }

    public static RaidWaveProfile forVariant(AlienVariant variant) {
        var profile = PROFILES.get(profileIdFor(variant));
        if (profile != null) {
            return profile;
        }
        return active();
    }

    /**
     * The revenge raid profile (queen killed): a datapack-registered {@code revenge} profile if present, else the
     * built-in 3-wave {@link RaidWaveProfile#revengeFallback}. Distinct from {@link #forVariant} so revenge stays a
     * shorter, sharper strike regardless of the variant's normal 5-wave profile.
     */
    public static RaidWaveProfile revenge() {
        var profile = PROFILES.get(REVENGE_ID);
        if (profile != null) {
            return profile;
        }
        return RaidWaveProfile.revengeFallback();
    }

    /**
     * The revenge profile for a lineage that HAS an empress: the same strike with the scourge tier allowed in,
     * harbinger excluded. Datapack {@code revenge_empress} if present, else the built-in fallback.
     */
    public static RaidWaveProfile revengeEmpress() {
        var profile = PROFILES.get(REVENGE_EMPRESS_ID);
        if (profile != null) {
            return profile;
        }
        return RaidWaveProfile.revengeEmpressFallback();
    }

    /**
     * The rescue raid profile (queen captured/lost): a datapack-registered {@code rescue} profile if present, else the
     * built-in {@link RaidWaveProfile#rescueFallback}.
     */
    public static RaidWaveProfile rescue() {
        var profile = PROFILES.get(RESCUE_ID);
        if (profile != null) {
            return profile;
        }
        return RaidWaveProfile.rescueFallback();
    }

    public static ResourceLocation profileIdFor(AlienVariant variant) {
        return AlienResources.location(variant.name().toLowerCase(Locale.ROOT));
    }

    public static @Nullable RaidWaveProfile get(ResourceLocation id) {
        return PROFILES.get(id);
    }

    public static Map<ResourceLocation, RaidWaveProfile> all() {
        return Collections.unmodifiableMap(PROFILES);
    }
}
