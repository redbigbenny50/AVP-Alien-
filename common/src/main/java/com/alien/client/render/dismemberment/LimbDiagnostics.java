package com.alien.client.render.dismemberment;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Client-only F3+B companion controls. No packets or gameplay state are involved. */
public final class LimbDiagnostics {

    private static boolean textVisible = true;

    private static boolean toggleHeld;

    private LimbDiagnostics() {}

    public static boolean isTextVisible() {
        return textVisible;
    }

    public static void tickToggle(Minecraft minecraft) {
        var window = minecraft.getWindow().getWindow();
        var held = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_F3) && InputConstants.isKeyDown(window, GLFW.GLFW_KEY_8);
        if (held && !toggleHeld) {
            textVisible = !textVisible;
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                    Component.literal("[AVP] Limb diagnostics " + (textVisible ? "enabled" : "disabled") + ". Press F3+8 to toggle."),
                    false
                );
            }
        }
        toggleHeld = held;
    }
}
