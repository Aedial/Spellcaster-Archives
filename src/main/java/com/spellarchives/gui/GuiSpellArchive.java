package com.spellarchives.gui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
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


public class GuiSpellArchive extends GuiContainer {
    // Initial defaults; will be overridden by dynamic sizing
    private static final int GUI_WIDTH = ClientConfig.DEFAULT_GUI_WIDTH;
    private static final int GUI_HEIGHT = ClientConfig.DEFAULT_GUI_HEIGHT;

    private final TileSpellArchive tile;
    private final EntityPlayer player;

    private int page = 0;

    private static final float FILTER_TEXT_SCALE = 1;
    private static final int FILTER_HEADER_PADDING = 3;
    private static final int FILTER_HEADER_GAP = 5;
    private static final int FILTER_OPTION_HEIGHT = 8;
    private static final int FILTERS_TOP_MARGIN = 2;
    private static final int FILTERS_BOTTOM_GAP = 3;
    private static final long DOUBLE_CLICK_MS = 300L;

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
    private final Set<String> modsPresentInSnapshot = new LinkedHashSet<>();
    private boolean modFilterTouched = false;

    private DiscoveryFilter discoveryFilter = DiscoveryFilter.ALL;
    private boolean discoveryDropdownOpen = false;
    private boolean modsDropdownOpen = false;
    private long lastModsHeaderClickMs = 0L;

    private int filterBarX, filterBarY, filterBarW;
    private final Rect discoveryHeaderRect = new Rect();
    private final Rect discoveryContentRect = new Rect();
    private final Rect modsHeaderRect = new Rect();
    private final Rect modsContentRect = new Rect();

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
        modsPresentInSnapshot.clear();

        refreshKnownModOptions();

        WizardData data = WizardData.get(player);
        boolean creative = player != null && player.capabilities != null && player.capabilities.isCreativeMode;
        boolean discoveryDisabled = !Wizardry.settings.discoveryMode;

        for (Map.Entry<String, Integer> e : tile.getSnapshot().entrySet()) {
            if (e.getValue() <= 0) continue;

            ItemStack stack = tile.stackFromKeyPublic(e.getKey());
            if (stack.isEmpty()) continue;

            Spell spell = tile.getSpellPublic(stack);
            boolean discovered = isSpellDiscoveredForFilters(spell, data, creative, discoveryDisabled);
            String modId = getModId(stack);
            modsPresentInSnapshot.add(modId);

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
        if (data == null) return false;

        return data.hasSpellBeenDiscovered(spell);
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
        renderFilterHeaders(mouseX, mouseY);

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
        placePaginationButtons(pageInfo.hasNext);

        // 4) Identification scroll slot
        computeAndRenderScrollSlot(mouseX, mouseY);

        // 5) Render page and details
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

        renderFilterDropdowns(mouseX, mouseY);
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

        // Get the longest spell name
        int minW = ClientConfig.RIGHT_PANEL_MIN_WIDTH;
        for (BookEntry entry : unfilteredEntries) {
            Spell spell = tile.getSpellPublic(entry.stack);
            if (spell == null) continue;

            String name = "66.6x " + spell.getDisplayNameWithFormatting();
            int nameW = title_gap + fontRenderer.getStringWidth(name) + right_text_pad * 2;
            if (nameW > minW) minW = nameW;
        }

        minW = Math.min(minW, (int)(this.xSize * 0.5f));        // Cap at 50% width

        int maxW = (int)(this.xSize * 0.65f);                   // Allow up to 65% width
        int step = minW < maxW - 10 ? (maxW - minW) / 10 : 1;   // 10 steps between min and max

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
            drawRoundedPanel(rightPanelX, rightPanelY, rightPanelW, rightPanelH, ClientConfig.RIGHT_PANEL_RADIUS, ClientConfig.RIGHT_PANEL_FILL, ClientConfig.RIGHT_PANEL_BORDER);
        } else {
            drawRoundedPanel(totalX, totalY, totalW, totalH, ClientConfig.PANEL_RADIUS, ClientConfig.BACKGROUND_FILL, ClientConfig.BACKGROUND_BORDER);
            drawRoundedPanel(rightPanelX, rightPanelY, rightPanelW, rightPanelH, ClientConfig.RIGHT_PANEL_RADIUS, ClientConfig.RIGHT_PANEL_FILL, ClientConfig.RIGHT_PANEL_BORDER);
        }
    }

    /**
     * Computes the grid geometry for the left panel content area.
     * @return The GridGeometry containing grid position and size
     */
    private GridGeometry computeGridGeometry() {
        int bottomBar = ClientConfig.BOTTOM_BAR_HEIGHT;
        updateFilterLayout();

        int reservedTop = discoveryHeaderRect.h + FILTERS_BOTTOM_GAP;
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
        filterBarX = leftPanelX + ClientConfig.GRID_INNER_PADDING;
        filterBarY = leftPanelY + FILTERS_TOP_MARGIN;
        filterBarW = Math.max(0, leftPanelW - ClientConfig.GRID_INNER_PADDING * 2);

        // Split available width evenly between the two headers, with gap
        int discoveryWidth = filterBarW / 2 - FILTER_HEADER_GAP / 2;
        int modsWidth = filterBarW - discoveryWidth - FILTER_HEADER_GAP;

        int textHeight = (int)(fontRenderer.FONT_HEIGHT * FILTER_TEXT_SCALE);

        discoveryHeaderRect.set(filterBarX, filterBarY, discoveryWidth, textHeight);
        modsHeaderRect.set(discoveryHeaderRect.x + discoveryHeaderRect.w + FILTER_HEADER_GAP, filterBarY, modsWidth, textHeight);

        int discoveryContentH = DiscoveryFilter.values().length * FILTER_OPTION_HEIGHT + 4;
        discoveryContentRect.set(discoveryHeaderRect.x, discoveryHeaderRect.y + discoveryHeaderRect.h, discoveryHeaderRect.w, discoveryContentH);

        int modsOptions = Math.max(1, availableModOptions.isEmpty() ? 1 : availableModOptions.size());
        int modsContentH = modsOptions * FILTER_OPTION_HEIGHT + 4;
        modsContentRect.set(modsHeaderRect.x, modsHeaderRect.y + modsHeaderRect.h, modsHeaderRect.w, modsContentH);

        if (modsHeaderRect.w <= 0 || modsHeaderRect.h <= 0) {
            modsHeaderRect.clear();
            modsContentRect.clear();
        }
    }

    private void renderFilterHeaders(int mouseX, int mouseY) {
        boolean discoveryHeaderHover = discoveryHeaderRect.contains(mouseX, mouseY);
        boolean discoveryContentHover = discoveryDropdownOpen && discoveryContentRect.contains(mouseX, mouseY);
        if (discoveryHeaderHover) discoveryDropdownOpen = true;
        else if (discoveryDropdownOpen && !discoveryContentHover) discoveryDropdownOpen = false;

        boolean modsHeaderHover = modsHeaderRect.contains(mouseX, mouseY);
        boolean modsContentHover = modsDropdownOpen && modsContentRect.contains(mouseX, mouseY);
        if (modsHeaderHover) modsDropdownOpen = true;
        else if (modsDropdownOpen && !modsContentHover) modsDropdownOpen = false;

        drawFilterHeader(discoveryHeaderRect, getDiscoveryHeaderText(), discoveryHeaderHover || discoveryDropdownOpen);
        drawFilterHeader(modsHeaderRect, getModsHeaderText(), modsHeaderHover || modsDropdownOpen);
    }

    private void renderFilterDropdowns(int mouseX, int mouseY) {
        float scale = FILTER_TEXT_SCALE;

        if (discoveryDropdownOpen && discoveryContentRect.w > 0 && discoveryContentRect.h > 0) {
            drawDropdownBackground(discoveryContentRect);

            DiscoveryFilter[] options = DiscoveryFilter.values();
            for (int i = 0; i < options.length; i++) {
                int rowY = discoveryContentRect.y + 2 + i * FILTER_OPTION_HEIGHT;
                boolean hover = mouseX >= discoveryContentRect.x && mouseX < discoveryContentRect.x + discoveryContentRect.w &&
                                mouseY >= rowY && mouseY < rowY + FILTER_OPTION_HEIGHT;
                drawFilterOptionRow(discoveryContentRect.x, rowY, discoveryContentRect.w, hover);
                drawIndicator(discoveryContentRect.x + 4, rowY + 2, true, discoveryFilter == options[i]);

                String label = I18n.format(getDiscoveryOptionKey(options[i]));
                int maxWidthUnscaled = (int) ((discoveryContentRect.w - 16) / scale);
                String trimmed = TextUtils.trimToWidth(fontRenderer, label, maxWidthUnscaled);

                GL11.glPushMatrix();
                GL11.glScalef(scale, scale, 1f);
                fontRenderer.drawString(trimmed, (discoveryContentRect.x + 16) / scale, (rowY + 2) / scale, 0xFFFFFF, false);
                GL11.glPopMatrix();
            }
        }

        if (modsDropdownOpen && modsContentRect.w > 0 && modsContentRect.h > 0) {
            drawDropdownBackground(modsContentRect);

            if (availableModOptions.isEmpty()) {
                String empty = I18n.format("gui.spellarchives.filter.mods.dropdown.empty");
                int maxWidthUnscaled = (int) ((modsContentRect.w - FILTER_HEADER_PADDING * 2) / scale);
                String trimmed = TextUtils.trimToWidth(fontRenderer, empty, maxWidthUnscaled);

                GL11.glPushMatrix();
                GL11.glScalef(scale, scale, 1f);
                fontRenderer.drawString(trimmed, (modsContentRect.x + FILTER_HEADER_PADDING) / scale, (modsContentRect.y + 4) / scale, 0xCCCCCC, false);
                GL11.glPopMatrix();
            } else {
                for (int i = 0; i < availableModOptions.size(); i++) {
                    int rowY = modsContentRect.y + 2 + i * FILTER_OPTION_HEIGHT;
                    boolean hover = mouseX >= modsContentRect.x && mouseX < modsContentRect.x + modsContentRect.w &&
                                    mouseY >= rowY && mouseY < rowY + FILTER_OPTION_HEIGHT;
                    String modId = availableModOptions.get(i);
                    boolean selected = selectedModFilters.contains(modId);

                    drawFilterOptionRow(modsContentRect.x, rowY, modsContentRect.w, hover);
                    drawIndicator(modsContentRect.x + 4, rowY + 2, false, selected);

                    int textColor = modsPresentInSnapshot.contains(modId) ? 0xFFFFFF : 0xAAAAAA;
                    int maxWidthUnscaled = (int) ((modsContentRect.w - 18) / scale);
                    String trimmed = TextUtils.trimToWidth(fontRenderer, modId, maxWidthUnscaled);

                    GL11.glPushMatrix();
                    GL11.glScalef(scale, scale, 1f);
                    fontRenderer.drawString(trimmed, (modsContentRect.x + 16) / scale, (rowY + 2) / scale, textColor, false);
                    GL11.glPopMatrix();
                }
            }
        }
    }

    private void drawFilterHeader(Rect rect, String text, boolean active) {
        if (rect.w <= 0 || rect.h <= 0) return;

        int fill = active ? 0xAA222222 : 0x66000000;
        int border = active ? ClientConfig.HOVER_BORDER : ClientConfig.RIGHT_PANEL_BORDER;
        drawRoundedPanel(rect.x, rect.y, rect.w, rect.h, ClientConfig.PANEL_RADIUS, fill, border);

        float scale = 0.5f;
        int maxWidthUnscaled = (int) ((rect.w - FILTER_HEADER_PADDING * 2) / scale);
        String fitted = TextUtils.trimToWidth(fontRenderer, text, maxWidthUnscaled);

        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, 1f);
        float textX = (rect.x + FILTER_HEADER_PADDING) / scale;
        float textY = (rect.y + (rect.h - fontRenderer.FONT_HEIGHT * scale) / 2f) / scale;
        fontRenderer.drawString(fitted, textX, textY, 0xFFFFFF, false);
        GL11.glPopMatrix();
    }

    private void drawDropdownBackground(Rect rect) {
        drawRoundedPanel(rect.x, rect.y, rect.w, rect.h, ClientConfig.PANEL_RADIUS, 0xCC111111, ClientConfig.RIGHT_PANEL_BORDER);
    }

    private void drawFilterOptionRow(int x, int y, int w, boolean hover) {
        int fill = hover ? 0x44FFFFFF : 0x22000000;
        drawRect(x + 1, y, x + w - 1, y + FILTER_OPTION_HEIGHT, fill);
    }

    private void drawIndicator(int x, int y, boolean radio, boolean selected) {
        int size = 4;
        int border = ClientConfig.RIGHT_PANEL_BORDER;
        int fill = 0xFF2A2A2A;
        drawRect(x, y, x + size, y + size, fill);
        drawRect(x, y, x + size, y + 1, border);
        drawRect(x, y + size - 1, x + size, y + size, border);
        drawRect(x, y, x + 1, y + size, border);
        drawRect(x + size - 1, y, x + size, y + size, border);

        if (selected) {
            drawRect(x + 1, y + 1, x + size - 1, y + size - 1, 0xFFFFFFFF);
        } else if (radio) {
            drawRect(x + 1, y + 1, x + size - 1, y + size - 1, 0x66333333);
        }
    }

    private String getDiscoveryHeaderText() {
        String label = I18n.format("gui.spellarchives.filter.discovery.label");
        String option = I18n.format(getDiscoveryOptionKey(discoveryFilter));
        return label + ": " + option;
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

    private String getModsHeaderText() {
        String label = I18n.format("gui.spellarchives.filter.mods.label");
        if (availableModOptions.isEmpty()) {
            return label + ": " + I18n.format("gui.spellarchives.filter.mods.summary.none");
        }

        if (selectedModFilters.isEmpty()) {
            return label + ": " + I18n.format("gui.spellarchives.filter.mods.summary.none");
        }

        if (!modFilterTouched || selectedModFilters.size() == availableModOptions.size()) {
            return label + ": " + I18n.format("gui.spellarchives.filter.mods.summary.all", availableModOptions.size());
        }

        return label + ": " + I18n.format("gui.spellarchives.filter.mods.summary.some", selectedModFilters.size(), availableModOptions.size());
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
     * Places and updates the visibility of the pagination buttons.
     * @param anyHasNext Whether there are more pages after the current one
     */
    private void placePaginationButtons(boolean anyHasNext) {
        int arrowsY = leftPanelY + leftPanelH - ClientConfig.BOTTOM_BAR_HEIGHT / 2 - ClientConfig.ARROWS_Y_OFFSET;
        int prevX = leftPanelX + ClientConfig.GRID_INNER_PADDING;
        int nextX = leftPanelX + leftPanelW - ClientConfig.GRID_INNER_PADDING - ClientConfig.NAV_BUTTON_SIZE;

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
     * Computes geometry and renders the identification scroll slot between prev/next buttons.
     */
    private void computeAndRenderScrollSlot(int mouseX, int mouseY) {
        int barY = leftPanelY + leftPanelH - ClientConfig.BOTTOM_BAR_HEIGHT / 2 - ClientConfig.ARROWS_Y_OFFSET;
        int prevX = leftPanelX + ClientConfig.GRID_INNER_PADDING;
        int nextX = leftPanelX + leftPanelW - ClientConfig.GRID_INNER_PADDING - ClientConfig.NAV_BUTTON_SIZE;

        int prevRight = prevX + ClientConfig.NAV_BUTTON_SIZE;
        int gapLeft = prevRight + ClientConfig.SCROLL_SLOT_SIDE_GAP;
        int gapRight = nextX - ClientConfig.SCROLL_SLOT_SIDE_GAP;
        int slotSize = Math.min(Math.min(ClientConfig.NAV_BUTTON_SIZE, gapRight - gapLeft), Math.max(8, ClientConfig.SCROLL_SLOT_MAX_SIZE));

        // Center slot vertically on the same baseline as nav buttons
        scrollSlotX = gapLeft + (gapRight - gapLeft - slotSize) / 2;
        scrollSlotY = barY + (ClientConfig.NAV_BUTTON_SIZE - slotSize) / 2;
        scrollSlotW = slotSize;
        scrollSlotH = slotSize;

        // Draw background
        scrollSlotEnabled = isScrollSlotEnabled();
        int fill = scrollSlotEnabled ? ClientConfig.SCROLL_SLOT_BG : ClientConfig.SCROLL_SLOT_BG_DISABLED;
        int border = scrollSlotEnabled ? ClientConfig.SCROLL_SLOT_BORDER : ClientConfig.SCROLL_SLOT_BORDER_DISABLED;
        drawRoundedPanel(scrollSlotX, scrollSlotY, scrollSlotW, scrollSlotH, ClientConfig.SCROLL_SLOT_RADIUS, fill, border);

        // Draw stack/icon and count if available
        int count = tile.getIdentificationScrollCountPublic();
        int maxCount = SpellArchivesConfig.getScrollReserveMax();
        if (count > 0) {
            ItemStack example = new ItemStack(Item.REGISTRY.getObject(new ResourceLocation("ebwizardry", "identification_scroll")));
            if (!example.isEmpty()) {
                RenderHelper.enableGUIStandardItemLighting();
                int iconX = scrollSlotX + 2;
                int iconY = scrollSlotY + (scrollSlotH - 16) / 2;
                itemRender.renderItemAndEffectIntoGUI(example, iconX, iconY);
                RenderHelper.disableStandardItemLighting();

                // Draw count at the bottom, horizontally centered within the slot (/2 for scaling)
                String ct = TextUtils.formatCompactCount(count);
                int textW = fontRenderer.getStringWidth(ct);
                int textX = scrollSlotX + (scrollSlotW - textW / 2) / 2;                // center align
                int textY = scrollSlotY + scrollSlotH + fontRenderer.FONT_HEIGHT / 4;   // under slot

                GL11.glPushMatrix();
                GL11.glScalef(0.5f, 0.5f, 1f); // scale down text to show well
                int col = scrollSlotEnabled ? 0xFFFFFF : 0x888888;
                fontRenderer.drawString(ct, textX * 2, textY * 2, col);
                GL11.glPopMatrix();
            }
        }

        // Visual overlays: gray when disabled
        if (!scrollSlotEnabled) {
            int overlay = 0x77000000;
            drawRect(scrollSlotX, scrollSlotY, scrollSlotX + scrollSlotW, scrollSlotY + scrollSlotH, overlay);
        }

        // Hover outline and tooltip
        boolean hover = mouseX >= scrollSlotX && mouseX < scrollSlotX + scrollSlotW && mouseY >= scrollSlotY && mouseY < scrollSlotY + scrollSlotH;
        if (hover) {
            if (scrollSlotEnabled) {
                drawRect(scrollSlotX, scrollSlotY, scrollSlotX + scrollSlotW, scrollSlotY + 1, ClientConfig.HOVER_BORDER);
                drawRect(scrollSlotX, scrollSlotY + scrollSlotH - 1, scrollSlotX + scrollSlotW, scrollSlotY + scrollSlotH, ClientConfig.HOVER_BORDER);
                drawRect(scrollSlotX, scrollSlotY, scrollSlotX + 1, scrollSlotY + scrollSlotH, ClientConfig.HOVER_BORDER);
                drawRect(scrollSlotX + scrollSlotW - 1, scrollSlotY, scrollSlotX + scrollSlotW, scrollSlotY + scrollSlotH, ClientConfig.HOVER_BORDER);
            }

            List<String> tip = new ArrayList<>();
            if (!scrollSlotEnabled) {
                tip.add(I18n.format("gui.spellarchives.scroll_slot.disabled.title"));
                tip.add(I18n.format("gui.spellarchives.scroll_slot.disabled.toggle"));
            } else if (count <= 0) {
                tip.add(I18n.format("gui.spellarchives.scroll_slot.tooltip.empty"));
            } else {
                tip.add(I18n.format("gui.spellarchives.scroll_slot.tooltip.count", count));
                if (maxCount > 0 && count >= maxCount) tip.add(I18n.format("gui.spellarchives.scroll_slot.tooltip.full"));
            }

            // Defer tooltip drawing so it renders above buttons and other widgets
            pendingTooltip = tip;
            pendingTipX = mouseX;
            pendingTipY = mouseY;
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
            int grooveW = Math.min(gg.gridW, 1 + gridCols * (cellW + ClientConfig.SPINE_LEFT_BORDER));
            drawRowGroove(gg.gridX, baseY, grooveW, cellH);

            // Tier header
            if (gr.showHeader) {
                String tierText;
                int rarityRGB = 0x777777;

                if (!slice.isEmpty()) {
                    Spell rep = slice.get(0).spell;
                    if (rep == null) rep = tile.getSpellPublic(slice.get(0).stack);
                    tierText = rep != null ? rep.getTier().getDisplayNameWithFormatting() : (I18n.format("gui.spellarchives.tier_fallback", gr.tier));
                    rarityRGB = slice.get(0).rarityColor;
                } else {
                    tierText = I18n.format("gui.spellarchives.tier_fallback", gr.tier);
                }

                String tierPlain = TextFormatting.getTextWithoutFormattingCodes(tierText);
                int tabPadX = ClientConfig.TAB_PADDING_X;
                int tabW = fontRenderer.getStringWidth(tierPlain) + tabPadX * 2;
                int tabH = gg.headerH;
                int headerY = baseY - gg.headerH + 1;
                int tabX = gg.gridX + ClientConfig.TAB_OFFSET_X;

                int fill = (0xCC << 24) | (rarityRGB & 0xFFFFFF);
                int border = 0xFF000000 | TextUtils.darkenColor(rarityRGB, 0.6f);
                drawRoundedPanel(tabX, headerY, tabW, tabH, ClientConfig.TAB_RADIUS, fill, border);
                fontRenderer.drawString(tierPlain, tabX + tabPadX, headerY + ClientConfig.HEADER_TEXT_OFFSET_Y, 0x000000);
            }

            // Books in this row
            for (int i = 0; i < slice.size(); i++) {
                BookEntry b = slice.get(i);
                int x = gg.gridX + i * (cellW + ClientConfig.SPINE_LEFT_BORDER);
                int y = baseY;

                // Base spine color from element.getColour(), fallback to precomputed
                int elemColor;
                Spell repSpell = b.spell;
                if (repSpell == null) repSpell = tile.getSpellPublic(b.stack);
                Element repElem = repSpell != null ? repSpell.getElement() : null;
                if (repElem != null) {
                    Style st = repElem.getColour();
                    elemColor = TextUtils.rgbFromStyle(st, b.elementColor);
                } else {
                    elemColor = b.elementColor;
                }

                // Draw shaded spine texture, cached by (color, width, height)
                int spineW = cellW;
                int spineH = cellH - (ClientConfig.SPINE_TOP_BORDER + ClientConfig.SPINE_BOTTOM_BORDER); // leave top/bottom borders inside groove
                if (spineW > 0 && spineH > 0) {
                    ResourceLocation eIcon = (repElem != null) ? repElem.getIcon() : null;
                    ResourceLocation spineTex = DynamicTextureFactory.getOrCreateSpineTexture(elemColor & 0xFFFFFF, spineW, spineH, ClientConfig.SPINE_EMBED_ICON ? eIcon : null, ClientConfig.SPINE_ICON_SIZE);

                    if (spineTex != null) {
                        this.mc.getTextureManager().bindTexture(spineTex);
                        GlStateManager.color(1f, 1f, 1f, 1f);
                        // Draw 1:1 without scaling artifacts
                        drawScaledCustomSizeModalRect(
                                x + ClientConfig.SPINE_LEFT_BORDER,
                                y + ClientConfig.SPINE_TOP_BORDER,
                                0, 0,
                                spineW, spineH,
                                spineW, spineH,
                                spineW, spineH);
                    } else {
                        // Fallback to solid fill
                        int lx = x + ClientConfig.SPINE_LEFT_BORDER;
                        int ty = y + ClientConfig.SPINE_TOP_BORDER;
                        int rx = lx + spineW;
                        int by = ty + spineH;
                        drawRect(lx, ty, rx, by, 0xFF000000 | elemColor);
                    }

                    // subtle 1px shadow under the spine for depth if theme enabled
                    if (ClientConfig.isPanelThemingEnabled()) {
                        int shadow = (0x22 << 24) | (ClientConfig.GROOVE_SH & 0x00FFFFFF);
                        drawRect(x + ClientConfig.SPINE_LEFT_BORDER, y + ClientConfig.SPINE_TOP_BORDER + spineH, x + ClientConfig.SPINE_LEFT_BORDER + spineW, y + ClientConfig.SPINE_TOP_BORDER + spineH + 1, shadow);
                    }
                }

                if (mouseX >= x && mouseX < x + cellW + ClientConfig.SPINE_LEFT_BORDER && mouseY >= y && mouseY < y + cellH) {
                    hoveredEntry = b;
                    int hoverBorder = ClientConfig.HOVER_BORDER;

                    drawRect(x, y, x + cellW + 1 + ClientConfig.SPINE_LEFT_BORDER, y + 1, hoverBorder);
                    drawRect(x, y + cellH - 1, x + cellW + 1 + ClientConfig.SPINE_LEFT_BORDER, y + cellH, hoverBorder);
                    drawRect(x, y, x + 1, y + cellH, hoverBorder);
                    drawRect(x + cellW + ClientConfig.SPINE_LEFT_BORDER, y, x + cellW + 1 + ClientConfig.SPINE_LEFT_BORDER, y + cellH, hoverBorder);
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
        int rightX = rightPanelX + ClientConfig.RIGHT_PANEL_INNER_MARGIN;
        int rightY = rightPanelY + ClientConfig.RIGHT_PANEL_INNER_MARGIN;

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

        int rowY = y + ClientConfig.RIGHT_AFTER_HEADER_GAP;
        rowY = drawElementLine(p, x, rowY, color);
        rowY = drawPropertiesLines(p, x, rowY);
        rowY += ClientConfig.RIGHT_SECTION_GAP;

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

        int rowY = y + ClientConfig.RIGHT_AFTER_HEADER_GAP;
        rowY = drawElementLine(p, x, rowY, color);
        rowY = drawPropertiesLines(p, x, rowY);

        // space before description
        rowY += ClientConfig.RIGHT_SECTION_GAP;

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
        Spell spell = b.spell != null ? b.spell : tile.getSpellPublic(toShow);
        if (spell == null) return null;

        WizardData data = WizardData.get(player);
        boolean discovered = b.discovered || !Wizardry.settings.discoveryMode ||
            (player != null && player.capabilities != null && player.capabilities.isCreativeMode) ||
            (data != null && data.hasSpellBeenDiscovered(spell));

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
        String elementName = element.getFormattingCode() + element.getDisplayName();
        ResourceLocation elementIcon = element.getIcon();

        int cost = spell.getCost();
        boolean isContinuous = spell.isContinuous;
        int chargeUpTime = spell.getChargeup();
        int cooldown = spell.getCooldown();

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
        int textStartX = x + ClientConfig.RIGHT_TITLE_TEXT_GAP;
        int rightContentRight = rightPanelX + rightPanelW - ClientConfig.RIGHT_PANEL_INNER_MARGIN;
        int maxHeaderW = Math.max(0, rightContentRight - textStartX);

        // Use mixed font renderer for undiscovered spells to show glyphs
        String header = p.discovered ? p.headerName : "#" + p.headerName;  // mark as glyph text
        FontRenderer fontRendererInstance = p.discovered ? fontRenderer : ClientProxy.mixedFontRenderer;
        int spaceLeft = Math.max(0, maxHeaderW - 2);
        String headerFitted = TextUtils.trimToWidth(fontRendererInstance, countStr + header, spaceLeft);

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
        int textLeft = x + ClientConfig.RIGHT_ELEMENT_ICON_SIZE + 4;

        if (p.elementIcon != null) {
            this.mc.getTextureManager().bindTexture(p.elementIcon);
            GlStateManager.color(1f, 1f, 1f, 1f);
            drawScaledCustomSizeModalRect(colLeft, rowY, 0, 0, 16, 16, ClientConfig.RIGHT_ELEMENT_ICON_SIZE, ClientConfig.RIGHT_ELEMENT_ICON_SIZE, 16, 16);
        }

        fontRenderer.drawString(p.elementName, textLeft, rowY, color);

        return rowY + ClientConfig.RIGHT_LINE_GAP_MEDIUM;
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
            costStr = I18n.format("gui.spellarchives.cost_fmt", p.cost, p.isContinuous ? "/" + I18n.format("timeunit.s") : "");
            cooldownStr = I18n.format("gui.spellarchives.cooldown_fmt", TextUtils.formatTimeTicks(p.cooldown));
            chargeStr = I18n.format("gui.spellarchives.charge_fmt", TextUtils.formatTimeTicks(p.chargeUpTime));
        }

        fontRenderer.drawString(costStr, x, rowY, ClientConfig.DETAIL_TEXT);
        rowY += ClientConfig.RIGHT_LINE_GAP_SMALL;

        fontRenderer.drawString(cooldownStr, x, rowY, ClientConfig.DETAIL_TEXT);
        rowY += ClientConfig.RIGHT_LINE_GAP_SMALL;

        fontRenderer.drawString(chargeStr, x, rowY, ClientConfig.DETAIL_TEXT);
        rowY += ClientConfig.RIGHT_LINE_GAP_SMALL;

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
        int maxW = rightPanelW - ClientConfig.RIGHT_PANEL_TEXT_SIDE_PAD * 2;
        int safeMaxW = Math.max(0, maxW);  // maxW may be negative if right panel is very narrow
        boolean showIcon = p.discovered && p.spellIcon != null;
        int contentWidth = rightPanelW - ClientConfig.RIGHT_PANEL_INNER_MARGIN * 2;
        int iconW = showIcon ? contentWidth : 0;
        int iconH = iconW;
        int bottomClamp = rightPanelY + rightPanelH - ClientConfig.RIGHT_BOTTOM_CLAMP_MARGIN - iconH - (showIcon ? 4 : 0);

        if (safeMaxW > 0 && desc.length() > 0) {
            FontRenderer fontRendererInstance = p.discovered ? fontRenderer : ClientProxy.mixedFontRenderer;

            // Sanitize the description string to ensure the ICU line-break iterator won't crash
            String safeDesc = TextUtils.sanitizeForBreakIterator(desc);

            for (String line : TextUtils.wrapTextToWidth(fontRendererInstance, safeDesc, safeMaxW)) {
                if (rowY + ClientConfig.RIGHT_DESC_LINE_HEIGHT > bottomClamp) break;
                if (!p.discovered) line = "#" + line;  // mark as glyph text

                fontRendererInstance.drawString(line, x, rowY, color);
                rowY += ClientConfig.RIGHT_DESC_LINE_HEIGHT;
            }
        }

        if (showIcon) {
            int iconX = rightPanelX + ClientConfig.RIGHT_PANEL_INNER_MARGIN;
            int iconY = rightPanelY + rightPanelH - ClientConfig.RIGHT_BOTTOM_CLAMP_MARGIN - iconH;
            this.mc.getTextureManager().bindTexture(p.spellIcon);
            GlStateManager.color(1f, 1f, 1f, 1f);
            drawScaledCustomSizeModalRect(iconX, iconY, 0, 0, 16, 16, iconW, iconH, 16, 16);
        }

        // Instructions block: position immediately above the icon (if visible) or at the bottom of the right panel.
        int instColor = ClientConfig.DETAIL_TEXT;
        String inst1 = I18n.format("gui.spellarchives.inst.left_extract");
        String inst2 = I18n.format("gui.spellarchives.inst.shift_left_stack", p.stack.getMaxStackSize());
        String inst3;
        if (!p.discovered) {
            boolean enabled = isScrollSlotEnabled();
            int reserve = tile.getIdentificationScrollCountPublic();
            if (!enabled) inst3 = I18n.format("gui.spellarchives.inst.right_discover_disabled");
            else if (reserve <= 0) inst3 = I18n.format("gui.spellarchives.inst.right_discover_add");
            else inst3 = I18n.format("gui.spellarchives.inst.right_discover");
        } else {
            inst3 = I18n.format("gui.spellarchives.inst.right_discover_known");
        }

        List<String> instLines = new ArrayList<>();
        instLines.add(inst1);
        instLines.add(inst2);
        if (inst3 != null && !inst3.isEmpty()) instLines.add(inst3);

        // Wrap each instruction line individually to avoid losing semantic separation
        List<String> wrappedInst1 = new ArrayList<>();
        List<String> wrappedInst2 = new ArrayList<>();
        List<String> wrappedInst3 = new ArrayList<>();
        if (safeMaxW > 0) {
            wrappedInst1.addAll(TextUtils.wrapTextToWidth(fontRenderer, inst1, safeMaxW));
            wrappedInst2.addAll(TextUtils.wrapTextToWidth(fontRenderer, inst2, safeMaxW));
            if (inst3 != null && !inst3.isEmpty()) wrappedInst3.addAll(TextUtils.wrapTextToWidth(fontRenderer, inst3, safeMaxW));
        }

        int lineGap = ClientConfig.RIGHT_LINE_GAP_SMALL;
        int minAllowed = rowY + ClientConfig.RIGHT_SECTION_GAP / 2;

        // No icon: bottom alignment to the panel bottom/top of icon. inst3 has priority, then inst2, then inst1.
        if (showIcon) {
            int iconTopY = rightPanelY + rightPanelH - ClientConfig.RIGHT_BOTTOM_CLAMP_MARGIN - iconH;
            int desiredBottom = iconTopY - 2; // small gap above icon

            int availablePixels = Math.max(0, desiredBottom - minAllowed);
            int availableLines = availablePixels / lineGap;

            int lines3 = wrappedInst3.size();
            int lines2 = wrappedInst2.size();
            int lines1 = wrappedInst1.size();

            boolean draw3 = false, draw2 = false, draw1 = false;
            int used = 0;
            if (lines3 > 0 && used + lines3 <= availableLines) { draw3 = true; used += lines3; }
            if (lines2 > 0 && used + lines2 <= availableLines) { draw2 = true; used += lines2; }
            if (lines1 > 0 && used + lines1 <= availableLines) { draw1 = true; used += lines1; }

            if (used > 0) {
                int totalHeight = used * lineGap;
                int startY = desiredBottom - totalHeight;

                // Draw in top-to-bottom order so inst3 ends up nearest the bottom
                if (draw1) {
                    for (String w : wrappedInst1) { fontRenderer.drawString(w, x, startY, instColor); startY += lineGap; }
                }
                if (draw2) {
                    for (String w : wrappedInst2) { fontRenderer.drawString(w, x, startY, instColor); startY += lineGap; }
                }
                if (draw3) {
                    for (String w : wrappedInst3) { fontRenderer.drawString(w, x, startY, instColor); startY += lineGap; }
                }
            }
        } else {
            int bottomEdge = rightPanelY + rightPanelH - ClientConfig.RIGHT_BOTTOM_CLAMP_MARGIN; // stick to panel bottom
            int availablePixels = Math.max(0, bottomEdge - minAllowed);
            int availableLines = availablePixels / lineGap;

            int lines3 = wrappedInst3.size();
            int lines2 = wrappedInst2.size();
            int lines1 = wrappedInst1.size();

            boolean draw3 = false, draw2 = false, draw1 = false;
            int used = 0;
            if (lines3 > 0 && used + lines3 <= availableLines) { draw3 = true; used += lines3; }
            if (lines2 > 0 && used + lines2 <= availableLines) { draw2 = true; used += lines2; }
            if (lines1 > 0 && used + lines1 <= availableLines) { draw1 = true; used += lines1; }

            if (used > 0) {
                int totalHeight = used * lineGap;
                int startY = bottomEdge - totalHeight;
                if (startY < minAllowed) startY = minAllowed; // safety clamp

                if (draw1) {
                    for (String w : wrappedInst1) { fontRenderer.drawString(w, x, startY, instColor); startY += lineGap; }
                }
                if (draw2) {
                    for (String w : wrappedInst2) { fontRenderer.drawString(w, x, startY, instColor); startY += lineGap; }
                }
                if (draw3) {
                    for (String w : wrappedInst3) { fontRenderer.drawString(w, x, startY, instColor); startY += lineGap; }
                }
            }
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
        if (handleFilterClick(mouseX, mouseY, mouseButton)) return;

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

    private boolean handleFilterClick(int mouseX, int mouseY, int mouseButton) {
        if (discoveryHeaderRect.contains(mouseX, mouseY)) return true; // dropdown opens via hover; click is consumed

        if (discoveryDropdownOpen && discoveryContentRect.contains(mouseX, mouseY)) {
            if (mouseButton == 0) {
                int relY = mouseY - (discoveryContentRect.y + 2);
                int index = relY / FILTER_OPTION_HEIGHT;
                DiscoveryFilter[] options = DiscoveryFilter.values();
                if (index >= 0 && index < options.length) {
                    DiscoveryFilter choice = options[index];
                    if (discoveryFilter != choice) {
                        discoveryFilter = choice;
                        onFiltersChanged();
                    }
                }

                discoveryDropdownOpen = false;
            }

            return true;
        }

        if (modsHeaderRect.contains(mouseX, mouseY)) {
            if (mouseButton == 0) {
                long now = Minecraft.getSystemTime();
                if (now - lastModsHeaderClickMs <= DOUBLE_CLICK_MS) {
                    toggleAllModFilters();
                    lastModsHeaderClickMs = 0L;
                } else {
                    lastModsHeaderClickMs = now;
                }
            }

            return true;
        }

        if (modsDropdownOpen && modsContentRect.contains(mouseX, mouseY)) {
            if (mouseButton == 0 && !availableModOptions.isEmpty()) {
                int relY = mouseY - (modsContentRect.y + 2);
                int index = relY / FILTER_OPTION_HEIGHT;
                if (index >= 0 && index < availableModOptions.size()) {
                    String modId = availableModOptions.get(index);
                    if (selectedModFilters.contains(modId)) selectedModFilters.remove(modId);
                    else selectedModFilters.add(modId);

                    modFilterTouched = true;
                    onFiltersChanged();
                }
            }
            return true;
        }

        return false;
    }

    private void toggleAllModFilters() {
        if (availableModOptions.isEmpty()) return;

        if (selectedModFilters.size() == availableModOptions.size()) {
            selectedModFilters.clear();
        } else {
            selectedModFilters.clear();
            selectedModFilters.addAll(availableModOptions);
        }

        modFilterTouched = true;
        onFiltersChanged();
    }

    /**
     * Wrapper method to determine if the scroll slot feature should be enabled.
     * Currently returns true when Wizardry discovery mode is enabled or the player is in creative.
     * Hook for future expansion/config bridging.
     */
    private boolean isScrollSlotEnabled() {
        return SpellArchivesConfig.isScrollReserveEnabled();
    }

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

    /**
     * Draws a grooved row background.
     * @param x the x position of the groove
     * @param y the y position of the groove
     * @param w the width of the groove
     * @param h the height of the groove
     */
    private void drawRowGroove(int x, int y, int w, int h) {
        int base = ClientConfig.GROOVE_BASE;    // slightly darker so it pops
        int hl = ClientConfig.GROOVE_HL;        // top highlight
        int sh = ClientConfig.GROOVE_SH;        // bottom shadow

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
