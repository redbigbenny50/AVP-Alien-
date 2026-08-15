package com.alien.client.gui;

import com.alien.Alien;
import com.alien.client.waypoint.ClientWaypointStore;
import com.alien.common.gameplay.level.saveddata.TrackedQueenRow;
import com.alien.common.network.payload.C2SAckLostTrackersPayload;
import com.alien.common.network.payload.C2SDestroyTrackerPayload;
import com.alien.common.network.payload.C2SRenameTrackerPayload;
import com.alien.common.network.payload.C2SRequestTrackedQueensPayload;
import com.alien.common.registry.init.AlienSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The tracking PDA's readout, drawn over a Weyland-Yutani mobile-data-unit frame texture. A green CRT screen lists
 * tagged queens with distance and last-seen location; the three physical buttons on the frame's bottom-right scroll the
 * list and (when a tracker has gone dark) raise a hazard button that opens a "lost communications" drawer. Does not
 * pause the world and draws its own dimmed backdrop (no vanilla blur).
 * <p>
 * Design space is the frame texture at quarter scale (340x440 for a 1360x1760 texture), so the font stays legible and
 * the whole unit is scaled to fit the window. Screen-area and button-slot rectangles are the texture pixels / 4.
 */
public class TrackingPdaScreen extends Screen {

    // ===== Frame + button textures =====
    private static final ResourceLocation FRAME_TEX = tex("pda_frame");

    private static final ResourceLocation SCROLL_UP_TEX = tex("pda_scroll_up");

    private static final ResourceLocation SCROLL_UP_CLICKED_TEX = tex("pda_scroll_up_clicked");

    private static final ResourceLocation SCROLL_DOWN_TEX = tex("pda_scroll_down");

    private static final ResourceLocation SCROLL_DOWN_CLICKED_TEX = tex("pda_scroll_down_clicked");

    private static final ResourceLocation CAUTION_TEX = tex("pda_caution");

    private static final ResourceLocation CAUTION_CLICKED_TEX = tex("pda_caution_clicked");

    private static final int TEX_W = 1360;

    private static final int TEX_H = 1760;

    private static final int BTN_TEX_W = 137;

    private static final int BTN_TEX_H = 103;

    // ===== Virtual design space (texture / 4) =====
    private static final int DESIGN_W = 340;

    private static final int DESIGN_H = 440;

    // ===== Screen (CRT) rectangle, mapped from texture px (148,317,1062,1127) / 4, inset slightly for margins =====
    private static final int SX = 39;

    private static final int SY = 81;

    private static final int SW = 262;

    private static final int SH = 278;

    private static final int CX = SX + 12;

    private static final int CR = SX + SW - 12;

    private static final int LIST_TOP = SY + 54;

    private static final int LIST_BOTTOM = SY + SH - 30;

    private static final int ROW_H = 18;

    // ===== Physical button slots (texture px / 4) =====
    private static final int BTN_Y = 389;

    private static final int BTN_W = 34;

    private static final int BTN_H = 26;

    private static final int UP_X = 199;

    private static final int DOWN_X = 238;

    private static final int CAUTION_X = 277;

    private static final int SLOT_UP = 90;

    private static final int SLOT_DOWN = 91;

    private static final int SLOT_CAUTION = 92;

    // ===== Theme colors =====
    private static final int BACKDROP = 0xC8000000;

    private static final int GREEN = 0xFF39C24A;

    private static final int GREEN_BRIGHT = 0xFF56E85E;

    private static final int GREEN_DIM = 0xFF1E7A2C;

    private static final int SEL_FILL = 0xFF52D33B;

    private static final int SEL_TEXT = 0xFF06210A;

    private static final int PANEL_BG = 0xFF07160A;

    private static final int BTN_FILL = 0xFF0E3312;

    private static final int BTN_FILL_HOVER = 0xFF1E7A2C;

    private static final int BTN_FILL_DISABLED = 0xFF0A200D;

    private static final int BTN_BORDER = 0xFF39C24A;

    private static final int BTN_BORDER_HOVER = 0xFF9BFF9B;

    private static final int BTN_TEXT = 0xFFCFFFD0;

    private static final int BTN_TEXT_DISABLED = 0xFF4A6A4C;

    private static final int HAZARD_RED = 0xFFE24B3B;

    private static final int HAZARD_AMBER = 0xFFE7A012;

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final int REFRESH_TICKS = 10;

    private final List<TrackedQueenRow> rows;

    private int scrollOffset;

    private int lastHover;

    private int pressedSlot = -1;

    private int refreshTimer;

    private boolean drawerOpen;

    private TrackedQueenRow selected;

    private boolean lostDrawerOpen;

    private boolean renaming;

    private String renameBuffer = "";

    public TrackingPdaScreen(List<TrackedQueenRow> rows) {
        super(Component.literal("Tracking PDA"));
        this.rows = new ArrayList<>(rows);
    }

    public static void open(List<TrackedQueenRow> rows) {
        Minecraft.getInstance().setScreen(new TrackingPdaScreen(rows));
    }

    /** Refresh the list in place without rebuilding the screen, keeping scroll position and any open drawer. */
    public void update(List<TrackedQueenRow> newRows) {
        UUID selectedId = selected != null ? selected.id() : null;
        rows.clear();
        rows.addAll(newRows);
        if (selectedId != null) {
            selected = null;
            for (var row : rows) {
                if (row.id().equals(selectedId)) {
                    selected = row;
                    break;
                }
            }
            if (selected == null) {
                drawerOpen = false;
            }
        }
        clampScroll();
    }

    @Override
    public void tick() {
        super.tick();
        if (++refreshTimer >= REFRESH_TICKS) {
            refreshTimer = 0;
            Alien.MOD.networking().sendToServer(C2SRequestTrackedQueensPayload.INSTANCE);
        }
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath("avp_alien", "textures/gui/" + name + ".png");
    }

    @Override
    protected void init() {
        super.init();
        playUi(AlienSoundEvents.UI_TERMINAL_OPEN.get(), 0.8F, 1.0F);
    }

    @Override
    public void removed() {
        playUi(AlienSoundEvents.UI_TERMINAL_CLOSE.get(), 0.8F, 1.0F);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Own dimmed backdrop, no vanilla blur.
    }

    private double scale() {
        return Math.min((double) this.width / DESIGN_W, (double) this.height / DESIGN_H) * 0.92D;
    }

    private double originX() {
        return (this.width - DESIGN_W * scale()) / 2.0D;
    }

    private double originY() {
        return (this.height - DESIGN_H * scale()) / 2.0D;
    }

    private double vx(double mouseX) {
        return (mouseX - originX()) / scale();
    }

    private double vy(double mouseY) {
        return (mouseY - originY()) / scale();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, BACKDROP);

        double mx = vx(mouseX);
        double my = vy(mouseY);
        updateHoverSound(mx, my);

        graphics.pose().pushPose();
        graphics.pose().translate((float) originX(), (float) originY(), 0.0F);
        graphics.pose().scale((float) scale(), (float) scale(), 1.0F);

        graphics.blit(FRAME_TEX, 0, 0, DESIGN_W, DESIGN_H, 0.0F, 0.0F, TEX_W, TEX_H, TEX_W, TEX_H);
        drawScreenContent(graphics, mx, my);
        drawButtons(graphics);

        if (drawerOpen && selected != null) {
            drawRowDrawer(graphics, mx, my);
        } else if (lostDrawerOpen) {
            drawLostDrawer(graphics, mx, my);
        }
        if (renaming) {
            drawRenameOverlay(graphics);
        }

        graphics.pose().popPose();
    }

    private void drawScreenContent(GuiGraphics g, double mx, double my) {
        g.drawString(this.font, Component.literal("MU/TH/UR SYSTEMS"), CX, SY + 8, GREEN_BRIGHT, false);
        g.drawString(this.font, Component.literal("MOBILE DATA UNIT"), CX, SY + 18, GREEN, false);
        drawRightString(g, LocalTime.now().format(CLOCK), CR, SY + 8, GREEN_BRIGHT);
        drawRightString(g, dateStamp(), CR, SY + 18, GREEN);

        g.hLine(CX, CR, SY + 32, GREEN_DIM);
        g.drawString(this.font, Component.literal("\u25C8 ALIEN QUEEN TRACKERS"), CX, SY + 40, GREEN_BRIGHT, false);

        boolean interactive = !drawerOpen && !lostDrawerOpen;
        if (rows.isEmpty()) {
            g.drawCenteredString(this.font, Component.literal("NO QUEENS TRACKED"), SX + SW / 2, LIST_TOP + 20, GREEN_DIM);
        } else {
            int visible = visibleRows();
            clampScroll();
            for (int i = 0; i < visible && (i + scrollOffset) < rows.size(); i++) {
                int index = i + scrollOffset;
                int rowTop = LIST_TOP + i * ROW_H;
                drawRow(g, rows.get(index), index, rowTop, interactive && isInRow(mx, my, rowTop));
            }
            if (rows.size() > visible) {
                drawScrollBar(g);
            }
        }

        g.hLine(CX, CR, SY + SH - 26, GREEN_DIM);
        g.drawString(this.font, Component.literal("[SCROLL] BROWSE  [CLICK] OPEN"), CX, SY + SH - 20, GREEN, false);
        g.drawString(this.font, Component.literal("[ESC] CLOSE"), CX, SY + SH - 11, GREEN, false);
    }

    private void drawRow(GuiGraphics g, TrackedQueenRow row, int index, int rowTop, boolean hovered) {
        boolean pinned = ClientWaypointStore.isPinned(row.id());
        int textColor = hovered ? SEL_TEXT : GREEN;

        if (hovered) {
            g.fill(SX + 6, rowTop, SX + SW - 6, rowTop + ROW_H - 2, SEL_FILL);
            g.drawString(this.font, Component.literal(">"), SX + 10, rowTop + 5, SEL_TEXT, false);
        }

        String id = String.format(Locale.ROOT, "AQ-%02d", index + 1);
        g.drawString(this.font, Component.literal(id), CX, rowTop + 5, textColor, false);

        String label = (shortDim(row.dimension().location().toString()) + " - " + row.name()).toUpperCase(Locale.ROOT);
        int nameX = CX + 48;
        g.drawString(this.font, Component.literal(fit(label, CR - 32 - nameX)), nameX, rowTop + 5, textColor, false);

        drawRightString(g, distanceText(row), CR - 12, rowTop + 5, textColor);

        String marker = pinned ? "\u2605" : "\u25B6";
        int markerColor = hovered ? SEL_TEXT : (pinned ? GREEN_BRIGHT : GREEN);
        g.drawString(this.font, Component.literal(marker), CR - 4, rowTop + 5, markerColor, false);
    }

    private void drawScrollBar(GuiGraphics g) {
        int x = SX + SW - 4;
        int top = LIST_TOP;
        int height = LIST_BOTTOM - LIST_TOP;
        int visible = visibleRows();
        g.fill(x, top, x + 2, top + height, GREEN_DIM);
        int thumb = Math.max(10, height * visible / rows.size());
        int maxScroll = Math.max(1, rows.size() - visible);
        int thumbY = top + (height - thumb) * scrollOffset / maxScroll;
        g.fill(x, thumbY, x + 2, thumbY + thumb, GREEN_BRIGHT);
    }

    // ===== Physical buttons on the frame =====
    private void drawButtons(GuiGraphics g) {
        blitButton(g, pressedSlot == SLOT_UP ? SCROLL_UP_CLICKED_TEX : SCROLL_UP_TEX, UP_X);
        blitButton(g, pressedSlot == SLOT_DOWN ? SCROLL_DOWN_CLICKED_TEX : SCROLL_DOWN_TEX, DOWN_X);

        if (ClientTrackerAlerts.hasLost()) {
            boolean blink = (System.currentTimeMillis() / 450L) % 2L == 0L;
            ResourceLocation caution = (pressedSlot == SLOT_CAUTION || blink) ? CAUTION_CLICKED_TEX : CAUTION_TEX;
            blitButton(g, caution, CAUTION_X);
        }
    }

    private void blitButton(GuiGraphics g, ResourceLocation texture, int x) {
        g.blit(texture, x, BTN_Y, BTN_W, BTN_H, 0.0F, 0.0F, BTN_TEX_W, BTN_TEX_H, BTN_TEX_W, BTN_TEX_H);
    }

    // ===== Row-action drawer =====
    private void drawRowDrawer(GuiGraphics g, double mx, double my) {
        g.fill(SX, SY, SX + SW, SY + SH, 0xCC000000);

        int pw = 232;
        int ph = 200;
        int px = SX + (SW - pw) / 2;
        int py = SY + (SH - ph) / 2;
        g.fill(px, py, px + pw, py + ph, PANEL_BG);
        drawBox(g, px, py, pw, ph, GREEN);

        boolean pinned = selected != null && ClientWaypointStore.isPinned(selected.id());
        g.drawCenteredString(this.font, Component.literal(selectedTitle()), px + pw / 2, py + 10, GREEN_BRIGHT);
        g.drawCenteredString(this.font, Component.literal(selectedSubtitle()), px + pw / 2, py + 22, GREEN_DIM);

        int bx = px + 14;
        int bw = pw - 28;
        drawButton(g, bx, py + 38, bw, 20, "CREATE WAYPOINT", contains(mx, my, bx, py + 38, bw, 20), !pinned);
        drawButton(g, bx, py + 62, bw, 20, "REMOVE WAYPOINT", contains(mx, my, bx, py + 62, bw, 20), pinned);
        drawButton(g, bx, py + 86, bw, 20, "SHARE WITH CHAT", contains(mx, my, bx, py + 86, bw, 20), true);
        drawButton(g, bx, py + 110, bw, 20, "RENAME TRACKER", contains(mx, my, bx, py + 110, bw, 20), true);
        drawButton(g, bx, py + 134, bw, 20, "DESTROY TRACKER", contains(mx, my, bx, py + 134, bw, 20), true);
        drawBox(g, bx, py + 134, bw, 20, HAZARD_RED);
        drawButton(g, bx, py + 158, bw, 20, "CLOSE", contains(mx, my, bx, py + 158, bw, 20), true);
    }

    // ===== Lost-communications drawer =====
    private void drawLostDrawer(GuiGraphics g, double mx, double my) {
        g.fill(SX, SY, SX + SW, SY + SH, 0xCC000000);

        int pw = 244;
        int ph = 200;
        int px = SX + (SW - pw) / 2;
        int py = SY + (SH - ph) / 2;
        g.fill(px, py, px + pw, py + ph, PANEL_BG);
        drawBox(g, px, py, pw, ph, HAZARD_AMBER);

        g.drawString(this.font, Component.literal("\u26A0 LOST COMMUNICATIONS"), px + 12, py + 10, HAZARD_AMBER, false);
        g.hLine(px + 12, px + pw - 12, py + 22, GREEN_DIM);

        var lost = ClientTrackerAlerts.lost();
        int listY = py + 28;
        int entryH = 22;
        int maxRows = 5;
        for (int i = 0; i < lost.size() && i < maxRows; i++) {
            var l = lost.get(i);
            int ey = listY + i * entryH;
            String line = l.name().toUpperCase(Locale.ROOT) + " \u2014 " + reasonText(l.reason());
            g.drawString(this.font, Component.literal(fit(line, pw - 24)), px + 12, ey, GREEN, false);
            String where = shortDim(l.dimension().location().toString()).toUpperCase(Locale.ROOT)
                + " " + l.pos().getX() + ", " + l.pos().getY() + ", " + l.pos().getZ();
            g.drawString(this.font, Component.literal(fit(where, pw - 32)), px + 20, ey + 10, GREEN_DIM, false);
        }
        if (lost.size() > maxRows) {
            g.drawString(
                this.font,
                Component.literal("+" + (lost.size() - maxRows) + " MORE"),
                px + 12,
                listY + maxRows * entryH,
                GREEN_DIM,
                false
            );
        }

        int bx = px + 14;
        int bw = pw - 28;
        drawButton(g, bx, py + ph - 46, bw, 20, "ACKNOWLEDGE ALL", contains(mx, my, bx, py + ph - 46, bw, 20), true);
        drawButton(g, bx, py + ph - 24, bw, 20, "CLOSE", contains(mx, my, bx, py + ph - 24, bw, 20), true);
    }

    // ===== Input =====
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        double mx = vx(mouseX);
        double my = vy(mouseY);

        if (renaming) {
            return true;
        }

        if (lostDrawerOpen) {
            int pw = 244;
            int ph = 200;
            int px = SX + (SW - pw) / 2;
            int py = SY + (SH - ph) / 2;
            int bx = px + 14;
            int bw = pw - 28;
            if (contains(mx, my, bx, py + ph - 46, bw, 20)) {
                acknowledgeLost();
                return true;
            }
            if (contains(mx, my, bx, py + ph - 24, bw, 20) || !contains(mx, my, px, py, pw, ph)) {
                lostDrawerOpen = false;
                playUi(AlienSoundEvents.UI_TERMINAL_CLOSE.get(), 0.7F, 1.2F);
                return true;
            }
            return true;
        }

        if (drawerOpen && selected != null) {
            int pw = 232;
            int ph = 200;
            int px = SX + (SW - pw) / 2;
            int py = SY + (SH - ph) / 2;
            int bx = px + 14;
            int bw = pw - 28;
            boolean pinned = ClientWaypointStore.isPinned(selected.id());

            if (contains(mx, my, bx, py + 38, bw, 20) && !pinned) {
                createWaypoint(selected);
                closeDrawer(false);
                return true;
            }
            if (contains(mx, my, bx, py + 62, bw, 20) && pinned) {
                removeWaypoint(selected);
                closeDrawer(false);
                return true;
            }
            if (contains(mx, my, bx, py + 86, bw, 20)) {
                shareWithChat(selected);
                closeDrawer(false);
                return true;
            }
            if (contains(mx, my, bx, py + 110, bw, 20)) {
                startRename();
                return true;
            }
            if (contains(mx, my, bx, py + 134, bw, 20)) {
                destroyTracker(selected);
                closeDrawer(false);
                return true;
            }
            if (contains(mx, my, bx, py + 158, bw, 20)) {
                closeDrawer(true);
                return true;
            }
            if (!contains(mx, my, px, py, pw, ph)) {
                closeDrawer(true);
            }
            return true;
        }

        // physical buttons
        if (contains(mx, my, UP_X, BTN_Y, BTN_W, BTN_H)) {
            pressedSlot = SLOT_UP;
            playUi(AlienSoundEvents.UI_TERMINAL_CLICK.get(), 0.5F, 1.15F);
            scroll(-1, false);
            return true;
        }
        if (contains(mx, my, DOWN_X, BTN_Y, BTN_W, BTN_H)) {
            pressedSlot = SLOT_DOWN;
            playUi(AlienSoundEvents.UI_TERMINAL_CLICK.get(), 0.5F, 1.05F);
            scroll(1, false);
            return true;
        }
        if (ClientTrackerAlerts.hasLost() && contains(mx, my, CAUTION_X, BTN_Y, BTN_W, BTN_H)) {
            pressedSlot = SLOT_CAUTION;
            lostDrawerOpen = true;
            playUi(AlienSoundEvents.UI_TERMINAL_CLICK.get(), 0.6F, 0.9F);
            lastHover = 0;
            return true;
        }

        if (!rows.isEmpty()) {
            int visible = visibleRows();
            for (int i = 0; i < visible && (i + scrollOffset) < rows.size(); i++) {
                int rowTop = LIST_TOP + i * ROW_H;
                if (isInRow(mx, my, rowTop)) {
                    selected = rows.get(i + scrollOffset);
                    drawerOpen = true;
                    playUi(AlienSoundEvents.UI_TERMINAL_CLICK.get(), 0.6F, 1.0F);
                    lastHover = 0;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        pressedSlot = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!drawerOpen && !lostDrawerOpen && scrollY != 0 && !rows.isEmpty()) {
            scroll(-(int) Math.signum(scrollY), true);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (renaming) {
            if (keyCode == 256) {
                renaming = false;
                playUi(AlienSoundEvents.UI_TERMINAL_CLOSE.get(), 0.6F, 1.2F);
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                confirmRename();
                return true;
            }
            if (keyCode == 259 && !renameBuffer.isEmpty()) {
                renameBuffer = renameBuffer.substring(0, renameBuffer.length() - 1);
                return true;
            }
            return true;
        }
        if (keyCode == 256) {
            if (lostDrawerOpen) {
                lostDrawerOpen = false;
                playUi(AlienSoundEvents.UI_TERMINAL_CLOSE.get(), 0.7F, 1.2F);
                return true;
            }
            if (drawerOpen) {
                closeDrawer(true);
                return true;
            }
            this.onClose();
            return true;
        }
        if (!drawerOpen && !lostDrawerOpen && (keyCode == 264 || keyCode == 265)) {
            scroll(keyCode == 264 ? 1 : -1, true);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ===== Actions =====
    private void createWaypoint(TrackedQueenRow row) {
        ClientWaypointStore.pin(new ClientWaypointStore.Waypoint(row.id(), row.name(), row.dimension(), row.pos()));
        playUi(AlienSoundEvents.UI_TERMINAL_EXECUTE.get(), 0.7F, 1.0F);
        feedback("Waypoint set: " + row.name() + " @ " + posText(row));
    }

    private void removeWaypoint(TrackedQueenRow row) {
        ClientWaypointStore.unpin(row.id());
        playUi(AlienSoundEvents.UI_TERMINAL_EXECUTE.get(), 0.7F, 0.85F);
        feedback("Waypoint removed: " + row.name());
    }

    private void destroyTracker(TrackedQueenRow row) {
        playUi(AlienSoundEvents.UI_TERMINAL_EXECUTE.get(), 0.7F, 0.75F);
        Alien.MOD.networking().sendToServer(C2SDestroyTrackerPayload.of(row.id()));
        ClientWaypointStore.unpin(row.id());
        rows.remove(row);
        feedback("Tracker destroyed: " + row.name());
    }

    private void shareWithChat(TrackedQueenRow row) {
        playUi(AlienSoundEvents.UI_TERMINAL_EXECUTE.get(), 0.7F, 1.0F);
        var mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return;
        }
        String message = "MU/TH/UR tracker: " + row.name() + " at " + posText(row)
            + " (" + shortDim(row.dimension().location().toString()) + ") [" + distanceText(row) + "]";
        mc.getConnection().sendChat(message);
    }

    private void acknowledgeLost() {
        playUi(AlienSoundEvents.UI_TERMINAL_EXECUTE.get(), 0.7F, 0.9F);
        Alien.MOD.networking().sendToServer(C2SAckLostTrackersPayload.INSTANCE);
        ClientTrackerAlerts.clear();
        lostDrawerOpen = false;
    }

    private void feedback(String text) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal(text), true);
        }
    }

    private void closeDrawer(boolean playSound) {
        drawerOpen = false;
        selected = null;
        lastHover = 0;
        if (playSound) {
            playUi(AlienSoundEvents.UI_TERMINAL_CLOSE.get(), 0.7F, 1.2F);
        }
    }

    private void scroll(int direction, boolean blip) {
        int before = scrollOffset;
        scrollOffset = clamp(scrollOffset + direction, 0, Math.max(0, rows.size() - visibleRows()));
        if (blip && scrollOffset != before) {
            playUi(AlienSoundEvents.UI_TERMINAL_MOUSEOVER.get(), 0.3F, 1.4F);
        }
    }

    // ===== Hover sound =====
    private void updateHoverSound(double mx, double my) {
        int target = hoverTarget(mx, my);
        if (target != 0 && target != lastHover) {
            playUi(AlienSoundEvents.UI_TERMINAL_MOUSEOVER.get(), 0.25F, 1.7F);
        }
        lastHover = target;
    }

    private int hoverTarget(double mx, double my) {
        if (renaming) {
            return 0;
        }
        if (lostDrawerOpen) {
            int pw = 244;
            int ph = 200;
            int px = SX + (SW - pw) / 2;
            int py = SY + (SH - ph) / 2;
            int bx = px + 14;
            int bw = pw - 28;
            if (contains(mx, my, bx, py + ph - 46, bw, 20)) {
                return 301;
            }
            if (contains(mx, my, bx, py + ph - 24, bw, 20)) {
                return 302;
            }
            return 0;
        }
        if (drawerOpen && selected != null) {
            int pw = 232;
            int ph = 200;
            int px = SX + (SW - pw) / 2;
            int py = SY + (SH - ph) / 2;
            int bx = px + 14;
            int bw = pw - 28;
            for (int i = 0; i < 6; i++) {
                if (contains(mx, my, bx, py + 38 + i * 24, bw, 20)) {
                    return 200 + i;
                }
            }
            return 0;
        }
        if (contains(mx, my, UP_X, BTN_Y, BTN_W, BTN_H)) {
            return SLOT_UP;
        }
        if (contains(mx, my, DOWN_X, BTN_Y, BTN_W, BTN_H)) {
            return SLOT_DOWN;
        }
        if (ClientTrackerAlerts.hasLost() && contains(mx, my, CAUTION_X, BTN_Y, BTN_W, BTN_H)) {
            return SLOT_CAUTION;
        }
        int visible = visibleRows();
        for (int i = 0; i < visible && (i + scrollOffset) < rows.size(); i++) {
            int rowTop = LIST_TOP + i * ROW_H;
            if (isInRow(mx, my, rowTop)) {
                return 1000 + i + scrollOffset;
            }
        }
        return 0;
    }

    // ===== Drawing helpers =====
    private void drawButton(GuiGraphics g, int x, int y, int w, int h, String text, boolean hovered, boolean enabled) {
        int fill = !enabled ? BTN_FILL_DISABLED : hovered ? BTN_FILL_HOVER : BTN_FILL;
        int edge = hovered && enabled ? BTN_BORDER_HOVER : BTN_BORDER;
        int textColor = !enabled ? BTN_TEXT_DISABLED : BTN_TEXT;
        g.fill(x, y, x + w, y + h, fill);
        drawBox(g, x, y, w, h, edge);
        g.drawCenteredString(this.font, Component.literal(text), x + w / 2, y + (h - 8) / 2, textColor);
    }

    private void drawBox(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.hLine(x, x + w, y, color);
        g.hLine(x, x + w, y + h, color);
        g.vLine(x, y, y + h, color);
        g.vLine(x + w, y, y + h, color);
    }

    private void drawRightString(GuiGraphics g, String text, int rightX, int y, int color) {
        g.drawString(this.font, Component.literal(text), rightX - this.font.width(text), y, color, false);
    }

    // ===== Utilities =====
    private int visibleRows() {
        return Math.max(1, (LIST_BOTTOM - LIST_TOP) / ROW_H);
    }

    private void clampScroll() {
        scrollOffset = clamp(scrollOffset, 0, Math.max(0, rows.size() - visibleRows()));
    }

    private boolean isInRow(double mx, double my, int rowTop) {
        return mx >= SX + 6 && mx <= SX + SW - 6 && my >= rowTop && my < rowTop + ROW_H;
    }

    private boolean contains(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private String selectedTitle() {
        int index = rows.indexOf(selected);
        String id = index >= 0 ? String.format(Locale.ROOT, "AQ-%02d  ", index + 1) : "";
        return (id + selected.name()).toUpperCase(Locale.ROOT);
    }

    private String selectedSubtitle() {
        return posText(selected) + "  (" + shortDim(selected.dimension().location().toString()) + ")  " + distanceText(selected);
    }

    private String posText(TrackedQueenRow row) {
        return row.pos().getX() + ", " + row.pos().getY() + ", " + row.pos().getZ();
    }

    private String distanceText(TrackedQueenRow row) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.level.dimension().equals(row.dimension())) {
            return "-- --";
        }
        double d = Math.sqrt(mc.player.distanceToSqr(Vec3.atCenterOf(row.pos())));
        if (d >= 1000.0) {
            return String.format(Locale.ROOT, "%.1f KM", d / 1000.0);
        }
        return String.format(Locale.ROOT, "%d M", Math.round(d));
    }

    private static String reasonText(String reason) {
        if ("EMPRESS".equals(reason)) {
            return "EMPRESS INTERFERENCE";
        }
        if ("DECEASED".equals(reason)) {
            return "SIGNAL LOST \u2014 DECEASED";
        }
        return reason;
    }

    private String dateStamp() {
        LocalDate now = LocalDate.now();
        return String.format(Locale.ROOT, "%02d/%02d/%d", now.getDayOfMonth(), now.getMonthValue(), now.getYear() + 159);
    }

    private String fit(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "\u2026";
        while (text.length() > 1 && this.font.width(text + ellipsis) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + ellipsis;
    }

    private static String shortDim(String dimension) {
        int idx = dimension.indexOf(':');
        return idx >= 0 ? dimension.substring(idx + 1) : dimension;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (renaming) {
            if (codePoint >= 32 && codePoint != 127 && renameBuffer.length() < 32) {
                renameBuffer += codePoint;
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void startRename() {
        renaming = true;
        renameBuffer = selected != null ? selected.name() : "";
        playUi(AlienSoundEvents.UI_TERMINAL_CLICK.get(), 0.6F, 1.0F);
    }

    private void confirmRename() {
        String name = renameBuffer.trim();
        renaming = false;
        if (selected == null || name.isEmpty()) {
            return;
        }
        playUi(AlienSoundEvents.UI_TERMINAL_EXECUTE.get(), 0.7F, 1.0F);
        Alien.MOD.networking().sendToServer(C2SRenameTrackerPayload.of(selected.id(), name));
        int idx = rows.indexOf(selected);
        if (idx >= 0) {
            var old = rows.get(idx);
            var updated = new TrackedQueenRow(old.id(), name, old.dimension(), old.pos(), old.lastSeenGameTime());
            rows.set(idx, updated);
            selected = updated;
        }
    }

    private void drawRenameOverlay(GuiGraphics g) {
        // Lift above the drawer so its fills AND text fully occlude what is behind (text batches otherwise bleed on
        // top).
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 300.0F);
        g.fill(SX, SY, SX + SW, SY + SH, 0xFF04100A);

        int pw = 240;
        int ph = 92;
        int px = SX + (SW - pw) / 2;
        int py = SY + (SH - ph) / 2;
        g.fill(px, py, px + pw, py + ph, PANEL_BG);
        drawBox(g, px, py, pw, ph, GREEN_BRIGHT);
        g.drawString(this.font, Component.literal("RENAME TRACKER"), px + 12, py + 10, GREEN_BRIGHT, false);

        int ibx = px + 12;
        int iby = py + 26;
        int ibw = pw - 24;
        int ibh = 18;
        g.fill(ibx, iby, ibx + ibw, iby + ibh, 0xFF06210A);
        drawBox(g, ibx, iby, ibw, ibh, GREEN);
        boolean caret = (System.currentTimeMillis() / 500L) % 2L == 0L;
        String shown = renameBuffer + (caret ? "_" : "");
        g.drawString(this.font, Component.literal(fit(shown, ibw - 8)), ibx + 4, iby + 5, GREEN_BRIGHT, false);

        g.drawString(this.font, Component.literal("[ENTER] SAVE   [ESC] CANCEL"), px + 12, py + ph - 16, GREEN_DIM, false);
        g.pose().popPose();
    }

    private void playUi(SoundEvent sound, float volume, float pitch) {
        var mc = Minecraft.getInstance();
        if (sound != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
        }
    }
}
