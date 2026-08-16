package com.alien.fabric.data.tag;

import com.alien.common.registry.init.block.AberrantAlienChitinBlocks;
import com.alien.common.registry.init.block.AberrantAlienResinBlocks;
import com.alien.common.registry.init.block.AlienBlocks;
import com.alien.common.registry.init.block.AlienChitinBlocks;
import com.alien.common.registry.init.block.AlienResinBlocks;
import com.alien.common.registry.init.block.IrradiatedAlienChitinBlocks;
import com.alien.common.registry.init.block.IrradiatedAlienResinBlocks;
import com.alien.common.registry.init.block.NetherAlienChitinBlocks;
import com.alien.common.registry.init.block.NetherAlienResinBlocks;
import com.alien.common.registry.tag.AlienBlockTags;
import com.blib.api.common.tag.v1.BLibBlockTags;
import mods.cybercat.gigeresque.common.tags.GigTags;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;

import java.util.concurrent.CompletableFuture;

public class AlienBlockTagProvider extends FabricTagProvider.BlockTagProvider {

    private static final String[] XENOMORPH_FRENZY_BREAKABLE_BLOCKS = {
        "avp_human:white_industrial_concrete",
        "avp_human:orange_industrial_concrete",
        "avp_human:magenta_industrial_concrete",
        "avp_human:light_blue_industrial_concrete",
        "avp_human:yellow_industrial_concrete",
        "avp_human:lime_industrial_concrete",
        "avp_human:pink_industrial_concrete",
        "avp_human:gray_industrial_concrete",
        "avp_human:light_gray_industrial_concrete",
        "avp_human:cyan_industrial_concrete",
        "avp_human:purple_industrial_concrete",
        "avp_human:blue_industrial_concrete",
        "avp_human:brown_industrial_concrete",
        "avp_human:green_industrial_concrete",
        "avp_human:red_industrial_concrete",
        "avp_human:black_industrial_concrete",
        "avp_human:white_industrial_concrete_slab",
        "avp_human:orange_industrial_concrete_slab",
        "avp_human:magenta_industrial_concrete_slab",
        "avp_human:light_blue_industrial_concrete_slab",
        "avp_human:yellow_industrial_concrete_slab",
        "avp_human:lime_industrial_concrete_slab",
        "avp_human:pink_industrial_concrete_slab",
        "avp_human:gray_industrial_concrete_slab",
        "avp_human:light_gray_industrial_concrete_slab",
        "avp_human:cyan_industrial_concrete_slab",
        "avp_human:purple_industrial_concrete_slab",
        "avp_human:blue_industrial_concrete_slab",
        "avp_human:brown_industrial_concrete_slab",
        "avp_human:green_industrial_concrete_slab",
        "avp_human:red_industrial_concrete_slab",
        "avp_human:black_industrial_concrete_slab",
        "avp_human:white_industrial_concrete_stairs",
        "avp_human:orange_industrial_concrete_stairs",
        "avp_human:magenta_industrial_concrete_stairs",
        "avp_human:light_blue_industrial_concrete_stairs",
        "avp_human:yellow_industrial_concrete_stairs",
        "avp_human:lime_industrial_concrete_stairs",
        "avp_human:pink_industrial_concrete_stairs",
        "avp_human:gray_industrial_concrete_stairs",
        "avp_human:light_gray_industrial_concrete_stairs",
        "avp_human:cyan_industrial_concrete_stairs",
        "avp_human:purple_industrial_concrete_stairs",
        "avp_human:blue_industrial_concrete_stairs",
        "avp_human:brown_industrial_concrete_stairs",
        "avp_human:green_industrial_concrete_stairs",
        "avp_human:red_industrial_concrete_stairs",
        "avp_human:black_industrial_concrete_stairs",
        "avp_human:white_industrial_concrete_wall",
        "avp_human:orange_industrial_concrete_wall",
        "avp_human:magenta_industrial_concrete_wall",
        "avp_human:light_blue_industrial_concrete_wall",
        "avp_human:yellow_industrial_concrete_wall",
        "avp_human:lime_industrial_concrete_wall",
        "avp_human:pink_industrial_concrete_wall",
        "avp_human:gray_industrial_concrete_wall",
        "avp_human:light_gray_industrial_concrete_wall",
        "avp_human:cyan_industrial_concrete_wall",
        "avp_human:purple_industrial_concrete_wall",
        "avp_human:blue_industrial_concrete_wall",
        "avp_human:brown_industrial_concrete_wall",
        "avp_human:green_industrial_concrete_wall",
        "avp_human:red_industrial_concrete_wall",
        "avp_human:black_industrial_concrete_wall",
        "avp_human:industrial_glass",
        "avp_human:white_industrial_glass",
        "avp_human:orange_industrial_glass",
        "avp_human:magenta_industrial_glass",
        "avp_human:light_blue_industrial_glass",
        "avp_human:yellow_industrial_glass",
        "avp_human:lime_industrial_glass",
        "avp_human:pink_industrial_glass",
        "avp_human:gray_industrial_glass",
        "avp_human:light_gray_industrial_glass",
        "avp_human:cyan_industrial_glass",
        "avp_human:purple_industrial_glass",
        "avp_human:blue_industrial_glass",
        "avp_human:brown_industrial_glass",
        "avp_human:green_industrial_glass",
        "avp_human:red_industrial_glass",
        "avp_human:black_industrial_glass",
        "avp_human:industrial_glass_pane",
        "avp_human:white_industrial_glass_pane",
        "avp_human:orange_industrial_glass_pane",
        "avp_human:magenta_industrial_glass_pane",
        "avp_human:light_blue_industrial_glass_pane",
        "avp_human:yellow_industrial_glass_pane",
        "avp_human:lime_industrial_glass_pane",
        "avp_human:pink_industrial_glass_pane",
        "avp_human:gray_industrial_glass_pane",
        "avp_human:light_gray_industrial_glass_pane",
        "avp_human:cyan_industrial_glass_pane",
        "avp_human:purple_industrial_glass_pane",
        "avp_human:blue_industrial_glass_pane",
        "avp_human:brown_industrial_glass_pane",
        "avp_human:green_industrial_glass_pane",
        "avp_human:red_industrial_glass_pane",
        "avp_human:black_industrial_glass_pane",
        "avp_human:industrial_glass_door",
        "avp_human:industrial_glass_slab",
        "avp_human:industrial_glass_stairs",
        "avp_human:industrial_glass_trapdoor",
        "avp_human:white_cut_plastic",
        "avp_human:orange_cut_plastic",
        "avp_human:magenta_cut_plastic",
        "avp_human:light_blue_cut_plastic",
        "avp_human:yellow_cut_plastic",
        "avp_human:lime_cut_plastic",
        "avp_human:pink_cut_plastic",
        "avp_human:gray_cut_plastic",
        "avp_human:light_gray_cut_plastic",
        "avp_human:cyan_cut_plastic",
        "avp_human:purple_cut_plastic",
        "avp_human:blue_cut_plastic",
        "avp_human:brown_cut_plastic",
        "avp_human:green_cut_plastic",
        "avp_human:red_cut_plastic",
        "avp_human:black_cut_plastic",
        "avp_human:white_cut_plastic_slab",
        "avp_human:orange_cut_plastic_slab",
        "avp_human:magenta_cut_plastic_slab",
        "avp_human:light_blue_cut_plastic_slab",
        "avp_human:yellow_cut_plastic_slab",
        "avp_human:lime_cut_plastic_slab",
        "avp_human:pink_cut_plastic_slab",
        "avp_human:gray_cut_plastic_slab",
        "avp_human:light_gray_cut_plastic_slab",
        "avp_human:cyan_cut_plastic_slab",
        "avp_human:purple_cut_plastic_slab",
        "avp_human:blue_cut_plastic_slab",
        "avp_human:brown_cut_plastic_slab",
        "avp_human:green_cut_plastic_slab",
        "avp_human:red_cut_plastic_slab",
        "avp_human:black_cut_plastic_slab",
        "avp_human:white_cut_plastic_stairs",
        "avp_human:orange_cut_plastic_stairs",
        "avp_human:magenta_cut_plastic_stairs",
        "avp_human:light_blue_cut_plastic_stairs",
        "avp_human:yellow_cut_plastic_stairs",
        "avp_human:lime_cut_plastic_stairs",
        "avp_human:pink_cut_plastic_stairs",
        "avp_human:gray_cut_plastic_stairs",
        "avp_human:light_gray_cut_plastic_stairs",
        "avp_human:cyan_cut_plastic_stairs",
        "avp_human:purple_cut_plastic_stairs",
        "avp_human:blue_cut_plastic_stairs",
        "avp_human:brown_cut_plastic_stairs",
        "avp_human:green_cut_plastic_stairs",
        "avp_human:red_cut_plastic_stairs",
        "avp_human:black_cut_plastic_stairs",
        "avp_human:white_framed_plastic",
        "avp_human:orange_framed_plastic",
        "avp_human:magenta_framed_plastic",
        "avp_human:light_blue_framed_plastic",
        "avp_human:yellow_framed_plastic",
        "avp_human:lime_framed_plastic",
        "avp_human:pink_framed_plastic",
        "avp_human:gray_framed_plastic",
        "avp_human:light_gray_framed_plastic",
        "avp_human:cyan_framed_plastic",
        "avp_human:purple_framed_plastic",
        "avp_human:blue_framed_plastic",
        "avp_human:brown_framed_plastic",
        "avp_human:green_framed_plastic",
        "avp_human:red_framed_plastic",
        "avp_human:black_framed_plastic",
        "avp_human:white_pitted_plastic",
        "avp_human:orange_pitted_plastic",
        "avp_human:magenta_pitted_plastic",
        "avp_human:light_blue_pitted_plastic",
        "avp_human:yellow_pitted_plastic",
        "avp_human:lime_pitted_plastic",
        "avp_human:pink_pitted_plastic",
        "avp_human:gray_pitted_plastic",
        "avp_human:light_gray_pitted_plastic",
        "avp_human:cyan_pitted_plastic",
        "avp_human:purple_pitted_plastic",
        "avp_human:blue_pitted_plastic",
        "avp_human:brown_pitted_plastic",
        "avp_human:green_pitted_plastic",
        "avp_human:red_pitted_plastic",
        "avp_human:black_pitted_plastic",
        "avp_human:white_pitted_plastic_slab",
        "avp_human:orange_pitted_plastic_slab",
        "avp_human:magenta_pitted_plastic_slab",
        "avp_human:light_blue_pitted_plastic_slab",
        "avp_human:yellow_pitted_plastic_slab",
        "avp_human:lime_pitted_plastic_slab",
        "avp_human:pink_pitted_plastic_slab",
        "avp_human:gray_pitted_plastic_slab",
        "avp_human:light_gray_pitted_plastic_slab",
        "avp_human:cyan_pitted_plastic_slab",
        "avp_human:purple_pitted_plastic_slab",
        "avp_human:blue_pitted_plastic_slab",
        "avp_human:brown_pitted_plastic_slab",
        "avp_human:green_pitted_plastic_slab",
        "avp_human:red_pitted_plastic_slab",
        "avp_human:black_pitted_plastic_slab",
        "avp_human:white_pitted_plastic_stairs",
        "avp_human:orange_pitted_plastic_stairs",
        "avp_human:magenta_pitted_plastic_stairs",
        "avp_human:light_blue_pitted_plastic_stairs",
        "avp_human:yellow_pitted_plastic_stairs",
        "avp_human:lime_pitted_plastic_stairs",
        "avp_human:pink_pitted_plastic_stairs",
        "avp_human:gray_pitted_plastic_stairs",
        "avp_human:light_gray_pitted_plastic_stairs",
        "avp_human:cyan_pitted_plastic_stairs",
        "avp_human:purple_pitted_plastic_stairs",
        "avp_human:blue_pitted_plastic_stairs",
        "avp_human:brown_pitted_plastic_stairs",
        "avp_human:green_pitted_plastic_stairs",
        "avp_human:red_pitted_plastic_stairs",
        "avp_human:black_pitted_plastic_stairs",
        "avp_human:white_plastic",
        "avp_human:orange_plastic",
        "avp_human:magenta_plastic",
        "avp_human:light_blue_plastic",
        "avp_human:yellow_plastic",
        "avp_human:lime_plastic",
        "avp_human:pink_plastic",
        "avp_human:gray_plastic",
        "avp_human:light_gray_plastic",
        "avp_human:cyan_plastic",
        "avp_human:purple_plastic",
        "avp_human:blue_plastic",
        "avp_human:brown_plastic",
        "avp_human:green_plastic",
        "avp_human:red_plastic",
        "avp_human:black_plastic",
        "avp_human:white_plastic_grate",
        "avp_human:orange_plastic_grate",
        "avp_human:magenta_plastic_grate",
        "avp_human:light_blue_plastic_grate",
        "avp_human:yellow_plastic_grate",
        "avp_human:lime_plastic_grate",
        "avp_human:pink_plastic_grate",
        "avp_human:gray_plastic_grate",
        "avp_human:light_gray_plastic_grate",
        "avp_human:cyan_plastic_grate",
        "avp_human:purple_plastic_grate",
        "avp_human:blue_plastic_grate",
        "avp_human:brown_plastic_grate",
        "avp_human:green_plastic_grate",
        "avp_human:red_plastic_grate",
        "avp_human:black_plastic_grate",
        "avp_human:white_plastic_grate_slab",
        "avp_human:orange_plastic_grate_slab",
        "avp_human:magenta_plastic_grate_slab",
        "avp_human:light_blue_plastic_grate_slab",
        "avp_human:yellow_plastic_grate_slab",
        "avp_human:lime_plastic_grate_slab",
        "avp_human:pink_plastic_grate_slab",
        "avp_human:gray_plastic_grate_slab",
        "avp_human:light_gray_plastic_grate_slab",
        "avp_human:cyan_plastic_grate_slab",
        "avp_human:purple_plastic_grate_slab",
        "avp_human:blue_plastic_grate_slab",
        "avp_human:brown_plastic_grate_slab",
        "avp_human:green_plastic_grate_slab",
        "avp_human:red_plastic_grate_slab",
        "avp_human:black_plastic_grate_slab",
        "avp_human:white_plastic_grate_stairs",
        "avp_human:orange_plastic_grate_stairs",
        "avp_human:magenta_plastic_grate_stairs",
        "avp_human:light_blue_plastic_grate_stairs",
        "avp_human:yellow_plastic_grate_stairs",
        "avp_human:lime_plastic_grate_stairs",
        "avp_human:pink_plastic_grate_stairs",
        "avp_human:gray_plastic_grate_stairs",
        "avp_human:light_gray_plastic_grate_stairs",
        "avp_human:cyan_plastic_grate_stairs",
        "avp_human:purple_plastic_grate_stairs",
        "avp_human:blue_plastic_grate_stairs",
        "avp_human:brown_plastic_grate_stairs",
        "avp_human:green_plastic_grate_stairs",
        "avp_human:red_plastic_grate_stairs",
        "avp_human:black_plastic_grate_stairs",
        "avp_human:white_plastic_slab",
        "avp_human:orange_plastic_slab",
        "avp_human:magenta_plastic_slab",
        "avp_human:light_blue_plastic_slab",
        "avp_human:yellow_plastic_slab",
        "avp_human:lime_plastic_slab",
        "avp_human:pink_plastic_slab",
        "avp_human:gray_plastic_slab",
        "avp_human:light_gray_plastic_slab",
        "avp_human:cyan_plastic_slab",
        "avp_human:purple_plastic_slab",
        "avp_human:blue_plastic_slab",
        "avp_human:brown_plastic_slab",
        "avp_human:green_plastic_slab",
        "avp_human:red_plastic_slab",
        "avp_human:black_plastic_slab",
        "avp_human:white_plastic_stairs",
        "avp_human:orange_plastic_stairs",
        "avp_human:magenta_plastic_stairs",
        "avp_human:light_blue_plastic_stairs",
        "avp_human:yellow_plastic_stairs",
        "avp_human:lime_plastic_stairs",
        "avp_human:pink_plastic_stairs",
        "avp_human:gray_plastic_stairs",
        "avp_human:light_gray_plastic_stairs",
        "avp_human:cyan_plastic_stairs",
        "avp_human:purple_plastic_stairs",
        "avp_human:blue_plastic_stairs",
        "avp_human:brown_plastic_stairs",
        "avp_human:green_plastic_stairs",
        "avp_human:red_plastic_stairs",
        "avp_human:black_plastic_stairs",
        "avp_human:aisle_hazard_plastic",
        "avp_human:aisle_hazard_plastic_stairs",
        "avp_human:aisle_hazard_plastic_slab",
        "avp_human:aisle_hazard_plastic_wall",
        "avp_human:alien_hazard_plastic",
        "avp_human:alien_hazard_plastic_stairs",
        "avp_human:alien_hazard_plastic_slab",
        "avp_human:alien_hazard_plastic_wall",
        "avp_human:fire_hazard_plastic",
        "avp_human:fire_hazard_plastic_stairs",
        "avp_human:fire_hazard_plastic_slab",
        "avp_human:fire_hazard_plastic_wall",
        "avp_human:hazard_plastic",
        "avp_human:hazard_plastic_stairs",
        "avp_human:hazard_plastic_slab",
        "avp_human:hazard_plastic_wall",
        "avp_human:machine_hazard_plastic",
        "avp_human:machine_hazard_plastic_stairs",
        "avp_human:machine_hazard_plastic_slab",
        "avp_human:machine_hazard_plastic_wall",
        "avp_human:radiation_hazard_plastic",
        "avp_human:radiation_hazard_plastic_stairs",
        "avp_human:radiation_hazard_plastic_slab",
        "avp_human:radiation_hazard_plastic_wall",
        "avp_human:safety_plastic",
        "avp_human:safety_plastic_stairs",
        "avp_human:safety_plastic_slab",
        "avp_human:safety_plastic_wall",
        "avp_human:traffic_hazard_plastic",
        "avp_human:traffic_hazard_plastic_stairs",
        "avp_human:traffic_hazard_plastic_slab",
        "avp_human:traffic_hazard_plastic_wall",
        "avp_human:chiseled_steel",
        "avp_human:cut_steel",
        "avp_human:cut_steel_slab",
        "avp_human:cut_steel_stairs",
        "avp_human:steel_bars",
        "avp_human:steel_block",
        "avp_human:steel_button",
        "avp_human:steel_chain_fence",
        "avp_human:steel_column",
        "avp_human:steel_door",
        "avp_human:steel_fastened_siding",
        "avp_human:steel_fastened_siding_slab",
        "avp_human:steel_fastened_siding_stairs",
        "avp_human:steel_fastened_standing",
        "avp_human:steel_fastened_standing_slab",
        "avp_human:steel_fastened_standing_stairs",
        "avp_human:steel_grate",
        "avp_human:steel_grate_slab",
        "avp_human:steel_grate_stairs",
        "avp_human:steel_plating",
        "avp_human:steel_plating_slab",
        "avp_human:steel_plating_stairs",
        "avp_human:steel_pressure_plate",
        "avp_human:steel_siding",
        "avp_human:steel_siding_slab",
        "avp_human:steel_siding_stairs",
        "avp_human:steel_slab",
        "avp_human:steel_stairs",
        "avp_human:steel_standing",
        "avp_human:steel_standing_slab",
        "avp_human:steel_standing_stairs",
        "avp_human:steel_trapdoor",
        "avp_human:steel_tread",
        "avp_human:steel_tread_slab",
        "avp_human:steel_tread_stairs",
        "avp_human:chiseled_titanium",
        "avp_human:cut_titanium",
        "avp_human:cut_titanium_slab",
        "avp_human:cut_titanium_stairs",
        "avp_human:titanium_block",
        "avp_human:titanium_button",
        "avp_human:titanium_chain_fence",
        "avp_human:titanium_column",
        "avp_human:titanium_door",
        "avp_human:titanium_fastened_siding",
        "avp_human:titanium_fastened_siding_slab",
        "avp_human:titanium_fastened_siding_stairs",
        "avp_human:titanium_fastened_standing",
        "avp_human:titanium_fastened_standing_slab",
        "avp_human:titanium_fastened_standing_stairs",
        "avp_human:titanium_grate",
        "avp_human:titanium_grate_slab",
        "avp_human:titanium_grate_stairs",
        "avp_human:titanium_plating",
        "avp_human:titanium_plating_slab",
        "avp_human:titanium_plating_stairs",
        "avp_human:titanium_pressure_plate",
        "avp_human:titanium_siding",
        "avp_human:titanium_siding_slab",
        "avp_human:titanium_siding_stairs",
        "avp_human:titanium_slab",
        "avp_human:titanium_stairs",
        "avp_human:titanium_standing",
        "avp_human:titanium_standing_slab",
        "avp_human:titanium_standing_stairs",
        "avp_human:titanium_trapdoor",
        "avp_human:titanium_tread",
        "avp_human:titanium_tread_slab",
        "avp_human:titanium_tread_stairs",
        "avp_human:uranium_block"
    };

    public AlienBlockTagProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> completableFuture) {
        super(output, completableFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider wrapperLookup) {
        addCompatibilityTags();
        addWallTags();

        getOrCreateTagBuilder(AlienBlockTags.IRRADIATED_RESIN)
            .add(
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN.get(),
                // The plain slab and stairs were MISSING here while every other strain lists theirs. The blocks were
                // registered all along, so nothing errored - they simply were not "resin" to any tag-driven rule:
                // acid immunity, the natural-spawn deny nets, and the irradiated exposure sweep all ask this tag.
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_SLAB.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STAIRS.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_NODE.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_VEIN.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_WEB.get(),

                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICKS.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_SLAB.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_STAIRS.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_WALL.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_VENT.get(),
                IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_SPINE.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_DOORWAY.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE_SLAB.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE_STAIRS.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED_SLAB.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED_STAIRS.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED_SLAB.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED_STAIRS.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL_SLAB.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL_STAIRS.get(),
                IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN_SLAB.get(),
                IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN_STAIRS.get(),
                IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN.get(),
                IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_SLAB.get(),
                IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_STAIRS.get(),
                IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_WALL.get()
            );

        getOrCreateTagBuilder(AlienBlockTags.ABERRANT_RESIN)
            .add(
                AberrantAlienResinBlocks.ABERRANT_RESIN.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_SLAB.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_STAIRS.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_NODE.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_VEIN.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_WEB.get(),

                AberrantAlienResinBlocks.ABERRANT_RESIN_BRICKS.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_SLAB.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_STAIRS.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_WALL.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_VENT.get(),
                AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_SPINE.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_DOORWAY.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_BONE.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_BONE_SLAB.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_BONE_STAIRS.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_ETCHED.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_ETCHED_SLAB.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_ETCHED_STAIRS.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_STRETCHED.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_STRETCHED_SLAB.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_STRETCHED_STAIRS.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_TENDRIL.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_TENDRIL_SLAB.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_TENDRIL_STAIRS.get(),
                AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN_SLAB.get(),
                AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN_STAIRS.get(),
                AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN.get(),
                AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_SLAB.get(),
                AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_STAIRS.get(),
                AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_WALL.get()
            );

        getOrCreateTagBuilder(AlienBlockTags.NETHER_RESIN)
            .add(
                NetherAlienResinBlocks.NETHER_RESIN.get(),
                NetherAlienResinBlocks.NETHER_RESIN_SLAB.get(),
                NetherAlienResinBlocks.NETHER_RESIN_STAIRS.get(),
                NetherAlienResinBlocks.NETHER_RESIN_NODE.get(),
                NetherAlienResinBlocks.NETHER_RESIN_VEIN.get(),
                NetherAlienResinBlocks.NETHER_RESIN_WEB.get(),

                NetherAlienResinBlocks.NETHER_RESIN_BRICKS.get(),
                NetherAlienResinBlocks.NETHER_RESIN_BRICK_SLAB.get(),
                NetherAlienResinBlocks.NETHER_RESIN_BRICK_STAIRS.get(),
                NetherAlienResinBlocks.NETHER_RESIN_BRICK_WALL.get(),
                NetherAlienResinBlocks.NETHER_RESIN_VENT.get(),
                NetherAlienResinBlocks.RIBBED_NETHER_RESIN.get(),
                NetherAlienResinBlocks.NETHER_RESIN_SPINE.get(),
                NetherAlienResinBlocks.NETHER_RESIN_DOORWAY.get(),
                NetherAlienResinBlocks.NETHER_RESIN_BONE.get(),
                NetherAlienResinBlocks.NETHER_RESIN_BONE_SLAB.get(),
                NetherAlienResinBlocks.NETHER_RESIN_BONE_STAIRS.get(),
                NetherAlienResinBlocks.NETHER_RESIN_ETCHED.get(),
                NetherAlienResinBlocks.NETHER_RESIN_ETCHED_SLAB.get(),
                NetherAlienResinBlocks.NETHER_RESIN_ETCHED_STAIRS.get(),
                NetherAlienResinBlocks.NETHER_RESIN_STRETCHED.get(),
                NetherAlienResinBlocks.NETHER_RESIN_STRETCHED_SLAB.get(),
                NetherAlienResinBlocks.NETHER_RESIN_STRETCHED_STAIRS.get(),
                NetherAlienResinBlocks.NETHER_RESIN_TENDRIL.get(),
                NetherAlienResinBlocks.NETHER_RESIN_TENDRIL_SLAB.get(),
                NetherAlienResinBlocks.NETHER_RESIN_TENDRIL_STAIRS.get(),
                NetherAlienResinBlocks.RIBBED_NETHER_RESIN_SLAB.get(),
                NetherAlienResinBlocks.RIBBED_NETHER_RESIN_STAIRS.get(),
                NetherAlienResinBlocks.SMOOTH_NETHER_RESIN.get(),
                NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_SLAB.get(),
                NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_STAIRS.get(),
                NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_WALL.get()
            );

        getOrCreateTagBuilder(AlienBlockTags.NORMAL_RESIN)
            .add(
                AlienResinBlocks.RESIN.get(),
                AlienResinBlocks.RESIN_SLAB.get(),
                AlienResinBlocks.RESIN_STAIRS.get(),
                AlienResinBlocks.RESIN_SPINE.get(),
                AlienResinBlocks.RESIN_DOORWAY.get(),
                AlienResinBlocks.RIBBED_RESIN.get(),
                AlienResinBlocks.RIBBED_RESIN_SLAB.get(),
                AlienResinBlocks.RIBBED_RESIN_STAIRS.get(),
                AlienResinBlocks.RESIN_BONE.get(),
                AlienResinBlocks.RESIN_BONE_SLAB.get(),
                AlienResinBlocks.RESIN_BONE_STAIRS.get(),
                AlienResinBlocks.RESIN_ETCHED.get(),
                AlienResinBlocks.RESIN_ETCHED_SLAB.get(),
                AlienResinBlocks.RESIN_ETCHED_STAIRS.get(),
                AlienResinBlocks.RESIN_STRETCHED.get(),
                AlienResinBlocks.RESIN_STRETCHED_SLAB.get(),
                AlienResinBlocks.RESIN_STRETCHED_STAIRS.get(),
                AlienResinBlocks.RESIN_TENDRIL.get(),
                AlienResinBlocks.RESIN_TENDRIL_SLAB.get(),
                AlienResinBlocks.RESIN_TENDRIL_STAIRS.get(),
                AlienResinBlocks.RESIN_NODE.get(),
                AlienResinBlocks.RESIN_VEIN.get(),
                AlienResinBlocks.RESIN_WEB.get(),

                AlienResinBlocks.RESIN_BRICKS.get(),
                AlienResinBlocks.RESIN_BRICK_SLAB.get(),
                AlienResinBlocks.RESIN_BRICK_STAIRS.get(),
                AlienResinBlocks.RESIN_BRICK_WALL.get(),
                AlienResinBlocks.RESIN_VENT.get(),
                AlienResinBlocks.SMOOTH_RESIN.get(),
                AlienResinBlocks.SMOOTH_RESIN_SLAB.get(),
                AlienResinBlocks.SMOOTH_RESIN_STAIRS.get(),
                AlienResinBlocks.SMOOTH_RESIN_WALL.get()
            );

        getOrCreateTagBuilder(AlienBlockTags.ABERRANT_CHITIN)
            .add(
                AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK.get(),
                AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK_SLAB.get(),
                AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK_STAIRS.get(),
                AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK_WALL.get(),
                AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICKS.get(),
                AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICK_SLAB.get(),
                AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICK_STAIRS.get(),
                AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICK_WALL.get(),
                AberrantAlienChitinBlocks.CHISELED_ABERRANT_CHITIN_BRICKS.get(),
                AberrantAlienChitinBlocks.CHISELED_ABERRANT_CHITIN_BRICKS_EMBRYO.get(),
                AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN.get(),
                AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN_SLAB.get(),
                AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN_STAIRS.get(),
                AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN_WALL.get()
            );

        getOrCreateTagBuilder(AlienBlockTags.IRRADIATED_CHITIN)
            .add(
                IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK.get(),
                IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK_SLAB.get(),
                IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK_STAIRS.get(),
                IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK_WALL.get(),
                IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICKS.get(),
                IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICK_SLAB.get(),
                IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICK_STAIRS.get(),
                IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICK_WALL.get(),
                IrradiatedAlienChitinBlocks.CHISELED_IRRADIATED_CHITIN_BRICKS.get(),
                IrradiatedAlienChitinBlocks.CHISELED_IRRADIATED_CHITIN_BRICKS_EMBRYO.get(),
                IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN.get(),
                IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN_SLAB.get(),
                IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN_STAIRS.get(),
                IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN_WALL.get()
            );

        getOrCreateTagBuilder(AlienBlockTags.NETHER_CHITIN)
            .add(

                NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK.get(),
                NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK_SLAB.get(),
                NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK_STAIRS.get(),
                NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK_WALL.get(),
                NetherAlienChitinBlocks.NETHER_CHITIN_BRICKS.get(),
                NetherAlienChitinBlocks.NETHER_CHITIN_BRICK_SLAB.get(),
                NetherAlienChitinBlocks.NETHER_CHITIN_BRICK_STAIRS.get(),
                NetherAlienChitinBlocks.NETHER_CHITIN_BRICK_WALL.get(),
                NetherAlienChitinBlocks.CHISELED_NETHER_CHITIN_BRICKS.get(),
                NetherAlienChitinBlocks.CHISELED_NETHER_CHITIN_BRICKS_EMBRYO.get(),
                NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN.get(),
                NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN_SLAB.get(),
                NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN_STAIRS.get(),
                NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN_WALL.get()
            );

        getOrCreateTagBuilder(AlienBlockTags.NORMAL_CHITIN)
            .add(
                AlienChitinBlocks.CHITIN_BLOCK.get(),
                AlienChitinBlocks.CHITIN_BLOCK_SLAB.get(),
                AlienChitinBlocks.CHITIN_BLOCK_STAIRS.get(),
                AlienChitinBlocks.CHITIN_BLOCK_WALL.get(),
                AlienChitinBlocks.CHITIN_BRICKS.get(),
                AlienChitinBlocks.CHITIN_BRICK_SLAB.get(),
                AlienChitinBlocks.CHITIN_BRICK_STAIRS.get(),
                AlienChitinBlocks.CHITIN_BRICK_WALL.get(),
                AlienChitinBlocks.CHISELED_CHITIN_BRICKS.get(),
                AlienChitinBlocks.CHISELED_CHITIN_BRICKS_EMBRYO.get(),
                AlienChitinBlocks.POLISHED_CHITIN.get(),
                AlienChitinBlocks.POLISHED_CHITIN_SLAB.get(),
                AlienChitinBlocks.POLISHED_CHITIN_STAIRS.get(),
                AlienChitinBlocks.POLISHED_CHITIN_WALL.get()
            );

        getOrCreateTagBuilder(AlienBlockTags.CHITIN)
            .addTag(AlienBlockTags.ABERRANT_CHITIN)
            .addTag(AlienBlockTags.IRRADIATED_CHITIN)
            .addTag(AlienBlockTags.NETHER_CHITIN)
            .addTag(AlienBlockTags.NORMAL_CHITIN);

        getOrCreateTagBuilder(AlienBlockTags.RESIN)
            .addTag(AlienBlockTags.ABERRANT_RESIN)
            .addTag(AlienBlockTags.IRRADIATED_RESIN)
            .addTag(AlienBlockTags.NETHER_RESIN)
            .addTag(AlienBlockTags.NORMAL_RESIN);

        getOrCreateTagBuilder(AlienBlockTags.RESIN_BLOCKS)
            .add(
                AberrantAlienResinBlocks.ABERRANT_RESIN.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN.get(),
                NetherAlienResinBlocks.NETHER_RESIN.get(),
                AlienResinBlocks.RESIN.get()
            );

        getOrCreateTagBuilder(AlienBlockTags.RESIN_NODES)
            .add(
                AberrantAlienResinBlocks.ABERRANT_RESIN_NODE.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_NODE.get(),
                NetherAlienResinBlocks.NETHER_RESIN_NODE.get(),
                AlienResinBlocks.RESIN_NODE.get()
            );

        getOrCreateTagBuilder(AlienBlockTags.RESIN_REPLACEABLE)
            .addOptionalTag(BlockTags.BASE_STONE_NETHER)
            .addOptionalTag(BlockTags.BASE_STONE_OVERWORLD)
            .addOptionalTag(BlockTags.DIRT)
            .addOptionalTag(BlockTags.NYLIUM)
            .addOptionalTag(BlockTags.TERRACOTTA)
            .add(
                Blocks.CALCITE,
                Blocks.CLAY,
                Blocks.GRASS_BLOCK,
                Blocks.DRIPSTONE_BLOCK,
                Blocks.END_STONE,
                Blocks.GRAVEL,
                Blocks.RED_SAND,
                Blocks.RED_SANDSTONE,
                Blocks.SAND,
                Blocks.SANDSTONE,
                Blocks.SMOOTH_BASALT,
                Blocks.SOUL_SAND,
                Blocks.SOUL_SOIL
            );

        getOrCreateTagBuilder(AlienBlockTags.ABERRANT_RESIN_REPLACEABLE)
            .addTag(AlienBlockTags.RESIN_REPLACEABLE)
            .addTag(AlienBlockTags.IRRADIATED_RESIN)
            .addTag(AlienBlockTags.NETHER_RESIN)
            .addTag(AlienBlockTags.NORMAL_RESIN);

        getOrCreateTagBuilder(AlienBlockTags.IRRADIATED_RESIN_REPLACEABLE)
            .addTag(AlienBlockTags.RESIN_REPLACEABLE)
            .addTag(AlienBlockTags.ABERRANT_RESIN)
            .addTag(AlienBlockTags.NETHER_RESIN)
            .addTag(AlienBlockTags.NORMAL_RESIN);

        getOrCreateTagBuilder(AlienBlockTags.NETHER_RESIN_REPLACEABLE)
            .addTag(AlienBlockTags.RESIN_REPLACEABLE)
            .addTag(AlienBlockTags.ABERRANT_RESIN)
            .addTag(AlienBlockTags.IRRADIATED_RESIN)
            .addTag(AlienBlockTags.NORMAL_RESIN);

        getOrCreateTagBuilder(AlienBlockTags.NORMAL_RESIN_REPLACEABLE)
            .addTag(AlienBlockTags.RESIN_REPLACEABLE)
            .addTag(AlienBlockTags.ABERRANT_RESIN)
            .addTag(AlienBlockTags.IRRADIATED_RESIN)
            .addTag(AlienBlockTags.NETHER_RESIN);

        getOrCreateTagBuilder(AlienBlockTags.RESIN_VEINS)
            .add(
                AberrantAlienResinBlocks.ABERRANT_RESIN_VEIN.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_VEIN.get(),
                NetherAlienResinBlocks.NETHER_RESIN_VEIN.get(),
                AlienResinBlocks.RESIN_VEIN.get()
            );

        // The FLOOR tendril of each strain - slabs and stairs deliberately excluded, chamber furniture needs a
        // full block under it.
        getOrCreateTagBuilder(AlienBlockTags.RESIN_TENDRILS)
            .add(
                AberrantAlienResinBlocks.ABERRANT_RESIN_TENDRIL.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL.get(),
                NetherAlienResinBlocks.NETHER_RESIN_TENDRIL.get(),
                AlienResinBlocks.RESIN_TENDRIL.get()
            );

        getOrCreateTagBuilder(AlienBlockTags.RESIN_VENTS)
            .add(
                AberrantAlienResinBlocks.ABERRANT_RESIN_VENT.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_VENT.get(),
                NetherAlienResinBlocks.NETHER_RESIN_VENT.get(),
                AlienResinBlocks.RESIN_VENT.get()
            );

        getOrCreateTagBuilder(AlienBlockTags.RESIN_WEBS)
            .add(
                AberrantAlienResinBlocks.ABERRANT_RESIN_WEB.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_WEB.get(),
                NetherAlienResinBlocks.NETHER_RESIN_WEB.get(),
                AlienResinBlocks.RESIN_WEB.get()
            );

        // ⭐ What the hive salvages instead of deleting. See AlienBlockTags.HIVE_SALVAGE.
        getOrCreateTagBuilder(AlienBlockTags.HIVE_SALVAGE)
            // ⚠ OPTIONAL: c:ores is a convention tag supplied by the loader, not by us. addOptionalTag means a
            // stripped-down instance without it loads instead of failing datapack validation.
            .addOptionalTag(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "ores"))
            // The one thing no ore tag anywhere contains.
            .add(Blocks.GILDED_BLACKSTONE);

        // Acid-immune blocks
        getOrCreateTagBuilder(AlienBlockTags.ACID_IMMUNE)
            .addOptionalTag(BLibBlockTags.SHOULD_NOT_BE_DESTROYED)
            .addTag(AlienBlockTags.CHITIN)
            .addTag(AlienBlockTags.RESIN)
            // ⚠ THE CONTAINERS ARE LISTED EXPLICITLY, not picked up by the RESIN tag. They are a resin BLOCK by
            // material but not a member of that tag - it is the plain building set - so relying on it would have
            // left them dissolving in their own hive's blood. [stated] "acid proof and explosion proof."
            .add(AlienBlocks.RESIN_CONTAINER.get())
            .add(AlienBlocks.NETHER_RESIN_CONTAINER.get())
            .add(AlienBlocks.ABERRANT_RESIN_CONTAINER.get())
            .add(AlienBlocks.IRRADIATED_RESIN_CONTAINER.get())
            .add(Blocks.AIR)
            .add(Blocks.FIRE)
            .add(Blocks.SOUL_FIRE);

        getOrCreateTagBuilder(AlienBlockTags.NETHER_ACID_IMMUNE)
            .addOptionalTag(BlockTags.INFINIBURN_NETHER)
            .addTag(AlienBlockTags.ACID_IMMUNE);

        getOrCreateTagBuilder(AlienBlockTags.IRRADIATED_ACID_IMMUNE)
            .addTag(AlienBlockTags.ACID_IMMUNE)
            .add(
                Blocks.BLUE_ICE
            );

        getOrCreateTagBuilder(AlienBlockTags.XENOMORPH_IMMUNE)
            .addOptionalTag(BLibBlockTags.SHOULD_NOT_BE_DESTROYED)
            .addTag(AlienBlockTags.RESIN_VENTS)
            .addTag(AlienBlockTags.RESIN_WEBS);

        // THE HARBINGER BREAK BLACKLIST. Her front kick breaks material ordinary xenomorph digging cannot, so
        // it carries its own much shorter list. Blocks with negative hardness (bedrock, barrier, end portal
        // frame, command blocks, structure/jigsaw blocks) are already unbreakable and are NOT listed here -
        // the kick rejects them on hardness alone. This tag is only for blocks that could be broken but must
        // not be, and it is the place to add more.
        getOrCreateTagBuilder(AlienBlockTags.HARBINGER_UNBREAKABLE)
            .addOptionalTag(BLibBlockTags.SHOULD_NOT_BE_DESTROYED)
            .addTag(AlienBlockTags.XENOMORPH_IMMUNE)
            .add(
                Blocks.REINFORCED_DEEPSLATE,
                Blocks.END_PORTAL,
                Blocks.END_PORTAL_FRAME,
                Blocks.END_GATEWAY,
                Blocks.BEDROCK,
                Blocks.OBSIDIAN,
                Blocks.CRYING_OBSIDIAN,
                Blocks.RESPAWN_ANCHOR,
                Blocks.ANCIENT_DEBRIS,
                Blocks.NETHER_PORTAL
            );

        var xenomorphFrenzyBreakable = getOrCreateTagBuilder(AlienBlockTags.XENOMORPH_FRENZY_BREAKABLE);
        for (var blockId : XENOMORPH_FRENZY_BREAKABLE_BLOCKS) {
            var idParts = blockId.split(":", 2);
            xenomorphFrenzyBreakable.addOptional(ResourceLocation.fromNamespaceAndPath(idParts[0], idParts[1]));
        }

        getOrCreateTagBuilder(BlockTags.MINEABLE_WITH_AXE)
            .addTag(AlienBlockTags.RESIN_VEINS)
            .addTag(AlienBlockTags.RESIN_WEBS);

        // Swords cut resin webbing and veins too, at their efficient mining speed (the same tag cobwebs sit in).
        // These blocks only drop with Silk Touch regardless, so this is purely about being able to slash them
        // apart with a blade as well as an axe.
        getOrCreateTagBuilder(BlockTags.SWORD_EFFICIENT)
            .addTag(AlienBlockTags.RESIN_VEINS)
            .addTag(AlienBlockTags.RESIN_WEBS);

        getOrCreateTagBuilder(BlockTags.MINEABLE_WITH_PICKAXE)
            .addTag(AlienBlockTags.CHITIN)
            .add(
                // ⚠⚠ WITHOUT THIS THE CONTAINERS ARE EFFECTIVELY UNBREAKABLE. The resin property base sets
                // requiresCorrectToolForDrops(), so a block in NO mineable tag has no correct tool at all - every
                // attempt takes the 100x wrong-tool penalty AND gets no speed bonus from a pickaxe, which at
                // strength 6 reads as "cannot break it with any tool or punching". Every other resin block is in
                // this tag; the containers were simply never added.
                com.alien.common.registry.init.block.AlienBlocks.RESIN_CONTAINER.get(),
                com.alien.common.registry.init.block.AlienBlocks.NETHER_RESIN_CONTAINER.get(),
                com.alien.common.registry.init.block.AlienBlocks.ABERRANT_RESIN_CONTAINER.get(),
                com.alien.common.registry.init.block.AlienBlocks.IRRADIATED_RESIN_CONTAINER.get(),
                com.alien.common.registry.init.block.AlienBlocks.JELLY_VAT.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_SLAB.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_STAIRS.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_BRICKS.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_SLAB.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_STAIRS.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_WALL.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_NODE.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_VENT.get(),
                AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_SPINE.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_DOORWAY.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_BONE.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_BONE_SLAB.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_BONE_STAIRS.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_ETCHED.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_ETCHED_SLAB.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_ETCHED_STAIRS.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_STRETCHED.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_STRETCHED_SLAB.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_STRETCHED_STAIRS.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_TENDRIL.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_TENDRIL_SLAB.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_TENDRIL_STAIRS.get(),
                AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN_SLAB.get(),
                AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN_STAIRS.get(),
                AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN.get(),
                AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_SLAB.get(),
                AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_STAIRS.get(),
                AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_WALL.get(),

                IrradiatedAlienResinBlocks.IRRADIATED_RESIN.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_SLAB.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STAIRS.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICKS.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_SLAB.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_STAIRS.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_WALL.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_NODE.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_VENT.get(),
                IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_SPINE.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_DOORWAY.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE_SLAB.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE_STAIRS.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED_SLAB.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED_STAIRS.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED_SLAB.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED_STAIRS.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL_SLAB.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL_STAIRS.get(),
                IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN_SLAB.get(),
                IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN_STAIRS.get(),
                IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN.get(),
                IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_SLAB.get(),
                IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_STAIRS.get(),
                IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_WALL.get(),

                NetherAlienResinBlocks.NETHER_RESIN.get(),
                NetherAlienResinBlocks.NETHER_RESIN_SLAB.get(),
                NetherAlienResinBlocks.NETHER_RESIN_STAIRS.get(),
                NetherAlienResinBlocks.NETHER_RESIN_BRICKS.get(),
                NetherAlienResinBlocks.NETHER_RESIN_BRICK_SLAB.get(),
                NetherAlienResinBlocks.NETHER_RESIN_BRICK_STAIRS.get(),
                NetherAlienResinBlocks.NETHER_RESIN_BRICK_WALL.get(),
                NetherAlienResinBlocks.NETHER_RESIN_NODE.get(),
                NetherAlienResinBlocks.NETHER_RESIN_VENT.get(),
                NetherAlienResinBlocks.RIBBED_NETHER_RESIN.get(),
                NetherAlienResinBlocks.NETHER_RESIN_SPINE.get(),
                NetherAlienResinBlocks.NETHER_RESIN_DOORWAY.get(),
                NetherAlienResinBlocks.NETHER_RESIN_BONE.get(),
                NetherAlienResinBlocks.NETHER_RESIN_BONE_SLAB.get(),
                NetherAlienResinBlocks.NETHER_RESIN_BONE_STAIRS.get(),
                NetherAlienResinBlocks.NETHER_RESIN_ETCHED.get(),
                NetherAlienResinBlocks.NETHER_RESIN_ETCHED_SLAB.get(),
                NetherAlienResinBlocks.NETHER_RESIN_ETCHED_STAIRS.get(),
                NetherAlienResinBlocks.NETHER_RESIN_STRETCHED.get(),
                NetherAlienResinBlocks.NETHER_RESIN_STRETCHED_SLAB.get(),
                NetherAlienResinBlocks.NETHER_RESIN_STRETCHED_STAIRS.get(),
                NetherAlienResinBlocks.NETHER_RESIN_TENDRIL.get(),
                NetherAlienResinBlocks.NETHER_RESIN_TENDRIL_SLAB.get(),
                NetherAlienResinBlocks.NETHER_RESIN_TENDRIL_STAIRS.get(),
                NetherAlienResinBlocks.RIBBED_NETHER_RESIN_SLAB.get(),
                NetherAlienResinBlocks.RIBBED_NETHER_RESIN_STAIRS.get(),
                NetherAlienResinBlocks.SMOOTH_NETHER_RESIN.get(),
                NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_SLAB.get(),
                NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_STAIRS.get(),
                NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_WALL.get(),

                AlienResinBlocks.RESIN.get(),
                AlienResinBlocks.RESIN_SLAB.get(),
                AlienResinBlocks.RESIN_STAIRS.get(),
                AlienResinBlocks.RESIN_BRICKS.get(),
                AlienResinBlocks.RESIN_BRICK_SLAB.get(),
                AlienResinBlocks.RESIN_BRICK_STAIRS.get(),
                AlienResinBlocks.RESIN_BRICK_WALL.get(),
                AlienResinBlocks.RESIN_NODE.get(),
                AlienResinBlocks.RESIN_VENT.get(),
                AlienResinBlocks.RESIN_SPINE.get(),
                AlienResinBlocks.RESIN_DOORWAY.get(),
                AlienResinBlocks.RIBBED_RESIN.get(),
                AlienResinBlocks.RIBBED_RESIN_SLAB.get(),
                AlienResinBlocks.RIBBED_RESIN_STAIRS.get(),
                AlienResinBlocks.RESIN_BONE.get(),
                AlienResinBlocks.RESIN_BONE_SLAB.get(),
                AlienResinBlocks.RESIN_BONE_STAIRS.get(),
                AlienResinBlocks.RESIN_ETCHED.get(),
                AlienResinBlocks.RESIN_ETCHED_SLAB.get(),
                AlienResinBlocks.RESIN_ETCHED_STAIRS.get(),
                AlienResinBlocks.RESIN_STRETCHED.get(),
                AlienResinBlocks.RESIN_STRETCHED_SLAB.get(),
                AlienResinBlocks.RESIN_STRETCHED_STAIRS.get(),
                AlienResinBlocks.RESIN_TENDRIL.get(),
                AlienResinBlocks.RESIN_TENDRIL_SLAB.get(),
                AlienResinBlocks.RESIN_TENDRIL_STAIRS.get(),
                AlienResinBlocks.SMOOTH_RESIN.get(),
                AlienResinBlocks.SMOOTH_RESIN_SLAB.get(),
                AlienResinBlocks.SMOOTH_RESIN_STAIRS.get(),
                AlienResinBlocks.SMOOTH_RESIN_WALL.get()
            );

        getOrCreateTagBuilder(BlockTags.NEEDS_STONE_TOOL)
            .addTag(AlienBlockTags.CHITIN)
            .addTag(AlienBlockTags.RESIN);
    }

    /**
     * Every wall block belongs in {@code minecraft:walls}, or it will not connect to anything.
     * <p>
     * {@code WallBlock.connectsTo} joins to a neighbour when the neighbour is IN THIS TAG, presents a sturdy face, or
     * is iron bars / a fence gate. A wall's own side face is not sturdy - only its top is - so membership of this tag
     * is the ONLY thing that makes wall-to-wall connections happen. Without it the models and blockstates are perfectly
     * correct and you still get a row of disconnected posts, which is exactly what the testers saw.
     * <p>
     * It also cuts both ways with the rest of the game: vanilla and other mods' walls will not connect to these either
     * until they are in here.
     */
    private void addWallTags() {
        getOrCreateTagBuilder(BlockTags.WALLS)
            .add(
                AlienResinBlocks.RESIN_BRICK_WALL.get(),
                AlienResinBlocks.SMOOTH_RESIN_WALL.get(),
                AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_WALL.get(),
                AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_WALL.get(),
                IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_WALL.get(),
                IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_WALL.get(),
                NetherAlienResinBlocks.NETHER_RESIN_BRICK_WALL.get(),
                NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_WALL.get(),
                AlienChitinBlocks.CHITIN_BLOCK_WALL.get(),
                AlienChitinBlocks.CHITIN_BRICK_WALL.get(),
                AlienChitinBlocks.POLISHED_CHITIN_WALL.get(),
                AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK_WALL.get(),
                AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICK_WALL.get(),
                AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN_WALL.get(),
                IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK_WALL.get(),
                IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICK_WALL.get(),
                IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN_WALL.get(),
                NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK_WALL.get(),
                NetherAlienChitinBlocks.NETHER_CHITIN_BRICK_WALL.get(),
                NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN_WALL.get()
            );
    }

    private void addCompatibilityTags() {
        getOrCreateTagBuilder(AlienBlockTags.ACID_IMMUNE)
            .addOptionalTag(GigTags.ACID_RESISTANT);

        getOrCreateTagBuilder(GigTags.ACID_RESISTANT)
            .addTag(AlienBlockTags.CHITIN)
            .addTag(AlienBlockTags.RESIN);
    }
}
