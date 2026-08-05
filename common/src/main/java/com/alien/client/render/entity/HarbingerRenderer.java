package com.alien.client.render.entity;

import com.alien.client.animation.entity.HarbingerAnimator;
import com.alien.client.render.AlienRenderResourceCache;
import com.alien.client.render.layer.MoltLayer;
import com.alien.client.render.layer.PraetorianRenderedLimbPickerLayer;
import com.alien.client.render.layer.RadiationGlowLayer;
import com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger.Harbinger;
import com.alien.common.model.alien.variant.AlienVariant;
import com.blib.api.client.render.v1.entity.AzEntityRenderer;
import com.blib.api.client.render.v1.entity.AzEntityRendererConfig;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class HarbingerRenderer extends AzEntityRenderer<Harbinger> {

    private static final String NAME = "harbinger";

    private static final AlienRenderResourceCache RESOURCE_CACHE = new AlienRenderResourceCache(NAME, RenderType::entityCutoutNoCull);

    public HarbingerRenderer(EntityRendererProvider.Context context) {
        super(
            AzEntityRendererConfig.builder(HarbingerRenderer::modelLocation, HarbingerRenderer::textureLocation)
                .setRenderType(HarbingerRenderer::renderType)
                .setAnimatorProvider(HarbingerAnimator::new)
                .addRenderLayer(new RadiationGlowLayer<>())
                .addRenderLayer(new MoltLayer<>())
                .addRenderLayer(new PraetorianRenderedLimbPickerLayer<>())
                .setShadowRadius(0.5F)
                .build(),
            context
        );
    }

    private static ResourceLocation modelLocation(Harbinger harbinger) {
        return RESOURCE_CACHE.getOrCreateModelLocationForVariant(AlienVariant.NORMAL);
    }

    private static RenderType renderType(Harbinger harbinger) {
        return RESOURCE_CACHE.getOrCreateRenderTypeForVariant(harbinger.getVariant());
    }

    public static ResourceLocation textureLocation(Harbinger harbinger) {
        return RESOURCE_CACHE.getOrCreateTextureLocationForVariant(harbinger.getVariant());
    }
}
