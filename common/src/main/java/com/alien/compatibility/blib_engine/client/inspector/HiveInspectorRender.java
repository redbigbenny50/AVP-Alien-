package com.alien.compatibility.blib_engine.client.inspector;

import com.blib.engine.api.client.v1.inspector.InspectorStyle;
import com.blib.engine.api.client.v1.ui.layout.UiRect;
import com.blib.engine.api.client.v1.ui.layout.UiText;
import com.blib.engine.api.client.v1.ui.layout.VerticalLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Small bundle of drawing helpers shared by the three AVP inspector sections. Mirrors the look of BLib's own inspector
 * rows but lives in AVP-Alien because BLib's helpers are package-private to {@code DetailsPanel}'s own section files.
 * Uses {@link InspectorStyle} for colors / padding so the visual style stays consistent with the rest of the inspector.
 */
public final class HiveInspectorRender {

    public record Metric(
        String label,
        String value
    ) {}

    public record CountColumns(
        int entityWidth,
        int countWidth
    ) {}

    public static Metric metric(String label, String value) {
        return new Metric(label, value);
    }

    private static final int ROW_LABEL_GAP = 5;

    private static final int SUBROW_INDENT = 8;

    private static final int METRIC_COLUMN_GAP = 6;

    private static final int ITEM_HEADER_BG_COLOR = 0xFF1F1F26;

    private static final int BAR_BG_COLOR = 0xFF14141A;

    private static final int BAR_FILL_COLOR = 0xFF6B5529;

    private HiveInspectorRender() {}

    /** Section header bar — dark band with bold label, mirrors {@code DetailsPanel.drawSectionHeader}. */
    public static int drawSectionHeader(GuiGraphics graphics, Font font, int x, int y, int width, String label) {
        var rect = UiRect.of(x, y, width, InspectorStyle.SECTION_HEADER_HEIGHT);
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), InspectorStyle.SECTION_HEADER_BG_COLOR());
        UiText.drawClipped(
            graphics,
            font,
            label,
            rect.x() + InspectorStyle.CONTENT_PADDING,
            UiText.centeredY(font, rect),
            Math.max(0, rect.width() - 2 * InspectorStyle.CONTENT_PADDING),
            InspectorStyle.HEADER_TEXT_COLOR()
        );
        return rect.bottom();
    }

    /** Label + value row on one line. Returns next-row y. */
    public static int drawRow(GuiGraphics graphics, Font font, int x, int y, int width, String label, String value) {
        return drawLabelValueRow(graphics, font, x, y, width, 0, label, value, InspectorStyle.LABEL_COLOR(), InspectorStyle.VALUE_COLOR());
    }

    /** Label + value row for values that should prefer ellipsis over horizontal overflow. */
    public static int drawClippedRow(GuiGraphics graphics, Font font, int x, int y, int width, String label, String value) {
        return drawRow(graphics, font, x, y, width, label, value);
    }

    /** Compatibility overload for callers that are still being migrated to explicit width-aware rows. */
    public static int drawRow(GuiGraphics graphics, Font font, int x, int y, String label, String value) {
        return drawLabelValueRow(
            graphics,
            font,
            x,
            y,
            InspectorStyle.LABEL_COLUMN_WIDTH * 3,
            0,
            label,
            value,
            InspectorStyle.LABEL_COLOR(),
            InspectorStyle.VALUE_COLOR()
        );
    }

    /** Indented label + value row for repeated child entries. */
    public static int drawSubRow(GuiGraphics graphics, Font font, int x, int y, int width, String label, String value) {
        return drawLabelValueRow(
            graphics,
            font,
            x,
            y,
            width,
            SUBROW_INDENT,
            label,
            value,
            InspectorStyle.LABEL_COLOR(),
            InspectorStyle.VALUE_COLOR()
        );
    }

    public static CountColumns countColumns(int width, int widestEntityWidth, int widestCountWidth) {
        var rowWidth = contentRow(0, 0, width, SUBROW_INDENT).width();
        var countWidth = Math.min(rowWidth, Math.max(24, widestCountWidth + 4));
        var entityWidth = Math.min(Math.max(0, widestEntityWidth), Math.max(0, rowWidth - ROW_LABEL_GAP - countWidth));
        return new CountColumns(entityWidth, countWidth);
    }

    /** Indented entity/count row with a group-aligned count column. */
    public static int drawCountRow(
        GuiGraphics graphics,
        Font font,
        int x,
        int y,
        int width,
        String entityId,
        String count,
        CountColumns columns
    ) {
        var row = contentRow(x, y, width, SUBROW_INDENT);
        var textY = UiText.centeredY(font, row);
        var countX = row.x() + Math.min(columns.entityWidth() + ROW_LABEL_GAP, Math.max(0, row.width() - columns.countWidth()));
        var countRect = UiRect.of(countX, row.y(), columns.countWidth(), row.height());

        var idWidth = Math.max(0, countX - ROW_LABEL_GAP - row.x());
        UiText.drawClipped(graphics, font, entityId, row.x(), textY, idWidth, InspectorStyle.LABEL_COLOR());
        UiText.drawRight(graphics, font, count, countRect, InspectorStyle.VALUE_COLOR());
        return row.bottom();
    }

    /** Indented entity/count row. Single-row fallback; count sits just after that row's entity id. */
    public static int drawCountRow(GuiGraphics graphics, Font font, int x, int y, int width, String entityId, String count) {
        return drawCountRow(graphics, font, x, y, width, entityId, count, countColumns(width, font.width(entityId), font.width(count)));
    }

    /** Indented entity/count rows with one count column shared across the whole group. */
    public static int drawCountRows(
        GuiGraphics graphics,
        Font font,
        int x,
        int y,
        int width,
        net.minecraft.nbt.ListTag rows,
        String keyName,
        String valueName
    ) {
        var widestEntityWidth = 0;
        var widestCountWidth = 0;
        for (var i = 0; i < rows.size(); i++) {
            var row = rows.getCompound(i);
            widestEntityWidth = Math.max(widestEntityWidth, font.width(row.getString(keyName)));
            widestCountWidth = Math.max(widestCountWidth, font.width(String.valueOf(row.getInt(valueName))));
        }
        var columns = countColumns(width, widestEntityWidth, widestCountWidth);

        var rowY = y;
        for (var i = 0; i < rows.size(); i++) {
            var row = rows.getCompound(i);
            rowY = drawCountRow(
                graphics,
                font,
                x,
                rowY,
                width,
                row.getString(keyName),
                String.valueOf(row.getInt(valueName)),
                columns
            );
        }
        return rowY;
    }

    /** Solo line of muted text — used for "(loading…)" and empty-state hints. */
    public static int drawNote(GuiGraphics graphics, Font font, int x, int y, String text) {
        UiText.drawClipped(
            graphics,
            font,
            text,
            x + InspectorStyle.CONTENT_PADDING,
            y,
            Math.max(0, InspectorStyle.LABEL_COLUMN_WIDTH * 3),
            InspectorStyle.LABEL_COLOR()
        );
        return y + InspectorStyle.LINE_HEIGHT;
    }

    /** Solo muted text with an explicit available width. */
    public static int drawNote(GuiGraphics graphics, Font font, int x, int y, int width, String text) {
        var row = contentRow(x, y, width, 0);
        UiText.drawClipped(graphics, font, text, row.x(), UiText.centeredY(font, row), row.width(), InspectorStyle.LABEL_COLOR());
        return row.bottom();
    }

    /**
     * Label + horizontal value bar showing {@code current/cap}. Bar tracks {@code (current / cap)}; accent color when
     * cap is non-zero. Returns next-row y. Used for biomass, royal jelly, scourge jelly, and population caps.
     */
    public static int drawBarRow(GuiGraphics graphics, Font font, int x, int y, int width, String label, long current, long cap) {
        var row = contentRow(x, y, width, 0);
        var labelWidth = labelColumnWidth(row.width());
        var textY = UiText.centeredY(font, row);
        UiText.drawClipped(graphics, font, label, row.x(), textY, labelWidth, InspectorStyle.LABEL_COLOR());

        var barX = row.x() + Math.min(labelWidth, row.width()) + ROW_LABEL_GAP;
        var barW = Math.max(0, row.right() - barX);
        var valueText = current + " / " + cap;
        if (barW < 42) {
            UiText.drawClipped(graphics, font, valueText, barX, textY, barW, InspectorStyle.VALUE_COLOR());
            return row.bottom();
        }

        var barH = Math.max(3, font.lineHeight - 1);
        var barY = row.y() + Math.max(0, (row.height() - barH) / 2);

        graphics.fill(barX, barY, barX + barW, barY + barH, BAR_BG_COLOR);
        if (cap > 0 && current > 0) {
            var filled = Math.max(0, Math.min(barW, (int) Math.round(((double) current / (double) cap) * (double) barW)));
            graphics.fill(barX, barY, barX + filled, barY + barH, BAR_FILL_COLOR);
        }

        var textW = font.width(valueText);
        if (textW <= barW) {
            UiText.drawClipped(graphics, font, valueText, barX + (barW - textW) / 2, textY, textW, InspectorStyle.VALUE_COLOR());
        } else {
            UiText.drawClipped(graphics, font, valueText, barX + 2, textY, Math.max(0, barW - 4), InspectorStyle.VALUE_COLOR());
        }

        return row.bottom();
    }

    /** Compact row group for short numeric facts. Wraps to multiple rows when the inspector is narrow. */
    public static int drawMetricStrip(GuiGraphics graphics, Font font, int x, int y, int width, Metric... metrics) {
        if (metrics == null || metrics.length == 0) {
            return y;
        }
        var contentWidth = Math.max(0, width - 2 * InspectorStyle.CONTENT_PADDING);
        var columns = contentWidth >= 240 ? 3 : contentWidth >= 150 ? 2 : 1;
        var layout = new VerticalLayout(x + InspectorStyle.CONTENT_PADDING, y, contentWidth);
        for (var i = 0; i < metrics.length;) {
            var count = Math.min(columns, metrics.length - i);
            var row = layout.take(InspectorStyle.LINE_HEIGHT);
            var rects = VerticalLayout.columns(row, count, METRIC_COLUMN_GAP);
            for (var c = 0; c < count; c++) {
                drawMetric(graphics, font, rects[c], metrics[i + c]);
            }
            i += count;
        }
        return layout.y();
    }

    /** Tinted one-line header for repeated list entries, followed by metric rows from the caller. */
    public static int drawItemHeader(GuiGraphics graphics, Font font, int x, int y, int width, String label, String value) {
        var row = contentRow(x, y, width, 0);
        graphics.fill(row.x(), row.y(), row.right(), row.bottom(), ITEM_HEADER_BG_COLOR);
        var inset = row.inset(0, 2, 0, 2);
        UiText.drawLabelValue(
            graphics,
            font,
            inset,
            label,
            value,
            labelColumnWidth(inset.width()),
            ROW_LABEL_GAP,
            InspectorStyle.HEADER_TEXT_COLOR(),
            InspectorStyle.VALUE_COLOR()
        );
        return row.bottom();
    }

    private static int drawLabelValueRow(
        GuiGraphics graphics,
        Font font,
        int x,
        int y,
        int width,
        int indent,
        String label,
        String value,
        int labelColor,
        int valueColor
    ) {
        var row = contentRow(x, y, width, indent);
        UiText.drawLabelValue(
            graphics,
            font,
            row,
            label,
            value,
            labelColumnWidth(row.width()),
            ROW_LABEL_GAP,
            labelColor,
            valueColor
        );
        return row.bottom();
    }

    private static UiRect contentRow(int x, int y, int width, int indent) {
        return UiRect.of(
            x + InspectorStyle.CONTENT_PADDING + Math.max(0, indent),
            y,
            width - 2 * InspectorStyle.CONTENT_PADDING - Math.max(0, indent),
            InspectorStyle.LINE_HEIGHT
        );
    }

    private static int labelColumnWidth(int contentWidth) {
        if (contentWidth <= 0) {
            return 0;
        }
        return Math.min(InspectorStyle.LABEL_COLUMN_WIDTH, Math.max(36, contentWidth * 2 / 5));
    }

    private static void drawMetric(GuiGraphics graphics, Font font, UiRect rect, Metric metric) {
        var label = metric.label() + ":";
        var labelWidth = Math.min(Math.max(28, font.width(label) + 3), Math.max(0, rect.width() / 2));
        UiText.drawLabelValue(
            graphics,
            font,
            rect,
            label,
            metric.value(),
            labelWidth,
            2,
            InspectorStyle.LABEL_COLOR(),
            InspectorStyle.VALUE_COLOR()
        );
    }

    /**
     * Format a tick count as a compact "{@code 1d 4h 32m 12s}" string, dropping the empty leading components. Ticks are
     * 20Hz; sub-second precision is dropped.
     */
    public static String formatTicks(long ticks) {
        var seconds = Math.max(0, ticks / 20);
        if (seconds < 60) {
            return seconds + "s";
        }
        var minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes < 60) {
            return minutes + "m " + seconds + "s";
        }
        var hours = minutes / 60;
        minutes = minutes % 60;
        if (hours < 24) {
            return hours + "h " + minutes + "m";
        }
        var days = hours / 24;
        hours = hours % 24;
        return days + "d " + hours + "h " + minutes + "m";
    }

    /** Shortened UUID (first 8 chars) for compact display in row labels. */
    public static String shortUuid(java.util.UUID uuid) {
        return uuid.toString().substring(0, 8);
    }
}
