package com.alien.mixin;

import com.alien.common.gameplay.entity.living.alien.parasite.HuggerImmunity;
import com.alien.common.gameplay.entity.living.alien.parasite.HuggerStruggle;
import com.alien.common.gameplay.hive.party.HostStruggle;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The player half of the host-capture arc.
 * <p>
 * <b>Dismount lock.</b> Vanilla lets any passenger simply sneak off its vehicle: {@code Player#rideTick} calls
 * {@code wantsToStopRiding()} (which is just "is the shift key down") and dismounts. Without this, a grabbed player
 * taps SHIFT and walks away free - no struggle, no grab-immunity, no carrier stun - and the whole struggle bar is
 * decoration. A player being carried by a xenomorph cannot voluntarily dismount; the bar is the only way out.
 * <p>
 * <b>Struggle tick.</b> {@link HostStruggle} is driven from the player's own tick rather than the carrier's, so the bar
 * and the carrier's speed modifier are still torn down correctly if the carrier dies, unloads or is otherwise never
 * ticked again.
 */
@Mixin(Player.class)
public abstract class MixinPlayer_HostCapture {

    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void avp_alien$noDismountWhileCarried(CallbackInfoReturnable<Boolean> cir) {
        if (HostStruggle.isBeingCarried((Player) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void avp_alien$tickHostStruggle(CallbackInfo ci) {
        var self = (Player) (Object) this;
        HostStruggle.tickPlayer(self);
        HuggerStruggle.tickPlayer(self);
    }

    /**
     * Death wipes both struggles and the facehugger escalation. A respawn produces a fresh {@code ServerPlayer} object
     * and the state maps are weakly keyed, so this is belt-and-braces rather than load-bearing - but it also closes any
     * boss bar the moment the host dies, instead of on the next tick.
     */
    @Inject(method = "die", at = @At("HEAD"))
    private void avp_alien$clearStruggleOnDeath(DamageSource damageSource, CallbackInfo ci) {
        var self = (Player) (Object) this;

        if (self instanceof ServerPlayer serverPlayer) {
            HostStruggle.end(serverPlayer);
            HuggerStruggle.end(serverPlayer);
        }

        HuggerImmunity.reset(self);
    }
}
