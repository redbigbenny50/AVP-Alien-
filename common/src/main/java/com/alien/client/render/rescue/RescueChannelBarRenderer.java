package com.alien.client.render.rescue;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

/**
 * ⭐⭐⭐ A BAR OVER A XENOMORPH THAT IS REVIVING A DOWNED ROYAL.
 * <p>
 * [stated] "have a bar above their head when they do it that fills. so you can see them attempting."
 * </p>
 * <p>
 * ⚠⚠ THE POINT IS THAT THE PLAYER CAN ACT ON IT. The kin rescue takes ten seconds and any damage to the royal breaks it
 * - but a channel you cannot SEE is indistinguishable from her healing for no reason, which is exactly how the "I hit
 * her and she instantly healed" reports read before the timing was fixed. Showing the attempt is what turns it from
 * something that happens to you into something you can interrupt.
 * </p>
 * <p>
 * ⚠ DRAWN IN THE WORLD PASS, not as a name tag. {@code renderNameTag} only fires for entities that already show a name,
 * so an ordinary drone would never draw one - and every caste has its own renderer with no shared base, so hooking them
 * individually would mean fifteen edits that the next caste would forget.
 * </p>
 */
public final class RescueChannelBarRenderer {

    /** How far above the entity's own height the bar floats. */
    private static final float HEIGHT_ABOVE_HEAD = 0.55F;

    private static final float WIDTH = 0.9F;

    private static final float THICKNESS = 0.11F;

    /** Beyond this the bar is not drawn - it is a tactical cue, not a map marker. */
    private static final double MAX_VIEW_DISTANCE = 48.0;

    private RescueChannelBarRenderer() {}

    public static void render(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 cameraPos) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        for (var entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof Alien alien)) {
                continue;
            }
            var progress = alien.getRescueChannelProgress();
            if (progress <= 0.0F) {
                continue;
            }
            if (alien.distanceToSqr(cameraPos) > MAX_VIEW_DISTANCE * MAX_VIEW_DISTANCE) {
                continue;
            }

            // ⚠ Interpolated, not the raw position: at 20 updates a second an un-lerped bar visibly stutters against
            // the smoothly drawn body it is supposed to belong to.
            var partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
            var x = Mth_lerp(partial, alien.xo, alien.getX()) - cameraPos.x;
            var y = Mth_lerp(partial, alien.yo, alien.getY()) - cameraPos.y + alien.getBbHeight() + HEIGHT_ABOVE_HEAD;
            var z = Mth_lerp(partial, alien.zo, alien.getZ()) - cameraPos.z;

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            // Billboard: face the camera so the bar reads from any angle.
            poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());

            var consumer = buffers.getBuffer(RenderType.debugQuads());
            // The empty track behind, then the filled portion over it.
            quad(poseStack, consumer, -WIDTH / 2.0F, WIDTH / 2.0F, 0.25F, 0.25F, 0.25F, 0.85F);
            var filledRight = -WIDTH / 2.0F + WIDTH * Math.min(1.0F, progress);
            quad(poseStack, consumer, -WIDTH / 2.0F, filledRight, 0.35F, 0.85F, 0.25F, 1.0F);

            poseStack.popPose();
        }
        buffers.endBatch(RenderType.debugQuads());
    }

    private static float Mth_lerp(float delta, double from, double to) {
        return (float) (from + (to - from) * delta);
    }

    private static void quad(
        PoseStack poseStack,
        VertexConsumer consumer,
        float left,
        float right,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        if (right <= left) {
            return;
        }
        var matrix = poseStack.last().pose();
        var top = THICKNESS / 2.0F;
        var bottom = -THICKNESS / 2.0F;
        consumer.addVertex(matrix, left, bottom, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, right, bottom, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, right, top, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, left, top, 0.0F).setColor(red, green, blue, alpha);
    }
}
