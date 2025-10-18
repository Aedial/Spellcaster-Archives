package com.spellarchives.gui;

import com.spellarchives.Log;
import com.spellarchives.container.ContainerSpellArchive;
import com.spellarchives.gui.GuiStyle;
import com.spellarchives.network.MessageExtractBook;
import com.spellarchives.network.NetworkHandler;
import com.spellarchives.tile.TileSpellArchive;

import electroblob.wizardry.Wizardry;
import electroblob.wizardry.constants.Element;
import electroblob.wizardry.data.SpellGlyphData;
import electroblob.wizardry.data.WizardData;
import electroblob.wizardry.spell.Spell;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.Style;

import java.io.IOException;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

import javax.annotation.Resource;


public class GuiSpellArchive extends GuiContainer {
    // Initial defaults; will be overridden by dynamic sizing
    private static final int GUI_WIDTH = GuiStyle.DEFAULT_GUI_WIDTH;
    private static final int GUI_HEIGHT = GuiStyle.DEFAULT_GUI_HEIGHT;

    private final TileSpellArchive tile;
    private final EntityPlayer player;

    private int page = 0;

    private GuiButton prevButton;
    private GuiButton nextButton;

    // Cached layout pieces, recalculated each frame
    private int leftPanelX, leftPanelY, leftPanelW, leftPanelH;
    private int rightPanelX, rightPanelY, rightPanelW, rightPanelH;
    private int gridCols, gridRows, cellW = GuiStyle.CELL_W, cellH = GuiStyle.CELL_H, rowGap = GuiStyle.ROW_GAP;
    
    // Helper value objects for layout computations
    private static class GridGeometry {
        final int gridX, gridY, gridW, gridH, headerH;

        GridGeometry(int gridX, int gridY, int gridW, int gridH, int headerH) {
            this.gridX = gridX;
            this.gridY = gridY;
            this.gridW = gridW;
            this.gridH = gridH;
            this.headerH = headerH;
        }
    }

    private static class GrooveRow {
        int rowIndex; // index into displayRows
        int tier;
        int baseY; // y position of groove top
        boolean showHeader;

        GrooveRow(int rowIndex, int tier, int baseY, boolean showHeader) {
            this.rowIndex = rowIndex;
            this.tier = tier;
            this.baseY = baseY;
            this.showHeader = showHeader;
        }   
    }

    private static class DisplayRows {
        final List<List<BookEntry>> rows;      // wrapped rows of books
        final List<Integer> rowTiers;          // tier per row, aligned with rows

        DisplayRows(List<List<BookEntry>> rows, List<Integer> rowTiers) {
            this.rows = rows;
            this.rowTiers = rowTiers;
        }
    }

    private static class PageInfo {
        final List<GrooveRow> layout;  // groove rows to render this page
        final boolean hasNext;         // whether more rows exist beyond this page

        PageInfo(List<GrooveRow> layout, boolean hasNext) {
            this.layout = layout;
            this.hasNext = hasNext;
        }
    }

    private static class BookEntry {
        final ItemStack stack;
        final int count;
        final int tier;     // numeric sort key for tier
        final int element;  // numeric sort key for element
        final int rarityColor;
        final int elementColor;

        BookEntry(ItemStack stack, int count, int tier, int element, int rarityColor, int elementColor) {
            this.stack = stack;
            this.count = count;
            this.tier = tier;
            this.element = element;
            this.rarityColor = rarityColor;
            this.elementColor = elementColor;
        }
    }

    private List<BookEntry> entries = new ArrayList<>();
    private Map<Integer, List<BookEntry>> rowsByTier = new LinkedHashMap<>();
    private List<Integer> tierOrder = new ArrayList<>();
    private BookEntry hoveredEntryCached = null;
    private int lastChangeRev = -1;

    // Cache for generated spine textures: key -> resource location
    private final Map<String, ResourceLocation> spineTextureCache = new LinkedHashMap<>();

    // Left panel caches (avoid recalculations unless keys/layout/page change)
    private Set<String> lastKeys = new HashSet<>();
    private DisplayRows cachedDisplayRows = null;
    private PageInfo cachedPageInfo = null;
    private GridGeometry cachedGG = null;
    private int cachedGridColsForDisplay = -1;
    private int cachedPageForLayout = -1;

    // Right panel caches (avoid recomputation unless hovered/count change)
    private String lastHoveredKey = null;
    private int lastHoveredCount = -1;
    private SpellPresentation cachedPresentation = null;

    public GuiSpellArchive(ContainerSpellArchive container, TileSpellArchive tile, EntityPlayer player) {
        super(container);
        this.tile = tile;
        this.player = player;
        this.xSize = GUI_WIDTH;
        this.ySize = GUI_HEIGHT;
    }

    @Override
    public void initGui() {
        // Dynamic sizing: 50% width, 75% height of the screen
        this.xSize = Math.max(GuiStyle.MIN_WIDTH, (int) (this.width * GuiStyle.WIDTH_RATIO));
        this.ySize = Math.max(GuiStyle.MIN_HEIGHT, (int) (this.height * GuiStyle.HEIGHT_RATIO));

        super.initGui();

        // Buttons with vanilla background
        this.buttonList.clear();
        this.prevButton = new GuiButton(1, 0, 0, 20, 20, "<");
        this.nextButton = new GuiButton(2, 0, 0, 20, 20, ">");
        this.buttonList.add(prevButton);
        this.buttonList.add(nextButton);

        rebuildEntries();
    }

    /**
     * Rebuilds the list of book entries from the tile snapshot.
     */
    private void rebuildEntries() {
        entries.clear();
        rowsByTier.clear();
        tierOrder.clear();

        for (Map.Entry<String, Integer> e : tile.getSnapshot().entrySet()) {
            ItemStack stack = tile.stackFromKeyPublic(e.getKey());
            if (stack.isEmpty()) continue;

            entries.add(new BookEntry(stack, e.getValue(), tile.getTierOf(stack), tile.getElementOf(stack), tile.getRarityColor(stack), tile.getElementColor(stack)));
        }

        // Group by tier (rarity). Keep a stable order: tier ascending (Novice->Master)
        Map<Integer, List<BookEntry>> grouped = entries.stream().collect(
            Collectors.groupingBy(b -> b.tier, LinkedHashMap::new, Collectors.toList())
        );

        grouped.keySet().stream().sorted().forEach(tier -> {
            List<BookEntry> list = grouped.get(tier);
            list.sort(Comparator.comparingInt((BookEntry b) -> b.element));
            rowsByTier.put(tier, list);
            tierOrder.add(tier);
        });
    }

    /**
     * Draws the background layer of the GUI container.
     * @param partialTicks The partial ticks for animation
     * @param mouseX The current mouse x position
     * @param mouseY The current mouse y position
     */
    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1f, 1f, 1f, 1f);

        // Keep entries up to date while GUI is open
        int rev = tile.getChangeCounterPublic();
        boolean tileChanged = (rev != lastChangeRev);

        // 1) Layout metrics and backgrounds
        computePanels();
        renderBackgroundPanels();
        GridGeometry gg = computeGridGeometry();

        // 2) Data rows and page layout (only recompute if keys/layout/page changed)
        boolean layoutChanged = (cachedGG == null) || gg.gridX != cachedGG.gridX || gg.gridY != cachedGG.gridY || gg.gridW != cachedGG.gridW || gg.gridH != cachedGG.gridH || gg.headerH != cachedGG.headerH;
        boolean colsChanged = (cachedGridColsForDisplay != gridCols);

        boolean keysChanged = false;
        if (tileChanged) {
            Set<String> currentKeys = getSnapshotKeys();
            keysChanged = !currentKeys.equals(lastKeys);
            if (keysChanged) {
                rebuildEntries();
                lastKeys = currentKeys;
            }
            lastChangeRev = rev;
        }

        if (cachedDisplayRows == null || keysChanged || colsChanged) {
            cachedDisplayRows = buildDisplayRows();
            cachedGridColsForDisplay = gridCols;
            cachedPageInfo = null; // dependent
        }

        if (cachedPageInfo == null || layoutChanged || keysChanged || colsChanged || cachedPageForLayout != page) {
            cachedPageInfo = buildPageInfo(cachedDisplayRows, gg);
            cachedPageForLayout = page;
            cachedGG = gg;
        }

        // 3) Pagination widgets
        placePaginationButtons(cachedPageInfo.hasNext);

        // 4) Render page and details
        BookEntry hovered = renderPage(cachedDisplayRows, cachedPageInfo, gg, mouseX, mouseY);
        hoveredEntryCached = hovered;
        renderRightPanel(hovered);
    }

    private Set<String> getSnapshotKeys() {
        Map<String, Integer> snap = tile.getSnapshot();
        return new HashSet<>(snap.keySet());
    }

    private void computePanels() {
        int margin = GuiStyle.MARGIN;
        int totalX = guiLeft;
        int totalY = guiTop;
        int totalW = xSize;
        int totalH = ySize;

        // Right panel width ~ 38% of total, min 120
        rightPanelW = Math.max(GuiStyle.RIGHT_PANEL_MIN_WIDTH, (int) (totalW * GuiStyle.RIGHT_PANEL_RATIO));
        rightPanelH = totalH - margin * 2;
        rightPanelX = totalX + totalW - rightPanelW - margin;
        rightPanelY = totalY + margin;

        leftPanelX = totalX + margin;
        leftPanelY = totalY + margin;
        leftPanelW = rightPanelX - leftPanelX - margin;
        leftPanelH = totalH - margin * 2;
    }

    private void renderBackgroundPanels() {
        int totalX = guiLeft;
        int totalY = guiTop;
        int totalW = xSize;
        int totalH = ySize;

        drawRoundedPanel(totalX, totalY, totalW, totalH, GuiStyle.PANEL_RADIUS, GuiStyle.BACKGROUND_FILL, GuiStyle.BACKGROUND_BORDER);
        drawRoundedPanel(rightPanelX, rightPanelY, rightPanelW, rightPanelH, GuiStyle.RIGHT_PANEL_RADIUS, GuiStyle.RIGHT_PANEL_FILL, GuiStyle.RIGHT_PANEL_BORDER);
    }

    /**
     * Computes the grid geometry for the left panel content area.
     * @return The GridGeometry containing grid position and size
     */
    private GridGeometry computeGridGeometry() {
        int bottomBar = GuiStyle.BOTTOM_BAR_HEIGHT;
        int gridX = leftPanelX + GuiStyle.GRID_INNER_PADDING;
        int gridY = leftPanelY + 8;
        int gridW = leftPanelW - GuiStyle.GRID_INNER_PADDING * 2;
        int gridH = leftPanelH - bottomBar - GuiStyle.LEFT_PANEL_BOTTOM_PAD;

        gridCols = Math.max(1, gridW / (cellW));
        int headerH = this.fontRenderer.FONT_HEIGHT + GuiStyle.HEADER_EXTRA;
        gridRows = Math.max(1, gridH / (cellH + rowGap + headerH));

        return new GridGeometry(gridX, gridY, gridW, gridH, headerH);
    }

    /**
     * Builds the display rows by wrapping the book entries into rows based on gridCols.
     * @return The DisplayRows containing wrapped book entries and their tiers
     */
    private DisplayRows buildDisplayRows() {
        List<List<BookEntry>> displayRows = new ArrayList<>();
        List<Integer> displayRowTiers = new ArrayList<>();

        for (int t = 0; t < tierOrder.size(); t++) {
            int tier = tierOrder.get(t);
            List<BookEntry> rowBooks = rowsByTier.get(tier);
            if (rowBooks == null || rowBooks.isEmpty()) continue;

            for (int off = 0; off < rowBooks.size(); off += gridCols) {
                int endIdx = Math.min(off + gridCols, rowBooks.size());
                displayRows.add(rowBooks.subList(off, endIdx));
                displayRowTiers.add(tier);
            }
        }

        return new DisplayRows(displayRows, displayRowTiers);
    }

    /**
     * Builds the layout for the current page of the display rows.
     * @param dr The display rows containing wrapped book entries
     * @param gg The grid geometry for positioning
     * @return The PageInfo containing layout details for the current page
     */
    private PageInfo buildPageInfo(DisplayRows dr, GridGeometry gg) {
        List<GrooveRow> pageLayout = new ArrayList<>();

        int totalRows = dr.rows.size();
        int gridBottom = gg.gridY + gg.gridH;
        int curY = gg.gridY + gg.headerH;
        int startRow = page * Math.max(1, gridRows);

        Set<Integer> seenTiers = new HashSet<>();

        for (int r = startRow; r < totalRows; r++) {
            int tier = dr.rowTiers.get(r);
            boolean showHeader = !seenTiers.contains(tier);

            if (curY + cellH > gridBottom) break;

            pageLayout.add(new GrooveRow(r, tier, curY, showHeader));
            seenTiers.add(tier);

            if (r + 1 < totalRows) {
                int nextTier = dr.rowTiers.get(r + 1);
                boolean nextIsFirstOfTier = !seenTiers.contains(nextTier);
                if (nextIsFirstOfTier) {
                    curY = curY + cellH + gg.headerH + rowGap;
                } else {
                    curY = curY + cellH + 1;
                }
            }
        }

        boolean anyHasNext = (startRow + pageLayout.size()) < totalRows;

        return new PageInfo(pageLayout, anyHasNext);
    }

    /**
     * Places and updates the visibility of the pagination buttons.
     * @param anyHasNext Whether there are more pages after the current one
     */
    private void placePaginationButtons(boolean anyHasNext) {
        int arrowsY = leftPanelY + leftPanelH - GuiStyle.BOTTOM_BAR_HEIGHT / 2 - GuiStyle.ARROWS_Y_OFFSET;
        int prevX = leftPanelX + GuiStyle.GRID_INNER_PADDING;
        int nextX = leftPanelX + leftPanelW - GuiStyle.GRID_INNER_PADDING - GuiStyle.NAV_BUTTON_SIZE;

        if (prevButton != null && nextButton != null) {
            prevButton.x = prevX;
            prevButton.y = arrowsY;

            nextButton.x = nextX;
            nextButton.y = arrowsY;

            prevButton.visible = page > 0;
            nextButton.visible = anyHasNext;
        }
    }

    /**
     * Renders the current page of spell book entries.
     * @param dr The display rows containing wrapped book entries
     * @param pi The page info with layout details
     * @param gg The grid geometry for positioning
     * @param mouseX The current mouse x position
     * @param mouseY The current mouse y position
     * @return The BookEntry currently hovered over, or null if none
     */
    private BookEntry renderPage(DisplayRows dr, PageInfo pi, GridGeometry gg, int mouseX, int mouseY) {
        BookEntry hoveredEntry = null;

        for (GrooveRow gr : pi.layout) {
            int idx = gr.rowIndex;
            int baseY = gr.baseY;
            List<BookEntry> slice = dr.rows.get(idx);

            // Groove background; shrink width to just cover the books in this row
            int grooveW = Math.min(gg.gridW, 1 + gridCols * cellW);
            drawRowGroove(gg.gridX, baseY, grooveW, cellH);

            // Tier header
            if (gr.showHeader) {
                String tierText;
                int rarityRGB = 0x777777;

                if (!slice.isEmpty()) {
                    Spell rep = tile.getSpellPublic(slice.get(0).stack);
                    tierText = rep != null ? rep.getTier().getDisplayNameWithFormatting() : ("Tier " + gr.tier);
                    rarityRGB = slice.get(0).rarityColor;
                } else {
                    tierText = ("Tier " + gr.tier);
                }

                String tierPlain = TextFormatting.getTextWithoutFormattingCodes(tierText);
                int tabPadX = GuiStyle.TAB_PADDING_X;
                int tabW = fontRenderer.getStringWidth(tierPlain) + tabPadX * 2;
                int tabH = gg.headerH;
                int headerY = baseY - gg.headerH + 1;
                int tabX = gg.gridX + GuiStyle.TAB_OFFSET_X;

                int fill = (0xCC << 24) | (rarityRGB & 0xFFFFFF);
                int border = 0xFF000000 | darkenColor(rarityRGB, 0.6f);
                drawRoundedPanel(tabX, headerY, tabW, tabH, GuiStyle.TAB_RADIUS, fill, border);
                fontRenderer.drawString(tierPlain, tabX + tabPadX, headerY + GuiStyle.HEADER_TEXT_OFFSET_Y, 0x000000);
            }

            // Books in this row
            for (int i = 0; i < slice.size(); i++) {
                BookEntry b = slice.get(i);
                int x = gg.gridX + i * cellW;
                int y = baseY;

                // Base spine color from element.getColour(), fallback to precomputed
                int elemColor;
                Spell repSpell = tile.getSpellPublic(b.stack);
                Element repElem = repSpell != null ? repSpell.getElement() : null;
                if (repElem != null) {
                    Style st = repElem.getColour();
                    elemColor = rgbFromStyle(st, b.elementColor);
                } else {
                    elemColor = b.elementColor;
                }

                // Draw shaded spine texture, cached by (color, width, height)
                int spineW = cellW - GuiStyle.SPINE_LEFT_BORDER; // leave left border, tight on right
                int spineH = cellH - (GuiStyle.SPINE_TOP_BORDER + GuiStyle.SPINE_BOTTOM_BORDER); // leave top/bottom borders inside groove
                if (spineW > 0 && spineH > 0) {
                    ResourceLocation eIcon = (repElem != null) ? repElem.getIcon() : null;
                    ResourceLocation spineTex = GuiStyle.SPINE_EMBED_ICON
                            ? getOrCreateSpineTexture(elemColor & 0xFFFFFF, spineW, spineH, eIcon, GuiStyle.SPINE_ICON_SIZE)
                            : getOrCreateSpineTexture(elemColor & 0xFFFFFF, spineW, spineH, null, 0);

                            if (spineTex != null) {
                        this.mc.getTextureManager().bindTexture(spineTex);
                        GlStateManager.color(1f, 1f, 1f, 1f);
                        // Draw 1:1 without scaling artifacts
                        drawScaledCustomSizeModalRect(
                                x + GuiStyle.SPINE_LEFT_BORDER,
                                y + GuiStyle.SPINE_TOP_BORDER,
                                0, 0,
                                spineW, spineH,
                                spineW, spineH,
                                spineW, spineH);
                    } else {
                        // Fallback to solid fill
                        int lx = x + GuiStyle.SPINE_LEFT_BORDER;
                        int ty = y + GuiStyle.SPINE_TOP_BORDER;
                        int rx = lx + spineW;
                        int by = ty + spineH;
                        drawRect(lx, ty, rx, by, 0xFF000000 | elemColor);
                    }
                }

                // Draw small element icon at the bottom center (only when not embedded)
                if (repElem != null && !GuiStyle.SPINE_EMBED_ICON) {
                    ResourceLocation eIcon = repElem.getIcon();
                    if (eIcon != null) {
                        int iconSize = GuiStyle.SPINE_ICON_SIZE;
                        int iconX = x + GuiStyle.SPINE_LEFT_BORDER + (spineW - iconSize) / 2;
                        int iconY = y + GuiStyle.SPINE_TOP_BORDER + (spineH - iconSize - GuiStyle.SPINE_ICON_BOTTOM_MARGIN) + GuiStyle.SPINE_ICON_Y_OFFSET;

                        this.mc.getTextureManager().bindTexture(eIcon);
                        GlStateManager.color(1f, 1f, 1f, 1f);
                        drawScaledCustomSizeModalRect(iconX, iconY, 0, 0, 16, 16, iconSize, iconSize, 16, 16);
                    }
                }

                if (mouseX >= x && mouseX < x + cellW && mouseY >= y && mouseY < y + cellH) {
                    hoveredEntry = b;
                    int hoverBorder = GuiStyle.HOVER_BORDER;

                    drawRect(x, y, x + cellW + 1, y + 1, hoverBorder);
                    drawRect(x, y + cellH - 1, x + cellW + 1, y + cellH, hoverBorder);
                    drawRect(x, y, x + 1, y + cellH, hoverBorder);
                    drawRect(x + cellW, y, x + cellW + 1, y + cellH, hoverBorder);
                }
            }
        }

        return hoveredEntry;
    }

    /**
     * Renders the right panel with details of the hovered spell.
     * @param hovered The BookEntry currently hovered over, or null if none
     */
    private void renderRightPanel(BookEntry hovered) {
        int rightX = rightPanelX + GuiStyle.RIGHT_PANEL_INNER_MARGIN;
        int rightY = rightPanelY + GuiStyle.RIGHT_PANEL_INNER_MARGIN;

        if (hovered == null) {
            lastHoveredKey = null;
            lastHoveredCount = -1;
            cachedPresentation = null;
            return;
        }

        String key = tile.keyOfPublic(hovered.stack);
        // Use live count from snapshot so right panel updates even if left panel keys didn't change
        Integer live = tile.getSnapshot().get(key);
        int liveCount = live == null ? 0 : live.intValue();

        boolean hoverChanged = (lastHoveredKey == null || !lastHoveredKey.equals(key));
        boolean countChanged = (lastHoveredCount != liveCount);

        if (cachedPresentation == null || hoverChanged || countChanged) {
            cachedPresentation = buildSpellPresentation(hovered, liveCount);
            lastHoveredKey = key;
            lastHoveredCount = liveCount;
        }

        if (cachedPresentation != null) {
            drawBookInfoFromPresentation(cachedPresentation, rightX, rightY, 0xFFFFFF);
        }
    }

    /**
     * Draws the book info in the right panel for the given SpellPresentation.
     * @param p The SpellPresentation containing spell data
     * @param x The x position to start drawing
     * @param y The y position to start drawing
     * @param color The text color
     */
    private void drawBookInfoFromPresentation(SpellPresentation p, int x, int y, int color) {
        drawHeader(p, x, y, color);

        int rowY = y + GuiStyle.RIGHT_AFTER_HEADER_GAP;
        rowY = drawElementLine(p, x, rowY, color);
        rowY = drawPropertiesLines(p, x, rowY);
        rowY += GuiStyle.RIGHT_SECTION_GAP;

        drawDescriptionAndIcon(p, x, rowY, color);
    }

    /**
     * Draws the book info in the right panel for the given BookEntry.
     * @param b The BookEntry representing the spell book
     * @param x The x position to start drawing
     * @param y The y position to start drawing
     * @param color The text color
     */
    private void drawBookInfo(BookEntry b, int x, int y, int color) {
        SpellPresentation p = buildSpellPresentation(b, b.count);
        if (p == null) return;

        drawHeader(p, x, y, color);

        int rowY = y + GuiStyle.RIGHT_AFTER_HEADER_GAP;
        rowY = drawElementLine(p, x, rowY, color);
        rowY = drawPropertiesLines(p, x, rowY);

        // space before description
        rowY += GuiStyle.RIGHT_SECTION_GAP;

        drawDescriptionAndIcon(p, x, rowY, color);
    }

    // Presentation data for a spell/book entry
    private static class SpellPresentation {
        final ItemStack stack;
        final Spell spell;
        final boolean discovered;
        final String headerName;
        final ResourceLocation spellIcon;
        final String description;
        final String tierName;
        final String elementName;
        final ResourceLocation elementIcon;
        final int count;
        final int cost;
        final boolean isContinuous;
        final int chargeUpTime;
        final int cooldown;

        SpellPresentation(ItemStack stack, Spell spell, boolean discovered, String headerName, ResourceLocation spellIcon, String description,
                          String tierName, String elementName, ResourceLocation elementIcon, int count, int cost, boolean isContinuous, int chargeUpTime, int cooldown) {
            this.stack = stack;
            this.spell = spell;
            this.discovered = discovered;
            this.headerName = headerName;
            this.spellIcon = spellIcon;
            this.description = description;
            this.tierName = tierName;
            this.elementName = elementName;
            this.elementIcon = elementIcon;
            this.count = count;
            this.cost = cost;
            this.isContinuous = isContinuous;
            this.chargeUpTime = chargeUpTime;
            this.cooldown = cooldown;
        }
    }

    /**
     * Builds a SpellPresentation object for the given BookEntry and effective count.
     * @param b The BookEntry representing the spell book
     * @param effectiveCount The effective count of books to display
     * @return A SpellPresentation object containing all relevant data for display
     */
    private SpellPresentation buildSpellPresentation(BookEntry b, int effectiveCount) {
        ItemStack toShow = b.stack;
        Spell spell = tile.getSpellPublic(toShow);
        if (spell == null) return null;

        WizardData data = WizardData.get(player);
        boolean discovered = (data != null) && data.hasSpellBeenDiscovered(spell);

        // Treat all spells as discovered if player is in creative mode
        if (player != null && player.capabilities != null && player.capabilities.isCreativeMode) {
            discovered = true;
        }

        String headerName;
        ResourceLocation spellIcon = null;
        String description;
        if (discovered) {
            headerName = "\u00A77" + spell.getDisplayNameWithFormatting();
            spellIcon = spell.getIcon();
            description = spell.getDescription();
        } else {
            World world = Wizardry.proxy.getTheWorld();
            headerName = "\u00A79" + SpellGlyphData.getGlyphName(spell, world);
            description = SpellGlyphData.getGlyphDescription(spell, world);
        }

        String tierName = spell.getTier().getDisplayNameWithFormatting();

        Element element = spell.getElement();
        String elementName = element.getFormattingCode() + element.getName();
        ResourceLocation elementIcon = element.getIcon();

        int cost = spell.getCost();
        boolean isContinuous = spell.isContinuous;
        int chargeUpTime = spell.getChargeup();
        int cooldown = spell.getCooldown();

        if (isContinuous) cost = cost * 20; // per second

        return new SpellPresentation(toShow, spell, discovered, headerName, spellIcon, description, tierName,
                     elementName, elementIcon, effectiveCount, cost, isContinuous, chargeUpTime, cooldown);
    }

    /**
     * Draws the header (item icon and name) in the right panel.
     * @param p The spell presentation data
     * @param x The x position to start drawing
     * @param y The y position to draw the header
     * @param color The text color
     */
    private void drawHeader(SpellPresentation p, int x, int y, int color) {
        String countStr = formatCompactCount(p.count) + "x ";
        int textStartX = x + GuiStyle.RIGHT_TITLE_TEXT_GAP;
        int rightContentRight = rightPanelX + rightPanelW - GuiStyle.RIGHT_PANEL_INNER_MARGIN;
        int maxHeaderW = Math.max(0, rightContentRight - textStartX);
        String headerFitted = countStr + trimToWidth(p.headerName, Math.max(0, maxHeaderW - fontRenderer.getStringWidth(countStr) - 2));

        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(p.stack, x, y);
        RenderHelper.disableStandardItemLighting();

        fontRenderer.drawString(headerFitted, textStartX + 2, y + 4, color);
    }

    /**
     * Draws the element line in the right panel.
     * @param p The spell presentation data
     * @param x The x position to start drawing
     * @param rowY The y position to draw the line
     * @param color The text color
     * @return The updated y position after drawing
     */
    private int drawElementLine(SpellPresentation p, int x, int rowY, int color) {
        int colLeft = x;
        int textLeft = x + GuiStyle.RIGHT_ELEMENT_ICON_SIZE + 4;

        if (p.elementIcon != null) {
            this.mc.getTextureManager().bindTexture(p.elementIcon);
            GlStateManager.color(1f, 1f, 1f, 1f);
            drawScaledCustomSizeModalRect(colLeft, rowY, 0, 0, 16, 16, GuiStyle.RIGHT_ELEMENT_ICON_SIZE, GuiStyle.RIGHT_ELEMENT_ICON_SIZE, 16, 16);
        }

        fontRenderer.drawString(p.elementName, textLeft, rowY, color);

        return rowY + GuiStyle.RIGHT_LINE_GAP_MEDIUM;
    }

    /**
     * Formats time in ticks to a human-readable string.
     * @param ticks Time in ticks
     * @return A human-readable string representing the time (e.g., "3h", "3.5s", "120t", "instant")
    */
    private String formatTimeTicks(int ticks) {
        if (ticks <= 0) return "instant";

        double value = ticks;
        final int[] timeUnits = {20, 60, 60, 24, Integer.MAX_VALUE};
        final String[] timeUnitLabels = {"t", "s", "m", "h", "d"};
        assert timeUnits.length == timeUnitLabels.length;

        int i = 0;
        for (; i < timeUnits.length; i++) {
            int unit = timeUnits[i];
            if (value < unit) break;

            value /= unit;
        }

        String unit = timeUnitLabels[i];
        String s = String.format(Locale.ROOT, "%.1f%s", value, unit);
        if (s.endsWith(".0" + unit)) {
            s = s.substring(0, s.length() - 3) + unit;
        }

        return s;
    }

    /**
     * Draws the spell properties (cost, cooldown, charge) in the right panel.
     * @param p The spell presentation data
     * @param x The x position to start drawing
     * @param rowY The starting y position to draw the properties
     * @return The updated y position after drawing
     */
    private int drawPropertiesLines(SpellPresentation p, int x, int rowY) {
        String costStr = "Cost: ?";
        String cooldownStr = "Cooldown: ?";
        String chargeStr = "Charge: ?";

        if (p.discovered) {
            costStr = "Cost: " + p.cost + " mana" + (p.isContinuous ? "/s" : "");
            cooldownStr = "Cooldown: " + formatTimeTicks(p.cooldown);
            chargeStr = "Charge: " + formatTimeTicks(p.chargeUpTime);
        }

        fontRenderer.drawString(costStr, x, rowY, GuiStyle.DETAIL_TEXT);
        rowY += GuiStyle.RIGHT_LINE_GAP_SMALL;

        fontRenderer.drawString(cooldownStr, x, rowY, GuiStyle.DETAIL_TEXT);
        rowY += GuiStyle.RIGHT_LINE_GAP_SMALL;

        fontRenderer.drawString(chargeStr, x, rowY, GuiStyle.DETAIL_TEXT);
        rowY += GuiStyle.RIGHT_LINE_GAP_SMALL;

        return rowY;
    }

    /**
     * Draws the spell description and icon in the right panel.
     * @param p The spell presentation data
     * @param x The x position to start drawing
     * @param rowY The starting y position to draw the description
     * @param color The text color
     */
    private void drawDescriptionAndIcon(SpellPresentation p, int x, int rowY, int color) {
        String desc = p.description != null ? p.description : "";
        int maxW = rightPanelW - GuiStyle.RIGHT_PANEL_TEXT_SIDE_PAD * 2;
        boolean showIcon = p.discovered && p.spellIcon != null;
        int contentWidth = rightPanelW - GuiStyle.RIGHT_PANEL_INNER_MARGIN * 2;
        int iconW = showIcon ? contentWidth : 0;
        int iconH = iconW;
        int bottomClamp = rightPanelY + rightPanelH - GuiStyle.RIGHT_BOTTOM_CLAMP_MARGIN - iconH - (showIcon ? 4 : 0);

        for (String line : fontRenderer.listFormattedStringToWidth(desc, maxW)) {
            if (rowY + GuiStyle.RIGHT_DESC_LINE_HEIGHT > bottomClamp) break;
            fontRenderer.drawString(line, x, rowY, color);
            rowY += GuiStyle.RIGHT_DESC_LINE_HEIGHT;
        }

        if (showIcon) {
            int iconX = rightPanelX + GuiStyle.RIGHT_PANEL_INNER_MARGIN;
            int iconY = rightPanelY + rightPanelH - GuiStyle.RIGHT_BOTTOM_CLAMP_MARGIN - iconH;
            this.mc.getTextureManager().bindTexture(p.spellIcon);
            GlStateManager.color(1f, 1f, 1f, 1f);
            drawScaledCustomSizeModalRect(iconX, iconY, 0, 0, 16, 16, iconW, iconH, 16, 16);
        }
    }

    /**
     * Handles mouse click events for extracting books.
     * @param mouseX The x position of the mouse
     * @param mouseY The y position of the mouse
     * @param mouseButton The mouse button that was clicked
     * @throws IOException If an I/O error occurs
     */
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        // Extract on click
        if (hoveredEntryCached != null) {
            boolean shift = isShiftKeyDown();
            int amount = shift ? 16 : 1;
            if (this.mc != null && this.mc.player != null) {
                String key = tile.keyOfPublic(hoveredEntryCached.stack);
                NetworkHandler.CHANNEL.sendToServer(new MessageExtractBook(tile.getPos(), key, amount));
            }
        }
    }

    /**
     * Handles button actions for pagination.
     * @param button The button that was pressed
     */
    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == prevButton) {
            if (page > 0) page--;
        } else if (button == nextButton) {
            int maxPage = computeMaxPage();
            if (page < maxPage) page++;
        }
    }

    /**
     * Trims a string to fit within a maximum pixel width, appending an ellipsis if trimmed.
     * @param text The text to trim
     * @param maxW The maximum width in pixels
     * @return The trimmed text with ellipsis if necessary
     */
    private String trimToWidth(String text, int maxW) {
        if (fontRenderer.getStringWidth(text) <= maxW) return text;

        String s = text;
        while (s.length() > 0 && fontRenderer.getStringWidth(s + "…") > maxW) {
            s = s.substring(0, s.length() - 1);
        }

        return s + "…";
    }

    /**
     * Formats a count into a compact string (e.g., 1.2k, 3M).
     * @param n The count to format
     * @return A compact string representation of the count
     */
    private String formatCompactCount(int n) {
        if (n < 1000) return String.valueOf(n);

        int unitIndex = -1;
        final String[] units = {"k", "M", "B", "T"};
        double value = n;
        while (value >= 1000 && unitIndex + 1 < units.length) { value /= 1000.0; unitIndex++; }

        String fmt = (value < 10)
            ? String.format(Locale.ROOT, "%.1f", value)
            : String.format(Locale.ROOT, "%.0f", value);
        if (fmt.endsWith(".0")) fmt = fmt.substring(0, fmt.length() - 2);

        return fmt + units[unitIndex];
    }

    /**
     * Draws a grooved row background.
     * @param x the x position of the groove
     * @param y the y position of the groove
     * @param w the width of the groove
     * @param h the height of the groove
     */
    private void drawRowGroove(int x, int y, int w, int h) {
        int base = GuiStyle.GROOVE_BASE;    // slightly darker so it pops
        int hl = GuiStyle.GROOVE_HL;        // top highlight
        int sh = GuiStyle.GROOVE_SH;        // bottom shadow

        drawRect(x, y, x + w, y + h, base);
        drawRect(x, y, x + w, y + 1, hl);
        drawRect(x, y + h - 1, x + w, y + h, sh);
    }

    /**
     * Computes the maximum page index based on current layout and entries.
     * @return The maximum page index (0-based)
     */
    private int computeMaxPage() {
        // Vertical pagination: compute total rows after wrapping and divide by rowsPerPage
        List<List<BookEntry>> displayRows = new ArrayList<>();
        int cols = Math.max(1, gridCols);

        for (int t = 0; t < tierOrder.size(); t++) {
            int tier = tierOrder.get(t);
            List<BookEntry> rowBooks = rowsByTier.get(tier);
            if (rowBooks == null || rowBooks.isEmpty()) continue;

            for (int off = 0; off < rowBooks.size(); off += cols) {
                int endIdx = Math.min(off + cols, rowBooks.size());
                displayRows.add(rowBooks.subList(off, endIdx));
            }
        }

        int totalRows = displayRows.size();
        int rowsPerPage = Math.max(1, gridRows);

        return Math.max(0, (totalRows - 1) / rowsPerPage);
    }

    private void drawRoundedPanel(int x, int y, int w, int h, int radius, int fill, int border) {
        // Simple approximation: filled rect + border + clipped corners via small squares
        drawRect(x, y, x + w, y + h, fill);

        // border
        drawRect(x, y, x + w, y + 1, border);
        drawRect(x, y + h - 1, x + w, y + h, border);
        drawRect(x, y, x + 1, y + h, border);
        drawRect(x + w - 1, y, x + w, y + h, border);

        // corner cutouts to fake roundness
        int r = Math.max(2, radius);

        // top-left
        drawRect(x, y, x + r - 1, y + 1, 0x00000000);
        drawRect(x, y, x + 1, y + r - 1, 0x00000000);

        // top-right
        drawRect(x + w - r + 1, y, x + w, y + 1, 0x00000000);
        drawRect(x + w - 1, y, x + w, y + r - 1, 0x00000000);

        // bottom-left
        drawRect(x, y + h - 1, x + r - 1, y + h, 0x00000000);
        drawRect(x, y + h - r + 1, x + 1, y + h, 0x00000000);

        // bottom-right
        drawRect(x + w - r + 1, y + h - 1, x + w, y + h, 0x00000000);
        drawRect(x + w - 1, y + h - r + 1, x + w, y + h, 0x00000000);
    }

    /**
     * Darkens a color by the given factor.
     * @param rgb the original color in 0xRRGGBB format
     * @param factor the darkening factor (0.0 = black, 1.0 = original color)
     * @return the darkened color in 0xRRGGBB format
     */
    private static int darkenColor(int rgb, float factor) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        r = Math.max(0, Math.min(255, (int)(r * factor)));
        g = Math.max(0, Math.min(255, (int)(g * factor)));
        b = Math.max(0, Math.min(255, (int)(b * factor)));

        return (r << 16) | (g << 8) | b;
    }

    /**
     * Creates and caches a spine texture with shading effects based on the base color and dimensions.
     * Any further requests for the same (color, width, height) will return the cached texture.
     * @param baseRgb The base RGB color for the spine
     * @param w The width of the texture
     * @param h The height of the texture
     * @param iconRl The ResourceLocation of the element icon to embed, or null for no icon
     * @param iconSize The size of the embedded icon, if any
     * @return The ResourceLocation of the spine texture
     */
    private ResourceLocation getOrCreateSpineTexture(int baseRgb, int w, int h, ResourceLocation iconRl, int iconSize) {
        String key = baseRgb + "_" + w + "x" + h + (iconRl != null && GuiStyle.SPINE_EMBED_ICON ? ("|icon=" + iconRl.toString() + "|s=" + iconSize) : "");
        ResourceLocation cached = spineTextureCache.get(key);
        if (cached != null) return cached;

        // Generate shaded spine pixels
        int[] pixels = new int[w * h];

        // Create a deterministic seed from (color, w, h) for asymmetric details
        int seed = 0x9E3779B9;
        seed ^= baseRgb * 0x45d9f3b;
        seed ^= (w << 16) ^ (h << 1);
        seed = (seed ^ (seed >>> 16)) * 0x7feb352d;
        seed = (seed ^ (seed >>> 15)) * 0x846ca68b;
        seed = seed ^ (seed >>> 16);

        // Pseudo-random helpers
        IntUnaryOperator next = s -> {
            int z = s + 0x6D2B79F5;
            z = (z ^ (z >>> 15)) * 0x2C1B3C6D;
            z = (z ^ (z >>> 12)) * 0x297A2D39;

            return z ^ (z >>> 15);
        };
        IntFunction<Float> rf = s -> ((s & 0x7FFFFFFF) / (float)0x7FFFFFFF);

        int s1 = next.applyAsInt(seed);
        int s2 = next.applyAsInt(s1);
        int s3 = next.applyAsInt(s2);
        int s4 = next.applyAsInt(s3);
        int s5 = next.applyAsInt(s4);

        // Off-center bias for spine curvature
        float centerBias = 0.5f + (rf.apply(s1) - 0.5f) * 0.18f; // 0.32..0.68
        centerBias = Math.max(0.3f, Math.min(0.7f, centerBias));
        float asymTilt = GuiStyle.SPINE_ENABLE_TILT ? (rf.apply(s2) - 0.5f) * 0.12f : 0f; // -0.06..0.06
        float noiseAmp = GuiStyle.SPINE_ENABLE_NOISE ? GuiStyle.SPINE_NOISE_AMPLITUDE : 0f; // +/- percentage noise

        // Two horizontal bands near the top of available area above icon reserve
        int iconReserve = GuiStyle.SPINE_ICON_SIZE + GuiStyle.SPINE_ICON_BOTTOM_MARGIN; // reserve for bottom icon area
        int available = Math.max(0, h - iconReserve);
        int bandThickness = GuiStyle.SPINE_BAND_THICKNESS;
        int bandGap = GuiStyle.SPINE_BAND_GAP;

        // Place bands starting at top with a small top space
        int band1 = available > 0 ? Math.min(available - bandThickness, GuiStyle.SPINE_BAND_TOP_SPACE) : -1;
        int band2 = band1 >= 0 ? Math.min(available - bandThickness, band1 + bandThickness + bandGap) : -1;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // Curved vertical shading with biased center and tilt
                float nx = (x + 0.5f) / w; // 0..1
                float dist = Math.abs(nx - centerBias) * 2f; // 0 center -> ~1 edges
                        float side = Math.signum(nx - centerBias);
                dist *= (1f + asymTilt * side);
                float shade = GuiStyle.SPINE_ENABLE_CURVATURE
                    ? (GuiStyle.SPINE_CENTER_BRIGHTEN - GuiStyle.SPINE_EDGE_FACTOR * dist)
                    : 1.0f;
                int rgb = darkenColor(baseRgb, shade);

                // Subtle horizontal roll-off towards top/bottom
                float ny = (y + 0.5f) / h;
                float edgeY = Math.min(ny, 1f - ny) * 2f; // 0 at edges, 1 at center
                float vshade = GuiStyle.SPINE_VSHADE_BASE + GuiStyle.SPINE_VSHADE_RANGE * edgeY; // darker near top/bottom
                rgb = darkenColor(rgb, vshade);

                // Per-pixel noise to break uniformity (deterministic)
                int ns = s5 + x * 7349 + y * 1931;
                ns = next.applyAsInt(ns);
                float n = (rf.apply(ns) - 0.5f) * 2f; // -1..1
                if (noiseAmp != 0f) rgb = darkenColor(rgb, 1f + n * noiseAmp);

                pixels[y * w + x] = 0xFF000000 | (rgb & 0xFFFFFF);
            }
        }

        // Apply horizontal bands post-pass for clean thin lines (darken slightly)
        if (GuiStyle.SPINE_ENABLE_BANDS && band1 >= 0) {
            for (int x = 0; x < w; x++) {
                for (int dy = 0; dy < bandThickness; dy++) { // 2px thickness
                    int yy = band1 + dy;
                    if (yy >= 0 && yy < h) {
                        int idx = yy * w + x;
                        int rgb = pixels[idx] & 0xFFFFFF;
                        pixels[idx] = 0xFF000000 | (darkenColor(rgb, GuiStyle.SPINE_BAND1_DARKEN) & 0xFFFFFF);
                    }
                }
            }
        }

        if (GuiStyle.SPINE_ENABLE_BANDS && band2 >= 0) {
            for (int x = 0; x < w; x++) {
                for (int dy = 0; dy < bandThickness; dy++) {
                    int yy = band2 + dy;
                    if (yy >= 0 && yy < h) {
                        int idx = yy * w + x;
                        int rgb = pixels[idx] & 0xFFFFFF;
                        pixels[idx] = 0xFF000000 | (darkenColor(rgb, GuiStyle.SPINE_BAND2_DARKEN) & 0xFFFFFF);
                    }
                }
            }
        }

        // Optionally embed the element icon into the spine texture (bottom-centered)
        if (GuiStyle.SPINE_EMBED_ICON && iconRl != null && iconSize > 0) {
            int ix = (w - iconSize) / 2;
            int iy = (h - iconSize - GuiStyle.SPINE_ICON_BOTTOM_MARGIN) + GuiStyle.SPINE_ICON_Y_OFFSET;

            if (ix >= 0 && iy >= 0 && ix + iconSize <= w && iy + iconSize <= h) {
                try {
                    IResourceManager rm = this.mc.getResourceManager();
                    try (IResource res = rm.getResource(iconRl)) {
                        BufferedImage img = ImageIO.read(res.getInputStream());
                        if (img != null) {
                            int srcW = img.getWidth();
                            int srcH = img.getHeight();
                            int[] iconBuf = img.getRGB(0, 0, srcW, srcH, null, 0, srcW);

                            for (int ty = 0; ty < iconSize; ty++) {
                                int sy = ty * srcH / iconSize;
                                for (int tx = 0; tx < iconSize; tx++) {
                                    int sx = tx * srcW / iconSize;
                                    int argb = iconBuf[sy * srcW + sx];
                                    int a = (argb >>> 24) & 0xFF;
                                    if (a == 0) continue;

                                    int dstIndex = (iy + ty) * w + (ix + tx);
                                    int dst = pixels[dstIndex];

                                    int sr = (argb >>> 16) & 0xFF;
                                    int sg = (argb >>> 8) & 0xFF;
                                    int sb = (argb) & 0xFF;

                                    int dr = (dst >>> 16) & 0xFF;
                                    int dg = (dst >>> 8) & 0xFF;
                                    int db = (dst) & 0xFF;

                                    float af = a / 255.0f;
                                    int rr = (int)(sr * af + dr * (1 - af));
                                    int rg = (int)(sg * af + dg * (1 - af));
                                    int rb = (int)(sb * af + db * (1 - af));

                                    pixels[dstIndex] = 0xFF000000 | ((rr & 0xFF) << 16) | ((rg & 0xFF) << 8) | (rb & 0xFF);
                                }
                            }
                        }
                    }
                } catch (IOException ioe) {
                    // Ignore icon embedding on IO issues; spine will render without embedded icon
                }
            }
        }

        DynamicTexture dyn = new DynamicTexture(w, h);
        int[] data = dyn.getTextureData();
        System.arraycopy(pixels, 0, data, 0, pixels.length);

        dyn.updateDynamicTexture();
        ResourceLocation rl = this.mc.getTextureManager().getDynamicTextureLocation("sa_spine_" + key, dyn);
        spineTextureCache.put(key, rl);

        return rl;
    }

    /**
     * Extracts an RGB color from a Style object, with a fallback if not present.
     * @param style The Style object to extract the color from
     * @param fallbackRgb The fallback RGB color if none is found
     * @return The extracted RGB color or the fallback
     */
    private static int rgbFromStyle(Style style, int fallbackRgb) {
        if (style == null) return fallbackRgb;

        TextFormatting tf = style.getColor();
        if (tf == null) return fallbackRgb;

        switch (tf) {
            case BLACK: return 0x000000;
            case DARK_BLUE: return 0x0000AA;
            case DARK_GREEN: return 0x00AA00;
            case DARK_AQUA: return 0x00AAAA;
            case DARK_RED: return 0xAA0000;
            case DARK_PURPLE: return 0xAA00AA;
            case GOLD: return 0xFFAA00;
            case GRAY: return 0xAAAAAA;
            case DARK_GRAY: return 0x555555;
            case BLUE: return 0x5555FF;
            case GREEN: return 0x55FF55;
            case AQUA: return 0x55FFFF;
            case RED: return 0xFF5555;
            case LIGHT_PURPLE: return 0xFF55FF;
            case YELLOW: return 0xFFFF55;
            case WHITE: return 0xFFFFFF;
            default: return fallbackRgb;
        }
    }
}
