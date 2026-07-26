package com.alien.fabric.data.loot;

import com.alien.common.registry.init.block.AberrantAlienChitinBlocks;
import com.alien.common.registry.init.block.AberrantAlienResinBlocks;
import com.alien.common.registry.init.block.AlienBlocks;
import com.alien.common.registry.init.block.AlienChitinBlocks;
import com.alien.common.registry.init.block.AlienResinBlocks;
import com.alien.common.registry.init.block.IrradiatedAlienChitinBlocks;
import com.alien.common.registry.init.block.IrradiatedAlienResinBlocks;
import com.alien.common.registry.init.block.NetherAlienChitinBlocks;
import com.alien.common.registry.init.block.NetherAlienResinBlocks;
import com.alien.common.registry.init.item.AlienXenomorphHeadItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

public class BlockLootTableProvider extends FabricBlockLootTableProvider {

    private static final Set<Block> TOUCHED_ENTRIES = new HashSet<>();

    public BlockLootTableProvider(FabricDataOutput dataOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(dataOutput, registryLookup);
    }

    @Override
    public void generate() {
        generateOtherDrops();
        generateSelfDrops();
        generateSlabDrops();
    }

    private void generateOtherDrops() {
        dropOther(AberrantAlienResinBlocks.ABERRANT_RESIN_NODE, AberrantAlienResinBlocks.ABERRANT_RESIN);
        dropWhenShearsOrSilkTouch(AberrantAlienResinBlocks.ABERRANT_RESIN_VEIN);
        dropOther(AberrantAlienResinBlocks.ABERRANT_RESIN_VENT, AberrantAlienResinBlocks.ABERRANT_RESIN);
        dropWhenShearsOrSilkTouch(AberrantAlienResinBlocks.ABERRANT_RESIN_WEB);
        dropOther(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_NODE, IrradiatedAlienResinBlocks.IRRADIATED_RESIN);
        dropWhenShearsOrSilkTouch(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_VEIN);
        dropOther(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_VENT, IrradiatedAlienResinBlocks.IRRADIATED_RESIN);
        dropWhenShearsOrSilkTouch(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_WEB);
        dropOther(NetherAlienResinBlocks.NETHER_RESIN_NODE, NetherAlienResinBlocks.NETHER_RESIN);
        dropWhenShearsOrSilkTouch(NetherAlienResinBlocks.NETHER_RESIN_VEIN);
        dropOther(NetherAlienResinBlocks.NETHER_RESIN_VENT, NetherAlienResinBlocks.NETHER_RESIN);
        dropWhenShearsOrSilkTouch(NetherAlienResinBlocks.NETHER_RESIN_WEB);
        dropOther(AlienResinBlocks.RESIN_NODE, AlienResinBlocks.RESIN);
        dropWhenShearsOrSilkTouch(AlienResinBlocks.RESIN_VEIN);
        dropOther(AlienResinBlocks.RESIN_VENT, AlienResinBlocks.RESIN);
        dropWhenShearsOrSilkTouch(AlienResinBlocks.RESIN_WEB);

        // Head blocks (floor + wall) drop the matching head item. Both block forms map to the same
        // item — `StandingAndWallBlockItem` placement re-derives floor-vs-wall from the placement context.
        AlienXenomorphHeadItems.ALL.forEach(entry -> {
            dropOther(entry.standingBlock(), entry.head());
            dropOther(entry.wallBlock(), entry.head());
        });
    }

    private void generateSelfDrops() {
        dropSelf(AberrantAlienResinBlocks.ABERRANT_RESIN);
        dropSelf(AberrantAlienResinBlocks.ABERRANT_RESIN_BONE);
        dropSelf(AberrantAlienResinBlocks.ABERRANT_RESIN_BONE_STAIRS);
        dropSelf(AberrantAlienResinBlocks.ABERRANT_RESIN_DOORWAY);
        dropSelf(AberrantAlienResinBlocks.ABERRANT_RESIN_ETCHED);
        dropSelf(AberrantAlienResinBlocks.ABERRANT_RESIN_ETCHED_STAIRS);
        dropSelf(AberrantAlienResinBlocks.ABERRANT_RESIN_SPINE);
        dropSelf(AberrantAlienResinBlocks.ABERRANT_RESIN_STRETCHED);
        dropSelf(AberrantAlienResinBlocks.ABERRANT_RESIN_STRETCHED_STAIRS);
        dropSelf(AberrantAlienResinBlocks.ABERRANT_RESIN_TENDRIL);
        dropSelf(AberrantAlienResinBlocks.ABERRANT_RESIN_TENDRIL_STAIRS);
        dropSelf(AberrantAlienResinBlocks.ABERRANT_RESIN_BRICKS);
        dropSelf(AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_STAIRS);
        dropSelf(AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_WALL);
        dropSelf(AberrantAlienResinBlocks.ABERRANT_RESIN_STAIRS);
        dropSelf(AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK);
        dropSelf(AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK_STAIRS);
        dropSelf(AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK_WALL);
        dropSelf(AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICKS);
        dropSelf(AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICK_STAIRS);
        dropSelf(AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICK_WALL);
        dropSelf(AberrantAlienChitinBlocks.CHISELED_ABERRANT_CHITIN_BRICKS);
        dropSelf(AberrantAlienChitinBlocks.CHISELED_ABERRANT_CHITIN_BRICKS_EMBRYO);
        dropSelf(AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN);
        dropSelf(AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN_STAIRS);
        dropSelf(AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN_WALL);

        dropSelf(IrradiatedAlienResinBlocks.IRRADIATED_RESIN);
        dropSelf(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE);
        dropSelf(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE_STAIRS);
        dropSelf(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_DOORWAY);
        dropSelf(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED);
        dropSelf(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED_STAIRS);
        dropSelf(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_SPINE);
        dropSelf(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED);
        dropSelf(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED_STAIRS);
        dropSelf(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL);
        dropSelf(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL_STAIRS);
        dropSelf(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICKS);
        dropSelf(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_STAIRS);
        dropSelf(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_WALL);
        dropSelf(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STAIRS);
        dropSelf(IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK);
        dropSelf(IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK_STAIRS);
        dropSelf(IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK_WALL);
        dropSelf(IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICKS);
        dropSelf(IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICK_STAIRS);
        dropSelf(IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICK_WALL);
        dropSelf(IrradiatedAlienChitinBlocks.CHISELED_IRRADIATED_CHITIN_BRICKS);
        dropSelf(IrradiatedAlienChitinBlocks.CHISELED_IRRADIATED_CHITIN_BRICKS_EMBRYO);
        dropSelf(IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN);
        dropSelf(IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN_STAIRS);
        dropSelf(IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN_WALL);

        dropSelf(NetherAlienResinBlocks.NETHER_RESIN);
        dropSelf(NetherAlienResinBlocks.NETHER_RESIN_BONE);
        dropSelf(NetherAlienResinBlocks.NETHER_RESIN_BONE_STAIRS);
        dropSelf(NetherAlienResinBlocks.NETHER_RESIN_DOORWAY);
        dropSelf(NetherAlienResinBlocks.NETHER_RESIN_ETCHED);
        dropSelf(NetherAlienResinBlocks.NETHER_RESIN_ETCHED_STAIRS);
        dropSelf(NetherAlienResinBlocks.NETHER_RESIN_SPINE);
        dropSelf(NetherAlienResinBlocks.NETHER_RESIN_STRETCHED);
        dropSelf(NetherAlienResinBlocks.NETHER_RESIN_STRETCHED_STAIRS);
        dropSelf(NetherAlienResinBlocks.NETHER_RESIN_TENDRIL);
        dropSelf(NetherAlienResinBlocks.NETHER_RESIN_TENDRIL_STAIRS);
        dropSelf(NetherAlienResinBlocks.NETHER_RESIN_BRICKS);
        dropSelf(NetherAlienResinBlocks.NETHER_RESIN_BRICK_STAIRS);
        dropSelf(NetherAlienResinBlocks.NETHER_RESIN_BRICK_WALL);
        dropSelf(NetherAlienResinBlocks.NETHER_RESIN_STAIRS);
        dropSelf(NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK);
        dropSelf(NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK_STAIRS);
        dropSelf(NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK_WALL);
        dropSelf(NetherAlienChitinBlocks.NETHER_CHITIN_BRICKS);
        dropSelf(NetherAlienChitinBlocks.NETHER_CHITIN_BRICK_STAIRS);
        dropSelf(NetherAlienChitinBlocks.NETHER_CHITIN_BRICK_WALL);
        dropSelf(NetherAlienChitinBlocks.CHISELED_NETHER_CHITIN_BRICKS);
        dropSelf(NetherAlienChitinBlocks.CHISELED_NETHER_CHITIN_BRICKS_EMBRYO);
        dropSelf(NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN);
        dropSelf(NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN_STAIRS);
        dropSelf(NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN_WALL);

        dropSelf(AlienResinBlocks.RESIN);
        dropSelf(AlienResinBlocks.RESIN_BONE);
        dropSelf(AlienResinBlocks.RESIN_ETCHED);
        dropSelf(AlienResinBlocks.RESIN_STRETCHED);
        dropWhenSilkTouch(AlienBlocks.JELLY_VAT);
        dropSelf(AlienResinBlocks.RESIN_TENDRIL);
        dropSelf(AlienResinBlocks.RESIN_SPINE);
        dropSelf(AlienResinBlocks.RESIN_DOORWAY);
        dropSelf(AlienResinBlocks.RESIN_BRICKS);
        dropSelf(AlienResinBlocks.RESIN_BRICK_STAIRS);
        dropSelf(AlienResinBlocks.RESIN_BRICK_WALL);
        dropSelf(AlienResinBlocks.RESIN_STAIRS);
        dropSelf(AlienResinBlocks.RESIN_BONE_STAIRS);
        dropSelf(AlienResinBlocks.RESIN_ETCHED_STAIRS);
        dropSelf(AlienResinBlocks.RESIN_STRETCHED_STAIRS);
        dropSelf(AlienResinBlocks.RESIN_TENDRIL_STAIRS);
        dropSelf(AlienChitinBlocks.CHITIN_BLOCK);
        dropSelf(AlienChitinBlocks.CHITIN_BLOCK_STAIRS);
        dropSelf(AlienChitinBlocks.CHITIN_BLOCK_WALL);
        dropSelf(AlienChitinBlocks.CHITIN_BRICKS);
        dropSelf(AlienChitinBlocks.CHITIN_BRICK_STAIRS);
        dropSelf(AlienChitinBlocks.CHITIN_BRICK_WALL);
        dropSelf(AlienChitinBlocks.CHISELED_CHITIN_BRICKS);
        dropSelf(AlienChitinBlocks.CHISELED_CHITIN_BRICKS_EMBRYO);
        dropSelf(AlienChitinBlocks.POLISHED_CHITIN);
        dropSelf(AlienChitinBlocks.POLISHED_CHITIN_STAIRS);
        dropSelf(AlienChitinBlocks.POLISHED_CHITIN_WALL);

        dropSelf(AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN);
        dropSelf(AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN_STAIRS);
        dropSelf(IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN);
        dropSelf(IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN_STAIRS);
        dropSelf(NetherAlienResinBlocks.RIBBED_NETHER_RESIN);
        dropSelf(NetherAlienResinBlocks.RIBBED_NETHER_RESIN_STAIRS);
        dropSelf(AlienResinBlocks.RIBBED_RESIN);
        dropSelf(AlienResinBlocks.RIBBED_RESIN_STAIRS);

        dropSelf(AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN);
        dropSelf(AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_STAIRS);
        dropSelf(AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_WALL);
        dropSelf(IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN);
        dropSelf(IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_STAIRS);
        dropSelf(IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_WALL);
        dropSelf(NetherAlienResinBlocks.SMOOTH_NETHER_RESIN);
        dropSelf(NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_STAIRS);
        dropSelf(NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_WALL);
        dropSelf(AlienResinBlocks.SMOOTH_RESIN);
        dropSelf(AlienResinBlocks.SMOOTH_RESIN_STAIRS);
        dropSelf(AlienResinBlocks.SMOOTH_RESIN_WALL);

        dropSelf(AlienBlocks.ROYAL_JELLY_BLOCK);
    }

    private void generateSlabDrops() {
        dropSlab(AberrantAlienResinBlocks.ABERRANT_RESIN_SLAB);
        dropSlab(AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_SLAB);
        dropSlab(AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_SLAB);
        dropSlab(AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN_SLAB);
        dropSlab(AberrantAlienResinBlocks.ABERRANT_RESIN_BONE_SLAB);
        dropSlab(AberrantAlienResinBlocks.ABERRANT_RESIN_ETCHED_SLAB);
        dropSlab(AberrantAlienResinBlocks.ABERRANT_RESIN_STRETCHED_SLAB);
        dropSlab(AberrantAlienResinBlocks.ABERRANT_RESIN_TENDRIL_SLAB);

        dropSlab(AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK_SLAB);
        dropSlab(AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICK_SLAB);
        dropSlab(AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN_SLAB);

        dropSlab(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_SLAB);
        dropSlab(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_SLAB);
        dropSlab(IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_SLAB);

        dropSlab(IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK_SLAB);
        dropSlab(IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICK_SLAB);
        dropSlab(IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN_SLAB);
        dropSlab(IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN_SLAB);
        dropSlab(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE_SLAB);
        dropSlab(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED_SLAB);
        dropSlab(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED_SLAB);
        dropSlab(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL_SLAB);

        dropSlab(NetherAlienResinBlocks.NETHER_RESIN_SLAB);
        dropSlab(NetherAlienResinBlocks.NETHER_RESIN_BRICK_SLAB);
        dropSlab(NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_SLAB);
        dropSlab(NetherAlienResinBlocks.RIBBED_NETHER_RESIN_SLAB);
        dropSlab(NetherAlienResinBlocks.NETHER_RESIN_BONE_SLAB);
        dropSlab(NetherAlienResinBlocks.NETHER_RESIN_ETCHED_SLAB);
        dropSlab(NetherAlienResinBlocks.NETHER_RESIN_STRETCHED_SLAB);
        dropSlab(NetherAlienResinBlocks.NETHER_RESIN_TENDRIL_SLAB);

        dropSlab(NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK_SLAB);
        dropSlab(NetherAlienChitinBlocks.NETHER_CHITIN_BRICK_SLAB);
        dropSlab(NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN_SLAB);

        dropSlab(AlienResinBlocks.RESIN_SLAB);
        dropSlab(AlienResinBlocks.RIBBED_RESIN_SLAB);
        dropSlab(AlienResinBlocks.RESIN_BONE_SLAB);
        dropSlab(AlienResinBlocks.RESIN_ETCHED_SLAB);
        dropSlab(AlienResinBlocks.RESIN_STRETCHED_SLAB);
        dropSlab(AlienResinBlocks.RESIN_TENDRIL_SLAB);
        dropSlab(AlienResinBlocks.RESIN_BRICK_SLAB);
        dropSlab(AlienResinBlocks.SMOOTH_RESIN_SLAB);

        dropSlab(AlienChitinBlocks.CHITIN_BLOCK_SLAB);
        dropSlab(AlienChitinBlocks.CHITIN_BRICK_SLAB);
        dropSlab(AlienChitinBlocks.POLISHED_CHITIN_SLAB);
    }

    public void add(Supplier<? extends Block> blockSupplier, Function<Block, LootTable.Builder> factory) {
        var block = blockSupplier.get();
        add(block, factory);
        TOUCHED_ENTRIES.add(block);
    }

    public void dropOther(Supplier<? extends Block> blockSupplier, Supplier<? extends ItemLike> itemLikeSupplier) {
        var block = blockSupplier.get();
        dropOther(block, itemLikeSupplier.get());
        TOUCHED_ENTRIES.add(block);
    }

    public void dropWhenSilkTouch(Supplier<? extends Block> blockSupplier) {
        var block = blockSupplier.get();
        dropWhenSilkTouch(block);
        TOUCHED_ENTRIES.add(block);
    }

    /** Leaves-style harvesting: the block drops itself when broken with shears OR a silk-touch tool, else nothing. */
    public void dropWhenShearsOrSilkTouch(Supplier<? extends Block> blockSupplier) {
        var block = blockSupplier.get();
        add(
            block,
            LootTable.lootTable()
                .withPool(
                    net.minecraft.world.level.storage.loot.LootPool.lootPool()
                        .setRolls(net.minecraft.world.level.storage.loot.providers.number.ConstantValue.exactly(1.0F))
                        .when(HAS_SHEARS.or(hasSilkTouch()))
                        .add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(block))
                )
        );
        TOUCHED_ENTRIES.add(block);
    }

    public void dropSelf(Supplier<? extends Block> blockSupplier) {
        var block = blockSupplier.get();
        dropSelf(block);
        TOUCHED_ENTRIES.add(block);
    }

    public void dropSlab(Supplier<? extends Block> blockSupplier) {
        var block = blockSupplier.get();
        add(block, createSlabItemTable(block));
        TOUCHED_ENTRIES.add(block);
    }
}
