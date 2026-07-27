package com.alien.mixin.client;

import com.alien.common.registry.init.AlienMobEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Xeno vision: while the LOCAL player is under Metamorphosis, every living thing within {@value #XENO_VISION_RADIUS}
 * blocks is outlined through walls, exactly as a spectral arrow outlines its victim.
 * <p>
 * <b>Client only, and that is the whole point.</b> {@code Minecraft.shouldEntityAppearGlowing} is the single question
 * the renderer asks before drawing an outline, and it answers it by calling {@code Entity.isCurrentlyGlowing}. Saying
 * "yes" here means the outline exists in one player's eyes and nowhere else - no mob effect is applied, nothing is
 * synced, nothing is written to the world. Another player standing in the same room sees an ordinary dark cave, and a
 * player who wants the sense has to drink for it.
 * <p>
 * The earlier attempt applied vanilla {@code GLOWING} to every mob in range from the effect's tick. That worked, but
 * glowing is real server state: it lit those mobs up for EVERYONE, and it needed a constant re-application dance to
 * expire when something walked out of range. None of that exists now - range is simply re-tested every frame.
 * <p>
 * Invisibility is no defence, and that is inherited rather than built: when an entity is invisible AND glowing,
 * {@code LivingEntityRenderer} selects {@code RenderType.outline}, so it renders as an outline and nothing else. Hiding
 * from a creature that has no eyes was never going to work.
 */
@Mixin(Entity.class)
public abstract class MixinEntity_XenoVision {

    /** How far the borrowed senses reach. */
    private static final double XENO_VISION_RADIUS = 24.0;

    private static final double XENO_VISION_RADIUS_SQUARED = XENO_VISION_RADIUS * XENO_VISION_RADIUS;

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void avp_alien$outlineForXenoVision(CallbackInfoReturnable<Boolean> cir) {
        var self = (Entity) (Object) this;

        // Rendering asks this per entity per frame, so the cheap disqualifiers go first: wrong side, not a creature,
        // or the viewer themselves.
        if (!self.level().isClientSide || !(self instanceof LivingEntity)) {
            return;
        }

        var viewer = Minecraft.getInstance().player;
        if (viewer == null || viewer == self) {
            return;
        }

        if (!viewer.hasEffect(AlienMobEffects.getMetamorphosisHolder())) {
            return;
        }

        if (self.distanceToSqr(viewer) > XENO_VISION_RADIUS_SQUARED) {
            return;
        }

        cir.setReturnValue(true);
    }
}
