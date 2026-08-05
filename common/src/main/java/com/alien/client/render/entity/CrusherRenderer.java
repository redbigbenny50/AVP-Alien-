package com.alien.client.render.entity;

import com.alien.client.animation.entity.CrusherAnimator;
import com.alien.client.render.AlienRenderResourceCache;
import com.alien.client.render.layer.MoltLayer;
import com.alien.client.render.layer.PraetorianRenderedLimbPickerLayer;
import com.alien.client.render.layer.RadiationGlowLayer;
import com.alien.common.gameplay.entity.living.alien.xenomorph.crusher.Crusher;
import com.alien.common.model.alien.variant.AlienVariant;
import com.blib.api.client.render.v1.entity.AzEntityRenderer;
import com.blib.api.client.render.v1.entity.AzEntityRendererConfig;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class CrusherRenderer extends AzEntityRenderer<Crusher> {

    private static final String NAME = "crusher";

    private static final AlienRenderResourceCache RESOURCE_CACHE = new AlienRenderResourceCache(NAME, RenderType::entityCutoutNoCull);

    public CrusherRenderer(EntityRendererProvider.Context context) {
        super(
            AzEntityRendererConfig.builder(CrusherRenderer::modelLocation, CrusherRenderer::textureLocation)
                .setRenderType(CrusherRenderer::renderType)
                .setAnimatorProvider(CrusherAnimator::new)
                .addRenderLayer(new RadiationGlowLayer<>())
                .addRenderLayer(new MoltLayer<>())
                .addRenderLayer(new PraetorianRenderedLimbPickerLayer<>())
                .setShadowRadius(0.5F)
                .build(),
            context
        );
    }

    public static ResourceLocation modelLocation(Crusher crusher) {
        return RESOURCE_CACHE.getOrCreateModelLocationForVariant(AlienVariant.NORMAL);
    }

    private static RenderType renderType(Crusher crusher) {
        return RESOURCE_CACHE.getOrCreateRenderTypeForVariant(crusher.getVariant());
    }

    public static ResourceLocation textureLocation(Crusher crusher) {
        return RESOURCE_CACHE.getOrCreateTextureLocationForVariant(crusher.getVariant());
    }
}
