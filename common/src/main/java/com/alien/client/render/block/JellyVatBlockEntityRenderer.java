package com.alien.client.render.block;

import com.alien.AlienResources;
import com.alien.common.gameplay.block.entity.jelly.JellyVatBlockEntity;
import com.alien.common.gameplay.block.jelly.JellyType;
import com.blib.api.client.model.v1.AzBone;
import com.blib.api.client.render.v1.block.AzBlockEntityRenderer;
import com.blib.api.client.render.v1.block.AzBlockEntityRendererConfig;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders a jelly vat: shows only the fill-stage bone for the vat's current level, and textures the whole model with
 * the royal or scourge jelly texture based on the vat's type.
 * <p>
 * The model has a static body plus nine nested fill-stage bones ({@code gStage1}..{@code gStage9}) sharing one UV
 * layout; only the bone matching the current level (1..9) is shown, the rest hidden - so successive stages appear as
 * the vat fills. Level 0 hides all stages (empty vat). The entire model uses one texture per jelly type (walls and
 * jelly are baked into the same UV atlas), so the type is a whole-model texture swap - no per-bone texture handling
 * needed.
 */
public class JellyVatBlockEntityRenderer extends AzBlockEntityRenderer<JellyVatBlockEntity> {

    private static final String STAGE_BONE_PREFIX = "gStage";

    private static final ResourceLocation MODEL = AlienResources.blockGeoModelLocation("jelly_vat");

    private static final ResourceLocation ROYAL_TEXTURE = AlienResources.blockTextureLocation("jelly_vat_royal_fill");

    private static final ResourceLocation SCOURGE_TEXTURE = AlienResources.blockTextureLocation("jelly_vat_scourge_fill");

    public JellyVatBlockEntityRenderer() {
        super(buildConfig());
    }

    private static AzBlockEntityRendererConfig buildConfig() {
        var builder = AzBlockEntityRendererConfig.builder(
            JellyVatBlockEntityRenderer::modelLocation,
            JellyVatBlockEntityRenderer::textureLocation
        );
        // setBoneVisibilityFilter is declared on the parent builder and returns the parent type, so we call it for its
        // side effect (it mutates this same builder) and keep our child-typed reference to reach the child build().
        builder.setBoneVisibilityFilter(JellyVatBlockEntityRenderer::shouldHideBone);
        return builder.build();
    }

    private static ResourceLocation modelLocation(JellyVatBlockEntity vat) {
        return MODEL;
    }

    /** Whole-model texture, chosen by the vat's jelly type (walls + jelly share one UV atlas per type). */
    private static ResourceLocation textureLocation(JellyVatBlockEntity vat) {
        return vat.getJellyType() == JellyType.SCOURGE ? SCOURGE_TEXTURE : ROYAL_TEXTURE;
    }

    /** Hide every stage bone except the one matching the vat's current fill level; keep all non-stage bones visible. */
    private static boolean shouldHideBone(AzBone bone, JellyVatBlockEntity vat) {
        String name = bone.getName();
        if (!name.startsWith(STAGE_BONE_PREFIX)) {
            return false;
        }
        return parseStage(name) != vat.getFillLevel();
    }

    private static int parseStage(String boneName) {
        try {
            return Integer.parseInt(boneName.substring(STAGE_BONE_PREFIX.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
