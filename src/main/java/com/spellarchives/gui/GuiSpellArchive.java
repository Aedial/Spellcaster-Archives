package com.spellarchives.gui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import com.spellarchives.container.ContainerSpellArchive;
import com.spellarchives.config.ClientConfig;
import com.spellarchives.network.MessageExtractBook;
import com.spellarchives.network.NetworkHandler;
import com.spellarchives.network.MessageDepositScrolls;
import com.spellarchives.network.MessageExtractScrolls;
import com.spellarchives.network.MessageDiscoverSpell;
import com.spellarchives.SpellArchives;
import com.spellarchives.config.SpellArchivesConfig;
import com.spellarchives.client.DynamicTextureFactory;
import com.spellarchives.util.TextUtils;
import com.spellarchives.tile.TileSpellArchive;

import electroblob.wizardry.Wizardry;
import electroblob.wizardry.client.ClientProxy;
import electroblob.wizardry.client.MixedFontRenderer;
import electroblob.wizardry.constants.Element;
import electroblob.wizardry.data.SpellGlyphData;
import electroblob.wizardry.data.WizardData;
import electroblob.wizardry.spell.Spell;

import com.spellarchives.gui.widget.DropdownWidget;
import com.spellarchives.gui.LeftPanelRenderer;
import com.spellarchives.gui.RightPanelRenderer;
import com.spellarchives.gui.SpellPresentation;
import com.spellarchives.gui.GuiUtils;
import com.spellarchives.gui.widget.DropdownWidget;
import com.spellarchives.gui.widget.InstructionWidget;


public class GuiSpellArchive extends GuiContainer {
    // Initial defaults; will be overridden by dynamic sizing
    private static final int GUI_WIDTH = 100;
    private static final int GUI_HEIGHT = 100;

    private final TileSpellArchive tile;
    private final EntityPlayer player;

    // Widgets
    private LeftPanelRenderer leftPanelRenderer;
    private RightPanelRenderer rightPanelRenderer;
    private DropdownWidget<DiscoveryFilter> discoveryDropdown;
    private DropdownWidget<String> modsDropdown;

    // Current page index (0-based)
    private int page = 0;

    // Scale of the filter dropdowns
    private static final float FILTER_HEADER_SCALE = 1.0f;
    // Scale of the filter dropdown content
    private static final float FILTER_CONTENT_SCALE = 1.0f;
    // Gap between the filters
    private static final int FILTER_HEADER_GAP = 5;
    // Reserved space above the filters
    private static final int FILTERS_TOP_MARGIN = 2;
    // Reserved space below the filters
    private static final int FILTERS_BOTTOM_GAP = 3;

    private enum DiscoveryFilter {
        ALL,
        DISCOVERED,
        UNDISCOVERED
    }

    private static class Rect {
        int x;
        int y;
        int w;
        int h;

        Rect set(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = Math.max(0, w);
            this.h = Math.max(0, h);

            return this;
        }

        void clear() {
            this.x = 0;
            this.y = 0;
            this.w = 0;
            this.h = 0;
        }

        boolean contains(int px, int py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    private GuiButton prevButton;
    private GuiButton nextButton;

    // Identification scroll slot geometry and hover state
    private int scrollSlotX, scrollSlotY, scrollSlotW, scrollSlotH;
    private boolean scrollSlotEnabled = true; // gated by config check

    // Cached layout pieces, recalculated each frame
    private int leftPanelX, leftPanelY, leftPanelW, leftPanelH;
    private int rightPanelX, rightPanelY, rightPanelW, rightPanelH;
    private int gridCols, gridRows;
    private int cellW = ClientConfig.CELL_W, cellH = ClientConfig.CELL_H, rowGap = ClientConfig.ROW_GAP;

    // Tooltip deferral so we can draw above buttons
    private List<String> pendingTooltip = null;
    private int pendingTipX = 0, pendingTipY = 0;

    // Filter state and caches
    private final Map<String, Integer> filteredSnapshot = new LinkedHashMap<>();
    private final List<String> availableModOptions = new ArrayList<>();
    private final Set<String> selectedModFilters = new LinkedHashSet<>();
    private boolean modFilterTouched = false;

    private DiscoveryFilter discoveryFilter = DiscoveryFilter.ALL;

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

    /**
     * Called when some external state changed (e.g., discovery sync) requiring the
     * right panel presentation to be rebuilt.
     */
    public void onExternalStateChanged() {
        cacheManager.clearCachedPresentation();
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
        final boolean discovered;
        final String modId;
        final Spell spell;

        BookEntry(ItemStack stack, int count, int tier, int element, int rarityColor, int elementColor,
                  boolean discovered, String modId, Spell spell) {
            this.stack = stack;
            this.count = count;
            this.tier = tier;
            this.element = element;
            this.rarityColor = rarityColor;
            this.elementColor = elementColor;
            this.discovered = discovered;
            this.modId = modId;
            this.spell = spell;
        }
    }

    private List<BookEntry> entries = new ArrayList<>();
    private List<BookEntry> unfilteredEntries = new ArrayList<>();
    private Map<Integer, List<BookEntry>> rowsByTier = new LinkedHashMap<>();
    private List<Integer> tierOrder = new ArrayList<>();
    private int lastChangeRev = -1;
    private int cachedEasyWidth = -1;
    private int cachedEasyWidthRev = -1;

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
        this.xSize = Math.max(ClientConfig.MIN_WIDTH, (int) (this.width * ClientConfig.WIDTH_RATIO));
        this.ySize = Math.max(ClientConfig.MIN_HEIGHT, (int) (this.height * ClientConfig.HEIGHT_RATIO));

        super.initGui();

        leftPanelRenderer = new LeftPanelRenderer(this);
        rightPanelRenderer = new RightPanelRenderer(this);

        discoveryDropdown = new DropdownWidget<>(mc, fontRenderer, I18n.format(getDiscoveryOptionKey(discoveryFilter)));
        discoveryDropdown.setHeaderScale(FILTER_HEADER_SCALE);
        discoveryDropdown.setOptionScale(FILTER_CONTENT_SCALE);
        discoveryDropdown.setOptions(Arrays.asList(DiscoveryFilter.values()));
        discoveryDropdown.setOptionProvider(f -> I18n.format(getDiscoveryOptionKey(f)));
        discoveryDropdown.setSelected(discoveryFilter);
        discoveryDropdown.setOnSelect(filter -> {
            this.discoveryFilter = filter;
            this.discoveryDropdown.setLabel(I18n.format(getDiscoveryOptionKey(filter)));
            onFiltersChanged();
        });

        modsDropdown = new DropdownWidget<>(mc, fontRenderer, getModTitle(new ArrayList<>(selectedModFilters)));
        modsDropdown.setHeaderScale(FILTER_HEADER_SCALE);
        modsDropdown.setOptionScale(FILTER_CONTENT_SCALE);
        modsDropdown.setMultiSelect(true);
        modsDropdown.setOnMultiSelect(filters -> {
            this.selectedModFilters.clear();
            this.selectedModFilters.addAll(filters);
            this.modsDropdown.setLabel(getModTitle(filters));
            this.modFilterTouched = true;
            onFiltersChanged();
        });

        // Reset easy layout cache on resize/init
        cachedEasyWidth = -1;

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
        unfilteredEntries.clear();
        rowsByTier.clear();
        tierOrder.clear();
        cachedEasyWidth = -1;
        filteredSnapshot.clear();

        refreshKnownModOptions();

        WizardData data = WizardData.get(player);
        boolean creative = player != null && player.capabilities != null && player.capabilities.isCreativeMode;
        boolean discoveryDisabled = !Wizardry.settings.discoveryMode;

        for (Map.Entry<String, Integer> e : tile.getSnapshot().entrySet()) {
            if (e.getValue() <= 0) continue;

            ItemStack stack = tile.stackFromKeyPublic(e.getKey());
            if (stack.isEmpty()) continue;

            Spell spell = tile.getSpellPublic(stack);
            String modId = getModId(stack);
            boolean discovered = isSpellDiscoveredForFilters(spell, data, creative, discoveryDisabled);
            BookEntry entry = new BookEntry(stack, e.getValue(), tile.getTierOf(stack), tile.getElementOf(stack),
                    tile.getRarityColor(stack), tile.getElementColor(stack), discovered, modId, spell);

            unfilteredEntries.add(entry);

            if (!passesDiscoveryFilter(entry) || !passesModFilter(entry)) continue;

            entries.add(entry);
            filteredSnapshot.put(e.getKey(), e.getValue());
        }

        // Group by tier (rarity). Keep a stable order: tier ascending (Novice->Master)
        Map<Integer, List<BookEntry>> grouped = entries.stream().collect(
            Collectors.groupingBy(b -> b.tier, LinkedHashMap::new, Collectors.toList())
        );

        grouped.keySet().stream().sorted().forEach(tier -> {
            List<BookEntry> list = grouped.get(tier);
            list.sort(Comparator.comparingInt((BookEntry b) -> b.element * 1000000 + b.stack.getMetadata())); // element, then metadata
            rowsByTier.put(tier, list);
            tierOrder.add(tier);
        });
    }

    private void refreshKnownModOptions() {
        List<String> mods = tile.getSpellModIdsPublic();
        Collections.sort(mods);

        availableModOptions.clear();
        availableModOptions.addAll(mods);

        if (!modFilterTouched) {
            selectedModFilters.clear();
            selectedModFilters.addAll(availableModOptions);
        } else {
            selectedModFilters.retainAll(availableModOptions);

        }

        this.modsDropdown.setLabel(getModTitle(new ArrayList<>(selectedModFilters)));
    }

    private boolean passesDiscoveryFilter(BookEntry entry) {
        if (discoveryFilter == DiscoveryFilter.ALL) return true;
        if (discoveryFilter == DiscoveryFilter.DISCOVERED) return entry.discovered;

        return !entry.discovered;
    }

    private boolean passesModFilter(BookEntry entry) {
        if (availableModOptions.isEmpty()) return true;
        if (!availableModOptions.contains(entry.modId)) return true;
        if (selectedModFilters.isEmpty()) return !modFilterTouched;

        return selectedModFilters.contains(entry.modId);
    }

    private boolean isSpellDiscoveredForFilters(Spell spell, WizardData data, boolean creative, boolean discoveryDisabled) {
        if (spell == null) return false;
        if (creative || discoveryDisabled) return true;

        return data != null && data.hasSpellBeenDiscovered(spell);
    }

    private String getModId(ItemStack stack) {
        ResourceLocation rl = stack.getItem().getRegistryName();
        return rl != null ? rl.getNamespace() : "unknown";
    }

    private void onFiltersChanged() {
        page = 0;
        cacheManager.clearAll();
        rebuildEntries();
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

    public TileSpellArchive getTile() {
        return tile;
    }

    public GuiCacheManager getCacheManager() {
        return cacheManager;
    }

    /**
     * To avoid drawing tooltips below buttons, we defer their rendering until after buttons are drawn.
     * This method sets the pending tooltip to be drawn later.
     * @param tooltip The tooltip lines
     * @param x The x position for the tooltip
     * @param y The y position for the tooltip
     */
    public void setPendingTooltip(List<String> tooltip, int x, int y) {
        this.pendingTooltip = tooltip;
        this.pendingTipX = x;
        this.pendingTipY = y;
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
        float darken = ClientConfig.SCREEN_DARKEN_FACTOR;
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
        cacheManager.checkStyleRevision(ClientConfig.CONFIG_REVISION);

        // 2) Data rows and page layout (only recompute if keys/layout/page changed)
        GuiSpellArchive.GridGeometry cachedGG = cacheManager.getCachedGG();
        boolean layoutChanged = (cachedGG == null) || gg.gridX != cachedGG.gridX || gg.gridY != cachedGG.gridY || gg.gridW != cachedGG.gridW || gg.gridH != cachedGG.gridH || gg.headerH != cachedGG.headerH;
        boolean colsChanged = cacheManager.haveColsChanged(gridCols);

        if (tileChanged) {
            rebuildEntries();
            cacheManager.clearAll();
            lastChangeRev = rev;
        }

        // Build or obtain display rows (cache manager will decide if rebuild is needed)
        DisplayRows displayRows = cacheManager.getOrBuildDisplayRows(getSnapshotKeys(), rowsByTier, gridCols);

        // Build or obtain page info
        PageInfo pageInfo = cacheManager.getOrBuildPageInfo(displayRows, gg.gridX, gg.gridY, gg.gridW, gg.gridH, gg.headerH, page, cellH, rowGap, gridRows);

        cacheManager.setCachedGG(gg);

        // 3) Pagination widgets
        leftPanelRenderer.placePaginationButtons(prevButton, nextButton, leftPanelX, leftPanelY, leftPanelW, leftPanelH, page, pageInfo.hasNext);

        // 4) Identification scroll slot
        leftPanelRenderer.computeAndRenderScrollSlot(mouseX, mouseY, leftPanelX, leftPanelY, leftPanelW, leftPanelH);

        // 5) Render page and details
        BookEntry hovered = leftPanelRenderer.renderPage(displayRows, pageInfo, gg, mouseX, mouseY, cellW, cellH, gridCols);

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
        rightPanelRenderer.render(hovered, rightPanelX, rightPanelY, rightPanelW, rightPanelH);

        discoveryDropdown.draw(mouseX, mouseY, partialTicks);
        modsDropdown.draw(mouseX, mouseY, partialTicks);
    }

    private Set<String> getSnapshotKeys() {
        return new HashSet<>(filteredSnapshot.keySet());
    }

    private void computePanels() {
        int margin = ClientConfig.MARGIN;
        int totalX = guiLeft;
        int totalY = guiTop;
        int totalW = xSize;
        int totalH = ySize;

        // Right panel width ~ 38% of total, min 120
        if (ClientConfig.EASY_LAYOUT_ENABLED) {
            // Check if we need to recalculate the easy layout width
            int currentRev = tile.getChangeCounterPublic();
            if (cachedEasyWidth == -1 || cachedEasyWidthRev != currentRev) {
                cachedEasyWidth = computeEasyLayoutWidth();
                cachedEasyWidthRev = currentRev;
            }
            rightPanelW = cachedEasyWidth;
        } else {
            rightPanelW = Math.max(ClientConfig.RIGHT_PANEL_MIN_WIDTH, (int) (totalW * ClientConfig.RIGHT_PANEL_RATIO));
        }

        rightPanelH = totalH - margin * 2;
        rightPanelX = totalX + totalW - rightPanelW - margin;
        rightPanelY = totalY + margin;

        leftPanelX = totalX + margin;
        leftPanelY = totalY + margin;
        leftPanelW = rightPanelX - leftPanelX - margin;
        leftPanelH = totalH - margin * 2;
    }

    private int computeEasyLayoutWidth() {
        if (unfilteredEntries.isEmpty()) return ClientConfig.RIGHT_PANEL_MIN_WIDTH;

        // Try to derive minW from fitting title, between Right_PANEL_MIN_WIDTH and 50% of total width
        int title_gap = ClientConfig.RIGHT_TITLE_TEXT_GAP;
        int right_text_pad = ClientConfig.RIGHT_PANEL_TEXT_SIDE_PAD;

        // Get the longest spell name (capped at 50% width)
        int minW = ClientConfig.RIGHT_PANEL_MIN_WIDTH;
        for (BookEntry entry : unfilteredEntries) {
            Spell spell = tile.getSpellPublic(entry.stack);
            if (spell == null) continue;

            String name = "66.6x " + spell.getDisplayNameWithFormatting();
            int nameW = title_gap + fontRenderer.getStringWidth(name) + right_text_pad * 2;
            if (nameW > minW) minW = nameW;
        }

        minW = Math.min(minW, (int)(this.xSize * 0.5f - 2 * right_text_pad));

        // Allow up to 65% width, trying to find best fit within that range (10 steps between min and max)
        int maxW = (int)(this.xSize * 0.65f);
        int step = minW < maxW - 10 ? (maxW - minW) / 10 : 1;

        int bestW = minW;
        int minOverflow = Integer.MAX_VALUE;

        int margin = ClientConfig.MARGIN;
        int innerMargin = ClientConfig.RIGHT_PANEL_INNER_MARGIN;
        // Available vertical space for content:
        // Panel Height - Top Margin (start Y) - Bottom Clamp Margin
        // rightPanelH is (ySize - margin * 2)
        int panelH = ySize - margin * 2;
        int availableSpace = panelH - margin - ClientConfig.RIGHT_BOTTOM_CLAMP_MARGIN;

        int headerH = ClientConfig.RIGHT_TITLE_ICON_SIZE + ClientConfig.RIGHT_AFTER_HEADER_GAP;
        int statsH = ClientConfig.RIGHT_LINE_GAP_MEDIUM + ClientConfig.RIGHT_LINE_GAP_SMALL * 3 + ClientConfig.RIGHT_SECTION_GAP;
        int fixedOverhead = headerH + statsH;

        for (int w = minW; w <= maxW; w += step) {
            int contentW = w - innerMargin * 2;
            int textW = w - ClientConfig.RIGHT_PANEL_TEXT_SIDE_PAD * 2;
            if (contentW <= 0 || textW <= 0) continue;

            int maxSpellOverflow = 0;

            for (BookEntry entry : unfilteredEntries) {
                Spell spell = tile.getSpellPublic(entry.stack);
                if (spell == null) continue;

                String desc = spell.getDescription();
                if (desc == null) desc = "";

                // Calculate description height
                List<String> lines = TextUtils.wrapTextToWidth(fontRenderer, desc, textW);
                int descH = lines.size() * ClientConfig.RIGHT_DESC_LINE_HEIGHT;

                // Icon height (full width)
                int iconH = contentW;

                int totalH = fixedOverhead + descH + iconH;
                int overflow = totalH - availableSpace;
                if (overflow > maxSpellOverflow) maxSpellOverflow = overflow;
            }

            if (maxSpellOverflow <= 0) return w;  // Found a width that fits everything

            if (maxSpellOverflow < minOverflow) {
                minOverflow = maxSpellOverflow;
                bestW = w;
            }
        }

        return bestW;
    }


    private void renderBackgroundPanels() {
        int totalX = guiLeft;
        int totalY = guiTop;
        int totalW = xSize;
        int totalH = ySize;

        if (ClientConfig.isPanelThemingEnabled()) {
            // Use centralized factory to obtain a cached panel background texture
            ResourceLocation bg = DynamicTextureFactory.getOrCreatePanelBg(Math.max(1, totalW), Math.max(1, totalH));
            if (bg != null) {
                this.mc.getTextureManager().bindTexture(bg);
                GlStateManager.color(1f, 1f, 1f, 1f);
                drawTexturedModalRect(totalX, totalY, 0, 0, totalW, totalH);
            }

            // soft inner shadow on top edge
            int shadowCol = (0x40 << 24) | (ClientConfig.GROOVE_SH & 0x00FFFFFF);
            drawRect(totalX, totalY, totalX + totalW, totalY + 6, shadowCol);

            // right panel uses darker fill
            GuiUtils.drawRoundedPanel(rightPanelX, rightPanelY, rightPanelW, rightPanelH, ClientConfig.RIGHT_PANEL_RADIUS, ClientConfig.RIGHT_PANEL_FILL, ClientConfig.RIGHT_PANEL_BORDER);
        } else {
            GuiUtils.drawRoundedPanel(totalX, totalY, totalW, totalH, ClientConfig.PANEL_RADIUS, ClientConfig.BACKGROUND_FILL, ClientConfig.BACKGROUND_BORDER);
            GuiUtils.drawRoundedPanel(rightPanelX, rightPanelY, rightPanelW, rightPanelH, ClientConfig.RIGHT_PANEL_RADIUS, ClientConfig.RIGHT_PANEL_FILL, ClientConfig.RIGHT_PANEL_BORDER);
        }
    }

    /**
     * Computes the grid geometry for the left panel content area.
     * @return The GridGeometry containing grid position and size
     */
    private GridGeometry computeGridGeometry() {
        int bottomBar = ClientConfig.BOTTOM_BAR_HEIGHT;
        updateFilterLayout();

        int reservedTop = discoveryDropdown.getHeaderHeight() + FILTERS_BOTTOM_GAP;
        int gridX = leftPanelX + ClientConfig.GRID_INNER_PADDING;
        int gridY = leftPanelY + 8 + reservedTop;
        int gridW = leftPanelW - ClientConfig.GRID_INNER_PADDING * 2;
        int gridH = leftPanelH - bottomBar - ClientConfig.LEFT_PANEL_BOTTOM_PAD - reservedTop;
        if (gridH < ClientConfig.GRID_INNER_PADDING) gridH = ClientConfig.GRID_INNER_PADDING;

        gridCols = Math.max(1, gridW / (cellW + ClientConfig.SPINE_LEFT_BORDER));
        int headerH = this.fontRenderer.FONT_HEIGHT + ClientConfig.HEADER_EXTRA;
        gridRows = Math.max(1, gridH / (cellH + rowGap + headerH));

        return new GridGeometry(gridX, gridY, gridW, gridH, headerH);
    }

    private void updateFilterLayout() {
        int filterBarX = leftPanelX + ClientConfig.GRID_INNER_PADDING;
        int filterBarY = leftPanelY + FILTERS_TOP_MARGIN;
        int filterBarW = Math.max(0, leftPanelW - ClientConfig.GRID_INNER_PADDING * 2);

        // Split available width evenly between the two headers, with gap
        int discoveryWidth = filterBarW / 2 - FILTER_HEADER_GAP / 2;
        int modsWidth = filterBarW - discoveryWidth - FILTER_HEADER_GAP;

        discoveryDropdown.setBounds(filterBarX, filterBarY, discoveryWidth, discoveryDropdown.getHeaderHeight());

        modsDropdown.setBounds(filterBarX + discoveryWidth + FILTER_HEADER_GAP, filterBarY, modsWidth, modsDropdown.getHeaderHeight());
        modsDropdown.setOptions(availableModOptions);
        modsDropdown.setSelectedOptions(new ArrayList<>(selectedModFilters));
    }

    private String getDiscoveryOptionKey(DiscoveryFilter filter) {
        switch (filter) {
            case DISCOVERED:
                return "gui.spellarchives.filter.discovery.option.discovered";
            case UNDISCOVERED:
                return "gui.spellarchives.filter.discovery.option.undiscovered";
            default:
                return "gui.spellarchives.filter.discovery.option.all";
        }
    }

    private String getModTitle(List<String> selectedMods) {
        int selected = selectedMods.size();
        int total = availableModOptions.size();

        if (total == 0) {
            return I18n.format("gui.spellarchives.filter.mods.dropdown.empty");
        } else if (selected == 0) {
            return I18n.format("gui.spellarchives.filter.mods.summary.none");
        } else if (selected == total) {
            return I18n.format("gui.spellarchives.filter.mods.summary.all", total);
        } else {
            return I18n.format("gui.spellarchives.filter.mods.summary.some", selected, total);
        }
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
     * Handles mouse click events for extracting books.
     * @param mouseX The x position of the mouse
     * @param mouseY The y position of the mouse
     * @param mouseButton The mouse button that was clicked
     * @throws IOException If an I/O error occurs
     */
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (discoveryDropdown.mouseClicked(mouseX, mouseY, mouseButton)) return;
        if (modsDropdown.mouseClicked(mouseX, mouseY, mouseButton)) return;

        super.mouseClicked(mouseX, mouseY, mouseButton);

        // Interactions on book rows (left: extract, right: discover)
        GuiSpellArchive.BookEntry hovered = cacheManager.getHoveredEntry();
        if (hovered == null || this.mc == null || this.mc.player == null) return;

        String key = tile.keyOfPublic(hovered.stack);

        if (mouseButton == 1) {
            // Right click: attempt discovery if undiscovered
            Spell spell = tile.getSpellPublic(hovered.stack);
            WizardData data = WizardData.get(player);
            if (spell != null && data != null) {
                if (data.hasSpellBeenDiscovered(spell)) return;  // discovered -> do nothing on right click

                // only send if config enabled and we have scrolls
                if (SpellArchivesConfig.isScrollReserveEnabled() && tile.getIdentificationScrollCountPublic() > 0) {
                    NetworkHandler.CHANNEL.sendToServer(new MessageDiscoverSpell(tile.getPos(), key));
                }
            }
        } else if (mouseButton == 0) {
            // Left click: extract (stack if shift)
            boolean shift = isShiftKeyDown();
            int amount = shift ? hovered.stack.getMaxStackSize() : 1;
            NetworkHandler.CHANNEL.sendToServer(new MessageExtractBook(tile.getPos(), key, amount));
        }
    }

    /**
     * Wrapper method to determine if the scroll slot feature should be enabled.
     * Currently returns true when Wizardry discovery mode is enabled or the player is in creative.
     * Hook for future expansion/config bridging.
     */
    private boolean isScrollSlotEnabled() {
        return SpellArchivesConfig.isScrollReserveEnabled();
    }

    /**
     * Handles mouse release events for the identification scroll slot.
     * @param mouseX The x position of the mouse
     * @param mouseY The y position of the mouse
     * @param state The mouse button that was released
     */
    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);

        boolean inScrollSlot = mouseX >= scrollSlotX && mouseX < scrollSlotX + scrollSlotW && mouseY >= scrollSlotY && mouseY < scrollSlotY + scrollSlotH;
        if (!inScrollSlot) return;

        if (this.mc == null || this.mc.player == null) return;

        ItemStack carried = this.mc.player.inventory.getItemStack();
        boolean rightClick = state == 1;

        // we don't need to check for enable or other items, as no external inventory is accessible in the GUI
        if (carried.isEmpty()) {
            // Only extract when the cursor is empty; left=all, right=half
            NetworkHandler.CHANNEL.sendToServer(new MessageExtractScrolls(tile.getPos(), rightClick));
        } else if (!rightClick && tile.isIdentificationScroll(carried)) {
            // Deposit identification scrolls if clicking the slot
            NetworkHandler.CHANNEL.sendToServer(new MessageDepositScrolls(tile.getPos(), carried.getCount()));
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
     * Draws the screen for the GUI. This handles any deferred tooltips to ensure they appear above buttons.
     * @param mouseX The x position of the mouse
     * @param mouseY The y position of the mouse
     * @param partialTicks The partial ticks for rendering
     */
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Clear any pending tooltip before rendering cycle
        pendingTooltip = null;
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Draw deferred tooltip last so it appears above buttons
        if (pendingTooltip != null && !pendingTooltip.isEmpty()) {
            this.drawHoveringText(pendingTooltip, pendingTipX, pendingTipY);
        }
    }


}
