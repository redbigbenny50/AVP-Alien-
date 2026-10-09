package com.alien.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ⭐⭐ WHAT THE MOD-LIST "CONFIG" BUTTON OPENS.
 * <p>
 * [stated] "have something for that neoforge like that tells them what command to enter? that way they change the
 * config the same way but no mystery how to do it."
 * </p>
 * <p>
 * ⚠⚠ IT CANNOT SHOW VALUES DIRECTLY, AND THAT IS NOT A LIMITATION WORTH HIDING. The hive config lives on the SERVER.
 * Opened from the main menu there is no server to ask, and opened in a world the values have to be fetched. So this
 * screen does the one honest thing: if you are in a world it asks for the config and hands you the real editor; if you
 * are not, it tells you the command instead of showing you defaults that are not what any server is running.
 * </p>
 * <p>
 * ⚠ Mirroring our fields into NeoForge's {@code ModConfigSpec} would have let the button show values - and would have
 * created a SECOND source of truth for ~150 settings whose failure mode is the mirror and the JSON disagreeing. That is
 * a worse problem than a button that redirects you.
 * </p>
 */
public class HiveConfigEntryScreen extends Screen {

    private static final String COMMAND = "/avp hive config gui";

    private final @Nullable Screen parent;

    public HiveConfigEntryScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.avp_alien.hive_config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // ⭐ IN A WORLD: skip this screen entirely. Asking the player to type a command they did not need to type
        // would be the mystery he asked us to remove, not the fix for it.
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.connection.sendCommand(COMMAND.substring(1));
            return;
        }

        addRenderableWidget(
            Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(this.width / 2 - 50, this.height / 2 + 30, 100, 20)
                .build()
        );
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);
        graphics.drawCenteredString(
            this.font,
            Component.literal("The hive config lives on the server.").withStyle(ChatFormatting.GRAY),
            this.width / 2,
            this.height / 2 - 16,
            0xFFFFFF
        );
        graphics.drawCenteredString(
            this.font,
            Component.literal("Join a world and run:").withStyle(ChatFormatting.GRAY),
            this.width / 2,
            this.height / 2 - 4,
            0xFFFFFF
        );
        graphics.drawCenteredString(
            this.font,
            Component.literal(COMMAND).withStyle(ChatFormatting.YELLOW),
            this.width / 2,
            this.height / 2 + 10,
            0xFFFFFF
        );
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
            return;
        }
        super.onClose();
    }
}
