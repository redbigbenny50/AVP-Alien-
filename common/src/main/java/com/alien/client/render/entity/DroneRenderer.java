package com.alien.client.render.entity;

import com.alien.client.animation.entity.DroneAnimator;
import com.alien.client.render.AlienRenderResourceCache;
import com.alien.client.render.layer.MoltLayer;
import com.alien.client.render.layer.PraetorianRenderedLimbPickerLayer;
import com.alien.client.render.layer.RadiationGlowLayer;
import com.alien.common.gameplay.entity.living.alien.xenomorph.drone.Drone;
import com.alien.common.model.alien.variant.AlienVariant;
import com.blib.api.client.render.v1.entity.AzEntityRenderer;
import com.blib.api.client.render.v1.entity.AzEntityRendererConfig;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class DroneRenderer extends AzEntityRenderer<Drone> {

    private static final String NAME = "drone";

    private static final AlienRenderResourceCache RESOURCE_CACHE = new AlienRenderResourceCache(NAME, RenderType::entityCutoutNoCull);

    public DroneRenderer(EntityRendererProvider.Context context) {
        super(
            AzEntityRendererConfig.builder(DroneRenderer::modelLocation, DroneRenderer::textureLocation)
                .setRenderType(DroneRenderer::renderType)
                .setAnimatorProvider(DroneAnimator::new)
                .addRenderLayer(new RadiationGlowLayer<>())
                .addRenderLayer(new MoltLayer<>())
                .addRenderLayer(new PraetorianRenderedLimbPickerLayer<>())
                .setShadowRadius(0.5F)
                .build(),
            context
        );
    }

    public static ResourceLocation modelLocation(Drone drone) {
        return RESOURCE_CACHE.getOrCreateModelLocationForVariant(AlienVariant.NORMAL);
    }

    private static RenderType renderType(Drone drone) {
        return RESOURCE_CACHE.getOrCreateRenderTypeForVariant(drone.getVariant());
    }

    public static ResourceLocation textureLocation(Drone drone) {
        return RESOURCE_CACHE.getOrCreateTextureLocationForVariant(drone.getVariant());
    }
}
