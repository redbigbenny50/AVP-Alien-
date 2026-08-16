package com.alien.client.render.block;

import com.alien.client.render.QueenShackleAnchorCache;
import com.alien.common.gameplay.block.capture.anchor.AnchorBlock;
import com.alien.common.gameplay.block.entity.capture.anchor.AnchorBlockEntity;
import com.alien.common.registry.init.item.AlienItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Draws the anchor geo (as the item-as-block, the same path the head trophies use) oriented to its mount surface: floor
 * upright, ceiling flipped, wall tilted onto the surface, each yawed by FACING. The wall/ground transform mode is
 * toggled so the BLib template picks its {@code fixed_wall} vs {@code fixed} transform.
 * <p>
 * NOTE: the exact wall/ceiling rotations are a sensible first pass and may need a small in-game tuning tweak.
 */
public class AnchorBlockEntityRenderer implements BlockEntityRenderer<AnchorBlockEntity> {

    /**
     * Always render, even when the anchor block itself is outside the view frustum.
     * <p>
     * A block entity is normally culled with the chunk section it sits in, which is correct for something that only
     * draws inside its own block - and wrong here, because the chain we draw reaches from this anchor all the way to
     * the queen. Turn until the anchor leaves the frustum and the whole chain vanished with it, even though most of it
     * was still on screen. [stated] "when i look away from the chains at certain angles they completely vanish this
     * makes it hard to place and attach".
     * <p>
     * This is what vanilla does for the beacon beam and the structure block for the same reason. The cost is one draw
     * call per loaded anchor inside the view distance below, which is nothing at the handful a capture site uses.
     */
    @Override
    public boolean shouldRenderOffScreen(AnchorBlockEntity blockEntity) {
        return true;
    }

    /**
     * Chains have to stay visible from further out than a normal block entity, since you are usually backing away from
     * the queen while you place them. Vanilla default is 64.
     */
    @Override
    public int getViewDistance() {
        return 128;
    }

    /**
     * How far each plate is pushed into its mounting surface to seat flush, in block units (~0.43 px). Applied on the
     * axis that points into the surface for that orientation: the floor pushes down (−Y), the ceiling up (+Y), and the
     * wall into its face along the facing direction (so all four wall facings seat the same). One knob for all three;
     * tune if a plate floats above or sinks into its surface.
     */
    private static final double SEAT_OFFSET = 0.027;

    @Override
    public void render(
        @NotNull AnchorBlockEntity entity,
        float partialTick,
        @NotNull PoseStack poseStack,
        @NotNull MultiBufferSource source,
        int packedLight,
        int packedOverlay
    ) {
        var state = entity.getBlockState();
        if (!(state.getBlock() instanceof AnchorBlock)) {
            return;
        }

        var face = state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE);
        var facing = state.getValue(HorizontalDirectionalBlock.FACING);

        poseStack.pushPose();

        switch (face) {
            case FLOOR -> {
                poseStack.translate(0.5, 0.5 - SEAT_OFFSET, 0.5);
                poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
            }
            case CEILING -> {
                poseStack.translate(0.5, 0.5 + SEAT_OFFSET, 0.5);
                poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
            }
            case WALL -> {
                poseStack.translate(
                    0.5 - facing.getStepX() * SEAT_OFFSET,
                    0.5,
                    0.5 - facing.getStepZ() * SEAT_OFFSET
                );
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            }
        }

        // SEAT CORRECTION - [stated] tester bug: "when placed it renders at the top of its block space not the
        // bottom like a slab. ceiling and wall do the same." The item pipeline this routes through (vanilla's
        // -0.5 centering + AzItemRenderer's re-center) lands the GEO BASE half a block up local +Y from the origin
        // we just built, so the plate (authored y 0-8px, a bottom slab) drew as a TOP-half slab, and the same
        // half-block error followed the rotation onto ceilings and walls. Every face's rotation above is built so
        // local +Y points AWAY from its mount surface, so one pull-back seats all three: floor base lands at
        // -SEAT_OFFSET (pressed into the floor), the flipped ceiling base at 1.0+SEAT_OFFSET (flush underneath),
        // and the wall plate slides half a block onto its wall face at unchanged height. Applied in LOCAL space,
        // after the rotations, deliberately - that is what makes it one line instead of three per-face cases.
        poseStack.translate(0.0, -0.5, 0.0);

        // The BLibItemTransformOverrides wall/ground toggling that used to wrap this call is GONE, deliberately:
        // it selected the geo-bone template's fixed_wall/fixed_ground contexts, and the anchor moved to
        // AzItemRenderer (July 31), which never reads those overrides - the toggles were inert and misleading.
        var mc = Minecraft.getInstance();
        mc.getItemRenderer()
            .renderStatic(
                new ItemStack(AlienItems.ANCHOR.get()),
                ItemDisplayContext.FIXED,
                packedLight,
                packedOverlay,
                poseStack,
                source,
                entity.getLevel(),
                0
            );

        poseStack.popPose();

        renderChainIfBound(entity, partialTick, poseStack, source, packedLight);
    }

    /** Draws the capture chain from the anchor's bind point to the held mob, if any. */
    private void renderChainIfBound(
        AnchorBlockEntity entity,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource source,
        int packedLight
    ) {
        int netId = entity.getBoundMobNetId();
        if (netId < 0 || entity.getLevel() == null) {
            return;
        }
        Entity bound = entity.getLevel().getEntity(netId);
        if (!(bound instanceof LivingEntity mob)) {
            return;
        }

        BlockPos pos = entity.getBlockPos();
        Vec3 origin = new Vec3(pos.getX(), pos.getY(), pos.getZ());

        Vec3 start = entity.chainAnchorPoint().subtract(origin);
        Vec3 mobPoint = shackleAttachPoint(mob, entity.getShackleSlot(), partialTick);
        Vec3 end = mobPoint.subtract(origin);

        Vec3 cameraLocal = Minecraft.getInstance().gameRenderer.getMainCamera()
            .getPosition()
            .subtract(origin);

        ChainRenderer.render(poseStack, source, start, end, cameraLocal, packedLight);
    }

    // ---- queen shackle attach points -----------------------------------------------------------------------------

    /**
     * World attach point for a chain. For a queen shackle slot we read the live, animated bone world position that
     * {@code ShackleAnchorLayer} publishes each frame (so the chain tracks her through movement and attacks); if she
     * has not been rendered recently the cache is empty and we fall back to a generic body point. Non-queen mobs
     * ({@code slot < 0}) always use the generic point.
     */
    private static Vec3 shackleAttachPoint(LivingEntity mob, int slot, float partialTick) {
        if (slot >= 0) {
            Vec3 bonePos = QueenShackleAnchorCache.get(mob.getId(), boneIndexFor(slot));
            if (bonePos != null) {
                return bonePos;
            }
        }
        return mob.getPosition(partialTick).add(0.0, mob.getBbHeight() * 0.6, 0.0);
    }

    /**
     * Map a bind slot to its shackle bone, matching the reveal order and the eight-chain distribution: slots 0,4 → left
     * arm; 1,5 → right arm; 2,3,6,7 → neck.
     */
    private static int boneIndexFor(int slot) {
        return switch (slot % 4) {
            case 0 -> QueenShackleAnchorCache.LEFT_ARM;
            case 1 -> QueenShackleAnchorCache.RIGHT_ARM;
            default -> QueenShackleAnchorCache.NECK;
        };
    }
}
