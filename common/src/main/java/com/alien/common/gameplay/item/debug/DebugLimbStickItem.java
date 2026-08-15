package com.alien.common.gameplay.item.debug;

import com.blib.api.common.dismemberment.v1.Dismemberable;
import com.blib.api.common.dismemberment.v1.LimbDefinition;
import com.blib.api.common.dismemberment.v1.LimbDismemberer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public class DebugLimbStickItem extends Item {

    private static final double HORIZONTAL_VELOCITY = 0.45D;

    private static final double VERTICAL_VELOCITY = 0.2D;

    public DebugLimbStickItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public @NotNull InteractionResult interactLivingEntity(
        @NotNull ItemStack itemStack,
        @NotNull Player player,
        @NotNull LivingEntity livingEntity,
        @NotNull InteractionHand interactionHand
    ) {
        if (livingEntity.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(livingEntity instanceof Dismemberable)) {
            player.displayClientMessage(Component.literal("Target has no dismemberment manager."), true);
            return InteractionResult.FAIL;
        }

        var remainingDefinitions = LimbDismemberer.getRemainingDefinitions(livingEntity);

        if (remainingDefinitions.isEmpty()) {
            player.displayClientMessage(Component.literal("No attached limbs remain."), true);
            return InteractionResult.FAIL;
        }

        var definition = remainingDefinitions.stream()
            .min(Comparator.comparingDouble(candidate -> distanceToLookRaySqr(player, livingEntity, candidate)))
            .orElse(remainingDefinitions.getFirst());
        var away = livingEntity.position()
            .subtract(player.position());
        var horizontal = new Vec3(away.x, 0.0D, away.z);

        if (horizontal.lengthSqr() < 1.0e-4D) {
            horizontal = livingEntity.getLookAngle()
                .multiply(1.0D, 0.0D, 1.0D);
        }

        if (horizontal.lengthSqr() < 1.0e-4D) {
            horizontal = new Vec3(1.0D, 0.0D, 0.0D);
        }

        var velocity = horizontal.normalize()
            .scale(HORIZONTAL_VELOCITY)
            .add(0.0D, VERTICAL_VELOCITY, 0.0D);

        var detached = LimbDismemberer.detach(livingEntity, definition.id(), limb -> limb.launch(velocity));

        if (detached.isEmpty()) {
            player.displayClientMessage(Component.literal("Could not detach " + definition.id() + "."), true);
            return InteractionResult.FAIL;
        }

        player.displayClientMessage(Component.literal("Detached " + definition.id() + "."), true);
        return InteractionResult.SUCCESS;
    }

    private static double distanceToLookRaySqr(Player player, LivingEntity livingEntity, LimbDefinition limbDefinition) {
        var eyePosition = player.getEyePosition();
        var look = player.getLookAngle()
            .normalize();
        var limbPosition = approximateLimbPosition(livingEntity, limbDefinition);
        var eyeToLimb = limbPosition.subtract(eyePosition);
        var projectedDistance = Math.max(0.0D, eyeToLimb.dot(look));
        var closestPoint = eyePosition.add(look.scale(projectedDistance));

        return limbPosition.distanceToSqr(closestPoint);
    }

    private static Vec3 approximateLimbPosition(LivingEntity livingEntity, LimbDefinition limbDefinition) {
        var offset = limbDefinition.spawnOffsetProvider()
            .apply(livingEntity);
        var path = limbDefinition.id()
            .getPath();
        var forward = livingEntity.getLookAngle()
            .multiply(1.0D, 0.0D, 1.0D);

        if (forward.lengthSqr() < 1.0e-4D) {
            forward = Vec3.directionFromRotation(0.0F, livingEntity.getYRot())
                .multiply(1.0D, 0.0D, 1.0D);
        }

        if (forward.lengthSqr() < 1.0e-4D) {
            forward = new Vec3(0.0D, 0.0D, 1.0D);
        }

        forward = forward.normalize();
        var right = new Vec3(-forward.z, 0.0D, forward.x);
        var sideOffset = livingEntity.getBbWidth() * 0.35D;

        if (path.contains("left_")) {
            offset = offset.subtract(right.scale(sideOffset));
        } else if (path.contains("right_")) {
            offset = offset.add(right.scale(sideOffset));
        }

        if (path.contains("tail")) {
            offset = offset.subtract(forward.scale(livingEntity.getBbWidth() * 0.8D));
            offset = new Vec3(offset.x, Math.max(offset.y, livingEntity.getBbHeight() * 0.35D), offset.z);
        }

        return livingEntity.position()
            .add(offset);
    }
}
