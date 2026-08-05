package com.alien.client.render.entity;

import com.alien.client.animation.entity.CarrierAnimator;
import com.alien.client.render.AlienRenderResourceCache;
import com.alien.client.render.layer.MoltLayer;
import com.alien.client.render.layer.PraetorianRenderedLimbPickerLayer;
import com.alien.client.render.layer.RadiationGlowLayer;
import com.alien.common.gameplay.entity.living.alien.xenomorph.carrier.Carrier;
import com.alien.common.model.alien.variant.AlienVariant;
import com.blib.api.client.render.v1.entity.AzEntityRenderer;
import com.blib.api.client.render.v1.entity.AzEntityRendererConfig;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class CarrierRenderer extends AzEntityRenderer<Carrier> {

    private static final String NAME = "carrier";

    private static final AlienRenderResourceCache RESOURCE_CACHE = new AlienRenderResourceCache(NAME, RenderType::entityCutoutNoCull);

    public CarrierRenderer(EntityRendererProvider.Context context) {
        super(
            AzEntityRendererConfig.builder(CarrierRenderer::modelLocation, CarrierRenderer::textureLocation)
                .setRenderType(CarrierRenderer::renderType)
                .setAnimatorProvider(CarrierAnimator::new)
                .addRenderLayer(new RadiationGlowLayer<>())
                .addRenderLayer(new MoltLayer<>())
                .addRenderLayer(new PraetorianRenderedLimbPickerLayer<>())
                .setShadowRadius(0.5F)
                .build(),
            context
        );
    }

    private static ResourceLocation modelLocation(Carrier carrier) {
        return RESOURCE_CACHE.getOrCreateModelLocationForVariant(AlienVariant.NORMAL);
    }

    private static RenderType renderType(Carrier carrier) {
        return RESOURCE_CACHE.getOrCreateRenderTypeForVariant(carrier.getVariant());
    }

    public static ResourceLocation textureLocation(Carrier carrier) {
        return RESOURCE_CACHE.getOrCreateTextureLocationForVariant(carrier.getVariant());
    }
}
