package com.alien.fabric.common;

import com.alien.common.registry.init.block.AberrantAlienResinBlocks;
import com.alien.common.registry.init.block.AlienResinBlocks;
import com.alien.common.registry.init.block.IrradiatedAlienResinBlocks;
import com.blib.api.common.registry.v1.BLibHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;

/**
 * Registers fire behavior for the full resin families of the normal, aberrant, and irradiated strains, so fire can
 * consume any family block and trigger the basalt conversion in {@code MixinBlockBehaviour_ResinConversion}. The nether
 * resin family is deliberately absent: nether resin takes no fire damage at all.
 */
public class FlammableBlockRegistry {

    public static void initialize() {
        var fireBlock = (FireBlock) Blocks.FIRE;

        setFlammable(fireBlock, AlienResinBlocks.RESIN);
        setFlammable(fireBlock, AlienResinBlocks.RESIN_BRICKS);
        setFlammable(fireBlock, AlienResinBlocks.RESIN_BRICK_SLAB);
        setFlammable(fireBlock, AlienResinBlocks.RESIN_BRICK_STAIRS);
        setFlammable(fireBlock, AlienResinBlocks.RESIN_BRICK_WALL);
        setFlammable(fireBlock, AlienResinBlocks.RESIN_NODE);
        setFlammable(fireBlock, AlienResinBlocks.RESIN_SLAB);
        setFlammable(fireBlock, AlienResinBlocks.RESIN_STAIRS);
        setFlammable(fireBlock, AlienResinBlocks.RESIN_VEIN);
        setFlammable(fireBlock, AlienResinBlocks.RESIN_VENT);
        setFlammable(fireBlock, AlienResinBlocks.RESIN_WEB);
        setFlammable(fireBlock, AlienResinBlocks.RIBBED_RESIN);
        setFlammable(fireBlock, AlienResinBlocks.RIBBED_RESIN_SLAB);
        setFlammable(fireBlock, AlienResinBlocks.RIBBED_RESIN_STAIRS);
        setFlammable(fireBlock, AlienResinBlocks.SMOOTH_RESIN);
        setFlammable(fireBlock, AlienResinBlocks.SMOOTH_RESIN_SLAB);
        setFlammable(fireBlock, AlienResinBlocks.SMOOTH_RESIN_STAIRS);
        setFlammable(fireBlock, AlienResinBlocks.SMOOTH_RESIN_WALL);
        setFlammable(fireBlock, AlienResinBlocks.RESIN_BONE);
        setFlammable(fireBlock, AlienResinBlocks.RESIN_BONE_SLAB);
        setFlammable(fireBlock, AlienResinBlocks.RESIN_BONE_STAIRS);
        setFlammable(fireBlock, AlienResinBlocks.RESIN_DOORWAY);
        setFlammable(fireBlock, AlienResinBlocks.RESIN_ETCHED);
        setFlammable(fireBlock, AlienResinBlocks.RESIN_ETCHED_SLAB);
        setFlammable(fireBlock, AlienResinBlocks.RESIN_ETCHED_STAIRS);
        setFlammable(fireBlock, AlienResinBlocks.RESIN_SPINE);
        setFlammable(fireBlock, AlienResinBlocks.RESIN_STRETCHED);
        setFlammable(fireBlock, AlienResinBlocks.RESIN_STRETCHED_SLAB);
        setFlammable(fireBlock, AlienResinBlocks.RESIN_STRETCHED_STAIRS);
        setFlammable(fireBlock, AlienResinBlocks.RESIN_TENDRIL);
        setFlammable(fireBlock, AlienResinBlocks.RESIN_TENDRIL_SLAB);
        setFlammable(fireBlock, AlienResinBlocks.RESIN_TENDRIL_STAIRS);

        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN);
        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN_BRICKS);
        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_SLAB);
        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_STAIRS);
        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_WALL);
        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN_NODE);
        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN_SLAB);
        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN_STAIRS);
        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN_VEIN);
        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN_VENT);
        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN_WEB);
        setFlammable(fireBlock, AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN);
        setFlammable(fireBlock, AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN_SLAB);
        setFlammable(fireBlock, AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN_STAIRS);
        setFlammable(fireBlock, AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN);
        setFlammable(fireBlock, AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_SLAB);
        setFlammable(fireBlock, AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_STAIRS);
        setFlammable(fireBlock, AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_WALL);
        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN_BONE);
        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN_BONE_SLAB);
        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN_BONE_STAIRS);
        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN_DOORWAY);
        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN_ETCHED);
        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN_ETCHED_SLAB);
        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN_ETCHED_STAIRS);
        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN_SPINE);
        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN_STRETCHED);
        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN_STRETCHED_SLAB);
        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN_STRETCHED_STAIRS);
        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN_TENDRIL);
        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN_TENDRIL_SLAB);
        setFlammable(fireBlock, AberrantAlienResinBlocks.ABERRANT_RESIN_TENDRIL_STAIRS);

        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICKS);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_SLAB);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_STAIRS);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_WALL);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_NODE);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_SLAB);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STAIRS);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_VEIN);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_VENT);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_WEB);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN_SLAB);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN_STAIRS);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_SLAB);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_STAIRS);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_WALL);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE_SLAB);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE_STAIRS);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_DOORWAY);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED_SLAB);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED_STAIRS);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_SPINE);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED_SLAB);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED_STAIRS);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL_SLAB);
        setFlammable(fireBlock, IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL_STAIRS);
    }

    /**
     * Resin BURNS but never CARRIES fire.
     * <p>
     * The two numbers are vanilla's and they do different jobs. IGNITE ODDS is how readily fire propagates ONTO a
     * position because of this block - at anything above zero, a hive's own walls become a fuse and one torch runs the
     * length of a corridor. BURN ODDS is how readily the block itself is consumed while fire is already touching it,
     * and consuming it is what triggers the basalt conversion in {@code MixinBlockBehaviour_ResinConversion}.
     * <p>
     * So: ZERO ignite, so nothing spreads; 20 burn - vanilla's wood rate - so the one block actually alight is eaten
     * and turns to basalt. [stated] "the fire shouldnt spread, it should only burn and convert the one block."
     * <p>
     * For scale, vanilla wood is {@code 5, 20} and wool is {@code 30, 60}. This was {@code 1, 20}: a one-in-a-hundred
     * chance per attempt is small, but over a whole hive of resin it is not nothing, which is what made fire crawl.
     */
    private static void setFlammable(FireBlock fireBlock, BLibHolder<? extends Block> holder) {
        fireBlock.setFlammable(holder.get(), 0, 20);
    }
}
