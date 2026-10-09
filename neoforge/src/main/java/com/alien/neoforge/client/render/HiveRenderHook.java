package com.alien.neoforge.client.render;

import com.alien.client.render.dismemberment.LimbHitboxRenderer;
import com.alien.client.render.hive.HiveRenderer;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * NeoForge world-render hook for the hive debug wireframe overlay. Subscribed on the game event bus from
 * {@code AlienNeoForgeClient}. Draws on the {@code AFTER_TRANSLUCENT_BLOCKS} stage; {@link HiveRenderer} applies the
 * camera offset itself, so we pass the raw camera position.
 */
public final class HiveRenderHook {

    private HiveRenderHook() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        HiveRenderer.render(
            event.getPoseStack(),
            buffers,
            event.getCamera().getPosition()
        );
        LimbHitboxRenderer.render(
            event.getPoseStack(),
            buffers,
            event.getCamera().getPosition()
        );
        // ⭐⭐ THE WAYPOINT BEAM WAS FABRIC-ONLY. I checked the shipped 0.2.3 jar: this hook called only HiveRenderer
        // and LimbHitboxRenderer, and NOTHING anywhere in the NeoForge build called WaypointBeamRenderer. Tracked
        // queens have had no beacon beam on NeoForge at all - which is most players.
        com.alien.client.render.waypoint.WaypointBeamRenderer.render(
            event.getPoseStack(),
            buffers,
            event.getCamera().getPosition()
        );
        // The rescue channel bar, matching the Fabric hook.
        com.alien.client.render.rescue.RescueChannelBarRenderer.render(
            event.getPoseStack(),
            buffers,
            event.getCamera().getPosition()
        );
    }
}
