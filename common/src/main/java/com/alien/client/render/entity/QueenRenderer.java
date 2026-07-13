package com.alien.client.render.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.QueenAnimator;
import com.alien.client.render.AlienRenderResourceCache;
import com.alien.client.render.layer.MoltLayer;
import com.alien.client.render.layer.RadiationGlowLayer;
import com.alien.client.render.layer.ShackleAnchorLayer;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.model.alien.variant.AlienVariant;
import com.blib.api.client.model.v1.AzBone;
import com.blib.api.client.render.v1.entity.AzEntityRenderer;
import com.blib.api.client.render.v1.entity.AzEntityRendererConfig;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class QueenRenderer extends AzEntityRenderer<Queen> {

    private static final String NAME = "queen";

    private static final AlienRenderResourceCache RESOURCE_CACHE = new AlienRenderResourceCache(NAME, RenderType::entityCutoutNoCull);

    public QueenRenderer(EntityRendererProvider.Context context) {
        super(
            AzEntityRendererConfig.builder(QueenRenderer::modelLocation, QueenRenderer::textureLocation)
                .setRenderType(QueenRenderer::renderType)
                .setBoneTextureOverrideProvider(QueenRenderer::boneTexture)
                .setAnimatorProvider(QueenAnimator::new)
                .addRenderLayer(new RadiationGlowLayer<>())
                .addRenderLayer(new MoltLayer<>())
                .addRenderLayer(new ShackleAnchorLayer<>())
                .setShadowRadius(1F)
                .build(),
            context
        );
    }

    /** Shackles (neck + both arms) draw from their own sheet, not the queen body sheet. */
    private static final ResourceLocation ATTACHMENTS_TEXTURE =
        AlienResources.entityTextureLocation("queen_attachments");

    private static final ResourceLocation INHIBITOR_TEXTURE =
        AlienResources.entityTextureLocation("queen_inhibitor");

    private static final ResourceLocation TRACKER_TEXTURE =
        AlienResources.entityTextureLocation("queen_tracker");

    /**
     * Per-bone texture override: the human-made attachments bolted onto her are NOT part of the queen's own texture, so
     * each draws from its own sheet. Everything else returns null and falls through to the body texture (which is
     * variant-aware - normal/nether/aberrant/irradiated).
     * <p>
     * Visibility is not decided here: {@code QueenAnimator} already hides these bones until their chain / device is
     * actually attached. This only says what they are painted with when shown.
     */
    private static ResourceLocation boneTexture(AzBone bone) {
        return switch (bone.getName()) {
            case "gNeckShackle", "gLeftArmShackle", "gRightArmShackle" -> ATTACHMENTS_TEXTURE;
            case "gInhibitor" -> INHIBITOR_TEXTURE;
            case "gTracker" -> TRACKER_TEXTURE;
            default -> null;
        };
    }

    private static ResourceLocation modelLocation(Queen queen) {
        return RESOURCE_CACHE.getOrCreateModelLocationForVariant(AlienVariant.NORMAL);
    }

    private static RenderType renderType(Queen queen) {
        return RESOURCE_CACHE.getOrCreateRenderTypeForVariant(queen.getVariant());
    }

    private static ResourceLocation textureLocation(Queen queen) {
        return RESOURCE_CACHE.getOrCreateTextureLocationForVariant(queen.getVariant());
    }
}
