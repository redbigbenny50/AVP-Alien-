package com.alien.fabric.data.tag;

import com.alien.common.registry.tag.AlienBiomeTags;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.concurrent.CompletableFuture;

public class AlienBiomeTagProvider extends FabricTagProvider<Biome> {

    public AlienBiomeTagProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> completableFuture) {
        super(output, Registries.BIOME, completableFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider wrapperLookup) {
        getOrCreateTagBuilder(AlienBiomeTags.HAS_BADLANDS_ALTAR)
            .add(Biomes.BADLANDS);

        getOrCreateTagBuilder(AlienBiomeTags.HAS_DESERT_ALTAR)
            .add(Biomes.DESERT)
            // Ad Astra. Folded in from the community avp_ad_astra datapack, Sep 22.
            .addOptional(ResourceLocation.fromNamespaceAndPath("ad_astra", "martian_wastelands"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("ad_astra", "martian_canyon_creek"))
            .addOptional(ResourceLocation.fromNamespaceAndPath("ad_astra", "venus_wastelands"));

        getOrCreateTagBuilder(AlienBiomeTags.HAS_DEEPSLATE_ALTAR)
            .addOptionalTag(BiomeTags.IS_OVERWORLD);

        getOrCreateTagBuilder(AlienBiomeTags.HAS_JUNGLE_ALTAR)
            .add(Biomes.JUNGLE)
            .add(Biomes.BAMBOO_JUNGLE)
            .add(Biomes.SPARSE_JUNGLE);

        getOrCreateTagBuilder(AlienBiomeTags.HAS_NETHER_ALTAR)
            .add(Biomes.NETHER_WASTES)
            .add(Biomes.CRIMSON_FOREST)
            // Ad Astra. Folded in from the community avp_ad_astra datapack, Sep 22.
            .addOptional(ResourceLocation.fromNamespaceAndPath("ad_astra", "mercury_deltas"));

        getOrCreateTagBuilder(AlienBiomeTags.HAS_ALTAR)
            .addTag(AlienBiomeTags.HAS_BADLANDS_ALTAR)
            .addTag(AlienBiomeTags.HAS_DESERT_ALTAR);

        getOrCreateTagBuilder(AlienBiomeTags.HAS_XENOMORPHS)
            .addOptionalTag(BiomeTags.IS_NETHER)
            .addOptionalTag(BiomeTags.IS_OVERWORLD);
    }
}
