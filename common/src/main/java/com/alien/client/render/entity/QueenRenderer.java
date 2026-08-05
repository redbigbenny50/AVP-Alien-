package com.alien.client.render.entity;

import com.alien.client.animation.entity.QueenAnimator;
import com.alien.client.render.AlienRenderResourceCache;
import com.alien.client.render.layer.MoltLayer;
import com.alien.client.render.layer.PraetorianRenderedLimbPickerLayer;
import com.alien.client.render.layer.RadiationGlowLayer;
import com.alien.client.render.layer.ShackleAnchorLayer;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.model.alien.variant.AlienVariant;
import com.blib.api.client.render.v1.entity.AzEntityRenderer;
import com.blib.api.client.render.v1.entity.AzEntityRendererConfig;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class QueenRenderer extends AzEntityRenderer<Queen> {

    private static final String NAME = "queen";

    private static final AlienRenderResourceCache RESOURCE_CACHE = new AlienRenderResourceCache(NAME, RenderType::entityCutoutNoCull);

    public QueenRenderer(EntityRendererProvider.Context context) {
        super(
            AzEntityRendererConfig.builder(QueenRenderer::modelLocation, QueenRenderer::textureLocation)
                .setRenderType(QueenRenderer::renderType)
                .setAnimatorProvider(QueenAnimator::new)
                .addRenderLayer(new RadiationGlowLayer<>())
                .addRenderLayer(new MoltLayer<>())
                .addRenderLayer(new ShackleAnchorLayer<>())
                .addRenderLayer(new PraetorianRenderedLimbPickerLayer<>())
                .setShadowRadius(1F)
                .build(),
            context
        );
    }

    private static ResourceLocation modelLocation(Queen queen) {
        return RESOURCE_CACHE.getOrCreateModelLocationForVariant(AlienVariant.NORMAL);
    }

    private static RenderType renderType(Queen queen) {
        return RESOURCE_CACHE.getOrCreateRenderTypeForVariant(queen.getVariant());
    }

    private static ResourceLocation textureLocation(Queen queen) {
        return RESOURCE_CACHE.getOrCreateTextureLocationForVariant(queen.getVariant());
    }
}
