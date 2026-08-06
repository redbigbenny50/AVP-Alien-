package com.alien.mixin;

import com.alien.common.registry.init.AlienMobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_Stunned {

    @Inject(
        method = "aiStep",
        at = @At("HEAD"),
        cancellable = true
    )
    private void stopMovementAndAiWhileStunned(CallbackInfo callbackInfo) {
        var self = (LivingEntity) (Object) this;

        if (!self.hasEffect(AlienMobEffects.getStunnedHolder())) {
            return;
        }

        var movement = self.getDeltaMovement();
        self.setDeltaMovement(0.0D, Math.min(0.0D, movement.y), 0.0D);

        if (self instanceof Mob mob) {
            mob.getNavigation().stop();
        }

        callbackInfo.cancel();
    }
}
