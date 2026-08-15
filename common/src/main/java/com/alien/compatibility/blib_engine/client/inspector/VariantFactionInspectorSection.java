package com.alien.compatibility.blib_engine.client.inspector;

import com.alien.common.gameplay.hive.inspection.HiveInspectionSnapshot;
import com.alien.common.registry.init.AlienFactionDataTypes;
import com.blib.engine.api.client.v1.inspector.InspectorStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import static com.alien.compatibility.blib_engine.client.inspector.HiveInspectorRender.metric;

/**
 * Inspector section for AVP {@code avp_alien:variant} factions — top-level species umbrella showing variant metadata,
 * aggregates across every lineage of this variant, and a per-lineage summary list with health-at-a-glance rows.
 */
public final class VariantFactionInspectorSection extends AbstractHiveInspectorSection {

    @Override
    public String id() {
        return "avp_alien:hive_variant_inspector";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    protected ResourceLocation supportedFactionTypeId() {
        return AlienFactionDataTypes.VARIANT.getResourceLocation();
    }

    @Override
    protected String snapshotKind() {
        return HiveInspectionSnapshot.KIND_VARIANT;
    }

    @Override
    protected int renderSnapshot(GuiGraphics graphics, Font font, int x, int y, int width, CompoundTag s, int mouseX, int mouseY) {
        var rowY = y;
        var lineages = listOrEmpty(s, HiveInspectionSnapshot.K_LINEAGES);

        var identity = drawCollapsibleSectionHeader(graphics, font, x, rowY, width, "variant", "Variant", mouseX, mouseY);
        rowY = identity.nextY();
        if (identity.expanded()) {
            rowY += InspectorStyle.CONTENT_PADDING / 2;

            rowY = HiveInspectorRender.drawClippedRow(
                graphics,
                font,
                x,
                rowY,
                width,
                "ID",
                s.getString(HiveInspectionSnapshot.K_FACTION_ID)
            );
            rowY = HiveInspectorRender.drawRow(
                graphics,
                font,
                x,
                rowY,
                width,
                "Variant",
                s.getString(HiveInspectionSnapshot.K_VARIANT_NAME)
            );
            rowY = HiveInspectorRender.drawMetricStrip(
                graphics,
                font,
                x,
                rowY,
                width,
                metric("Age", HiveInspectorRender.formatTicks(s.getLong(HiveInspectionSnapshot.K_AGE_TICKS))),
                metric("Next #", String.valueOf(s.getLong(HiveInspectionSnapshot.K_NEXT_LINEAGE_NUMBER))),
                metric("Members", String.valueOf(s.getInt(HiveInspectionSnapshot.K_VARIANT_MEMBERS))),
                metric("Lineages", String.valueOf(lineages.size())),
                metric("Queen mothers", String.valueOf(listOrEmpty(s, HiveInspectionSnapshot.K_QUEEN_MOTHERS).size()))
            );
        }

        rowY += InspectorStyle.ROW_GAP;
        var aggregate = drawCollapsibleSectionHeader(graphics, font, x, rowY, width, "aggregate", "Aggregate", mouseX, mouseY);
        rowY = aggregate.nextY();
        if (aggregate.expanded()) {
            rowY += InspectorStyle.CONTENT_PADDING / 2;
            rowY = HiveInspectorRender.drawMetricStrip(
                graphics,
                font,
                x,
                rowY,
                width,
                metric("Biomass", String.valueOf(s.getLong(HiveInspectionSnapshot.K_AGG_BIOMASS))),
                metric("Royal", String.valueOf(s.getLong(HiveInspectionSnapshot.K_AGG_ROYAL_JELLY))),
                metric("Scourge", String.valueOf(s.getLong(HiveInspectionSnapshot.K_AGG_SCOURGE_JELLY)))
            );
            rowY = HiveInspectorRender.drawBarRow(
                graphics,
                font,
                x,
                rowY,
                width,
                "Location pop",
                s.getLong(HiveInspectionSnapshot.K_AGG_TOTAL_POP),
                s.getLong(HiveInspectionSnapshot.K_AGG_POP_CAP)
            );
            rowY = HiveInspectorRender.drawMetricStrip(
                graphics,
                font,
                x,
                rowY,
                width,
                metric("Loaded", String.valueOf(s.getLong(HiveInspectionSnapshot.K_AGG_LOADED_MEMBERS))),
                metric("Local res", String.valueOf(s.getLong(HiveInspectionSnapshot.K_AGG_LOCAL_RESERVES))),
                metric("Convoy pop", String.valueOf(s.getLong(HiveInspectionSnapshot.K_AGG_CONVOY_MEMBERS))),
                metric("Lineage members", String.valueOf(s.getLong(HiveInspectionSnapshot.K_AGG_LINEAGE_MEMBERS))),
                metric("Loc members", String.valueOf(s.getLong(HiveInspectionSnapshot.K_AGG_LOCATION_MEMBERS))),
                metric("Locations", String.valueOf(s.getLong(HiveInspectionSnapshot.K_AGG_LOCATIONS))),
                metric(
                    "Chunks",
                    s.getLong(HiveInspectionSnapshot.K_AGG_CHUNKS_LOADED) + "/" + s.getLong(HiveInspectionSnapshot.K_AGG_CLAIMED_CHUNKS)
                )
            );
        }

        rowY += InspectorStyle.ROW_GAP;
        var queenMothers = listOrEmpty(s, HiveInspectionSnapshot.K_QUEEN_MOTHERS);
        var queenMotherSection = drawCollapsibleSectionHeader(
            graphics,
            font,
            x,
            rowY,
            width,
            "queen_mothers",
            "Queen Mothers (" + queenMothers.size() + ")",
            mouseX,
            mouseY
        );
        rowY = queenMotherSection.nextY();
        if (queenMotherSection.expanded()) {
            rowY += InspectorStyle.CONTENT_PADDING / 2;
            if (queenMothers.isEmpty()) {
                rowY = HiveInspectorRender.drawNote(graphics, font, x, rowY, width, "(none recorded)");
            } else {
                for (var i = 0; i < queenMothers.size(); i++) {
                    var row = queenMothers.getCompound(i);
                    rowY = HiveInspectorRender.drawRow(
                        graphics,
                        font,
                        x,
                        rowY,
                        width,
                        row.getString(HiveInspectionSnapshot.K_DIMENSION),
                        row.hasUUID(HiveInspectionSnapshot.K_UUID)
                            ? HiveInspectorRender.shortUuid(row.getUUID(HiveInspectionSnapshot.K_UUID))
                            : "missing"
                    );
                }
            }
        }

        rowY += InspectorStyle.ROW_GAP;
        var lineagesSection = drawCollapsibleSectionHeader(
            graphics,
            font,
            x,
            rowY,
            width,
            "lineages",
            "Lineages (" + lineages.size() + ")",
            mouseX,
            mouseY
        );
        rowY = lineagesSection.nextY();
        if (lineagesSection.expanded()) {
            rowY += InspectorStyle.CONTENT_PADDING / 2;
            if (lineages.isEmpty()) {
                rowY = HiveInspectorRender.drawNote(graphics, font, x, rowY, width, "(none - variant has no lineages)");
            } else {
                for (var i = 0; i < lineages.size(); i++) {
                    var row = lineages.getCompound(i);
                    var label = "#" + row.getLong(HiveInspectionSnapshot.K_LINEAGE_NUMBER);
                    var title = row.getString(HiveInspectionSnapshot.K_DISPLAY_NAME);
                    if (title.isBlank()) {
                        title = row.getString(HiveInspectionSnapshot.K_FACTION_ID);
                    }
                    if (row.contains(HiveInspectionSnapshot.K_REMOVAL_REASON)) {
                        title = title + " (" + row.getString(HiveInspectionSnapshot.K_REMOVAL_REASON) + ")";
                    }
                    rowY = HiveInspectorRender.drawItemHeader(graphics, font, x, rowY, width, label, title);
                    rowY = HiveInspectorRender.drawMetricStrip(
                        graphics,
                        font,
                        x,
                        rowY,
                        width,
                        metric("Dim", row.getString(HiveInspectionSnapshot.K_DIMENSION)),
                        metric("Locs", String.valueOf(row.getInt(HiveInspectionSnapshot.K_LOCATION_COUNT))),
                        metric("Members", String.valueOf(row.getInt(HiveInspectionSnapshot.K_LINEAGE_MEMBER_TOTAL))),
                        metric("Biomass", String.valueOf(row.getLong(HiveInspectionSnapshot.K_BIOMASS))),
                        metric("Royal", String.valueOf(row.getLong(HiveInspectionSnapshot.K_ROYAL_JELLY))),
                        metric("Scourge", String.valueOf(row.getLong(HiveInspectionSnapshot.K_SCOURGE_JELLY))),
                        metric(
                            "Pop",
                            row.getLong(HiveInspectionSnapshot.K_TOTAL_POP) + "/" + row.getLong(HiveInspectionSnapshot.K_POP_CAP)
                        ),
                        metric("Loaded", String.valueOf(row.getInt(HiveInspectionSnapshot.K_LOADED_MEMBER_TOTAL))),
                        metric("Local res", String.valueOf(row.getInt(HiveInspectionSnapshot.K_LOCAL_RESERVE_TOTAL))),
                        metric(
                            "Convoys",
                            row.getInt(HiveInspectionSnapshot.K_CONVOY_COUNT) + "/" + row.getInt(
                                HiveInspectionSnapshot.K_CONVOY_MEMBER_TOTAL
                            )
                        ),
                        metric(
                            "Chunks",
                            row.getInt(HiveInspectionSnapshot.K_CHUNKS_LOADED) + "/" + row.getInt(HiveInspectionSnapshot.K_CLAIMED_CHUNKS)
                        ),
                        metric("State", lineageState(row))
                    );
                    rowY = HiveInspectorRender.drawClippedRow(
                        graphics,
                        font,
                        x,
                        rowY,
                        width,
                        "ID",
                        row.getString(HiveInspectionSnapshot.K_FACTION_ID)
                    );
                    if (row.hasUUID(HiveInspectionSnapshot.K_FOUNDER_ID)) {
                        rowY = HiveInspectorRender.drawRow(
                            graphics,
                            font,
                            x,
                            rowY,
                            width,
                            "Founder",
                            HiveInspectorRender.shortUuid(row.getUUID(HiveInspectionSnapshot.K_FOUNDER_ID))
                        );
                    }
                    if (row.hasUUID(HiveInspectionSnapshot.K_EMPRESS_ID)) {
                        rowY = HiveInspectorRender.drawRow(
                            graphics,
                            font,
                            x,
                            rowY,
                            width,
                            "Empress",
                            HiveInspectorRender.shortUuid(row.getUUID(HiveInspectionSnapshot.K_EMPRESS_ID))
                        );
                    }
                    rowY += InspectorStyle.ROW_GAP;
                }
            }
        }

        return rowY;
    }

    private static String lineageState(CompoundTag row) {
        var pendingEmpress = row.getBoolean(HiveInspectionSnapshot.K_PENDING_EMPRESS);
        if (pendingEmpress) {
            return "empress";
        }
        return "stable";
    }
}
