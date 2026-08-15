package com.alien.compatibility.blib_engine.client.inspector;

import com.alien.Alien;
import com.alien.common.gameplay.hive.config.HiveConfigSchema;
import com.alien.common.gameplay.hive.config.HiveConfigSchema.Field;
import com.alien.common.gameplay.hive.inspection.HiveInspectionSnapshot;
import com.alien.common.network.payload.C2SRequestHiveInspectionPayload;
import com.alien.common.network.payload.C2SUpdateHiveConfigPayload;
import com.alien.common.registry.init.AlienFactionDataTypes;
import com.blib.engine.api.client.v1.inspector.CollapsibleInspectorSections;
import com.blib.engine.api.client.v1.inspector.InspectorSection;
import com.blib.engine.api.client.v1.inspector.InspectorStyle;
import com.blib.engine.api.client.v1.selection.FactionInspectionView;
import com.blib.engine.api.client.v1.selection.FactionSelection;
import com.blib.engine.api.client.v1.ui.EngineFont;
import com.blib.engine.api.client.v1.ui.layout.UiText;
import com.blib.engine.api.client.v1.ui.widget.Checkbox;
import com.blib.engine.api.client.v1.ui.widget.TextInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class HiveConfigInspectorSection implements InspectorSection<FactionSelection> {

    private static final int REQUEST_INTERVAL_TICKS = 10;

    private static final int MIN_LABEL_WIDTH = 112;

    private static final int MAX_LABEL_WIDTH = 168;

    private final CollapsibleInspectorSections collapsibleSections = new CollapsibleInspectorSections(getClass().getName());

    private final Map<String, TextInput> inputs = new HashMap<>();

    private final Map<String, Checkbox> checkboxes = new HashMap<>();

    private @Nullable ResourceLocation lastRequestedFactionId;

    private @Nullable ResourceLocation lastRenderedFactionId;

    private long lastRequestGameTick = Long.MIN_VALUE;

    @Override
    public String id() {
        return "avp_alien:hive_config_inspector";
    }

    @Override
    public Class<FactionSelection> selectableType() {
        return FactionSelection.class;
    }

    @Override
    public int order() {
        return 250;
    }

    @Override
    public int render(GuiGraphics graphics, int x, int y, int width, FactionSelection target, int mouseX, int mouseY) {
        collapsibleSections.beginFrame();
        lastRenderedFactionId = null;

        var generic = FactionInspectionView.current();
        if (generic == null || !generic.factionId().equals(target.factionId()) || !isHiveFactionType(generic.typeId())) {
            return y;
        }

        requestSnapshotIfNeeded(target.factionId());

        var cached = ClientHiveInspectionCache.current();
        if (
            cached == null
                || !cached.factionId().equals(target.factionId())
                || !cached.data().contains(HiveInspectionSnapshot.K_HIVE_CONFIG, Tag.TAG_COMPOUND)
        ) {
            var font = EngineFont.get();
            var rowY = HiveInspectorRender.drawSectionHeader(graphics, font, x, y, width, "Hive Config (loading...)");
            return HiveInspectorRender.drawNote(graphics, font, x, rowY, width, "Requesting config snapshot from server...");
        }

        lastRenderedFactionId = target.factionId();
        return renderConfig(
            graphics,
            EngineFont.get(),
            x,
            y,
            width,
            cached.data().getCompound(HiveInspectionSnapshot.K_HIVE_CONFIG),
            mouseX,
            mouseY
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, FactionSelection target) {
        if (lastRenderedFactionId == null || !lastRenderedFactionId.equals(target.factionId())) {
            return false;
        }

        for (var input : inputs.values()) {
            if (input.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        for (var checkbox : checkboxes.values()) {
            if (checkbox.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return collapsibleSections.mouseClicked(mouseX, mouseY, button);
    }

    private int renderConfig(
        GuiGraphics graphics,
        Font font,
        int x,
        int y,
        int width,
        CompoundTag config,
        int mouseX,
        int mouseY
    ) {
        var rowY = y;
        for (var group : HiveConfigSchema.groups()) {
            var header = collapsibleSections.drawHeader(
                graphics,
                font,
                x,
                rowY,
                width,
                "config/" + group.name(),
                "Hive Config - " + group.name(),
                mouseX,
                mouseY
            );
            rowY = header.nextY();
            if (!header.expanded()) {
                rowY += InspectorStyle.ROW_GAP;
                continue;
            }

            rowY += InspectorStyle.CONTENT_PADDING / 2;
            for (var field : group.fields()) {
                var value = config.getString(field.name());
                rowY = drawFieldRow(graphics, font, x, rowY, width, field, value, mouseX, mouseY);
            }
            rowY += InspectorStyle.ROW_GAP;
        }
        return rowY;
    }

    private int drawFieldRow(
        GuiGraphics graphics,
        Font font,
        int x,
        int y,
        int width,
        Field field,
        String value,
        int mouseX,
        int mouseY
    ) {
        var rowHeight = Math.max(TextInput.HEIGHT, Checkbox.SIZE);
        var labelColumnWidth = labelColumnWidth(width);
        var labelX = x + InspectorStyle.CONTENT_PADDING;
        var labelY = y + (rowHeight - font.lineHeight + 2) / 2;
        UiText.drawClipped(graphics, font, field.label(), labelX, labelY, labelColumnWidth - 4, InspectorStyle.LABEL_COLOR());

        var controlX = x + InspectorStyle.CONTENT_PADDING + labelColumnWidth;
        var controlWidth = Math.max(0, width - labelColumnWidth - 2 * InspectorStyle.CONTENT_PADDING);

        if (field.type() == HiveConfigSchema.ValueType.BOOLEAN) {
            var checkbox = checkboxFor(field);
            checkbox.setChecked(Boolean.parseBoolean(value));
            checkbox.render(graphics, controlX, y + Math.max(0, (rowHeight - Checkbox.SIZE) / 2), mouseX, mouseY);

            var state = Boolean.parseBoolean(value) ? "On" : "Off";
            UiText.drawClipped(
                graphics,
                font,
                state,
                controlX + Checkbox.SIZE + 5,
                labelY,
                Math.max(0, controlWidth - Checkbox.SIZE - 5),
                InspectorStyle.VALUE_COLOR()
            );
            return y + rowHeight + InspectorStyle.ROW_GAP;
        }

        var input = inputFor(field);
        if (!input.isFocused() && !input.content().equals(value)) {
            input.setContent(value);
        }
        input.render(graphics, controlX, y, controlWidth, mouseX, mouseY);
        return y + rowHeight + InspectorStyle.ROW_GAP;
    }

    private TextInput inputFor(Field field) {
        return inputs.computeIfAbsent(
            field.name(),
            name -> new TextInput(field.type().displayName(), value -> commit(field.name(), value))
        );
    }

    private Checkbox checkboxFor(Field field) {
        return checkboxes.computeIfAbsent(
            field.name(),
            name -> new Checkbox(false, value -> commit(field.name(), Boolean.toString(value)))
        );
    }

    private void commit(String fieldName, String value) {
        Alien.MOD.networking().sendToServer(new C2SUpdateHiveConfigPayload(fieldName, value));
    }

    private void requestSnapshotIfNeeded(ResourceLocation factionId) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        var currentTick = level.getGameTime();
        var selectionChanged = !factionId.equals(lastRequestedFactionId);
        if (selectionChanged || currentTick - lastRequestGameTick >= REQUEST_INTERVAL_TICKS) {
            Alien.MOD.networking().sendToServer(new C2SRequestHiveInspectionPayload(factionId));
            lastRequestedFactionId = factionId;
            lastRequestGameTick = currentTick;
        }
    }

    private static int labelColumnWidth(int width) {
        return Math.min(MAX_LABEL_WIDTH, Math.max(MIN_LABEL_WIDTH, width / 2));
    }

    private static boolean isHiveFactionType(ResourceLocation typeId) {
        return AlienFactionDataTypes.LOCATION.getResourceLocation().equals(typeId)
            || AlienFactionDataTypes.LINEAGE.getResourceLocation().equals(typeId)
            || AlienFactionDataTypes.VARIANT.getResourceLocation().equals(typeId);
    }
}
