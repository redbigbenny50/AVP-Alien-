package com.alien.client.gui;

import com.alien.common.gameplay.level.saveddata.TrackedQueenRow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The tracking PDA's readout: a scrollable list of tagged queens with last-seen coordinates and dimension. Clicking a
 * row echoes her position to chat and closes the screen. Populated from {@link TrackedQueenRow}s pushed by the server
 * in reply to the PDA's request; opened by {@code AlienClientPacketListener#handleTrackedQueens}.
 */
public class TrackingPdaScreen extends Screen {

    private static final int ROW_HEIGHT = 14;

    private static final int LIST_TOP = 40;

    private static final int HALF_WIDTH = 140;

    private final List<TrackedQueenRow> rows;

    private int scrollOffset = 0;

    public TrackingPdaScreen(List<TrackedQueenRow> rows) {
        super(Component.literal("Tracking PDA"));
        this.rows = rows;
    }

    /** Client entry point used by the network handler. */
    public static void open(List<TrackedQueenRow> rows) {
        Minecraft.getInstance().setScreen(new TrackingPdaScreen(rows));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);

        if (rows.isEmpty()) {
            guiGraphics.drawCenteredString(
                this.font,
                Component.literal("No queens tracked."),
                this.width / 2,
                LIST_TOP + 10,
                0xAAAAAA
            );
            return;
        }

        var maxVisible = maxVisibleRows();

        for (var i = 0; i < maxVisible && (i + scrollOffset) < rows.size(); i++) {
            var row = rows.get(i + scrollOffset);
            var y = LIST_TOP + i * ROW_HEIGHT;
            var hovered = isInRow(mouseX, mouseY, y);
            var color = hovered ? 0xFFFF55 : 0xFFFFFF;

            var text = row.name()
                + "  \u2014  " + row.pos().getX() + ", " + row.pos().getY() + ", " + row.pos().getZ()
                + "  (" + shortDim(row.dimension().location().toString()) + ")";

            guiGraphics.drawString(this.font, text, this.width / 2 - HALF_WIDTH, y + 2, color);
        }

        if (rows.size() > maxVisible) {
            guiGraphics.drawCenteredString(
                this.font,
                Component.literal("scroll for more (" + rows.size() + " total)"),
                this.width / 2,
                LIST_TOP + maxVisible * ROW_HEIGHT + 4,
                0x777777
            );
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !rows.isEmpty()) {
            var maxVisible = maxVisibleRows();
            for (var i = 0; i < maxVisible && (i + scrollOffset) < rows.size(); i++) {
                var y = LIST_TOP + i * ROW_HEIGHT;
                if (isInRow(mouseX, mouseY, y)) {
                    onRowClicked(rows.get(i + scrollOffset));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && !rows.isEmpty()) {
            var maxOffset = Math.max(0, rows.size() - maxVisibleRows());
            scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void onRowClicked(TrackedQueenRow row) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(
                Component.literal(
                    row.name() + " at " + row.pos().getX() + ", " + row.pos().getY() + ", " + row.pos().getZ()
                        + " (" + shortDim(row.dimension().location().toString()) + ")"
                ),
                false
            );
        }
        this.onClose();
    }

    private int maxVisibleRows() {
        var listBottom = this.height - 24;
        return Math.max(1, (listBottom - LIST_TOP) / ROW_HEIGHT);
    }

    private boolean isInRow(double mouseX, double mouseY, int rowY) {
        return mouseX >= this.width / 2.0 - HALF_WIDTH
            && mouseX <= this.width / 2.0 + HALF_WIDTH
            && mouseY >= rowY
            && mouseY < rowY + ROW_HEIGHT;
    }

    private static String shortDim(String dimension) {
        var idx = dimension.indexOf(':');
        return idx >= 0 ? dimension.substring(idx + 1) : dimension;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
