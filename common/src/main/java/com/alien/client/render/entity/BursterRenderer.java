package com.alien.client.render.entity;

import com.alien.client.animation.entity.BursterAnimator;
import com.alien.client.render.AlienRenderResourceCache;
import com.alien.client.render.layer.MoltLayer;
import com.alien.client.render.layer.PraetorianRenderedLimbPickerLayer;
import com.alien.client.render.layer.RadiationGlowLayer;
import com.alien.common.gameplay.entity.living.alien.xenomorph.burster.Burster;
import com.alien.common.model.alien.variant.AlienVariant;
import com.blib.api.client.render.v1.entity.AzEntityRenderer;
import com.blib.api.client.render.v1.entity.AzEntityRendererConfig;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class BursterRenderer extends AzEntityRenderer<Burster> {

    private static final String NAME = "burster";

    private static final AlienRenderResourceCache RESOURCE_CACHE = new AlienRenderResourceCache(NAME, RenderType::entityCutoutNoCull);

    public BursterRenderer(EntityRendererProvider.Context context) {
        super(
            AzEntityRendererConfig.builder(BursterRenderer::modelLocation, BursterRenderer::textureLocation)
                .setRenderType(BursterRenderer::renderType)
                .setAnimatorProvider(BursterAnimator::new)
                .addRenderLayer(new RadiationGlowLayer<>())
                .addRenderLayer(new MoltLayer<>())
                .addRenderLayer(new PraetorianRenderedLimbPickerLayer<>())
                .setShadowRadius(0.5F)
                .build(),
            context
        );
    }

    private static ResourceLocation modelLocation(Burster burster) {
        return RESOURCE_CACHE.getOrCreateModelLocationForVariant(AlienVariant.NORMAL);
    }

    private static RenderType renderType(Burster burster) {
        return RESOURCE_CACHE.getOrCreateRenderTypeForVariant(burster.getVariant());
    }

    public static ResourceLocation textureLocation(Burster burster) {
        return RESOURCE_CACHE.getOrCreateTextureLocationForVariant(burster.getVariant());
    }
}
