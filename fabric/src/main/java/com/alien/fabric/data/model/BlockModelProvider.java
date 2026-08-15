package com.alien.fabric.data.model;

import com.alien.common.registry.init.block.AberrantAlienChitinBlocks;
import com.alien.common.registry.init.block.AberrantAlienResinBlocks;
import com.alien.common.registry.init.block.AlienBlocks;
import com.alien.common.registry.init.block.AlienChitinBlocks;
import com.alien.common.registry.init.block.AlienResinBlocks;
import com.alien.common.registry.init.block.IrradiatedAlienChitinBlocks;
import com.alien.common.registry.init.block.IrradiatedAlienResinBlocks;
import com.alien.common.registry.init.block.NetherAlienChitinBlocks;
import com.alien.common.registry.init.block.NetherAlienResinBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.models.BlockModelGenerators;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.models.model.ModelTemplates;
import net.minecraft.data.models.model.TextureMapping;
import net.minecraft.data.models.model.TextureSlot;
import net.minecraft.data.models.model.TexturedModel;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

public class BlockModelProvider extends FabricModelProvider {

    public BlockModelProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockModelGenerators generators) {
        createFullyRotatedVariantBlock(generators, IrradiatedAlienResinBlocks.IRRADIATED_RESIN.get());
        createSlab(generators, IrradiatedAlienResinBlocks.IRRADIATED_RESIN.get(), IrradiatedAlienResinBlocks.IRRADIATED_RESIN_SLAB.get());
        createStairs(
            generators,
            IrradiatedAlienResinBlocks.IRRADIATED_RESIN.get(),
            IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STAIRS.get()
        );
        createSlab(
            generators,
            IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE.get(),
            IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE_SLAB.get()
        );
        createStairs(
            generators,
            IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE.get(),
            IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE_STAIRS.get()
        );
        createSlab(
            generators,
            IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED.get(),
            IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED_SLAB.get()
        );
        createStairs(
            generators,
            IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED.get(),
            IrradiatedAlienResinBlocks.IRRADIATED_RESIN_ETCHED_STAIRS.get()
        );
        createSlab(
            generators,
            IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED.get(),
            IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED_SLAB.get()
        );
        createStairs(
            generators,
            IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED.get(),
            IrradiatedAlienResinBlocks.IRRADIATED_RESIN_STRETCHED_STAIRS.get()
        );
        createSlab(
            generators,
            IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL.get(),
            IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL_SLAB.get()
        );
        createStairs(
            generators,
            IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL.get(),
            IrradiatedAlienResinBlocks.IRRADIATED_RESIN_TENDRIL_STAIRS.get()
        );
        createSlab(
            generators,
            IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN.get(),
            IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN_SLAB.get()
        );
        createStairs(
            generators,
            IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN.get(),
            IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN_STAIRS.get()
        );
        generators.createRotatedVariantBlock(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_NODE.get());
        generators.createCrossBlock(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_WEB.get(), BlockModelGenerators.TintState.NOT_TINTED);

        generators.family(IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK.get())
            .slab(IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK_SLAB.get())
            .stairs(IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK_STAIRS.get())
            .wall(IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BLOCK_WALL.get());
        generators.family(IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICKS.get())
            .slab(IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICK_SLAB.get())
            .stairs(IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICK_STAIRS.get())
            .wall(IrradiatedAlienChitinBlocks.IRRADIATED_CHITIN_BRICK_WALL.get());
        createBottomTopBlock(
            generators,
            IrradiatedAlienChitinBlocks.CHISELED_IRRADIATED_CHITIN_BRICKS.get(),
            IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN.get()
        );
        createBottomTopBlock(
            generators,
            IrradiatedAlienChitinBlocks.CHISELED_IRRADIATED_CHITIN_BRICKS_EMBRYO.get(),
            IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN.get()
        );
        generators.family(IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN.get())
            .slab(IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN_SLAB.get())
            .stairs(IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN_STAIRS.get())
            .wall(IrradiatedAlienChitinBlocks.POLISHED_IRRADIATED_CHITIN_WALL.get());

        createFullyRotatedVariantBlock(generators, AberrantAlienResinBlocks.ABERRANT_RESIN.get());
        createSlab(generators, AberrantAlienResinBlocks.ABERRANT_RESIN.get(), AberrantAlienResinBlocks.ABERRANT_RESIN_SLAB.get());
        createStairs(generators, AberrantAlienResinBlocks.ABERRANT_RESIN.get(), AberrantAlienResinBlocks.ABERRANT_RESIN_STAIRS.get());
        createSlab(generators, AberrantAlienResinBlocks.ABERRANT_RESIN_BONE.get(), AberrantAlienResinBlocks.ABERRANT_RESIN_BONE_SLAB.get());
        createStairs(
            generators,
            AberrantAlienResinBlocks.ABERRANT_RESIN_BONE.get(),
            AberrantAlienResinBlocks.ABERRANT_RESIN_BONE_STAIRS.get()
        );
        createSlab(
            generators,
            AberrantAlienResinBlocks.ABERRANT_RESIN_ETCHED.get(),
            AberrantAlienResinBlocks.ABERRANT_RESIN_ETCHED_SLAB.get()
        );
        createStairs(
            generators,
            AberrantAlienResinBlocks.ABERRANT_RESIN_ETCHED.get(),
            AberrantAlienResinBlocks.ABERRANT_RESIN_ETCHED_STAIRS.get()
        );
        createSlab(
            generators,
            AberrantAlienResinBlocks.ABERRANT_RESIN_STRETCHED.get(),
            AberrantAlienResinBlocks.ABERRANT_RESIN_STRETCHED_SLAB.get()
        );
        createStairs(
            generators,
            AberrantAlienResinBlocks.ABERRANT_RESIN_STRETCHED.get(),
            AberrantAlienResinBlocks.ABERRANT_RESIN_STRETCHED_STAIRS.get()
        );
        createSlab(
            generators,
            AberrantAlienResinBlocks.ABERRANT_RESIN_TENDRIL.get(),
            AberrantAlienResinBlocks.ABERRANT_RESIN_TENDRIL_SLAB.get()
        );
        createStairs(
            generators,
            AberrantAlienResinBlocks.ABERRANT_RESIN_TENDRIL.get(),
            AberrantAlienResinBlocks.ABERRANT_RESIN_TENDRIL_STAIRS.get()
        );
        createSlab(
            generators,
            AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN.get(),
            AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN_SLAB.get()
        );
        createStairs(
            generators,
            AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN.get(),
            AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN_STAIRS.get()
        );
        generators.createRotatedVariantBlock(AberrantAlienResinBlocks.ABERRANT_RESIN_NODE.get());
        generators.createCrossBlock(AberrantAlienResinBlocks.ABERRANT_RESIN_WEB.get(), BlockModelGenerators.TintState.NOT_TINTED);

        generators.family(AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK.get())
            .slab(AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK_SLAB.get())
            .stairs(AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK_STAIRS.get())
            .wall(AberrantAlienChitinBlocks.ABERRANT_CHITIN_BLOCK_WALL.get());
        generators.family(AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICKS.get())
            .slab(AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICK_SLAB.get())
            .stairs(AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICK_STAIRS.get())
            .wall(AberrantAlienChitinBlocks.ABERRANT_CHITIN_BRICK_WALL.get());
        createBottomTopBlock(
            generators,
            AberrantAlienChitinBlocks.CHISELED_ABERRANT_CHITIN_BRICKS.get(),
            AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN.get()
        );
        createBottomTopBlock(
            generators,
            AberrantAlienChitinBlocks.CHISELED_ABERRANT_CHITIN_BRICKS_EMBRYO.get(),
            AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN.get()
        );
        generators.family(AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN.get())
            .slab(AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN_SLAB.get())
            .stairs(AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN_STAIRS.get())
            .wall(AberrantAlienChitinBlocks.POLISHED_ABERRANT_CHITIN_WALL.get());

        createFullyRotatedVariantBlock(generators, NetherAlienResinBlocks.NETHER_RESIN.get());
        createSlab(generators, NetherAlienResinBlocks.NETHER_RESIN.get(), NetherAlienResinBlocks.NETHER_RESIN_SLAB.get());
        createStairs(generators, NetherAlienResinBlocks.NETHER_RESIN.get(), NetherAlienResinBlocks.NETHER_RESIN_STAIRS.get());
        createSlab(generators, NetherAlienResinBlocks.NETHER_RESIN_BONE.get(), NetherAlienResinBlocks.NETHER_RESIN_BONE_SLAB.get());
        createStairs(generators, NetherAlienResinBlocks.NETHER_RESIN_BONE.get(), NetherAlienResinBlocks.NETHER_RESIN_BONE_STAIRS.get());
        createSlab(generators, NetherAlienResinBlocks.NETHER_RESIN_ETCHED.get(), NetherAlienResinBlocks.NETHER_RESIN_ETCHED_SLAB.get());
        createStairs(generators, NetherAlienResinBlocks.NETHER_RESIN_ETCHED.get(), NetherAlienResinBlocks.NETHER_RESIN_ETCHED_STAIRS.get());
        createSlab(
            generators,
            NetherAlienResinBlocks.NETHER_RESIN_STRETCHED.get(),
            NetherAlienResinBlocks.NETHER_RESIN_STRETCHED_SLAB.get()
        );
        createStairs(
            generators,
            NetherAlienResinBlocks.NETHER_RESIN_STRETCHED.get(),
            NetherAlienResinBlocks.NETHER_RESIN_STRETCHED_STAIRS.get()
        );
        createSlab(generators, NetherAlienResinBlocks.NETHER_RESIN_TENDRIL.get(), NetherAlienResinBlocks.NETHER_RESIN_TENDRIL_SLAB.get());
        createStairs(
            generators,
            NetherAlienResinBlocks.NETHER_RESIN_TENDRIL.get(),
            NetherAlienResinBlocks.NETHER_RESIN_TENDRIL_STAIRS.get()
        );
        createSlab(generators, NetherAlienResinBlocks.RIBBED_NETHER_RESIN.get(), NetherAlienResinBlocks.RIBBED_NETHER_RESIN_SLAB.get());
        createStairs(generators, NetherAlienResinBlocks.RIBBED_NETHER_RESIN.get(), NetherAlienResinBlocks.RIBBED_NETHER_RESIN_STAIRS.get());
        generators.createRotatedVariantBlock(NetherAlienResinBlocks.NETHER_RESIN_NODE.get());
        generators.createCrossBlock(NetherAlienResinBlocks.NETHER_RESIN_WEB.get(), BlockModelGenerators.TintState.NOT_TINTED);

        generators.family(NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK.get())
            .slab(NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK_SLAB.get())
            .stairs(NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK_STAIRS.get())
            .wall(NetherAlienChitinBlocks.NETHER_CHITIN_BLOCK_WALL.get());
        generators.family(NetherAlienChitinBlocks.NETHER_CHITIN_BRICKS.get())
            .slab(NetherAlienChitinBlocks.NETHER_CHITIN_BRICK_SLAB.get())
            .stairs(NetherAlienChitinBlocks.NETHER_CHITIN_BRICK_STAIRS.get())
            .wall(NetherAlienChitinBlocks.NETHER_CHITIN_BRICK_WALL.get());
        createBottomTopBlock(
            generators,
            NetherAlienChitinBlocks.CHISELED_NETHER_CHITIN_BRICKS.get(),
            NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN.get()
        );
        createBottomTopBlock(
            generators,
            NetherAlienChitinBlocks.CHISELED_NETHER_CHITIN_BRICKS_EMBRYO.get(),
            NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN.get()
        );
        generators.family(NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN.get())
            .slab(NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN_SLAB.get())
            .stairs(NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN_STAIRS.get())
            .wall(NetherAlienChitinBlocks.POLISHED_NETHER_CHITIN_WALL.get());

        createSlab(generators, AlienResinBlocks.RESIN.get(), AlienResinBlocks.RESIN_SLAB.get());
        createStairs(generators, AlienResinBlocks.RESIN.get(), AlienResinBlocks.RESIN_STAIRS.get());
        createSlab(generators, AlienResinBlocks.RESIN_TENDRIL.get(), AlienResinBlocks.RESIN_TENDRIL_SLAB.get());
        createStairs(generators, AlienResinBlocks.RESIN_TENDRIL.get(), AlienResinBlocks.RESIN_TENDRIL_STAIRS.get());
        createSlab(generators, AlienResinBlocks.RESIN_ETCHED.get(), AlienResinBlocks.RESIN_ETCHED_SLAB.get());
        createStairs(generators, AlienResinBlocks.RESIN_ETCHED.get(), AlienResinBlocks.RESIN_ETCHED_STAIRS.get());
        createSlab(generators, AlienResinBlocks.RESIN_BONE.get(), AlienResinBlocks.RESIN_BONE_SLAB.get());
        createStairs(generators, AlienResinBlocks.RESIN_BONE.get(), AlienResinBlocks.RESIN_BONE_STAIRS.get());
        createSlab(generators, AlienResinBlocks.RESIN_STRETCHED.get(), AlienResinBlocks.RESIN_STRETCHED_SLAB.get());
        createStairs(generators, AlienResinBlocks.RESIN_STRETCHED.get(), AlienResinBlocks.RESIN_STRETCHED_STAIRS.get());
        createSlab(generators, AlienResinBlocks.RIBBED_RESIN.get(), AlienResinBlocks.RIBBED_RESIN_SLAB.get());
        createStairs(generators, AlienResinBlocks.RIBBED_RESIN.get(), AlienResinBlocks.RIBBED_RESIN_STAIRS.get());
        generators.createRotatedVariantBlock(AlienResinBlocks.RESIN_NODE.get());
        generators.createCrossBlock(AlienResinBlocks.RESIN_WEB.get(), BlockModelGenerators.TintState.NOT_TINTED);

        generators.family(AlienChitinBlocks.CHITIN_BLOCK.get())
            .slab(AlienChitinBlocks.CHITIN_BLOCK_SLAB.get())
            .stairs(AlienChitinBlocks.CHITIN_BLOCK_STAIRS.get())
            .wall(AlienChitinBlocks.CHITIN_BLOCK_WALL.get());
        generators.family(AlienChitinBlocks.CHITIN_BRICKS.get())
            .slab(AlienChitinBlocks.CHITIN_BRICK_SLAB.get())
            .stairs(AlienChitinBlocks.CHITIN_BRICK_STAIRS.get())
            .wall(AlienChitinBlocks.CHITIN_BRICK_WALL.get());
        createBottomTopBlock(generators, AlienChitinBlocks.CHISELED_CHITIN_BRICKS.get(), AlienChitinBlocks.POLISHED_CHITIN.get());
        createBottomTopBlock(generators, AlienChitinBlocks.CHISELED_CHITIN_BRICKS_EMBRYO.get(), AlienChitinBlocks.POLISHED_CHITIN.get());
        generators.family(AlienChitinBlocks.POLISHED_CHITIN.get())
            .slab(AlienChitinBlocks.POLISHED_CHITIN_SLAB.get())
            .stairs(AlienChitinBlocks.POLISHED_CHITIN_STAIRS.get())
            .wall(AlienChitinBlocks.POLISHED_CHITIN_WALL.get());

        generators.createNonTemplateModelBlock(AlienBlocks.ROYAL_JELLY_BLOCK.get());
        generators.createNonTemplateModelBlock(AlienBlocks.SCOURGE_JELLY_BLOCK.get());
        generators.createNonTemplateModelBlock(AlienBlocks.IRRADIATED_JELLY_BLOCK.get());

        generators.family(AberrantAlienResinBlocks.ABERRANT_RESIN_BRICKS.get())
            .slab(AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_SLAB.get())
            .stairs(AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_STAIRS.get())
            .wall(AberrantAlienResinBlocks.ABERRANT_RESIN_BRICK_WALL.get());
        generators.createTrivialCube(AberrantAlienResinBlocks.ABERRANT_RESIN_VENT.get());

        generators.family(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICKS.get())
            .slab(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_SLAB.get())
            .stairs(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_STAIRS.get())
            .wall(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BRICK_WALL.get());
        generators.createTrivialCube(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_VENT.get());

        generators.family(NetherAlienResinBlocks.NETHER_RESIN_BRICKS.get())
            .slab(NetherAlienResinBlocks.NETHER_RESIN_BRICK_SLAB.get())
            .stairs(NetherAlienResinBlocks.NETHER_RESIN_BRICK_STAIRS.get())
            .wall(NetherAlienResinBlocks.NETHER_RESIN_BRICK_WALL.get());
        generators.createTrivialCube(NetherAlienResinBlocks.NETHER_RESIN_VENT.get());

        generators.family(AlienResinBlocks.RESIN_BRICKS.get())
            .slab(AlienResinBlocks.RESIN_BRICK_SLAB.get())
            .stairs(AlienResinBlocks.RESIN_BRICK_STAIRS.get())
            .wall(AlienResinBlocks.RESIN_BRICK_WALL.get());
        generators.createTrivialCube(AlienResinBlocks.RESIN_VENT.get());

        createRotatedPillar(generators, AberrantAlienResinBlocks.RIBBED_ABERRANT_RESIN.get(), TexturedModel.CUBE);
        createRotatedPillar(generators, IrradiatedAlienResinBlocks.RIBBED_IRRADIATED_RESIN.get(), TexturedModel.CUBE);
        createRotatedPillar(generators, NetherAlienResinBlocks.RIBBED_NETHER_RESIN.get(), TexturedModel.CUBE);

        generators.family(AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN.get())
            .slab(AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_SLAB.get())
            .stairs(AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_STAIRS.get())
            .wall(AberrantAlienResinBlocks.SMOOTH_ABERRANT_RESIN_WALL.get());

        generators.family(IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN.get())
            .slab(IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_SLAB.get())
            .stairs(IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_STAIRS.get())
            .wall(IrradiatedAlienResinBlocks.SMOOTH_IRRADIATED_RESIN_WALL.get());

        generators.family(NetherAlienResinBlocks.SMOOTH_NETHER_RESIN.get())
            .slab(NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_SLAB.get())
            .stairs(NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_STAIRS.get())
            .wall(NetherAlienResinBlocks.SMOOTH_NETHER_RESIN_WALL.get());

        generators.family(AlienResinBlocks.SMOOTH_RESIN.get())
            .slab(AlienResinBlocks.SMOOTH_RESIN_SLAB.get())
            .stairs(AlienResinBlocks.SMOOTH_RESIN_STAIRS.get())
            .wall(AlienResinBlocks.SMOOTH_RESIN_WALL.get());
    }

    @Override
    public void generateItemModels(ItemModelGenerators generators) {}

    @Override
    public @NotNull String getName() {
        return "Block Model Definitions";
    }

    private void createBottomTopBlock(BlockModelGenerators generators, Block block, Block yBlock) {
        var yResourceLocation = ModelLocationUtils.getModelLocation(yBlock);
        var chiseledTextureResourceLocation = BuiltInRegistries.BLOCK.getKey(block).withPrefix("block/").withSuffix("_side");

        var textureMapping = TextureMapping.cube(block)
            .put(TextureSlot.BOTTOM, yResourceLocation)
            .put(TextureSlot.SIDE, chiseledTextureResourceLocation)
            .put(TextureSlot.TOP, yResourceLocation);

        generators.createTrivialBlock(block, textureMapping, ModelTemplates.CUBE_BOTTOM_TOP);
    }

    /**
     * Emits a cube blockstate randomised across ALL SIXTEEN orientations - every combination of x and y rotation -
     * rather than vanilla's four.
     * <p>
     * [stated] "aberrant resin, nether resin, and irradiated resin ... should have the same rotations as the normal
     * resin." Normal resin is hand-authored in resources with 16 variants precisely because vanilla cannot express
     * this: {@code BlockModelGenerators.createRotatedVariantBlock} only ever varies y, so the three strain resins were
     * generating 4 variants and tiling far more obviously than the normal strain beside them.
     * <p>
     * Generated rather than hand-authored on purpose. A manual copy in resources would collide with the datagen output
     * on the same resource path - the same duplicate-root problem that had 120 files disagreeing - because the model
     * and the blockstate come out of one vanilla call and cannot be split apart.
     * <p>
     * The block item model is unaffected: vanilla emits {@code {"parent": <block model>}} for BlockItems at the end of
     * its own run, not from this call.
     */
    private void createFullyRotatedVariantBlock(BlockModelGenerators generators, Block block) {
        var model = TexturedModel.CUBE.create(block, generators.modelOutput);
        var rotations = new VariantProperties.Rotation[] {
            VariantProperties.Rotation.R0,
            VariantProperties.Rotation.R90,
            VariantProperties.Rotation.R180,
            VariantProperties.Rotation.R270
        };

        var variants = new Variant[rotations.length * rotations.length];
        var index = 0;

        for (var xRotation : rotations) {
            for (var yRotation : rotations) {
                variants[index++] = Variant.variant()
                    .with(VariantProperties.MODEL, model)
                    .with(VariantProperties.X_ROT, xRotation)
                    .with(VariantProperties.Y_ROT, yRotation);
            }
        }

        generators.blockStateOutput.accept(MultiVariantGenerator.multiVariant(block, variants));
    }

    private void createRotatedPillar(BlockModelGenerators generators, Block rotatedPillarBlock, TexturedModel.Provider modelProvider) {
        var resourceLocation = modelProvider.create(rotatedPillarBlock, generators.modelOutput);
        generators.blockStateOutput.accept(
            BlockModelGenerators.createRotatedPillarWithHorizontalVariant(rotatedPillarBlock, resourceLocation, resourceLocation)
        );
    }

    private void createSlab(
        BlockModelGenerators generators,
        Block baseBlock,
        Block slabBlock
    ) {
        var resourceLocation = ModelLocationUtils.getModelLocation(baseBlock);
        var textureMapping = TextureMapping.cube(baseBlock)
            .put(TextureSlot.BOTTOM, resourceLocation)
            .put(TextureSlot.TOP, resourceLocation);

        var bottom = ModelTemplates.SLAB_BOTTOM.create(slabBlock, textureMapping, generators.modelOutput);
        var top = ModelTemplates.SLAB_TOP.create(slabBlock, textureMapping, generators.modelOutput);

        generators.blockStateOutput.accept(
            BlockModelGenerators.createSlab(slabBlock, bottom, top, resourceLocation)
        );
    }

    private void createStairs(BlockModelGenerators generators, Block baseBlock, Block stairsBlock) {
        var resourceLocation = ModelLocationUtils.getModelLocation(baseBlock);

        var textureMapping = TextureMapping.cube(baseBlock)
            .put(TextureSlot.BOTTOM, resourceLocation)
            .put(TextureSlot.TOP, resourceLocation);

        var innerResourceLocation = ModelTemplates.STAIRS_INNER.create(stairsBlock, textureMapping, generators.modelOutput);
        var straightResourceLocation = ModelTemplates.STAIRS_STRAIGHT.create(stairsBlock, textureMapping, generators.modelOutput);
        var outerResourceLocation = ModelTemplates.STAIRS_OUTER.create(stairsBlock, textureMapping, generators.modelOutput);

        generators.blockStateOutput.accept(
            BlockModelGenerators.createStairs(stairsBlock, innerResourceLocation, straightResourceLocation, outerResourceLocation)
        );
    }
}
