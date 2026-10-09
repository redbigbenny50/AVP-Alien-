package com.alien.mixin;

import com.alien.common.gameplay.entity.living.alien.xenomorph.IncapacitatableRoyal;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ⭐⭐⭐ A DOWNED ROYAL IS ALREADY DEAD AS FAR AS EVERY MOB IS CONCERNED.
 * <p>
 * [stated] "make is so while shes down they just dont attack her. have them consider her already dead that should be
 * much easier than making it so attacks dont register on her."
 * </p>
 * <p>
 * ⚠⚠ THIS REPLACES A FILTER THAT MADE HER INVINCIBLE. {@code Queen.hurt} used to decide WHO was allowed to work her
 * incapacitation bar, and every source that test failed to anticipate was silently shrugged off. Deciding who may hurt
 * her is a guess about intent; deciding who may AIM at her is a statement of it - and it fails safe, because a mob that
 * never acquires the target cannot chew her down while the player is walking over to finish her.
 * </p>
 * <p>
 * ⚠ {@code Mob.canAttack} IS THE RIGHT SEAM. Every vanilla targeting goal funnels through it - nearest-attackable,
 * hurt-by-target, the piglin and zombified variants - so one injection covers mobs from any mod that uses the standard
 * goals, which patching our own {@code setTarget} never could.
 * </p>
 * <p>
 * ⚠ PLAYERS ARE UNAFFECTED. A player attacks by clicking, not through {@code canAttack}, so the finisher still works
 * exactly as before. This only blinds mobs.
 * </p>
 */
@Mixin(LivingEntity.class)
public class MixinLivingEntity_IgnoreDownedRoyal {

    @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void avp_alien$ignoreDownedRoyal(LivingEntity target, CallbackInfoReturnable<Boolean> callback) {
        if (target instanceof IncapacitatableRoyal royal && royal.isIncapacitated()) {
            // ⭐ THE WARDEN IS THE ONE MOB THAT STILL FINISHES HER - [stated] Sep 23 "yes". This exemption is for
            // ambient wildlife and piglins; a warden is a boss-class threat, not a scavenger, and its sonic boom works
            // the finisher bar exactly as a player's blow would (Queen.hurt no longer filters attackers at all).
            if ((Object) this instanceof net.minecraft.world.entity.monster.warden.Warden) {
                return;
            }
            callback.setReturnValue(false);
        }
    }
}
