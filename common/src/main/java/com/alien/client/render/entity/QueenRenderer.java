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
                .setBoneRenderTypeOverrideProvider(QueenRenderer::boneRenderType)
                .setBoneVisibilityFilter(QueenRenderer::shouldHideBone)
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

    // BLib does NOT reset a per-bone texture/render-type override once applied: getOrRefreshRenderBuffer only calls
    // setTextureOverride when the provider returns NON-null, so returning null for a body bone leaves the previous
    // bone's override active. Once an attachment bone (e.g. a visible shackle) sets the attachments sheet, every
    // bone drawn AFTER it in the tree inherited that sheet and sampled empty space - which is why the lower body
    // vanished the instant a chain attached. So body bones must ACTIVELY reset to the queen's own sheet, not return
    // null. These capture the current queen's variant-aware body sheet + render type, set per-entity in renderType().
    private static ResourceLocation currentBodyTexture;

    private static RenderType currentBodyRenderType;

    /**
     * Per-bone texture override: the human-made attachments bolted onto her are NOT part of the queen's own texture, so
     * each draws from its own sheet. Every other bone returns the queen's variant body sheet (NOT null) to actively
     * reset the override - BLib leaves the previous bone's override in place on a null return.
     * <p>
     * Visibility is not decided here: {@code QueenAnimator} already hides these bones until their chain / device is
     * actually attached. This only says what they are painted with when shown.
     */
    private static ResourceLocation boneTexture(AzBone bone) {
        return switch (bone.getName()) {
            case "gNeckShackle", "gLeftArmShackle", "gRightArmShackle" -> ATTACHMENTS_TEXTURE;
            case "gInhibitor" -> INHIBITOR_TEXTURE;
            case "gTracker" -> TRACKER_TEXTURE;
            default -> currentBodyTexture; // reset, don't inherit the previous bone's attachment sheet
        };
    }

    /**
     * Per-bone RENDER TYPE override - the twin of {@link #boneTexture}. A texture override alone is not enough: BLib
     * binds each bone's vertices to a render type, and without a matching type the attachment bones draw into the
     * BODY's render type (bound to the body texture), so their own sheet is never sampled and they render blank even
     * though QueenAnimator has un-hidden them. Each attachment gets a cutout render type keyed to its own sheet;
     * everything else falls through (null) to the variant body render type. Mirrors the working
     * JellyVatBlockEntityRenderer, which sets both providers.
     */
    private static RenderType boneRenderType(AzBone bone) {
        return switch (bone.getName()) {
            case "gNeckShackle", "gLeftArmShackle", "gRightArmShackle" -> RenderType.entityCutoutNoCull(ATTACHMENTS_TEXTURE);
            case "gInhibitor" -> RenderType.entityCutoutNoCull(INHIBITOR_TEXTURE);
            case "gTracker" -> RenderType.entityCutoutNoCull(TRACKER_TEXTURE);
            default -> currentBodyRenderType; // reset, don't inherit the previous bone's attachment render type
        };
    }

    /**
     * Per-bone VISIBILITY, driven by capture state. This MUST live on the renderer config, not as setHidden() calls in
     * the animator: BLib resets each bone's hidden flag every frame during its pipeline, so a hidden flag set in
     * setCustomAnimations is wiped before the bone is drawn - which is why the shackles/inhibitor/ tracker never
     * appeared. The visibility filter is the persistent hook BLib consults DURING rendering (the same mechanism the
     * working JellyVatBlockEntityRenderer uses). Return true to hide.
     * <p>
     * Shackles reveal by chain count (1 -> left arm, 2 -> right arm, 3+ -> neck); the device bones show with their
     * flag. Every non-attachment bone returns false (always shown).
     */
    private static boolean shouldHideBone(AzBone bone, Queen queen) {
        var chains = queen.bindChainCount.get();
        return switch (bone.getName()) {
            case "gLeftArmShackle" -> chains < 1;
            case "gRightArmShackle" -> chains < 2;
            case "gNeckShackle" -> chains < 3;
            case "gInhibitor" -> !queen.isInhibited();
            case "gTracker" -> !queen.isTracked();
            default -> false;
        };
    }

    private static ResourceLocation modelLocation(Queen queen) {
        return RESOURCE_CACHE.getOrCreateModelLocationForVariant(AlienVariant.NORMAL);
    }

    private static RenderType renderType(Queen queen) {
        // BLib calls this per-entity before walking the bones, so capture the body sheet + type here for the bone
        // providers' default case to reset to (see the field comment above).
        currentBodyTexture = RESOURCE_CACHE.getOrCreateTextureLocationForVariant(queen.getVariant());
        currentBodyRenderType = RESOURCE_CACHE.getOrCreateRenderTypeForVariant(queen.getVariant());
        return currentBodyRenderType;
    }

    private static ResourceLocation textureLocation(Queen queen) {
        return RESOURCE_CACHE.getOrCreateTextureLocationForVariant(queen.getVariant());
    }
}
