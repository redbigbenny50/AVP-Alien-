package com.alien.client.render.entity;

import com.alien.client.animation.entity.EmpressAnimator;
import com.alien.client.render.AlienRenderResourceCache;
import com.alien.client.render.layer.MoltLayer;
import com.alien.client.render.layer.PraetorianRenderedLimbPickerLayer;
import com.alien.client.render.layer.RadiationGlowLayer;
import com.alien.common.gameplay.entity.living.alien.xenomorph.empress.Empress;
import com.alien.common.model.alien.variant.AlienVariant;
import com.blib.api.client.render.v1.entity.AzEntityRenderer;
import com.blib.api.client.render.v1.entity.AzEntityRendererConfig;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class EmpressRenderer extends AzEntityRenderer<Empress> {

    private static final String NAME = "empress";

    private static final AlienRenderResourceCache RESOURCE_CACHE = new AlienRenderResourceCache(NAME, RenderType::entityCutoutNoCull);

    public EmpressRenderer(EntityRendererProvider.Context context) {
        super(
            AzEntityRendererConfig.builder(EmpressRenderer::modelLocation, EmpressRenderer::textureLocation)
                .setRenderType(EmpressRenderer::renderType)
                .setAnimatorProvider(EmpressAnimator::new)
                .addRenderLayer(new RadiationGlowLayer<>())
                .addRenderLayer(new MoltLayer<>())
                .addRenderLayer(new PraetorianRenderedLimbPickerLayer<>())
                .setShadowRadius(1F)
                .build(),
            context
        );
    }

    private static ResourceLocation modelLocation(Empress empress) {
        return RESOURCE_CACHE.getOrCreateModelLocationForVariant(AlienVariant.NORMAL);
    }

    private static RenderType renderType(Empress empress) {
        return RESOURCE_CACHE.getOrCreateRenderTypeForVariant(empress.getVariant());
    }

    private static ResourceLocation textureLocation(Empress empress) {
        return RESOURCE_CACHE.getOrCreateTextureLocationForVariant(empress.getVariant());
    }
}
