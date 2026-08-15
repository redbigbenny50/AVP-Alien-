package com.alien.client.render.hive;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-agnostic draw logic for the hive render overlay. Each loader's world-render hook supplies the frame's
 * {@link PoseStack}, a buffer source, and the camera position, then calls {@link #render}.
 * <p>
 * Three see-through wireframe layers per cached hive:
 * <ul>
 * <li><b>Claimed chunk columns</b> (cyan) - full world-height box per claimed chunk: ownership.</li>
 * <li><b>Slab band</b> (amber) - the strict [floorY, ceilingY) built-hive level.</li>
 * <li><b>Bleed zone</b> (magenta) - the +/-4 margins above/below the slab.</li>
 * </ul>
 * <p>
 * See-through-walls is achieved by disabling the depth test around the line flush (the lines are drawn regardless of
 * occluding terrain), then restoring depth state. We translate the pose by {@code -camera} so the verified
 * {@code renderLineBox(PoseStack, ...)} overload can be fed raw world coordinates.
 */
public final class HiveRenderer {

    private static final int BLEED = 4;

    // r, g, b, a. Claim columns are dimmer so the slab reads as the prominent layer.
    private static final float[] CLAIM_RGBA = { 0.2f, 0.7f, 1.0f, 0.35f };

    private static final float[] SLAB_RGBA = { 1.0f, 0.15f, 0.15f, 1.0f };

    private static final float[] BLEED_RGBA = { 0.95f, 0.2f, 0.95f, 0.7f };

    private HiveRenderer() {}

    public static void render(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 camera) {
        var hives = ClientHiveRenderCache.current();
        if (hives.isEmpty()) {
            return;
        }

        var level = Minecraft.getInstance().level;
        var worldBottom = level != null ? level.getMinBuildHeight() : -64;
        var worldTop = level != null ? level.getMaxBuildHeight() : 320;

        var consumer = buffers.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        for (var hive : hives) {
            var floorY = hive.floorY();
            var ceilingY = hive.ceilingY();

            for (var chunk : hive.chunks()) {
                var minX = (double) chunk.getMinBlockX();
                var minZ = (double) chunk.getMinBlockZ();
                var maxX = (double) chunk.getMaxBlockX() + 1;
                var maxZ = (double) chunk.getMaxBlockZ() + 1;

                // Claimed chunk column (cyan) - full world height: true ownership extent.
                box(poseStack, consumer, minX, worldBottom, minZ, maxX, worldTop, maxZ, CLAIM_RGBA);
                // Slab band (red) - drawn as a TIGHT cage: the outer box plus a horizontal rectangle loop at every
                // Y level within the band, so the slab reads as a dense band rather than a single hollow outline and
                // stands out from the sparse claim columns / bleed shell.
                box(poseStack, consumer, minX, floorY, minZ, maxX, ceilingY, maxZ, SLAB_RGBA);
                for (var y = floorY; y <= ceilingY; y++) {
                    // A near-flat box at height y is a horizontal rectangle loop at that level.
                    box(poseStack, consumer, minX, y, minZ, maxX, y, maxZ, SLAB_RGBA);
                }
                // Bleed zone (magenta) - a 4-block shell around ALL SIX faces of the slab (top, bottom, and the four
                // horizontal sides). Drawn as one box expanded by BLEED on every axis, with the slab nested inside.
                // NOTE: per-chunk this draws a full shell; where chunks are adjacent the interior shell faces overlap
                // the neighbour's slab. That is acceptable for a debug overlay - it reads as a continuous margin around
                // the whole claimed footprint.
                box(
                    poseStack,
                    consumer,
                    minX - BLEED,
                    floorY - BLEED,
                    minZ - BLEED,
                    maxX + BLEED,
                    ceilingY + BLEED,
                    maxZ + BLEED,
                    BLEED_RGBA
                );
            }
        }

        poseStack.popPose();

        // Flush the line buffer with depth-testing OFF so the wireframe shows through terrain (see it from any Y
        // level),
        // then restore depth state so we don't affect later rendering.
        RenderSystem.disableDepthTest();
        buffers.endBatch(RenderType.lines());
        RenderSystem.enableDepthTest();
    }

    private static void box(
        PoseStack poseStack,
        com.mojang.blaze3d.vertex.VertexConsumer consumer,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        float[] rgba
    ) {
        LevelRenderer.renderLineBox(
            poseStack,
            consumer,
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ,
            rgba[0],
            rgba[1],
            rgba[2],
            rgba[3]
        );
    }
}
