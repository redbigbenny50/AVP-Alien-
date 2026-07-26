package com.alien.common.registry.init.creative_mode_tab.initializer;

import com.alien.common.registry.init.block.AberrantAlienChitinBlocks;
import com.alien.common.registry.init.block.AberrantAlienResinBlocks;
import com.alien.common.registry.init.block.AlienBlocks;
import com.alien.common.registry.init.block.AlienChitinBlocks;
import com.alien.common.registry.init.block.AlienResinBlocks;
import com.alien.common.registry.init.block.IrradiatedAlienChitinBlocks;
import com.alien.common.registry.init.block.IrradiatedAlienResinBlocks;
import com.alien.common.registry.init.block.NetherAlienChitinBlocks;
import com.alien.common.registry.init.block.NetherAlienResinBlocks;
import com.alien.compatibility.avp_human.AVPHuman;
import net.minecraft.world.item.CreativeModeTab;

import java.util.function.Consumer;

public class BlocksCreativeModeTabInitializer {

    public static final Consumer<CreativeModeTab.Output> OUTPUT_CONSUMER = output -> {
        // Alien blocks
        addBaseXenomorphBlocks(output);
        addNetherXenomorphBlocks(output);
        addAberrantXenomorphBlocks(output);

        if (AVPHuman.MOD.isLoaded()) {
            addIrradiatedXenomorphBlocks(output);
        }

        CreativeModeTabUtil.accept(output, AlienBlocks.ROYAL_JELLY_BLOCK);
        CreativeModeTabUtil.accept(output, AlienBlocks.SCOURGE_JELLY_BLOCK);
    };

    private static void addBaseXenomorphBlocks(CreativeModeTab.Output output) {
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RESIN);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RESIN_SLAB);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RESIN_STAIRS);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RESIN_BONE);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RESIN_BONE_SLAB);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RESIN_BONE_STAIRS);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RESIN_ETCHED);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RESIN_ETCHED_SLAB);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RESIN_ETCHED_STAIRS);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RESIN_STRETCHED);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RESIN_STRETCHED_SLAB);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RESIN_STRETCHED_STAIRS);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RESIN_TENDRIL);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RESIN_TENDRIL_SLAB);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RESIN_TENDRIL_STAIRS);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RESIN_DOORWAY);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RESIN_SPINE);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RESIN_BRICKS);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RESIN_BRICK_SLAB);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RESIN_BRICK_STAIRS);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RESIN_BRICK_WALL);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RESIN_VEIN);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RESIN_VENT);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RESIN_WEB);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RIBBED_RESIN);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RIBBED_RESIN_SLAB);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.RIBBED_RESIN_STAIRS);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.SMOOTH_RESIN);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.SMOOTH_RESIN_SLAB);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.SMOOTH_RESIN_STAIRS);
        CreativeModeTabUtil.accept(output, AlienResinBlocks.SMOOTH_RESIN_WALL);

        CreativeModeTabUtil.accept(output, AlienChitinBlocks.CHITIN_BLOCK);
        CreativeModeTabUtil.accept(output, AlienChitinBlocks.CHITIN_BLOCK_SLAB);
        CreativeModeTabUtil.accept(output, AlienChitinBlocks.CHITIN_BLOCK_STAIRS);
        CreativeModeTabUtil.accept(output, AlienChitinBlocks.CHITIN_BLOCK_WALL);
        CreativeModeTabUtil.accept(output, AlienChitinBlocks.CHITIN_BRICKS);
        CreativeModeTabUtil.accept(output, AlienChitinBlocks.CHITIN_BRICK_SLAB);
        CreativeModeTabUtil.accept(output, AlienChitinBlocks.CHITIN_BRICK_STAIRS);
        CreativeModeTabUtil.accept(output, AlienChitinBlocks.CHITIN_BRICK_WALL);
        CreativeModeTabUtil.accept(output, AlienChitinBlocks.POLISHED_CHITIN);
        CreativeModeTabUtil.accept(output, AlienChitinBlocks.POLISHED_CHITIN_SLAB);
        CreativeModeTabUtil.accept(output, AlienChitinBlocks.POLISHED_CHITIN_STAIRS);
        CreativeModeTabUtil.accept(output, AlienChitinBlocks.POLISHED_CHITIN_WALL);
        CreativeModeTabUtil.accept(output, AlienChitinBlocks.CHISELED_CHITIN_BRICKS);
        CreativeModeTabUtil.accept(output, AlienChitinBlocks.CHISELED_CHITIN_BRICKS_EMBRYO);
    }

    private static void addNetherXenomorphBlocks(CreativeModeTab.Output output) {
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.NETHER_RESIN);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.NETHER_RESIN_SLAB);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.NETHER_RESIN_STAIRS);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.NETHER_RESIN_BONE);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.NETHER_RESIN_BONE_SLAB);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.NETHER_RESIN_BONE_STAIRS);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.NETHER_RESIN_ETCHED);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.NETHER_RESIN_ETCHED_SLAB);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.NETHER_RESIN_ETCHED_STAIRS);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.NETHER_RESIN_STRETCHED);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.NETHER_RESIN_STRETCHED_SLAB);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.NETHER_RESIN_STRETCHED_STAIRS);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.NETHER_RESIN_TENDRIL);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.NETHER_RESIN_TENDRIL_SLAB);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.NETHER_RESIN_TENDRIL_STAIRS);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.NETHER_RESIN_DOORWAY);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.NETHER_RESIN_SPINE);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.NETHER_RESIN_BRICKS);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.NETHER_RESIN_BRICK_SLAB);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.NETHER_RESIN_BRICK_STAIRS);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.NETHER_RESIN_BRICK_WALL);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.NETHER_RESIN_VEIN);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.NETHER_RESIN_VENT);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.NETHER_RESIN_WEB);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.RIBBED_NETHER_RESIN);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.RIBBED_NETHER_RESIN_SLAB);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.RIBBED_NETHER_RESIN_STAIRS);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.SMOOTH_NETHER_RESIN);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_SLAB);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_STAIRS);
        CreativeModeTabUtil.accept(output, NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_WALL);

        CreativeModeTabUtil.accept(output, NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK);
        CreativeModeTabUtil.accept(output, NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK_SLAB);
        CreativeModeTabUtil.accept(output, NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK_STAIRS);
        CreativeModeTabUtil.accept(output, NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK_WALL);
        CreativeModeTabUtil.accept(output, NetherAlienChitinBlocks.NETHER_CHITIN_BRICKS);
        CreativeModeTabUtil.accept(output, NetherAlienChitinBlocks.NETHER_CHITIN_BRICK_SLAB);
        CreativeModeTabUtil.accept(output, NetherAlienChitinBlocks.NETHER_CHITIN_BRICK_STAIRS);
        CreativeModeTabUtil.accept(output, NetherAlienChitinBlocks.NETHER_CHITIN_BRICK_WALL);
        CreativeModeTabUtil.accept(output, NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN);
        CreativeModeTabUtil.accept(output, NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN_SLAB);
        CreativeModeTabUtil.accept(output, NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN_STAIRS);
        CreativeModeTabUtil.accept(output, NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN_WALL);
        CreativeModeTabUtil.accept(output, NetherAlienChitinBlocks.CHISELED_NETHER_CHITIN_BRICKS);
        CreativeModeTabUtil.accept(output, NetherAlienChitinBlocks.CHISELED_NETHER_CHITIN_BRICKS_EMBRYO);
    }

    private static void addAberrantXenomorphBlocks(CreativeModeTab.Output output) {
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.ABERRANT_RESIN);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.ABERRANT_RESIN_SLAB);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.ABERRANT_RESIN_STAIRS);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.ABERRANT_RESIN_BONE);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.ABERRANT_RESIN_BONE_SLAB);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.ABERRANT_RESIN_BONE_STAIRS);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.ABERRANT_RESIN_ETCHED);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.ABERRANT_RESIN_ETCHED_SLAB);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.ABERRANT_RESIN_ETCHED_STAIRS);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.ABERRANT_RESIN_STRETCHED);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.ABERRANT_RESIN_STRETCHED_SLAB);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.ABERRANT_RESIN_STRETCHED_STAIRS);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.ABERRANT_RESIN_TENDRIL);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.ABERRANT_RESIN_TENDRIL_SLAB);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.ABERRANT_RESIN_TENDRIL_STAIRS);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.ABERRANT_RESIN_DOORWAY);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.ABERRANT_RESIN_SPINE);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.ABERRANT_RESIN_BRICKS);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_SLAB);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_STAIRS);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_WALL);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.ABERRANT_RESIN_VEIN);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.ABERRANT_RESIN_VENT);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.ABERRANT_RESIN_WEB);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN_SLAB);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN_STAIRS);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_SLAB);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_STAIRS);
        CreativeModeTabUtil.accept(output, AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_WALL);

        CreativeModeTabUtil.accept(output, AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK);
        CreativeModeTabUtil.accept(output, AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK_SLAB);
        CreativeModeTabUtil.accept(output, AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK_STAIRS);
        CreativeModeTabUtil.accept(output, AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK_WALL);
        CreativeModeTabUtil.accept(output, AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICKS);
        CreativeModeTabUtil.accept(output, AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICK_SLAB);
        CreativeModeTabUtil.accept(output, AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICK_STAIRS);
        CreativeModeTabUtil.accept(output, AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICK_WALL);
        CreativeModeTabUtil.accept(output, AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN);
        CreativeModeTabUtil.accept(output, AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN_SLAB);
        CreativeModeTabUtil.accept(output, AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN_STAIRS);
        CreativeModeTabUtil.accept(output, AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN_WALL);
        CreativeModeTabUtil.accept(output, AberrantAlienChitinBlocks.CHISELED_ABERRANT_CHITIN_BRICKS);
        CreativeModeTabUtil.accept(output, AberrantAlienChitinBlocks.CHISELED_ABERRANT_CHITIN_BRICKS_EMBRYO);
    }

    private static void addIrradiatedXenomorphBlocks(CreativeModeTab.Output output) {
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.IRRADIATED_RESIN);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_SLAB);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STAIRS);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE_SLAB);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE_STAIRS);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED_SLAB);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED_STAIRS);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED_SLAB);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED_STAIRS);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL_SLAB);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL_STAIRS);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_DOORWAY);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_SPINE);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICKS);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_SLAB);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_STAIRS);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_WALL);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_VEIN);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_VENT);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_WEB);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN_SLAB);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN_STAIRS);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_SLAB);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_STAIRS);
        CreativeModeTabUtil.accept(output, IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_WALL);

        CreativeModeTabUtil.accept(output, IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK);
        CreativeModeTabUtil.accept(output, IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK_SLAB);
        CreativeModeTabUtil.accept(output, IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK_STAIRS);
        CreativeModeTabUtil.accept(output, IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK_WALL);
        CreativeModeTabUtil.accept(output, IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICKS);
        CreativeModeTabUtil.accept(output, IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICK_SLAB);
        CreativeModeTabUtil.accept(output, IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICK_STAIRS);
        CreativeModeTabUtil.accept(output, IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICK_WALL);
        CreativeModeTabUtil.accept(output, IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN);
        CreativeModeTabUtil.accept(output, IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN_SLAB);
        CreativeModeTabUtil.accept(output, IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN_STAIRS);
        CreativeModeTabUtil.accept(output, IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN_WALL);
        CreativeModeTabUtil.accept(output, IrradiatedAlienChitinBlocks.CHISELED_IRRADIATED_CHITIN_BRICKS);
        CreativeModeTabUtil.accept(output, IrradiatedAlienChitinBlocks.CHISELED_IRRADIATED_CHITIN_BRICKS_EMBRYO);
    }
}
