package com.alien.neoforge.client.render;

import com.alien.AlienResources;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public class ResinAlphaBakedModel extends BakedModelWrapper<BakedModel> {

    private static final int RESIN_ALPHA = (int) (0.75F * 255.0F);

    private static final int RESIN_ALPHA_MASK = RESIN_ALPHA << 24;

    private static final Set<ResourceLocation> RESIN_MODEL_IDS = Set.of(
        AlienResources.location("aberrant_resin_vein"),
        AlienResources.location("aberrant_resin_vein_2"),
        AlienResources.location("aberrant_resin_vein_3"),
        AlienResources.location("aberrant_resin_vein_4"),
        AlienResources.location("aberrant_resin_web"),
        AlienResources.location("block/aberrant_resin_vein"),
        AlienResources.location("block/aberrant_resin_vein_2"),
        AlienResources.location("block/aberrant_resin_vein_3"),
        AlienResources.location("block/aberrant_resin_vein_4"),
        AlienResources.location("block/aberrant_resin_web"),
        AlienResources.location("block/irradiated_resin_vein"),
        AlienResources.location("block/irradiated_resin_vein_2"),
        AlienResources.location("block/irradiated_resin_vein_3"),
        AlienResources.location("block/irradiated_resin_vein_4"),
        AlienResources.location("block/irradiated_resin_web"),
        AlienResources.location("block/nether_resin_vein"),
        AlienResources.location("block/nether_resin_vein_2"),
        AlienResources.location("block/nether_resin_vein_3"),
        AlienResources.location("block/nether_resin_vein_4"),
        AlienResources.location("block/nether_resin_web"),
        AlienResources.location("block/resin_vein"),
        AlienResources.location("block/resin_vein_1"),
        AlienResources.location("block/resin_vein_2"),
        AlienResources.location("block/resin_vein_3"),
        AlienResources.location("block/resin_vein_4"),
        AlienResources.location("block/resin_web"),
        AlienResources.location("irradiated_resin_vein"),
        AlienResources.location("irradiated_resin_vein_2"),
        AlienResources.location("irradiated_resin_vein_3"),
        AlienResources.location("irradiated_resin_vein_4"),
        AlienResources.location("irradiated_resin_web"),
        AlienResources.location("item/aberrant_resin_web"),
        AlienResources.location("item/irradiated_resin_web"),
        AlienResources.location("item/nether_resin_web"),
        AlienResources.location("item/resin_web"),
        AlienResources.location("nether_resin_vein"),
        AlienResources.location("nether_resin_vein_2"),
        AlienResources.location("nether_resin_vein_3"),
        AlienResources.location("nether_resin_vein_4"),
        AlienResources.location("nether_resin_web"),
        AlienResources.location("resin_vein"),
        AlienResources.location("resin_vein_1"),
        AlienResources.location("resin_vein_2"),
        AlienResources.location("resin_vein_3"),
        AlienResources.location("resin_vein_4"),
        AlienResources.location("resin_web")
    );

    private static final IQuadTransformer RESIN_ALPHA_TRANSFORMER = quad -> {
        var vertices = quad.getVertices();

        for (var vertex = 0; vertex < 4; vertex++) {
            var offset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.COLOR;
            vertices[offset] = (vertices[offset] & 0x00FFFFFF) | RESIN_ALPHA_MASK;
        }
    };

    public ResinAlphaBakedModel(BakedModel originalModel) {
        super(originalModel);
    }

    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll(ResinAlphaBakedModel::wrapResinModel);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return transform(originalModel.getQuads(state, side, rand));
    }

    @Override
    public List<BakedQuad> getQuads(
        @Nullable BlockState state,
        @Nullable Direction side,
        RandomSource rand,
        ModelData extraData,
        @Nullable RenderType renderType
    ) {
        return transform(originalModel.getQuads(state, side, rand, extraData, renderType));
    }

    private static BakedModel wrapResinModel(ModelResourceLocation location, BakedModel model) {
        if (model instanceof ResinAlphaBakedModel || !RESIN_MODEL_IDS.contains(location.id())) {
            return model;
        }

        return new ResinAlphaBakedModel(model);
    }

    private static List<BakedQuad> transform(List<BakedQuad> quads) {
        if (quads.isEmpty()) {
            return quads;
        }

        return RESIN_ALPHA_TRANSFORMER.process(quads);
    }
}
