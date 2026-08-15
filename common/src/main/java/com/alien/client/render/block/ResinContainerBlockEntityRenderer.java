package com.alien.client.render.block;

import com.alien.client.animation.block.ResinContainerAnimator;
import com.alien.common.gameplay.block.entity.container.ResinContainerBlockEntity;
import com.blib.api.client.render.v1.block.AzBlockEntityRenderer;
import com.blib.api.client.render.v1.block.AzBlockEntityRendererConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

/**
 * Draws the resin container and its opening ribs.
 * <h2>⚠ THE TEXTURE IS PER STRAIN, THE MODEL IS NOT</h2> All four strains share one geo; only the sheet differs. The
 * texture provider is therefore a function of the block entity, resolved from whichever block it is sitting in - which
 * is also why one block entity type serves all four.
 * <h2>⚠ SIX-WAY ROTATION IS DONE HERE, NOT IN THE BLOCKSTATE</h2> The model has one authored orientation. Rotating it
 * in the pose stack keeps the blockstate to a single variant and means the ceiling and wall cases need no extra model
 * files - a blockstate-driven version would need six.
 */
public class ResinContainerBlockEntityRenderer extends AzBlockEntityRenderer<ResinContainerBlockEntity> {

    private static final ResourceLocation MODEL =
        ResourceLocation.fromNamespaceAndPath("avp_alien", "geo/block/resin_container.geo.json");

    /**
     * ⚠⚠ THE ANIMATOR GOES THROUGH THE CONFIG, NOT AN OVERRIDE OF getAnimator().
     * <p>
     * I originally overrode {@code getAnimator()} to return {@code new ResinContainerAnimator()}. That CRASHED the
     * render thread: {@code AzBlockEntityRenderer} keeps a REUSED animator that the pipeline initialises once, and
     * {@code getAnimator()} is its accessor - handing back a fresh instance on every call returned an animator whose
     * {@code currentContext} had never been set, so {@code AzAnimator.animate} dereferenced null.
     * </p>
     * <p>
     * {@code setAnimatorProvider} is the supported hook: the pipeline calls the supplier ONCE and owns the result.
     * </p>
     */
    public ResinContainerBlockEntityRenderer() {
        super(
            AzBlockEntityRendererConfig.<ResinContainerBlockEntity>builder(
                container -> MODEL,
                ResinContainerBlockEntityRenderer::textureFor
            ).setAnimatorProvider(ResinContainerAnimator::new).build()
        );
    }

    @Override
    public void render(
        ResinContainerBlockEntity container,
        float partialTick,
        @NotNull PoseStack poseStack,
        @NotNull MultiBufferSource source,
        int packedLight,
        int packedOverlay
    ) {
        // ⭐⭐ BLIB APPLIES THE YAW; IT NEVER APPLIES A TILT.
        //
        // AzBlockEntityModelRenderer does translate(0.5, 0, 0.5) then rotateBlock(FACING), and rotateBlock only
        // ever issues Axis.YP rotations - verified in the bytecode. That is the whole horizontal orientation, and
        // adding any yaw of my own here is what made the model stop turning at all.
        //
        // ⚠ SO THIS ADDS THE TILT AND NOTHING ELSE. [stated] "on the walls the top needs to face outward and on
        // the ceiling the top needs to face downward."
        var state = container.getBlockState();
        var face = state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE);

        if (face != AttachFace.FLOOR) {
            var facing = state.getValue(HorizontalDirectionalBlock.FACING);

            // ⚠ ROTATE ABOUT THE BLOCK CENTRE. My transform composes AFTER the pipeline's, so it acts in world
            // space about the block ORIGIN unless wrapped - which would swing the model into its neighbour.
            poseStack.translate(0.5, 0.5, 0.5);

            if (face == AttachFace.CEILING) {
                // ⚠ 180 ABOUT THE **FACING** AXIS, not about X. A flip about a fixed axis would also mirror the
                // horizontal facing and undo the yaw the pipeline just applied; rolling about the facing axis
                // leaves that facing untouched and only turns the top downward.
                poseStack.mulPose(ceilingRoll(facing));
            } else {
                // ⚠ 90 ABOUT THE HORIZONTAL AXIS PERPENDICULAR TO FACING, which tips the top from up to
                // outward along FACING. The axis is FACING.getCounterClockWise(), derived per wall.
                poseStack.mulPose(wallTilt(facing));
            }

            poseStack.translate(-0.5, -0.5, -0.5);
        }

        super.render(container, partialTick, poseStack, source, packedLight, packedOverlay);
    }

    /**
     * ⚠ ROTATE ABOUT THE BLOCK CENTRE, NOT THE CORNER. The model's origin is the block corner, so a bare rotation
     * swings it into the neighbouring block - the translate out and back is what keeps it inside its own cube on the
     * wall and ceiling cases.
     */
    /** ⚠ Keyed on the BLOCK, so a container knows its own strain without a synced field. */
    private static ResourceLocation textureFor(ResinContainerBlockEntity container) {
        var name = blockName(container.getBlockState().getBlock());

        return ResourceLocation.fromNamespaceAndPath("avp_alien", "textures/block/" + name + ".png");
    }

    private static String blockName(Block block) {
        var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);

        return key.getPath();
    }

    /**
     * Tips the top from UP to OUTWARD along {@code facing}, for a wall mount.
     * <p>
     * ⚠ THE AXIS IS {@code facing.getCounterClockWise()} - the horizontal direction 90 degrees from the facing.
     * Rotating +90 about it carries +Y onto FACING. Written out per wall so the axis constants stay readable.
     * </p>
     */
    private static Quaternionf wallTilt(Direction facing) {
        return switch (facing) {
            case NORTH -> Axis.XN.rotationDegrees(90.0F);
            case SOUTH -> Axis.XP.rotationDegrees(90.0F);
            case EAST -> Axis.ZN.rotationDegrees(90.0F);
            case WEST -> Axis.ZP.rotationDegrees(90.0F);
            default -> Axis.XP.rotationDegrees(0.0F);
        };
    }

    /** ⚠ 180 about the FACING axis - keeps the yaw, turns the top down. Sign is irrelevant at 180 degrees. */
    private static Quaternionf ceilingRoll(Direction facing) {
        return facing.getAxis() == Direction.Axis.X
            ? Axis.XP.rotationDegrees(180.0F)
            : Axis.ZP.rotationDegrees(180.0F);
    }
}
