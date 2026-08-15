package com.alien.common.data;

import com.alien.Alien;
import com.alien.common.model.parasite.HeadAttachmentProfile;
import com.alien.common.network.HeadAttachmentSync;
import com.alien.common.registry.HeadAttachmentRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Loads facehugger head-attachment profiles from {@code data/<namespace>/head_data/<name>.json}. The mod ships one JSON
 * per supported vanilla mob; any datapack can add third-party mobs or override the shipped numbers (later packs win per
 * normal datapack stacking, since profiles are keyed by the {@code entity} field, not the file name). After every
 * (re)load the fresh set is pushed to all connected clients so {@code /reload} takes effect without relogging.
 */
public class HeadAttachmentReloadListener extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY_NAME = "head_data";

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    public HeadAttachmentReloadListener() {
        super(GSON, DIRECTORY_NAME);
    }

    @Override
    protected void apply(
        @NotNull Map<ResourceLocation, JsonElement> resourceLocationJsonElementMap,
        @NotNull ResourceManager resourceManager,
        @NotNull ProfilerFiller profilerFiller
    ) {
        HeadAttachmentRegistry.clear();

        for (var entry : resourceLocationJsonElementMap.entrySet()) {
            var id = entry.getKey();
            var jsonElement = entry.getValue();

            HeadAttachmentProfile.CODEC.parse(JsonOps.INSTANCE, jsonElement)
                .resultOrPartial(err -> Alien.LOGGER.error("Failed to parse HeadAttachmentProfile {}: {}", id, err))
                .ifPresent(HeadAttachmentRegistry::register);
        }

        Alien.LOGGER.info("Loaded {} facehugger head attachment profile(s)", HeadAttachmentRegistry.size());

        // No-op during initial server boot (no server captured yet, no players connected); live after /reload.
        HeadAttachmentSync.broadcastToAll();
    }
}
