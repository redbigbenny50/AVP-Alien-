package com.alien.neoforge.mixin;

import com.alien.client.render.block.AnchorBlockEntityRenderer;
import com.alien.common.gameplay.block.entity.capture.anchor.AnchorBlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Stop NeoForge frustum-culling an anchor whose chain reaches off screen.
 * <p>
 * WHY THIS EXISTS, AND WHY IT IS NEOFORGE-ONLY. {@code shouldRenderOffScreen} was supposed to be the whole fix: it
 * moves the block entity into {@code LevelRenderer.globalBlockEntities}, which vanilla renders without any frustum
 * test. NeoForge does not. Its patched loop reads:
 *
 * <pre>
 * for (BlockEntity be : this.globalBlockEntities) {
 *     if (!ClientHooks.isBlockEntityRendererVisible(dispatcher, be, frustum)) continue;
 * </pre>
 *
 * and that hook is {@code frustum.isVisible(renderer.getRenderBoundingBox(be))}, whose default is
 * {@code new AABB(blockEntity.getBlockPos())} - the anchor's own single block. So a global block entity on NeoForge is
 * still culled the moment its BLOCK leaves the view, no matter what shouldRenderOffScreen said, and the chain vanished
 * with it while most of its length was on screen. [stated] "the culling for the chains is still happening... you can
 * only see 2 at this angle."
 * <p>
 * {@code getRenderBoundingBox} comes from {@code IBlockEntityRendererExtension}, which only exists on NeoForge, so it
 * cannot be overridden in the common renderer - hence a loader mixin rather than a plain override.
 * <p>
 * {@link AABB#INFINITE} is what the NeoForge javadoc itself nominates for "should be visible everywhere". A box
 * spanning anchor and mob would be tighter, but the mob has to be resolved by network id every frame to build it and an
 * unbound anchor draws nothing anyway - the render method returns immediately - so the only cost here is one frustum
 * test that always passes.
 */
@Mixin(AnchorBlockEntityRenderer.class)
public class MixinAnchorBlockEntityRenderer_RenderBox {

    /**
     * Merged into the renderer by Mixin - NOT an @Overwrite, because the target class does not declare this method at
     * all. It is a default on {@code IBlockEntityRendererExtension}, and a merged method of the same signature takes
     * precedence over an inherited default exactly as a normal override would.
     */
    public AABB getRenderBoundingBox(AnchorBlockEntity blockEntity) {
        return AABB.INFINITE;
    }
}
