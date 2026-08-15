package com.alien.client.render.item;

import com.alien.AlienResources;
import com.blib.api.client.render.v1.item.AzItemRenderer;
import com.blib.api.client.render.v1.item.AzItemRendererConfig;
import net.minecraft.resources.ResourceLocation;

/**
 * The inventory/hand model for the irradiated_resin_container.
 * <p>
 * ⚠ WITHOUT ONE OF THESE THE ITEM IS INVISIBLE IN THE GUI. Its model is {@code builtin/entity}, which tells vanilla
 * "something else draws this" - the block gets a BlockEntityRenderer, but the ITEM form has no renderer at all unless
 * it is registered here. That is why the container had no icon.
 * </p>
 * <p>
 * ⚠ AzItemRenderer, NOT the geo-bone/template path: the template renderer replaces vanilla display handling with BLib's
 * own transform system, so every Blockbench value would have to be re-derived by hand. This leaves the display block
 * alone. Same choice as the anchor, inhibitor and tracker.
 * </p>
 */
public class IrradiatedResinContainerItemRenderer extends AzItemRenderer {

    private static final ResourceLocation MODEL = AlienResources.location("geo/block/resin_container.geo.json");

    private static final ResourceLocation TEXTURE = AlienResources.location("textures/block/irradiated_resin_container.png");

    public IrradiatedResinContainerItemRenderer() {
        super(
            AzItemRendererConfig.builder(MODEL, TEXTURE)
                .setScale(1.0F)
                .build()
        );
    }
}
