package com.alien.fabric.client.render;

import com.alien.client.render.hive.HiveRenderer;
import com.alien.client.render.waypoint.WaypointBeamRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Fabric world-render hook for the hive debug wireframe overlay. Registered from {@code AlienFabricClient}. Fires on
 * {@code AFTER_TRANSLUCENT}, where vanilla expects camera-relative vertex coordinates — {@link HiveRenderer} handles
 * the camera offset itself.
 */
public final class HiveRenderHook {

    private HiveRenderHook() {}

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            var consumers = context.consumers();
            if (!(consumers instanceof MultiBufferSource.BufferSource buffers)) {
                return;
            }
            HiveRenderer.render(
                context.matrixStack(),
                buffers,
                context.camera().getPosition()
            );
            WaypointBeamRenderer.render(
                context.matrixStack(),
                buffers,
                context.camera().getPosition()
            );
        });
    }
}
