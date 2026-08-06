package com.alien.client.render.entity;

import com.alien.client.animation.entity.RazorClawAnimator;
import com.alien.client.render.AlienRenderResourceCache;
import com.alien.client.render.layer.MoltLayer;
import com.alien.client.render.layer.PraetorianRenderedLimbPickerLayer;
import com.alien.client.render.layer.RadiationGlowLayer;
import com.alien.common.gameplay.entity.living.alien.xenomorph.razor_claw.RazorClaw;
import com.alien.common.model.alien.variant.AlienVariant;
import com.blib.api.client.render.v1.entity.AzEntityRenderer;
import com.blib.api.client.render.v1.entity.AzEntityRendererConfig;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class RazorClawRenderer extends AzEntityRenderer<RazorClaw> {

    private static final String NAME = "razor_claw";

    private static final AlienRenderResourceCache RESOURCE_CACHE = new AlienRenderResourceCache(NAME, RenderType::entityCutoutNoCull);

    public RazorClawRenderer(EntityRendererProvider.Context context) {
        super(
            AzEntityRendererConfig.builder(RazorClawRenderer::modelLocation, RazorClawRenderer::textureLocation)
                .setRenderType(RazorClawRenderer::renderType)
                .setAnimatorProvider(RazorClawAnimator::new)
                .addRenderLayer(new RadiationGlowLayer<>())
                .addRenderLayer(new MoltLayer<>())
                .addRenderLayer(new PraetorianRenderedLimbPickerLayer<>())
                .setShadowRadius(0.5F)
                .build(),
            context
        );
    }

    private static ResourceLocation modelLocation(RazorClaw razorClaw) {
        return RESOURCE_CACHE.getOrCreateModelLocationForVariant(AlienVariant.NORMAL);
    }

    private static RenderType renderType(RazorClaw razorClaw) {
        return RESOURCE_CACHE.getOrCreateRenderTypeForVariant(razorClaw.getVariant());
    }

    public static ResourceLocation textureLocation(RazorClaw razorClaw) {
        return RESOURCE_CACHE.getOrCreateTextureLocationForVariant(razorClaw.getVariant());
    }
}
