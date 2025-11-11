package com.spellarchives.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * A small per-GUI cache manager for layout and presentation caches used by GuiSpellArchive.
 * It keeps simple cached values and invalidates them when the GuiStyle.CONFIG_REVISION changes
 * or when the snapshot keys / grid columns change.
 */
public final class GuiCacheManager {
    private Set<String> lastKeys = new HashSet<>();
    private GuiSpellArchive.DisplayRows cachedDisplayRows = null;
    private GuiSpellArchive.PageInfo cachedPageInfo = null;

    // GUI instance helpers
    private GuiSpellArchive.BookEntry hoveredEntry = null;
    private GuiSpellArchive.SpellPresentation cachedPresentationObj = null;
    private String cachedPresentationKey = null;
    private GuiSpellArchive.GridGeometry cachedGG = null;

    private int cachedGridColsForDisplay = -1;
    private int cachedPageForLayout = -1;

    private int cachedStyleRevision = -1;

    public GuiCacheManager() {}

    public void checkStyleRevision(int styleRevision) {
        if (this.cachedStyleRevision != styleRevision) {
            clearAll();

            this.cachedStyleRevision = styleRevision;
        }
    }

    public void clearAll() {
        lastKeys.clear();
        cachedDisplayRows = null;
        cachedPageInfo = null;
        cachedGridColsForDisplay = -1;
        cachedPageForLayout = -1;
        hoveredEntry = null;
        cachedPresentationObj = null;
        cachedGG = null;
    }

    public void setHoveredEntry(GuiSpellArchive.BookEntry e) {
        this.hoveredEntry = e;
    }

    public GuiSpellArchive.BookEntry getHoveredEntry() {
        return this.hoveredEntry;
    }

    public GuiSpellArchive.SpellPresentation getCachedPresentation(String key, int count) {
        if (this.cachedPresentationObj == null) return null;

        // simple match by equality on previously cached key/counter encoded in headerName and count
        // We store presentation only for the current hovered; if mismatch return null
        if (this.cachedPresentationKey != null && this.cachedPresentationKey.equals(key) && this.cachedPresentationObj.count == count) return this.cachedPresentationObj;

        return null;
    }

    public void putCachedPresentation(String key, GuiSpellArchive.SpellPresentation p) {
        this.cachedPresentationKey = key;
        this.cachedPresentationObj = p;
    }

    public void clearCachedPresentation() {
        this.cachedPresentationObj = null;
        this.cachedPresentationKey = null;
    }

    public GuiSpellArchive.GridGeometry getCachedGG() {
        return this.cachedGG;
    }

    public void setCachedGG(GuiSpellArchive.GridGeometry gg) {
        this.cachedGG = gg;
    }

    public boolean haveKeysChanged(Set<String> keys) {
        return !keys.equals(this.lastKeys);
    }

    public boolean haveColsChanged(int gridCols) {
        return this.cachedGridColsForDisplay != gridCols;
    }

    public GuiSpellArchive.DisplayRows getOrBuildDisplayRows(Set<String> snapshotKeys, Map<Integer, List<GuiSpellArchive.BookEntry>> rowsByTier, int gridCols) {
        boolean keysChanged = !snapshotKeys.equals(this.lastKeys);
        boolean colsChanged = (this.cachedGridColsForDisplay != gridCols);

        if (this.cachedDisplayRows == null || keysChanged || colsChanged) {
            // rebuild
            List<List<GuiSpellArchive.BookEntry>> displayRows = new ArrayList<>();
            List<Integer> displayRowTiers = new ArrayList<>();

            for (Integer tierKey : rowsByTier.keySet()) {
                List<GuiSpellArchive.BookEntry> rowBooks = rowsByTier.get(tierKey);
                if (rowBooks == null || rowBooks.isEmpty()) continue;

                for (int off = 0; off < rowBooks.size(); off += gridCols) {
                    int endIdx = Math.min(off + gridCols, rowBooks.size());
                    displayRows.add(rowBooks.subList(off, endIdx));
                    displayRowTiers.add(tierKey);
                }
            }

            this.cachedDisplayRows = new GuiSpellArchive.DisplayRows(displayRows, displayRowTiers);
            this.cachedGridColsForDisplay = gridCols;
            this.cachedPageInfo = null; // page info depends on displayRows
            this.lastKeys = new HashSet<>(snapshotKeys);
        }

        return this.cachedDisplayRows;
    }

    public GuiSpellArchive.PageInfo getOrBuildPageInfo(GuiSpellArchive.DisplayRows dr,
                                                       int gridX, int gridY, int gridW, int gridH, int headerH,
                                                       int page, int cellH, int rowGap, int gridRows) {
        boolean layoutChanged = false;

        // Check if geometry changed by comparing stored grid geometry values
        if (this.cachedPageInfo == null) layoutChanged = true;

        if (this.cachedPageInfo == null || layoutChanged || this.cachedPageForLayout != page) {
            // build page info
            List<GuiSpellArchive.GrooveRow> pageLayout = new ArrayList<>();

            int totalRows = dr.rows.size();
            int gridBottom = gridY + gridH;
            int curY = gridY + headerH;

            // Use explicit gridRows (rows per page) for deterministic startRow
            int rowsPerPage = Math.max(1, gridRows);
            int startRow = page * rowsPerPage;

            Set<Integer> seenTiers = new HashSet<>();

            for (int r = startRow; r < totalRows; r++) {
                int tier = dr.rowTiers.get(r);
                boolean showHeader = !seenTiers.contains(tier);

                if (curY + cellH > gridBottom) break;

                pageLayout.add(new GuiSpellArchive.GrooveRow(r, tier, curY, showHeader));
                seenTiers.add(tier);

                if (r + 1 < totalRows) {
                    int nextTier = dr.rowTiers.get(r + 1);
                    boolean nextIsFirstOfTier = !seenTiers.contains(nextTier);
                    curY += cellH + (nextIsFirstOfTier ? headerH + rowGap : 1);
                }
            }

            boolean anyHasNext = (startRow + pageLayout.size()) < totalRows;
            this.cachedPageInfo = new GuiSpellArchive.PageInfo(pageLayout, anyHasNext);
            this.cachedPageForLayout = page;
        }

        return this.cachedPageInfo;
    }
}
