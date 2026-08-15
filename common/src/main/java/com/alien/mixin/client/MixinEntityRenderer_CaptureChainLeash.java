package com.alien.mixin.client;

import com.alien.client.render.CaptureChainLeashRenderer;
import com.alien.client.render.CaptureHoldClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws the capture chain for a held mob. The capture chain no longer uses vanilla leashing, so there is no vanilla
 * rope to suppress — this is a pure, additive draw: at the head of {@code EntityRenderer#render} (the same axis-aligned
 * entity-local frame vanilla uses for the leash) we check the client hold map and, if this entity is held, draw the
 * chain ribbon from the holder to it. Because the queen's BLib renderer calls {@code super.render()}, this single
 * inject covers her and every vanilla-rendered mob alike.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer_CaptureChainLeash {

    /**
     * Keep a chained mob renderable while the chain to its holder is on screen.
     * <p>
     * The chain is drawn from the HELD entity's renderer, so when that entity is frustum-culled the chain goes with it
     * - which is why turning away made a chain vanish even with most of its length still in view, and why the chain you
     * are carrying disappeared depending which way you faced. [stated] "depending how im facing the chain im holding
     * vanishes".
     * <p>
     * Vanilla has the same problem with leads and solves it the same way: widen the visibility test to the box that
     * spans both ends. Returning true only when that combined box is visible keeps the saving for everything that is
     * genuinely off screen.
     */
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void avp_alien$keepChainedMobRenderable(
        Entity entity,
        Frustum frustum,
        double cameraX,
        double cameraY,
        double cameraZ,
        CallbackInfoReturnable<Boolean> cir
    ) {
        Entity holder = CaptureHoldClientState.holderOf(entity);
        if (holder == null) {
            return;
        }
        // The span from the holder to the held mob, padded so the sag of the ribbon is inside the test too.
        var span = new AABB(entity.position(), holder.position()).inflate(2.0);
        if (frustum.isVisible(span)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void avp_alien$drawCaptureChain(
        Entity entity,
        float entityYaw,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        CallbackInfo ci
    ) {
        Entity holder = CaptureHoldClientState.holderOf(entity);
        if (holder != null) {
            CaptureChainLeashRenderer.render(entity, holder, partialTick, poseStack, buffer);
        }
    }
}
