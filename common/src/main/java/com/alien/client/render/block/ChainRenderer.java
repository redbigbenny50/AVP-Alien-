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
 * Draws a capture chain as a sagging '+' cross-section ribbon textured with {@code capture_chain.png}. Coordinates are
 * in the caller's local (block-relative) space. Two perpendicular ribbons around the chain axis keep it visible from
 * every angle (a single camera-facing ribbon vanishes when viewed end-on). The texture tiles along the chain's length.
 */
public final class ChainRenderer {

    private static final ResourceLocation CHAIN_TEXTURE = AlienResources.location("textures/entity/capture_chain.png");

    private static final int SEGMENTS = 16;

    /** Half the rendered ribbon width in blocks; full chain thickness is twice this. Bump up/down to taste. */
    private static final float HALF_WIDTH = 0.20F;

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
        // camLocal retained for call-site compatibility; the cross-section no longer needs to billboard to the camera.
        if (camLocal == null) {
            return;
        }
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

        // Two perpendicular edge pairs per point form a '+' cross-section around the chain axis, so the chain stays
        // visible from any angle. Per-point (shared) edges also keep adjacent segments flush -- no sliver gaps at
        // bends.
        Vec3[] aMinus = new Vec3[SEGMENTS + 1];
        Vec3[] aPlus = new Vec3[SEGMENTS + 1];
        Vec3[] bMinus = new Vec3[SEGMENTS + 1];
        Vec3[] bPlus = new Vec3[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            Vec3 prev = points[Math.max(i - 1, 0)];
            Vec3 next = points[Math.min(i + 1, SEGMENTS)];
            Vec3 tangent = next.subtract(prev);
            double tLen = tangent.length();
            tangent = tLen < 1.0e-5 ? new Vec3(0.0, 0.0, 1.0) : tangent.scale(1.0 / tLen);

            // Reference axis not parallel to the tangent, so the cross-section is well defined even for a vertical
            // chain.
            Vec3 reference = Math.abs(tangent.y) > 0.99 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
            Vec3 perpA = tangent.cross(reference);
            perpA = perpA.scale(HALF_WIDTH / perpA.length());
            Vec3 perpB = tangent.cross(perpA);
            perpB = perpB.scale(HALF_WIDTH / perpB.length());

            aMinus[i] = points[i].subtract(perpA);
            aPlus[i] = points[i].add(perpA);
            bMinus[i] = points[i].subtract(perpB);
            bPlus[i] = points[i].add(perpB);
        }

        VertexConsumer buffer = source.getBuffer(RenderType.entityCutoutNoCull(CHAIN_TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        float u = 0.0F;

        for (int i = 0; i < SEGMENTS; i++) {
            Vec3 a = points[i];
            Vec3 b = points[i + 1];
            double segLen = b.subtract(a).length();
            if (segLen < 1.0e-5) {
                continue;
            }
            Vec3 segDir = b.subtract(a).scale(1.0 / segLen);
            float uNext = u + (float) (segLen / TILE_LENGTH);

            float nx = (float) segDir.x;
            float ny = (float) segDir.y;
            float nz = (float) segDir.z;

            // Both ribbons; entityCutoutNoCull draws each from both sides.
            quad(buffer, pose, aMinus[i], aPlus[i], aPlus[i + 1], aMinus[i + 1], u, uNext, light, nx, ny, nz);
            quad(buffer, pose, bMinus[i], bPlus[i], bPlus[i + 1], bMinus[i + 1], u, uNext, light, nx, ny, nz);

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
