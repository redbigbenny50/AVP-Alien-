package com.alien.client.render.block;

import com.alien.AlienResources;
import com.alien.common.gameplay.block.entity.jelly.JellyVatBlockEntity;
import com.alien.common.gameplay.block.jelly.JellyType;
import com.blib.api.client.model.v1.AzBone;
import com.blib.api.client.render.v1.block.AzBlockEntityRenderer;
import com.blib.api.client.render.v1.block.AzBlockEntityRendererConfig;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders a jelly vat. The whole model is drawn on the base vat texture ({@code jelly_vat.png}) so the walls/veins are
 * correct; the fill-stage bones are re-textured to the royal or scourge jelly fill via per-bone overrides.
 * <p>
 * Why this split: the walls' UVs only exist on {@code jelly_vat.png} - if the model were drawn on the fill texture, the
 * wall UVs would land on empty space and the walls would be invisible. So the base texture is always {@code jelly_vat},
 * and only the {@code gStageN} bones are overridden to the fill texture.
 * <p>
 * Royal vs scourge: the per-bone overrides can't see the vat, so we capture the current vat's fill texture in a field
 * at the start of each render (via {@link #renderType}, which BLib calls per-vat before processing bones). Block-entity
 * rendering is sequential on the render thread, so a single shared field is safe here. The stage bones then read that
 * captured texture. Fill level: the {@code gStage1}..{@code gStage9} bones are independent siblings, each the full
 * jelly column at its level; exactly the one matching the current level is shown, the rest hidden (level 0 shows none).
 */
public class JellyVatBlockEntityRenderer extends AzBlockEntityRenderer<JellyVatBlockEntity> {

    private static final String STAGE_BONE_PREFIX = "gStage";

    private static final ResourceLocation MODEL = AlienResources.blockGeoModelLocation("jelly_vat");

    private static final ResourceLocation BASE_TEXTURE = AlienResources.blockTextureLocation("jelly_vat");

    private static final ResourceLocation ROYAL_FILL = AlienResources.blockTextureLocation("jelly_vat_royal_fill");

    private static final ResourceLocation SCOURGE_FILL = AlienResources.blockTextureLocation("jelly_vat_scourge_fill");

    // Set per-render (in renderType) so the bone-only overrides know which fill texture the current vat needs.
    private static ResourceLocation currentFill = ROYAL_FILL;

    public JellyVatBlockEntityRenderer() {
        super(buildConfig());
    }

    private static AzBlockEntityRendererConfig buildConfig() {
        var builder = AzBlockEntityRendererConfig.builder(
            JellyVatBlockEntityRenderer::modelLocation,
            JellyVatBlockEntityRenderer::textureLocation
        );
        builder.setBoneTextureOverrideProvider(JellyVatBlockEntityRenderer::boneTexture);
        builder.setBoneRenderTypeOverrideProvider(JellyVatBlockEntityRenderer::boneRenderType);
        builder.setBoneVisibilityFilter(JellyVatBlockEntityRenderer::shouldHideBone);
        builder.setRenderType(JellyVatBlockEntityRenderer::renderType);
        return builder.build();
    }

    private static ResourceLocation modelLocation(JellyVatBlockEntity vat) {
        return MODEL;
    }

    /** Whole-model base texture = jelly_vat.png so the walls/veins are correct. Stage bones override to the fill. */
    private static ResourceLocation textureLocation(JellyVatBlockEntity vat) {
        return BASE_TEXTURE;
    }

    /**
     * Base render type on the vat texture (cutout so it draws). Also captures this vat's fill texture (royal/scourge)
     * into {@link #currentFill} for the bone overrides, since BLib calls this per-vat before processing bones.
     */
    private static RenderType renderType(JellyVatBlockEntity vat) {
        currentFill = vat.getJellyType() == JellyType.SCOURGE ? SCOURGE_FILL : ROYAL_FILL;
        return RenderType.entityCutoutNoCull(BASE_TEXTURE);
    }

    /** Stage bones use the current vat's fill texture; other bones fall through (null) to the base vat texture. */
    private static ResourceLocation boneTexture(AzBone bone) {
        if (bone.getName().startsWith(STAGE_BONE_PREFIX)) {
            return currentFill;
        }
        return null;
    }

    /** Stage bones draw under the current fill render type; other bones fall through (null) to the base render type. */
    private static RenderType boneRenderType(AzBone bone) {
        if (bone.getName().startsWith(STAGE_BONE_PREFIX)) {
            return RenderType.entityCutoutNoCull(currentFill);
        }
        return null;
    }

    /** Show only the stage bone matching the current fill level; hide the rest. Non-stage bones always shown. */
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
