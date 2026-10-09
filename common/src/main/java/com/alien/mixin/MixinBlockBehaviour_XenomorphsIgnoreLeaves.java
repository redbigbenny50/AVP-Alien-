package com.alien.mixin;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Xenomorphs push through leaves instead of ducking under them, but still stand on top of them.
 * <p>
 * [stated] "xenomorphs should not have to crawl or duck under leaves especially the big ones. can we have them walk
 * through leaves like they arent there but still allow them to stand on leaves." He weighed the alternative himself and
 * rejected it: [stated] "the alternative makes alot of drops" — a hive patrol breaking its way through a forest would
 * carpet the ground in saplings and sticks and fire a block-break event per leaf.
 * </p>
 * <p>
 * ⭐ WALK THROUGH BUT STAND ON IS A REAL VANILLA PATTERN, NOT A TRICK. {@code ScaffoldingBlock} and
 * {@code PowderSnowBlock} both do exactly this: return an empty collision shape unless the entity is ABOVE the block.
 * {@link EntityCollisionContext#isAbove} is the same test they use, so a xenomorph walking into a canopy passes through
 * it and one landing on top of it is held up.
 * </p>
 * <p>
 * ⚠⚠ THIS IS ONE THIRD OF THE CHANGE AND IS USELESS ALONE. {@code CrawlingManager.isTightSpace} would still read the
 * canopy as a low ceiling and crouch them under blocks they no longer collide with, and BLib's terrain classifier would
 * still route them around leaves they can now walk through. All three move together.
 * </p>
 * <p>
 * ⚠ ORDERING IS DELIBERATE IN A HOT PATH. getCollisionShape is called for every entity against every nearby block every
 * tick. The tag test is cheapest and rejects almost everything, so it goes first; the context cast and the entity check
 * only run for actual leaves.
 * </p>
 */
@Mixin(BlockBehaviour.class)
public class MixinBlockBehaviour_XenomorphsIgnoreLeaves {

    @Inject(
        method = "getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
        at = @At("HEAD"), cancellable = true
    )
    private void avp_alien$xenomorphsPushThroughLeaves(
        BlockState blockState,
        BlockGetter blockGetter,
        BlockPos blockPos,
        CollisionContext collisionContext,
        CallbackInfoReturnable<VoxelShape> callbackInfoReturnable
    ) {
        if (!blockState.is(BlockTags.LEAVES)) {
            return;
        }

        if (!(collisionContext instanceof EntityCollisionContext entityCollisionContext)) {
            return;
        }

        if (!(entityCollisionContext.getEntity() instanceof Xenomorph)) {
            return;
        }

        // Above the block: keep it solid, so she can stand on the canopy. Anywhere else: let her walk through it.
        if (entityCollisionContext.isAbove(Shapes.block(), blockPos, false)) {
            return;
        }

        callbackInfoReturnable.setReturnValue(Shapes.empty());
    }
}
