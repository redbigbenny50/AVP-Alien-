package com.alien.client.render.block;

import com.alien.common.gameplay.block.entity.queen.QueenHeadBlockEntity;
import com.alien.common.gameplay.block.queen.QueenHeadBlock;
import com.alien.common.gameplay.block.queen.QueenHeadVariant;
import com.alien.common.gameplay.block.queen.QueenWallHeadBlock;
import com.alien.common.registry.init.item.AlienItems;
import com.blib.api.client.render.v1.item.BLibItemTransformOverrides;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import org.jetbrains.annotations.NotNull;

/**
 * Renders placed {@link QueenHeadBlockEntity}s by reusing the corresponding queen-head item's
 * {@link com.blib.api.client.render.v1.item.BLibGeoBoneItemRenderer} pipeline. We delegate to
 * {@link net.minecraft.client.renderer.entity.ItemRenderer#renderStatic} with {@link ItemDisplayContext#FIXED}, which
 * is the closest semantic for "stationary item display in the world." The user can tune {@code FIXED} via the regular
 * transform-tune command to fit it to a block.
 * <p>
 * Floor blocks rotate around Y based on the {@code ROTATION_16} blockstate (16-step yaw); wall blocks pick a 90°
 * increment from {@code FACING}, plus a translate so the head visually sticks out of the wall instead of sitting at the
 * block's volumetric center.
 */
public class QueenHeadBlockEntityRenderer implements BlockEntityRenderer<QueenHeadBlockEntity> {

    public QueenHeadBlockEntityRenderer() {
        // Block entity renderer providers pass a Context arg in MC 1.21; we don't need anything from it,
        // but having a default constructor (or one that accepts and ignores Context) keeps the registration
        // call site simple — the loader-specific hooks instantiate the renderer per-block-entity-type.
    }

    @Override
    public void render(
        @NotNull QueenHeadBlockEntity entity,
        float partialTick,
        @NotNull PoseStack poseStack,
        @NotNull MultiBufferSource source,
        int packedLight,
        int packedOverlay
    ) {
        var variant = entity.variant();

        if (variant == null) {
            return;
        }

        var stack = itemStackFor(variant);
        var state = entity.getBlockState();
        var block = state.getBlock();
        boolean isWall;

        poseStack.pushPose();

        if (block instanceof QueenHeadBlock) {
            // Floor: center yaw at block center, rotate by ROTATION_16 segments to match player facing
            // at placement time. The rest of the pose comes from the renderer's `.fixed(...)` transform.
            poseStack.translate(0.5, 0.0, 0.5);
            int seg = state.getValue(QueenHeadBlock.ROTATION);
            float yawDeg = RotationSegment.convertToDegrees(seg);
            poseStack.mulPose(Axis.YP.rotationDegrees(-yawDeg));
            isWall = false;
        } else if (block instanceof QueenWallHeadBlock) {
            // Wall: anchor at block center on the wall side. The wall-specific tilt and finer
            // positioning come entirely from the renderer's `.fixedWall(...)` transform (selected by
            // BLib when RENDER_AS_WALL_BLOCK is true), so this pose stack just establishes the block
            // location and faces the head outward.
            poseStack.translate(0.5, 0.5, 0.5);
            Direction facing = state.getValue(QueenWallHeadBlock.FACING);
            float yawDeg = facing.toYRot();
            poseStack.mulPose(Axis.YP.rotationDegrees(180f - yawDeg));
            isWall = true;
        } else {
            poseStack.popPose();
            return;
        }

        // Flip BLib's fixed-surface flags so the geo-bone item renderer picks the block-placement transform that
        // matches
        // this render. Wrapped in try/finally so an exception in renderStatic doesn't leak the flag to later renders.
        var mc = Minecraft.getInstance();
        var priorWall = BLibItemTransformOverrides.isRenderAsWallBlock();
        var priorGround = BLibItemTransformOverrides.isRenderAsGroundBlock();
        BLibItemTransformOverrides.setRenderAsWallBlock(isWall);
        BLibItemTransformOverrides.setRenderAsGroundBlock(!isWall);

        try {
            mc.getItemRenderer()
                .renderStatic(
                    stack,
                    ItemDisplayContext.FIXED,
                    packedLight,
                    packedOverlay,
                    poseStack,
                    source,
                    entity.getLevel(),
                    0
                );
        } finally {
            BLibItemTransformOverrides.setRenderAsWallBlock(priorWall);
            BLibItemTransformOverrides.setRenderAsGroundBlock(priorGround);
        }

        poseStack.popPose();
    }

    private static ItemStack itemStackFor(QueenHeadVariant variant) {
        return switch (variant) {
            case QUEEN -> new ItemStack(AlienItems.QUEEN_HEAD.get());
            case ABERRANT -> new ItemStack(AlienItems.ABERRANT_QUEEN_HEAD.get());
            case IRRADIATED -> new ItemStack(AlienItems.IRRADIATED_QUEEN_HEAD.get());
            case NETHER -> new ItemStack(AlienItems.NETHER_QUEEN_HEAD.get());
        };
    }
}
