package com.alien.client.render.waypoint;

import com.alien.client.waypoint.ClientWaypointStore;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a vanilla-style beacon beam straight up from every pinned waypoint in the current dimension, so a player with
 * no map mod can still see where a tracked queen is from a distance. Loader-agnostic: each world-render hook supplies
 * the frame's {@link PoseStack}, a buffer source, and the camera position, then calls {@link #render}. Mirrors
 * {@code HiveRenderer}: translate the pose by {@code -camera} so world coordinates can be fed directly, draw, flush.
 */
public final class WaypointBeamRenderer {

    /** ARGB green, matching the PDA's terminal palette. */
    private static final int BEAM_COLOR = 0xFF33FF66;

    private static final int BEAM_HEIGHT = 1024;

    private static final float BEAM_RADIUS = 0.2F;

    private static final float GLOW_RADIUS = 0.28F;

    private WaypointBeamRenderer() {}

    public static void render(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 camera) {
        var waypoints = ClientWaypointStore.all();
        if (waypoints.isEmpty()) {
            return;
        }

        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) {
            return;
        }

        var dimension = level.dimension();
        long gameTime = level.getGameTime();
        // partialTick only smooths the beam texture scroll; 0 is fine and avoids a mapping-specific Minecraft call.
        float partialTick = 0.0F;

        boolean drewAny = false;
        for (var waypoint : waypoints) {
            if (!waypoint.dimension().equals(dimension)) {
                continue;
            }
            var pos = waypoint.pos();
            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            BeaconRenderer.renderBeaconBeam(
                poseStack,
                buffers,
                BeaconRenderer.BEAM_LOCATION,
                partialTick,
                1.0F,
                gameTime,
                0,
                BEAM_HEIGHT,
                BEAM_COLOR,
                BEAM_RADIUS,
                GLOW_RADIUS
            );
            poseStack.popPose();
            drewAny = true;
        }

        if (drewAny) {
            buffers.endBatch();
        }
    }
}
