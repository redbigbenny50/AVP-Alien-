package com.alien.client.render.entity;

import com.alien.AlienResources;
import com.alien.common.gameplay.entity.living.alien.ovipositor.EmpressOvipositor;
import com.alien.common.gameplay.entity.living.alien.xenomorph.empress.Empress;
import com.blib.api.client.render.v1.entity.AzEntityRenderer;
import com.blib.api.client.render.v1.entity.AzEntityRendererConfig;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * The empress's eggsack. Bigger geo, per-strain textures, and NO chained form.
 * <p>
 * The queen's renderer carries a second model and a restraints layer for when she is a captive; the empress cannot be
 * chained or inhibited, so there is no chained version of her eggsack and none of that exists here. What it gains
 * instead is a texture per strain - unlike the queen's, which uses one sheet for every variant.
 * <p>
 * IRRADIATED HAS NO SHEET, deliberately: that strain cannot reproduce at all, so an irradiated empress never grows one
 * of these. The lookup falls back to the base texture rather than a magenta checkerboard if one ever appears anyway.
 */
public class EmpressOvipositorRenderer extends AzEntityRenderer<EmpressOvipositor> {

    private static final String NAME = "empress_ovipositor";

    private static final ResourceLocation MODEL = AlienResources.entityGeoModelLocation(NAME);

    private static final ResourceLocation TEXTURE = AlienResources.entityTextureLocation(NAME);

    private static final ResourceLocation NETHER_TEXTURE = AlienResources.entityTextureLocation("nether_" + NAME);

    private static final ResourceLocation ABERRANT_TEXTURE = AlienResources.entityTextureLocation("aberrant_" + NAME);

    public EmpressOvipositorRenderer(EntityRendererProvider.Context context) {
        super(
            AzEntityRendererConfig.<EmpressOvipositor>builder(
                ovipositor -> MODEL,
                EmpressOvipositorRenderer::textureFor
            ).build(),
            context
        );
        this.shadowRadius = 0.5F;
    }

    /** The strain of the empress underneath it - an eggsack has no variant of its own to read. */
    private static ResourceLocation textureFor(EmpressOvipositor ovipositor) {
        if (!(ovipositor.getVehicle() instanceof Empress empress)) {
            return TEXTURE;
        }

        return switch (empress.getVariant()) {
            case NETHER -> NETHER_TEXTURE;
            case ABERRANT -> ABERRANT_TEXTURE;
            default -> TEXTURE;
        };
    }
}
