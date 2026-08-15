package com.alien.fabric.data.recipe.impl.resin;

import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

record DecorativeResinSet(
    Supplier<Block> resinBlock, // the input you cut from
    Supplier<Block> doorway,
    Supplier<Block> spine,
    Supplier<Block> ribbed,
    Supplier<Block> ribbedSlab,
    Supplier<Block> ribbedStairs,
    Supplier<Block> bone,
    Supplier<Block> boneSlab,
    Supplier<Block> boneStairs,
    Supplier<Block> etched,
    Supplier<Block> etchedSlab,
    Supplier<Block> etchedStairs,
    Supplier<Block> stretched,
    Supplier<Block> stretchedSlab,
    Supplier<Block> stretchedStairs,
    Supplier<Block> tendril,
    Supplier<Block> tendrilSlab,
    Supplier<Block> tendrilStairs
    // ...and the slab/stairs for whichever 4th block has them
) {}
