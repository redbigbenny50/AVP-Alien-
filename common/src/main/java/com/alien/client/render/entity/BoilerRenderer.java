package com.alien.client.render.entity;

import com.alien.client.animation.entity.BoilerAnimator;
import com.alien.client.render.AlienRenderResourceCache;
import com.alien.client.render.layer.BoilGlowLayer;
import com.alien.client.render.layer.MoltLayer;
import com.alien.client.render.layer.PraetorianRenderedLimbPickerLayer;
import com.alien.common.gameplay.entity.living.alien.xenomorph.boiler.Boiler;
import com.alien.common.model.alien.variant.AlienVariant;
import com.blib.api.client.render.v1.entity.AzEntityRenderer;
import com.blib.api.client.render.v1.entity.AzEntityRendererConfig;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class BoilerRenderer extends AzEntityRenderer<Boiler> {

    private static final String NAME = "boiler";

    private static final AlienRenderResourceCache RESOURCE_CACHE = new AlienRenderResourceCache(NAME, RenderType::entityCutoutNoCull);

    public BoilerRenderer(EntityRendererProvider.Context context) {
        super(
            AzEntityRendererConfig.builder(BoilerRenderer::modelLocation, BoilerRenderer::textureLocation)
                .setRenderType(BoilerRenderer::renderType)
                .setAnimatorProvider(BoilerAnimator::new)
                .addRenderLayer(new BoilGlowLayer<>())
                .addRenderLayer(new MoltLayer<>())
                .addRenderLayer(new PraetorianRenderedLimbPickerLayer<>())
                .setShadowRadius(0.5F)
                .build(),
            context
        );
    }

    public static ResourceLocation modelLocation(Boiler boiler) {
        return RESOURCE_CACHE.getOrCreateModelLocationForVariant(AlienVariant.NORMAL);
    }

    private static RenderType renderType(Boiler boiler) {
        return RESOURCE_CACHE.getOrCreateRenderTypeForVariant(boiler.getVariant());
    }

    public static ResourceLocation textureLocation(Boiler boiler) {
        return RESOURCE_CACHE.getOrCreateTextureLocationForVariant(boiler.getVariant());
    }
}
