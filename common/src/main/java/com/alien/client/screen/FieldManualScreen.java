/*
 * Ported verbatim from the dropship_transport module's FieldManualScreen - same renderer, same JSON format, same
 * chrome. Only the namespace, the mod-id constant and the two hardcoded strings changed; the layout, the nine element
 * types, the scrolling and the recipe grid are untouched, so anything authored for that manual works here unmodified.
 * The panel draws itself with filled rects (22 fill() calls, and the single blit() only serves content images), so
 * porting it needed no GUI textures at all.
 */
package com.alien.client.screen;

import com.alien.Alien;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class FieldManualScreen extends Screen {

    private static final Gson GSON = new Gson();

    private static final ResourceLocation MANUAL_JSON =
        ResourceLocation.fromNamespaceAndPath(Alien.MOD_ID, "manual/field_manual.json");

    private static final int PANEL_WIDTH = 350;

    private static final int PANEL_HEIGHT = 210;

    private static final int BACKGROUND = 0xF0D8D2C2;

    private static final int BORDER = 0xFF2B2B2B;

    private static final int TITLE = 0xFF1B1B1B;

    private static final int HEADER = 0xFF167A55;

    private static final int TEXT = 0xFF303030;

    private static final int MUTED_TEXT = 0xFF5D5D5D;

    private static final int WY_GREEN = 0xFF167A55;

    private static final int IMAGE_BORDER = 0xFF565656;

    private static final int IMAGE_FILL = 0x40202020;

    private List<ManualSection> sections = fallbackSections();

    private int sectionIndex;

    private int pageIndex;

    private ItemStack hoveredItem = ItemStack.EMPTY;

    private int scrollAmount;

    private int contentHeight;

    private int maxScroll;

    public FieldManualScreen() {
        super(Component.translatable("screen.avp_alien.field_manual"));
    }

    @Override
    protected void init() {
        this.sections = loadManualSections();
        this.sectionIndex = clamp(this.sectionIndex, 0, this.sections.size() - 1);
        this.pageIndex = clamp(this.pageIndex, 0, currentSection().pages().size() - 1);
        rebuildManualWidgets();
    }

    private void rebuildManualWidgets() {
        clearWidgets();

        int x = (this.width - PANEL_WIDTH) / 2;
        int y = (this.height - PANEL_HEIGHT) / 2;
        int buttonY = y + PANEL_HEIGHT - 25;

        addRenderableWidget(
            Button.builder(Component.literal("<<"), button -> previousSection())
                .bounds(x + 8, buttonY, 28, 18)
                .build()
        );
        addRenderableWidget(
            Button.builder(Component.literal("<"), button -> previousPage())
                .bounds(x + 42, buttonY, 24, 18)
                .build()
        );
        addRenderableWidget(
            Button.builder(Component.literal(">"), button -> nextPage())
                .bounds(x + PANEL_WIDTH - 66, buttonY, 24, 18)
                .build()
        );
        addRenderableWidget(
            Button.builder(Component.literal(">>"), button -> nextSection())
                .bounds(x + PANEL_WIDTH - 36, buttonY, 28, 18)
                .build()
        );

        int indexX = x + 8;
        int indexY = y + 18;
        int indexWidth = 128;
        for (int i = 0; i < this.sections.size(); i++) {
            int target = i;
            ManualSection section = this.sections.get(i);
            addRenderableWidget(
                Button.builder(Component.literal(section.title()), button -> setSection(target))
                    .bounds(indexX, indexY + i * 18, indexWidth, 16)
                    .build()
            );
        }
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally no-op. The manual draws its own panel and should not
        // use Minecraft's default blurred screen background.
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = (this.width - PANEL_WIDTH) / 2;
        int y = (this.height - PANEL_HEIGHT) / 2;

        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, BACKGROUND);
        graphics.fill(x, y, x + PANEL_WIDTH, y + 2, BORDER);
        graphics.fill(x, y + PANEL_HEIGHT - 2, x + PANEL_WIDTH, y + PANEL_HEIGHT, BORDER);
        graphics.fill(x, y, x + 2, y + PANEL_HEIGHT, BORDER);
        graphics.fill(x + PANEL_WIDTH - 2, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, BORDER);
        graphics.fill(x + 146, y + 12, x + 148, y + PANEL_HEIGHT - 30, 0x66303030);

        ManualSection section = currentSection();
        ManualPage page = currentPage();

        graphics.drawString(this.font, Component.literal(page.title()), x + 160, y + 14, TITLE, false);

        int contentX = x + 160;
        int contentY = y + 30;
        int contentWidth = PANEL_WIDTH - 182;
        int maxY = y + PANEL_HEIGHT - 33;
        int viewportHeight = Math.max(1, maxY - contentY);
        this.hoveredItem = ItemStack.EMPTY;

        this.maxScroll = Math.max(0, this.contentHeight - viewportHeight);
        this.scrollAmount = clamp(this.scrollAmount, 0, this.maxScroll);

        boolean mouseInsideContent = mouseX >= contentX && mouseX < contentX + contentWidth && mouseY >= contentY && mouseY < maxY;
        int renderMouseX = mouseInsideContent ? mouseX : Integer.MIN_VALUE;
        int renderMouseY = mouseInsideContent ? mouseY : Integer.MIN_VALUE;
        graphics.enableScissor(contentX, contentY, contentX + contentWidth, maxY);
        int renderedEndY = drawPageElements(
            graphics,
            page.elements(),
            contentX,
            contentY - this.scrollAmount,
            contentWidth,
            contentY - this.scrollAmount + 10000,
            renderMouseX,
            renderMouseY
        );
        graphics.disableScissor();
        this.contentHeight = Math.max(0, renderedEndY - (contentY - this.scrollAmount));
        this.maxScroll = Math.max(0, this.contentHeight - viewportHeight);
        this.scrollAmount = clamp(this.scrollAmount, 0, this.maxScroll);
        drawContentScrollbar(graphics, contentX + contentWidth + 3, contentY, maxY, viewportHeight);

        String count = (pageIndex + 1) + " / " + section.pages().size();
        graphics.drawString(
            this.font,
            Component.literal(count),
            x + PANEL_WIDTH / 2 - this.font.width(count) / 2,
            y + PANEL_HEIGHT - 21,
            TEXT,
            false
        );

        super.render(graphics, mouseX, mouseY, partialTick);

        if (!this.hoveredItem.isEmpty()) {
            graphics.renderTooltip(this.font, this.hoveredItem, mouseX, mouseY);
        }
    }

    private void drawContentScrollbar(GuiGraphics graphics, int x, int y, int maxY, int viewportHeight) {
        if (this.maxScroll <= 0) {
            return;
        }

        int trackHeight = Math.max(1, maxY - y);
        graphics.fill(x, y, x + 3, maxY, 0x40303030);

        int thumbHeight = clamp((viewportHeight * trackHeight) / Math.max(viewportHeight, this.contentHeight), 12, trackHeight);
        int travel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = y + (int) ((this.scrollAmount / (double) Math.max(1, this.maxScroll)) * travel);
        graphics.fill(x, thumbY, x + 3, thumbY + thumbHeight, 0x66303030);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int x = (this.width - PANEL_WIDTH) / 2;
        int y = (this.height - PANEL_HEIGHT) / 2;
        int contentX = x + 160;
        int contentY = y + 30;
        int contentWidth = PANEL_WIDTH - 182;
        int maxY = y + PANEL_HEIGHT - 33;

        if (mouseX >= contentX && mouseX < contentX + contentWidth + 8 && mouseY >= contentY && mouseY < maxY && this.maxScroll > 0) {
            this.scrollAmount = clamp(this.scrollAmount - (int) Math.round(scrollY * 16.0D), 0, this.maxScroll);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int drawPageElements(
        GuiGraphics graphics,
        List<ManualElement> elements,
        int x,
        int y,
        int width,
        int maxY,
        int mouseX,
        int mouseY
    ) {
        int cursorY = y;
        for (ManualElement element : elements) {
            if (element == null || cursorY > maxY) {
                continue;
            }

            String type = element.type().toLowerCase(Locale.ROOT);
            switch (type) {
                case "header" -> {
                    cursorY = drawWrappedText(graphics, element.text(), x, cursorY, width, maxY, HEADER);
                    cursorY += element.spacingAfter();
                }
                case "bullet" -> {
                    graphics.drawString(this.font, Component.literal("•"), x, cursorY, TEXT, false);
                    cursorY = drawWrappedText(graphics, element.text(), x + 9, cursorY, width - 9, maxY, TEXT);
                    cursorY += element.spacingAfter();
                }
                case "image" -> {
                    int imageWidth = clamp(element.width(), 16, width);
                    int imageHeight = clamp(element.height(), 16, Math.max(16, maxY - cursorY));
                    int imageX = x + Math.max(0, (width - imageWidth) / 2);
                    if (cursorY + imageHeight > maxY) {
                        graphics.drawString(this.font, Component.literal("..."), x, cursorY, TEXT, false);
                        return maxY + 1;
                    }
                    drawManualImage(graphics, imageX, cursorY, imageWidth, imageHeight, element);
                    cursorY += imageHeight + element.spacingAfter();
                }
                case "cycle_image" -> {
                    int imageWidth = clamp(element.width(), 16, width);
                    int imageHeight = clamp(element.height(), 16, Math.max(16, maxY - cursorY));
                    int imageX = x + Math.max(0, (width - imageWidth) / 2);
                    if (cursorY + imageHeight > maxY) {
                        graphics.drawString(this.font, Component.literal("..."), x, cursorY, TEXT, false);
                        return maxY + 1;
                    }
                    drawCycleImage(graphics, imageX, cursorY, imageWidth, imageHeight, element);
                    cursorY += imageHeight + element.spacingAfter();
                }
                case "recipe" -> {
                    int recipeWidth = clamp(element.width(), 76, width);
                    int recipeHeight = Math.max(58, element.height());
                    int recipeX = x + Math.max(0, (width - recipeWidth) / 2);
                    if (cursorY + recipeHeight > maxY) {
                        graphics.drawString(this.font, Component.literal("..."), x, cursorY, TEXT, false);
                        return maxY + 1;
                    }
                    drawRecipe(graphics, element, recipeX, cursorY, recipeWidth, recipeHeight, mouseX, mouseY);
                    cursorY += recipeHeight + element.spacingAfter();
                }
                case "columns" -> {
                    int columnHeight = drawColumns(graphics, element, x, cursorY, width, maxY, mouseX, mouseY);
                    if (columnHeight < 0) {
                        graphics.drawString(this.font, Component.literal("..."), x, cursorY, TEXT, false);
                        return maxY + 1;
                    }
                    cursorY += columnHeight + element.spacingAfter();
                }
                case "row" -> {
                    int rowHeight = drawRow(graphics, element.children(), x, cursorY, width, maxY, element.gap(), mouseX, mouseY);
                    if (rowHeight < 0) {
                        graphics.drawString(this.font, Component.literal("..."), x, cursorY, TEXT, false);
                        return maxY + 1;
                    }
                    cursorY += rowHeight + element.spacingAfter();
                }
                case "space" -> cursorY += Math.max(0, element.height());
                default -> {
                    cursorY = drawWrappedText(graphics, element.text(), x, cursorY, width, maxY, TEXT);
                    cursorY += element.spacingAfter();
                }
            }
        }

        return cursorY;
    }

    private int drawColumns(GuiGraphics graphics, ManualElement element, int x, int y, int width, int maxY, int mouseX, int mouseY) {
        int gap = Math.max(0, element.gap());
        int leftWidth = element.leftWidth() > 0 ? element.leftWidth() : (width - gap) / 2;
        int rightWidth = element.rightWidth() > 0 ? element.rightWidth() : width - gap - leftWidth;
        leftWidth = clamp(leftWidth, 20, Math.max(20, width - gap - 20));
        rightWidth = clamp(rightWidth, 20, Math.max(20, width - gap - leftWidth));

        int totalWidth = leftWidth + gap + rightWidth;
        int startX = x + Math.max(0, (width - totalWidth) / 2);

        int leftEnd = drawPageElements(graphics, element.left(), startX, y, leftWidth, maxY, mouseX, mouseY);
        int rightEnd = drawPageElements(graphics, element.right(), startX + leftWidth + gap, y, rightWidth, maxY, mouseX, mouseY);
        int used = Math.max(leftEnd, rightEnd) - y;
        return used < 0 || y + used > maxY + 8 ? -1 : used;
    }

    private int drawRow(
        GuiGraphics graphics,
        List<ManualElement> children,
        int x,
        int y,
        int width,
        int maxY,
        int gap,
        int mouseX,
        int mouseY
    ) {
        if (children == null || children.isEmpty()) {
            return 0;
        }

        int safeGap = Math.max(0, gap);
        int totalWidth = Math.max(0, safeGap * (children.size() - 1));
        int rowHeight = 0;
        for (ManualElement child : children) {
            if (child == null) {
                continue;
            }
            String type = child.type().toLowerCase(Locale.ROOT);
            if ("text".equals(type) || "header".equals(type) || "bullet".equals(type)) {
                totalWidth += Math.max(24, Math.min(width, child.width()));
                rowHeight = Math.max(rowHeight, Math.max(18, child.height()));
            } else {
                totalWidth += Math.max(16, child.width());
                rowHeight = Math.max(rowHeight, Math.max(16, child.height()));
            }
        }

        if (y + rowHeight > maxY) {
            return -1;
        }

        int cursorX = x + Math.max(0, (width - totalWidth) / 2);
        for (ManualElement child : children) {
            if (child == null) {
                continue;
            }
            int childWidth = Math.max(16, Math.min(width, child.width()));
            int childHeight = Math.max(16, child.height());
            String type = child.type().toLowerCase(Locale.ROOT);
            if ("image".equals(type)) {
                drawManualImage(graphics, cursorX, y, childWidth, Math.min(rowHeight, childHeight), child);
            } else if ("cycle_image".equals(type)) {
                drawCycleImage(graphics, cursorX, y, childWidth, Math.min(rowHeight, childHeight), child);
            } else if ("recipe".equals(type)) {
                drawRecipe(graphics, child, cursorX, y, childWidth, Math.min(rowHeight, childHeight), mouseX, mouseY);
            } else if ("header".equals(type)) {
                drawWrappedText(graphics, child.text(), cursorX, y, childWidth, y + rowHeight, HEADER);
            } else if ("bullet".equals(type)) {
                graphics.drawString(this.font, Component.literal("•"), cursorX, y, TEXT, false);
                drawWrappedText(graphics, child.text(), cursorX + 9, y, childWidth - 9, y + rowHeight, TEXT);
            } else if ("text".equals(type)) {
                drawWrappedText(graphics, child.text(), cursorX, y, childWidth, y + rowHeight, TEXT);
            } else if ("space".equals(type)) {
                // Horizontal spacer inside a row.
            }
            cursorX += childWidth + safeGap;
        }
        return rowHeight;
    }

    private void drawCycleImage(GuiGraphics graphics, int x, int y, int width, int height, ManualElement element) {
        List<String> images = element.images();
        if (images == null || images.isEmpty()) {
            drawManualImage(graphics, x, y, width, height, element);
            return;
        }

        int ticks = Math.max(1, element.ticks());
        long frameClock = System.currentTimeMillis() / Math.max(1L, ticks * 50L);
        String image = images.get((int) (frameClock % images.size()));
        drawImage(graphics, x, y, width, height, image, element.showBorder());
    }

    private void drawRecipe(GuiGraphics graphics, ManualElement element, int x, int y, int width, int height, int mouseX, int mouseY) {
        int slotSize = 18;
        int gridX = x;
        int gridY = y + Math.max(0, (height - 54) / 2);
        List<String> items = element.items();

        for (int index = 0; index < 9; index++) {
            int slotX = gridX + (index % 3) * slotSize;
            int slotY = gridY + (index / 3) * slotSize;
            drawRecipeSlot(graphics, slotX, slotY);
            if (items != null && index < items.size()) {
                ItemStack stack = stackFromId(items.get(index));
                drawRecipeStack(graphics, stack, slotX + 1, slotY + 1, mouseX, mouseY);
            }
        }

        int arrowX = gridX + 61;
        int arrowY = gridY + 20;
        graphics.drawString(this.font, Component.literal("→"), arrowX, arrowY, MUTED_TEXT, false);

        int resultX = gridX + 82;
        int resultY = gridY + 18;
        drawRecipeSlot(graphics, resultX, resultY);
        ItemStack result = stackFromId(element.result());
        drawRecipeStack(graphics, result, resultX + 1, resultY + 1, mouseX, mouseY);
    }

    private void drawRecipeSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0x80303030);
        graphics.fill(x, y, x + 18, y + 1, IMAGE_BORDER);
        graphics.fill(x, y + 17, x + 18, y + 18, IMAGE_BORDER);
        graphics.fill(x, y, x + 1, y + 18, IMAGE_BORDER);
        graphics.fill(x + 17, y, x + 18, y + 18, IMAGE_BORDER);
    }

    private void drawRecipeStack(GuiGraphics graphics, ItemStack stack, int x, int y, int mouseX, int mouseY) {
        if (stack.isEmpty()) {
            return;
        }
        graphics.renderItem(stack, x, y);
        graphics.renderItemDecorations(this.font, stack, x, y);
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            this.hoveredItem = stack;
        }
    }

    private static ItemStack stackFromId(String id) {
        if (id == null || id.isBlank() || "minecraft:air".equals(id)) {
            return ItemStack.EMPTY;
        }
        ResourceLocation location = ResourceLocation.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(location)) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(location);
        return new ItemStack(item);
    }

    private void drawManualImage(GuiGraphics graphics, int x, int y, int width, int height, ManualElement element) {
        if (element.image() == null || element.image().isBlank()) {
            drawImagePlaceholder(graphics, x, y, width, height, element.label(), element.showBorder());
        } else {
            drawImage(graphics, x, y, width, height, element.image(), element.showBorder());
        }
    }

    private int drawWrappedText(GuiGraphics graphics, String text, int x, int y, int width, int maxY, int color) {
        if (text == null || text.isBlank()) {
            return y;
        }

        int textY = y;
        List<FormattedCharSequence> wrapped = this.font.split(Component.literal(text), width);
        for (FormattedCharSequence line : wrapped) {
            if (textY + 9 > maxY) {
                graphics.drawString(this.font, Component.literal("..."), x, textY, color, false);
                return maxY + 1;
            }
            graphics.drawString(this.font, line, x, textY, color, false);
            textY += 9;
        }
        return textY;
    }

    private void drawImage(GuiGraphics graphics, int x, int y, int width, int height, String image, boolean showBorder) {
        ResourceLocation texture = textureLocation(image);
        graphics.blit(texture, x, y, 0.0F, 0.0F, width, height, width, height);
        if (showBorder) {
            graphics.fill(x, y, x + width, y + 1, IMAGE_BORDER);
            graphics.fill(x, y + height - 1, x + width, y + height, IMAGE_BORDER);
            graphics.fill(x, y, x + 1, y + height, IMAGE_BORDER);
            graphics.fill(x + width - 1, y, x + width, y + height, IMAGE_BORDER);
        }
    }

    private void drawImagePlaceholder(GuiGraphics graphics, int x, int y, int width, int height, String label, boolean showBorder) {
        graphics.fill(x, y, x + width, y + height, IMAGE_FILL);
        if (showBorder) {
            graphics.fill(x, y, x + width, y + 1, IMAGE_BORDER);
            graphics.fill(x, y + height - 1, x + width, y + height, IMAGE_BORDER);
            graphics.fill(x, y, x + 1, y + height, IMAGE_BORDER);
            graphics.fill(x + width - 1, y, x + width, y + height, IMAGE_BORDER);
        }
        List<FormattedCharSequence> lines = this.font.split(
            Component.literal(label == null || label.isBlank() ? "Image" : label),
            width - 6
        );
        int textY = y + Math.max(4, (height - lines.size() * 9) / 2);
        for (FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, x + 3, textY, MUTED_TEXT, false);
            textY += 9;
        }
    }

    private ManualSection currentSection() {
        return this.sections.get(sectionIndex);
    }

    private ManualPage currentPage() {
        return currentSection().pages().get(pageIndex);
    }

    private void previousSection() {
        setSection(sectionIndex <= 0 ? this.sections.size() - 1 : sectionIndex - 1);
    }

    private void nextSection() {
        setSection((sectionIndex + 1) % this.sections.size());
    }

    private void previousPage() {
        ManualSection section = currentSection();
        setPage(pageIndex <= 0 ? section.pages().size() - 1 : pageIndex - 1);
    }

    private void nextPage() {
        ManualSection section = currentSection();
        setPage((pageIndex + 1) % section.pages().size());
    }

    private void setSection(int sectionIndex) {
        this.sectionIndex = clamp(sectionIndex, 0, this.sections.size() - 1);
        this.pageIndex = 0;
        this.scrollAmount = 0;
        this.contentHeight = 0;
        this.maxScroll = 0;
        rebuildManualWidgets();
    }

    private void setPage(int pageIndex) {
        ManualSection section = currentSection();
        this.pageIndex = clamp(pageIndex, 0, section.pages().size() - 1);
        this.scrollAmount = 0;
        this.contentHeight = 0;
        this.maxScroll = 0;
    }

    /**
     * Whether a {@code required_mod} gate is satisfied.
     * <p>
     * Deliberately GENERIC rather than a hardcoded avp_human check: the id is looked up through BLib, so any section or
     * page can be gated on any mod ("avp_predator", a future module) by adding one JSON field and no code. Absent,
     * empty or blank means ungated, so every existing section keeps working untouched.
     * <p>
     * A section whose gate fails is dropped BEFORE the index rail is built, so it leaves no empty entry behind. If a
     * gated section's every page is individually gated away, the section is dropped too - see the empty-pages check at
     * the end of the section loop.
     */
    private static boolean isModPresent(String modId) {
        if (modId == null || modId.isBlank()) {
            return true;
        }

        try {
            return com.blib.api.BLibAPI.createMod(modId.trim()).isLoaded();
        } catch (Exception ignored) {
            // An unresolvable id hides the content rather than crashing the screen - same defensive posture as the
            // broken-JSON fallback below.
            return false;
        }
    }

    private static List<ManualSection> loadManualSections() {
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(MANUAL_JSON);
            if (resource.isPresent()) {
                try (Reader reader = resource.get().openAsReader()) {
                    ManualData data = GSON.fromJson(reader, ManualData.class);
                    List<ManualSection> parsed = normalize(data);
                    if (!parsed.isEmpty()) {
                        return parsed;
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall through to the built-in manual so broken JSON cannot crash the client.
        }
        return fallbackSections();
    }

    private static List<ManualSection> normalize(ManualData data) {
        List<ManualSection> result = new ArrayList<>();
        if (data == null || data.sections == null) {
            return result;
        }

        for (ManualSectionData sectionData : data.sections) {
            if (sectionData == null || sectionData.title == null || sectionData.title.isBlank()) {
                continue;
            }

            // Content about another module's gear is hidden outright when that module is absent, rather than shown
            // and disclaimed - a reader with no avp_human installed should never see a tracker page at all.
            if (!isModPresent(sectionData.required_mod)) {
                continue;
            }

            List<ManualPage> pages = new ArrayList<>();
            if (sectionData.pages != null) {
                for (ManualPageData pageData : sectionData.pages) {
                    if (pageData == null) {
                        continue;
                    }

                    if (!isModPresent(pageData.required_mod)) {
                        continue;
                    }

                    String title = pageData.title == null || pageData.title.isBlank() ? sectionData.title : pageData.title;
                    List<ManualElement> elements = new ArrayList<>();
                    if (pageData.elements != null) {
                        for (ManualElementData elementData : pageData.elements) {
                            if (elementData != null) {
                                elements.add(normalizeElement(elementData));
                            }
                        }
                    }

                    if (elements.isEmpty()) {
                        elements.add(
                            new ManualElement(
                                "text",
                                "Manual page pending content.",
                                "",
                                "Image",
                                112,
                                56,
                                6,
                                6,
                                30,
                                "",
                                List.of(),
                                List.of(),
                                List.of()
                            )
                        );
                    }
                    pages.add(new ManualPage(title, elements));
                }
            }

            if (pages.isEmpty()) {
                pages.add(
                    new ManualPage(
                        sectionData.title,
                        List.of(
                            new ManualElement(
                                "text",
                                "Manual section pending content.",
                                "",
                                "Image",
                                112,
                                56,
                                6,
                                6,
                                30,
                                "",
                                List.of(),
                                List.of(),
                                List.of()
                            )
                        )
                    )
                );
            }
            result.add(new ManualSection(sectionData.title, pages));
        }
        return result;
    }

    private static List<ManualElement> normalizeElementList(List<ManualElementData> source) {
        List<ManualElement> elements = new ArrayList<>();
        if (source != null) {
            for (ManualElementData child : source) {
                if (child != null) {
                    elements.add(normalizeElement(child));
                }
            }
        }
        return List.copyOf(elements);
    }

    private static ManualElement normalizeElement(ManualElementData elementData) {
        List<ManualElement> children = normalizeElementList(elementData.children);
        List<ManualElement> left = normalizeElementList(elementData.left);
        List<ManualElement> right = normalizeElementList(elementData.right);

        return new ManualElement(
            valueOr(elementData.type, "text"),
            valueOr(elementData.text, ""),
            valueOr(elementData.image, ""),
            valueOr(elementData.label, "Image"),
            elementData.width <= 0 ? 112 : elementData.width,
            elementData.height <= 0 ? 56 : elementData.height,
            Math.max(0, elementData.spacing_after),
            Math.max(0, elementData.gap <= 0 ? 6 : elementData.gap),
            Math.max(1, elementData.ticks <= 0 ? 30 : elementData.ticks),
            valueOr(elementData.result, ""),
            elementData.images == null ? List.of() : List.copyOf(elementData.images),
            elementData.items == null ? List.of() : List.copyOf(elementData.items),
            children,
            Math.max(0, elementData.left_width),
            Math.max(0, elementData.right_width),
            left,
            right,
            elementData.show_border == null || elementData.show_border
        );
    }

    private static List<ManualSection> fallbackSections() {
        return List.of(
            new ManualSection(
                "Introduction",
                List.of(
                    new ManualPage(
                        "Weyland-Yutani Field Manual",
                        List.of(
                            new ManualElement(
                                "image",
                                "",
                                "",
                                "Cover / issue stamp",
                                112,
                                56,
                                6,
                                6,
                                30,
                                "",
                                List.of(),
                                List.of(),
                                List.of()
                            ),
                            new ManualElement(
                                "text",
                                "ICC Navigation Division issue.",
                                "",
                                "",
                                112,
                                56,
                                5,
                                6,
                                30,
                                "",
                                List.of(),
                                List.of(),
                                List.of()
                            ),
                            new ManualElement(
                                "text",
                                "Manual content failed to load. Report this to the Survey.",
                                "",
                                "",
                                112,
                                56,
                                5,
                                6,
                                30,
                                "",
                                List.of(),
                                List.of(),
                                List.of()
                            )
                        )
                    ),
                    new ManualPage(
                        "Using This Manual",
                        List.of(
                            new ManualElement(
                                "text",
                                "Use the section buttons on the left to jump between major topics.",
                                "",
                                "",
                                112,
                                56,
                                5,
                                6,
                                30,
                                "",
                                List.of(),
                                List.of(),
                                List.of()
                            ),
                            new ManualElement(
                                "text",
                                "Use single arrows to move through pages inside the current section. Use double arrows to move between sections.",
                                "",
                                "",
                                112,
                                56,
                                5,
                                6,
                                30,
                                "",
                                List.of(),
                                List.of(),
                                List.of()
                            )
                        )
                    )
                )
            ),
            new ManualSection(
                "Where To Start",
                List.of(
                    new ManualPage(
                        "First Steps",
                        List.of(
                            new ManualElement(
                                "text",
                                "Start by finding or building a Navigation Station and locating a Navigation Officer.",
                                "",
                                "",
                                112,
                                56,
                                5,
                                6,
                                30,
                                "",
                                List.of(),
                                List.of(),
                                List.of()
                            )
                        )
                    ),
                    new ManualPage(
                        "Landing Pad Multiblock",
                        List.of(
                            new ManualElement(
                                "text",
                                "The Launch Pad is a large 23 x 23 landing platform. Build the frame from approved AVP Human industrial materials.",
                                "",
                                "",
                                112,
                                56,
                                5,
                                6,
                                30,
                                "",
                                List.of(),
                                List.of(),
                                List.of()
                            )
                        )
                    )
                )
            ),
            new ManualSection(
                "Machines",
                List.of(
                    new ManualPage(
                        "Launch Terminal",
                        List.of(
                            new ManualElement(
                                "text",
                                "The Launch Terminal opens the MU/TH/UR NavCom interface.",
                                "",
                                "",
                                112,
                                56,
                                5,
                                6,
                                30,
                                "",
                                List.of(),
                                List.of(),
                                List.of()
                            )
                        )
                    ),
                    new ManualPage(
                        "Navigation Station",
                        List.of(
                            new ManualElement(
                                "text",
                                "The Navigation Station copies surveys and posters.",
                                "",
                                "",
                                112,
                                56,
                                5,
                                6,
                                30,
                                "",
                                List.of(),
                                List.of(),
                                List.of()
                            )
                        )
                    ),
                    new ManualPage(
                        "Oxygenator",
                        List.of(
                            new ManualElement(
                                "text",
                                "The Oxygenator creates a breathable safe zone when powered.",
                                "",
                                "",
                                112,
                                56,
                                5,
                                6,
                                30,
                                "",
                                List.of(),
                                List.of(),
                                List.of()
                            )
                        )
                    )
                )
            ),
            new ManualSection(
                "Items Of Note",
                List.of(
                    new ManualPage(
                        "Surveys And Posters",
                        List.of(
                            new ManualElement(
                                "text",
                                "System surveys unlock star systems. Cosmetic posters are decoration only.",
                                "",
                                "",
                                112,
                                56,
                                5,
                                6,
                                30,
                                "",
                                List.of(),
                                List.of(),
                                List.of()
                            )
                        )
                    ),
                    new ManualPage(
                        "Eitr And Trimonite",
                        List.of(
                            new ManualElement(
                                "text",
                                "Eitr can be used as high-value travel fuel. Trimonite geodes grow shards over time.",
                                "",
                                "",
                                112,
                                56,
                                5,
                                6,
                                30,
                                "",
                                List.of(),
                                List.of(),
                                List.of()
                            )
                        )
                    )
                )
            ),
            new ManualSection(
                "You're Not Alone",
                List.of(
                    new ManualPage(
                        "Known Hazards",
                        List.of(
                            new ManualElement(
                                "text",
                                "Remote worlds may contain hostile lifeforms, damaged stations, or wreckage sites.",
                                "",
                                "",
                                112,
                                56,
                                5,
                                6,
                                30,
                                "",
                                List.of(),
                                List.of(),
                                List.of()
                            )
                        )
                    ),
                    new ManualPage(
                        "Colonists And Officers",
                        List.of(
                            new ManualElement(
                                "text",
                                "Colonists can take professions from nearby workstations.",
                                "",
                                "",
                                112,
                                56,
                                5,
                                6,
                                30,
                                "",
                                List.of(),
                                List.of(),
                                List.of()
                            )
                        )
                    )
                )
            )
        );
    }

    private static ResourceLocation textureLocation(String image) {
        ResourceLocation parsed = image.contains(":")
            ? ResourceLocation.parse(image)
            : ResourceLocation.fromNamespaceAndPath(Alien.MOD_ID, image);
        String path = parsed.getPath();
        if (!path.startsWith("textures/")) {
            path = "textures/" + path;
        }
        if (!path.endsWith(".png")) {
            path = path + ".png";
        }
        return ResourceLocation.fromNamespaceAndPath(parsed.getNamespace(), path);
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static String valueOr(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private record ManualSection(
        String title,
        List<ManualPage> pages
    ) {}

    private record ManualPage(
        String title,
        List<ManualElement> elements
    ) {}

    private static class ManualElement {

        private final String type;

        private final String text;

        private final String image;

        private final String label;

        private final int width;

        private final int height;

        private final int spacingAfter;

        private final int gap;

        private final int ticks;

        private final String result;

        private final List<String> images;

        private final List<String> items;

        private final List<ManualElement> children;

        private final int leftWidth;

        private final int rightWidth;

        private final List<ManualElement> left;

        private final List<ManualElement> right;

        private final boolean showBorder;

        private ManualElement(
            String type,
            String text,
            String image,
            String label,
            int width,
            int height,
            int spacingAfter,
            int gap,
            int ticks,
            String result,
            List<String> images,
            List<String> items,
            List<ManualElement> children
        ) {
            this(
                type,
                text,
                image,
                label,
                width,
                height,
                spacingAfter,
                gap,
                ticks,
                result,
                images,
                items,
                children,
                0,
                0,
                List.of(),
                List.of(),
                true
            );
        }

        private ManualElement(
            String type,
            String text,
            String image,
            String label,
            int width,
            int height,
            int spacingAfter,
            int gap,
            int ticks,
            String result,
            List<String> images,
            List<String> items,
            List<ManualElement> children,
            int leftWidth,
            int rightWidth,
            List<ManualElement> left,
            List<ManualElement> right,
            boolean showBorder
        ) {
            this.type = type;
            this.text = text;
            this.image = image;
            this.label = label;
            this.width = width;
            this.height = height;
            this.spacingAfter = spacingAfter;
            this.gap = gap;
            this.ticks = ticks;
            this.result = result;
            this.images = images == null ? List.of() : images;
            this.items = items == null ? List.of() : items;
            this.children = children == null ? List.of() : children;
            this.leftWidth = leftWidth;
            this.rightWidth = rightWidth;
            this.left = left == null ? List.of() : left;
            this.right = right == null ? List.of() : right;
            this.showBorder = showBorder;
        }

        private String type() {
            return type;
        }

        private String text() {
            return text;
        }

        private String image() {
            return image;
        }

        private String label() {
            return label;
        }

        private int width() {
            return width;
        }

        private int height() {
            return height;
        }

        private int spacingAfter() {
            return spacingAfter;
        }

        private int gap() {
            return gap;
        }

        private int ticks() {
            return ticks;
        }

        private String result() {
            return result;
        }

        private List<String> images() {
            return images;
        }

        private List<String> items() {
            return items;
        }

        private List<ManualElement> children() {
            return children;
        }

        private int leftWidth() {
            return leftWidth;
        }

        private int rightWidth() {
            return rightWidth;
        }

        private List<ManualElement> left() {
            return left;
        }

        private List<ManualElement> right() {
            return right;
        }

        private boolean showBorder() {
            return showBorder;
        }
    }

    private static class ManualData {

        List<ManualSectionData> sections;
    }

    private static class ManualSectionData {

        String title;

        /** Optional mod id gate - see {@link FieldManualScreen#isModPresent}. */
        String required_mod;

        List<ManualPageData> pages;
    }

    private static class ManualPageData {

        String title;

        /** Optional mod id gate, same rule as the section's. */
        String required_mod;

        List<ManualElementData> elements;
    }

    private static class ManualElementData {

        String type;

        String text;

        String image;

        String label;

        int width;

        int height;

        int spacing_after = 5;

        int gap = 6;

        int ticks = 30;

        String result;

        int left_width;

        int right_width;

        Boolean show_border;

        List<String> images;

        List<String> items;

        List<ManualElementData> children;

        List<ManualElementData> left;

        List<ManualElementData> right;
    }
}
