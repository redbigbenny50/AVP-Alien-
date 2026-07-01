package com.alien.client.render.block;

import com.alien.AlienResources;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a capture chain as a sagging, camera-facing ribbon textured with {@code capture_chain.png}. Coordinates are in
 * the caller's local (block-relative) space; {@code camLocal} is the camera in that same space, used to billboard each
 * segment so the chain reads as a line from any angle. The texture tiles along the chain's length.
 */
public final class ChainRenderer {

    private static final ResourceLocation CHAIN_TEXTURE = AlienResources.location("textures/entity/capture_chain.png");

    private static final int SEGMENTS = 16;

    /** Half the rendered ribbon width in blocks; full chain thickness is twice this. Bump up/down to taste. */
    private static final float HALF_WIDTH = 0.13F;

    /** World length over which the texture repeats once (tune for link spacing). */
    private static final double TILE_LENGTH = 0.5;

    /** Sag depth as a fraction of span length, capped. */
    private static final double SAG_FACTOR = 0.2;

    private static final double SAG_MAX = 1.25;

    private ChainRenderer() {}

    public static void render(
            PoseStack poseStack,
            MultiBufferSource source,
            Vec3 start,
            Vec3 end,
            Vec3 camLocal,
            int light
    ) {
        double dist = start.distanceTo(end);
        if (dist < 1.0e-4) {
            return;
        }
        double sag = Math.min(dist * SAG_FACTOR, SAG_MAX);

        Vec3[] points = new Vec3[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            double t = (double) i / SEGMENTS;
            double x = start.x + (end.x - start.x) * t;
            double y = start.y + (end.y - start.y) * t - sag * 4.0 * t * (1.0 - t);
            double z = start.z + (end.z - start.z) * t;
            points[i] = new Vec3(x, y, z);
        }

        VertexConsumer buffer = source.getBuffer(RenderType.entityCutoutNoCull(CHAIN_TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        float u = 0.0F;

        for (int i = 0; i < SEGMENTS; i++) {
            Vec3 a = points[i];
            Vec3 b = points[i + 1];
            Vec3 segment = b.subtract(a);
            double segLen = segment.length();
            if (segLen < 1.0e-5) {
                continue;
            }
            Vec3 segDir = segment.scale(1.0 / segLen);
            Vec3 mid = a.add(b).scale(0.5);
            Vec3 viewDir = mid.subtract(camLocal);
            Vec3 width = segDir.cross(viewDir);
            double wLen = width.length();
            width = wLen < 1.0e-5 ? new Vec3(HALF_WIDTH, 0.0, 0.0) : width.scale(HALF_WIDTH / wLen);

            float uNext = u + (float) (segLen / TILE_LENGTH);

            Vec3 a0 = a.subtract(width);
            Vec3 a1 = a.add(width);
            Vec3 b0 = b.subtract(width);
            Vec3 b1 = b.add(width);

            float nx = (float) segDir.x;
            float ny = (float) segDir.y;
            float nz = (float) segDir.z;

            // A single quad suffices: entityCutoutNoCull disables backface culling, so this face is drawn
            // from both sides. A second, coplanar back-face quad only causes z-fighting.
            quad(buffer, pose, a0, a1, b1, b0, u, uNext, light, nx, ny, nz);

            u = uNext;
        }
    }

    private static void quad(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            Vec3 p0,
            Vec3 p1,
            Vec3 p2,
            Vec3 p3,
            float uStart,
            float uEnd,
            int light,
            float nx,
            float ny,
            float nz
    ) {
        vertex(buffer, pose, p0, uStart, 0.0F, light, nx, ny, nz);
        vertex(buffer, pose, p1, uStart, 1.0F, light, nx, ny, nz);
        vertex(buffer, pose, p2, uEnd, 1.0F, light, nx, ny, nz);
        vertex(buffer, pose, p3, uEnd, 0.0F, light, nx, ny, nz);
    }

    private static void vertex(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            Vec3 p,
            float u,
            float v,
            int light,
            float nx,
            float ny,
            float nz
    ) {
        buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}