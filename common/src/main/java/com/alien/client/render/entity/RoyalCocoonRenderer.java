package com.alien.client.render.entity;

import com.alien.AlienResources;
import com.alien.common.gameplay.entity.living.alien.royal_cocoon.RoyalCocoon;
import com.alien.common.registry.init.AlienEntityTypes;
import com.blib.api.client.render.v1.entity.AzEntityRenderer;
import com.blib.api.client.render.v1.entity.AzEntityRendererConfig;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the royal cocoon. One geo, FOUR strain textures picked by entity type (regular / aberrant / nether /
 * irradiated), mirroring the dynamic-texture pattern used by e.g. {@code AdolescentRenderer}.
 */
public class RoyalCocoonRenderer extends AzEntityRenderer<RoyalCocoon> {

    private static final ResourceLocation MODEL = AlienResources.entityGeoModelLocation("royal_cocoon");

    /**
     * How far {@code royal_cocoon.geo.json} hangs below the entity origin, in blocks.
     * <p>
     * ⚠ MEASURED FROM THE MODEL, NOT GUESSED: its lowest cube origin is Y -13.83 in model units, and 16 model units
     * make a block. If the geo is ever re-exported with a different anchor this number has to move with it, which is
     * why it is written down rather than inlined as a magic offset.
     * </p>
     */
    private static final float MODEL_UNDERHANG_BLOCKS = 13.83409F / 16.0F;

    private static final ResourceLocation REGULAR_TEXTURE = AlienResources.entityTextureLocation("royal_cocoon");

    private static final ResourceLocation ABERRANT_TEXTURE =
        AlienResources.entityTextureLocation("aberrant_royal_cocoon");

    private static final ResourceLocation NETHER_TEXTURE = AlienResources.entityTextureLocation("nether_royal_cocoon");

    private static final ResourceLocation IRRADIATED_TEXTURE =
        AlienResources.entityTextureLocation("irradiated_royal_cocoon");

    public RoyalCocoonRenderer(EntityRendererProvider.Context context) {
        super(
            AzEntityRendererConfig.<RoyalCocoon>builder(RoyalCocoonRenderer::modelLocation, RoyalCocoonRenderer::textureLocation)
                .build(),
            context
        );
        this.shadowRadius = 0.6F;
    }

    /**
     * ⭐⭐ AN ADOLESCENT'S COCOON IS DRAWN AT 60%.
     * <p>
     * [stated] "the royal cocoon that spawns when an pred adol or royal adol is changing into a royal needs to be
     * smaller ... 60% but only for that scenario when the adults transform into queens it can stay the current size."
     * </p>
     * <p>
     * ⚠ SHADOW SCALES WITH IT. A 60% cocoon casting a full-size shadow reads as a floating model, and it is the sort of
     * detail that gets reported as "the shrink looks wrong" without anyone being able to say why.
     * </p>
     */
    @Override
    public void render(
        RoyalCocoon entity,
        float entityYaw,
        float partialTick,
        com.mojang.blaze3d.vertex.PoseStack poseStack,
        net.minecraft.client.renderer.MultiBufferSource bufferSource,
        int packedLight
    ) {
        if (!entity.isAdolescentSized()) {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }

        var previousShadow = this.shadowRadius;
        this.shadowRadius = previousShadow * RoyalCocoon.ADOLESCENT_SCALE;

        poseStack.pushPose();

        // ⭐⭐ KEEP THE BASE PLANTED. poseStack.scale scales about the ENTITY ORIGIN, and this model hangs BELOW it -
        // its lowest cube sits at Y -13.83 in model units, which is 0.865 blocks under the entity position. Shrinking
        // it shrinks that overhang too, so the bottom RISES by (1 - scale) x 0.865 and the cocoon appears to float.
        //
        // ⚠ IT FLOATS, IT DOES NOT SINK - worth stating because the instinct is to push a shrunken model DOWN, and
        // doing that here would bury it. Translating down by exactly the amount the base rose leaves it meeting the
        // ground in the same place the full-size cocoon does.
        //
        // ⚠ TRANSLATE BEFORE SCALE, so the offset is measured in blocks rather than being scaled by 0.6 itself.
        poseStack.translate(0.0F, -MODEL_UNDERHANG_BLOCKS * (1.0F - RoyalCocoon.ADOLESCENT_SCALE), 0.0F);
        poseStack.scale(
            RoyalCocoon.ADOLESCENT_SCALE,
            RoyalCocoon.ADOLESCENT_SCALE,
            RoyalCocoon.ADOLESCENT_SCALE
        );
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();

        this.shadowRadius = previousShadow;
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
        if (type == AlienEntityTypes.IRRADIATED_ROYAL_COCOON.get()) {
            return IRRADIATED_TEXTURE;
        }
        return REGULAR_TEXTURE;
    }
}
