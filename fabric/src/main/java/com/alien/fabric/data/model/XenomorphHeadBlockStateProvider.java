package com.alien.fabric.data.model;

import com.alien.AlienResources;
import com.alien.common.registry.init.item.AlienXenomorphHeadItems;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public final class XenomorphHeadBlockStateProvider implements DataProvider {

    private final FabricDataOutput output;

    public XenomorphHeadBlockStateProvider(FabricDataOutput output) {
        this.output = output;
    }

    @Override
    public @NotNull CompletableFuture<?> run(CachedOutput cachedOutput) {
        var pathProvider = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "blockstates");
        var futures = new ArrayList<CompletableFuture<?>>();

        AlienXenomorphHeadItems.GENERIC_BLOCK_ENTRIES.forEach(entry -> {
            futures.add(save(cachedOutput, pathProvider, entry.standingBlock().get(), entry.itemPath()));
            futures.add(save(cachedOutput, pathProvider, entry.wallBlock().get(), entry.itemPath()));
        });

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private static CompletableFuture<?> save(
        CachedOutput cachedOutput,
        PackOutput.PathProvider pathProvider,
        Block block,
        String itemPath
    ) {
        var blockId = BuiltInRegistries.BLOCK.getKey(block);
        var root = new JsonObject();
        var variants = new JsonObject();
        var variant = new JsonObject();

        variant.addProperty("model", AlienResources.location("item/" + itemPath).toString());
        variants.add("", variant);
        root.add("variants", variants);

        return DataProvider.saveStable(cachedOutput, root, pathProvider.json(blockId));
    }

    @Override
    public @NotNull String getName() {
        return "Xenomorph Head Block States";
    }
}
