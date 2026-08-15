package com.alien.client.render.entity;

import com.alien.AlienResources;
import com.alien.common.gameplay.entity.living.alien.royal_cocoon.RoyalCocoon;
import com.alien.common.registry.init.AlienEntityTypes;
import com.blib.api.client.render.v1.entity.AzEntityRenderer;
import com.blib.api.client.render.v1.entity.AzEntityRendererConfig;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the royal cocoon. One geo, three strain textures picked by entity type (regular / aberrant / nether),
 * mirroring the dynamic-texture pattern used by e.g. {@code AdolescentRenderer}.
 */
public class RoyalCocoonRenderer extends AzEntityRenderer<RoyalCocoon> {

    private static final ResourceLocation MODEL = AlienResources.entityGeoModelLocation("royal_cocoon");

    private static final ResourceLocation REGULAR_TEXTURE = AlienResources.entityTextureLocation("royal_cocoon");

    private static final ResourceLocation ABERRANT_TEXTURE =
        AlienResources.entityTextureLocation("aberrant_royal_cocoon");

    private static final ResourceLocation NETHER_TEXTURE = AlienResources.entityTextureLocation("nether_royal_cocoon");

    public RoyalCocoonRenderer(EntityRendererProvider.Context context) {
        super(
            AzEntityRendererConfig.<RoyalCocoon>builder(RoyalCocoonRenderer::modelLocation, RoyalCocoonRenderer::textureLocation)
                .build(),
            context
        );
        this.shadowRadius = 0.6F;
    }

    private static ResourceLocation modelLocation(RoyalCocoon cocoon) {
        return MODEL;
    }

    private static ResourceLocation textureLocation(RoyalCocoon cocoon) {
        var type = cocoon.getType();
        if (type == AlienEntityTypes.ABERRANT_ROYAL_COCOON.get()) {
            return ABERRANT_TEXTURE;
        }
        if (type == AlienEntityTypes.NETHER_ROYAL_COCOON.get()) {
            return NETHER_TEXTURE;
        }
        return REGULAR_TEXTURE;
    }
}
