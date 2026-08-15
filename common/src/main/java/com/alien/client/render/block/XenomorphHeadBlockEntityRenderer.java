package com.alien.client.render.block;

import com.alien.common.gameplay.block.entity.xenomorph.head.XenomorphHeadBlockEntity;
import com.alien.common.gameplay.block.xenomorph.head.XenomorphHeadBlock;
import com.alien.common.gameplay.block.xenomorph.head.XenomorphWallHeadBlock;
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

public class XenomorphHeadBlockEntityRenderer implements BlockEntityRenderer<XenomorphHeadBlockEntity> {

    @Override
    public void render(
        @NotNull XenomorphHeadBlockEntity entity,
        float partialTick,
        @NotNull PoseStack poseStack,
        @NotNull MultiBufferSource source,
        int packedLight,
        int packedOverlay
    ) {
        var entry = entity.entry();

        if (entry == null) {
            return;
        }

        var state = entity.getBlockState();
        var block = state.getBlock();
        boolean isWall;

        poseStack.pushPose();

        if (block instanceof XenomorphHeadBlock) {
            poseStack.translate(0.5, 0.0, 0.5);
            int seg = state.getValue(XenomorphHeadBlock.ROTATION);
            float yawDeg = RotationSegment.convertToDegrees(seg);
            poseStack.mulPose(Axis.YP.rotationDegrees(-yawDeg));
            isWall = false;
        } else if (block instanceof XenomorphWallHeadBlock) {
            poseStack.translate(0.5, 0.5, 0.5);
            Direction facing = state.getValue(XenomorphWallHeadBlock.FACING);
            float yawDeg = facing.toYRot();
            poseStack.mulPose(Axis.YP.rotationDegrees(180f - yawDeg));
            isWall = true;
        } else {
            poseStack.popPose();
            return;
        }

        var mc = Minecraft.getInstance();
        var priorWall = BLibItemTransformOverrides.isRenderAsWallBlock();
        var priorGround = BLibItemTransformOverrides.isRenderAsGroundBlock();
        BLibItemTransformOverrides.setRenderAsWallBlock(isWall);
        BLibItemTransformOverrides.setRenderAsGroundBlock(!isWall);

        try {
            mc.getItemRenderer()
                .renderStatic(
                    new ItemStack(entry.head().get()),
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
}
