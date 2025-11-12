package com.spellarchives.gui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import com.spellarchives.container.ContainerSpellArchive;
import com.spellarchives.gui.GuiStyle;
import com.spellarchives.network.MessageExtractBook;
import com.spellarchives.network.NetworkHandler;
import com.spellarchives.render.DynamicTextureFactory;
import com.spellarchives.util.TextUtils;
import com.spellarchives.tile.TileSpellArchive;

import electroblob.wizardry.Wizardry;
import electroblob.wizardry.client.ClientProxy;
import electroblob.wizardry.client.MixedFontRenderer;
import electroblob.wizardry.constants.Element;
import electroblob.wizardry.data.SpellGlyphData;
import electroblob.wizardry.data.WizardData;
import electroblob.wizardry.spell.Spell;


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
    private int gridCols, gridRows;
    private int cellW = GuiStyle.CELL_W, cellH = GuiStyle.CELL_H, rowGap = GuiStyle.ROW_GAP;
    
    // Helper value objects for layout computations
    public static class GridGeometry {
        final int gridX, gridY, gridW, gridH, headerH;

        GridGeometry(int gridX, int gridY, int gridW, int gridH, int headerH) {
            this.gridX = gridX;
            this.gridY = gridY;
            this.gridW = gridW;
            this.gridH = gridH;
            this.headerH = headerH;
        }
    }

    public static class GrooveRow {
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

    public static class DisplayRows {
        final List<List<BookEntry>> rows;      // wrapped rows of books
        final List<Integer> rowTiers;          // tier per row, aligned with rows

        DisplayRows(List<List<BookEntry>> rows, List<Integer> rowTiers) {
            this.rows = rows;
            this.rowTiers = rowTiers;
        }
    }

    public static class PageInfo {
        final List<GrooveRow> layout;  // groove rows to render this page
        final boolean hasNext;         // whether more rows exist beyond this page

        PageInfo(List<GrooveRow> layout, boolean hasNext) {
            this.layout = layout;
            this.hasNext = hasNext;
        }
    }

    public static class BookEntry {
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
    private int lastChangeRev = -1;

    // (Spine and background dynamic textures are produced and cached by DynamicTextureFactory)

    private final GuiCacheManager cacheManager = new GuiCacheManager();

    public GuiSpellArchive(ContainerSpellArchive container, TileSpellArchive tile, EntityPlayer player) {
        super(container);
        this.tile = tile;
        this.player = player;
        this.xSize = GUI_WIDTH;
        this.ySize = GUI_HEIGHT;
    }

    @Override
    public void initGui() {
        // Dynamic sizing
        this.xSize = Math.max(GuiStyle.MIN_WIDTH, (int) (this.width * GuiStyle.WIDTH_RATIO));
        this.ySize = Math.max(GuiStyle.MIN_HEIGHT, (int) (this.height * GuiStyle.HEIGHT_RATIO));

        super.initGui();

        // Buttons with vanilla background
        this.buttonList.clear();
        this.prevButton = new GuiButton(1, 0, 0, 20, 20, I18n.format("gui.spellarchives.prev"));
        this.nextButton = new GuiButton(2, 0, 0, 20, 20, I18n.format("gui.spellarchives.next"));
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
            if (e.getValue() <= 0) continue;

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

        // Darken the outside world behind the GUI according to configured factor
        float darken = GuiStyle.SCREEN_DARKEN_FACTOR;
        if (darken > 0f) {
            int alpha = Math.max(0, Math.min(255, (int)(darken * 255f)));
            // drawRect uses ARGB int; full-screen overlay
            drawRect(0, 0, this.mc.displayWidth, this.mc.displayHeight, (alpha << 24));
        }

        // Keep entries up to date while GUI is open
        int rev = tile.getChangeCounterPublic();
        boolean tileChanged = (rev != lastChangeRev);

        // 1) Layout metrics and backgrounds
        computePanels();
        renderBackgroundPanels();
        GridGeometry gg = computeGridGeometry();

        // ensure caches are valid for current style revision
        cacheManager.checkStyleRevision(GuiStyle.CONFIG_REVISION);

        // 2) Data rows and page layout (only recompute if keys/layout/page changed)
        GuiSpellArchive.GridGeometry cachedGG = cacheManager.getCachedGG();
        boolean layoutChanged = (cachedGG == null) || gg.gridX != cachedGG.gridX || gg.gridY != cachedGG.gridY || gg.gridW != cachedGG.gridW || gg.gridH != cachedGG.gridH || gg.headerH != cachedGG.headerH;
        boolean colsChanged = cacheManager.haveColsChanged(gridCols);

        boolean keysChanged = false;
        if (tileChanged) {
            Set<String> currentKeys = getSnapshotKeys();
            if (cacheManager.haveKeysChanged(currentKeys)) rebuildEntries();
    
            lastChangeRev = rev;
        }

        // Build or obtain display rows (cache manager will decide if rebuild is needed)
        DisplayRows displayRows = cacheManager.getOrBuildDisplayRows(getSnapshotKeys(), rowsByTier, gridCols);

        // Build or obtain page info
        PageInfo pageInfo = cacheManager.getOrBuildPageInfo(displayRows, gg.gridX, gg.gridY, gg.gridW, gg.gridH, gg.headerH, page, cellH, rowGap, gridRows);

        cacheManager.setCachedGG(gg);

        // 3) Pagination widgets
        placePaginationButtons(pageInfo.hasNext);

        // 4) Render page and details
        BookEntry hovered = renderPage(displayRows, pageInfo, gg, mouseX, mouseY);

        // If hovered entry changed since last frame, clear cached presentation so the
        // right panel rebuilds immediately rather than reusing stale data.
        BookEntry prevHovered = cacheManager.getHoveredEntry();
        boolean hoveredChanged;
        if (prevHovered == null) hoveredChanged = (hovered != null);
        else if (hovered == null) hoveredChanged = true;
        else {
            String prevKey = tile.keyOfPublic(prevHovered.stack);
            String newKey = tile.keyOfPublic(hovered.stack);
            hoveredChanged = !prevKey.equals(newKey);
        }

        if (hoveredChanged) cacheManager.clearCachedPresentation();

        cacheManager.setHoveredEntry(hovered);
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

        if (GuiStyle.isPanelThemingEnabled()) {
            // Use centralized factory to obtain a cached panel background texture
            ResourceLocation bg = DynamicTextureFactory.getOrCreatePanelBg(Math.max(1, totalW), Math.max(1, totalH));
            if (bg != null) {
                this.mc.getTextureManager().bindTexture(bg);
                GlStateManager.color(1f, 1f, 1f, 1f);
                drawTexturedModalRect(totalX, totalY, 0, 0, totalW, totalH);
            }

            // soft inner shadow on top edge
            int shadowCol = (0x40 << 24) | (GuiStyle.GROOVE_SH & 0x00FFFFFF);
            drawRect(totalX, totalY, totalX + totalW, totalY + 6, shadowCol);

            // right panel uses darker fill
            drawRoundedPanel(rightPanelX, rightPanelY, rightPanelW, rightPanelH, GuiStyle.RIGHT_PANEL_RADIUS, GuiStyle.RIGHT_PANEL_FILL, GuiStyle.RIGHT_PANEL_BORDER);
        } else {
            drawRoundedPanel(totalX, totalY, totalW, totalH, GuiStyle.PANEL_RADIUS, GuiStyle.BACKGROUND_FILL, GuiStyle.BACKGROUND_BORDER);
            drawRoundedPanel(rightPanelX, rightPanelY, rightPanelW, rightPanelH, GuiStyle.RIGHT_PANEL_RADIUS, GuiStyle.RIGHT_PANEL_FILL, GuiStyle.RIGHT_PANEL_BORDER);
        }
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
                    tierText = rep != null ? rep.getTier().getDisplayNameWithFormatting() : (I18n.format("gui.spellarchives.tier_fallback", gr.tier));
                    rarityRGB = slice.get(0).rarityColor;
                } else {
                    tierText = I18n.format("gui.spellarchives.tier_fallback", gr.tier);
                }

                String tierPlain = TextFormatting.getTextWithoutFormattingCodes(tierText);
                int tabPadX = GuiStyle.TAB_PADDING_X;
                int tabW = fontRenderer.getStringWidth(tierPlain) + tabPadX * 2;
                int tabH = gg.headerH;
                int headerY = baseY - gg.headerH + 1;
                int tabX = gg.gridX + GuiStyle.TAB_OFFSET_X;

                int fill = (0xCC << 24) | (rarityRGB & 0xFFFFFF);
                int border = 0xFF000000 | TextUtils.darkenColor(rarityRGB, 0.6f);
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
                    elemColor = TextUtils.rgbFromStyle(st, b.elementColor);
                } else {
                    elemColor = b.elementColor;
                }

                // Draw shaded spine texture, cached by (color, width, height)
                int spineW = cellW - GuiStyle.SPINE_LEFT_BORDER; // leave left border, tight on right
                int spineH = cellH - (GuiStyle.SPINE_TOP_BORDER + GuiStyle.SPINE_BOTTOM_BORDER); // leave top/bottom borders inside groove
                if (spineW > 0 && spineH > 0) {
                    ResourceLocation eIcon = (repElem != null) ? repElem.getIcon() : null;
                    ResourceLocation spineTex = DynamicTextureFactory.getOrCreateSpineTexture(elemColor & 0xFFFFFF, spineW, spineH, GuiStyle.SPINE_EMBED_ICON ? eIcon : null, GuiStyle.SPINE_ICON_SIZE);

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

                    // subtle 1px shadow under the spine for depth if theme enabled
                    if (GuiStyle.isPanelThemingEnabled()) {
                        int shadow = (0x22 << 24) | (GuiStyle.GROOVE_SH & 0x00FFFFFF);
                        drawRect(x + GuiStyle.SPINE_LEFT_BORDER, y + GuiStyle.SPINE_TOP_BORDER + spineH, x + GuiStyle.SPINE_LEFT_BORDER + spineW, y + GuiStyle.SPINE_TOP_BORDER + spineH + 1, shadow);
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
            cacheManager.clearCachedPresentation();
            return;
        }

        String key = tile.keyOfPublic(hovered.stack);
        Integer live = tile.getSnapshot().get(key);
        int liveCount = live == null ? 0 : live.intValue();

        GuiSpellArchive.SpellPresentation p = cacheManager.getCachedPresentation(key, liveCount);
        if (p == null) {
            p = buildSpellPresentation(hovered, liveCount);
            cacheManager.putCachedPresentation(key, p);
        }

        if (p != null) drawBookInfoFromPresentation(p, rightX, rightY, 0xFFFFFF);
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
    public static class SpellPresentation {
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

        // Treat all spells as discovered if player is in creative mode or discovery mode is off
        if (player != null && player.capabilities != null && player.capabilities.isCreativeMode) discovered = true;
        if (!Wizardry.settings.discoveryMode) discovered = true;

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
        String countStr = TextUtils.formatCompactCount(p.count) + "x ";
        int textStartX = x + GuiStyle.RIGHT_TITLE_TEXT_GAP;
        int rightContentRight = rightPanelX + rightPanelW - GuiStyle.RIGHT_PANEL_INNER_MARGIN;
        int maxHeaderW = Math.max(0, rightContentRight - textStartX);

        // Use mixed font renderer for undiscovered spells to show glyphs
        String header = p.discovered ? p.headerName : "#" + p.headerName;  // mark as glyph text
        FontRenderer fontRendererInstance = p.discovered ? fontRenderer : ClientProxy.mixedFontRenderer;
        int spaceLeft = Math.max(0, maxHeaderW - fontRendererInstance.getStringWidth(countStr) - 2);
        String headerFitted = countStr + TextUtils.trimToWidth(fontRendererInstance, header, spaceLeft);

        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(p.stack, x, y);
        RenderHelper.disableStandardItemLighting();

        fontRendererInstance.drawString(headerFitted, textStartX + 2, y + 4, color);
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
     * Draws the spell properties (cost, cooldown, charge) in the right panel.
     * @param p The spell presentation data
     * @param x The x position to start drawing
     * @param rowY The starting y position to draw the properties
     * @return The updated y position after drawing
     */
    private int drawPropertiesLines(SpellPresentation p, int x, int rowY) {
        String costStr = I18n.format("gui.spellarchives.cost_unknown");
        String cooldownStr = I18n.format("gui.spellarchives.cooldown_unknown");
        String chargeStr = I18n.format("gui.spellarchives.charge_unknown");

        if (p.discovered) {
            costStr = I18n.format("gui.spellarchives.cost_fmt", p.cost, p.isContinuous ? I18n.format("timeunit.s") : "");
            cooldownStr = I18n.format("gui.spellarchives.cooldown_fmt", TextUtils.formatTimeTicks(p.cooldown));
            chargeStr = I18n.format("gui.spellarchives.charge_fmt", TextUtils.formatTimeTicks(p.chargeUpTime));
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
        int safeMaxW = Math.max(0, maxW);  // maxW may be negative if right panel is very narrow
        boolean showIcon = p.discovered && p.spellIcon != null;
        int contentWidth = rightPanelW - GuiStyle.RIGHT_PANEL_INNER_MARGIN * 2;
        int iconW = showIcon ? contentWidth : 0;
        int iconH = iconW;
        int bottomClamp = rightPanelY + rightPanelH - GuiStyle.RIGHT_BOTTOM_CLAMP_MARGIN - iconH - (showIcon ? 4 : 0);

        if (safeMaxW > 0 && desc.length() > 0) {
            FontRenderer fontRendererInstance = p.discovered ? fontRenderer : ClientProxy.mixedFontRenderer;

            // Sanitize the description string to ensure the ICU line-break iterator won't crash
            String safeDesc = TextUtils.sanitizeForBreakIterator(desc);

            for (String line : TextUtils.wrapTextToWidth(fontRendererInstance, safeDesc, safeMaxW)) {
                if (rowY + GuiStyle.RIGHT_DESC_LINE_HEIGHT > bottomClamp) break;
                if (!p.discovered) line = "#" + line;  // mark as glyph text

                fontRendererInstance.drawString(line, x, rowY, color);
                rowY += GuiStyle.RIGHT_DESC_LINE_HEIGHT;
            }
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
        GuiSpellArchive.BookEntry hovered = cacheManager.getHoveredEntry();
        if (hovered != null) {
            boolean shift = isShiftKeyDown();
            int amount = shift ? 16 : 1;
            if (this.mc != null && this.mc.player != null) {
                String key = tile.keyOfPublic(hovered.stack);
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

        // subtle drop shadow under the groove for depth (1-2px)
        // use a translucent darker color based on groove shadow
        int shadowAlpha = 0x30; // low alpha
        int shadowRgb = TextUtils.darkenColor(sh & 0xFFFFFF, 0.6f);
        int shadowCol = (shadowAlpha << 24) | (shadowRgb & 0x00FFFFFF);
        drawRect(x, y + h, x + w, y + h + 2, shadowCol);
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
}
