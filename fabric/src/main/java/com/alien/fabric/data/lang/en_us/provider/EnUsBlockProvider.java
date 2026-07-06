package com.alien.fabric.data.lang.en_us.provider;

import com.alien.common.registry.init.block.AberrantAlienChitinBlocks;
import com.alien.common.registry.init.block.AberrantAlienResinBlocks;
import com.alien.common.registry.init.block.AlienBlocks;
import com.alien.common.registry.init.block.AlienChitinBlocks;
import com.alien.common.registry.init.block.AlienResinBlocks;
import com.alien.common.registry.init.block.IrradiatedAlienResinBlocks;
import com.alien.common.registry.init.block.NetherAlienChitinBlocks;
import com.alien.common.registry.init.block.NetherAlienResinBlocks;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.world.level.block.Block;

import java.util.HashSet;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class EnUsBlockProvider {

    private static final HashSet<Block> TOUCHED_ENTRIES = new HashSet<>();

    public static final Consumer<FabricLanguageProvider.TranslationBuilder> CONSUMER = builder -> {
        addBlock(builder, AlienBlocks.ROYAL_JELLY_BLOCK, "Royal Jelly Block");
        addBlock(builder, AlienBlocks.SCOURGE_JELLY_BLOCK, "Scourge Jelly Block");
        addBlock(builder, AlienBlocks.ANCHOR, "Anchor");
        addBlock(builder, AlienBlocks.JELLY_VAT, "Jelly Vat");

        addBlock(builder, AberrantAlienResinBlocks.ABERRANT_RESIN, "Aberrant Resin");
        addBlock(builder, AberrantAlienResinBlocks.ABERRANT_RESIN_BRICKS, "Aberrant Resin Bricks");
        addBlock(builder, AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_SLAB, "Aberrant Resin Brick Slab");
        addBlock(builder, AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_STAIRS, "Aberrant Resin Brick Stairs");
        addBlock(builder, AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_WALL, "Aberrant Resin Brick Wall");
        addBlock(builder, AberrantAlienResinBlocks.ABERRANT_RESIN_SLAB, "Aberrant Resin Slab");
        addBlock(builder, AberrantAlienResinBlocks.ABERRANT_RESIN_STAIRS, "Aberrant Resin Stairs");
        addBlock(builder, AberrantAlienResinBlocks.ABERRANT_RESIN_NODE, "Aberrant Resin");
        addBlock(builder, AberrantAlienResinBlocks.ABERRANT_RESIN_VEIN, "Aberrant Resin Vein");
        addBlock(builder, AberrantAlienResinBlocks.ABERRANT_RESIN_VENT, "Aberrant Resin Vent");
        addBlock(builder, AberrantAlienResinBlocks.ABERRANT_RESIN_WEB, "Aberrant Resin Web");

        addBlock(builder, IrradiatedAlienResinBlocks.IRRADIATED_RESIN, "Irradiated Resin");
        addBlock(builder, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICKS, "Irradiated Resin Bricks");
        addBlock(builder, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_SLAB, "Irradiated Resin Brick Slab");
        addBlock(builder, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_STAIRS, "Irradiated Resin Brick Stairs");
        addBlock(builder, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_WALL, "Irradiated Resin Brick Wall");
        addBlock(builder, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_SLAB, "Irradiated Resin Slab");
        addBlock(builder, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STAIRS, "Irradiated Resin Stairs");
        addBlock(builder, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_NODE, "Irradiated Resin");
        addBlock(builder, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_VEIN, "Irradiated Resin Vein");
        addBlock(builder, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_VENT, "Irradiated Resin Vent");
        addBlock(builder, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_WEB, "Irradiated Resin Web");

        addBlock(builder, NetherAlienResinBlocks.NETHER_RESIN, "Nether Resin");
        addBlock(builder, NetherAlienResinBlocks.NETHER_RESIN_BRICKS, "Nether Resin Bricks");
        addBlock(builder, NetherAlienResinBlocks.NETHER_RESIN_BRICK_SLAB, "Nether Resin Brick Slab");
        addBlock(builder, NetherAlienResinBlocks.NETHER_RESIN_BRICK_STAIRS, "Nether Resin Brick Stairs");
        addBlock(builder, NetherAlienResinBlocks.NETHER_RESIN_BRICK_WALL, "Nether Resin Brick Wall");
        addBlock(builder, NetherAlienResinBlocks.NETHER_RESIN_SLAB, "Nether Resin Slab");
        addBlock(builder, NetherAlienResinBlocks.NETHER_RESIN_STAIRS, "Nether Resin Stairs");
        addBlock(builder, NetherAlienResinBlocks.NETHER_RESIN_NODE, "Nether Resin");
        addBlock(builder, NetherAlienResinBlocks.NETHER_RESIN_VEIN, "Nether Resin Vein");
        addBlock(builder, NetherAlienResinBlocks.NETHER_RESIN_VENT, "Nether Resin Vent");
        addBlock(builder, NetherAlienResinBlocks.NETHER_RESIN_WEB, "Nether Resin Web");

        addBlock(builder, AlienResinBlocks.RESIN, "Resin");
        addBlock(builder, AlienResinBlocks.RESIN_BRICKS, "Resin Bricks");
        addBlock(builder, AlienResinBlocks.RESIN_BRICK_SLAB, "Resin Brick Slab");
        addBlock(builder, AlienResinBlocks.RESIN_BRICK_STAIRS, "Resin Brick Stairs");
        addBlock(builder, AlienResinBlocks.RESIN_BRICK_WALL, "Resin Brick Wall");
        addBlock(builder, AlienResinBlocks.RESIN_NODE, "Resin");
        addBlock(builder, AlienResinBlocks.RESIN_SLAB, "Resin Slab");
        addBlock(builder, AlienResinBlocks.RESIN_STAIRS, "Resin Stairs");
        addBlock(builder, AlienResinBlocks.RESIN_VEIN, "Resin Vein");
        addBlock(builder, AlienResinBlocks.RESIN_VENT, "Resin Vent");
        addBlock(builder, AlienResinBlocks.RESIN_WEB, "Resin Web");
        addBlock(builder, AlienResinBlocks.RESIN_DOORWAY, "Resin Doorway");
        addBlock(builder, AlienResinBlocks.RESIN_SPINE, "Resin Spine");
        addBlock(builder, AlienResinBlocks.RESIN_BONE, "Resin Bone");
        addBlock(builder, AlienResinBlocks.RESIN_BONE_SLAB, "Resin Bone Slab");
        addBlock(builder, AlienResinBlocks.RESIN_BONE_STAIRS, "Resin Bone Stairs");
        addBlock(builder, AlienResinBlocks.RESIN_ETCHED, "Resin Etched");
        addBlock(builder, AlienResinBlocks.RESIN_ETCHED_SLAB, "Resin Etched Slab");
        addBlock(builder, AlienResinBlocks.RESIN_ETCHED_STAIRS, "Resin Etched Stairs");
        addBlock(builder, AlienResinBlocks.RESIN_STRETCHED, "Resin Stretched");
        addBlock(builder, AlienResinBlocks.RESIN_STRETCHED_SLAB, "Resin Stretched Slab");
        addBlock(builder, AlienResinBlocks.RESIN_STRETCHED_STAIRS, "Resin Stretched Stairs");
        addBlock(builder, AlienResinBlocks.RESIN_TENDRIL, "Resin Tendril");
        addBlock(builder, AlienResinBlocks.RESIN_TENDRIL_SLAB, "Resin Tendril Slab");
        addBlock(builder, AlienResinBlocks.RESIN_TENDRIL_STAIRS, "Resin Tendril Stairs");

        addBlock(builder, AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN, "Ribbed Aberrant Resin");
        addBlock(builder, IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN, "Ribbed Irradiated Resin");
        addBlock(builder, NetherAlienResinBlocks.RIBBED_NETHER_RESIN, "Ribbed Nether Resin");
        addBlock(builder, AlienResinBlocks.RIBBED_RESIN, "Ribbed Resin");
        addBlock(builder, AlienResinBlocks.RIBBED_RESIN_SLAB, "Ribbed Resin Slab");
        addBlock(builder, AlienResinBlocks.RIBBED_RESIN_STAIRS, "Ribbed Resin Stairs");
        addBlock(builder, AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN, "Smooth Aberrant Resin");
        addBlock(builder, AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_SLAB, "Smooth Aberrant Resin Slab");
        addBlock(builder, AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_STAIRS, "Smooth Aberrant Resin Stairs");
        addBlock(builder, AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_WALL, "Smooth Aberrant Resin Wall");
        addBlock(builder, IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN, "Smooth Irradiated Resin");
        addBlock(builder, IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_SLAB, "Smooth Irradiated Resin Slab");
        addBlock(builder, IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_STAIRS, "Smooth Irradiated Resin Stairs");
        addBlock(builder, IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_WALL, "Smooth Irradiated Resin Wall");
        addBlock(builder, NetherAlienResinBlocks.SMOOTH_NETHER_RESIN, "Smooth Nether Resin");
        addBlock(builder, NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_SLAB, "Smooth Nether Resin Slab");
        addBlock(builder, NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_STAIRS, "Smooth Nether Resin Stairs");
        addBlock(builder, NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_WALL, "Smooth Nether Resin Wall");
        addBlock(builder, AlienResinBlocks.SMOOTH_RESIN, "Smooth Resin");
        addBlock(builder, AlienResinBlocks.SMOOTH_RESIN_SLAB, "Smooth Resin Slab");
        addBlock(builder, AlienResinBlocks.SMOOTH_RESIN_STAIRS, "Smooth Resin Stairs");
        addBlock(builder, AlienResinBlocks.SMOOTH_RESIN_WALL, "Smooth Resin Wall");

        addBlock(builder, AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK, "Block of Aberrant Chitin");
        addBlock(builder, AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK_SLAB, "Aberrant Chitin Slab");
        addBlock(builder, AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK_STAIRS, "Aberrant Chitin Stairs");
        addBlock(builder, AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK_WALL, "Aberrant Chitin Wall");
        addBlock(builder, AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICKS, "Aberrant Chitin Bricks");
        addBlock(builder, AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICK_SLAB, "Aberrant Chitin Brick Slab");
        addBlock(builder, AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICK_STAIRS, "Aberrant Chitin Brick Stairs");
        addBlock(builder, AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICK_WALL, "Aberrant Chitin Brick Wall");
        addBlock(builder, AberrantAlienChitinBlocks.CHISELED_ABERRANT_CHITIN_BRICKS, "Chiseled Aberrant Chitin Bricks");
        addBlock(builder, AberrantAlienChitinBlocks.CHISELED_ABERRANT_CHITIN_BRICKS_EMBRYO, "Chiseled Aberrant Chitin Bricks (Embryo)");
        addBlock(builder, AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN, "Polished Aberrant Chitin");
        addBlock(builder, AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN_SLAB, "Polished Aberrant Chitin Slab");
        addBlock(builder, AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN_STAIRS, "Polished Aberrant Chitin Stairs");
        addBlock(builder, AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN_WALL, "Polished Aberrant Chitin Wall");

        addBlock(builder, AlienChitinBlocks.CHITIN_BLOCK, "Block of Chitin");
        addBlock(builder, AlienChitinBlocks.CHITIN_BLOCK_SLAB, "Chitin Slab");
        addBlock(builder, AlienChitinBlocks.CHITIN_BLOCK_STAIRS, "Chitin Stairs");
        addBlock(builder, AlienChitinBlocks.CHITIN_BLOCK_WALL, "Chitin Wall");
        addBlock(builder, AlienChitinBlocks.CHITIN_BRICKS, "Chitin Bricks");
        addBlock(builder, AlienChitinBlocks.CHITIN_BRICK_SLAB, "Chitin Brick Slab");
        addBlock(builder, AlienChitinBlocks.CHITIN_BRICK_STAIRS, "Chitin Brick Stairs");
        addBlock(builder, AlienChitinBlocks.CHITIN_BRICK_WALL, "Chitin Brick Wall");
        addBlock(builder, AlienChitinBlocks.CHISELED_CHITIN_BRICKS, "Chiseled Chitin Bricks");
        addBlock(builder, AlienChitinBlocks.CHISELED_CHITIN_BRICKS_EMBRYO, "Chiseled Chitin Bricks (Embryo)");
        addBlock(builder, AlienChitinBlocks.POLISHED_CHITIN, "Polished Chitin");
        addBlock(builder, AlienChitinBlocks.POLISHED_CHITIN_SLAB, "Polished Chitin Slab");
        addBlock(builder, AlienChitinBlocks.POLISHED_CHITIN_STAIRS, "Polished Chitin Stairs");
        addBlock(builder, AlienChitinBlocks.POLISHED_CHITIN_WALL, "Polished Chitin Wall");

        addBlock(builder, NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK, "Block of Nether Chitin");
        addBlock(builder, NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK_SLAB, "Nether Chitin Slab");
        addBlock(builder, NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK_STAIRS, "Nether Chitin Stairs");
        addBlock(builder, NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK_WALL, "Nether Chitin Wall");
        addBlock(builder, NetherAlienChitinBlocks.NETHER_CHITIN_BRICKS, "Nether Chitin Bricks");
        addBlock(builder, NetherAlienChitinBlocks.NETHER_CHITIN_BRICK_SLAB, "Nether Chitin Brick Slab");
        addBlock(builder, NetherAlienChitinBlocks.NETHER_CHITIN_BRICK_STAIRS, "Nether Chitin Brick Stairs");
        addBlock(builder, NetherAlienChitinBlocks.NETHER_CHITIN_BRICK_WALL, "Nether Chitin Brick Wall");
        addBlock(builder, NetherAlienChitinBlocks.CHISELED_NETHER_CHITIN_BRICKS, "Chiseled Nether Chitin Bricks");
        addBlock(builder, NetherAlienChitinBlocks.CHISELED_NETHER_CHITIN_BRICKS_EMBRYO, "Chiseled Nether Chitin Bricks (Embryo)");
        addBlock(builder, NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN, "Polished Nether Chitin");
        addBlock(builder, NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN_SLAB, "Polished Nether Chitin Slab");
        addBlock(builder, NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN_STAIRS, "Polished Nether Chitin Stairs");
        addBlock(builder, NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN_WALL, "Polished Nether Chitin Wall");
    };

    private static void addBlock(
            FabricLanguageProvider.TranslationBuilder translationBuilder,
            Supplier<? extends Block> blockSupplier,
            String value
    ) {
        addBlock(translationBuilder, blockSupplier.get(), value);
    }

    private static void addBlock(FabricLanguageProvider.TranslationBuilder translationBuilder, Block block, String value) {
        TOUCHED_ENTRIES.add(block);
        translationBuilder.add(block, value);
    }
}