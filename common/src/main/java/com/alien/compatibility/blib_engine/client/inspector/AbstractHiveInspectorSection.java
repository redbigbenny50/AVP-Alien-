package com.alien.compatibility.blib_engine.client.inspector;

import com.alien.Alien;
import com.alien.common.network.payload.C2SRequestHiveInspectionPayload;
import com.blib.engine.api.client.v1.inspector.CollapsibleInspectorSections;
import com.blib.engine.api.client.v1.inspector.InspectorSection;
import com.blib.engine.api.client.v1.selection.FactionInspectionView;
import com.blib.engine.api.client.v1.selection.FactionSelection;
import com.blib.engine.api.client.v1.ui.EngineFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Shared scaffolding for the three AVP hive inspector sections. Polls the server once per client game tick for a fresh
 * snapshot while the inspector is open, plus an immediate refresh whenever the selection swaps; the cached snapshot is
 * rendered by {@link #renderSnapshot} or a "(loading…)" placeholder is shown if no matching reply has landed yet.
 * <p>
 * Subclasses declare which {@link com.blib.api.common.faction.v1.FactionDataType} typeId they own and render the
 * appropriate fields from the snapshot.
 */
public abstract class AbstractHiveInspectorSection implements InspectorSection<FactionSelection> {

    private final CollapsibleInspectorSections collapsibleSections = new CollapsibleInspectorSections(getClass().getName());

    private @Nullable ResourceLocation lastRequestedFactionId;

    private long lastRequestGameTick = Long.MIN_VALUE;

    /** Typed faction-type id this section renders for (e.g. {@code avp_alien:location}). */
    protected abstract ResourceLocation supportedFactionTypeId();

    /** Snapshot-kind discriminator the server sends back for this section's faction kind. */
    protected abstract String snapshotKind();

    /** Render the section's body from the cached snapshot. {@code y} is the top of the section's row band. */
    protected abstract int renderSnapshot(
        GuiGraphics graphics,
        net.minecraft.client.gui.Font font,
        int x,
        int y,
        int width,
        CompoundTag snapshot,
        int mouseX,
        int mouseY
    );

    @Override
    public Class<FactionSelection> selectableType() {
        return FactionSelection.class;
    }

    @Override
    public int render(GuiGraphics graphics, int x, int y, int width, FactionSelection target, int mouseX, int mouseY) {
        collapsibleSections.beginFrame();

        var font = EngineFont.get();
        var factionId = target.factionId();

        // Only render when the current generic faction inspector's snapshot agrees this is OUR type — that way we
        // don't paint over the wrong faction's details if the directory entry's type hasn't loaded yet.
        var generic = FactionInspectionView.current();
        if (generic == null || !generic.factionId().equals(factionId) || !generic.typeId().equals(supportedFactionTypeId())) {
            // Re-arm so a back-and-forth selection still triggers a fresh request the next time this faction is picked.
            if (lastRequestedFactionId != null && !lastRequestedFactionId.equals(factionId)) {
                lastRequestedFactionId = null;
            }
            return y;
        }

        // Poll once per client game tick (≈20 Hz) so live state (biomass / jelly / population / etc.) tracks in real
        // time. Render frames can run faster than ticks; gating on getGameTime() keeps the request rate bounded to the
        // server's update cadence regardless of FPS. A selection swap also forces an immediate refresh independent of
        // the tick boundary so the user doesn't see up to 50 ms of stale data after clicking a new faction.
        var level = Minecraft.getInstance().level;
        var currentTick = level != null ? level.getGameTime() : 0L;
        var selectionChanged = !factionId.equals(lastRequestedFactionId);
        if (selectionChanged || currentTick != lastRequestGameTick) {
            Alien.MOD.networking().sendToServer(new C2SRequestHiveInspectionPayload(factionId));
            lastRequestedFactionId = factionId;
            lastRequestGameTick = currentTick;
        }

        var cached = ClientHiveInspectionCache.current();
        if (cached == null || !cached.factionId().equals(factionId) || !cached.kind().equals(snapshotKind())) {
            var rowY = HiveInspectorRender.drawSectionHeader(graphics, font, x, y, width, snapshotKind() + " (loading…)");
            return HiveInspectorRender.drawNote(graphics, font, x, rowY, width, "Requesting snapshot from server…");
        }

        return renderSnapshot(graphics, font, x, y, width, cached.data(), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, FactionSelection target) {
        return collapsibleSections.mouseClicked(mouseX, mouseY, button);
    }

    protected CollapsibleInspectorSections.Header drawCollapsibleSectionHeader(
        GuiGraphics graphics,
        net.minecraft.client.gui.Font font,
        int x,
        int y,
        int width,
        String key,
        String label,
        int mouseX,
        int mouseY
    ) {
        return collapsibleSections.drawHeader(graphics, font, x, y, width, key, label, mouseX, mouseY);
    }

    /** Read a non-null ListTag of compound rows, in original insertion order, by key. */
    protected static net.minecraft.nbt.ListTag listOrEmpty(CompoundTag tag, String key) {
        return tag.contains(key) ? tag.getList(key, net.minecraft.nbt.Tag.TAG_COMPOUND) : new net.minecraft.nbt.ListTag();
    }
}
