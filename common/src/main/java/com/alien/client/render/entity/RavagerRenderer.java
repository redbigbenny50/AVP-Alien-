package com.alien.client.render.entity;

import com.alien.client.animation.entity.RavagerAnimator;
import com.alien.client.render.AlienRenderResourceCache;
import com.alien.client.render.layer.MoltLayer;
import com.alien.client.render.layer.PraetorianRenderedLimbPickerLayer;
import com.alien.client.render.layer.RadiationGlowLayer;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ravager.Ravager;
import com.alien.common.model.alien.variant.AlienVariant;
import com.blib.api.client.render.v1.entity.AzEntityRenderer;
import com.blib.api.client.render.v1.entity.AzEntityRendererConfig;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class RavagerRenderer extends AzEntityRenderer<Ravager> {

    private static final String NAME = "ravager";

    private static final AlienRenderResourceCache RESOURCE_CACHE = new AlienRenderResourceCache(NAME, RenderType::entityCutoutNoCull);

    public RavagerRenderer(EntityRendererProvider.Context context) {
        super(
            AzEntityRendererConfig.builder(RavagerRenderer::modelLocation, RavagerRenderer::textureLocation)
                .setRenderType(RavagerRenderer::renderType)
                .setAnimatorProvider(RavagerAnimator::new)
                .addRenderLayer(new RadiationGlowLayer<>())
                .addRenderLayer(new MoltLayer<>())
                .addRenderLayer(new PraetorianRenderedLimbPickerLayer<>())
                .setShadowRadius(0.5F)
                .build(),
            context
        );
    }

    private static ResourceLocation modelLocation(Ravager ravager) {
        return RESOURCE_CACHE.getOrCreateModelLocationForVariant(AlienVariant.NORMAL);
    }

    private static RenderType renderType(Ravager ravager) {
        return RESOURCE_CACHE.getOrCreateRenderTypeForVariant(ravager.getVariant());
    }

    public static ResourceLocation textureLocation(Ravager ravager) {
        return RESOURCE_CACHE.getOrCreateTextureLocationForVariant(ravager.getVariant());
    }
}
