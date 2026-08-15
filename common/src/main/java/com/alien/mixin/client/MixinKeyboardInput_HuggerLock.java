package com.alien.mixin.client;

import com.alien.common.gameplay.entity.living.alien.parasite.HuggerStruggle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A host with a facehugger on its face cannot walk and cannot jump - it can only fight.
 * <p>
 * The server side of that lock is a slowness effect at an absurd amplifier (see {@code ParasiteAttachmentManager}), but
 * slowness does not stop a jump: the player would hop in place while mashing SPACE, which is both silly and confusing.
 * Consuming the keybind click does not help either - {@code KeyboardInput#tick} reads {@code keyJump.isDown()}, not
 * {@code consumeClick()}. So the movement input itself is zeroed here, right after vanilla has filled it in.
 * <p>
 * Look is deliberately left alone: you can still turn your head while a hugger smothers you.
 */
@Mixin(KeyboardInput.class)
public abstract class MixinKeyboardInput_HuggerLock {

    @Inject(method = "tick", at = @At("TAIL"))
    private void avp_alien$lockMovementWhileHugged(
        boolean isSneaking,
        float sneakingSpeedMultiplier,
        CallbackInfo ci
    ) {
        var player = Minecraft.getInstance().player;

        if (player == null || !HuggerStruggle.isBeingHugged(player)) {
            return;
        }

        var self = (Input) (Object) this;
        self.up = false;
        self.down = false;
        self.left = false;
        self.right = false;
        self.forwardImpulse = 0.0F;
        self.leftImpulse = 0.0F;
        self.jumping = false;
    }
}
