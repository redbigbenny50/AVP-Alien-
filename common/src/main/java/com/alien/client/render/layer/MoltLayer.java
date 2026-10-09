package com.alien.client.render.layer;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.blib.api.client.model.v1.AzBone;
import com.blib.api.client.render.v1.AzRendererPipelineContext;
import com.blib.api.client.render.v1.layer.AzRenderLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

import java.util.UUID;

public class MoltLayer<T> implements AzRenderLayer<UUID, T> {

    private static final float MOLT_INFLATE = 0.025F;

    /**
     * The shell starts fading out at this distance from the camera (blocks)...
     */
    private static final double FADE_START = 16.0;

    /**
     * ...and is gone at this one. The shell is a full second pass of the model, plus a translucent batch that has to be
     * sorted every time the next alien starts rendering, so skipping it where it's a few dark pixels saves the most.
     * Fading rather than cutting off keeps it from popping.
     */
    private static final double FADE_END = 32.0;

    private static final double FADE_START_SQR = FADE_START * FADE_START;

    private static final double FADE_END_SQR = FADE_END * FADE_END;

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

        // Distance fade: checked before any render state is touched, so far-away molting aliens cost nothing here.
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        var distanceSqr = alien.distanceToSqr(camera);

        if (distanceSqr >= FADE_END_SQR) {
            return;
        }

        if (distanceSqr > FADE_START_SQR) {
            var distance = Math.sqrt(distanceSqr);
            alpha *= (float) Mth.clamp((FADE_END - distance) / (FADE_END - FADE_START), 0.0, 1.0);
        }

        var alphaInt = (int) (alpha * 128);

        if (alphaInt <= 0) {
            return;
        }

        var config = context.rendererPipeline().config();
        var textureLocation = config.textureLocation(context.currentEntity(), animatable);
        var renderType = RenderType.entityTranslucent(textureLocation);
        var renderColor = (alphaInt << 24) | (MOLT_TINT_R << 16) | (MOLT_TINT_G << 8) | MOLT_TINT_B;

        var previousRenderType = context.renderType();
        var previousRenderColor = context.renderColor();
        var previousVertexConsumer = context.vertexConsumer();
        var previousCubeInflate = context.cubeInflate();

        context.setRenderType(renderType);
        context.setRenderColor(renderColor);
        context.setVertexConsumer(context.multiBufferSource().getBuffer(renderType));
        context.setCubeInflate(MOLT_INFLATE);

        try {
            context.rendererPipeline().reRender(context);
        } finally {
            // Leave the context as later layers expect it (e.g. a glow layer re-rendering after this one).
            context.setCubeInflate(previousCubeInflate);
            context.setRenderType(previousRenderType);
            context.setRenderColor(previousRenderColor);
            context.setVertexConsumer(previousVertexConsumer);
        }
    }

    @Override
    public void renderForBone(AzRendererPipelineContext<UUID, T> context, AzBone bone) {}
}
