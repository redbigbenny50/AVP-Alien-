package com.alien.client.input;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.parasite.HuggerStruggle;
import com.alien.common.network.payload.C2SHostStruggleMashPayload;
import net.minecraft.client.Minecraft;

/**
 * Client half of the struggle bar: while the player is riding a xenomorph (which, for a player, only ever happens
 * because they have been grabbed), every fresh press of SPACE or LEFT-CLICK is reported to the server as one mash.
 * <p>
 * No grab state has to be synced down - "my vehicle is an alien" IS the grab condition. Both keys are also
 * <b>consumed</b> here so vanilla does not additionally process them: a left-click would otherwise swing at the drone
 * the player is sitting on (and {@code MixinProjectileUtil_AllowHittingVehicle} explicitly allows hitting an alien
 * vehicle), which would be a free escape that skips the bar entirely.
 * <p>
 * Rate-limiting lives on the server ({@code HostStruggle.MASH_COOLDOWN_TICKS}); this handler only reports edges.
 */
public final class HostStruggleInputHandler {

    private static boolean jumpWasDown;

    private static boolean attackWasDown;

    private HostStruggleInputHandler() {}

    public static void handle(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            jumpWasDown = false;
            attackWasDown = false;
            return;
        }

        // Two struggles share this input: being carried off (we are riding an alien) and being face-hugged (a parasite
        // is riding us). The server decides which one a mash actually feeds.
        var carried = minecraft.player.getVehicle() instanceof com.alien.common.gameplay.entity.living.alien.Alien;
        var hugged = HuggerStruggle.isBeingHugged(minecraft.player);

        if (!carried && !hugged) {
            jumpWasDown = false;
            attackWasDown = false;
            return;
        }

        var jumpDown = minecraft.options.keyJump.isDown();
        var attackDown = minecraft.options.keyAttack.isDown();

        var mashed = (jumpDown && !jumpWasDown) || (attackDown && !attackWasDown);

        jumpWasDown = jumpDown;
        attackWasDown = attackDown;

        if (mashed) {
            Alien.MOD.networking().sendToServer(C2SHostStruggleMashPayload.INSTANCE);
        }

        while (minecraft.options.keyAttack.consumeClick()) {
            // Swallow the swing: the click is a struggle input, not an attack on the drone carrying us.
        }

        while (minecraft.options.keyJump.consumeClick()) {
            // Swallow the jump: it is a struggle input.
        }
    }
}
