package com.alien.common.registry.init.creative_mode_tab.initializer;

import com.alien.common.registry.init.block.AlienBlocks;
import net.minecraft.world.item.CreativeModeTab;

import java.util.function.Consumer;

public class FunctionalBlocksCreativeModeTabInitializer {

    public static final Consumer<CreativeModeTab.Output> OUTPUT_CONSUMER = output -> {
        XenomorphHeadCreativeModeTabEntries.addHeads(output);
        addResinContainers(output);
    };

    /**
     * ⚠ FUNCTIONAL BLOCKS, NOT THE BUILDING TAB. The containers are furniture with an inventory, so they sit beside the
     * heads rather than among the resin building set - and it keeps four one-per-strain entries out of a tab that is
     * already long.
     */
    private static void addResinContainers(CreativeModeTab.Output output) {
        CreativeModeTabUtil.accept(output, AlienBlocks.RESIN_CONTAINER);
        CreativeModeTabUtil.accept(output, AlienBlocks.NETHER_RESIN_CONTAINER);
        CreativeModeTabUtil.accept(output, AlienBlocks.ABERRANT_RESIN_CONTAINER);
        CreativeModeTabUtil.accept(output, AlienBlocks.IRRADIATED_RESIN_CONTAINER);
    }
}
