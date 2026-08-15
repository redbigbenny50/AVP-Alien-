package com.alien.client.render.layer;

import com.alien.client.render.QueenShackleAnchorCache;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.blib.api.client.model.v1.AzBone;
import com.blib.api.client.render.v1.AzRendererPipelineContext;
import com.blib.api.client.render.v1.layer.AzRenderLayer;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Publishes the queen's three shackle bones' live world positions every frame so the capture-chain anchors can draw to
 * the actual animated attach points -- following her as she moves, swings, and lunges -- instead of fixed body offsets.
 * As a render layer it runs after the base model is posed, so {@link AzBone#getWorldPosition()} returns this frame's
 * transform; matrix tracking on these bones is enabled in {@code QueenAnimator}. Hidden bones (their chain not yet
 * attached) are skipped -- nothing draws to them.
 */
public class ShackleAnchorLayer<T> implements AzRenderLayer<UUID, T> {

    @Override
    public void preRender(AzRendererPipelineContext<UUID, T> context) {}

    @Override
    public void render(AzRendererPipelineContext<UUID, T> context) {
        if (!(context.animatable() instanceof Queen queen)) {
            return;
        }
        var model = context.bakedModel();
        publish(queen.getId(), QueenShackleAnchorCache.LEFT_ARM, model.getBoneOrNull("gLeftArmShackle"));
        publish(queen.getId(), QueenShackleAnchorCache.RIGHT_ARM, model.getBoneOrNull("gRightArmShackle"));
        publish(queen.getId(), QueenShackleAnchorCache.NECK, model.getBoneOrNull("gNeckShackle"));
    }

    @Override
    public void renderForBone(AzRendererPipelineContext<UUID, T> context, AzBone bone) {}

    private static void publish(int entityId, int boneIndex, AzBone bone) {
        if (bone == null || bone.isHidden()) {
            return;
        }
        var wp = bone.getWorldPosition();
        QueenShackleAnchorCache.put(entityId, boneIndex, new Vec3(wp.x, wp.y, wp.z));
    }
}
