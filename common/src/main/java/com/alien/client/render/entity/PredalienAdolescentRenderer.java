package com.alien.client.render.entity;

import com.alien.client.animation.entity.PredalienAdolescentAnimator;
import com.alien.client.render.AlienRenderResourceCache;
import com.alien.client.render.layer.MoltLayer;
import com.alien.common.gameplay.entity.living.alien.predalien_adolescent.PredalienAdolescent;
import com.alien.common.model.alien.variant.AlienVariant;
import com.blib.api.client.render.v1.entity.AzEntityRenderer;
import com.blib.api.client.render.v1.entity.AzEntityRendererConfig;
import com.blib.api.client.render.v1.lod.AzLodConfig;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class PredalienAdolescentRenderer extends AzEntityRenderer<PredalienAdolescent> {

    private static final String NAME = "predalien_adolescent";

    private static final AlienRenderResourceCache RESOURCE_CACHE = new AlienRenderResourceCache(NAME, RenderType::entityCutoutNoCull);

    public PredalienAdolescentRenderer(EntityRendererProvider.Context context) {
        super(
            AzEntityRendererConfig.builder(PredalienAdolescentRenderer::modelLocation, PredalienAdolescentRenderer::textureLocation)
                .setRenderType(PredalienAdolescentRenderer::renderType)
                .setAnimatorProvider(PredalienAdolescentAnimator::new)
                .addRenderLayer(new MoltLayer<>())
                .withLodConfig(AzLodConfig.DEFAULT)
                .build(),
            context
        );
    }

    private static ResourceLocation modelLocation(PredalienAdolescent predalienAdolescent) {
        return RESOURCE_CACHE.getOrCreateModelLocationForVariant(AlienVariant.NORMAL);
    }

    private static RenderType renderType(PredalienAdolescent predalienAdolescent) {
        return RESOURCE_CACHE.getOrCreateRenderTypeForVariant(predalienAdolescent.getVariant());
    }

    private static ResourceLocation textureLocation(PredalienAdolescent predalienAdolescent) {
        return RESOURCE_CACHE.getOrCreateTextureLocationForVariant(predalienAdolescent.getVariant());
    }
}
