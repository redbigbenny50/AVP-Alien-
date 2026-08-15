package com.alien.fabric.data.molting_profile;

import com.alien.AlienResources;
import com.alien.common.data.MoltingProfileReloadListener;
import com.alien.common.model.lifecycle.growth.MoltingProfile;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public abstract class MoltingProfileDataProvider implements DataProvider {

    private final FabricDataOutput output;

    private final Map<String, MoltingProfile> dataByName;

    protected MoltingProfileDataProvider(FabricDataOutput output) {
        this.output = output;
        this.dataByName = new HashMap<>();
    }

    protected abstract void generate();

    public void add(String name, MoltingProfile moltingProfile) {
        dataByName.put(name, moltingProfile);
    }

    @Override
    public final @NotNull CompletableFuture<?> run(CachedOutput cached) {
        generate();

        var pathProvider = output.createPathProvider(PackOutput.Target.DATA_PACK, MoltingProfileReloadListener.DIRECTORY_NAME);

        var futures = dataByName.entrySet()
            .stream()
            .map(entry -> {
                var name = entry.getKey();
                var moltingProfile = entry.getValue();
                var id = AlienResources.location(name);

                var filePath = pathProvider.json(id);
                var jsonElement = MoltingProfile.CODEC.encodeStart(JsonOps.INSTANCE, moltingProfile)
                    .getOrThrow();

                return DataProvider.saveStable(cached, jsonElement, filePath);
            });

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    @Override
    public final @NotNull String getName() {
        return "Molting Profiles";
    }
}
