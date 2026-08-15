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
 * Inspector section for AVP {@code avp_alien:lineage} factions — shows lineage-level metadata plus aggregated biomass /
 * jelly / population across all owned hive locations, with a per-location summary list for drill-down.
 */
public final class LineageFactionInspectorSection extends AbstractHiveInspectorSection {

    @Override
    public String id() {
        return "avp_alien:hive_lineage_inspector";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    protected ResourceLocation supportedFactionTypeId() {
        return AlienFactionDataTypes.LINEAGE.getResourceLocation();
    }

    @Override
    protected String snapshotKind() {
        return HiveInspectionSnapshot.KIND_LINEAGE;
    }

    @Override
    protected int renderSnapshot(GuiGraphics graphics, Font font, int x, int y, int width, CompoundTag s, int mouseX, int mouseY) {
        var rowY = y;

        var identity = drawCollapsibleSectionHeader(graphics, font, x, rowY, width, "lineage", "Lineage", mouseX, mouseY);
        rowY = identity.nextY();
        if (identity.expanded()) {
            rowY += InspectorStyle.CONTENT_PADDING / 2;

            if (s.contains(HiveInspectionSnapshot.K_REMOVAL_REASON)) {
                rowY = HiveInspectorRender.drawRow(
                    graphics,
                    font,
                    x,
                    rowY,
                    width,
                    "Removal",
                    s.getString(HiveInspectionSnapshot.K_REMOVAL_REASON)
                );
            }
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
            rowY = HiveInspectorRender.drawRow(
                graphics,
                font,
                x,
                rowY,
                width,
                "Dimension",
                s.getString(HiveInspectionSnapshot.K_DIMENSION)
            );
            rowY = HiveInspectorRender.drawMetricStrip(
                graphics,
                font,
                x,
                rowY,
                width,
                metric("Lineage #", String.valueOf(s.getLong(HiveInspectionSnapshot.K_LINEAGE_NUMBER))),
                metric("Age", HiveInspectorRender.formatTicks(s.getLong(HiveInspectionSnapshot.K_AGE_TICKS))),
                metric("Members", String.valueOf(s.getInt(HiveInspectionSnapshot.K_LINEAGE_MEMBER_TOTAL))),
                metric("Locations", String.valueOf(s.getInt(HiveInspectionSnapshot.K_LOCATION_COUNT))),
                metric("Next loc #", String.valueOf(s.getLong(HiveInspectionSnapshot.K_NEXT_LOCATION_NUMBER))),
                metric("Convoys", String.valueOf(s.getInt(HiveInspectionSnapshot.K_CONVOY_COUNT)))
            );
            if (s.hasUUID(HiveInspectionSnapshot.K_FOUNDER_ID)) {
                rowY = HiveInspectorRender.drawRow(
                    graphics,
                    font,
                    x,
                    rowY,
                    width,
                    "Founder",
                    HiveInspectorRender.shortUuid(s.getUUID(HiveInspectionSnapshot.K_FOUNDER_ID))
                );
            }
            if (s.hasUUID(HiveInspectionSnapshot.K_EMPRESS_ID)) {
                rowY = HiveInspectorRender.drawRow(
                    graphics,
                    font,
                    x,
                    rowY,
                    width,
                    "Empress",
                    HiveInspectorRender.shortUuid(s.getUUID(HiveInspectionSnapshot.K_EMPRESS_ID))
                );
            }
            if (s.getBoolean(HiveInspectionSnapshot.K_PENDING_EMPRESS)) {
                rowY = HiveInspectorRender.drawNote(graphics, font, x, rowY, width, "Pending empress emergence");
            }
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
                metric("Convoy pop", String.valueOf(s.getInt(HiveInspectionSnapshot.K_CONVOY_MEMBER_TOTAL))),
                metric("Loc members", String.valueOf(s.getLong(HiveInspectionSnapshot.K_AGG_LOCATION_MEMBERS))),
                metric(
                    "Chunks",
                    s.getLong(HiveInspectionSnapshot.K_AGG_CHUNKS_LOADED) + "/" + s.getLong(HiveInspectionSnapshot.K_AGG_CLAIMED_CHUNKS)
                )
            );
        }

        rowY += InspectorStyle.ROW_GAP;
        var convoyRows = listOrEmpty(s, HiveInspectionSnapshot.K_CONVOYS);
        var convoys = drawCollapsibleSectionHeader(
            graphics,
            font,
            x,
            rowY,
            width,
            "convoys",
            "Convoys (" + s.getInt(HiveInspectionSnapshot.K_CONVOY_COUNT) + ")",
            mouseX,
            mouseY
        );
        rowY = convoys.nextY();
        if (convoys.expanded()) {
            rowY += InspectorStyle.CONTENT_PADDING / 2;
            if (convoyRows.isEmpty()) {
                rowY = HiveInspectorRender.drawNote(graphics, font, x, rowY, width, "(none - no population is in transit)");
            } else {
                for (var i = 0; i < convoyRows.size(); i++) {
                    var row = convoyRows.getCompound(i);
                    rowY = HiveInspectorRender.drawItemHeader(
                        graphics,
                        font,
                        x,
                        rowY,
                        width,
                        row.getString(HiveInspectionSnapshot.K_TYPE),
                        "total " + row.getInt(HiveInspectionSnapshot.K_VALUE)
                    );
                    rowY = HiveInspectorRender.drawMetricStrip(
                        graphics,
                        font,
                        x,
                        rowY,
                        width,
                        metric("Dim", row.getString(HiveInspectionSnapshot.K_DIMENSION)),
                        metric(
                            "Current",
                            coords(
                                row,
                                HiveInspectionSnapshot.K_CURRENT_X,
                                HiveInspectionSnapshot.K_CURRENT_Y,
                                HiveInspectionSnapshot.K_CURRENT_Z
                            )
                        ),
                        metric(
                            "Target",
                            coords(row, HiveInspectionSnapshot.K_DEST_X, HiveInspectionSnapshot.K_DEST_Y, HiveInspectionSnapshot.K_DEST_Z)
                        ),
                        metric("Dispatched", String.valueOf(row.getLong(HiveInspectionSnapshot.K_DISPATCHED_TICK))),
                        metric("Biomass", String.valueOf(row.getInt(HiveInspectionSnapshot.K_BIOMASS_PAYLOAD))),
                        metric("Empress", row.getBoolean(HiveInspectionSnapshot.K_CARRIES_EMPRESS) ? "yes" : "no")
                    );
                    if (row.contains(HiveInspectionSnapshot.K_SOURCE_LOCATION_ID)) {
                        rowY = HiveInspectorRender.drawClippedRow(
                            graphics,
                            font,
                            x,
                            rowY,
                            width,
                            "Source",
                            row.getString(HiveInspectionSnapshot.K_SOURCE_LOCATION_ID)
                        );
                    }
                    if (row.contains(HiveInspectionSnapshot.K_DESTINATION_LOCATION_ID)) {
                        rowY = HiveInspectorRender.drawClippedRow(
                            graphics,
                            font,
                            x,
                            rowY,
                            width,
                            "Destination",
                            row.getString(HiveInspectionSnapshot.K_DESTINATION_LOCATION_ID)
                        );
                    }
                    if (row.hasUUID(HiveInspectionSnapshot.K_TARGET_PLAYER_ID)) {
                        rowY = HiveInspectorRender.drawRow(
                            graphics,
                            font,
                            x,
                            rowY,
                            width,
                            "Target",
                            HiveInspectorRender.shortUuid(row.getUUID(HiveInspectionSnapshot.K_TARGET_PLAYER_ID))
                        );
                    }
                    var composition = listOrEmpty(row, HiveInspectionSnapshot.K_RESERVES_BY_TYPE);
                    if (!composition.isEmpty()) {
                        rowY = HiveInspectorRender.drawCountRows(
                            graphics,
                            font,
                            x,
                            rowY,
                            width,
                            composition,
                            HiveInspectionSnapshot.K_KEY,
                            HiveInspectionSnapshot.K_VALUE
                        );
                    }
                    rowY += InspectorStyle.ROW_GAP;
                }
            }
        }

        rowY += InspectorStyle.ROW_GAP;
        var locations = listOrEmpty(s, HiveInspectionSnapshot.K_LOCATIONS);
        var locationsSection = drawCollapsibleSectionHeader(
            graphics,
            font,
            x,
            rowY,
            width,
            "locations",
            "Locations (" + locations.size() + ")",
            mouseX,
            mouseY
        );
        rowY = locationsSection.nextY();
        if (locationsSection.expanded()) {
            rowY += InspectorStyle.CONTENT_PADDING / 2;
            if (locations.isEmpty()) {
                rowY = HiveInspectorRender.drawNote(graphics, font, x, rowY, width, "(none - lineage owns no locations)");
            } else {
                for (var i = 0; i < locations.size(); i++) {
                    var row = locations.getCompound(i);
                    var label = row.getString(HiveInspectionSnapshot.K_LOCATION_ID);
                    if (row.contains(HiveInspectionSnapshot.K_REMOVAL_REASON)) {
                        label = label + " (" + row.getString(HiveInspectionSnapshot.K_REMOVAL_REASON) + ")";
                    }
                    var locationNumber = row.getLong(HiveInspectionSnapshot.K_LOCATION_NUMBER);
                    rowY = HiveInspectorRender.drawItemHeader(
                        graphics,
                        font,
                        x,
                        rowY,
                        width,
                        locationNumber >= 0 ? "Location #" + locationNumber : "Location",
                        label
                    );
                    rowY = HiveInspectorRender.drawMetricStrip(
                        graphics,
                        font,
                        x,
                        rowY,
                        width,
                        metric("Pop", row.getInt(HiveInspectionSnapshot.K_TOTAL_POP) + "/" + row.getInt(HiveInspectionSnapshot.K_POP_CAP)),
                        metric("Loaded", String.valueOf(row.getInt(HiveInspectionSnapshot.K_LOADED_MEMBER_TOTAL))),
                        metric("Reserves", String.valueOf(row.getInt(HiveInspectionSnapshot.K_LOCAL_RESERVE_TOTAL))),
                        metric("Members", String.valueOf(row.getInt(HiveInspectionSnapshot.K_LOCATION_FACTION_MEMBER_COUNT))),
                        metric("Biomass", String.valueOf(row.getInt(HiveInspectionSnapshot.K_BIOMASS))),
                        metric("Royal", String.valueOf(row.getInt(HiveInspectionSnapshot.K_ROYAL_JELLY))),
                        metric("Scourge", String.valueOf(row.getInt(HiveInspectionSnapshot.K_SCOURGE_JELLY))),
                        metric(
                            "Chunks",
                            row.getInt(HiveInspectionSnapshot.K_CHUNKS_LOADED) + "/" + row.getInt(HiveInspectionSnapshot.K_CLAIMED_CHUNKS)
                        ),
                        metric("Age", HiveInspectorRender.formatTicks(row.getLong(HiveInspectionSnapshot.K_AGE_TICKS))),
                        metric("No contact", HiveInspectorRender.formatTicks(row.getLong(HiveInspectionSnapshot.K_NO_CONTACT_TICKS))),
                        metric("Evac", HiveInspectorRender.formatTicks(row.getLong(HiveInspectionSnapshot.K_EVACUATING_TICKS))),
                        metric("Spread", formatSpreadStatus(row)),
                        metric("Attempt", formatSpreadAttempt(row)),
                        metric(
                            "Leader",
                            row.hasUUID(HiveInspectionSnapshot.K_LEADER_ID)
                                ? HiveInspectorRender.shortUuid(row.getUUID(HiveInspectionSnapshot.K_LEADER_ID))
                                : "none"
                        )
                    );
                    rowY = HiveInspectorRender.drawRow(
                        graphics,
                        font,
                        x,
                        rowY,
                        width,
                        "Center",
                        coords(row, HiveInspectionSnapshot.K_CENTER_X, HiveInspectionSnapshot.K_CENTER_Y, HiveInspectionSnapshot.K_CENTER_Z)
                    );
                    rowY += InspectorStyle.ROW_GAP;
                }
            }
        }

        return rowY;
    }

    private static String coords(CompoundTag tag, String xKey, String yKey, String zKey) {
        return tag.getInt(xKey) + ", " + tag.getInt(yKey) + ", " + tag.getInt(zKey);
    }

    private static String formatSpreadStatus(CompoundTag row) {
        if (row.getInt(HiveInspectionSnapshot.K_LOCATION_COUNT) >= row.getInt(HiveInspectionSnapshot.K_SPREAD_MAX_LOCATIONS)) {
            return "cap";
        }
        var remaining = row.getLong(HiveInspectionSnapshot.K_SPREAD_COOLDOWN_REMAINING_TICKS);
        if (remaining < 0L) {
            return "n/a";
        }
        return row.getBoolean(HiveInspectionSnapshot.K_SPREAD_COOLDOWN_ELIGIBLE)
            ? "ready"
            : HiveInspectorRender.formatTicks(remaining);
    }

    private static String formatSpreadAttempt(CompoundTag row) {
        return row.getBoolean(HiveInspectionSnapshot.K_SPREAD_HAS_LAST_ATTEMPT)
            ? row.getString(HiveInspectionSnapshot.K_SPREAD_LAST_ATTEMPT_RESULT)
            : "none";
    }
}
