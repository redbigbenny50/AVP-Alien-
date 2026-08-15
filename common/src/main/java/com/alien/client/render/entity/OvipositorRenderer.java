package com.alien.client.render.entity;

import com.alien.AlienResources;
import com.alien.client.render.layer.EggsackRestraintsLayer;
import com.alien.common.gameplay.entity.living.alien.ovipositor.Ovipositor;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.blib.api.client.render.v1.entity.AzEntityRenderer;
import com.blib.api.client.render.v1.entity.AzEntityRendererConfig;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * The queen's eggsack: two models (loose and chained) and a texture per strain for each.
 * <p>
 * This used to hand the config a constant texture lambda, so every queen of every strain rendered the plain sheet - a
 * nether queen's eggsack looked like a normal one, and so did an aberrant's, chained or not. Only the MODEL ever
 * swapped. The strain now resolves the same way it does on the empress, off the queen this ovipositor is riding.
 * <p>
 * The CHAINED form is a model swap only. It shares the strain sheet with the loose form - a captive queen's eggsack is
 * the same organ in a different pose - and the restraints themselves are drawn by {@link EggsackRestraintsLayer} off
 * its own texture, so there is nothing strain-specific left for a chained sheet to carry.
 * <p>
 * IRRADIATED IS DELIBERATELY ABSENT and falls through to the base sheet: that strain cannot reproduce, so an irradiated
 * queen never grows one of these and there is nothing to draw. The fallback exists so that if one ever does appear it
 * is the wrong colour rather than a magenta checkerboard.
 */
public class OvipositorRenderer extends AzEntityRenderer<Ovipositor> {

    private static final String NAME = "ovipositor";

    /** Chained-eggsack presentation, shown when this ovipositor rides a contained, inhibited (captive) queen. */
    private static final String CHAINED_NAME = "chained_eggsack";

    private static final ResourceLocation MODEL = AlienResources.entityGeoModelLocation(NAME);

    private static final ResourceLocation CHAINED_MODEL = AlienResources.entityGeoModelLocation(CHAINED_NAME);

    private static final ResourceLocation TEXTURE = AlienResources.entityTextureLocation(NAME);

    private static final ResourceLocation NETHER_TEXTURE = AlienResources.entityTextureLocation("nether_" + NAME);

    private static final ResourceLocation ABERRANT_TEXTURE = AlienResources.entityTextureLocation("aberrant_" + NAME);

    public OvipositorRenderer(EntityRendererProvider.Context context) {
        super(
            AzEntityRendererConfig.<Ovipositor>builder(OvipositorRenderer::modelFor, OvipositorRenderer::textureFor)
                .addRenderLayer(new EggsackRestraintsLayer())
                .build(),
            context
        );
        this.shadowRadius = 0.4F;
    }

    /** The captive chained-eggsack geo when she is a contained, inhibited queen; the normal ovipositor otherwise. */
    private static ResourceLocation modelFor(Ovipositor ovipositor) {
        return isChained(ovipositor) ? CHAINED_MODEL : MODEL;
    }

    /**
     * The strain of the queen underneath it - an eggsack has no variant of its own to read.
     * <p>
     * ⚠ FALLS BACK TO THE REMEMBERED STRAIN, NOT TO THE PLAIN SHEET. An abandoned sack lingers for two minutes with no
     * vehicle; asking the vehicle and defaulting on null is what turned an aberrant hive's eggsack black the instant
     * its queen was knocked off it. The ovipositor records her strain every tick while carried, so the last value is
     * still right once she is gone.
     * </p>
     */
    private static ResourceLocation textureFor(Ovipositor ovipositor) {
        var variant = ovipositor.getVehicle() instanceof Queen queen
            ? queen.getVariant()
            : ovipositor.getRoyalVariant();

        return switch (variant) {
            case NETHER -> NETHER_TEXTURE;
            case ABERRANT -> ABERRANT_TEXTURE;
            default -> TEXTURE;
        };
    }

    private static boolean isChained(Ovipositor ovipositor) {
        return ovipositor.getVehicle() instanceof Queen queen && queen.isInhibited() && queen.isContained();
    }
}
