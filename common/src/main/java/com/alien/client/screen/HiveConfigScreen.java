package com.alien.client.screen;

import com.alien.common.gameplay.hive.config.HiveConfigDescriptions;
import com.alien.common.gameplay.hive.config.HiveConfigSchema;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * ⭐⭐⭐ A SCREEN FOR THE HIVE CONFIG, DRAWN FROM THE SCHEMA.
 * <p>
 * [stated] "is it possible for us to do option 3 for a visual way to change and update the config but have something
 * for that neoforge like that tells them what command to enter? that way they change the config the same way but no
 * mystery how to do it."
 * </p>
 * <p>
 * ⭐⭐ EDITS RUN THE COMMAND. Nothing here writes a config value directly — every change is sent as
 * {@code /avp hive config set <field> <value>}, which is the same path an admin typing in chat uses. That is the whole
 * point of building our own screen rather than mirroring into NeoForge's {@code ModConfigSpec}: ONE source of truth and
 * ONE write path, so a value can never be changed by a route that skips the clamps, the save, or the permission check.
 * A non-op simply has the command refused, and the screen does not have to know that.
 * </p>
 * <p>
 * ⚠ AND THE COMMAND IS ALWAYS ON SCREEN, under the field it belongs to — [stated] "no mystery how to do it". Anyone who
 * prefers typing, or is reading over a shoulder, or is on a server where the screen is awkward, can copy it.
 * </p>
 * <p>
 * ⚠ THE VALUES COME FROM THE SERVER. The config lives there and only there; a screen drawn from client-side state would
 * show every player the defaults on every server. See {@code S2CHiveConfigSnapshotPayload}.
 * </p>
 */
public class HiveConfigScreen extends Screen {

    private static final int ROW_HEIGHT = 34;

    private static final int FIELD_WIDTH = 90;

    private final CompoundTag snapshot;

    private final List<Row> rows = new ArrayList<>();

    private int scrollOffset;

    private record Row(
        HiveConfigSchema.Field field,
        EditBox box
    ) {}

    /**
     * ⭐⭐⭐ OPENS THE SCREEN. Call THIS from a common-side packet handler, never {@code new HiveConfigScreen(...)}.
     * <p>
     * ⚠⚠ THIS IS WHAT BROKE runDatagen FOR TWO DAYS. The handler used to do
     * {@code Minecraft.getInstance().setScreen(new HiveConfigScreen(data))} directly. Verifying that line requires the
     * JVM to prove HiveConfigScreen is assignable to Screen, which forces BOTH classes to load the moment
     * AlienClientPacketListener is first touched - and datagen runs in a SERVER environment where Fabric refuses to
     * load {@code net.minecraft.client.gui.screens.Screen} at all. Mod init died, so NO provider ever ran and nothing
     * regenerated.
     * </p>
     * <p>
     * ⭐ A static call is resolved LAZILY - only when it actually executes, which on a server is never. This is exactly
     * the shape {@code TrackingPdaScreen.open} has always used, which is why that one never broke anything.
     * </p>
     */
    public static void open(CompoundTag snapshot) {
        Minecraft.getInstance().setScreen(new HiveConfigScreen(snapshot));
    }

    public HiveConfigScreen(CompoundTag snapshot) {
        super(Component.translatable("screen.avp_alien.hive_config"));
        this.snapshot = snapshot;
    }

    @Override
    protected void init() {
        rows.clear();

        var y = 40;
        for (var group : HiveConfigSchema.groups()) {
            for (var field : group.fields()) {
                var box = new EditBox(
                    this.font,
                    this.width - FIELD_WIDTH - 90,
                    y,
                    FIELD_WIDTH,
                    16,
                    Component.literal(field.name())
                );
                box.setValue(valueOf(field));
                box.setMaxLength(32);
                addRenderableWidget(box);

                // ⚠ APPLY PER FIELD, not one "save everything" button. A single save would resend all ~150 values as
                // ~150 commands, and any one of them being refused would leave the player unable to tell which.
                addRenderableWidget(
                    Button.builder(Component.literal("Set"), b -> apply(field, box))
                        .bounds(this.width - 80, y, 40, 16)
                        .build()
                );

                rows.add(new Row(field, box));
                y += ROW_HEIGHT;
            }
        }

        addRenderableWidget(
            Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 26, 100, 20)
                .build()
        );
    }

    /**
     * ⚠ SENDS A CHAT COMMAND rather than a custom packet. The command already exists, already validates, already clamps
     * and already saves — a bespoke packet would be a second door into the same room, and the one that skips the checks
     * is always the one that gets used by accident.
     */
    private void apply(HiveConfigSchema.Field field, EditBox box) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        this.minecraft.player.connection
            .sendCommand("avp hive config set " + field.name() + " " + box.getValue());
    }

    private String valueOf(HiveConfigSchema.Field field) {
        return snapshot.contains(field.name()) ? snapshot.getString(field.name()) : field.defaultValue();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        for (var row : rows) {
            var y = row.box().getY();
            if (y < 30 || y > this.height - 40) {
                continue;
            }
            graphics.drawString(this.font, row.field().name(), 20, y + 4, 0xFFFFFF, false);

            // ⭐ The command, always visible, under the field it belongs to.
            graphics.drawString(
                this.font,
                Component.literal("/avp hive config set " + row.field().name() + " <value>")
                    .withStyle(ChatFormatting.DARK_GRAY),
                20,
                y + 15,
                0xFFFFFF,
                false
            );

            var description = HiveConfigDescriptions.of(row.field().name());
            if (description != null && mouseY >= y && mouseY <= y + 16) {
                graphics.renderTooltip(this.font, Component.literal(description), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
