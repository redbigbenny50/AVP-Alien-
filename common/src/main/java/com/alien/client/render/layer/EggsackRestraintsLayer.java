package com.alien.client.render.layer;

import com.alien.AlienResources;
import com.alien.common.gameplay.entity.living.alien.ovipositor.Ovipositor;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.blib.api.client.model.v1.AzBone;
import com.blib.api.client.render.v1.AzRendererPipelineContext;
import com.blib.api.client.render.v1.layer.AzRenderLayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Overlays the iron restraints onto the chained eggsack. Mirrors {@link MoltLayer}'s second-pass approach: re-renders
 * the same baked geo with the restraints texture (a cutout overlay that's transparent everywhere except the iron
 * bands), slightly inflated so it sits on the sack surface without z-fighting. Only drawn for the chained-eggsack
 * presentation — an ovipositor riding a contained, inhibited (captive) queen — so a normal founding ovipositor is never
 * touched.
 */
public class EggsackRestraintsLayer implements AzRenderLayer<UUID, Ovipositor> {

    private static final ResourceLocation RESTRAINTS_TEXTURE =
        AlienResources.entityTextureLocation("eggsack_iron_restraints");

    private static final float RESTRAINTS_INFLATE = 0.02F;

    @Override
    public void preRender(AzRendererPipelineContext<UUID, Ovipositor> context) {}

    @Override
    public void render(AzRendererPipelineContext<UUID, Ovipositor> context) {
        var ovipositor = context.animatable();
        if (!(ovipositor.getVehicle() instanceof Queen queen) || !queen.isInhibited() || !queen.isContained()) {
            return;
        }

        var renderType = RenderType.entityCutoutNoCull(RESTRAINTS_TEXTURE);
        context.setRenderType(renderType);
        context.setRenderColor(0xFFFFFFFF);
        context.setVertexConsumer(context.multiBufferSource().getBuffer(renderType));
        context.setCubeInflate(RESTRAINTS_INFLATE);

        context.rendererPipeline().reRender(context);

        context.setCubeInflate(0);
    }

    @Override
    public void renderForBone(AzRendererPipelineContext<UUID, Ovipositor> context, AzBone bone) {}
}
