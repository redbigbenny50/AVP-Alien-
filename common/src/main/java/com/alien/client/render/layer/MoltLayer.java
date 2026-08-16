package com.alien.client.render.layer;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.blib.api.client.model.v1.AzBone;
import com.blib.api.client.render.v1.AzRendererPipelineContext;
import com.blib.api.client.render.v1.layer.AzRenderLayer;
import net.minecraft.client.renderer.RenderType;

import java.util.UUID;

public class MoltLayer<T> implements AzRenderLayer<UUID, T> {

    private static final float MOLT_INFLATE = 0.025F;

    /**
     * The shed skin is a DARK translucent shell, not a pale one.
     * <p>
     * These are multiplied against the alien's own texture, so a light warm tint (the previous 160/130/100) brightens
     * the copy instead of darkening it: at 50% alpha that reads as a solid pale duplicate one cube * larger than the
     * alien, which is why molting aliens looked swollen and fat rather than sheathed. Near-black keeps the silhouette
     * dark so the alpha actually reads as translucency ([stated] "it used to be a black transparent layer").
     * <p>
     * Not pure zero: a hair of value keeps the shell from flattening into a pure silhouette against a dark hive wall,
     * so the inflate still reads as a shell rather than a hole.
     */
    private static final int MOLT_TINT_R = 12;

    private static final int MOLT_TINT_G = 12;

    private static final int MOLT_TINT_B = 12;

    @Override
    public void preRender(AzRendererPipelineContext<UUID, T> context) {}

    @Override
    public void render(AzRendererPipelineContext<UUID, T> context) {
        var animatable = context.animatable();

        if (!(animatable instanceof Alien alien)) {
            return;
        }

        var alpha = alien.moltAlpha.get();

        if (alpha <= 0.001F) {
            return;
        }

        var config = context.rendererPipeline().config();
        var textureLocation = config.textureLocation(context.currentEntity(), animatable);
        var renderType = RenderType.entityTranslucent(textureLocation);

        var alphaInt = (int) (alpha * 128);
        var renderColor = (alphaInt << 24) | (MOLT_TINT_R << 16) | (MOLT_TINT_G << 8) | MOLT_TINT_B;

        context.setRenderType(renderType);
        context.setRenderColor(renderColor);
        context.setVertexConsumer(context.multiBufferSource().getBuffer(renderType));
        context.setCubeInflate(MOLT_INFLATE);

        context.rendererPipeline().reRender(context);

        context.setCubeInflate(0);
    }

    @Override
    public void renderForBone(AzRendererPipelineContext<UUID, T> context, AzBone bone) {}
}
