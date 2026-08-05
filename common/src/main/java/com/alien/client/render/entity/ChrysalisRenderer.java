package com.alien.client.render.entity;

import com.alien.client.animation.entity.ChrysalisAnimator;
import com.alien.client.render.AlienRenderResourceCache;
import com.alien.client.render.layer.MoltLayer;
import com.alien.client.render.layer.PraetorianRenderedLimbPickerLayer;
import com.alien.client.render.layer.RadiationGlowLayer;
import com.alien.common.gameplay.entity.living.alien.xenomorph.chrysalis.Chrysalis;
import com.alien.common.model.alien.variant.AlienVariant;
import com.blib.api.client.render.v1.entity.AzEntityRenderer;
import com.blib.api.client.render.v1.entity.AzEntityRendererConfig;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class ChrysalisRenderer extends AzEntityRenderer<Chrysalis> {

    private static final String NAME = "chrysalis";

    private static final AlienRenderResourceCache RESOURCE_CACHE = new AlienRenderResourceCache(NAME, RenderType::entityCutoutNoCull);

    public ChrysalisRenderer(EntityRendererProvider.Context context) {
        super(
            AzEntityRendererConfig.builder(ChrysalisRenderer::modelLocation, ChrysalisRenderer::textureLocation)
                .setRenderType(ChrysalisRenderer::renderType)
                .setAnimatorProvider(ChrysalisAnimator::new)
                .addRenderLayer(new RadiationGlowLayer<>())
                .addRenderLayer(new MoltLayer<>())
                .addRenderLayer(new PraetorianRenderedLimbPickerLayer<>())
                .setShadowRadius(0.5F)
                .build(),
            context
        );
    }

    private static ResourceLocation modelLocation(Chrysalis chrysalis) {
        return RESOURCE_CACHE.getOrCreateModelLocationForVariant(AlienVariant.NORMAL);
    }

    private static RenderType renderType(Chrysalis chrysalis) {
        return RESOURCE_CACHE.getOrCreateRenderTypeForVariant(chrysalis.getVariant());
    }

    public static ResourceLocation textureLocation(Chrysalis chrysalis) {
        return RESOURCE_CACHE.getOrCreateTextureLocationForVariant(chrysalis.getVariant());
    }
}
