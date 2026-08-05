package com.alien.client.render.item;

import com.alien.AlienResources;
import com.blib.api.client.render.v1.item.AzItemRenderer;
import com.blib.api.client.render.v1.item.AzItemRendererConfig;

import net.minecraft.resources.ResourceLocation;

/**
 * Item renderer for the Anchor.
 * <p>
 * Uses {@link AzItemRenderer}, NOT {@code BLibGeoBoneItemRenderer}. The distinction is the whole reason this class
 * exists: the geo-bone renderer replaces vanilla's display handling with BLib's own template system, where translations
 * are in BLOCKS rather than sixteenths, nothing is pre-centred the way vanilla centres a block model, and every
 * Blockbench value therefore has to be re-derived by hand. AzItemRenderer leaves vanilla's display transforms alone, so
 * the {@code display} block in {@code models/item/anchor.json} is the raw Blockbench export and behaves exactly as
 * previewed.
 * <p>
 * Animation is unaffected - the dropship's oxygenator is animated through this same renderer.
 */
public class AnchorItemRenderer extends AzItemRenderer {

    private static final ResourceLocation MODEL = AlienResources.location("geo/block/anchor.geo.json");

    private static final ResourceLocation TEXTURE = AlienResources.location("textures/block/anchor.png");

    public AnchorItemRenderer() {
        super(
            AzItemRendererConfig.builder(MODEL, TEXTURE)
                .setScale(1.0F)
                .build()
        );
    }
}
