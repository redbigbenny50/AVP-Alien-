package com.alien.client.render.entity;

import com.alien.client.animation.entity.AdolescentAnimator;
import com.alien.client.render.AlienRenderResourceCache;
import com.alien.client.render.layer.MoltLayer;
import com.alien.common.gameplay.entity.living.alien.adolescent.Adolescent;
import com.alien.common.model.alien.variant.AlienVariant;
import com.blib.api.client.render.v1.entity.AzEntityRenderer;
import com.blib.api.client.render.v1.entity.AzEntityRendererConfig;
import com.blib.api.client.render.v1.lod.AzLodConfig;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class AdolescentRenderer extends AzEntityRenderer<Adolescent> {

    private static final String NAME = "adolescent";

    private static final AlienRenderResourceCache RESOURCE_CACHE = new AlienRenderResourceCache(NAME, RenderType::entityCutoutNoCull);

    public AdolescentRenderer(EntityRendererProvider.Context context) {
        super(
            AzEntityRendererConfig.builder(AdolescentRenderer::modelLocation, AdolescentRenderer::textureLocation)
                .setRenderType(AdolescentRenderer::renderType)
                .setAnimatorProvider(AdolescentAnimator::new)
                .addRenderLayer(new MoltLayer<>())
                .withLodConfig(AzLodConfig.DEFAULT)
                .build(),
            context
        );
    }

    private static ResourceLocation modelLocation(Adolescent adolescent) {
        return RESOURCE_CACHE.getOrCreateModelLocationForVariant(AlienVariant.NORMAL, adolescent.isRoyal());
    }

    private static RenderType renderType(Adolescent adolescent) {
        return RESOURCE_CACHE.getOrCreateRenderTypeForVariant(adolescent.getVariant(), adolescent.isRoyal());
    }

    private static ResourceLocation textureLocation(Adolescent adolescent) {
        return RESOURCE_CACHE.getOrCreateTextureLocationForVariant(adolescent.getVariant(), adolescent.isRoyal());
    }
}
